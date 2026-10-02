# EA-FB V6 — central successful-source memory release gate

Status: **RED staging code only; not deployed or live playback capable.**
Do not edit `main`/V5, BLUE staging or frozen V46. No Actions or production D1
migration is authorized by this document.

## Implemented on RED staging

- `PlaybackData` provides stable movie and exact episode identifiers for
  CloudStream metadata load responses. IDs are **not** playable stream URLs.
- D1 migration `0003_playback_success.sql` stores a short-lived mapping from
  TMDb movie/episode to source ID and variant ID, with no URLs, cookies, user
  identifiers, playback headers or tokens.
- `playback-success.mjs` reads, updates and expires private D1 candidates.
- `playback-candidate-service.mjs` orders eligible offers from current
  healthy, enabled, registry-approved sources that are **also** present in
  an independent, release-controlled rights grant list.
- `trusted-playback-recorder.mjs` requires an independently verified,
  fresh, source-specific playback observation and current registry health.
  The default has no verifier, rights grants or public route, so it writes
  nothing in a deployed release.

## Not implemented (do not represent as done)

- No actual trusted player-start evidence producer or anti-replay verifier.
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
4. Apply D1 migration only to an isolated staging DB, test with real D1,
   verify schema constraints and source/episode isolation.
5. Wire client read, local fresh link resolution, first-success candidate,
   fallback to remaining approved healthy adapters, and stale-candidate
   invalidation. Do not change a currently playing stream.
6. Run Node tests (`cd source-watchdog && npm test`), SQLite migration
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
