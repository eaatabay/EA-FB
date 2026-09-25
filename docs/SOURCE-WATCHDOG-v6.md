# EA-FB Source Watchdog — foundation (v6 feature branch)

The **policy engine, D1-compatible private registry, immutable audit triggers,
local tests, isolated Cron/fixture Worker and unpublished snapshot serializer** are implemented in v6.
Nothing is deployed: no real source monitoring, deployed cron, signed snapshot
delivery or admin UI is live. v5/main and the production TMDb
catalog Worker remain unchanged.

## Non-negotiable production contract
- The catalog (TMDb metadata) and licensed source system are failure-isolated.
- Search receives only healthy, enabled and permission-reviewed source adapters.
  A failing source never blocks or hides independent healthy sources.
- A successful HTTP response is *not* a healthy source. Search, detail, TV episode,
  and an authorized playback fixture (if configured) must each pass.
- Unknown redirects, identity mismatches and structural changes require admin
  review. Even a move to an already approved hostname requires two consistent
  successful functional checks.
- Sources return only after two consecutive healthy checks; intermittent network
  failures enter degraded then quarantine, with capped backoff.
- Previous working configuration remains available; changes must be audited and
  reversible. A manual release from admin review always restarts tests.
- Never probe third-party sites without authorization, ignore robots/terms,
  bypass DRM or authentication, or trust unvalidated redirects.
- No public endpoint accepts arbitrary URLs. The future runner must use only
  admin-approved source IDs and explicitly validated allowlisted hosts. Block
  private/reserved destinations at DNS resolution and at every redirect hop;
  pure URL syntax validation in this module is **not** sufficient SSRF defense.

## Durable registry implemented on v6 (NOT deployed)
- Separate `migrations/0001_registry.sql` defines a private D1-compatible
  registry, monotonic global revision, trigger-backed immutable audit history,
  and unique per-source probe IDs. A stale compare-and-swap update changes
  nothing; a replayed historical probe ID rolls back the entire update.
- `src/registry.mjs` exposes authenticated-*caller-only* building blocks:
  register, read, commit a trusted probe, enable/disable, approve/revoke an
  adapter, release from admin review, roll back to a previously audited healthy
  approved URL, and build an **unpublished** client snapshot. There are NO
  write-enabled HTTP routes, credentials, client signing, or live adapters.
- Persisted configuration uses an explicit allowlist. Never store API keys,
  cookies, headers, or free-form source objects in registry/audit history.
- Offline tests: `npm test` checks policy and snapshot on supported Node;
  `test/registry.test.mjs` additionally runs SQLite-backed D1-adapter tests on
  Node >=22 (`node:sqlite`); `python3 -m unittest discover -s tests -v`
  checks migration rollback, CAS, replay and audit independently.
- Neither local SQLite nor mock D1 proves Cloudflare deployment compatibility.
  Create a **separate development D1**, apply migration locally first, then
  verify Wrangler against the isolated development Worker before any rollout.

## Current source files
- `source-watchdog/src/policy.mjs`: side-effect-free state transitions.
- `source-watchdog/test/policy.test.mjs`: fixture-only isolation/failure/recovery tests.
- `source-watchdog/package.json`: `npm test`, no external dependencies.

## Scheduler and runner implemented on v6 (NOT deployed)
- `src/scheduler.mjs` selects only enabled, integration-approved sources whose
  next check is due. Degraded sources are checked before quarantined, then
  healthy sources. Admin-required and disabled sources are never auto-run.
- `src/runner.mjs` isolates each adapter with a timeout and bounded
  concurrency. One crashing adapter cannot stop the other source checks.
- User-facing playback/search never waits for a repair check. An incident can
  request an early check for one source, with a five-minute minimum spacing;
  it cannot bypass disabled/admin-review/integration-approval gates.
- There are still **no real third-party adapters or network probes** in this
  branch. Source-specific adapters must be permission-reviewed and must enforce
  manual redirects, verified host allowlists, SSRF protections and request
  budgets before they are connected.
- Added fixture tests for scheduling priority, incident throttling, two-check
  recovery and isolation when one adapter throws.

## Isolated Cloudflare Cron Worker (implemented, NOT deployed)
- The separate source-watchdog/wrangler.jsonc uses name ea-fb-source-watchdog-dev,
  with a 15-minute Cron trigger. This is NOT the live catalog Worker.
  Existing source policy retains its ~6-hour normal healthy-source cadence,
  one-hour degraded retry and slower quarantine backoff. Cron only wakes the
  scheduler to process whichever sources are due.
- Committed configuration is intentionally INERT: WATCHDOG_MODE=disabled,
  WATCHDOG_CRON_ENABLED=false, WATCHDOG_FIXTURE_ENABLED=false, workers_dev=false,
  and NO D1 binding. No live Cloudflare or third-party API was contacted.
- src/worker.mjs only exposes a minimal GET /health; the registry, incident
  intake and admin writes have NO public routes. No real adapters are included.
- All three fixture flags and an isolated SOURCES_DB binding must be enabled
  before the fixture Cron runs. The fixture registry refuses mixed real IDs
  and non-example.org destinations before invoking any runner.
- src/fixtures.mjs models 27 ordinary sources, two approved test-domain moves,
  and one structural failure requiring admin intervention. It makes no network
  calls. dev/generate-fixture-seed.mjs prints idempotent SQL for 30 fictitious
  source records in a separate LOCAL D1 test database.
- New offline tests cover disabled-by-default gating, no public admin routes,
  safe test sources, seeded migration audit and fixture runner coordination.
  These tests are not a substitute for a real Cloudflare D1/Workers test.

### Future developer-only local verification (do NOT deploy)
1. In source-watchdog run npm install, then create a NEW empty dev-only D1
   named ea-fb-source-watchdog-dev with Wrangler.
2. Copy wrangler.jsonc to ignored wrangler.local.jsonc, adding a SOURCES_DB D1
   binding using the newly created database ID. Do not edit tracked config.
3. Apply migrations with Wrangler --local and the ignored local config.
4. Run node dev/generate-fixture-seed.mjs > dev/generated-fixtures.sql,
   then import that file ONLY with wrangler d1 execute --local.
5. In ignored LOCAL config set fixture mode and its two explicit true flags,
   then run wrangler dev --test-scheduled --config wrangler.local.jsonc.
6. Trigger /cdn-cgi/local/scheduled?format=json and check two-success recovery,
   the structural admin hold, audit revision, and historical-run de-duplication.
7. Leave the committed Wrangler flags off and DO NOT run Wrangler deploy.

## Next gated milestones
1. Run full Node/SQLite tests from the actual v6 branch, then a local Wrangler
   test using only the isolated fixture D1. Never interpret fixture health
   as availability of any real external source.
2. Review authorized real-source adapters one by one: explicit approved hosts,
   DNS and redirect SSRF protection, functional checks and request budgets.
3. Add verified, rate-limited incident reporting and a serialized per-source
   runner. User-facing search/playback must never wait for repair.
4. Build admin panel with strong authentication/2FA, approvals, audit and rollback.
5. Implement signed read-only snapshot delivery and client signature checks.
6. Run staged rollout on a separate test service before touching v5/main.

GitHub Actions quota is currently exhausted; use local fixture tests where
available. Build or deployment results MUST NOT be inferred from source review.
