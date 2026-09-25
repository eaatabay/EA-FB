#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Only modifies EA-FB. No GitHub Actions, TJK-BOT or other repositories.
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Tracked worktree changes found. Commit or save those changes first; nothing overwritten." >&2
  exit 2
fi
git pull --ff-only origin main
if [[ -z "${TMDB_READ_ACCESS_TOKEN:-}" ]]; then
  echo "TMDB_READ_ACCESS_TOKEN Codespaces secret not loaded. Restart this Codespace." >&2
  exit 2
fi
command -v curl >/dev/null || { echo "curl missing" >&2; exit 2; }
# A freshly-created EA-FB Codespace contains Java/Gradle but not necessarily Node.
# Install a verified official Node binary for the current user automatically.
bash scripts/ensure-node.sh
export PATH="$HOME/.local/bin:$PATH"

echo "Step 1/5: Test keyless TMDb relay..."
node --test worker/test/catalog.test.mjs
echo "Step 2/5: Install Wrangler, then authorize Cloudflare once..."
npm install --prefix worker --no-save --no-package-lock --no-audit --no-fund
echo "Cloudflare login uses a device code suitable for Codespaces."
echo "Log in or create an account when the displayed link opens; approve the one-time access."
(cd worker && npx --no-install wrangler login --device --browser=false)

echo "Step 3/5: Deploy Worker, transfer Codespaces secret server-side..."
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
(cd worker && npx --no-install wrangler deploy --config wrangler.jsonc) | tee "$log"
endpoint="$(grep -Eo 'https://[[:alnum:].-]+[.]workers[.]dev' "$log" | head -n 1 || true)"
if [[ -z "$endpoint" ]]; then
  echo "No public workers.dev URL in Wrangler output; cannot configure EA-FB safely." >&2
  exit 2
fi
printf "%s" "$TMDB_READ_ACCESS_TOKEN" | (cd worker && npx --no-install wrangler secret put TMDB_READ_ACCESS_TOKEN --config wrangler.jsonc)
echo "TMDb secret installed in Cloudflare Worker; no secret appears in GitHub or .cs3."

ready=0
for attempt in 1 2 3 4 5 6; do
  if curl -fsS --max-time 15 "$endpoint/health" | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status")=="ready" else 1)' 2>/dev/null; then
    ready=1
    break
  fi
  sleep 3
done
if [[ "$ready" -ne 1 ]]; then
  echo "Worker health check failed; public v5 is NOT published." >&2
  exit 2
fi
echo "Cloudflare catalog relay healthy."

# A configured secret is not proof that TMDb accepted it. Check a real catalog request.
echo "Checking an actual Turkish TMDb search through the relay..."
if ! curl -fsS --max-time 20 "$endpoint/v1/search/multi?query=Silo&language=tr-TR" |
  python3 -c 'import json,sys; data=json.load(sys.stdin); sys.exit(0 if isinstance(data.get("results"),list) and len(data["results"])>0 else 1)'; then
  echo "TMDb lookup failed. Nothing will be published; check the secret and Worker logs." >&2
  exit 2
fi
echo "Real TMDb search passed."

echo "Step 4/5: Save only public relay URL to GitHub..."
export EA_FB_PUBLIC_ENDPOINT="$endpoint"
python3 - <<'PY'
import json, os
from pathlib import Path
url = os.environ["EA_FB_PUBLIC_ENDPOINT"]
assert url.startswith("https://") and url.endswith(".workers.dev")
file = Path("config/backend.json")
file.write_text(json.dumps({"apiBaseUrl": url, "status":"ready"}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Public backend URL configured. No token recorded.")
PY
git add config/backend.json
if ! git diff --cached --quiet; then
  git commit -m "config: connect EA-FB to public metadata relay"
  git push origin main
fi

echo "Step 5/5: Compile public v5 from scratch and publish to the existing GitHub repo..."
bash scripts/build-codespace.sh
python3 - <<'PY'
import json
from pathlib import Path
item = json.loads(Path("dist/plugins.json").read_text(encoding="utf-8"))[0]
if item["version"] != 5: raise SystemExit("Refusing to publish unexpected version")
if Path("dist/EA-FB.cs3").stat().st_size < 100: raise SystemExit("Missing binary")
print("EA-FB v5 manifest and public binary validated")
PY
git add -f dist/EA-FB.cs3 dist/plugins.json dist/repo.json
git commit -m "release: public EA-FB v5 with server-side TMDb catalog"
git push origin main
echo
echo "EA-FB v5 published at the existing CloudStream repository URL."
echo "The API key is NOT in the .cs3. In Mi Box, refresh extensions and update EA-FB."
