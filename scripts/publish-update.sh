#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Future releases use the already deployed Cloudflare metadata relay.
# Neither this command nor the public .cs3 ever reads your TMDb secret.
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Tracked local changes found: publish aborted to protect your work." >&2
  exit 2
fi
git pull --ff-only origin main
endpoint="$(python3 - <<'PY'
import json
from pathlib import Path
d = json.loads(Path("config/backend.json").read_text())
url = d.get("apiBaseUrl") or ""
if not url.startswith("https://"): raise SystemExit("Catalog backend not configured")
print(url.rstrip("/"))
PY
)"
curl -fsS --max-time 20 "$endpoint/health" |
  python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status")=="ready" else 1)'
old_version="$(python3 - <<'PY'
import json
from pathlib import Path
print(json.loads(Path("dist/plugins.json").read_text())[0]["version"])
PY
)"
bash scripts/build-codespace.sh
NEW_VERSION="$(python3 - <<'PY'
import json
from pathlib import Path
print(json.loads(Path("dist/plugins.json").read_text())[0]["version"])
PY
)"
if (( NEW_VERSION <= old_version )); then
  echo "Version must exceed currently published v$old_version before upload." >&2
  exit 2
fi
git add -f dist/EA-FB.cs3 dist/plugins.json dist/repo.json
git commit -m "release: public EA-FB v$NEW_VERSION"
git push origin main
echo "Public EA-FB v$NEW_VERSION published at the same CloudStream repository URL."
