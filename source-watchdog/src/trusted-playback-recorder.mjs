import { getSource } from "./registry.mjs";
import { playbackKey, recordPlaybackSuccess, expirePlaybackCandidate }
  from "./playback-success.mjs";

/**
 * Internal-only trust boundary for playback feedback. No public HTTP route,
 * no client-asserted 'played' boolean, no persisted URLs or user identifiers.
 *
 * The independent verifier must authenticate a trusted playback observer,
 * validate an actual media-start signal (not mere link discovery), enforce
 * event freshness and anti-replay, and return its verified identity. This
 * module checks that identity against the submitted record and live registry.
 * Default: no verifier => no writes. Production wiring is deliberately absent.
 */
export async function recordVerifiedPlayback({
  db, event, nowMs, approvedSourceIds = [],
  verifyEvidence, readSource = getSource,
  writeSuccess = recordPlaybackSuccess,
  expireCandidate = expirePlaybackCandidate,
} = {}) {
  if (!db?.prepare || !Number.isSafeInteger(nowMs) || nowMs < 0 ||
      !Array.isArray(approvedSourceIds) ||
      approvedSourceIds.some(id => typeof id !== "string" ||
        !/^[a-z][a-z0-9-]{2,63}$/.test(id) || id.startsWith("fixture-")) ||
      new Set(approvedSourceIds).size !== approvedSourceIds.length ||
      typeof verifyEvidence !== "function" || !event ||
      !["success","failure"].includes(event.outcome)) {
    throw new Error("untrusted_playback_event");
  }
  const key = playbackKey(event.media);
  const source = event.source;
  if (typeof source?.sourceId !== "string" ||
      !approvedSourceIds.includes(source.sourceId) ||
      source.sourceId.startsWith("fixture-")) {
    throw new Error("source_not_permitted");
  }
  // Verify before any database lookup or write. The verifier MUST reject
  // replays and unsigned/expired observations. Only its identity is trusted.
  const proof = await verifyEvidence(event, nowMs);
  if (proof?.verified !== true ||
      proof.outcome !== event.outcome ||
      proof.sourceId !== source.sourceId ||
      proof.variantId !== source.variantId ||
      proof.mediaKind !== key.kind ||
      proof.tmdbId !== key.tmdbId ||
      proof.season !== key.season ||
      proof.episode !== key.episode ||
      !Number.isSafeInteger(proof.observedAtMs) ||
      proof.observedAtMs < 0 ||
      proof.observedAtMs > nowMs ||
      nowMs - proof.observedAtMs > 60_000) {
    throw new Error("untrusted_playback_evidence");
  }
  const record = await readSource(db, source.sourceId);
  if (!record || record.id !== source.sourceId ||
      record.config?.id !== source.sourceId ||
      record.state?.id !== source.sourceId ||
      record.config?.enabled !== true ||
      record.config?.integrationApproved !== true ||
      record.state?.status !== "healthy" ||
      !["movie","series","both"].includes(record.config?.mediaKind) ||
      (record.config.mediaKind !== "both" &&
        record.config.mediaKind !== key.kind)) {
    throw new Error("source_not_healthy");
  }
  if (event.outcome === "failure") {
    // A failure only expires a previously recorded candidate, never the
    // whole source. Other viewers can retry independent healthy variants.
    return expireCandidate(db, event.media, source, nowMs);
  }
  return writeSuccess(db, event.media, source, nowMs);
}
