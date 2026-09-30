-- V42 staging-only cache for AI-localized episode titles.
-- This stores translation memory only; it is not a film/series catalog.

CREATE TABLE IF NOT EXISTS episode_title_cache (
  series_id INTEGER NOT NULL,
  season INTEGER NOT NULL,
  episode INTEGER NOT NULL,
  source_language TEXT NOT NULL,
  original_title TEXT NOT NULL,
  turkish_title TEXT NOT NULL,
  translation_version TEXT NOT NULL,
  model TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (series_id, season, episode)
);

CREATE INDEX IF NOT EXISTS idx_episode_title_cache_version
  ON episode_title_cache (translation_version);

CREATE TABLE IF NOT EXISTS episode_title_locks (
  lock_key TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_episode_title_locks_expiry
  ON episode_title_locks (expires_at);
