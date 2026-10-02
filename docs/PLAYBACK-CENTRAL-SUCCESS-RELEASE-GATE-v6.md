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
