"""Offline SQLite checks for the private central playback success D1 schema."""
import sqlite3
import unittest
from pathlib import Path

MIGRATION = Path(__file__).resolve().parents[1] / "migrations" / "0003_playback_success.sql"
RETENTION = Path(__file__).resolve().parents[1] / "migrations" / "0004_playback_retention.sql"

class PlaybackSuccessMigrationTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.executescript(MIGRATION.read_text(encoding="utf-8"))
        self.db.executescript(RETENTION.read_text(encoding="utf-8"))

    def tearDown(self):
        self.db.close()

    def insert(self, kind="series", tmdb=123, season=3, episode=2,
               source="source-a", variant="tr-1080", confirmed=1000, expires=2000):
        return self.db.execute("""INSERT INTO playback_success
          (media_kind,tmdb_id,season,episode,source_id,variant_id,
           audio_language,quality,confirmed_count,last_confirmed_at_ms,expires_at_ms)
          VALUES (?,?,?,?,?,?,?,?,1,?,?)""",
          (kind,tmdb,season,episode,source,variant,"tr",1080,confirmed,expires))

    def test_schema_never_persists_stream_urls_or_user_identity(self):
        columns = {row[1] for row in self.db.execute(
            "PRAGMA table_info(playback_success)")}
        self.assertFalse(columns & {
            "url", "stream_url", "playback_url", "cookie", "headers",
            "token", "device_id", "user_id", "ip_address"})
        self.assertTrue({"media_kind", "tmdb_id", "season", "episode",
            "source_id", "variant_id", "expires_at_ms"} <= columns)

    def test_reapply_is_safe(self):
        self.db.executescript(MIGRATION.read_text(encoding="utf-8"))
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM playback_success").fetchone()[0],0)

    def test_episode_and_film_isolation(self):
        self.insert()
        self.insert(kind="movie",season=-1,episode=-1)
        self.insert(episode=3)
        self.assertEqual(self.db.execute("""SELECT COUNT(*) FROM playback_success
          WHERE media_kind='series' AND tmdb_id=123 AND season=3
          AND episode=2 AND expires_at_ms>1500""").fetchone()[0],1)
        self.assertEqual(self.db.execute("""SELECT COUNT(*) FROM playback_success
          WHERE media_kind='series' AND tmdb_id=123 AND season=3
          AND episode=2 AND expires_at_ms>2000""").fetchone()[0],0)

    def test_bounded_retention_keeps_unexpired_candidates(self):
        self.insert(episode=2, expires=2000)
        self.insert(episode=3, expires=3000)
        self.insert(episode=4, expires=9000)
        cleanup = """DELETE FROM playback_success WHERE
          (media_kind,tmdb_id,season,episode,source_id,variant_id) IN
          (SELECT media_kind,tmdb_id,season,episode,source_id,variant_id
           FROM playback_success WHERE expires_at_ms<=?
           ORDER BY expires_at_ms ASC LIMIT ?)"""
        self.assertEqual(self.db.execute(cleanup, (4000, 1)).rowcount, 1)
        self.assertEqual(self.db.execute(cleanup, (4000, 1)).rowcount, 1)
        self.assertEqual(self.db.execute(cleanup, (4000, 1)).rowcount, 0)
        self.assertEqual(self.db.execute(
            "SELECT episode FROM playback_success").fetchone()[0], 4)

    def test_invalid_media_keys_rejected(self):
        for values in [
            ("movie",123,0,-1), ("movie",123,-1,1),
            ("series",123,-1,2), ("series",123,3,0),
            ("series",0,3,2), ("live",123,-1,-1),
        ]:
            with self.assertRaises(sqlite3.IntegrityError):
                self.insert(*values)
            self.db.rollback()

    def test_invalid_ttl_and_duplicate_rejected(self):
        self.insert()
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert()
        self.db.rollback()
        with self.assertRaises(sqlite3.IntegrityError):
            self.insert(variant="expired",confirmed=1000,expires=1000)
        self.db.rollback()

    def test_atomic_upsert_and_stale_failure(self):
        self.insert()
        result=self.db.execute("""INSERT INTO playback_success
          (media_kind,tmdb_id,season,episode,source_id,variant_id,
           audio_language,quality,confirmed_count,last_confirmed_at_ms,expires_at_ms)
          VALUES ('series',123,3,2,'source-a','tr-1080','tr',1080,1,1200,2200)
          ON CONFLICT(media_kind,tmdb_id,season,episode,source_id,variant_id)
          DO UPDATE SET confirmed_count=playback_success.confirmed_count+1,
            last_confirmed_at_ms=excluded.last_confirmed_at_ms,
            expires_at_ms=excluded.expires_at_ms
          WHERE excluded.last_confirmed_at_ms >= playback_success.last_confirmed_at_ms""")
        self.assertEqual(result.rowcount,1)
        row=self.db.execute("""SELECT confirmed_count,last_confirmed_at_ms,expires_at_ms
          FROM playback_success""").fetchone()
        self.assertEqual(row,(2,1200,2200))
        self.assertEqual(self.db.execute("""UPDATE playback_success
          SET expires_at_ms=1300 WHERE media_kind='series' AND tmdb_id=123
          AND season=3 AND episode=2 AND source_id='source-a'
          AND variant_id='tr-1080' AND expires_at_ms>1300""").rowcount,1)
        self.assertEqual(self.db.execute("""SELECT COUNT(*) FROM playback_success
          WHERE expires_at_ms>1300""").fetchone()[0],0)

if __name__ == "__main__":
    unittest.main()
