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
