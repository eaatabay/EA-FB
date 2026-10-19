# Dizi isim eşleştirmesi — 8501 düzeltme raporu

## Kanıt ve kapsam
EA-FB katalog JSON'u TV 320754 / IMDb tt38732131 / 2026 / S1 / 8 bölüm kimliğini doğruladı. name ve original_name aynı kısa adı taşıyor; doğrulanmış uzun sezon/alternatif adı mevcut adaylara iletilmiyordu. Canlı DiziYou kısa aramaya uzun adı döndürüyordu. Bu uyuşmazlık gerçek metadata üzerinden eski kapıda yeniden üretildi.

SourceSearchTitles şimdi yalnız bu kimliğe uzun tam adı ekler. Genel contains/substring, kelime sırası veya The kuralı dizilere yayılmadı. 2005 Organize İşler (movie 30634) ve 2019 Sazan Sarmalı (movie 577990) kapsam dışıdır. Yeni HTML kabulünde TVSeries, tam ad, 2026 yılı ve sayfa URL'si; Bronze'da TV türü, pozitif yıl ve tam bölüm; bütün yeni yollarda belirsizlik reddi korunur. Diğer dizilerin/filmlerin eski aday sırası ve oynatma zinciri korunur.

## Çalıştırılan doğrulamalar
- Alias kimlik sözleşmesi: doğru S1 E1..8 kabul; başka ID/kind/yıl/sezon/episode/başlık ret.
- 59 replay assertion + 36 yeni kaynak güvenliği assertion + 37 profil/kurulum assertion PASS.
- 12 mevcut Kotlin programı (film alias testleri dahil), 7 mevcut bağımsız Python paket testi PASS.
- V83 baseline arşivinde eski 7 V83 paket/scope testi değiştirilmeden PASS. Yeni çalışma için eski test kaldırılmadı; arşiv V83, yeni guard 8501'i ayrı doğrular.
- Yeni üç paket testi PASS; gerçek UI DEX komutu değiştirilip DEX checksumları yeniden hesaplandığında koruma reddetti.
- Gerçek DiziYou adapter lookup: 4 HTTP isteğiyle doğrulanmış tek S1E8 teklifi; extractor, iframe, playlist veya video çağrısı yok. LIVE-DIZIYOU-LOOKUP.json kayıtlı. Diğer üç kaynak için fixture kanıtı var; canlı stream iddiası yok.
- Android yerel derleme başarılı. CI 39316844187 success; tam loglar incelendi. GitHub raw yayın/hash doğrulaması PASS.

