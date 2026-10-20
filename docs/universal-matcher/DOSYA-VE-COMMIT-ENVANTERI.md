# Dosya ve commit envanteri

Dal: test/ea-fb-universal-matcher-v83-20261020
Temel: 16396702470eb1f81cc6dac4360e6b0ff84927d1

## Kod ve CI commitleri

1e8a9668a85fceb18351616e964e123807b78e26	docs(universal): record clean V83 baseline and general matching design
998d4cc668debbf69349b4370c2b00a0b41dc193	feat(universal): match metadata-derived titles and source identity across four adapters
4c5a26b0ea1bab7f6a1290c29edb94eeb9b8ce11	fix(universal): preserve playback query call compatibility
4784668e6a42d9ea246e238bc3f0ae9b4c3e275c	fix(universal): retain existing media offer constructor ABI
097cf50fee2addef6124b385d647fc3b9c73a071	fix(universal): report ambiguous page and episode decisions without URLs
14754e50262ef89af1250ef7797665ad76e83679	fix(universal): distinguish series premiere evidence from season year
9d56ba95a86c93097e9761958fa406841d87ddb1	build(universal): verify baseline, compare regressions and package 8601 in CI

## V83 üzerinde değişen/eklenen dosyalar

- .github/workflows/universal-matcher.yml
- EA-FB/build.gradle.kts
- EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt
- EA-FB/src/main/kotlin/com/eafb/CatalogMatchMetadata.kt
- EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt
- EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt
- EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt
- EA-FB/src/main/kotlin/com/eafb/EAProvider.kt
- EA-FB/src/main/kotlin/com/eafb/PlaybackLinkBridge.kt
- EA-FB/src/main/kotlin/com/eafb/PlaybackQuery.kt
- EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt
- EA-FB/src/main/kotlin/com/eafb/SourcePageIdentity.kt
- EA-FB/src/main/kotlin/com/eafb/SourceSearchTitles.kt
- EA-FB/src/main/kotlin/com/eafb/SourceTitleMatcher.kt
- core-tests/clean/BronzeBridgeTest.kt
- core-tests/clean/BronzeFilmSearchTest.kt
- core-tests/clean/CleanAdaptersTest.kt
- core-tests/clean/UniversalAdaptersTest.kt
- core-tests/clean/UniversalLiveReplayTest.kt
- core-tests/clean/UniversalMatcherTest.kt
- docs/universal-matcher/BASLANGIC-VE-V83-REFERANS.md
- docs/universal-matcher/CANLI-OKUMA.json
- docs/universal-matcher/DEGISIKLIK-DEFTERI.md
- docs/universal-matcher/GENEL-ESLESTIRME-TASARIMI.md
- docs/universal-matcher/GERI-ALMA-PLANI.md
- docs/universal-matcher/KARSILASTIRMA.json
- docs/universal-matcher/KORUNAN-UZAK-REFLER.tsv
- docs/universal-matcher/STAGING-METADATA-ONERISI.md
- docs/universal-matcher/TEST-SONUCLARI.md
- scripts/compare-universal-v83.sh
- scripts/package-universal.py
- scripts/test-bronze-nl-land.sh
- scripts/test-universal-matcher.sh
- scripts/verify-universal-v83.py
- tests/test_universal_generality.py
- tests/test_universal_guard.py
- tests/test_universal_package.py

Yayın dist-universal-8601-test altında altı yeni dosyadır; eski dağıtımlar diğer dallarında ve korunan V83 ağacında değişmez. Son yayın/kayıt commitleri sonraki kanıt kaydına eklenecek; son commit kendi SHA’sını içeremez, final rapor ve git log -1 onu gösterir.
