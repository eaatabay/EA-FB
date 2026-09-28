#!/usr/bin/env bash
# Build an isolated red V6 test package. Never publish to main/dist or production.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ "$(git branch --show-current)" == "feature/detail-dual-ratings-v6" ]] || {
  echo "BLOCKED: wrong branch" >&2; exit 2;
}
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || {
  echo "BLOCKED: tracked working tree has edits; commit or stash before staging build" >&2; exit 2;
}
[[ "${1:-}" == "--build" ]] || {
  echo "Dry run. To compile isolated V6 staging package: bash scripts/build-v6-staging-codespace.sh --build"
  exit 0
}
command -v python3 >/dev/null || { echo "Python 3 required" >&2; exit 2; }
command -v curl >/dev/null || { echo "curl required" >&2; exit 2; }
# Refuse to build a test plugin when its dedicated relay is unconfigured.
health="$(curl --fail --silent --show-error --max-time 20 \
  https://ea-fb-catalog-v6-staging.eaatabay.workers.dev/health)"
HEALTH_JSON="$health" python3 - <<'PY'
import json,os
data=json.loads(os.environ["HEALTH_JSON"])
assert data.get("status")=="ready" and data.get("service")=="EA-FB catalog", \
    "Staging Worker not ready; refusing Android build"
print("PASS: live V6 staging Worker ready")
PY
readonly tmp="$(mktemp -d)"
readonly policy="EA-FB/src/main/kotlin/com/eafb/CatalogRelayPolicy.kt"
readonly provider="EA-FB/src/main/kotlin/com/eafb/EAProvider.kt"
readonly settings="EA-FB/src/main/kotlin/com/eafb/EASettings.kt"
readonly buildfile="EA-FB/build.gradle.kts"
cp "$policy" "$tmp/policy.kt"
cp "$provider" "$tmp/provider.kt"
cp "$settings" "$tmp/settings.kt"
cp "$buildfile" "$tmp/build.gradle.kts"
for f in dist/EA-FB.cs3 dist/plugins.json dist/repo.json; do
  if [[ -f "$f" ]]; then
    mkdir -p "$tmp/$(dirname "$f")"
    cp "$f" "$tmp/$f"
  fi
done
restore() {
  cp "$tmp/policy.kt" "$policy"
  cp "$tmp/provider.kt" "$provider"
  cp "$tmp/settings.kt" "$settings"
  cp "$tmp/build.gradle.kts" "$buildfile"
  for f in dist/EA-FB.cs3 dist/plugins.json dist/repo.json; do
    if [[ -f "$tmp/$f" ]]; then cp "$tmp/$f" "$f"; else rm -f "$f"; fi
  done
  rm -rf "$tmp"
}
trap restore EXIT
python3 scripts/prepare-v6-staging-overlay.py
# Validate only the red staging pin before invoking the existing Android build.
python3 scripts/prepare-v6-staging-overlay.py --verify
bash scripts/build-codespace.sh
mkdir -p build/v6-staging-artifacts
cp dist/EA-FB.cs3 build/v6-staging-artifacts/EA-FB-V6-STAGING.cs3
cp dist/plugins.json build/v6-staging-artifacts/plugins.json
python3 - <<'PY'
from pathlib import Path
import zipfile
p=Path('build/v6-staging-artifacts/EA-FB-V6-STAGING.cs3')
assert p.is_file() and zipfile.is_zipfile(p)
print(f'PASS: isolated red V6 staging test package: {p} ({p.stat().st_size} bytes)')
PY
python3 - <<'PY'
import json
from pathlib import Path
p=Path("build/v6-staging-artifacts/plugins.json")
entries=json.loads(p.read_text(encoding="utf-8"))
assert isinstance(entries,list) and len(entries)==1
e=entries[0]
e["name"]="EA-FB V6 STAGING"
e["url"]="https://raw.githubusercontent.com/eaatabay/EA-FB/feature/detail-dual-ratings-v6/dist-v6-staging/EA-FB-V6-STAGING.cs3"
p.write_text(json.dumps(entries,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
Path("build/v6-staging-artifacts/repo.json").write_text(json.dumps({
  "name":"EA-FB STREAM",
  "description":"EA-FB V6 staging test repository",
  "manifestVersion":1,
  "pluginLists":["https://raw.githubusercontent.com/eaatabay/EA-FB/feature/detail-dual-ratings-v6/dist-v6-staging/plugins.json"]
},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("PASS: V6 web repository manifests prepared")
PY
echo "NOT published. V5 dist/ files and production relay pin restored on exit."
