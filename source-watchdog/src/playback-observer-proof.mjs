import {playbackKey} from "./playback-success.mjs";

const NONCE = /^[A-Za-z0-9_-]{22,64}$/;
const SOURCE = /^[a-z][a-z0-9-]{2,63}$/;
const VARIANT = /^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,63}$/;
const LANGUAGE = /^[a-z]{2,3}(?:-[a-z]{2,8})?$/;
const MAX_AGE_MS = 60_000;

/**
 * Authenticates the identity and freshness of an independently trusted
 * playback observer, NOT a raw client assertion that a stream started.
 * A server-side observer must hold the HMAC key and establish real playback
 * independently. Never bundle this key in the .cs3 or expose a signing route.
 *
 * All signatures cover a minimal canonical tuple: no URL, headers, session,
 * IP, user ID or device ID. D1 replay receipt is inserted atomically.
 */
export function playbackObserverMessage(event) {
  const key = playbackKey(event?.media);
  if (!["success","failure"].includes(event?.outcome) ||
      !SOURCE.test(event?.source?.sourceId ?? "") ||
      !VARIANT.test(event?.source?.variantId ?? "") ||
      !LANGUAGE.test(event?.source?.audioLanguage ?? "und") ||
      (event?.source?.quality != null &&
        (!Number.isSafeInteger(event.source.quality) ||
          event.source.quality < 1 || event.source.quality > 4320)) ||
      !NONCE.test(event?.proof?.eventId ?? "") ||
      !Number.isSafeInteger(event?.proof?.observedAtMs) ||
      event.proof.observedAtMs < 0) {
    throw new Error("invalid_observer_message");
  }
  return JSON.stringify([
    "ea-fb-playback-observer-v1",
    event.proof.eventId, event.proof.observedAtMs,
    event.outcome, key.kind, key.tmdbId, key.season, key.episode,
    event.source.sourceId, event.source.variantId,
    event.source.audioLanguage ?? "und", event.source.quality ?? null,
  ]);
}

function signatureBytes(base64url) {
  if (typeof base64url !== "string" ||
      !/^[A-Za-z0-9_-]{43}$/.test(base64url))
    throw new Error("invalid_observer_signature");
  const normalized = base64url.replace(/-/g,"+").replace(/_/g,"/");
  const binary = atob(normalized + "=");
  if (binary.length !== 32) throw new Error("invalid_observer_signature");
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}

/**
 * Build once for a server-side, nonexportable HMAC key; callers must load
 * keyBytes from a secret manager, not source control or client configuration.
 */
export async function createPlaybackObserverVerifier(db, keyBytes, subtle = crypto.subtle) {
  if (!db?.prepare || !(keyBytes instanceof Uint8Array) ||
      keyBytes.byteLength < 32 || !subtle?.importKey || !subtle?.verify)
    throw new Error("observer_verifier_not_configured");
  const key = await subtle.importKey("raw", keyBytes,
    {name:"HMAC",hash:"SHA-256"}, false, ["verify"]);
  return async function verifyEvidence(event, nowMs) {
    if (!Number.isSafeInteger(nowMs) || nowMs < 0 ||
        !Number.isSafeInteger(nowMs + MAX_AGE_MS))
      throw new Error("invalid_observer_clock");
    const message = playbackObserverMessage(event);
    const observedAtMs = event.proof.observedAtMs;
    if (observedAtMs > nowMs || nowMs - observedAtMs > MAX_AGE_MS)
      throw new Error("expired_observer_message");
    const signature = signatureBytes(event.proof.signature);
    const verified = await subtle.verify("HMAC", key, signature,
      new TextEncoder().encode(message));
    if (!verified) throw new Error("invalid_observer_signature");
    // D1 PRIMARY KEY makes concurrent duplicate event IDs fail closed.
    // A replay is consumed before any downstream source/health check.
    const result = await db.prepare(`INSERT INTO playback_observer_receipts
      (event_id, expires_at_ms) VALUES (?, ?) ON CONFLICT(event_id) DO NOTHING`)
      .bind(event.proof.eventId, nowMs + MAX_AGE_MS).run();
    if (result?.meta?.changes !== 1)
      throw new Error("replayed_observer_message");
    const media = playbackKey(event.media);
    return {verified:true, outcome:event.outcome,
      sourceId:event.source.sourceId, variantId:event.source.variantId,
      mediaKind:media.kind, tmdbId:media.tmdbId,
      season:media.season, episode:media.episode, observedAtMs};
  };
}
