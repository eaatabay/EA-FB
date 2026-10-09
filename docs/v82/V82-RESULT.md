# EA-FB V82 TEST — İsim temizliği sonuç raporu

Tarih: 10 Ekim 2026, Europe/Istanbul. V81'in dört kaynakla Mi Box'ta çalıştığı kullanıcı tarafından bildirilmiştir. **V82 için bu görevde Mi Box testi yapılmadı.** Bu sürüm isim/kimlik temizliğidir; yeni extractor veya kaynak çözümleme geliştirmesi içermez.

## Temel, dal ve yayın kimliği

- Temel ve korunan V81 commit'i: `88d7966fedf5150c38e4c898da86c482f1284b70`.
- V81 arşiv/geliştirme Git ağacı: `f1c42c56735b2135182170f5db2debe018b93d2b`.
- Yeni dal: `test/ea-fb-v82-name-cleanup`.
- V82 uygulama/derleme kaynak commit'i: `bee0fb3b7dec73efe67f713ba943659cbab40036`.
- Yayın commit'i: `8310fa667147b2fe8627673d1f9b72fc40871de2`. Sonuç belgesinin sonraki commit'i, paketin kaynak commit'iyle karıştırılmamalıdır.
- Provider/name: **EA-FB V82 TEST**, manifest version **82**, internalName **EA-FB-V82-TEST**.
- Ayrı dağıtım klasörü: `dist-v82-test/`; tek yayın CS3: `EA-FB-V82-TEST.cs3`.
- Ayrı ayar alanı: `ea_fb_v82_test`; V81 `ea_fb_clean_bronze_nl_land_20261009` yalnızca okunur. Mevcut migration mekanizması değiştirilmedi; kimlik yapılandırması V81'in dört kaynak tercihini yeni alana bir kez aktarır. V81 ayarları ve kurulumu üzerine yazılmaz.

## Kullanıcıya görünen adlar

| Eski | Yeni |
|---|---|
| EA-FB CODEX BRONZE NL LAND TEST | **EA-FB V82 TEST** |
| LAND (Bronze v4) • SetPlay (Türkçe Dublaj) 1080p | **HDFilmCehennemi LAND • SetPlay (Türkçe Dublaj) 1080p** |
| NL (Bronze v52) • Close DUAL | **HDFilmCehennemi • Close DUAL** |
| NL (Bronze v52) • Rapidrame DUAL | **HDFilmCehennemi • Rapidrame DUAL** |

Adapter display prefix, ayar etiketi ve EAProvider extractor source etiketi aynı HDFilmCehennemi / HDFilmCehennemi LAND isimlerine getirildi. İç extractor link.name bilgisi kesilmez/değiştirilmez: SetPlay, Close, Rapidrame, dil/dublaj/altyazı ve mevcut kalite bilgisi aynen taşınır. Kaynak etiketi haricinde callback dönüştürme kodu değiştirilmedi.

## NL çözünürlük kararı

Mevcut `SourceLink.quality` ve EAProvider quality aktarımı aynen korundu. Özgün NL local-source callback zincirinde `Qualities.Unknown` ataması bulunur; bilinmeyen kalite gerçek 720p/1080p gibi yorumlanmadı. Güvenilir kalite bir extractor tarafından zaten döndürülüyorsa mevcut CloudStream kalite gösterimi devam eder. Yeni bir çözünürlük metni, tahmin, playlist isteği, parser, stream indirme veya gecikme eklenmedi. Sırf çözünürlük göstermek için DEX/extractor mantığına dokunulmadı.

## Değişen dosyalar

- `.github/workflows/v82-test.yml`
- `EA-FB/build.gradle.kts`
- `EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt`
- `EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt`
- `EA-FB/src/main/kotlin/com/eafb/EAProvider.kt`
- `EA-FB/src/main/kotlin/com/eafb/EASettingsDialog.kt`
- `core-tests/clean/BronzeAdaptersTest.kt`
- `core-tests/clean/CleanSettingsTest.kt`
- `dist-v82-test/EA-FB-V82-TEST.cs3`
- `dist-v82-test/SOURCE-LICENSE`
- `dist-v82-test/SOURCE-NOTICE.txt`
- `dist-v82-test/build-info.json`
- `dist-v82-test/plugins.json`
- `dist-v82-test/repo.json`
- `docs/v82/SOURCE-LICENSE`
- `docs/v82/SOURCE-NOTICE.txt`
- `scripts/package-v82.py`
- `tests/test_v82_package.py`
- `docs/v82/V82-RESULT.md`

