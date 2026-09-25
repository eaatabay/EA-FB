# EA-FB Source Watchdog — foundation (v6 feature branch)

The **policy engine, D1-compatible private registry, immutable audit triggers,
local-only D1 fixture harness, isolated Cron Worker, signed snapshot contract and
read-only Access-gated admin dashboard** are staged on the v6 branch.
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

## Durable per-source lease and Cron/incident race protection (v6 code only)
- `migrations/0002_source_leases.sql` adds a per-source D1 lease with a unique
  token and bounded expiration; a second Cron or incident runner skips an
  already-claimed source. A crashed runner's lease can expire, but a stale
  owner cannot delete a successor's lease.
- `src/runner.mjs` rechecks enabled/integration-approved/admin-review status
  and whether the check is due **after** acquiring the lease. A source-specific
  database or adapter failure is isolated from other source checks.
- `src/registry.mjs` atomically enforces both the source revision and active
  lease token on scheduled/incident probe writes. Late results cannot publish
  if another runner took over. Earlier direct registry fixture tests retain
  the optional no-lease internal API; there is no public route for it.
- Timeouts abort cooperative adapters. Rejecting the timeout before emitting
  the abort signal prevents an adapter's synchronous abort listener from
  reporting false success.
- Tests: `test/lease.test.mjs` exercises real local Node SQLite lock, expiry
  and safe release; `test/runner.test.mjs` covers overlapping Cron runs and
  timeout race; `test/registry.test.mjs` verifies an expired owner cannot
  mutate health, audit or global revision; Python migration tests cover CAS
  and duplicate protection.
- `test/full-cycle.test.mjs` is a separate Node >=22 SQLite integration test:
  30 fabricated sources, 15-minute wake-ups, 27 ordinary recoveries, two
  approved test-domain moves, one admin-held structural failure and two fresh
  checks after simulated admin repair. It requires **both** D1 migrations.
  Offline fixture assertions and lease/SQL unit tests were individually
  verified during development; full Node/Worker/D1 end-to-end execution
  remains a release gate.

## Current source files
- `source-watchdog/src/policy.mjs`: side-effect-free state transitions.
- `source-watchdog/test/policy.test.mjs`: fixture-only isolation/failure/recovery tests.
- `source-watchdog/package.json`: `npm test` (Node built-ins and SQLite);
  Wrangler is a development-only dependency, never part of the plugin.

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
  WATCHDOG_CRON_ENABLED=false, WATCHDOG_FIXTURE_ENABLED=false,
  WATCHDOG_SNAPSHOT_ENABLED=false, WATCHDOG_ADMIN_ENABLED=false,
  workers_dev=false and NO D1 binding. No live Cloudflare or third-party API was contacted.
- src/worker.mjs exposes GET /health only with the committed OFF flags;
  signed /v1/sources and read-only /admin require separate explicit enablement.
  The incident intake and admin WRITES have no public routes. No real adapters
  are included.
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

### Developer-only LOCAL D1 fixture verification (do NOT deploy)
- `dev/make-local-config.mjs` creates an ignored `wrangler.local.jsonc` with
  an inert UUID placeholder and preview_database_id for a LOCAL-only D1.
  The config generator refuses to run unless every tracked production flag
  is disabled and the tracked config contains no D1 binding. It uses an
  exclusive create and will not overwrite an existing local file.
- From the v6 feature branch: `cd source-watchdog && npm install`, then
  `npm run test:local`. This executes the Node, SQLite and Python tests;
  applies BOTH D1 migrations using Wrangler `--local`; and imports exactly
  30 synthetic fixture records into an isolated local D1 database.
- To exercise the local Cloudflare runtime: `npm run dev:fixture`, then
  request `/cdn-cgi/local/scheduled?cron=*/15+*+*+*+*` on localhost.
  All test records use fictitious `*.example.org` domains; no real scraping
  or network health checks occur. The local admin and snapshot routes stay OFF.
- No `wrangler d1 create`, `--remote`, `wrangler deploy`, real database ID,
  Access domain or production token is required for local verification.
