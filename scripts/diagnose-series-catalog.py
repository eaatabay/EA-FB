#!/usr/bin/env python3
"""Read-only identity diagnosis. No deployments, source edits or playback requests."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

ORIGIN = "https://ea-fb-catalog-v6-staging.eaatabay.workers.dev"
CONFIG_API = "https://api.github.com/repos/eaatabay/EA-FB/contents/config/backend.v6-staging.json?ref=feature/detail-dual-ratings-v6"
RAW_CONFIG = "https://raw.githubusercontent.com/eaatabay/EA-FB/feature/detail-dual-ratings-v6/config/backend.v6-staging.json"
TV_ID = 320754
IMDB_ID = "tt38732131"
MOVIE_IDS = {30634, 577990}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def fetch(url, github=False):
    headers = {"Accept": "application/json", "User-Agent": "EA-FB-series-identity-diagnosis"}
    if github:
        headers["Accept"] = "application/vnd.github.raw+json"
        token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
        if token:
            headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=30) as response:
            return {"status": response.status, "body": json.load(response)}
    except urllib.error.HTTPError as error:
        return {"status": error.code, "error": "http-error"}
    except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
        # Never persist raw exception text, headers, tokens, redirects or response bodies.
        return {"status": None, "error": type(error).__name__}

def text(value):
    return value if isinstance(value, str) and len(value) <= 300 else None

def summarize_tv(body):
    out = {key: body.get(key) for key in ["id", "name", "original_name", "first_air_date",
                                          "number_of_seasons", "number_of_episodes"]}
    out["name"] = text(out["name"])
    out["original_name"] = text(out["original_name"])
    out["first_air_date"] = text(out["first_air_date"])
    external = body.get("external_ids")
    imdb = external.get("imdb_id") if isinstance(external, dict) else None
    out["imdb_id"] = imdb if isinstance(imdb, str) and re.fullmatch(r"tt[0-9]+", imdb) else None
    out["seasons"] = [
        {key: season.get(key) for key in ["id", "name", "season_number", "episode_count", "air_date"]}
        for season in body.get("seasons", []) if isinstance(season, dict)
    ]
    return out

def summarize_season(body):
    return {
        "id": body.get("id"), "name": text(body.get("name")), "season_number": body.get("season_number"),
        "air_date": text(body.get("air_date")),
        "episodes": [{key: episode.get(key) for key in ["id", "name", "season_number", "episode_number", "air_date"]}
                     for episode in body.get("episodes", []) if isinstance(episode, dict)]
    }

def identity_checks(tv, external, season, search):
    episodes = season.get("episodes", [])
    return {
        "tv_id": tv.get("id") == TV_ID and external.get("id") == TV_ID,
        "tv_name": tv.get("name") == "Organize İşler",
        "tv_year": (tv.get("first_air_date") or "").startswith("2026-"),
        "imdb": external.get("imdb_id") == IMDB_ID,
        "season": season.get("season_number") == 1,
        "eight_exact_episodes": len(episodes) == 8 and
            {e.get("episode_number") for e in episodes} == set(range(1, 9)) and
            all(e.get("season_number") == 1 for e in episodes),
        "search_tv_identity": any(row.get("id") == TV_ID and row.get("media_type") == "tv" for row in search),
        "movies_not_tv": all(row.get("media_type") == "movie" for row in search if row.get("id") in MOVIE_IDS),
    }

def diagnose():
    # The API is needed for a private repository on a GitHub Actions runner.
    config_result = fetch(CONFIG_API, github=True)
    if config_result.get("status") != 200:
        config_result = fetch(RAW_CONFIG)
    config = config_result.pop("body", None)
    report = {"scope": "read-only staging catalogue; no playback or runtime alias changes",
              "tv_candidate": TV_ID, "reported_imdb": IMDB_ID, "config_status": config_result}
    if not isinstance(config, dict) or config.get("apiBaseUrl") != ORIGIN or config.get("status") != "ready":
        report["result"] = "approved-config-unavailable-or-mismatched"
        return report, 2
    report["approved_origin"] = ORIGIN
    paths = {
        "tv": "/v1/tv/320754?language=tr-TR",
        "external": "/v1/tv/320754?append_to_response=external_ids&language=tr-TR",
        "season": "/v1/tv/320754/season/1?language=tr-TR",
        "search": "/v1/search/multi?query=" + urllib.parse.quote("Organize İşler") + "&language=tr-TR",
    }
    response = {key: fetch(ORIGIN + path) for key, path in paths.items()}
    report["requests"] = {key: {k: v for k, v in result.items() if k != "body"}
                          for key, result in response.items()}
    if any(result.get("status") != 200 or not isinstance(result.get("body"), dict) for result in response.values()):
        report["result"] = "catalogue-response-unavailable"
        return report, 2
    report["tv"] = summarize_tv(response["tv"]["body"])
    report["external"] = summarize_tv(response["external"]["body"])
    report["season"] = summarize_season(response["season"]["body"])
    report["search"] = [
        {key: row.get(key) for key in ["id", "media_type", "name", "original_name", "title", "original_title",
                                      "first_air_date", "release_date"]}
        for row in response["search"]["body"].get("results", [])
        if isinstance(row, dict) and row.get("id") in (MOVIE_IDS | {TV_ID})
    ]
    report["checks"] = identity_checks(report["tv"], report["external"], report["season"], report["search"])
    report["result"] = "catalogue-identity-confirmed" if all(report["checks"].values()) else "identity-mismatch-or-incomplete"
    return report, 0 if all(report["checks"].values()) else 1

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("build/series-diagnostics/catalog-identity.json"))
    args = parser.parse_args()
    report, code = diagnose()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return code

if __name__ == "__main__":
    sys.exit(main())
