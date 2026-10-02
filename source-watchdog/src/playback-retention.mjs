/**
 * Explicit internal maintenance operation; not scheduled or deployed by
 * default. Bounded cleanup of expired, non-secret source identities.
 * Requires the isolated private D1 binding and migration 0004.
 */
export async function pruneExpiredPlaybackCandidates(db, nowMs, limit = 250) {
  if (!db?.prepare || !Number.isSafeInteger(nowMs) || nowMs < 0 ||
      !Number.isSafeInteger(limit) || limit < 1 || limit > 1000)
    throw new Error("invalid_playback_retention");
  const result = await db.prepare(`DELETE FROM playback_success
    WHERE (media_kind,tmdb_id,season,episode,source_id,variant_id) IN (
      SELECT media_kind,tmdb_id,season,episode,source_id,variant_id
      FROM playback_success WHERE expires_at_ms <= ?
      ORDER BY expires_at_ms ASC LIMIT ?
    )`).bind(nowMs,limit).run();
  return {deleted:result?.meta?.changes ?? 0};
}
