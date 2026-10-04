#!/usr/bin/env bash
# Build an isolated red V6 test package. Never publish to main/dist or production.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ "$(git branch --show-current)" == "test/dizibox-king-mibox" ]] || {
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
python3 scripts/prepare-v6-staging-overlay.py --verify
EA_FB_V6_STAGING_BUILD=1 bash scripts/build-codespace.sh
mkdir -p build/v6-staging-artifacts
cp dist/EA-FB.cs3 build/v6-staging-artifacts/EA-FB-V6-STAGING.cs3
python3 - <<'PY'
from pathlib import Path
import zipfile
p=Path('build/v6-staging-artifacts/EA-FB-V6-STAGING.cs3')
assert p.is_file() and zipfile.is_zipfile(p)
print(f'PASS: isolated red V6 staging test package: {p} ({p.stat().st_size} bytes)')
PY
# This is a CI artifact, not a published GitHub Pages/raw repository.
# Never advertise dist-v6-staging URLs before those files actually exist.
echo "PASS: CS3 artifact prepared without unverified public repository manifests."
echo "NOT published. Isolated test branch only."