## V83 / UI / rollback
V83 dal HEAD'i ve CS3 hash'i korunur. Yeni dalda 305 temel dosya aynı, yedi dosya tam onaylı hash'lerle doğrulanır; arama dışı metot bölgeleri karşılaştırılır. UI kaynakları, EAProvider, EASettings, Domain, SourceEngine ve PlaybackLinkBridge değiştirilmedi. Son DEX'te 2028 korunan V83 metodu (UI'da yalnız tam isim sabiti normalize edilir) ve 250 özgün Bronze metodu korundu.

8501 test paketinin internalName/provider/store alanı ayrı. Eski tercih alanlarına taşıma/yazma yok. Ayar/UI/provider öncesi native UID ve manifest envanteri gate'i ana profili ve başlangıçtaki aynı profil EA-FB çakışmasını reddeder. Bu, iki EA-FB'nin aynı profilde beraber güvenli çalıştığı iddiası değildir. Ayrı Android kullanıcı profilindeki ayrı süreç/veri alanı zorunludur. Yeniden adlandırılmış CS3 de entry point'ten tanınır.

**Ana Mi Box profiline yüklemeyin.** Cihazda ayrı profil desteği veya gerçek oynatma doğrulanmadı. UI kaynakları değiştirilmeden, çalışan V83'ün bulunduğu sürece girilmesi engellendi. Aynı test profiline çalışma sırasında ikinci EA-FB kurmak desteklenmez; runtime birlikte çalışma garantisi yoktur.

Geri alma yalnız test profilindeki yeni internalName ve yeni depo URL'sinin kaldırılması, ardından gerekirse yeni GitHub dalının silinmesidir. Ana V83'ü kaldırma/veri temizleme/reset/force-push/cherry-pick gerekmez.

## Yeni dalın değişen dosyaları (CI kod commit'ine kadar)
- .github/workflows/series-name-catalog-diagnosis.yml
- .github/workflows/series-name-diagnostics.yml
- EA-FB/build.gradle.kts
- EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt
- EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt
- EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt
- EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt
- EA-FB/src/main/kotlin/com/eafb/EAPlugin.kt
- EA-FB/src/main/kotlin/com/eafb/SeriesTestIsolation.kt
- EA-FB/src/main/kotlin/com/eafb/SourceSearchTitles.kt
- core-tests/clean/SeriesAliasContractTest.kt
- core-tests/clean/SeriesAliasSafetyTest.kt
- core-tests/clean/SeriesIsolationTest.kt
- core-tests/clean/SeriesLiveLookupTest.kt
- core-tests/clean/SeriesNameDiagnosisTest.kt
- docs/series-name-codex/BASLANGIC.md
- docs/series-name-codex/BASLIK-KANITI.json
- docs/series-name-codex/CI-39309504108-INCELEME.json
- docs/series-name-codex/DEGISIKLIK-DEFTERI.md
- docs/series-name-codex/DEVAM-NOTLARI.md
- docs/series-name-codex/DIZIYOU-GOZLEM.json
- docs/series-name-codex/EA-FB-KATALOG-GOZLEM.json
- docs/series-name-codex/GERI-ALMA-PLANI.md
- docs/series-name-codex/LIVE-DIZIYOU-LOOKUP.json
- docs/series-name-codex/SON-CI-INCELEMESI.json
- docs/series-name-codex/SON-DURUM.json
- docs/series-name-codex/SONUC-TESHIS.md
- docs/series-name-codex/SURUM-TARAMASI.json
- docs/series-name-codex/TEST-KURULUMU.md
- docs/series-name-codex/UI-HOOK-INCELEMESI.json
- docs/series-name-codex/UI-HOOK-RISKI.md
- docs/series-name-codex/UZAK-DALLAR-BASLANGIC.tsv
- docs/series-name-codex/UZAK-DALLAR-SON.tsv
- scripts/diagnose-series-catalog.py
- scripts/package-series-codex.py
- scripts/test-series-name-codex.sh
- scripts/verify-series-v83-baseline.py
- tests/test_series_catalog_diagnosis.py
- tests/test_series_package.py
- tests/test_series_v83_baseline.py

Son dağıtım/sonuç belgeleri ve yeni dist-series-codex-8501-test dosyaları yayımlama kaydında ayrıca listelenir.

## Kesin yayın sonucu
- Kaynak kod: [9aa829f1b494498762e02c16e5e6ad0f6c7de297](https://github.com/eaatabay/EA-FB/commit/9aa829f1b494498762e02c16e5e6ad0f6c7de297).
- Dağıtım: [eba59a87802695772750582c505b5fe0abf6d0c6](https://github.com/eaatabay/EA-FB/commit/eba59a87802695772750582c505b5fe0abf6d0c6).
- CI: https://github.com/eaatabay/EA-FB/actions/runs/39316844187 — success; paket/koruma ve üç negatif paket testi dahil.
- CS3 332876 byte / SHA-256 `f2b2d28a8a6adf5fbdfe8eae91fb70f36c346f658c3dbf7bcc91aae55fe12a3b`.
- Depo manifesti: https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-series-name-codex-20261019/dist-series-codex-8501-test/repo.json
- Altı gerçek raw dosya indirildi; manifest, DEX ve SHA-256 yeniden doğrulandı.
- Aynı CI hash'i yerel paketle eşleşti; artifact blob yönlendirmesi bu ortamda engelli olduğundan CI ikili artifact'inin doğrudan indirilmiş olduğu iddia edilmiyor. Yayınlanan ikili doğrudan GitHub raw'dan doğrulandı.
- Sonuç: yazılım düzeltmesi ve kontrollü test yayını tamamlandı. **Ana profil engelli; yalnız ayrı test profili. Mi Box gerçek cihaz testi yapılmadı.**

## Bütün commit bağlantıları
| Tam SHA | İşlem | GitHub |
|---|---|---|
| `45956ae74fdc512fa838ed6e99112a41baba9187` | docs(series): record V83 baseline and rollback boundaries | [commit](https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187) |
| `069824f8433792f8f5dee2b7079390ee1f9e0374` | test(series): isolate V83 title rejection across four adapters | [commit](https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374) |
| `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` | test(series): enforce byte-level V83 baseline preservation | [commit](https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6) |
| `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` | ci(series): add branch-scoped diagnostics without publication | [commit](https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099) |
| `87344068c46877c720e8f1afa53084cc06400fab` | docs(series): record verified season alias and obsolete branch cleanup | [commit](https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab) |
| `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` | test(series): track researched TV identity separately from films | [commit](https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4) |
| `63782dbd89fc52d4382a973c82fac67ca442ad75` | docs(series): audit V83 UI hooks and preserve diagnostic handoff | [commit](https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75) |
| `22922df2f887c6c14cf687bb63bd13118476bb22` | test(series): probe approved catalogue identity from read-only CI | [commit](https://github.com/eaatabay/EA-FB/commit/22922df2f887c6c14cf687bb63bd13118476bb22) |
| `6433b1fcf5a88de40412bbe852112eb15bd90950` | test(series): replay confirmed catalogue identity through V83 title gates | [commit](https://github.com/eaatabay/EA-FB/commit/6433b1fcf5a88de40412bbe852112eb15bd90950) |
| `1aa79597420845beb18afa58849c917a9499beab` | docs(series): finalize confirmed diagnosis and authentication handoff | [commit](https://github.com/eaatabay/EA-FB/commit/1aa79597420845beb18afa58849c917a9499beab) |
| `f0abf3b1d800b344984167a5ffd77d6cc65fcb99` | docs(series): confirm restored access and successful final CI | [commit](https://github.com/eaatabay/EA-FB/commit/f0abf3b1d800b344984167a5ffd77d6cc65fcb99) |
| `732d4613854042587a9fd0526cfd11163f6451c4` | test(series): reproduce missing confirmed season alias before correction | [commit](https://github.com/eaatabay/EA-FB/commit/732d4613854042587a9fd0526cfd11163f6451c4) |
| `1ac4de36b50b28c5313ea3c3d71e2b802f7e051a` | fix(series): add corroborated alias only for confirmed Organize Isler season | [commit](https://github.com/eaatabay/EA-FB/commit/1ac4de36b50b28c5313ea3c3d71e2b802f7e051a) |
| `9846add14a62fd118ccbfb874131d46fd6eb4c70` | test(series): reject ambiguous or uncorroborated alias source pages | [commit](https://github.com/eaatabay/EA-FB/commit/9846add14a62fd118ccbfb874131d46fd6eb4c70) |
| `b0094da7a3daab69c45894ad2da4776678fd75eb` | feat(series-test): isolate version 8501 and reject primary-profile loading | [commit](https://github.com/eaatabay/EA-FB/commit/b0094da7a3daab69c45894ad2da4776678fd75eb) |
| `bb0dc17706c29ac3e3ac037253983553036d6493` | fix(series-test): detect renamed EA-FB plugins before initialization | [commit](https://github.com/eaatabay/EA-FB/commit/bb0dc17706c29ac3e3ac037253983553036d6493) |
| `9aa829f1b494498762e02c16e5e6ad0f6c7de297` | build(series-test): verify isolated CS3 bytes and protected V83 DEX | [commit](https://github.com/eaatabay/EA-FB/commit/9aa829f1b494498762e02c16e5e6ad0f6c7de297) |
| `eba59a87802695772750582c505b5fe0abf6d0c6` | release(series-test): stage verified isolated 8501 package | [commit](https://github.com/eaatabay/EA-FB/commit/eba59a87802695772750582c505b5fe0abf6d0c6) |

