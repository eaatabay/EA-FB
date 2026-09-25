#!/usr/bin/env bash
# Bundles a personal TMDb Read Access Token into a LOCAL-ONLY .cs3 package.
# Never commits the secret to Git and never modifies public dist/.
set -euo pipefail
cd "$(dirname "$0")/.."

config="EA-FB/src/main/kotlin/com/eafb/EAConfig.kt"
[[ -f "$config" ]] || { echo "Missing EAConfig.kt" >&2; exit 2; }
[[ -n "${TMDB_READ_ACCESS_TOKEN:-}" ]] || {
  echo "Set the TMDB_READ_ACCESS_TOKEN Codespaces repository secret first; do not paste it into source control." >&2
  exit 2
}
if ! git diff --quiet -- "$config"; then
  echo "EAConfig.kt has local changes. Keep your existing work safe before a private build." >&2
  exit 2
fi
backup="$(mktemp)"
chmod 600 "$backup"
cp "$config" "$backup"
restore() {
  cp "$backup" "$config"
  rm -f "$backup"
}
trap restore EXIT
trap 'exit 1' INT TERM

python3 - <<'PY'
import json
import os
from pathlib import Path
token = os.environ["TMDB_READ_ACCESS_TOKEN"].strip()
if token.lower().startswith("bearer "):
    token = token[7:].strip()
if len(token) < 35 or any(ch.isspace() for ch in token):
    raise SystemExit("TMDb token looks invalid; enter only the API Read Access Token in Codespaces secrets")
file = Path("EA-FB/src/main/kotlin/com/eafb/EAConfig.kt")
file.write_text(
    "package com.eafb\n\n" +
    "/** Temporarily generated for an unpublished personal build. */\n" +
    "object EAConfig {\n    const val tmdbBearerToken: String = " +
    json.dumps(token, ensure_ascii=True) +
    "\n}\n",
    encoding="utf-8",
)
print("Private TMDb credential prepared in temporary build source (value hidden).")
PY

export EA_FB_PRIVATE_BUILD=1
bash scripts/build-codespace.sh

echo
echo "Private build ready: private-dist/EA-FB.cs3"
echo "EAConfig.kt restored on exit. Public dist/ remains unchanged."
echo "To install locally, transfer .cs3 to Android Cloudstream3/plugins/ and restart CloudStream."
