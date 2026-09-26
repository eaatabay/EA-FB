# EA-FB v6 — candidate verification gate (feature branch only)

This is a **development checklist**, not a release authorization. Do not modify main, v5, production Worker/D1, rights grants or signing keys.

## Automated gate (Codespace on feature/detail-dual-ratings-v6)

1. Run `bash scripts/verify-v6.sh`. It covers Worker and Watchdog Node tests, Python migrations, local Wrangler/D1 fixtures, pure Kotlin policies, distribution checks and the local Gradle candidate build.
2. Inspect `source-watchdog/test/catalog-shelves.test.mjs`, `catalog-draft-cli.test.mjs`, `settings-refresh-wiring.test.mjs` and `detail-ratings-collection-wiring.test.mjs` for the latest changes. Node's test glob includes these files automatically.
3. Confirm `dist/EA-FB.cs3` is a nonempty, valid archive with manifest version 6. Do not install it on the user's Mi Box before the automated gate passes.

## Manual red CloudStream Pre-release checks

- Blue Stable remains on v5. Back up red v6 and install a **newly built** candidate only after automated verification.
- Enable Apple TV film/TV and Paramount+ film/TV switches. Verify TR-specific provider results without substituting other countries. A zero-result provider in TMDb's TR catalog is not a playback error.
- Test Popular / En Yeni / Puanı Yüksek, including future premieres, empty pages, platform filters, native trending, and pagination. Confirm the bounded newest scan doesn't duplicate cards.
- Change a single category, dismiss Settings and check whether the EA-FB home reloads. Confirm no double recreation after explicit refresh; verify remote focus on a physical Mi Box. A static code test cannot prove host focus preservation.
- Open Spider-Man: Homecoming and another official TMDb collection film. Verify the series note lists only visible installments in release order and no reboot mixing. The stock CloudStream detail still has one recommendations rail.
- Verify IMDb and TMDb independently on film/TV details. Without a reviewed v6 Worker deployment and a configured server-side OMDb key, IMDb may legitimately be absent; never display TMDb as IMDb. Small cards and TV episodes show TMDb only.
- Big Buck Bunny is the separate open-licensed playback test. Do not interpret metadata listings as authorization for any other movie's stream.
- Catalog admin CLI only manages local **unpublished** drafts. The compile-preview command does not publish to a remote config or grant playback rights.

## Release blockers

Automated test output, Gradle compilation, new cs3 package inspection, device validation, approved v6 Worker/OMDb deployment and reviewed remote catalog publication are **not established** by these feature-branch commits.
