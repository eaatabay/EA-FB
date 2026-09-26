import {
  HEALTH, applyProbe, initialState, releaseForRetest, validateSource,
} from "./policy.mjs";
import { buildClientSnapshot } from "./snapshot.mjs";

/**
 * Private D1 registry. NO public HTTP endpoints or live third-party probes.
 *
 * Trigger-backed writes atomically advance a global snapshot revision and
 * append an audit record. Every write is compare-and-swap on source revision.
 * source_probe_runs additionally prevents replaying an older run ID.
 */
export class RegistryConflict extends Error {
  constructor() { super("source_revision_conflict"); this.name = "RegistryConflict"; }
}

function clock(now) {
  if (!Number.isSafeInteger(now) || now < 0) throw new Error("invalid_clock");
  return now;
}

function actorName(actor, expected) {
  if (typeof actor !== "string" ||
      !new RegExp("^" + expected + ":[a-zA-Z0-9_-]{3,64}$").test(actor)) {
    throw new Error("invalid_actor");
  }
  return actor;
}

function reasonText(reason) {
  if (typeof reason !== "string" || !/^[a-z0-9_.:-]{3,96}$/.test(reason)) {
    throw new Error("invalid_change_reason");
  }
  return reason;
}

function runIdText(runId) {
  if (typeof runId !== "string" || !/^[a-zA-Z0-9][a-zA-Z0-9_.:-]{7,95}$/.test(runId)) {
    throw new Error("invalid_run_id");
  }
  return runId;
}

function validApprovalReference(value) {
  return typeof value === "string" && value.length >= 8 && value.length <= 160 &&
    /^[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_.-]+)*$/.test(value) &&
    value.split("/").every(part => part !== "." && part !== "..");
}

function requireApprovedConfig(input) {
  const config = validateSource(input);
  if (typeof config.integrationApproved !== "boolean" ||
      !Number.isSafeInteger(config.adapterVersion) ||
      config.adapterVersion < 1 || config.adapterVersion > 1_000_000) {
    throw new Error("missing_integration_review");
  }
  // Persist an explicit allowlist only. Unexpected fields must never carry
  // credentials, cookies, probe secrets, or internal URLs into D1/audit history.
  const clean = {
    id: config.id,
    enabled: config.enabled,
    mediaKind: config.mediaKind,
    integrationApproved: config.integrationApproved,
    adapterVersion: config.adapterVersion,
    currentUrl: config.currentUrl,
    lastKnownGoodUrl: config.lastKnownGoodUrl,
    verifiedDomains: [...config.verifiedDomains],
    requiredChecks: [...config.requiredChecks],
  };
  if (config.approvalRef != null) {
    if (!validApprovalReference(config.approvalRef)) {
      throw new Error("invalid_approval_reference");
    }
    clean.approvalRef = config.approvalRef;
  }
  return clean;
}

function rowRecord(row) {
  if (!row) return null;
  const config = requireApprovedConfig(JSON.parse(row.config_json));
  const state = JSON.parse(row.state_json);
  if (row.id !== config.id || row.id !== state.id ||
      !Number.isSafeInteger(row.revision) || row.revision < 0 ||
      state.revision !== row.revision ||
      !Number.isSafeInteger(row.updated_at_ms)) throw new Error("corrupt_source_record");
  return {
    id: row.id, config, state, revision: row.revision,
    lastCheckRunId: row.last_check_run_id,
    updatedAt: row.updated_at_ms,
  };
}

/** Optional precondition for administrative writes; required by the HTTP layer. */
function requireRevision(previous, expectedRevision) {
  if (expectedRevision === null) return;
  if (!Number.isSafeInteger(expectedRevision) || expectedRevision < 0) {
    throw new Error("invalid_expected_revision");
  }
  if (previous.revision !== expectedRevision) throw new RegistryConflict();
}

function dbReady(db) {
  if (!db || typeof db.prepare !== "function") throw new Error("d1_not_configured");
  return db;
}

export async function getSource(db, id) {
  dbReady(db);
  if (typeof id !== "string" || !/^[a-z][a-z0-9-]{2,63}$/.test(id)) {
    throw new Error("invalid_source_id");
  }
  const row = await db.prepare(
    "SELECT id, config_json, state_json, revision, last_check_run_id, updated_at_ms " +
    "FROM source_registry WHERE id = ?"
  ).bind(id).first();
  return rowRecord(row);
}

export async function registerSource(db, rawConfig, actor, now) {
  dbReady(db);
  const config = requireApprovedConfig(rawConfig);
  actorName(actor, "admin");
  clock(now);
  // A source is never shown to devices until two independent successful
  // probe runs and an explicit reviewed adapter approval.
  const state = initialState(config, now);
  const result = await db.prepare(
    "INSERT INTO source_registry " +
    "(id, config_json, state_json, revision, last_check_run_id, updated_at_ms, " +
    "changed_by, change_reason) VALUES (?, ?, ?, 0, NULL, ?, ?, 'source_registered') " +
    "ON CONFLICT(id) DO NOTHING"
  ).bind(config.id, JSON.stringify(config), JSON.stringify(state), now, actor).run();
  if (result?.meta?.changes !== 1) throw new RegistryConflict();
  return { id: config.id, config, state, revision: 0, lastCheckRunId: null, updatedAt: now };
}

