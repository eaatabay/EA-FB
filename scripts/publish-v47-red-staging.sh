#!/usr/bin/env bash
# Explicitly build and publish the NEXT RED V6 package only (V47).
# No main/V5/blue staging, production Worker, D1 or live source grants.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${1:-}" != "--publish" ]]; then
  echo "Dry run. From the red V6 Codespace: bash scripts/publish-v47-red-staging.sh --publish"
  exit 0
fi
BRANCH="feature/detail-dual-ratings-v6"
[[ "$(git branch --show-current)" == "$BRANCH" ]] || {
  echo "BLOCKED: not on the red V6 branch" >&2; exit 2;
}
[[ -z "$(git status --porcelain)" ]] || {
  echo "BLOCKED: commit or stash local changes first" >&2; exit 2;
}
remote="$(git remote get-url origin)"
[[ "$remote" =~ eaatabay/EA-FB(\.git)?$ ]] || {
  echo "BLOCKED: unexpected Git remote" >&2; exit 2;
}
git fetch origin "$BRANCH"
[[ "$(git rev-parse HEAD)" == "$(git rev-parse "origin/$BRANCH")" ]] || {
  echo "BLOCKED: red V6 branch is not synchronized with origin" >&2; exit 2;
}
python3 - <<'PY'
import json
from pathlib import Path
entry=json.loads(Path("dist-v6-staging/plugins.json").read_text())[0]
assert entry["version"] == 46, "Refusing to overwrite an unexpected staging version"
assert entry["name"] == "EA-FB V6 STAGING"
assert "/feature/detail-dual-ratings-v6/dist-v6-staging/" in entry["url"]
print("PASS: previous frozen V46 staging package remains in Git history")
PY
# Version selection affects only temporary staging overlays and manifest checks.
EA_FB_V6_STAGING_VERSION=47 bash scripts/build-v6-staging-codespace.sh --build
python3 - <<'PY'
import json,zipfile
from pathlib import Path
base=Path("build/v6-staging-artifacts")
entry=json.loads((base/"plugins.json").read_text())[0]
binary=base/"EA-FB-V6-STAGING.cs3"
assert entry["version"]==47 and entry["name"]=="EA-FB V6 STAGING"
assert binary.is_file() and zipfile.is_zipfile(binary)
assert entry["fileSize"]==binary.stat().st_size
assert entry["url"].endswith("/feature/detail-dual-ratings-v6/dist-v6-staging/EA-FB-V6-STAGING.cs3")
with zipfile.ZipFile(binary) as archive:
    assert archive.testzip() is None
    assert {"classes.dex","manifest.json"}.issubset(archive.namelist())
print("PASS: V47 isolated CS3 and staging manifest verified")
PY
cp build/v6-staging-artifacts/EA-FB-V6-STAGING.cs3 dist-v6-staging/
cp build/v6-staging-artifacts/plugins.json dist-v6-staging/
cp build/v6-staging-artifacts/repo.json dist-v6-staging/
git add -f dist-v6-staging/EA-FB-V6-STAGING.cs3 \
  dist-v6-staging/plugins.json dist-v6-staging/repo.json
python3 - <<'PY'
import subprocess
changed=set(subprocess.check_output(
    ["git","diff","--cached","--name-only"],text=True).splitlines())
allowed={
  "dist-v6-staging/EA-FB-V6-STAGING.cs3",
  "dist-v6-staging/plugins.json",
  "dist-v6-staging/repo.json",
}
assert changed and changed<=allowed, "Refusing changes outside RED staging package"
PY
git commit -m "release: RED V6 staging V47 next-episode date fallback"
git push origin "HEAD:refs/heads/$BRANCH"
echo "PUBLISHED: RED V6 staging V47 only. Refresh CloudStream extensions on Mi Box."
