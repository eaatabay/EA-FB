import { getPrivateRegistry } from "./registry.mjs";
import { findPlaybackCandidates, prioritizeApprovedOffers, playbackKey } from "./playback-success.mjs";

/**
 * Internal-only central-first lookup. Does NOT expose HTTP, return a stream
 * URL or override release-compiled source rights. Only the actual adapter
 * can resolve a fresh playable URL for the viewer.
 *
 * approvedSourceIds MUST come from a separately verified, release-controlled
 * rights grant list. An admin toggle or a D1 row is not a rights grant.
 */
export async function orderPlaybackOffers({
  db, media, offers, nowMs, approvedSourceIds = [],
  readRegistry = getPrivateRegistry,
  readHistory = findPlaybackCandidates,
} = {}) {
  const key = playbackKey(media);
  if (!db?.prepare || !Array.isArray(offers) ||
      !Number.isSafeInteger(nowMs) || nowMs < 0 ||
      !Array.isArray(approvedSourceIds) ||
      approvedSourceIds.some(id => typeof id !== "string" ||
        !/^[a-z][a-z0-9-]{2,63}$/.test(id) || id.startsWith("fixture-")) ||
      new Set(approvedSourceIds).size !== approvedSourceIds.length)
    throw new Error("invalid_playback_lookup");
  if (approvedSourceIds.length === 0) return [];
  const approved = new Set(approvedSourceIds);
  const records = await readRegistry(db);
  if (!Array.isArray(records)) throw new Error("invalid_source_registry");
  const healthy = new Set(records.filter(record =>
    record && approved.has(record.id) &&
    record.config?.id === record.id &&
    record.config?.enabled === true &&
    record.config?.integrationApproved === true &&
    record.state?.id === record.id &&
    record.state?.status === "healthy" &&
    !record.id.startsWith("fixture-") &&
    ["movie","series","both"].includes(record.config?.mediaKind) &&
    (record.config.mediaKind === "both" ||
      record.config.mediaKind === key.kind)
  ).map(record => record.id));
  if (healthy.size === 0) return [];
  // Fetch history only after rights, current registry health and identity
  // checks. Never persist or share URLs, tokens, headers or user identity.
  const history = await readHistory(db, media, nowMs);
  return prioritizeApprovedOffers(
    offers.map(item => ({
      ...item,
      approved:healthy.has(item?.sourceId) && item?.approved === true,
      healthy:healthy.has(item?.sourceId) && item?.healthy === true,
    })),
    history
  );
}
