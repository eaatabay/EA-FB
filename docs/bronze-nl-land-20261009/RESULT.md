# EA-FB V81 — Bronze NL/LAND entegrasyonu sonuç raporu

**Sonuç:** V66 temelinden oluşturulan ayrı dalda dört kaynaklı V81 test paketi üretildi ve yayımlandı. GitHub Actions'ın bütün adımları başarılı; GitHub'dan indirilen nihai CS3, manifest/güncelleme listesinin hash ve boyutuyla eşleşiyor. Yeni NL/LAND entegrasyonunun Mi Box'ta oynadığı henüz doğrulanmadı.

## Dal ve commit'ler

Dal: [`test/clean-codex-bronze-nl-land-20261009`](https://github.com/eaatabay/EA-FB/tree/test/clean-codex-bronze-nl-land-20261009).

Temel HEAD/paket doğrulandı: [`649a45cdc50c86a86d4134ffb1668838275b19e8`](https://github.com/eaatabay/EA-FB/commit/649a45cdc50c86a86d4134ffb1668838275b19e8), `test/clean-codex-fix-20261009` (V66). Yeni dal bu noktadan oluşturuldu. Mevcut V66 dalı ve dağıtımı değiştirilmedi.

| Ayrı gönderilmiş commit | İçerik |
|---|---|
| [00a771ab72f998a1ed47694e4d410ea0edc073ce](https://github.com/eaatabay/EA-FB/commit/00a771ab72f998a1ed47694e4d410ea0edc073ce) | V81 adı/internalName/store; V66 tercihlerini yalnızca okuyarak tek yönlü aktarım; dört bağımsız kaynak anahtarı |
| [94671e13a3e51960b61089f76bb471e08a80f3c4](https://github.com/eaatabay/EA-FB/commit/94671e13a3e51960b61089f76bb471e08a80f3c4) | Bronze NL/LAND MainAPI delegasyonu; opaque veri; headers/altyazılar; dar LAND loopback HLS policy |
| [fe2d85435054c962875c885caecc973ac7c58e98](https://github.com/eaatabay/EA-FB/commit/fe2d85435054c962875c885caecc973ac7c58e98) | Dört kaynaklı bridge/opt-in ve yeni dağıtım sözleşmesi testleri |
| [b0c333c28876161a63d66753761ded889e84fbdc](https://github.com/eaatabay/EA-FB/commit/b0c333c28876161a63d66753761ded889e84fbdc) | NL/LAND ayrı regresyonları; resolver runtime hatasının izolasyonu |
| [86c4f13dba0bb3da3f9e6ae75ea2e1215f8bf3b7](https://github.com/eaatabay/EA-FB/commit/86c4f13dba0bb3da3f9e6ae75ea2e1215f8bf3b7) | Eski V66 test runner'ının yeni dalda çalışması ve eski paket testlerinin korunması |
| [6d2e6504ebb1c9d4aecbfc71769dcea4a457dd02](https://github.com/eaatabay/EA-FB/commit/6d2e6504ebb1c9d4aecbfc71769dcea4a457dd02) | Sabit Bronze DEX birleştirme, 250 metodun komut koruma kontrolü, ayrı workflow, lisans/provenance ve teknik kayıt |
| [39a6d4c753a6c9ad7705433cb5ad5e90e9ad6307](https://github.com/eaatabay/EA-FB/commit/39a6d4c753a6c9ad7705433cb5ad5e90e9ad6307) | GitHub Actions tarafından yalnızca yeni dalın dağıtımına yapılan yayın |

Bu sonuç belgesi yayın sonrasında ayrı bir belge commit'iyle eklenir; CS3'ün `build-info.json` kaynak commit'i **6d2e6504ebb1c9d4aecbfc71769dcea4a457dd02**, yayın commit'i **39a6d4c753a6c9ad7705433cb5ad5e90e9ad6307** olarak kalır. Dalın güncel HEAD'i paket içindeki kaynak commit'iyle karıştırılmamalıdır.

## Bronze davranışı ve V66'nın korunması

Özgün Bronze Kotlin kaynakları yüklenen master arşivinde bulunmadı. NL **v52** ve LAND **v4**, builds commit'i `7f680cd5ef2ad8be2dc579b1e46b53ba3e28d4cd` üzerindeki SHA-256 doğrulanmış CS3/DEX sınıflarından alınır. Yeni EA-FB Kotlin wrapper'ları bu derlenmiş MainAPI sınıflarının `search`, `load`, `loadLinks` metotlarını çağırır. Özgün Kotlin kaynakları geri kazanılmış gibi gösterilmedi.

NL'de `/search?q=`, film/dizi/bölüm load, `/video/` alternatifleri, JSON/iframe, unpack/Rhino, local source/generic extractor ve altyazı zinciri korunur. LAND'de GET arama, redirect/fallback, episode load, nonce/AJAX get_video_url, SPG/XOR/base64 FastPlay, özgün X-Sp, audio/video/master yeniden yazma ve **LocalHlsServer** korunur. Tüm HTTP URL'leri açılmadı: yalnızca işaretli LAND HLS loopback master'ları ortak filtrede kabul edilir. LocalHlsServer'ın bütün uzak segmentleri proxy'lediği veya her segmentte token yenilediği iddia edilmez.

Ana/orijinal başlıkla arama; doğru tür/bilinen yıl/sezon/bölüm; opaque payload; extractor type/headers/referer; subtitle headers; kısmi timeout ve kaynak hatasının diğer kaynaklardan izolasyonu wrapper/motor düzeyinde uygulanır.

**DiziBoxAdapter.kt ve DiziYouAdapter.kt, V66 temel commit'iyle byte-for-byte aynı kaldı.** AES King/Moly/Haydi, DiziYou GET/AJAX fallback, altyazılar, orijinal başlık, HLS türü/headers, adaptive master filtresi ve timeout öncesi bağlantılar korunur. Yeni store eski V66 store'a yazmaz; NL/LAND varsayılan kapalıdır.

Özgün DEX metot konumları ve yeni kodun dal/commit/dosya/satır referansları [teknik kayıt](INTEGRATION.md) içindedir. Eski V65/V79/V80 HDF/FastPlay portları çalışma başarısı kanıtı veya entegrasyon kaynağı olarak kopyalanmadı.

## Nihai paket ve DEX kanıtı

| Alan | Doğrulanmış değer |
|---|---|
| Sürüm | **81** — incelenen V65–V80 sürümleriyle çakışmıyor |
| Provider/name | `EA-FB CODEX BRONZE NL LAND TEST` |
| internalName | `EA-FB-CODEX-BRONZE-NL-LAND-20261009` |
| pluginClassName | `com.eafb.EAPlugin` |
| requiresResources | `false` |
| CS3 boyutu | **322124 bayt** |
| CS3 SHA-256 | `e9ebd976fa0f083752689d76735330a4e50892bc8a828479c46b78bb32bd9942` |
| DEX SHA-256 | `49a00b1ef00d2145b48c00de2727510a44219f2cdd92678ad0379c570c835e5b` |
| DEX tanımlı sınıf sayısı | **371** |
| Bronze komut koruma kontrolü | **250/250 metodun komut dizisi korundu**, tablo indeksleri normalize edilerek |

`class_def` tablosunda `com.eafb.DiziBoxAdapter`, `DiziYouAdapter`, `BronzeNlAdapter`, `BronzeLandAdapter`, `BronzeApiAdapter`, `PlaybackLinkBridge`, `EAProvider`, `EAPlugin`; özgün `com.keyiflerolsun.HDFilmCehennemi`, `HDFilmcehennemiLand`, `LocalHlsServer` tanımları doğrulandı. Salt class adı string araması yapılmadı.

Yayımlanmış DEX'te `PlaybackLinkBridge.<clinit>`, `code_offset=0x3c9dc`: DiziYou constructor +0xe, DiziBox +0x16, BronzeNl +0x1d ve BronzeLand +0x25. Böylece dört sınıf yalnızca paket içinde bulunmuyor; gerçek bridge listesinde oluşturuluyor. Bronze plugin entrypoint'leri ayrıca çalıştırılmıyor.

## Testler, Actions ve açık sınırlar

- V66'nın **9 Kotlin suite'i** başarılı; DiziYou parser **10/10**. Gerçek DiziBox/DiziYou adaptörleri çevrimdışı HTML/extractor fixture'larıyla test edildi.
- NL ve LAND'in ayrı wrapper regresyonları başarılı: arama/orijinal ad, film ve doğru bölüm, yanlış ad/domain/yıl/sezon/bölüm reddi, opaque veri, headers/referer/Origin/UA/HLS, geç subtitle headers, kısmi timeout, search/resolve runtime hataları.
- Dört-adaptörlü gerçek bridge'in tek tek opt-in ve birleşik listesi başarılı. **Bronze API sınıfları burada test-only fixture'lardır**; özgün Android DEX algoritmalarının JVM'de canlı çalıştığı iddia edilmez.
- Yeni dağıtım testleri **4/4**, korunan V66 paket testleri **3/3** başarılı.
- Gerçek Android derlemesi, tek DEX birleştirme, namespace/çakışma/sınıf birleşimi ve 250 metot komut kontrolü başarılı.
- GitHub'dan yayımlanan CS3 tekrar indirildi; yerel yayın dosyasıyla byte-for-byte aynı, feed hash/boyut ve manifest ile uyumlu. Özgün yüklenen Bronze DEX'lerine karşı 250 metot kontrolü yeniden geçti.
- Seçilen yeni testlerde başarısızlık yok. Yerel derlemede **203 D8 Kotlin metadata rewrite uyarısı**, Gradle/SDK/deprecation uyarıları vardı. Sıfır uyarı iddia edilmiyor. `git diff --check` ilk paketleme commit'inde bir EOF boş satırını bildirdi; sonuç commit'inde düzeltildi ve kontrol geçti.
- Önceki V66 çalışmasındaki legacy `test-playback-v6.sh` Kotlin bölümü Android Log stub eksikliğiyle başarısız olmuştu; eski başarısızlık yeni runner'ın yeşil sonucuyla gizlenmedi.
- Kontrollü canlı NL/LAND video/segment testi ve yeni entegrasyonun Mi Box testi yapılmadı; izinli canlı test fixture'ı ve cihaz erişimi yok. Host Rhino/CloudStream/NiceHttp/OkHttp uyumluluğu, loopback kabulü, segment/key erişimi, token ömrü, uzun süreli oynatma cihazda doğrulanmalıdır.
- Yaklaşık 20 MB Claude paketi ve önceki cihaz logları erişilemez; Bronze builds ZIP bunlar yerine gösterilmedi.

[GitHub Actions 37966426174 — başarılı](https://github.com/eaatabay/EA-FB/actions/runs/37966426174), [job 113941807006](https://github.com/eaatabay/EA-FB/actions/runs/37966426174/job/113941807006): test, SDK/D8 kurulumu, Android build, paket/doğrulama, artifact ve yeni dala yayın dahil bütün adımlar **success**.

## Kurulum bağlantıları

- **CloudStream yeni test depo adresi:** https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-bronze-nl-land-20261009/dist-clean-codex-bronze-nl-land-20261009/repo.json
- **Sabit yayın CS3:** https://raw.githubusercontent.com/eaatabay/EA-FB/39a6d4c753a6c9ad7705433cb5ad5e90e9ad6307/dist-clean-codex-bronze-nl-land-20261009/EA-FB-CODEX-BRONZE-NL-LAND-20261009.cs3
- [Güncelleme listesi](https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-bronze-nl-land-20261009/dist-clean-codex-bronze-nl-land-20261009/plugins.json)
- [Sabit build-info](https://raw.githubusercontent.com/eaatabay/EA-FB/39a6d4c753a6c9ad7705433cb5ad5e90e9ad6307/dist-clean-codex-bronze-nl-land-20261009/build-info.json)

Mi Box'ta yeni provider'ı seçerek NL ve LAND'i ayrı ayrı açın; film/doğru sezon-bölüm ve bulunan kaynakları kontrol edin. Video/ses, dublaj/orijinal ses, TR/EN altyazı, kalite, ileri-geri sarma, uzun oynatma/token ömrü ve timeout sırasında diğer kaynakların korunması test edilmeli. Sonra dört kaynak birlikte ve DiziBox/DiziYou regresyonu kaydedilmeli. Bu cihaz sonucu gelene kadar NL/LAND oynatma başarısı söylenemez.

## Korunan dalların son kontrolü

GitHub `ls-remote` son kontrolünde aşağıdaki başlar başlangıçla aynı kaldı:

| Dal | HEAD |
|---|---|
| main / Blue V5 | `d7de664092a9236da477783f4cc35b5f7bf895d3` |
| feature/detail-dual-ratings-v6 | `e5f0501b75e9f0957a6e2d63911f460204e73c35` |
| test/bronze-direct-integration-v79 | `4658aaaa12e54ef5b02e3e91f5d36c5b3f41e514` |
| test/v63-bronze-cleanroom-20261008 | `c53ee48c7bbca37aa1bec85ca47668c13bcdd899` |
| test/clean-codex-fix-20261009 | `649a45cdc50c86a86d4134ffb1668838275b19e8` |
| test/nl-land-v75-isolated | `2653bfdfc6d511c71561bde60af304cebecbcc61` |
| test/land-fastplay-isolated-20261009 | `2eaf679c7bcc1dbefcb9230d7f4f127311cb435f` |

Diğer dal/ref'lere push, commit/PR, production Worker/D1 işlemi veya mevcut workflow değişikliği yapılmadı. Tamamlanan bütün gruplar yeni dala gönderildi. Kalan iş **Mi Box cihaz doğrulamasıdır**; otomatik cihaz testi/devam sözü verilmez.
