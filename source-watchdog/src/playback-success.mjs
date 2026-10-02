/**
 * Private D1 mapping of previously confirmed playable source identities.
 * NEVER persist raw playback URLs, signed URLs, cookies, headers, device IDs,
 * IP addresses or credentials. This is not a public API and does not itself
 * confirm that a link will still work for a different viewer.
 * Call recordPlaybackSuccess only after an authenticated, trusted playback
 * confirmation. Client-provided "link found" events are NOT confirmation.
 */
const SOURCE_ID = /^[a-z][a-z0-9-]{2,63}$/;
const VARIANT_ID = /^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,63}$/;
const LANGUAGE = /^[a-z]{2,3}(?:-[a-z]{2,8})?$/;
const MAX_TTL_MS = 6 * 60 * 60 * 1000;

function integer(n, min, max = Number.MAX_SAFE_INTEGER) {
  return Number.isSafeInteger(n) && n >= min && n <= max;
}

export function playbackKey(input) {
  if (!input || !['movie', 'series'].includes(input.kind) ||
      !integer(input.tmdbId, 1)) throw new Error('invalid_playback_key');
  if (input.kind === 'movie') {
    if (input.season != null || input.episode != null)
      throw new Error('invalid_playback_key');
    return {kind:'movie', tmdbId:input.tmdbId, season:-1, episode:-1};
  }
  if (!integer(input.season, 0) || !integer(input.episode, 1))
    throw new Error('invalid_playback_key');
  return {kind:'series', tmdbId:input.tmdbId,
    season:input.season, episode:input.episode};
}

function requireDb(db) {
  if (!db || typeof db.prepare !== 'function') throw new Error('d1_not_configured');
  return db;
}

function validatedSource(input) {
  if (!SOURCE_ID.test(input?.sourceId ?? '') ||
      !VARIANT_ID.test(input?.variantId ?? '') ||
      !LANGUAGE.test(input?.audioLanguage ?? 'und') ||
      (input.quality != null && !integer(input.quality, 1, 4320)))
    throw new Error('invalid_source_identity');
  return {sourceId:input.sourceId, variantId:input.variantId,
    audioLanguage:input.audioLanguage ?? 'und', quality:input.quality ?? null};
}

export async function recordPlaybackSuccess(db, media, source, nowMs,
                                            ttlMs = 30 * 60_000) {
  requireDb(db);
  const key = playbackKey(media);
  const candidate = validatedSource(source);
  if (!integer(nowMs, 0) || !integer(ttlMs, 1_000, MAX_TTL_MS) ||
      !Number.isSafeInteger(nowMs + ttlMs))
    throw new Error('invalid_success_lifetime');
  const expires = nowMs + ttlMs;
  // Source approval/health MUST be verified by the caller; this function
  // deliberately accepts no URL and is not wired to an unauthenticated route.
  const result = await db.prepare(`INSERT INTO playback_success
    (media_kind, tmdb_id, season, episode, source_id, variant_id,
     audio_language, quality, confirmed_count, last_confirmed_at_ms, expires_at_ms)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
    ON CONFLICT(media_kind, tmdb_id, season, episode, source_id, variant_id)
    DO UPDATE SET
      confirmed_count = playback_success.confirmed_count + 1,
      audio_language = excluded.audio_language,
      quality = excluded.quality,
      last_confirmed_at_ms = excluded.last_confirmed_at_ms,
      expires_at_ms = excluded.expires_at_ms
    WHERE excluded.last_confirmed_at_ms >= playback_success.last_confirmed_at_ms`)
    .bind(key.kind, key.tmdbId, key.season, key.episode,
      candidate.sourceId, candidate.variantId, candidate.audioLanguage,
      candidate.quality, nowMs, expires).run();
  return {stored:result?.meta?.changes === 1};
}

export async function findPlaybackCandidates(db, media, nowMs, limit = 5) {
  requireDb(db);
  const key = playbackKey(media);
  if (!integer(nowMs, 0) || !integer(limit, 1, 20))
    throw new Error('invalid_candidate_query');
  const rows = await db.prepare(`SELECT source_id, variant_id, audio_language,
    quality, confirmed_count, last_confirmed_at_ms
    FROM playback_success
    WHERE media_kind = ? AND tmdb_id = ? AND season = ? AND episode = ?
      AND expires_at_ms > ?
    ORDER BY (audio_language = 'tr') DESC,
      last_confirmed_at_ms DESC, confirmed_count DESC LIMIT ?`)
    .bind(key.kind, key.tmdbId, key.season, key.episode, nowMs, limit).all();
  return (rows.results ?? []).map(row => ({
    sourceId:row.source_id, variantId:row.variant_id,
    audioLanguage:row.audio_language, quality:row.quality,
    confirmedCount:row.confirmed_count,
    lastConfirmedAtMs:row.last_confirmed_at_ms,
  }));
}

/**
 * Mark a previously confirmed candidate as stale when a trusted resolver or
 * playback checker verifies it no longer works. This is not a client-facing
 * write endpoint. Failure does not delete the provider or block other variants.
 */
export async function expirePlaybackCandidate(db, media, source, nowMs) {
  requireDb(db);
  const key = playbackKey(media);
  const candidate = validatedSource(source);
  if (!integer(nowMs, 0)) throw new Error('invalid_failure_clock');
  const result = await db.prepare(`UPDATE playback_success
    SET expires_at_ms = ?
    WHERE media_kind = ? AND tmdb_id = ? AND season = ? AND episode = ?
      AND source_id = ? AND variant_id = ? AND expires_at_ms > ? AND last_confirmed_at_ms < ?`)
    .bind(nowMs, key.kind, key.tmdbId, key.season, key.episode,
      candidate.sourceId, candidate.variantId, nowMs, nowMs).run();
  return {expired:(result?.meta?.changes ?? 0) > 0};
}

/**
 * Prioritize central success history only among currently reviewed/healthy
 * provider variants supplied by a trusted registry. Never turn historical
 * IDs into playable URLs; the adapter must freshly resolve the offer.
 */
export function prioritizeApprovedOffers(offers, history) {
  if (!Array.isArray(offers) || !Array.isArray(history))
    throw new Error('invalid_candidate_lists');
  const rank = new Map(history.map((item, i) =>
    [item.sourceId + '\\u0000' + item.variantId, i]));
  return offers.filter(item =>
    item && item.approved === true && item.healthy === true &&
    SOURCE_ID.test(item.sourceId ?? '') &&
    VARIANT_ID.test(item.variantId ?? '')
  ).map((item, i) => ({
    item, i,
    rank:rank.get(item.sourceId + '\\u0000' + item.variantId) ?? Number.MAX_SAFE_INTEGER,
  })).sort((a,b) => a.rank - b.rank || a.i - b.i)
    .map(entry => entry.item);
}
