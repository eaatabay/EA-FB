-- EA-FB v6 Source Watchdog: private, independent D1 database.
-- Apply ONLY to a NEW test database first. Never attach to the catalog Worker.
-- Registry and audit changes are part of the SAME SQLite statement via triggers.
-- No admin or public API is enabled by this migration.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS registry_meta (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
INSERT OR IGNORE INTO registry_meta(singleton, revision) VALUES (1, 0);

CREATE TABLE IF NOT EXISTS source_registry (
    id TEXT PRIMARY KEY NOT NULL,
    config_json TEXT NOT NULL CHECK (json_valid(config_json)),
    state_json TEXT NOT NULL CHECK (json_valid(state_json)),
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    last_check_run_id TEXT,
    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= 0),
    changed_by TEXT NOT NULL,
    change_reason TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS source_audit (
    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id TEXT NOT NULL,
    source_revision INTEGER NOT NULL,
    previous_config_json TEXT,
    new_config_json TEXT NOT NULL,
    previous_state_json TEXT,
    new_state_json TEXT NOT NULL,
    previous_run_id TEXT,
    run_id TEXT,
    changed_at_ms INTEGER NOT NULL,
    changed_by TEXT NOT NULL,
    reason TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS source_audit_lookup
    ON source_audit(source_id, event_id DESC);

CREATE TRIGGER IF NOT EXISTS source_registry_created
AFTER INSERT ON source_registry
BEGIN
    UPDATE registry_meta SET revision = revision + 1 WHERE singleton = 1;
    INSERT INTO source_audit (
        source_id, source_revision, previous_config_json, new_config_json,
        previous_state_json, new_state_json, previous_run_id, run_id,
        changed_at_ms, changed_by, reason
    ) VALUES (
        NEW.id, NEW.revision, NULL, NEW.config_json, NULL, NEW.state_json,
        NULL, NEW.last_check_run_id, NEW.updated_at_ms, NEW.changed_by,
        NEW.change_reason
    );
END;

CREATE TRIGGER IF NOT EXISTS source_registry_updated
AFTER UPDATE ON source_registry
BEGIN
    UPDATE registry_meta SET revision = revision + 1 WHERE singleton = 1;
    INSERT INTO source_audit (
        source_id, source_revision, previous_config_json, new_config_json,
        previous_state_json, new_state_json, previous_run_id, run_id,
        changed_at_ms, changed_by, reason
    ) VALUES (
        NEW.id, NEW.revision, OLD.config_json, NEW.config_json,
        OLD.state_json, NEW.state_json, OLD.last_check_run_id,
        NEW.last_check_run_id, NEW.updated_at_ms, NEW.changed_by,
        NEW.change_reason
    );
END;