- A full local Wrangler run remains to be performed in a checkout with the
  Wrangler dependency installed; this repository edit alone is not a test run.

## Signed client snapshot contract (v6 code and offline tests, NOT live)
- `src/snapshot-crypto.mjs` adds canonical Ed25519 signing and verification
  for read-only registry snapshots. The signature authenticates source IDs,
  approved HTTPS base URLs, adapter versions, revision and 15-minute expiry.
  It also rejects malformed URLs, duplicate IDs, unknown fields, future dates,
  old revisions, expired payloads, unpinned signers and modified payloads.
- A valid signature is not permission to execute new parser code: verified
  sources are restricted to IDs and EXACT adapter versions already bundled
  into the installed client. New domain values may change without reinstall
  ONLY when the same approved adapter remains compatible.
- `src/snapshot-publisher.mjs` imports a PKCS#8 Ed25519 private key only from
  future Worker secrets SNAPSHOT_SIGNING_PKCS8_B64 and KEY_ID; it signs a
  prefiltered unpublished D1 snapshot via a private code path, NOT HTTP.
  No private key, production public key or public snapshot endpoint exists
  in this branch. Key material was generated ephemerally for tests only.
- To avoid breaking clients when no source health state changes for six hours,
  snapshots with an unchanged registry revision may be refreshed when their
  signed generatedAt timestamp is strictly newer than the last verified one.
  A replay with the same timestamp is still rejected. The client must persist
  BOTH the last accepted revision and last generatedAt.
- `test/snapshot-crypto.test.mjs` and `test/snapshot-publisher.test.mjs`
  exercise signatures, domain tampering, revision replay, expiration, key
  rotation rejection, strict schema, adapter allowlisting and secret failures.
  All ten new signing and replay tests passed with local Node v22 during
  development; the wider Node/Cloudflare/D1 suite remains a release gate.
- Cloudflare documents standard Ed25519 support in Workers WebCrypto.
  The actual Workers runtime, dedicated D1 and Android pinned-key verification
  are still mandatory integration gates before enabling delivery.

