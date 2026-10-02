-- Replay protection for independently verified playback-observer messages.
-- No stream URLs, device identifiers, cookies, tokens or user identities.
-- Only apply to isolated staging D1 after explicit authorization.
CREATE TABLE IF NOT EXISTS playback_observer_receipts (
  event_id TEXT PRIMARY KEY CHECK (length(event_id) BETWEEN 22 AND 64),
  expires_at_ms INTEGER NOT NULL CHECK (expires_at_ms >= 0)
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS playback_observer_receipts_expiry
ON playback_observer_receipts(expires_at_ms);
