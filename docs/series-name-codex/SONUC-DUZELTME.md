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
- Android yerel derleme başarılı. CI sonuç/hash bölümü dağıtım doğrulaması sonrasında kesinleştirilecek.

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
