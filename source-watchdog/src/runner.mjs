import { commitProbe, getPrivateRegistry, getSource, RegistryConflict } from "./registry.mjs";
import { dueSources, incidentEligible } from "./scheduler.mjs";
import { claimProbeLease, releaseProbeLease } from "./lease.mjs";
import { HEALTH } from "./policy.mjs";

function checkAdapters(adapters) {
  if (!(adapters instanceof Map)) throw new Error("adapter_map_required");
  for (const [id, adapter] of adapters) {
    if (typeof id !== "string" || !adapter || adapter.id !== id ||
        typeof adapter.probe !== "function") throw new Error("invalid_adapter");
  }
}

const TIMEOUT = Symbol("probe_timeout");

async function withTimeout(task, timeoutMs) {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 500 || timeoutMs > 60000) {
    throw new Error("invalid_probe_timeout");
  }
  const controller = new AbortController();
  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(() => task(controller.signal)),
      new Promise((_, reject) => {
        timer = setTimeout(() => {
          // Reject BEFORE aborting: a synchronous abort listener may resolve
          // the adapter promise. Timeout must never count as healthy.
          reject(TIMEOUT);
          controller.abort();
        }, timeoutMs);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

function safeRunId(sourceId, now, ordinal) {
  return "run-" + sourceId + "-" + now + "-" + ordinal;
}

/**
 * Each source owns a persisted lease before probing. Revalidate eligibility
 * after acquisition and enforce the unexpired lease in the commit's DB CAS.
 * Future network adapters MUST honor signal, run network-boundary preflight
 * on EVERY redirect hop, and pin/check the actual remote IP to prevent DNS
 * rebinding. No live transport is attached to this runner in v6 yet.
 */
export async function runOneSourceCheck({
  db, adapters, sourceId, now, runId, timeoutMs = 12000,
  mode = "scheduled", leaseClock = Date.now,
}) {
  checkAdapters(adapters);
  if (!Number.isSafeInteger(now) || now < 0 ||
      !Number.isSafeInteger(timeoutMs) || timeoutMs < 500 || timeoutMs > 60000 ||
      !["scheduled", "incident"].includes(mode) ||
      typeof runId !== "string" || !/^[a-zA-Z0-9][a-zA-Z0-9_.:-]{7,95}$/.test(runId) ||
      typeof leaseClock !== "function") throw new Error("invalid_check_request");
  const record = await getSource(db, sourceId);
  if (!record) return {sourceId, status:"not_found"};
  const adapter = adapters.get(sourceId);
  if (!adapter) return {sourceId, status:"adapter_missing"};
  if (!record.config.enabled || record.config.integrationApproved !== true ||
      record.state.status === HEALTH.ADMIN_REQUIRED) {
    return {sourceId, status:"not_eligible"};
  }

  const token = "lease-" + globalThis.crypto.randomUUID();
  const leaseNow = leaseClock();
  const acquired = await claimProbeLease(db, sourceId, token, leaseNow,
    Math.min(120000, Math.max(30000, timeoutMs + 15000)));
  if (!acquired) return {sourceId, status:"lease_busy"};
  try {
    const current = await getSource(db, sourceId);
    if (!current || !current.config.enabled ||
        current.config.integrationApproved !== true ||
        current.state.status === HEALTH.ADMIN_REQUIRED) {
      return {sourceId, status:"not_eligible"};
    }
    if (mode === "scheduled" && dueSources([current], now, 1).length === 0) {
      return {sourceId, status:"not_due"};
    }
    if (mode === "incident" && !incidentEligible(current, now)) {
      return {sourceId, status:"rate_limited_or_ineligible"};
    }

    // Adapter crashes and timeouts are REAL source health failures, not
    // invisible runner errors. Persist them through the same lease/CAS/audit
    // path so repeated failures back off and eventually quarantine. Never
    // allow an abort listener's late "healthy" response to override timeout.
    let probe, probeError = null;
    try {
      probe = await withTimeout(
        signal => adapter.probe({source: current.config, now, signal}), timeoutMs);
      if (!probe || typeof probe !== "object" || Array.isArray(probe) ||
          Object.hasOwn(probe, "runnerFailure")) {
        throw new Error("invalid_adapter_result");
      }
    } catch (err) {
      probeError = err === TIMEOUT ?
        "probe_timeout" : "adapter_error";
      probe = {reached:false, finalUrl:current.config.currentUrl,
        identityVerified:false, checks:{}, runnerFailure:probeError};
    }
    const committed = await commitProbe(db, sourceId, runId, probe, now,
      {token, checkedAtMs: leaseClock()});
    return {sourceId,
      status: committed.skipped ? "skipped" : committed.duplicate ? "duplicate" :
        probeError ? "probe_failed" : "committed",
      ...(probeError ? {error:probeError} : {}),
      detail: committed};
  } catch (err) {
    // Do not leak exception text, URLs, auth headers or site content into logs.
    const error = err instanceof RegistryConflict ? "stale_revision_or_lease" :
      err === TIMEOUT ? "probe_timeout" : "internal_failure";
    return {sourceId, status:"runner_error", error};
  } finally {
    try { await releaseProbeLease(db, sourceId, token); }
    catch { /* Expiration releases a crashed owner without blocking others. */ }
  }
}

export async function runDueChecks({
  db, adapters, now, limit = 8, concurrency = 4, timeoutMs = 12000,
}) {
  checkAdapters(adapters);
  if (!Number.isSafeInteger(concurrency) || concurrency < 1 || concurrency > 8) {
    throw new Error("invalid_concurrency");
  }
  const records = await getPrivateRegistry(db);
  const due = dueSources(records, now, limit);
  const out = new Array(due.length);
  let cursor = 0;

  async function worker(slot) {
    while (true) {
      const index = cursor++;
      if (index >= due.length) return;
      const record = due[index];
      try {
        out[index] = await runOneSourceCheck({
          db, adapters, sourceId: record.id, now,
          runId: safeRunId(record.id, now, index + slot * 1000), timeoutMs,
        });
      } catch (_) {
        // A corrupt source or isolated D1 failure must not stop other probes.
        out[index] = {sourceId: record.id, status:"runner_error", error:"scheduling_failure"};
      }
    }
  }

  const workers = Math.min(concurrency, Math.max(1, due.length));
  await Promise.all(Array.from({length: workers}, (_,i) => worker(i)));
  return out;
}

export async function runIncidentCheck({
  db, adapters, sourceId, now, incidentId, timeoutMs = 12000,
}) {
  checkAdapters(adapters);
  if (typeof incidentId !== "string" || !/^[a-zA-Z0-9_.:-]{8,88}$/.test(incidentId)) {
    throw new Error("invalid_incident_id");
  }
  const record = await getSource(db, sourceId);
  if (!record) return {sourceId,status:"not_found"};
  if (!incidentEligible(record, now)) {
    return {sourceId,status:"rate_limited_or_ineligible"};
  }
  return runOneSourceCheck({
    db,
    adapters,
    sourceId,
    now,
    runId:"inc-" + incidentId,
    timeoutMs,
    mode:"incident",
  });
}
