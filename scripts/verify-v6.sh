#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Development-only verification: this script NEVER deploys Worker or pushes dist/.
branch="$(git branch --show-current)"
if [[ "$branch" != "feature/detail-dual-ratings-v6" ]]; then
  echo "Refusing to build outside the v6 feature branch (current: $branch)" >&2
  exit 2
fi

echo "== Worker gateway tests =="
(cd worker && npm test)

echo "== Source Watchdog policy/SQLite tests (isolated, no network) =="
(cd source-watchdog && npm test)
python3 -m unittest discover -s source-watchdog/tests -v

echo "== Pure Kotlin core and visual-policy tests =="
bash scripts/test-core.sh

echo "== Distribution staging tests =="
python3 -m unittest discover -s tests -v

echo "== Compile a local v6 candidate without publishing =="
bash scripts/build-codespace.sh

echo "== Verify candidate version =="
python3 - <<'PY'
import json
from pathlib import Path
from zipfile import ZipFile

manifest = json.loads(Path("dist/plugins.json").read_text())
assert len(manifest) == 1 and manifest[0]["version"] == 6, "Expected exactly v6"
archive = Path("dist/EA-FB.cs3")
assert archive.is_file() and archive.stat().st_size > 0
with ZipFile(archive) as zf:
    assert zf.testzip() is None, "Corrupt plugin archive"
print("PASS: v6 local candidate built; NOTHING deployed or pushed")
PY
