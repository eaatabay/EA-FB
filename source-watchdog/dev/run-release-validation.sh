#!/usr/bin/env bash
# EA-FB v6 Watchdog RELEASE GATE. Local only; NEVER deploys or uses remote D1.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(git branch --show-current)" != "feature/detail-dual-ratings-v6" ]]; then
  echo "Refusing to validate outside the EA-FB v6 feature branch" >&2
  exit 2
fi
if [[ ! -x node_modules/.bin/wrangler ]]; then
  echo "Wrangler dependency missing. In source-watchdog run: npm install" >&2
  exit 2
fi
echo "== Runtime: Node 22+, SQLite and Ed25519 =="
node dev/check-runtime.mjs
echo "== ALL Node unit + SQLite runner + crypto + admin + network tests =="
npm test
echo "== SQLite migration suite (Python) =="
python3 -m unittest discover -s tests -v
echo "== Isolated real LOCAL Wrangler + D1 + 9 Cron tick smoke =="
node dev/verify-local-wrangler.mjs
echo "PASS: WATCHDOG LOCAL RELEASE GATE"
echo "NOTE: Cloudflare deployment, real-source probes and Mi Box are NOT tested."