async function replaceCAS(db, previous, nextConfig, nextState, runId, actor, reason, now, lease = null) {
  dbReady(db);
  clock(now);
  const config = requireApprovedConfig(nextConfig);
  actorName(actor, actor.startsWith("admin:") ? "admin" : "watchdog");
  reasonText(reason);
  if (config.id !== previous.id || nextState?.id !== previous.id ||
      !Number.isSafeInteger(previous.revision) || previous.revision < 0 ||
      now < previous.updatedAt) throw new Error("invalid_update");
  if (runId !== null) runIdText(runId);
  // One atomic UPDATE CAS also verifies that the CURRENT runner still owns
  // its unexpired lease. A late probe cannot overwrite a newer lease holder.
  // Legacy direct internal/test calls may omit the lease; the public Worker
  // exposes no route that can call commitProbe directly.
  if (lease !== null && (!lease || typeof lease.token !== "string" ||
      !Number.isSafeInteger(lease.checkedAtMs) || lease.checkedAtMs < 0)) {
    throw new Error("invalid_probe_lease");
  }
  const leaseClause = lease === null ? "" :
    " AND EXISTS (SELECT 1 FROM source_probe_leases WHERE source_id=? " +
    "AND lease_token=? AND expires_at_ms>?)";
  const leaseArgs = lease === null ? [] :
    [previous.id, lease.token, lease.checkedAtMs];
  const result = await db.prepare(
    "UPDATE source_registry SET config_json = ?, state_json = ?, revision = revision + 1, " +
    "last_check_run_id = ?, updated_at_ms = ?, changed_by = ?, change_reason = ? " +
    "WHERE id = ? AND revision = ?" + leaseClause
  ).bind(JSON.stringify(config), JSON.stringify(nextState), runId, now,
    actor, reason, previous.id, previous.revision, ...leaseArgs).run();
  if (result?.meta?.changes !== 1) throw new RegistryConflict();
  return {
    id: previous.id, config, state: nextState, revision: previous.revision + 1,
    lastCheckRunId: runId, updatedAt: now,
  };
}

/**
 * ONLY trusted internal probe runners may call this function. The caller must
 * verify source ownership/permissions and implement DNS/redirect SSRF guards,
 * request budgets and the source-specific fixture checks independently.
 */
export async function commitProbe(db, id, runId, probe, now, lease = null) {
  runIdText(runId);
  clock(now);
  const previous = await getSource(db, id);
  if (!previous) throw new Error("source_not_found");
  // Historical duplicate IDs must not count twice, even after intervening runs.
  const already = await db.prepare(
    "SELECT source_revision FROM source_probe_runs WHERE source_id = ? AND run_id = ?"
  ).bind(id, runId).first();
  if (already) return { duplicate: true, revision: already.source_revision };
  if (!previous.config.enabled || !previous.config.integrationApproved ||
      previous.state.status === HEALTH.ADMIN_REQUIRED) {
    return { skipped: true, reason: !previous.config.integrationApproved
      ? "integration_not_approved" : previous.state.status };
  }
  const state = applyProbe(previous.config, previous.state, probe, now);
  const promoted = state.status === HEALTH.HEALTHY &&
    state.currentUrl !== previous.config.currentUrl;
  const config = promoted ? {
    ...previous.config, currentUrl: state.currentUrl,
    lastKnownGoodUrl: state.lastKnownGoodUrl,
  } : previous.config;
  const reason = state.lastFailure ? "probe:" + state.lastFailure : (
    promoted ? "probe:verified_domain_change" : "probe:" + state.status
  );
  const record = await replaceCAS(db, previous, config, state, runId,
    "watchdog:runner", reason, now, lease);
  return { duplicate: false, record };
}

export async function setEnabled(db, id, enabled, actor, now, expectedRevision = null) {
  actorName(actor, "admin");
  clock(now);
  if (typeof enabled !== "boolean") throw new Error("invalid_enabled_state");
  const previous = await getSource(db, id);
  if (!previous) throw new Error("source_not_found");
  requireRevision(previous, expectedRevision);
  if (previous.config.enabled === enabled) return { unchanged: true, record: previous };
  const config = { ...previous.config, enabled };
  const state = enabled ? {
    ...initialState(config, now), revision: previous.state.revision + 1,
  } : {
    ...previous.state, status: HEALTH.DISABLED,
    nextCheckAt: null, candidateUrl: null,
    consecutiveFailures: 0, consecutiveSuccesses: 0,
    revision: previous.state.revision + 1,
  };
  const record = await replaceCAS(db, previous, config, state,
    previous.lastCheckRunId, actor,
    enabled ? "admin:enabled" : "admin:disabled", now);
  return { unchanged: false, record };
}

