import { HEALTH } from "./policy.mjs";

/**
 * Pure scheduling policy for Source Watchdog.
 * No network, no database writes, no Cloudflare deployment side effects.
 */
const PRIORITY = Object.freeze({
  [HEALTH.DEGRADED]: 0,
  [HEALTH.QUARANTINED]: 1,
  [HEALTH.HEALTHY]: 2,
});

export function dueSources(records, now, limit = 8) {
  if (!Array.isArray(records) || !Number.isSafeInteger(now) || now < 0 ||
      !Number.isSafeInteger(limit) || limit < 1 || limit > 64) {
    throw new Error("invalid_schedule_input");
  }
  return records
    .filter(record => {
      const config = record?.config, state = record?.state;
      if (!config || !state || !config.enabled || config.integrationApproved !== true) return false;
      // Unknown/corrupt statuses and timestamps must never become runnable.
      if (typeof state.status !== 'string' ||
          (typeof state.status !== 'string' ||
       !Object.hasOwn(PRIORITY, state.status))) return false;
      return Number.isSafeInteger(state.nextCheckAt) &&
        state.nextCheckAt >= 0 && state.nextCheckAt <= now;
    })
    .sort((a,b) => {
      const pa = PRIORITY[a.state.status] ?? 99;
      const pb = PRIORITY[b.state.status] ?? 99;
      if (pa !== pb) return pa - pb;
      if (a.state.nextCheckAt !== b.state.nextCheckAt) return a.state.nextCheckAt - b.state.nextCheckAt;
      return a.id.localeCompare(b.id);
    })
    .slice(0, limit);
}

export function incidentEligible(record, now, minSpacingMs = 5 * 60 * 1000) {
  if (!record?.config || !record?.state || !Number.isSafeInteger(now) || now < 0 ||
      !Number.isSafeInteger(minSpacingMs) || minSpacingMs < 60000) return false;
  const { config, state } = record;
  if (!config.enabled || config.integrationApproved !== true ||
      !Object.hasOwn(PRIORITY, state.status)) return false;
  if (state.lastCheckedAt == null) return true;
  if (!Number.isSafeInteger(state.lastCheckedAt) ||
      state.lastCheckedAt < 0) return false;
  return now - state.lastCheckedAt >= minSpacingMs;
}
