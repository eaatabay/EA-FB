import { commitProbe, getPrivateRegistry, getSource } from "./registry.mjs";
import { dueSources, incidentEligible } from "./scheduler.mjs";

function checkAdapters(adapters) {
  if (!(adapters instanceof Map)) throw new Error("adapter_map_required");
  for (const [id, adapter] of adapters) {
    if (typeof id !== "string" || !adapter || adapter.id !== id ||
        typeof adapter.probe !== "function") throw new Error("invalid_adapter");
  }
}

async function withTimeout(task, timeoutMs) {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 500 || timeoutMs > 60000) {
    throw new Error("invalid_probe_timeout");
  }
  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(task),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error("probe_timeout")), timeoutMs);
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
 * Adapter contract:
 * adapter.probe({source, now}) -> trusted structured result.
 * The adapter implementation must use only authorized source-specific requests,
 * manual redirects and explicit host allowlists. No arbitrary public URL input
 * is accepted here.
 */
export async function runOneSourceCheck({
  db, adapters, sourceId, now, runId, timeoutMs = 12000,
}) {
  checkAdapters(adapters);
  const record = await getSource(db, sourceId);
  if (!record) return {sourceId, status:"not_found"};
  const adapter = adapters.get(sourceId);
  if (!adapter) return {sourceId, status:"adapter_missing"};
  if (!record.config.enabled || record.config.integrationApproved !== true) {
    return {sourceId, status:"not_eligible"};
  }
  try {
    const probe = await withTimeout(
      () => adapter.probe({source: record.config, now}),
      timeoutMs,
    );
    const committed = await commitProbe(db, sourceId, runId, probe, now);
    return {
      sourceId,
      status: committed.skipped ? "skipped" : committed.duplicate ? "duplicate" : "committed",
      detail: committed,
    };
  } catch (err) {
    return {
      sourceId,
      status:"runner_error",
      error:String(err?.message || "unknown").replace(/[^a-zA-Z0-9_.:-]/g,"_").slice(0,96),
    };
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
      out[index] = await runOneSourceCheck({
        db,
        adapters,
        sourceId: record.id,
        now,
        runId: safeRunId(record.id, now, index + slot * 1000),
        timeoutMs,
      });
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
  if (typeof incidentId !== "string" || !/^[a-zA-Z0-9_.:-]{8,96}$/.test(incidentId)) {
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
    runId:"incident-" + incidentId + "-" + sourceId,
    timeoutMs,
  });
}
