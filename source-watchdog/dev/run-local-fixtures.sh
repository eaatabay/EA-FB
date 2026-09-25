#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# This script deliberately NEVER invokes wrangler d1 create, --remote, or deploy.
# Run only inside a checked-out feature branch; never touch production metadata.
branch="$(git branch --show-current)"
if [[ "$branch" != "feature/detail-dual-ratings-v6" ]]; then
  echo "Refusing to run outside v6 feature branch" >&2
  exit 2
fi
if [[ ! -f wrangler.local.jsonc ]]; then
  node dev/make-local-config.mjs
fi
node dev/check-runtime.mjs
npm test
python3 -m unittest discover -s tests -v
# All D1 operations are explicitly --local. Apply both migrations before seed.
./node_modules/.bin/wrangler d1 migrations apply ea-fb-watchdog-fixture-local \
  --local --config wrangler.local.jsonc
node dev/generate-fixture-seed.mjs > dev/generated-fixtures.sql
./node_modules/.bin/wrangler d1 execute ea-fb-watchdog-fixture-local \
  --local --config wrangler.local.jsonc --file dev/generated-fixtures.sql
./node_modules/.bin/wrangler d1 execute ea-fb-watchdog-fixture-local \
  --local --config wrangler.local.jsonc \
  --command "SELECT COUNT(*) AS count FROM source_registry;"
echo "Fixture DB seeded locally. Next: run 'npm run dev:fixture' in a separate"
echo "terminal, then test /cdn-cgi/local/scheduled?cron=*/15+*+*+*+*."
echo "No Cloudflare production resources were contacted or changed."
