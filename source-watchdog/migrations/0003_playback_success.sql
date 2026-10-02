-- EA-FB: central playback success candidates (no playable URLs or credentials).
-- Separate private D1 database; no public route or telemetry enabled by migration.
-- Apply only to a new isolated staging D1 database after 0001 and 0002.
CREATE TABLE IF NOT EXISTS playback_success (
  media_kind TEXT NOT NULL CHECK (media_kind IN ('movie', 'series')),
  tmdb_id INTEGER NOT NULL CHECK (tmdb_id > 0),
  season INTEGER NOT NULL DEFAULT -1 CHECK (season >= -1),
  episode INTEGER NOT NULL DEFAULT -1 CHECK (episode >= -1),
  source_id TEXT NOT NULL CHECK (length(source_id) BETWEEN 3 AND 64),
  variant_id TEXT NOT NULL CHECK (length(variant_id) BETWEEN 1 AND 64),
  audio_language TEXT NOT NULL DEFAULT 'und' CHECK (length(audio_language) BETWEEN 2 AND 12),
  quality INTEGER CHECK (quality IS NULL OR quality BETWEEN 1 AND 4320),
  confirmed_count INTEGER NOT NULL DEFAULT 1 CHECK (confirmed_count > 0),
  last_confirmed_at_ms INTEGER NOT NULL CHECK (last_confirmed_at_ms >= 0),
  expires_at_ms INTEGER NOT NULL CHECK (expires_at_ms > last_confirmed_at_ms),
  PRIMARY KEY (media_kind, tmdb_id, season, episode, source_id, variant_id),
  CHECK ((media_kind = 'movie' AND season = -1 AND episode = -1) OR
         (media_kind = 'series' AND season >= 0 AND episode > 0))
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS playback_success_fresh
ON playback_success(media_kind, tmdb_id, season, episode, expires_at_ms DESC);
