-- Private Source Watchdog D1 database only. Never apply to catalog Worker.
-- A durable one-runner-per-source lock protects Cron/incident overlaps.
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS source_probe_leases (
  source_id TEXT PRIMARY KEY REFERENCES source_registry(id) ON DELETE CASCADE,
  lease_token TEXT NOT NULL UNIQUE,
  acquired_at_ms INTEGER NOT NULL CHECK(acquired_at_ms >= 0),
  expires_at_ms INTEGER NOT NULL CHECK(expires_at_ms > acquired_at_ms)
);
CREATE INDEX IF NOT EXISTS source_probe_leases_expiry ON source_probe_leases(expires_at_ms);