Kaynak ağacındaki değişiklikler: Gradle sürüm 81→82; CleanTestIdentity isim/internalName/izole ayar kimlikleri ve dört kaynaklık migration yapılandırması; üç Kotlin dosyasında yalnızca sunum etiketleri. Testlerde yeni görünen ad ve V81 dört kaynak tercihi aktarımı doğrulanır. Yeni V82 dosya adları eski deneme etiketlerini kullanmaz. Eski arşivler, raporlar ve V81/V66 workflow/dağıtım dosyaları geriye dönük değiştirilmedi.

## Korunan kritik kaynak ve ikili bileşenler

- `DiziBoxAdapter.kt`, `DiziYouAdapter.kt`, `DiziYouEpisodeParser.kt`, `SourceEngine.kt`, `PlaybackLinkBridge.kt`, `PlaybackLinkPreferences.kt`, `Domain.kt`, `PlaybackData.kt`, `PlaybackQuery.kt`, `HlsPlaylistInfo.kt`, `SourceSearchTitles.kt` ve `EASettings.kt` V81 ile byte-for-byte aynı.
- `BronzeApiAdapter.kt` yalnızca NL/LAND display label sabitlerinde değişti; search/load/loadLinks, opaque veri, runtime classloader, callback, URL/loopback, HLS, headers/referer, altyazı ve timeout davranışı aynı.
- `EAProvider.kt` yalnızca iki provider label sabitinde değişti; oynatma callback'i, kalite, header/referer ve subtitle aktarımı aynı.
- Özgün NL/LAND giriş paketlerinin sabit commit'i `7f680cd5ef2ad8be2dc579b1e46b53ba3e28d4cd`, SHA-256'ları ve D8 35.0.0 birleştirme davranışı korundu.
- Özgün derlenmiş `com.keyiflerolsun.HDFilmCehennemi`, `HDFilmcehennemiLand`, `LocalHlsServer`, FastPlay/X-Sp/Rhino ve helper sınıfları yeniden adlandırılmadı.
- `com.eafb.BronzeApiAdapter`, `BronzeNlAdapter`, `BronzeLandAdapter`, eski teknik parser/test betik isimleri ve upstream paket adları çalışma/kanıt zincirinin parçası olarak korundu. Bunlar ürünün görünür kaynak etiketleri değildir. Lisans/provenance metnindeki upstream isimler kasıtlı olarak tutuldu; yeni dağıtımda lisans/notice dosya adları `SOURCE-LICENSE` ve `SOURCE-NOTICE.txt` oldu.
- Tüm EA-FB main kaynakları için yalnızca beklenen isim/kimlik değişikliklerini kabul eden kaynak kapsam kontrolü geçti.
- İki özgün kaynak DEX'inin **250/250 metodunun komut dizisi** tablo indeksleri normalize edilerek aynı bulundu.
- V81/V82 runtime metot kümeleri aynı. **2026 metodun komut dizisi**, izin verilen sunum/izole store string değişiklikleri normalize edilerek aynı bulundu. Sadece `CleanTestIdentity.<clinit>` dört kaynaklık legacy tercih yapılandırması nedeniyle farklıdır; bu dosyanın tam kaynak değişikliği ayrı whitelist kontrolüne tabidir.
- Birleşik DEX'in tamamının hash'i doğal olarak isim/kimlik sabitleri nedeniyle değişti. **V81 ve V82 DEX dosyaları byte-for-byte aynı denmiyor.** Çalışan özgün kaynak algoritmalarının komutları korundu; bütünlüklü V82 paketinin yeni hash'i aşağıdadır.

## Test, derleme ve tek yayın sonucu

