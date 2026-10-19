import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("catalog_probe", ROOT / "scripts/diagnose-series-catalog.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

class CatalogueIdentityDiagnosisTests(unittest.TestCase):
    def test_summaries_exclude_unrelated_playback_and_credential_fields(self):
        source = {"id": 320754, "name": "Organize İşler", "original_name": "Organize İşler",
                  "first_air_date": "2026-10-16", "token": "PRIVATE_SENTINEL",
                  "external_ids": {"imdb_id": "tt38732131", "private": "PRIVATE_SENTINEL"},
                  "stream": "PRIVATE_SENTINEL", "seasons": []}
        self.assertNotIn("PRIVATE_SENTINEL", str(probe.summarize_tv(source)))
        source["external_ids"]["imdb_id"] = "PRIVATE_SENTINEL"
        self.assertIsNone(probe.summarize_tv(source)["imdb_id"])

    def test_correct_tv_identity_and_exact_season_episode_set(self):
        tv = {"id": 320754, "name": "Organize İşler", "first_air_date": "2026-10-16"}
        external = {**tv, "imdb_id": "tt38732131"}
        season = {"season_number": 1, "episodes": [{"episode_number": n, "season_number": 1} for n in range(1, 9)]}
        search = [{"id": 320754, "media_type": "tv"}, {"id": 30634, "media_type": "movie"},
                  {"id": 577990, "media_type": "movie"}]
        self.assertTrue(all(probe.identity_checks(tv, external, season, search).values()))
        for bad in [
            {**season, "season_number": 2},
            {**season, "episodes": season["episodes"][:-1]},
            {**season, "episodes": [{"episode_number": 8, "season_number": 1}] * 8},
            {**season, "episodes": [{"episode_number": n, "season_number": 2} for n in range(1, 9)]},
        ]:
            self.assertFalse(all(probe.identity_checks(tv, external, bad, search).values()))

    def test_same_name_movie_or_wrong_imdb_is_not_a_series_confirmation(self):
        tv = {"id": 30634, "name": "Organize İşler", "first_air_date": "2005-01-01"}
        external = {**tv, "imdb_id": "tt00000000"}
        checks = probe.identity_checks(tv, external, {"season_number": 1, "episodes": []},
                                       [{"id": 320754, "media_type": "movie"}, {"id": 30634, "media_type": "tv"}])
        for key in ["tv_id", "tv_year", "imdb", "search_tv_identity", "movies_not_tv", "eight_exact_episodes"]:
            self.assertFalse(checks[key], key)

if __name__ == "__main__":
    unittest.main()
