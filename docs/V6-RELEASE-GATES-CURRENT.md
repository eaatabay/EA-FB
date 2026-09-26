# EA-FB v6 — current release gates (26.09.2026)

This file is the concise **current** status. Historical parkur entries in
MASTER-CHECKLIST-v6.md and DUAL-RATINGS-v6.md are chronological; earlier
descriptions of the hero score or test counts may be superseded.

## Development status
- Branch: `feature/detail-dual-ratings-v6` only. Stable `main`/v5 unchanged.
- Current detail UI: IMDb and TMDb are **separately labeled CloudStream tags**.
  The native single hero score is deliberately unset in current source to avoid
  a misleading duplicate. Exact placement next to the year is **not guaranteed**
  by stock CloudStream and needs Mi Box visual verification.
- Small catalog cards: TMDb score only when vote data is real and available.
  Posterless entries are omitted; backdrop may replace a missing poster.
- Official TMDb film collection: chronologically sorted cards at the beginning
  of the existing Recommendations rail, not a separately headed native rail.
- Episodes: ratings are TMDb episode scores when voted; never IMDb.
- Metadata relay: `CatalogRelayPolicy` accepts only the reviewed compiled
  `ea-fb-catalog.eaatabay.workers.dev` origin with `status=ready`.
  A previously pinned origin can survive a config-fetch outage for 24h;
  an explicit disabled config cannot be bypassed with the cache.
- Live stream URL policy: reject literal private/loopback IPs, local
  hostnames, explicit ports, fragments and path traversal; signed HLS query
  parameters remain allowed. This is URL hygiene, **not** DNS/IP pinning
  or proof of distribution rights. Current channel list is empty.
- Source Watchdog scheduler: mismatched config/state IDs and incomplete
  incident metadata fail closed; legitimate first incident remains eligible.
- Source Watchdog: no live signing pins, approved source grants or production
  adapters are compiled into the public plugin. No live probing enabled.

## Verified locally in isolated reconstruction this parkur
- Kotlin HTTPS transport: 6/6 smoke checks.
- Kotlin signed refresh: 5/5 smoke checks, including cancellation races.
- Kotlin failed SharedPreferences commit and second-store guard: 7/7 smoke checks.
- Kotlin metadata relay pin: 18/18 pure policy checks.
- Kotlin live URL policy: 23/23 locally reconstructed full policy
  assertions, including channel merging and private-address rejection.
- Node relay wiring: 3/3 equivalent static checks.
- Node scheduler: 6/6 locally reconstructed policy checks, including
  mismatched identity, missing incident timestamps and original priorities.
These are **not** a claim that the exact complete GitHub checkout or Android
Gradle build passed. Source changes and new regression test files were committed
to the v6 branch and should be run together in a full checkout.

## Blocking verification before any release
1. Full exact checkout: `bash scripts/test-core.sh`,
   `cd worker && npm test`, `cd source-watchdog && npm test`,
   Python test suites and `bash scripts/verify-v6.sh`.
2. Real local Wrangler/workerd + D1 migrations, Cron, leases and signed
   snapshot replay, without deploying a Worker.
3. Android Gradle build and local .cs3 validation, then Mi Box beta:
   dual source-labeled scores, poster coverage, series order, settings
   persistence, D-pad navigation, slow HTTPS and offline cache recovery.
4. Review rights evidence, actual approved adapters, signing keys,
   Cloudflare Access/MFA and source network IP-pinning before any live
   Watchdog activation.
5. Separate explicit owner approval before changing main, v5, production
   Worker/D1, secrets, signing keys, deployment or release.

GitHub Actions quota was previously exhausted. Do not trigger CI merely
to validate these source-only changes until the owner approves.
