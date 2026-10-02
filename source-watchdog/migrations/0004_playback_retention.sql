-- Optional offline-only schema extension for bounded expired-candidate cleanup.
-- Apply after 0003 on a separately authorized, isolated staging D1.
-- Does not expose or activate any HTTP route, timer or data collection.
CREATE INDEX IF NOT EXISTS playback_success_expiry
ON playback_success(expires_at_ms);
