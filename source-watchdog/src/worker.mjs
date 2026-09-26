/**
 * EA-FB Source Watchdog — isolated Cloudflare Worker entrypoint.
 * SAFE BY DEFAULT: the committed Wrangler config disables all writes. There
 * are NO third-party network adapters or public registry/admin endpoints.
 * Fixture mode requires an entirely fixture-only D1 database.
 */
import { getPrivateRegistry, buildUnpublishedSnapshot, RegistryConflict } from "./registry.mjs";
import { assertReviewedPublication } from "./publication-guard.mjs";
import {adminWritesConfigured,parseAdminMutationRequest,executeAdminMutation,AdminMutationError} from "./admin-actions.mjs";
import { publishSignedSnapshot } from "./snapshot-publisher.mjs";
import { adminAccessConfigured, verifyAdminAccess } from "./admin-auth.mjs";
import { summarizeSources, renderAdminDashboard } from "./admin-view.mjs";
import { runDueChecks } from "./runner.mjs";
import { assertIsolatedFixtureRegistry, createFixtureAdapters } from "./fixtures.mjs";

function response(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
    },
  });
}

function snapshotModeEnabled(env) {
  return env?.WATCHDOG_MODE === "production" &&
    env?.WATCHDOG_SNAPSHOT_ENABLED === "true" &&
    env?.WATCHDOG_FIXTURE_ENABLED !== "true";
}

function fixtureModeEnabled(env) {
  return env?.WATCHDOG_CRON_ENABLED === "true" &&
    env?.WATCHDOG_FIXTURE_ENABLED === "true" &&
    env?.WATCHDOG_MODE === "fixture";
}