- Mevcut V81 test runner'ı: **11 Kotlin suite'i** başarılı (V66'nın 9 suite'i + NL/LAND wrapper suite'i + dört-adapter bridge suite'i); DiziYou parser **10/10**.
- Eski paket regresyonları **7/7**, V82 metadata/dağıtım testleri **4/4** başarılı. Eski testler etkisizleştirilmedi; migration testi yeni V82'nin V81'deki dört tercihi korumasını kontrol eder.
- Yerel Android `:EA-FB:make` başarılı; paket kapsamı ve V81 runtime karşılaştırması geçti. Actions'ta aynı sürüm yeniden doğrulandı; ayrı alternatif sürüm/CS3 varyantı yayımlanmadı.
- [GitHub Actions 37996103933](https://github.com/eaatabay/EA-FB/actions/runs/37996103933), [job 114042481311](https://github.com/eaatabay/EA-FB/actions/runs/37996103933/job/114042481311): testler, Android build, paket/runtime doğrulama, artifact ve yeni dala yayın dahil tüm adımlar **success**.
- Bu işte çalıştırılan testlerde başarısızlık olmadı. Yerel derlemede **203 D8 Kotlin metadata rewrite uyarısı**, Gradle/SDK/deprecation uyarıları vardı; uyarısız derleme iddiası yoktur. Oynatma kodunu düzeltmeyi gerektiren bir engel çıkmadı.
- Yalnızca `test/ea-fb-v82-name-cleanup/dist-v82-test/` yayımlandı. Workflow diğer ref'lerde çalışmayı reddeder, force push kullanmaz. Sonuç belgesi `[skip ci]` ile eklenir; ikinci yayın başlatılmaz.

## Yayımlanan paket doğrulaması

| Alan | Değer |
|---|---|
| CS3 boyutu | 322134 bayt |
| CS3 SHA-256 | `8daf4413a46a44baee0dce89ec7743e8838cef579e993f344fb5d59c721b1d08` |
| DEX SHA-256 | `af41a7e406e8bd0ad63f3eefd2051b1de219b3fe218cabb013c868914cbbc845` |
| DEX class_def sayısı | 371 |
| Manifest | version=82, name=EA-FB V82 TEST, internalName=EA-FB-V82-TEST, pluginClassName=com.eafb.EAPlugin, requiresResources=false |

GitHub raw adresinden indirilen V82 CS3'ün bytes/hash/boyutu güncelleme listesiyle eşleşti; ZIP ve DEX bütünlüğü ile dört adaptörün class_def kayıtları doğrulandı. Yayın sonrası V81→V82 runtime karşılaştırması yeniden geçti.

**Mi Box için yeni test depo adresi:**

https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-v82-name-cleanup/dist-v82-test/repo.json

**Sabit yayın CS3 adresi:**

https://raw.githubusercontent.com/eaatabay/EA-FB/8310fa667147b2fe8627673d1f9b72fc40871de2/dist-v82-test/EA-FB-V82-TEST.cs3

## V81 ve diğer korunan sistemlerin son kontrolü

V81 geliştirme ve arşiv dalları hâlâ `88d7966fedf5150c38e4c898da86c482f1284b70`, aynı `f1c42c56735b2135182170f5db2debe018b93d2b` ağacı üzerinde. V81 gerçek CS3 SHA-256'sı yeniden `e9ebd976fa0f083752689d76735330a4e50892bc8a828479c46b78bb32bd9942`, boyutu 322124 bayt bulundu. Yeni V82 dalı dışında başlangıçta mevcut olan bütün GitHub dal başları değişmedi; Blue V5/main, V49, V66, V81 arşiv/geliştirme, mevcut dağıtımlar ve production Worker/D1/kısa kod/yayın adreslerine yazma yapılmadı.

Kalan kontrol kullanıcı tarafından Mi Box'ta yapılacak: EA-FB V82 TEST provider adı, NL/LAND liste ve üst bilgi isimleri; dört kaynakta video/ses/altyazı ve sarma regresyonu. Bu otomatik doğrulamalar yeni cihaz oynatma testi yerine geçmez.
