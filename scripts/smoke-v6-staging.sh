#!/usr/bin/env bash
# V6 staging smoke test: read-only GETs, no credentials printed, no deployment.
set -euo pipefail
: "${EA_FB_V6_STAGING_URL:?Set EA_FB_V6_STAGING_URL to the confirmed staging Worker origin}"
url="${EA_FB_V6_STAGING_URL%/}"
case "$url" in
  https://ea-fb-catalog.eaatabay.workers.dev|https://ea-fb-catalog.eaatabay.workers.dev/*)
    echo "BLOCKED: production Worker must never be used as staging" >&2; exit 2 ;;
  https://ea-fb-catalog-v6-staging.*.workers.dev) ;;
  *) echo "BLOCKED: expected a dedicated ea-fb-catalog-v6-staging workers.dev origin" >&2; exit 2 ;;
esac
command -v curl >/dev/null || { echo "curl required" >&2; exit 2; }
check() {
  local path="$1" file code
  file="$(mktemp)"
  code="$(curl --silent --show-error --max-redirs 0 --max-time 20 \
    --output "$file" --write-out '%{http_code}' "$url$path")" || { rm -f "$file"; return 1; }
  if [[ "$code" != 200 ]]; then
    echo "FAIL: staging $path returned HTTP $code" >&2; rm -f "$file"; return 1
  fi
  if ! python3 - "$file" "$path" <<'PY'
import json,sys
with open(sys.argv[1],encoding="utf-8") as f: data=json.load(f)
path=sys.argv[2]
if path=="/health":
    assert data.get("status")=="ready" and data.get("service")=="EA-FB catalog"
elif path.startswith("/v1/discover/"):
    assert isinstance(data.get("results"),list) and isinstance(data.get("page"),int)
elif path.startswith("/v1/collection/"):
    assert isinstance(data.get("parts"),list)
elif "/episode-titles?" in path:
    assert isinstance(data.get("titles"),dict)
elif path.endswith("/translations"):
    assert isinstance(data.get("translations"),list)
else:
    assert isinstance(data,dict) and data.get("id")
PY
  then
    echo "FAIL: staging $path returned invalid catalog shape" >&2
    rm -f "$file"; return 1
  fi
  rm -f "$file"
  echo "PASS: $path"
}
check "/health"
check "/v1/discover/movie?with_watch_providers=8&watch_region=TR&with_watch_monetization_types=flatrate&language=tr-TR"
check "/v1/discover/tv?with_watch_providers=119&watch_region=TR&with_watch_monetization_types=flatrate&language=tr-TR"
check "/v1/discover/movie?with_watch_providers=350&watch_region=TR&with_watch_monetization_types=flatrate&language=tr-TR"
check "/v1/discover/tv?with_watch_providers=350&watch_region=TR&with_watch_monetization_types=flatrate&language=tr-TR"
check "/v1/discover/movie?with_watch_providers=531&watch_region=TR&with_watch_monetization_types=flatrate&language=tr-TR"
check "/v1/discover/tv?with_watch_providers=531&watch_region=TR&with_watch_monetization_types=flatrate&language=tr-TR"
check "/v1/discover/movie?with_genres=878&sort_by=vote_average.desc&vote_count.gte=100&language=tr-TR"
today="$(date -u +%F)"
check "/v1/discover/tv?sort_by=first_air_date.desc&first_air_date.lte=$today&language=tr-TR"
# The official Spider-Man (2002) trilogy's TMDb collection ID is 556.
check "/v1/collection/556?language=tr-TR"
check "/v1/tv/247718/season/1/episode/3/translations"
echo "PASS: dedicated V6 staging catalog smoke checks"

# V41 lazy batch: official Turkish title first, bounded AI fallback when missing.
check "/v1/tv/247718/season/1/episode-titles?episodes=3,7&language=tr-TR"

# V41 staging AI binding must fill known MobLand gaps.
tmp_titles="$(mktemp)"
code="$(curl --silent --show-error --max-redirs 0 --max-time 25   --output "$tmp_titles" --write-out '%{http_code}'   "$url/v1/tv/247718/season/1/episode-titles?episodes=3,7&language=tr-TR")"
test "$code" = 200 || { echo "FAIL: V41 title fallback HTTP $code" >&2; rm -f "$tmp_titles"; exit 1; }
python3 - "$tmp_titles" <<'PY'
import json,sys
with open(sys.argv[1],encoding="utf-8") as f: data=json.load(f)
titles=data.get("titles") or {}
assert isinstance(titles.get("3"),str) and titles["3"].strip()
assert isinstance(titles.get("7"),str) and titles["7"].strip()
PY
rm -f "$tmp_titles"
echo "PASS: V41 staging AI title fallback"
