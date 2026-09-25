"""SQLite-level integration checks for the isolated D1 source registry migration.

Runs offline; Cloudflare D1 is SQLite-based, but a real D1 deployment still
requires its own binding/migration verification.
"""
import json
import sqlite3
import unittest
from pathlib import Path

MIGRATION = Path(__file__).resolve().parents[1] / "migrations" / "0001_registry.sql"


class RegistryMigrationTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys = ON")
        self.db.executescript(MIGRATION.read_text(encoding="utf-8"))

    def tearDown(self):
        self.db.close()

    def register(self, source="licensed-demo"):
        self.db.execute(
            """INSERT INTO source_registry
            (id,config_json,state_json,revision,last_check_run_id,
             updated_at_ms,changed_by,change_reason)
            VALUES(?,?,?,?,?,?,?,?)""",
            (source, json.dumps({"id": source}),
             json.dumps({"id": source, "status": "degraded"}), 0, None,
             1000, "admin:tester", "source_registered"),
        )
        self.db.commit()

    def counts(self):
        return (
            self.db.execute("SELECT revision FROM registry_meta WHERE singleton=1").fetchone()[0],
            self.db.execute("SELECT COUNT(*) FROM source_audit").fetchone()[0],
            self.db.execute("SELECT COUNT(*) FROM source_probe_runs").fetchone()[0],
        )

    def update_probe(self, run_id, expected_revision, when=2000):
        result = self.db.execute(
            """UPDATE source_registry
            SET state_json=?,revision=revision+1,last_check_run_id=?,
                updated_at_ms=?,changed_by=?,change_reason=?
            WHERE id=? AND revision=?""",
            (json.dumps({"id": "licensed-demo", "status": "healthy"}),
             run_id, when, "watchdog:runner", "probe:healthy",
             "licensed-demo", expected_revision),
        )
        self.db.commit()
        return result.rowcount

    def test_migration_idempotent_and_empty_registry(self):
        self.assertEqual(self.counts(), (0, 0, 0))
        self.db.executescript(MIGRATION.read_text(encoding="utf-8"))
        self.assertEqual(self.counts(), (0, 0, 0))

    def test_registration_and_cas_audit_in_same_statement(self):
        self.register()
        self.assertEqual(self.counts(), (1, 1, 0))
        self.assertEqual(self.update_probe("probe-00000001", 0), 1)
        self.assertEqual(self.counts(), (2, 2, 1))
        audit = self.db.execute(
            "SELECT previous_state_json,new_state_json,previous_run_id,run_id "
            "FROM source_audit ORDER BY event_id DESC LIMIT 1"
        ).fetchone()
        self.assertEqual(json.loads(audit[0])["status"], "degraded")
        self.assertEqual(json.loads(audit[1])["status"], "healthy")
        self.assertIsNone(audit[2])
        self.assertEqual(audit[3], "probe-00000001")

    def test_duplicate_run_after_other_run_rolls_back_everything(self):
        self.register()
        self.assertEqual(self.update_probe("probe-00000001", 0), 1)
        self.assertEqual(self.update_probe("probe-00000002", 1, 3000), 1)
        self.assertEqual(self.counts(), (3, 3, 2))
        with self.assertRaises(sqlite3.IntegrityError):
            self.update_probe("probe-00000001", 2, 4000)
        self.db.rollback()
        self.assertEqual(self.counts(), (3, 3, 2))
        row = self.db.execute(
            "SELECT revision,last_check_run_id FROM source_registry "
            "WHERE id='licensed-demo'"
        ).fetchone()
        self.assertEqual(row, (2, "probe-00000002"))

    def test_stale_cas_does_not_create_audit_or_revision(self):
        self.register()
        self.assertEqual(self.update_probe("probe-00000001", 0), 1)
        self.assertEqual(self.update_probe("probe-00000003", 0), 0)
        self.assertEqual(self.counts(), (2, 2, 1))

    def test_duplicate_source_and_bad_json_are_rejected(self):
        self.register()
        with self.assertRaises(sqlite3.IntegrityError):
            self.register()
        self.db.rollback()
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(
                """INSERT INTO source_registry
                (id,config_json,state_json,revision,updated_at_ms,
                 changed_by,change_reason) VALUES(?,?,?,?,?,?,?)""",
                ("another", "{not-json}", "{}", 0, 1000,
                 "admin:tester", "source_registered"),
            )
        self.db.rollback()
        self.assertEqual(self.counts(), (1, 1, 0))

    def test_admin_update_keeps_run_id_and_creates_audit(self):
        self.register()
        self.update_probe("probe-00000001", 0)
        self.db.execute(
            """UPDATE source_registry SET config_json=?,revision=revision+1,
               changed_by=?,change_reason=?,updated_at_ms=?
               WHERE id=? AND revision=?""",
            (json.dumps({"id":"licensed-demo","enabled":False}),
             "admin:tester", "admin:disabled", 3500,
             "licensed-demo", 1),
        )
        self.db.commit()
        self.assertEqual(self.counts(), (3, 3, 1))
        last = self.db.execute(
            "SELECT changed_by,reason FROM source_audit ORDER BY event_id DESC LIMIT 1"
        ).fetchone()
        self.assertEqual(last, ("admin:tester", "admin:disabled"))


if __name__ == "__main__":
    unittest.main()