## Android/Mi Box client signature-verification foundation (STAGED, NOT RELEASED)
- Added pure Kotlin \`SourceSnapshotTrust.kt\` using BouncyCastle Ed25519 verification
  because older Mi Box Android runtimes cannot be assumed to implement JCA
  Ed25519. The BC 1.80 library is now a pinned v6 Gradle dependency.
- The Kotlin verifier independently canonicalizes the **same** snapshot
  contract as the Worker and rejects invalid signatures, forged URLs, unknown
  signing keys, stale revisions, duplicate IDs, expired payloads and
  unsupported adapter versions. It checks time again before every new search.
- Android \`WatchdogSnapshotJson.kt\` strictly parses untrusted JSON with an
  exact schema and 32 KiB cap. \`WatchdogClientStore.kt\` now commits the
  ORIGINAL signed envelope and both replay counters in one synchronous
  SharedPreferences edit. Durable storage failure rejects the response.
  \`SourceSnapshotOfflinePolicy.kt\` re-verifies a stored envelope's
  Ed25519 signature, exact persisted revision/generation, expiry and
  installed adapter versions on EVERY offline restore. Expired or tampered
  bytes never rejoin new searches; durable replay guards remain intact.
- \`SourceSnapshotGate.kt\` only configures installed, exactly version-matching
  adapters for NEW searches. It never downloads code or interrupts a stream
  already playing. With no verified snapshot it returns zero adapters.
- **No production signing public key is pinned yet**, no external adapters are
  bundled, and the Worker publishes no endpoint. \`WatchdogTrustConfig\`
  intentionally contains EMPTY key and adapter maps. A separately approved
  release must explicitly provision and pin the real public key, authenticate
  the read-only endpoint, and connect the source list to the actual provider.
- Added one shared Node WebCrypto public test vector in
  \`core-tests/fixtures/signed-source-snapshot-vector.json\` (NO private key).
  Both JS and Kotlin validate its Ed25519 signature, allowing test-only
  cross-language compatibility without trusting real source records.
- Local JVM checks: 14/14 signature and replay cases and 6/6 adapter-gate
  cases pass using Kotlin/JVM and BouncyCastle 1.80; Node verifies the same
  vector. The Android parser and storage code passed a Kotlin syntax/type
  compile against local Android/JSON *stubs*; actual Android Gradle/Dex
  packaging and Mi Box remote navigation/playback remain UNVERIFIED.

## Signed offline snapshot cache (26 September; CODED, NOT RELEASED)
- Offline reuse requires exact persisted replay markers AND a fresh cryptographic
  verification of the original signed bytes with app-pinned Ed25519 keys.
  A once-valid but expired or tampered cache returns no sources. Only installed
  adapters matching the signed version are eligible; no dynamic code download.
- Two exact GitHub Kotlin source files plus all JVM-only test/stub files
  matched their local copies by Git blob SHA. Kotlin 1.9 / BC 1.80 tests:
  **14/14** pure cache policy and **17/17** atomic Android store checks PASSED.
  Android-store tests used minimal Context and JSON-parser TEST STUBS.
  \`scripts/test-core.sh\` now runs both suites and passed \`bash -n\`.
- REAL Android SharedPreferences, Gradle/Dex, actual Mi Box restart/network
  polling, production signing-key pinning and Worker integration are UNTESTED.
  The checked-in key/adapter maps and all Cloudflare runtime flags remain OFF.

## Approved first-party HTTPS refresh client (26 September; STAGED, OFF)
- Added `WatchdogSnapshotRefresh.kt`: explicit opt-in config, exact reviewed
  HTTPS origin and `/v1/sources` path, installed public signing key AND
  bundled adapter requirement, 32 KiB response cap, strict JSON MIME and
  UTF-8 validation, 15-minute cadence and bounded 15–60-minute retry backoff.
  Concurrent refresh calls are serialized with a Kotlin coroutine `Mutex`.
- All responses go through `WatchdogClientStore.acceptSignedJson` (injected
  callback) before use, including durable anti-replay checks. On a network
  failure, ONLY an already signed and still-unexpired cache may be used.
  Redirects, HTTP 200 + empty, bad JSON/MIME, malformed UTF-8, forged
  signatures, clock rollback and expired cached data fail closed.
- Added `WatchdogHttpsTransport.kt` for a separately approved first-party
  HTTPS Worker endpoint. System TLS, no cookies or credentials, no redirects,
  no HTTP caching, 4-second connection/read timeouts and a bounded byte
  stream prevent untrusted response bloat. This is strictly a snapshot
  delivery client, NOT a source-probing transport or a substitute for
  IP-pinned server-side probes.
- The checked-in `WatchdogDeliveryConfig` has `enabled=false`, an EMPTY
  endpoint and EMPTY approved origin. `WatchdogTrustConfig` still has NO
  production public key and NO real adapters. The plugin does NOT schedule
  refresh or instantiate this client on startup; no network call occurs.
- The exact GitHub source blobs compiled locally with Kotlin 1.9,
  BouncyCastle 1.80 and kotlinx.coroutines on JVM. New deterministic
  injected-transport test suite: **23/23 passed** across twelve invalid
  configurations, pin/version checks, UTF-8/size/MIME failures, redirect,
  replay/expiry, offline fallback, corrupt persisted cache, coroutine cancellation,
  clock rollback and concurrent requests.
  `scripts/test-core.sh` now runs the suite; NO GitHub Actions quota used.
- The HTTPS transport additionally passed **9/9** offline tests against a
  fake `HttpsURLConnection`: no redirect following, cache disabling, enforced
  4-second timeouts, correct request headers, declared/actual body cap and
  cleanup on I/O failure. The production `WatchdogClientStore` now exposes
  `approvedRefreshClient()`, which returns null with the checked-in empty
  endpoint/pins/adapters and NEVER starts network activity on construction.
  All four offline/cache/refresh/HTTPS test suites totaling **63/63** cases
  were compiled against exact SHA-matched GitHub sources on Kotlin/JVM.
  The exact updated `scripts/test-core.sh` also passed `bash -n`.
- Still required: production endpoint ownership/authorization review, actual
  public-key pinning, one approved bundled source adapter, explicit plugin
  lifecycle integration, real Android HTTPS/device tests and isolated
  Cloudflare Worker+D1 integration. No deployment or main-branch change.

## Signed media adapter bridge (26 September; OFFLINE ONLY, NO LIVE SOURCES)
- `WatchdogAdapterSelection.kt` applies signed `mediaKind` scope before
  instantiating only exact-version compiled `VerifiedBaseUrlAdapter`s.
  Movie searches may use `movie`/`both`, series may use `series`/`both`;
  `LIVE` remains the separate existing TV path. No new code may arrive
  from a downloaded snapshot. Missing/expired snapshots, mismatched IDs,
  duplicate adapters, over 32 bundles and failing adapter factories are
  all rejected or isolated before any network call.
- `WatchdogApprovedAdapterBridge.kt` is a dormant future integration point:
  it can obtain an **already signature-verified** offline snapshot from
  `WatchdogClientStore` only if the compiled endpoint/origin, public signing
  keys and *entire* installed adapter ID/version map have been approved.
  The checked-in production values are EMPTY/OFF. The existing EAProvider
  search and playback paths have NOT been modified or activated.
- Against local JVM tests using minimal *test-only MediaSourceAdapter* and
  *test-only Android Context/JSON* stubs, **15/15 selection cases** and
  **5/5 production-OFF bridge cases** passed. The local source and test
  files were cross-checked by exact Git blob SHA against GitHub. The
  revised `scripts/test-core.sh` includes both suites and passed `bash -n`.
  Full real CloudStream/Android Gradle, workerd+D1, authorized live
  adapters and Mi Box integration are still PENDING. GitHub Actions
  quota was not used; no PR or deployment was initiated.

## Release-compiled rights permits for real sources (26 Sep; EMPTY, OFF)
- `ReviewedSourcePermitPolicy.kt` adds an independently compiled **rights,
  host, path and expiry** gate to the existing signed Android snapshot +
  matching bundled-adapter version checks. One successful signed snapshot
  cannot grant media distribution rights or introduce a new executable parser.
  The checked-in `ReviewedSourcePermits.bundled` is an **EMPTY list**.
- The prospective permit specifies fixed source ID, adapter version,
  movie/series/both scope, up to 12 exact HTTPS hosts, an approved URI path
  prefix, a source-specific evidence reference under `rights/`, review date
  and expiry within 366 days. Invalid/expired/duplicate permit records fail
  the entire list closed. Per-source version, media-scope, host and path
  mismatches exclude that signed source without disrupting unrelated sources.
  Pre-approved alternate hosts can be promoted, but a redirect cannot expand
  the built-in permit list.
- `WatchdogApprovedAdapterBridge` now passes only its *already verified*
  offline snapshot through this permit gate BEFORE new-search adapter
  activation. The default plugin still has no endpoint, signing pins,
  approved sources, real adapters or automatic network behavior.
- Exact GitHub source/test blobs were compared to the Kotlin/JVM build:
  **23/23 pure rights-permit cases passed** and the pre-existing
  production-OFF bridge was recompiled with the new permit and passed **5/5**
  additional cases. The first test exposed an actual Kotlin test-fixture
  shadowing error, which was fixed before recording the passing results.
  This is NOT a live source, licensing, Gradle, Android or D1 test.
- `docs/SOURCE-RIGHTS-REVIEW-v6.md` documents the per-source written
  authorization, rights scope, expiry, verified host/path, request budget
  and **explicit user approval** needed BEFORE compiling any real permit.
  Merely adding a document or writing `authorized:true` is not evidence
  of rights or permission to scan a service.

## Signed read-only Worker endpoint (STAGED, DISABLED, NOT DEPLOYED)
- GET /v1/sources is implemented behind four independent safeguards:
  WATCHDOG_MODE=production, WATCHDOG_SNAPSHOT_ENABLED=true,
  WATCHDOG_FIXTURE_ENABLED != true, plus a bound private SOURCES_DB and
  Cloudflare Worker secrets SNAPSHOT_SIGNING_KEY_ID and
  SNAPSHOT_SIGNING_PKCS8_B64. The COMMITTED config keeps the route OFF,
  Worker dev exposure OFF and ALL fixture Cron settings OFF.
- The endpoint publishes only the strictly filtered D1 client snapshot
  signed by the private Worker key. It NEVER accepts source URLs or writes
  source data from HTTP requests and never returns private signing keys.
- It rejects any database containing fixture IDs or example.org test domains;
  it also refuses test records that appear between registry read and signing.
  A bad/missing database, key, or invalid snapshot yields sanitized HTTP 503.
  JSON is no-store: the Android side must persist replay guards and recheck
  the signed 15-minute expiry before using any source for a new search.
- Added test/signed-endpoint.test.mjs: route OFF by default, missing secrets,
  mixed fixtures, late fixture insertion, ephemeral real Ed25519 signing,
  signature verification with the corresponding public key, and blocked writes.
  The local Worker module boundary was additionally checked using Node 22
  fixture-only dependencies; this does NOT constitute deployed Cloudflare
  D1 or actual Mi Box integration testing.
- Android WatchdogClientStore now synchronizes snapshot acceptance before
  updating the durable replay guard to avoid concurrent refresh rollback.

## Admin dashboard foundation (STAGED, OFF, READ-ONLY)
- `src/admin-auth.mjs` verifies the actual RS256 Cloudflare Access JWT
  signature against the JWKS at the fixed admin-chosen team domain, exact
  issuer and application audience, expiration/nbf/iat and a lower-case
  explicit allowlist of admin email addresses. An unverified email header
  alone NEVER authorizes a request. Unknown signers and malformed JWTs fail
  closed. The test JWKS uses ephemeral RSA keys, not production credentials.
- `src/admin-view.mjs` summarizes health and renders a responsive navy/yellow
  HTML dashboard. Every D1-derived value is HTML escaped; the page includes
  no JavaScript, forms or write controls. An authenticated GET /admin receives
  no-store and a restrictive CSP/frame protection. Any admin or D1 failure
  returns a sanitized 403 or 503 without exposing internal exceptions.
- The tracked Worker config sets WATCHDOG_ADMIN_ENABLED=false. Enabling it
  eventually requires a separately reviewed Cloudflare Access application,
  MFA policy, team domain, application AUD tag, explicit admin email list
  and the separate D1 binding. None is configured or deployed now.
- New offline tests cover credential gates, forged JWT claims, invalid
  issuer/audience, token replay timing, unknown JWKS signing keys, HTML
  injection, read-only route behavior and disabled-by-default config.
  Eight pure repository-code safety checks passed in V8. Additionally, the
  exact GitHub admin-auth source and test-file blobs (verified by git SHA)
  passed **5/5 Node 22 JWT tests** with ephemeral RSA keys. The broader
  Node/Worker/D1 suite and real Cloudflare Access + MFA still require testing
  before any admin route is enabled.

## Admin repair/write endpoints (STAGED ONLY; INDEPENDENTLY OFF)
- `src/admin-actions.mjs` validates strict same-origin requests with an
  explicit confirmation header, JSON-only input, a **1 KiB streaming body
  limit**, fixed action names and a mandatory per-source expected revision.
  It rejects unexpected fields, cross-site requests, stale revisions and
  arbitrary URLs. A verified Access email becomes a stable hashed audit actor.
- The ONLY staged HTTP actions are **disable, enable, retest and rollback**.
  Each calls an existing audited, trigger-backed D1 operation with a source
  revision precondition; a concurrent write or historical replay returns 409.
  No HTTP path can grant integration permission, add/change a source domain,
  register a source, run SQL or download parser code.
- `POST /admin/api/sources/:id` requires a valid Cloudflare Access admin JWT
  **and** a second independent opt-in switch,
  `WATCHDOG_ADMIN_WRITES_ENABLED=true`, and an exact
  `WATCHDOG_ADMIN_ORIGIN`. The checked-in Wrangler file and generated local
  test config both keep admin writes OFF. The existing dashboard deliberately
  remains read-only, with no mutation forms or script controls.
- New `test/admin-actions.test.mjs` covers validation, CSRF, oversized
  streamed bodies, replay/CAS, audit actors and sanitized API errors. These
  full Node/SQLite integration tests still require a real v6 checkout run.
  During development, the **exact GitHub admin action and Worker sources**
  passed 12/12 pure policy checks and 10/10 route checks in an isolated V8
  harness with mocked URL/stream/cryptography/database primitives. This is
  NOT an end-to-end Node, real D1 or Cloudflare Access validation.

## Network preflight safety gate (CODED, TESTED LOCALLY; NO LIVE TRANSPORT)
- `src/network-boundary.mjs` performs side-effect-free preflight against
  preapproved, exact HTTPS hostnames only. It rejects local/private/reserved
  IPv4 addresses, malformed targets, credentials/ports, mixed public-private
  DNS answers, IPv6 (conservative pending pinned IPv6 transport), unknown
  redirects, redirect loops and more than three redirects.
- Approved-host DNS is checked again at each supplied redirect hop. This
  module never performs `fetch` and never accepts arbitrary new domains.
  Unknown new hosts require human ownership/authorization review.
- IMPORTANT: DNS preflight **does not prevent DNS rebinding** by itself.
  Source-specific adapters must still use a transport that pins/checks the
  actual remote IP on every connection AND redirect before any live probing.
  Cloudflare Worker global fetch must not be treated as an automatically
  pinned socket, so LIVE probes stay OFF until this is solved and tested.
- `test/network-boundary.test.mjs` adds five Node >=22 tests for public
  address filtering, unsafe mixed DNS answers, strict HTTPS host approval,
  per-hop redirect validation, redirect limits and loop detection.
  The exact GitHub code and test blobs were verified against the local
  copies; all **5/5** tests passed on Node v22.16.0.
- Both migration files `0001_registry.sql` and `0002_source_leases.sql`
  were likewise verified byte-for-byte against current v6 GitHub contents.
  Four independent **Python SQLite 3.13 smoke tests** passed: idempotent
  migration, expired-owner lease takeover with old CAS denial, historical
  run-ID replay rollback, and invalid JSON/unknown lease rejection.
  These are actual local SQLite tests; they are NOT a Cloudflare D1 or
  full repository Node suite result. Wrangler is not provisioned here.

## One-command LOCAL Wrangler/D1 acceptance harness (STAGED, NOT YET RUN)
- `source-watchdog/dev/verify-local-wrangler.mjs` now launches an actual
  LOCAL Cloudflare workerd via Wrangler, using a newly generated ephemeral
  D1 persistence directory. It applies both migrations, seeds 30 wholly
  fictional offline sources, triggers eight 15-minute simulated Cron events,
  and replays the last event to check for duplicate writes.
- The script fails unless it sees **29 healthy, 1 admin-held, 2 approved
  test-domain moves, 59 probe runs, 89 audit events, revision 89,
  zero leaked leases and zero writes on repeated Cron**. It checks that
  tracked and local Wrangler configs still have every production flag OFF,
  and explicitly forbids `--remote` or `deploy` commands.
- A single release command (`cd source-watchdog && npm install &&
  npm run test:release-local`) first checks Node 22/SQLite/Ed25519,
  then runs ALL repository Node tests, Python migration tests and finally
  the Wrangler+D1 real-local-runtime smoke. See
  `docs/WATCHDOG-RELEASE-GATE-v6.md` for usage and acceptance criteria.
- **Honest status:** The new harness code and configuration syntax were
  inspected and parsed. The full suite / workerd test has NOT run here:
  Wrangler's npm package is not available in the offline container.
  Do not use this scripted acceptance target as evidence of a passing test.

## Kotlin test-gate repair and verified JVM execution (25 Sep 2026)
- Discovered a **broken and duplicated** BouncyCastle/snapshot-test block in
  `scripts/test-core.sh`. Fixed the unterminated grep pattern and removed
  the repeated Kotlin compile/run commands. A binary-equivalent local copy
  of the **current GitHub script** passed `bash -n`; the trust and adapter
  tests each appear exactly once.
- The GitHub Kotlin files were compared character-for-character using
  length and FNV32 checks against local copies, then compiled with installed
  Kotlin JVM and BouncyCastle 1.80: **14/14 Ed25519 snapshot-verifier cases**
  and **6/6 verified-adapter gate cases** passed. The adapter-gate JVM test
  used standalone MediaSourceAdapter interface stubs, not a full Android
  Gradle/.cs3 build.
- The exact public JS/Kotlin cross-language test vector also passed local
  Node 22 signature verification, URL tamper rejection and replay rejection.
- Added `source-watchdog/test/shell-syntax.test.mjs`, which checks four
  shell test/release scripts with `bash -n` and fails if either Kotlin
  snapshot test entry point is duplicated. This new regression test has
  been committed, but the full repository `npm test` cannot be run here
  until the private repo test files and Wrangler are present locally.
- All production Cloudflare switches remain OFF; no Mi Box or live Worker
  compilation/deployment has taken place.

## Signed snapshot replay parity — OFFLINE fix (26 September)
- The unsigned helper `canReplaceSnapshot` previously rejected *all*
  snapshots carrying the same D1 revision, even after a new signed generation
  legitimately refreshed expiry. Android and the Ed25519 verifier both
  permit the same revision only with a strictly newer `generatedAt`.
- The helper now uses the **same complete `validSnapshot` schema check** as
  the signing verifier: no unexpected fields, duplicate IDs, bad URLs, invalid
  adapter versions, future generation, oversized TTL or expired snapshots.
  It is NOT a signature verifier: callers MUST cryptographically verify the
  envelope before setting `signatureVerified=true`.
- Seven existing and new snapshot tests, including eleven malformed-input
  mutations, passed against the exact GitHub source blobs in an isolated
  JavaScript/V8 harness with a URL shim. This does NOT count as a full Node,
  Android or Cloudflare integration run.
- No production keys were created or pinned, and all Watchdog switches stay
  OFF. GitHub Actions quota is exhausted, so no PR or workflow was started.

## Next gated milestones
1. Run full Node/SQLite tests from the actual v6 branch, then a local Wrangler
   test using only the isolated fixture D1. Never interpret fixture health
   as availability of any real external source.
2. Review authorized real-source adapters one by one: the new strict DNS /
   redirect preflight exists, but a genuinely IP-pinned live transport (not
   default Worker fetch), source authorization, functional checks and request
   budgets must be designed and independently verified before activation.
3. Add verified, authenticated and rate-limited incident intake for real
   sources; the private runner already has lease-based serialization. User-facing
   search/playback must never wait for repair.
4. Run the new admin-actions Node/SQLite tests, connect read-only admin view
   to a real MFA-enforced Cloudflare Access application, then separately review
   and enable only the narrowly scoped disable/enable/retest/rollback API.
   All admin writes remain OFF until tests, origin and audit are confirmed.
5. Provision and pin a production signing public key in a reviewed .cs3,
   connect the staged read-only Worker endpoint to the Android refresh client,
   and integrate only genuinely approved bundled source adapters. Verify
   Gradle/Dex packaging on Mi Box; fail closed until every gate passes.
6. Run staged rollout on a separate test service before touching v5/main.

GitHub Actions quota is currently exhausted; use local fixture tests where
available. Build or deployment results MUST NOT be inferred from source review.

## Reviewed-source permission regression extension (26 Sep 2026; committed, unrun)
The Kotlin offline rights suite now covers exact approved HTTPS hosts and path segments, URL credentials/ports/query/fragment, encoded and literal traversal, invalid or future review records, missing/invalid evidence references, duplicate permits, adapter-version mismatch and signed movie/series/both narrowing. A defense-in-depth fix rejects unsupported signed media kinds even if an already-verified snapshot is supplied directly to the permit policy. `scripts/test-core.sh` already invokes the permit and bridge JVM tests. A separate Node source-wiring regression asserts that the bridge passes the rights-restricted snapshot, not the raw signed snapshot, to adapter selection. Neither new Kotlin cases nor the new Node regression has been executed in this session. The evidence reference is a compiled review-record pointer, not proof of legal rights by itself; no real source has been approved. Production permit list and all live/admin switches remain OFF.
