# EA-FB — step 6: real source integration review (2026-10-03)

## Release boundary
- Target branch: `develop/post-v49`. Do not alter frozen V49, V5, production Worker/D1, or release a CS3.
- A source's presence in a catalog or a public web page is **not** permission to restream its video.
- A media provider may enter `releaseApprovedIds` only after **both** source API/use terms and the rights to access/play the specific media have been checked.
- `userEnabledIds` and fresh `healthyIds` are additional independent requirements. Neither confers rights.
- No DRM circumvention, credential/session sharing, user-specific link caching, or unofficial bypass endpoints.
- No source is release-approved by this review. Existing production bridge remains empty and fail-closed.

## Candidate 1 — Internet Archive (research candidate, NOT approved)
- Official item search endpoint: `https://archive.org/advancedsearch.php` (query, fields, paging).
- Official metadata read endpoint: `https://archive.org/metadata/{identifier}`.
- The metadata model includes `licenseurl` and `rights` fields, but neither is guaranteed to be present or independently verified.
- A safe adapter must accept only individually allowlisted item identifiers with documented rights evidence, allowed territories and media file URLs, plus a verified direct playback format. Never infer rights from `mediatype:movies`, public accessibility, a title match, or a Creative Commons-looking search result.
- Search-to-TMDb matching must enforce exact movie/year or exact series season/episode; archive identifiers are not TMDb identifiers. Unknown mappings fail closed.
- Metadata alone is insufficient to assert an actual working video URL or playable license.

References:
- https://doc-tools.readthedocs.io/en/ia-test-gsod/item-search-apis.html
- https://doc-tools.readthedocs.io/en/ia-test-gsod/md-read.html
- https://internetarchive.readthedocs.io/en/stable/metadata.html

## Candidate 2 — CloudStream official extensions (reference only)
- Upstream extension repo lists an InternetArchiveProvider, plus YouTube/Twitch and others.
- An existing extension's implementation is a useful interface reference; it is **not** permission to copy its source or to access every listed media item.
- Review the actual upstream provider license and per-item playback terms before reuse.
- https://github.com/recloudstream/extensions
- https://recloudstream.github.io/csdocs/devs/gettingstarted/

## Candidate 3 — commercial streaming catalogs
- TMDb availability, provider logos, and catalog metadata do not provide playable streams or authorize third-party playback.
- Do not claim Netflix, Prime Video, Disney+, etc. as integrated without provider authorization and a documented playback API.
- Do not treat scraped mirrors as approved merely because a public page or direct video URL exists.

## Acceptance gates for any real adapter
1. Named provider, stable endpoint, documented API/terms, rights holder or valid permission evidence.
2. An item-level allowlist with provenance, permitted usage, geography, and expiry/revocation handling.
3. Correct identity matching (TMDb/title/year, exact series season+episode), no cross-title fallback.
4. HTTPS, timeouts, URL validation, no session-private links, no shared credential leakage.
5. Automated unit tests, actual network contract test against authorized sample media, Android build, then Mi Box playback verification.
6. Only after review, add adapter to an explicitly reviewed release registry. No remotely supplied flag can grant release rights.

## Status
**Step 6 remains OPEN.** API candidates are documented; no source's content rights or live playable endpoint have been verified, and no adapter has been registered. Do not report real film/episode playback as complete.

## DiziMom — decompiled PLT-KOD audit (2026-10-03)
- The supplied decompiled `DiziMomExtractor.java` includes `searchWithAjax`, `searchWithPage` and an `extract` method. Previous shorthand that DiziMom's extraction path was independently validated was **incorrect**: JADX output includes tangled coroutine state-machine control flow, so method presence does not prove executable correctness or a reproducible host resolver.
- `searchWithPage` references `div.single-item`, `div.categorytitle a`, `div.cat-img a` and a `Yapım Yılı` pattern. These are observations from decompiled PLT, **not** verified against the current live site.
- The current EA-FB `DiziMomAdapter` is a separately written, inactive candidate. It returns no `SourceLink`; no approved playback endpoint, item rights, live network contract, or Mi Box result exists.
- Do not infer that PLT's use of Cloudflare interception, Ajax nonce or embedded host player grants authorization to reproduce those methods. No such bypass is part of the EA-FB candidate.
- Next acceptance prerequisite: a documented permitted playback API and an item with verifiable playback rights; then implement and test an independent resolver. Until then keep `PlaybackLinkBridge` empty and step 6 OPEN.

## Decompiled nested resolver finding (2026-10-03)
- Inspected `PLT-KOD/sources/com/pltmustafa/pltstream/extractors/sites/DiziMomExtractor$extract$2$1$1.java`, not just the outer `DiziMomExtractor.java`.
- The nested `invokeSuspend` contains a JADX `JadxOverflowException`, `Method dump skipped, instruction units count: 3504`, and ends in `UnsupportedOperationException("Method not decompiled...")`. The outer `extract` method existing is **not** evidence that the video resolver has been recovered.
- The current independent DiziMom candidate has no resolved streams and must remain unregistered. Recovering the nested logic would require a separate source/bytecode analysis and lawful, documented endpoint and media rights review; never treat a decompiler stub as functioning extraction.
