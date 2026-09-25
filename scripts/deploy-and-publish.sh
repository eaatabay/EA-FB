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
echo "Step 2/5: Install Wrangler, then reuse your existing Cloudflare authorization..."
npm install --prefix worker --no-save --no-package-lock --no-audit --no-fund
if (cd worker && npx --no-install wrangler whoami --json >/dev/null 2>&1); then
  echo "Cloudflare login already authorized. No new code needed."
else
  echo "Authorize Cloudflare once at the device URL printed below."
  (cd worker && npx --no-install wrangler login --device --browser=false)
fi

echo "Step 3/5: Deploy Worker, transfer Codespaces secret server-side..."
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
if ! (cd worker && npx --no-install wrangler deploy --config wrangler.jsonc) 2>&1 | tee "$log"; then
  if grep -qi 'register.*workers.dev subdomain' "$log"; then
    echo
    echo "Cloudflare Workers requires one-time workers.dev subdomain registration."
    echo "Open the exact .../workers/onboarding URL shown above, choose a free subdomain, and register."
    echo "Then rerun this same deployment command; the existing Cloudflare authorization is reused."
  fi
  exit 2
fi
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
smoke="$(mktemp)"
trap 'rm -f "$log" "$smoke"' EXIT
response_code="$(curl -sS --retry 2 --retry-all-errors --retry-delay 2 --max-time 20 -o "$smoke" -w '%{http_code}' "$endpoint/v1/search/multi?query=Silo&language=tr-TR" || true)"
if [[ "$response_code" != 200 ]]; then
  echo "Worker returned HTTP $response_code; safe error code follows:" >&2
  python3 - "$smoke" <<'PY'
import json,sys
try:
  d=json.load(open(sys.argv[1]))
  error=d.get("error", "unknown_error")
  reason=d.get("reason", "")
  safe=lambda v: v if isinstance(v,str) and len(v)<80 and all(ch.islower() or ch.isdigit() or ch=="_" for ch in v) else ""
  print("Worker error:", safe(error) or "unknown_error")
  if reason: print("Connection diagnostic:", safe(reason) or "unknown_reason")
except (OSError,ValueError):
  print("non_json_or_network_error")
PY
  echo "Public v5 was NOT published; Codespaces secret and GitHub files remain private." >&2
  exit 2
fi
python3 - "$smoke" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]))
if not isinstance(d.get("results"), list) or not d["results"]:
  raise SystemExit("Real TMDb search returned no catalog rows: public v5 NOT published.")
print("Real TMDb search passed.")
PY

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
