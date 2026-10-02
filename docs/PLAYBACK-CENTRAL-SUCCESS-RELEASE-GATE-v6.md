# EA-FB V6 — central successful-source memory release gate

Status: **RED staging code only; not deployed or live playback capable.**
Do not edit `main`/V5, BLUE staging or frozen V46. No Actions or production D1
migration is authorized by this document.

## Implemented on RED staging

- `PlaybackData` provides stable movie and exact episode identifiers for
  CloudStream metadata load responses. IDs are **not** playable stream URLs.
- D1 migrations `0003_playback_success.sql` and `0004_playback_retention.sql` store a short-lived mapping from
  TMDb movie/episode to source ID and variant ID, with no URLs, cookies, user
  identifiers, playback headers or tokens.
- `playback-success.mjs` reads, updates and expires private D1 candidates.
- `playback-retention.mjs` offers bounded, manual candidate and replay-receipt
  expiry cleanup; no timer is enabled.
- `playback-candidate-service.mjs` orders eligible offers from current
  healthy, enabled, registry-approved sources that are **also** present in
  an independent, release-controlled rights grant list.
- `PlaybackQuery` and `SourceEngine` preserve exact season/episode matching
  for future adapter resolution; the engine can try a preferred approved
  source first and fall back after an empty result, exception or timeout.
  No adapters are currently wired.
- `trusted-playback-recorder.mjs` requires an independently verified,
  fresh, source-specific playback observation and current registry health.
  The private `playback-observer-proof.mjs` verifies HMAC-SHA256 signatures
  from an independently trusted server-side observer, checks event age and
  consumes D1 one-time receipts. Signatures bind media, source, variant,
  language, quality and outcome. `playback-observer-service.mjs` composes
  that proof with the rights/health-gated recorder. There is no signing
  observer, server key, rights grant or public route in the release.

## Not implemented (do not represent as done)

- No actual trusted player-start evidence producer. The private HMAC proof
  verifier exists, but cannot prove a client played a video unless an
  independently trusted observer establishes playback before signing.
- No authenticated consumer route for reading the short-lived candidate
  identities from the CloudStream client.
- No app-side candidate-to-adapter resolution and automatic fallback.
- No production D1 binding, migration or live Worker publication.
- No rights-reviewed third-party playback adapters bundled in this release.
- No completed Android build or Mi Box real-playback acceptance test.
- Newly committed Node and Kotlin regression suites have not yet been
  executed end-to-end against the current RED branch; code presence is not
  a passing test result.

## Before enabling central shared success

1. Confirm source distribution rights and release-pin exact permitted
   adapter IDs, versions, hosts and paths. A D1 admin flag is insufficient.
2. Design a trusted playback-start attestation that distinguishes actual
   playback from discovery or player-open. It must reject spoofing, replay,
   expired events and media/source/variant mismatches. No plaintext
   user-specific playback links in telemetry or D1.
3. Define authenticated read and write boundaries with bounded per-client
   request rates, safe cache TTL and minimal event retention.
4. Apply D1 migrations 0003–0005 only to an isolated staging DB, test with real D1,
   verify schema constraints and source/episode isolation.
5. Wire client read, local fresh link resolution, first-success candidate,
   fallback to remaining approved healthy adapters, and stale-candidate
   invalidation. Do not change a currently playing stream.
6. Run offline playback checks (`bash scripts/test-playback-v6.sh`),
   full Node tests (`cd source-watchdog && npm test`), SQLite migration
   tests (`python3 -m unittest discover -s source-watchdog/tests -v`),
   Kotlin core tests and an Android build without GitHub Actions.
7. On Mi Box, verify distinct episodes, dubbing preference, fallback after
   a broken source, shared cross-device success and no playback data stored
   persistently on the box. Record timings before making speed claims.

## Important invariants

- Source found != video successfully started.
- Success on one device is a **candidate**, never a guarantee on another.
- Per-user entitlements, short-lived URLs, signed URLs and session tokens
  must never become shared playable links.
- Unapproved or unhealthy source cannot be resurrected by history.
- Failure expires only the matching movie/episode/source/variant candidate.
- Missing verifier, grants, healthy registry or D1 means fail closed, not
  silent bypass.

## 2026-10-02 — stale next-airing detail regression

The reported **Cennetin Doğusu** example showed a 1 October 2026
"Sonraki bölüm" date on 2 October, despite episode 7 already appearing
in the episode list. This is a *metadata display bug*, not evidence of
successful playback. The detail label now uses
`EpisodeAirPolicy.nextAirDateLabel` (same future-only rule as the
native `nextAiring` property). `DetailMetaRow` also clears its own
recycled next-airing holder when the provider has no future date,
including after a detail page remains open across the airing-day boundary.
The offline pure-Kotlin date regression passed locally for seven
future/past/invalid cases across UTC and Europe/Istanbul; a full Android/UI build and Mi Box visual
check remain required. No live staging publish was performed.


## Next-airing metadata correction — 2026-10-02

`EAProvider.load` now selects the nearest **future calendar day** across
TMDb's `next_episode_to_air` hint and the already fetched per-season episode
rows, instead of trusting the hint alone. Episodes with missing air dates are
not guessed. Special season 0, invalid episode numbers, already aired dates,
and same-day batch releases are excluded from the future-date label.
The native CloudStream `nextAiring` and the custom detail label use the same
selected episode. A stale view is cleared and the label expires when its date
arrives. Tests cover weekly releases, finales, same-day batches, stale TMDb
hints, multiple future dates, and Türkiye/UTC local calendar boundaries.

A **manually transcribed copy of the pure Kotlin policy** passed 22 local
checks; this is not a full repository build, staging CS3 build, deployment or
Mi Box verification. Do not claim the current compiled staging package
contains these changes until an actual V6-only Android build is produced and
published. No main/V5/blue/frozen V46 release is modified here.


### V47 RED-only publishing handoff

The current published RED staging manifest remains V46. V47 is not published
merely by changing Kotlin source or committing a release script.
`scripts/publish-v47-red-staging.sh --publish` is a deliberately explicit
Codespaces-only build/publish command: it checks the exact red branch and
clean synchronized checkout, verifies the frozen V46 manifest, uses a
temporary **V47** staging-only overlay, compiles and verifies the CS3, and
pushes only the three `dist-v6-staging/` files. The default overlay still
builds frozen V46, and production V5/main/blue remain unchanged.
No GitHub Actions minutes, Cloudflare Worker deployment, source grants,
or D1 migration are required for this metadata-only client change.

**The command has not been executed from this chat.** The Android SDK/Gradle
build, actual Git push, and Mi Box acceptance must succeed before marking
V47 published. If any gate fails, the script stops without claiming success.