/** Dependency injection exists only for deterministic offline tests. */
export function createWatchdogWorker({
  readRegistry = getPrivateRegistry,
  runChecks = runDueChecks,
  makeAdapters = createFixtureAdapters,
  validateFixtures = assertIsolatedFixtureRegistry,
  logger = console,
  readSnapshot = buildUnpublishedSnapshot,
  signSnapshot = publishSignedSnapshot,
  verifyAdmin = verifyAdminAccess,
  makeAdminOverview = summarizeSources,
  renderAdmin = renderAdminDashboard,
  performAdminMutation = executeAdminMutation,
  parseAdminRequest = parseAdminMutationRequest,
  nowMillis = Date.now,
  // Test-only injection; the deployed default has ZERO approved rights refs.
  approvedEvidenceRefs = [],
} = {}) {
  async function runScheduledFixture(controller, env) {
    // An accidental Worker deployment must remain read-only and inert.
    if (!fixtureModeEnabled(env)) return {status: "disabled", checked: 0};
    if (!env.SOURCES_DB || typeof env.SOURCES_DB.prepare !== "function") {
      throw new Error("fixture_d1_not_configured");
    }
    const now = Number.isSafeInteger(controller?.scheduledTime) && controller.scheduledTime >= 0
      ? controller.scheduledTime : Date.now();
    const records = await readRegistry(env.SOURCES_DB);
    validateFixtures(records); // fails closed before any probe is scheduled
    if (records.length === 0) return {status: "empty_fixture_registry", checked: 0};
    const adapters = makeAdapters(records);
    const result = await runChecks({
      db: env.SOURCES_DB,
      adapters,
      now,
      limit: 8,
      concurrency: 4,
      timeoutMs: 12000,
    });
    const totals = {committed:0, probe_failed:0, skipped:0, duplicate:0, lease_busy:0,
      not_due:0, rate_limited_or_ineligible:0, runner_error:0, other:0};
    for (const item of result) {
      if (Object.hasOwn(totals, item.status)) totals[item.status]++;
      else totals.other++;
    }
    // Never log domains, source URLs, playback links, tokens or raw errors.
    logger.info("WATCHDOG_FIXTURE_TICK", JSON.stringify(totals));
    return {status: "fixture_checked", checked: result.length, totals};
  }

  return {
    async fetch(request, env) {
      const url = new URL(request.url);
      if (url.pathname.startsWith("/admin/api/")) {
        // No production writes unless explicitly enabled BEYOND read-only
        // dashboard access, with a verified Access JWT and a strict origin.
        if (!adminAccessConfigured(env) || !adminWritesConfigured(env)) {
          return response({error:"not_found"},404);
        }
        if (request.method !== "POST") return response({error:"method_not_allowed"},405);
        let email;
        try { email = await verifyAdmin(request, env); }
        catch { email = null; }
        if (!email) return response({error:"forbidden"},403);
        if (!env.SOURCES_DB?.prepare) return response({error:"admin_unavailable"},503);
        try {
          const action = await parseAdminRequest(request, env);
          const result = await performAdminMutation(env.SOURCES_DB, action, email, nowMillis());
          return response(result,200);
        } catch (err) {
          if (err instanceof AdminMutationError) {
            return response({error:err.message},err.status);
          }
          if (err instanceof RegistryConflict) {
            return response({error:"revision_conflict"},409);
          }
          if (err?.message === "source_not_found") {
            return response({error:"source_not_found"},404);
          }
          if (["source_not_eligible", "invalid_admin_release",
               "no_approved_previous_healthy_address"].includes(err?.message)) {
            return response({error:"invalid_source_state"},409);
          }
          logger.warn?.("WATCHDOG_ADMIN_WRITE_FAILED");
          return response({error:"admin_unavailable"},503);
        }
      }
      if (request.method !== "GET") return response({error:"method_not_allowed"},405);
      if (url.pathname === "/health") {
        return response({service:"EA-FB Source Watchdog",status:"configured_offline"});
      }
      if (url.pathname === "/admin") {
        // Cloudflare Access-signed identity is checked even if a fronting
        // Access policy was misconfigured. OFF by default in tracked config.
        if (!adminAccessConfigured(env)) return response({error:"not_found"},404);
        const email = await verifyAdmin(request, env);
        if (!email) return response({error:"forbidden"},403);
        if (!env.SOURCES_DB?.prepare) {
          return response({error:"admin_unavailable"},503);
        }
        try {
          const records = await readRegistry(env.SOURCES_DB);
          const html = renderAdmin(makeAdminOverview(records,nowMillis()));
          return new Response(html,{status:200,headers:{
            "content-type":"text/html; charset=utf-8",
            "cache-control":"no-store",
            "x-content-type-options":"nosniff",
            "x-frame-options":"DENY",
            "referrer-policy":"no-referrer",
            "content-security-policy":"default-src 'none'; style-src 'unsafe-inline'; " +
              "frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
          }});
        } catch (_) {
          logger.warn?.("WATCHDOG_ADMIN_READ_FAILED");
          return response({error:"admin_unavailable"},503);
        }
      }
      if (url.pathname !== "/v1/sources" || !snapshotModeEnabled(env)) {
        return response({error:"not_found"},404);
      }
      // This route is read-only, opt-in, and requires a separate private D1
      // plus a configured signing key. The tracked Wrangler config disables it.
      if (!env.SOURCES_DB?.prepare || typeof env.SNAPSHOT_SIGNING_KEY_ID !== "string" ||
          typeof env.SNAPSHOT_SIGNING_PKCS8_B64 !== "string") {
        return response({error:"source_snapshot_unavailable"},503);
      }
      try {
        const now = nowMillis();
        const signed = await signSnapshot(env.SOURCES_DB, env, now, async (db, at) => {
          // Production publication refuses any fixture/mixed test database.
          // Even disabled fixture records must not coexist with real sources.
          const records = await readRegistry(db);
          if (records.some(x => x.id.startsWith("fixture-") ||
              x.config.currentUrl.includes(".example.org"))) {
            throw new Error("fixture_record_in_production_registry");
          }
          const snapshot = await readSnapshot(db, at);
          if (!snapshot || !Array.isArray(snapshot.sources) ||
              snapshot.sources.some(x => x.id?.startsWith("fixture-") ||
                x.baseUrl?.includes(".example.org"))) {
            throw new Error("unsafe_production_snapshot");
          }
          assertReviewedPublication(records, snapshot, approvedEvidenceRefs);
          return snapshot;
        });
        return response(signed);
      } catch (_) {
        // Never expose D1 internals, signing secrets, source URLs or exceptions.
        logger.warn?.("WATCHDOG_SNAPSHOT_PUBLICATION_FAILED");
        return response({error:"source_snapshot_unavailable"},503);
      }
    },
    async scheduled(controller, env) {
      return runScheduledFixture(controller, env);
    },
    runScheduledFixture,
  };
}

export default createWatchdogWorker();
