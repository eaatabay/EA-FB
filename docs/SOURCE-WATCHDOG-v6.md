# EA-FB Source Watchdog — foundation (v6 feature branch)

The **policy engine, D1-compatible private registry, immutable audit triggers,
local tests and unpublished snapshot serializer** are implemented in v6.
Nothing is deployed: no real monitoring, automatic recovery, cron, signed
snapshot delivery or admin UI is live. v5/main and the production TMDb
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
  HTTP routes, credentials, scheduled checks, client signing, or live adapters.
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

## Next gated milestones
1. Design a durable central registry and audit log, and enforce CAS/versioned
   writes plus least-privilege administration. Keep the catalog Worker separate.
2. Authorized probe runner with actual search/detail/episode test fixtures,
   SSRF-safe DNS and redirect handling, per-host rate limits and no video
   downloading. Never classify HTTP 200 alone as success.
3. Scheduled checks (initially six-hour baseline), incident-triggered checks,
   serialized per-source runs, and automatically retained last-known-good config.
4. Admin-only navy/yellow dashboard, login/2FA, review/release/retest/rollback.
5. Hook EA-FB client source adapters into a **read-only, signed or authenticated**
   registry snapshot; enforce last-known-good cache TTL and fail closed.
6. Test with three independent **authorized/licensed** fixtures: two domain
   moves, one structural failure, 27 healthy simulated sources; then real
   test environment and controlled rollout. No production deployment until
   approved validation passes.

GitHub Actions quota is currently exhausted; use local fixture tests where
available. Build or deployment results MUST NOT be inferred from source review.
