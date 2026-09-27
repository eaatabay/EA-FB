# EA-FB V6 — staging readiness (production V5 untouched)

## Verified code-level mismatch (2026-09-27)

Compared `worker/src/index.js` on `main` and `feature/detail-dual-ratings-v6` using GitHub's file API. This compares **repository code**, not the currently deployed Cloudflare Worker; production deployment revision and live Mi Box behavior remain unverified.

| Capability | `main` Worker code | V6 Worker code |
|---|---|---|
| Provider/genre discover filters | Yes | Yes |
| `first_air_date.desc` (newest TV) | No | Yes |
| `vote_count.gte` (rated discover) | No | Yes |
| `first_air_date.lte` / `primary_release_date.lte` | No | Yes |
| TMDb official `/collection/{id}` | Yes | Yes |
| OMDb IMDb enrichment | No | Optional, requires `OMDB_API_KEY` |

The V6 Android client still reads `main/config/backend.json`, which points to the V5 production relay. Thus V6-only Worker features cannot be assumed available in the Mi Box client. The Android code uses a clearly labeled popular fallback for empty/unsupported sorted discover routes; this is not a substitute for V6 Worker deployment.

## Release gates (do not skip)

1. Keep `main`, the blue V5 plugin, production Worker, production D1 and existing public URLs unchanged.
2. Provision a **separate staging Worker name and URL** with staging-only secrets; never commit keys. Do not change production `wrangler.jsonc` or production bindings.
3. The client pins `CatalogRelayPolicy.approvedOrigin` to the production V5 Worker and reads `main/config/backend.json`. A separate V6 test build must deliberately pin the staging origin **in its own reviewed source/config**, not through an untrusted remote URL override. Verify the red test build cannot silently redirect the blue V5 client.
4. Run V6 Worker tests, Android offline catalog policy tests, Watchdog local acceptance and a complete Android plugin build.
5. Verify staging `/health`, provider and genre `/v1/discover`, sorted movie/TV routes, `/v1/collection/{id}` and OMDb enrichment with a legitimate IMDb ID. Test absent/invalid OMDb key: IMDb badge must be absent, not copied from TMDb.
6. Install V6 as a separate red test plugin on Mi Box; verify Netflix, Amazon, genre shelves, TMDb/IMDb labels and franchise order. Keep the blue V5 plugin installed.
7. Only after staging and device verification, explicitly review promotion/deployment. No automatic production deployment.

## Current automated verification

`scripts/test-v6-catalog-local.sh` and `.github/workflows/v6-catalog-ci.yml` cover Worker, Android catalog policy and Watchdog tests. They do **not** prove Android plugin compilation, actual Cloudflare deployment revision, OMDb configuration or Mi Box UI behavior.

## Staging configuration prepared (not deployed)

- `worker/wrangler.staging.jsonc` uses a separate `ea-fb-catalog-v6-staging` Worker name and separate rate-limit namespaces. No production D1 bindings or secrets are copied.
- To deploy after reviewing the Cloudflare account, run from `worker/`: `npx wrangler deploy --config wrangler.staging.jsonc`. **Do not run this against the production config.** Cloudflare credentials and `TMDB_READ_ACCESS_TOKEN` must be configured for staging separately; `OMDB_API_KEY` is optional for authentic IMDb ratings.
- The actual workers.dev subdomain must be confirmed from Cloudflare; do not assume the URL exists before deployment.
- Once deployed, run `EA_FB_V6_STAGING_URL=https://ea-fb-catalog-v6-staging.<confirmed-subdomain>.workers.dev bash scripts/smoke-v6-staging.sh` from the repo root. This script refuses the production origin and makes GET requests only.
- **No red Mi Box APK is ready yet.** The red V6 test build must pin the confirmed staging origin and staging-only config in reviewed code. The production V5 plugin and `main/config/backend.json` stay unchanged.
