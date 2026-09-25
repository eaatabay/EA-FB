#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Diagnose the original Cloudflare 502 without printing, committing, or
# forwarding your TMDb token to ChatGPT. Only status and error category are shown.
python3 - <<'PY'
import json, os, sys, urllib.error, urllib.request
secret = os.environ.get("TMDB_READ_ACCESS_TOKEN", "").strip()
if secret.lower().startswith("bearer "):
    print("TMDb secret: saved with optional Bearer prefix; normalized for testing")
    secret = secret[7:].strip()
if not secret:
    raise SystemExit("TMDb secret: MISSING. Codespaces must be restarted to load it.")
if len(secret) == 32 and all(c in "0123456789abcdefABCDEF" for c in secret):
    raise SystemExit("TMDb credential looks like the 32-character API KEY, not API READ ACCESS TOKEN. Do not send it here; replace the GitHub Codespaces secret.")
print("TMDb secret: present (actual value never displayed)")
request = urllib.request.Request(
    "https://api.themoviedb.org/3/search/multi?query=Silo&language=tr-TR",
    headers={"Authorization": "Bearer " + secret, "Accept": "application/json"}
)
try:
    with urllib.request.urlopen(request, timeout=18) as response:
        data = json.load(response)
        if isinstance(data.get("results"), list) and data["results"]:
            print("Direct TMDb check: HTTP 200, catalog works")
        else:
            raise SystemExit("Direct TMDb check: HTTP 200 but catalog was empty. Stopped before deployment.")
except urllib.error.HTTPError as exc:
    print(f"Direct TMDb check: HTTP {exc.code}")
    if exc.code in (401, 403):
        raise SystemExit("Credential not accepted by TMDb. Confirm the GitHub Codespaces secret contains the long API Read Access Token.")
    raise SystemExit("TMDb returned an error. No release published.")
except (urllib.error.URLError, TimeoutError, OSError) as exc:
    raise SystemExit("Direct TMDb check: network/TLS failure from Codespaces. No release published.")
PY

echo "Direct access works. Re-deploying the updated keyless Worker and publishing v5 only if its real TMDb search passes..."
exec bash scripts/deploy-and-publish.sh
