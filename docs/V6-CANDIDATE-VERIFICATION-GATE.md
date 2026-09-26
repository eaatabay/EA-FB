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

## 27 September — additional development checks

- New `CatalogPagePolicy` is exercised by 18 pure JVM assertions via `scripts/test-core.sh`. Its bounded page-2/page-3 scan is only eligible on an empty, valid first page of a newest discover feed; a scanned/fallback rail never advertises further pagination. This prevents a previous silent duplicate-card risk.
- Admin drafts now reject coercible genre/provider values and forged envelope fields, normalize contiguous order after edits, append new rails without moving existing ones, and preserve the edited rail's position on replace.
- `node source-watchdog/dev/catalog-draft-cli.mjs diff BEFORE.catalog-draft.json AFTER.catalog-draft.json` produces a metadata-only review report (added/removed/changed title, order, platform, language, year, enabled). This remains an **offline review tool**, not a remote publication endpoint.
- A standalone local copy of the new page-policy assertions passed 18/18. This does **not** establish that the full repository Node suite, Gradle build or device checks passed.

## 27 September — isolated checks and safety follow-up

- Locally compiled the pure Kotlin pagination policy against its actual 18-assertion test logic: **18/18 passed**. Independently compiled the catalog-shelf policy against minimal JVM stubs and checked six key behaviors: **6/6 passed**. These are isolated checks, not a complete Gradle build or repository test run.
- Added a Node regression guard comparing all 29 built-in Android home IDs with the admin catalog's reserved-ID validator, preventing a future newly added built-in rail from being overridden by an admin draft.
- Fixed the admin's zero-provider-ID acceptance, invalid filter coercion and forged draft-envelope rejection. Hardened Settings against recreating a finishing/destroyed Activity; selecting the already-active sort no longer triggers an unnecessary refresh.
- **Release remains blocked** pending full Node/Gradle verification and a red Mi Box smoke test. No Actions run, deployment or production change was requested or performed.

## 27 September — catalog activation boundary

- The public metadata projection now rejects an **enabled** shelf with language/year filters unsupported by the deployed relay. It may retain such filters in a **disabled** unpublished draft. This prevents a broader catalog from being presented as though the requested language/year restriction had been honored.
- The Android batch compiler now validates disabled shelf identity, title, provider, genre and year/language syntax before accepting the batch. Disabled shelves may retain valid, unsupported language/year constraints but cannot conceal malformed metadata until activation.
- Regression cases added for public projection, zero provider IDs, and invalid disabled Android shelf definitions. These commits remain on the isolated feature branch; no remote catalog publication or streaming grant is implied.
