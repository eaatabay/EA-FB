# EA-FB v6 – Mi Box review and navy/yellow settings

Last updated: 25.09.2026. Review observations came from the user's photos of **red CloudStream Pre-release** running a locally installed v6 package. The blue app and `main/dist` still run/publish v5.

## Confirmed by Mi Box review

- Portrait home posters, remote navigation up/down/left/right, provider search and Silo's episode cards work.
- Silo has season buttons 1–4 and TMDb episode scores. Its long-dated next-episode notice was buried in its plot.
- The original v6 detail view displayed the same TMDb score in native hero score, first line of the plot and a tag. Independent IMDb ratings cannot be fabricated from TMDb scores.
- EA-FB returns TMDb Spider-Man / Harry Potter / Star Wars collection films, but **native CloudStream has only one recommendation row**. The separate numbered, highlighted film-series row seen in the first photographs was PLT-Stream, not EA-FB.
- The review approved deep navy and vivid yellow, no red, for all EA-FB-owned settings UI; not for CloudStream's generic Extensions page.
- PLT's approximately 32 source switches are third-party film/series playback adapters, **not live TV**. EA-FB does not currently implement those adapters. BelgeselX rows are likewise not connected.

## Source changes on feature/detail-dual-ratings-v6 after Mi Box baseline

1. `EASettingsDialog.kt`: TV-remote-focusable, navy/yellow EA-FB wordmark, Kategoriler/Kaynaklar tabs, category switches, bulk actions, three sort modes. The Sources page truthfully shows zero integrated playback adapters instead of deceptive on/off buttons.
2. `EASettings.kt`: SharedPreferences persist switches and ordering preferences. Only actual TMDb-bound home categories are exposed as active switches; documentary-only placeholders are labeled pending. `EAProvider.mainPage` derives enabled sections when EA-FB is reopened.
3. `CatalogSortPolicy.kt`: platform and genre discover feeds sort by popular, latest (movie release date or TV first air date), or rating with at least 100 votes. Trending/top-rated/native now-playing categories preserve their meaningful source order.
4. `worker/src/index.js`: securely allowlists TV `first_air_date.desc` and `vote_count.gte` for the new discover sorting; tests added. **Deploy the updated Worker before releasing a client that emits these new query parameters**.
6. `assets/ea-fb-logo.png` and `assets/ea-fb-logo.svg`: matching navy/yellow logo assets; future staging now inserts the PNG as `iconUrl` in both repo and extension manifests. Main is untouched.
5. Detail rating cleanup: no unlabeled native hero score and no duplicate rating line in the plot; one set of explicitly named IMDb/TMDb chips when independent data exists. The next-episode date for far-off episodes moves to the plot's first line. Collection text becomes a short note rather than a long list.

## Pending – must not be presented as finished

- The new settings UI and updated Android v6 package have **not** been compiled, deployed or tested on Mi Box after these new edits. Earlier v6 baseline was compiled and tested; do not conflate the two.
- Worker updates have not been deployed. OMDb secret has not been confirmed; until it is configured, only real TMDb scores appear.
- A separately headed, TV-focusable `Serinin Filmleri` carousel, blue/current-film selection and unlimited horizontal navigation require CloudStream UI extension support or an application fork. Current standard `MovieLoadResponse` cannot create a second native rail.
- Logo PNG is present in the feature branch and release staging is configured, **but the public/main icon URL will not work until publication**. Repo card theming beyond the icon is controlled by CloudStream. Do not replace the v5 `main/dist` manifest with an untested package.
- Platform and genre feeds should be tested on-device, especially the new Worker sort paths. Actual BelgeselX feed, external source adapters and user choice of them need separate implementation and permission review.
- Styling the CloudStream-wide search screen, playback controls, sidebar or Extension manager requires modifying the host app, not just EA-FB's plugin.

## Safe release gate

Run `bash scripts/verify-v6.sh` on the **feature branch**, inspect Android UI on Mi Box, then deploy Worker changes, verify TV and movie discover sorts, configure/verify optional server-side OMDb if desired, and publish `dist` only after all checks. The blue/stable v5 must remain unaffected until a deliberate release.


## 26.09.2026 — v6-only regression fixes (source changes; not released)

