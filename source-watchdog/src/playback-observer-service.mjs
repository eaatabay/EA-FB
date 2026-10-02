import {createPlaybackObserverVerifier} from "./playback-observer-proof.mjs";
import {recordVerifiedPlayback} from "./trusted-playback-recorder.mjs";

/**
 * Internal, unexposed orchestration. A trusted independent playback observer
 * must submit HMAC evidence; the .cs3 client never receives the signing key.
 * This service verifies authenticity, one-time use, source approval and
 * current health before updating a short-lived candidate in D1.
 *
 * IMPORTANT: An HMAC proves observer origin, not that a viewer played media.
 * The signing observer must itself verify media-start and is NOT implemented.
 * Default release has no observer key and no approved source grants.
 */
export async function acceptTrustedPlaybackObservation({
  db, event, nowMs, keyBytes, approvedSourceIds = [],
  subtle,
  createVerifier = createPlaybackObserverVerifier,
  recordEvent = recordVerifiedPlayback,
} = {}) {
  if (!db?.prepare || !(keyBytes instanceof Uint8Array) ||
      keyBytes.byteLength < 32 || !Array.isArray(approvedSourceIds) ||
      approvedSourceIds.length === 0)
    throw new Error("playback_observer_disabled");
  const verifier = await createVerifier(db,keyBytes,subtle);
  return recordEvent({db,event,nowMs,approvedSourceIds,
    verifyEvidence:verifier});
}
