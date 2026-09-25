/**
 * EA-FB Source Watchdog — isolated Cloudflare Worker entrypoint.
 * SAFE BY DEFAULT: the committed Wrangler config disables all writes. There
 * are NO third-party network adapters or public registry/admin endpoints.
 * Fixture mode requires an entirely fixture-only D1 database.
 */
import { getPrivateRegistry } from "./registry.mjs";
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
    const totals = {committed:0, skipped:0, duplicate:0, runner_error:0, other:0};
    for (const item of result) {
      if (Object.hasOwn(totals, item.status)) totals[item.status]++;
      else totals.other++;
    }
    // Never log domains, source URLs, playback links, tokens or raw errors.
    logger.info("WATCHDOG_FIXTURE_TICK", JSON.stringify(totals));
    return {status: "fixture_checked", checked: result.length, totals};
  }

  return {
    async fetch(request) {
      const url = new URL(request.url);
      if (request.method !== "GET") return response({error:"method_not_allowed"},405);
      if (url.pathname === "/health") {
        return response({service:"EA-FB Source Watchdog",status:"configured_offline"});
      }
      return response({error:"not_found"},404);
    },
    async scheduled(controller, env) {
      return runScheduledFixture(controller, env);
    },
    runScheduledFixture,
  };
}

export default createWatchdogWorker();
