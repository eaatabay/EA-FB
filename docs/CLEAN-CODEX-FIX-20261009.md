# Isolated CODEX CLEAN fix

Base: `test/v63-bronze-cleanroom-20261008`, commit `c53ee48c7bbca37aa1bec85ca47668c13bcdd899`.
Work only on `test/clean-codex-fix-20261009`. Source/package version: 66.

## Scope and installation

- Bundled adapters: DiziBox and DiziYou. NL and LAND are not added or claimed to work.
- Name: **EA-FB CODEX CLEAN TEST**; internalName: `EA-FB-CODEX-CLEAN-20261009`.
- Preference store: `ea_fb_clean_codex_fix_20261009`. Existing explicit choices are copied once from the V64 legacy store; that store is never written. Sources remain opt-in.
- New test repository: https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-fix-20261009/dist-clean-codex-fix-20261009/repo.json
- Do not use inherited `dist-v63-clean`, `dist-v6-staging`, `dist`, or their update URLs for this test. They remain inherited historical files; the new package does not update their provider IDs.
- `build-info.json` records the compiled source commit, bundled adapter IDs and DEX hash. Feed size/hash are computed after final manifest rewriting; package verification checks DEX integrity and defined classes.

## Changes

1. Isolated provider/manifest/settings identity, matching UI hooks, read-only legacy settings migration.
2. Keep explicitly identified adaptive HLS masters across the automatic quality filter. Fixed 4K files still obey the quality cap. Adaptive master playback can choose a higher variant; this change does not enforce a 1080 hardware limit inside the player.
3. Incremental adapter results survive a later adapter timeout; external cancellation still propagates.
4. DiziBox resolves the main player before alternate pages; King/Moly/Haydi and generic extractors remain available. Extractor headers, HLS type and late subtitles survive the SourceLink conversion.
5. DiziYou retains AJAX success, adds Bronze v27 HTML GET fallback, and restores option-specific subtitle URLs. Both adapters try the original metadata title after the localized title and preserve exact episode matching.

## Validation and publication

Run `bash scripts/test-clean-codex.sh` with Kotlin 2.4.0 and Java installed. It verifies pinned test-only JSoup/JSON jars and runs the actual adapters/bridge with fake HTTP and extractor callbacks; no live source requests are made. Test stubs are outside the Android source set.

Android build: `bash scripts/prepare-cloudstream-gradle.sh`, then `gradle :EA-FB:make --no-daemon`.
Package: `python3 scripts/package-clean-codex.py EA-FB/build/EA-FB.cs3`.
Verify: `python3 scripts/package-clean-codex.py --verify`.

The new `.github/workflows/clean-codex-isolated.yml` tests, builds and publishes only `dist-clean-codex-fix-20261009` to this new branch. It is guarded by the repository and exact branch ref, uses a normal push, and never deploys Cloudflare. Existing workflows are unchanged.

## Known limitations / test history

- The original `scripts/test-playback-v6.sh` ran 26 Node tests and 9 isolated SQLite tests successfully, then failed because its Kotlin invocation does not include an Android Log stub. This inherited runner remains unchanged; the new runner supplies test stubs.
- The original DiziYou parser test expected a query string to be rejected, although the base CLEAN parser already accepts it. The expectation was corrected without changing the parser; query/fragment, valid subdomain and invalid host/userinfo/HTTP cases are tested.
- Local Android compilation succeeded. Gradle/SDK/deprecation warnings and D8 Kotlin metadata rewrite warnings were observed; a successful DEX build does not prove runtime compatibility on Mi Box.
- Physical Mi Box playback has not been tested. Check installed package hash/provider name, source switches, the same season/episode in Bronze vs this provider, video/audio/subtitle segments, seeking and playback over time before claiming success.
- No previous Mi Box logs or the Claude review package were available. They are not presented as reviewed evidence.