export async function setIntegrationApproval(db, id, approved, evidenceRef, actor, now, expectedRevision = null) {
  actorName(actor, "admin");
  clock(now);
  if (typeof approved !== "boolean" ||
      !validApprovalReference(evidenceRef)) {
    throw new Error("invalid_approval_evidence");
  }
  const previous = await getSource(db, id);
  if (!previous) throw new Error("source_not_found");
  requireRevision(previous, expectedRevision);
  if (previous.config.integrationApproved === approved) {
    return { unchanged: true, record: previous };
  }
  const config = {
    ...previous.config,
    integrationApproved: approved,
    approvalRef: evidenceRef,
  };
  // Any permission re-approval must restart health checks. A revoked adapter
  // is removed from the next snapshot immediately (global revision advances).
  const state = {
    ...initialState(config, now), revision: previous.state.revision + 1,
  };
  const record = await replaceCAS(db, previous, config, state,
    previous.lastCheckRunId, actor,
    approved ? "admin:integration_approved" : "admin:integration_revoked", now);
  return { unchanged: false, record };
}

export async function retestAfterReview(db, id, actor, now, expectedRevision = null) {
  actorName(actor, "admin");
  clock(now);
  const previous = await getSource(db, id);
  if (!previous) throw new Error("source_not_found");
  requireRevision(previous, expectedRevision);
  if (!previous.config.enabled || !previous.config.integrationApproved) {
    throw new Error("source_not_eligible");
  }
  const state = releaseForRetest(previous.config, previous.state, now);
  const record = await replaceCAS(db, previous, previous.config, state,
    previous.lastCheckRunId, actor, "admin:retest", now);
  return { record };
}

/**
 * Roll back to an audited, previously HEALTHY address already on the allowlist.
 * Returns to DEGRADED while two fresh checks run: never publish stale health.
 * An unapproved/new hostname needs separate human ownership verification.
 */
export async function rollbackToLastHealthy(db, id, actor, now, expectedRevision = null) {
  actorName(actor, "admin");
  clock(now);
  const previous = await getSource(db, id);
  if (!previous) throw new Error("source_not_found");
  requireRevision(previous, expectedRevision);
  const results = await db.prepare(
    "SELECT new_state_json FROM source_audit WHERE source_id = ? " +
    "ORDER BY event_id DESC LIMIT 100"
  ).bind(id).all();
  let target = null;
  for (const row of results.results ?? []) {
    const prior = JSON.parse(row.new_state_json);
    if (prior.status !== HEALTH.HEALTHY ||
        prior.currentUrl === previous.state.currentUrl) continue;
    const checked = validateSource({
      ...previous.config, currentUrl: prior.currentUrl,
      lastKnownGoodUrl: prior.currentUrl,
    });
    if (checked.currentUrl === prior.currentUrl) {
      target = prior.currentUrl;
      break;
    }
  }
  if (!target) throw new Error("no_approved_previous_healthy_address");
  const config = {
    ...previous.config, currentUrl: target, lastKnownGoodUrl: target,
  };
  const state = {
    ...initialState(config, now), revision: previous.state.revision + 1,
  };
  const record = await replaceCAS(db, previous, config, state,
    previous.lastCheckRunId, actor, "admin:rollback_retest", now);
  return { record };
}

export async function getPrivateRegistry(db) {
  dbReady(db);
  const response = await db.prepare(
    "SELECT id, config_json, state_json, revision, last_check_run_id, updated_at_ms " +
    "FROM source_registry ORDER BY id"
  ).all();
  return (response.results ?? []).map(rowRecord);
}

/**
 * Internal only: D1 batch runs both SELECT statements in one read transaction.
 * Client-facing publication later MUST sign the result and validate signature
 * in the app. This function DOES NOT expose an HTTP route.
 */
export async function buildUnpublishedSnapshot(db, now) {
  dbReady(db);
  clock(now);
  if (typeof db.batch !== "function") throw new Error("d1_batch_required");
  const [meta, rows] = await db.batch([
    db.prepare("SELECT revision FROM registry_meta WHERE singleton = 1"),
    db.prepare(
      "SELECT id, config_json, state_json, revision, last_check_run_id, updated_at_ms " +
      "FROM source_registry ORDER BY id"
    ),
  ]);
  const revision = meta?.results?.[0]?.revision;
  if (!Number.isSafeInteger(revision)) throw new Error("registry_not_migrated");
  const records = (rows?.results ?? []).map(rowRecord);
  return buildClientSnapshot(records.map(x => x.config),
    records.map(x => x.state), revision, now);
}
