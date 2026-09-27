#!/usr/bin/env bash
# Explicit, isolated V6 staging deployment. No production configuration.
set -euo pipefail
cd "$(dirname "$0")/../worker"
if [[ "${1:-}" != "--deploy" ]]; then
  echo "Dry run only. To deploy V6 staging: bash scripts/deploy-v6-staging.sh --deploy"
  echo "Requires Cloudflare login/API token and staging TMDB_READ_ACCESS_TOKEN secret."
  exit 0
fi
[[ -f wrangler.staging.jsonc ]] || { echo "Staging config missing" >&2; exit 2; }
node --input-type=module -e '
import {readFileSync} from "node:fs";
const prod=JSON.parse(readFileSync("wrangler.jsonc"));
const stage=JSON.parse(readFileSync("wrangler.staging.jsonc"));
if(stage.name!=="ea-fb-catalog-v6-staging"||stage.name===prod.name||
   "d1_databases" in stage || "routes" in stage || "zone_id" in stage)
  throw Error("staging isolation guard failed");
console.log("PASS: isolated staging Worker configuration");
'
if ! command -v npx >/dev/null; then
  echo "Node.js/npm required" >&2; exit 2
fi
npm test
# Secrets must be provisioned for the staging Worker using Wrangler.
# Never pass the token on the command line or echo it into CI logs.
echo "Deploying ONLY ea-fb-catalog-v6-staging (no D1 bindings)"
npx wrangler deploy --config wrangler.staging.jsonc
echo "Staging deployed. Confirm workers.dev URL from Wrangler output."
echo "Set the staging TMDB_READ_ACCESS_TOKEN secret separately:"
echo "  cd worker && npx wrangler secret put TMDB_READ_ACCESS_TOKEN --config wrangler.staging.jsonc"
echo "Optional independent IMDb rating:"
echo "  cd worker && npx wrangler secret put OMDB_API_KEY --config wrangler.staging.jsonc"
echo "Then run scripts/smoke-v6-staging.sh with the confirmed staging URL."
