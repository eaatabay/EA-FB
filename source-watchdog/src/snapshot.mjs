import { HEALTH, normalizedHttpsUrl, validateSource } from "./policy.mjs";
import { validSnapshot } from "./snapshot-crypto.mjs";

/**
 * Client-safe, read-only registry view. This is only a pure serializer; the
 * admin-only durable store, signed delivery and device-side cache come later.
 * No diagnostic errors, test paths, credentials or unapproved candidates leak.
 */
export function buildClientSnapshot(sources, states, revision, now, options={}) {
  if (!Array.isArray(sources) || !Array.isArray(states) ||
      !Number.isSafeInteger(revision) || revision < 0 ||
      !Number.isFinite(now) || now < 0) throw new Error("invalid_snapshot_input");
  const maxAgeMs = options.maxHealthAgeMs ?? 12 * 3_600_000;
  const ttlMs = options.ttlMs ?? 15 * 60_000;
  if (!(maxAgeMs > 0 && maxAgeMs <= 48 * 3_600_000) ||
      !(ttlMs > 0 && ttlMs <= 60 * 60_000)) throw new Error("invalid_snapshot_lifetime");
  const stateById = new Map();
  for (const state of states) {
    if (!state || typeof state.id !== "string" || stateById.has(state.id)) {
      throw new Error("duplicate_or_invalid_state");
    }
    stateById.set(state.id, state);
  }
  const published = [];
  const ids = new Set();
  for (const rawSource of sources) {
    const source = validateSource(rawSource);
    if (ids.has(source.id)) throw new Error("duplicate_source");
    ids.add(source.id);
    const state = stateById.get(source.id);
    if (!source.enabled || source.integrationApproved !== true ||
        !state || state.status !== HEALTH.HEALTHY ||
        !Number.isFinite(state.lastCheckedAt) || state.lastCheckedAt > now ||
        now - state.lastCheckedAt > maxAgeMs) continue;
    const current = normalizedHttpsUrl(state.currentUrl);
    if (!current || !source.verifiedDomains.includes(new URL(current).hostname)) continue;
    if (!Number.isSafeInteger(source.adapterVersion) || source.adapterVersion < 1) {
      throw new Error("invalid_adapter_version");
    }
    published.push({
      id: source.id,
      mediaKind: source.mediaKind,
      baseUrl: current,
      adapterVersion: source.adapterVersion,
    });
  }
  published.sort((a,b) => a.id.localeCompare(b.id));
  return {
    schemaVersion: 1,
    revision,
    generatedAt: now,
    expiresAt: now + ttlMs,
    sources: published,
  };
}

/**
 * Legacy pure replacement helper for ALREADY signature-verified snapshots.
 * It does NOT verify signatures: callers must use verifySnapshot() first.
 *
 * A new signed generation may refresh TTL without changing D1 revision.
 * Match the Ed25519 verifier's revision+generatedAt replay rules exactly.
 * Fail closed on malformed/partial payloads instead of shallow URL checks.
 */
export function canReplaceSnapshot(current, incoming, now, signatureVerified = false) {
  if (signatureVerified !== true || !validSnapshot(incoming, now)) return false;
  if (current == null) return true;
  if (!Number.isSafeInteger(current.revision) || current.revision < 0 ||
      !Number.isSafeInteger(current.generatedAt) || current.generatedAt < 0) {
    return false;
  }
  if (incoming.revision < current.revision) return false;
  if (incoming.revision === current.revision &&
      incoming.generatedAt <= current.generatedAt) return false;
  return true;
}