- Added the Paramount+ **movie** shelf and its settings toggle; regional TMDb availability is not guaranteed. Apple TV+ movie/TV and Paramount+ TV IDs already existed. A regional TMDb empty response can still legitimately hide a shelf.
- Newest now rejects future and invalid premiere dates in the Android client, including fallback cards. Client requests deliberately omit new date-bound query parameters until the updated Worker is separately approved and deployed; the existing production relay must remain untouched. Consequently a newest TMDb page full of future titles can produce a labeled popular alternative rather than exhaustive newest results.
- A successful but empty regional discovery response can trigger a **clearly labeled** popular alternative on the first page. Network/relay failures are not silently relabeled as empty feeds.
- Film detail now displays the official collection's chronological title/year list in its plot when a real TMDb collection exists. CloudStream's stock detail UI still exposes one native recommendations rail; a separately titled series carousel requires host UI support and is not claimed as implemented.
- Real IMDb enrichment remains conditional on the **server-side** OMDb secret and Worker deployment. No synthetic IMDb values or credential changes were made.
- Automatic home refresh on settings changes and admin-configurable dynamic shelves remain open: the plugin currently stores preferences immediately but stock CloudStream owns the home screen lifecycle. Do not promise automatic focus-preserving reload without a verified host API.
- This section documents code changes, **not** a successful Kotlin/Android build, Mi Box visual test, Worker deployment, or new cs3 package.


## 27.09.2026 — offline catalog hardening (v6 branch only)

- Fixed a regression: future-premiere filtering now applies only to sortable discovery shelves, never to TMDb native trending/top-rated rows.
- Admin shelf validation now rejects IDs colliding with any built-in home category and accepts strictly bounded optional yearFrom/yearTo and two-letter original-language filters. Invalid year ranges, malformed filters, or playback-authorization fields fail closed.
- Added a pure Kotlin shelf-to-TMDb-route policy and offline tests. **Important:** the current production metadata relay does not support admin language/year filters; the client policy therefore refuses to activate shelves using those draft filters. The admin-to-Mi-Box publishing pipeline is not wired and no dynamic shelf is currently enabled.
- Detail IMDb badges now require an explicit OMDb API provenance marker and a valid TMDb-provided IMDb title ID. This protects display provenance but **does not** configure the production OMDb secret or prove that IMDb scores are available on the user's Mi Box.
- The explicit settings refresh now unwraps Android ContextWrapper to locate its Activity; TV host behavior and focus retention still require device testing.
- No production Worker, D1, permissions, keys, main, or v5 release changes. Build and offline suites must be run on the latest v6 branch before creating a new .cs3.

## 27.09.2026 — uninterrupted v6 development parkur

- Admin metadata shelf drafts now support **add, replace, remove, rename, reorder and enable/disable** as pure revision-checked operations. Drafts are deep-frozen and cannot silently grant streaming rights or publish themselves.
- Local-only `source-watchdog/dev/catalog-draft-cli.mjs` supports `init`, `edit` and `preview` for files ending `.catalog-draft.json`. It uses create-only initialization, an exclusive lock and atomic replacement for edits. Preview renders escaped navy/yellow HTML. This is **not a remote production admin endpoint**.
- Example: `node source-watchdog/dev/catalog-draft-cli.mjs init ./my.catalog-draft.json`; edit commands are local JSON files with `expectedRevision` and `operation` (for example `{"expectedRevision":0,"operation":{"action":"add","id":"custom-tv","shelf":{"id":"custom-tv","title":"Özel Diziler","kind":"tv","genres":"18","enabled":true,"order":0}}}`). `preview` prints HTML to stdout, which may be redirected to a local HTML file.
- Settings now marks changes as dirty and requests one Activity refresh on dialog dismissal; the explicit refresh button suppresses duplicate recreation. The host's focus restoration is **not verified on Mi Box**.
- A discover shelf whose first En Yeni page has no released artwork now scans at most two more pages before trying the clearly labeled popular fallback. Scanned first-page results are non-paginated to avoid duplicate page-2 cards. Native trending/top-rated feeds are never filtered by this discover-only rule.
- Fixed a serious Worker bug in the new strict ISO date regex (it previously had over-escaped digits); added leap-day and invalid-year test cases. Production Worker is unchanged.
- New Node regression tests cover offline draft CLI locking, create-only initialization, CAS mutations, preview escaping and Android source wiring. **No test execution or Android build has been confirmed for these commits.** Do not release a new cs3 until the complete v6 verification suite passes.
