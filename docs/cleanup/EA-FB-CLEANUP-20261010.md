# EA-FB GitHub kontrollü temizlik raporu — 10 Ekim 2026

Tarih bağlamı: Europe/Istanbul. Bu belge tamamlanmış temizlik işleminin kalıcı kaydıdır; yeni bir temizlik veya oynatma testi raporu değildir.

Başlangıç 21 dal; sonuç 20 dal. Silinen tek ref: test/nl-land-v75-isolated. Kod veya dağıtım dosyası için ayrı silme commit’i yapılmadı; tüm 245 dosya ve tam geçmiş yedek etiketinde korundu.

Yedek: https://github.com/eaatabay/EA-FB/tree/backup/cleanup-20261010-nl-land-v75-isolated
V81 koruma etiketi: https://github.com/eaatabay/EA-FB/tree/protect/v81-mibox-working-20261009

V81 arşiv/develop commit: 88d7966fedf5150c38e4c898da86c482f1284b70; tree: f1c42c56735b2135182170f5db2debe018b93d2b.
V81 CS3: 322124 bayt; SHA256 e9ebd976fa0f083752689d76735330a4e50892bc8a828479c46b78bb32bd9942.
DEX: 741848 bayt; SHA256 49a00b1ef00d2145b48c00de2727510a44219f2cdd92678ad0379c570c835e5b.
V81 arşiv ve develop tam ağaç eşitliği; Kotlin/Bronze, testler, scripts, workflow, manifest ve dist korunması doğrulandı. V66 gerçek CS3 hash/version yeniden doğrulandı; V66/V81 repo.json/plugins.json HTTP 200.
GitHub üzerinden yedek etiket ZIP’i indirildi; 245 dosyanın tamamı orijinal Git blob kimliklerine karşı doğrulandı.

## Silinen dal ve geri dönüş

| Silinen dal | Silme öncesi son commit SHA | GitHub doğrulaması |
|---|---|---|
| `test/nl-land-v75-isolated` | `2653bfdfc6d511c71561bde60af304cebecbcc61` | Silme push'u başarılı; `git ls-remote --heads` çıktısında ref artık yok |

Silme yalnızca bu branch ref'ine uygulandı. `--force-with-lease=refs/heads/test/nl-land-v75-isolated:2653bfdfc6d511c71561bde60af304cebecbcc61` koşulu, başın eşzamanlı değişmesi halinde işlemi reddedecek şekilde kullanıldı. Başka dal güncellenmedi.

Yedek etiketinin GitHub'daki peeled commit SHA'sı: `2653bfdfc6d511c71561bde60af304cebecbcc61`. GitHub codeload ZIP'i indirildi; dosya kümesi ve 245 dosyanın her birinin Git blob SHA'sı özgün ağaçla eşleşti. Yedek ZIP SHA-256: `25957de692f370088a15925931fc3d0be9366907ab29c5536e10f35e05959051`.

## Silinen dosyalar ve dağıtım paketleri

**Yok.** Kaynak veya dağıtım dosyası silen bir commit oluşturulmadı; hiçbir CS3, JSON, manifest, log veya Actions artifact'i ayrıca silinmedi. Silinen dalın eski ada bağlı raw URL'leri artık branch üzerinden çözülmez; bütün dosyalar ve geçmiş doğrulanmış yedek etiketinde korunur. Bir branch ref'inin kaldırılması, paketin yeni bir sürümünü yayımlamak değildir.

## Silme gerekçesi
N79 dalı: açık PR yok (tek açık draft PR #1 fix/season-title-refresh-v48 -> feature/detail-dual-ratings-v6); çalışan/bekleyen Actions yok; tüm commit geçmişi V80 dalında mevcut; hiçbir envanter repo/feed URL’si N79 dalını hedeflemiyor. Ayrı doğrulanmış yedek, SHA eşleşmesini zorunlu kılan push lease ve silme sonrası refs/heads yokluğu kontrol edildi.

## Korunan adaylar
- test/bronze-direct-integration-v79: 8 benzersiz commit; dist-v80-test yayını HTTP 200; dış kullanım bilinmiyor.
- test/land-fastplay-isolated-20261009: 19 benzersiz commit; V66/V81 içinde dist-v63-clean/plugins.json:20 bu dalın CS3’üne yöneliyor.
- test/v63-bronze-cleanroom-20261008: V66/V81 dist-v63-clean/repo.json:6 bu dalın plugins.json adresine yöneliyor.
- test/dizibox-king-mibox: V66/V81 .github/workflows/v6-staging-build.yml:25 bu dalı checkout ediyor.
- Sekiz eski arşiv tarihsel paket/kaynak snapshot veya recovery bilgisi nedeniyle saklandı. develop/post-v49 belirsiz ortak geliştirme; feature/detail-dual-ratings-v6 ve fix/season-title-refresh-v48 açık PR ve RED dağıtımı nedeniyle otomatik silinmedi. Bunlar ayrı onay listesinde.

## Erişim / işlem sınırları
GitHub API pulls/rulesets Forbidden. Açık PR bilgisi public GitHub dashboard JSON’undan ve PR #1 sayfasından doğrulandı. GitHub settings/rules 404; yetkili koruma politikası yönetimi doğrulanamadı. Etiket oluşturuldu ama branch silme/force push engelleyen kural uygulanamadı; mevcut kurallar gevşetilmedi. Etiket tek başına server-side silme/yazma koruması değildir.
186 dağıtım kaydı (65 CS3 girdisi, 18 farklı CS3 blob’u), 51 benzersiz dağıtım blob’u envantere alındı. Manifestlerdeki 24 farklı GitHub raw yayın adresi HTTP 200. Bu, gerçek istemci kullanımının sıfır olduğu anlamına gelmez; belirsiz yayın dosyaları korundu.
Production Worker/D1/kısa kodlar değiştirilmedi veya çağrılmadı. Actions/log/artefact silinmedi. Derleme, yeni release veya sürüm yayını yapılmadı. Mi Box cihaz testi yapılmadı.

## Blue V5, V49, V66 ve V81 koruma sonucu

| Sürüm / rol | Dal | Doğrulanmış ve değişmeyen commit SHA |
|---|---|---|
| Blue V5 / production | `main` | `d7de664092a9236da477783f4cc35b5f7bf895d3` |
| V49 tasarım referansı | `archive/frozen-v49-20261002` | `c3544d1d9dd346ac8737d0fcf21c67e3cd1da13a` |
| V66 çalışan temiz temel | `test/clean-codex-fix-20261009` | `649a45cdc50c86a86d4134ffb1668838275b19e8` |
| V81 dondurulmuş arşiv | `archive/frozen-v81-mibox-working-20261009` | `88d7966fedf5150c38e4c898da86c482f1284b70` |
| V81 geliştirme / dağıtım | `test/clean-codex-bronze-nl-land-20261009` | `88d7966fedf5150c38e4c898da86c482f1284b70` |

Temizlik sonrasında kalan **20 dalın tamamının HEAD SHA'sı başlangıçla aynı** bulundu. Korunan dalların dosyaları, mevcut arşivler ve yayın paketleri değiştirilmedi.

## V81 CS3 / DEX bütünlüğü ve GitHub'da doğrulanan sonuçlar

- Arşiv ve çalışan V81 dalının commit'i: `88d7966fedf5150c38e4c898da86c482f1284b70`.
- İkisinin tam Git ağacı: `f1c42c56735b2135182170f5db2debe018b93d2b`; kaynak/paket karşılaştırması tam ağaç eşitliğiyle doğrulandı.
- CS3 yolu: `dist-clean-codex-bronze-nl-land-20261009/EA-FB-CODEX-BRONZE-NL-LAND-20261009.cs3`.
- CS3: **322124 bayt**, SHA-256 `e9ebd976fa0f083752689d76735330a4e50892bc8a828479c46b78bb32bd9942`.
- CS3 ZIP bütünlük kontrolü geçti; içindeki dosyalar: `classes.dex`, `manifest.json`, `BRONZE-LICENSE`, `BRONZE-NOTICE.txt`.
- DEX yolu: yukarıdaki CS3 içindeki `classes.dex`; **741848 bayt**, SHA-256 `49a00b1ef00d2145b48c00de2727510a44219f2cdd92678ad0379c570c835e5b`.
- Manifest: sürüm `81`, pluginClassName `com.eafb.EAPlugin`, name `EA-FB CODEX BRONZE NL LAND TEST`, internalName `EA-FB-CODEX-BRONZE-NL-LAND-20261009`, requiresResources `false`.
- Tam ağaçta 49 Kotlin kaynak dosyası, 45 core-tests dosyası, 23 script, 8 workflow; Bronze entegrasyonu, testler, betikler, manifest ve dağıtım dosyaları korunmuş durumda.
- V81 CS3 hem geliştirme hem arşiv dalının gerçek GitHub raw adresinden indirildi; temizlik sonrası aynı hash/boyut yeniden doğrulandı.
- V66 CS3 yolu: `dist-clean-codex-fix-20261009/EA-FB-CODEX-CLEAN-20261009.cs3`; **244782 bayt**, SHA-256 `89dfc4ac1d5f737364e1cbc21e62a2846e8cf6b5cc563df89c9e54c166be9480`; gerçek GitHub dosyası ve manifest sürümü `66` yeniden doğrulandı.
- V81 ve V66 `repo.json` ile `plugins.json` adresleri temizlik sonrasında **HTTP 200** döndü.
- `protect/v81-mibox-working-20261009` etiketi GitHub'a gönderildi ve peeled commit'i V81 SHA'sıyla eşleşti. Bu, doğrulanmış bir geri dönüş referansıdır; branch silme/yazma yasağı uygulandığı anlamına gelmez.

Doğrudan doğrulanan yayın adresleri:

- [V81 arşiv CS3](https://raw.githubusercontent.com/eaatabay/EA-FB/archive/frozen-v81-mibox-working-20261009/dist-clean-codex-bronze-nl-land-20261009/EA-FB-CODEX-BRONZE-NL-LAND-20261009.cs3)
- [V81 geliştirme CS3](https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-bronze-nl-land-20261009/dist-clean-codex-bronze-nl-land-20261009/EA-FB-CODEX-BRONZE-NL-LAND-20261009.cs3)
- [V81 repo.json](https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-bronze-nl-land-20261009/dist-clean-codex-bronze-nl-land-20261009/repo.json)
- [V81 plugins.json](https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-bronze-nl-land-20261009/dist-clean-codex-bronze-nl-land-20261009/plugins.json)
- [V66 CS3](https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-fix-20261009/dist-clean-codex-fix-20261009/EA-FB-CODEX-CLEAN-20261009.cs3)
- [V66 repo.json](https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-fix-20261009/dist-clean-codex-fix-20261009/repo.json)
- [V66 plugins.json](https://raw.githubusercontent.com/eaatabay/EA-FB/test/clean-codex-fix-20261009/dist-clean-codex-fix-20261009/plugins.json)

Mi Box'ta çalıştığına ilişkin V81 tanımı kullanıcı bildirimidir; bu temizlik görevinde yeni cihaz testi yapılmadı. Hash/ZIP/ağaç doğrulamaları yeni oynatma başarısı iddiası değildir.

## Raporun yayımlanma kapsamı

Bu dosya yalnızca yeni `docs/cleanup-report-20261010` dalında oluşturulur. Dalın temeli, değiştirilmeyen `main` commit'i `d7de664092a9236da477783f4cc35b5f7bf895d3` olur. Rapor commit'inin tek dosya değişikliği `docs/cleanup/EA-FB-CLEANUP-20261010.md` dosyasıdır; yeni temizlik, silme, paket değişikliği, build/release veya production işlemi içermez.

**21 → 20 sayımı tamamlanan temizlik işlemini anlatır.** Bu ayrı rapor dalının eklenmesiyle GitHub dal sayısı ayrıca **21** olur; bu artış eski bir dalın geri getirilmesi veya yeni temizlik değildir.

## Başlangıç ve sonuç HEAD’leri

| Dal | Başlangıç SHA | Sonuç |
|---|---|---|
| `archive/frozen-v44-20261002` | `56afc2a7972d9fb2a95370c857e0f47e38e27c97` | Saklandı; SHA aynı |
| `archive/frozen-v45-20261002` | `7c39bc3f590c69f7a20e042ec48f6f0e933597d2` | Saklandı; SHA aynı |
| `archive/frozen-v46-20261001` | `252e8126bce68829e5cac7e8fadca2a27e4d3a03` | Saklandı; SHA aynı |
| `archive/frozen-v47-20261002` | `9eedba60cdfbf82d2fd3aadb894fc9193c7bbe3b` | Saklandı; SHA aynı |
| `archive/frozen-v49-20261002` | `c3544d1d9dd346ac8737d0fcf21c67e3cd1da13a` | Kesin korunan; SHA aynı |
| `archive/frozen-v62-mibox-20261005` | `6e000453be03d1e5bed79acd5cd17a8d5e3e85d2` | Saklandı; SHA aynı |
| `archive/frozen-v63-mibox-20261006` | `006d4782a864224f25c8bdc69b312994841c00f5` | Saklandı; SHA aynı |
| `archive/frozen-v64-complete-20261009` | `4730b9717a76c5810607e59b28600e274fb7b875` | Saklandı; SHA aynı |
| `archive/frozen-v81-mibox-working-20261009` | `88d7966fedf5150c38e4c898da86c482f1284b70` | Kesin korunan; SHA aynı |
| `archive/recovery-v63-complete-20261008` | `ff7615d17fad2a818f569e5e46d8efdf1011ed5e` | Saklandı; SHA aynı |
| `develop/post-v49` | `7cc8658f3c592722362ed6ab6ffaf65fca8763e7` | Saklandı; SHA aynı |
| `feature/detail-dual-ratings-v6` | `e5f0501b75e9f0957a6e2d63911f460204e73c35` | Saklandı; SHA aynı |
| `fix/season-title-refresh-v48` | `c3544d1d9dd346ac8737d0fcf21c67e3cd1da13a` | Saklandı; SHA aynı |
| `main` | `d7de664092a9236da477783f4cc35b5f7bf895d3` | Kesin korunan; SHA aynı |
| `test/bronze-direct-integration-v79` | `4658aaaa12e54ef5b02e3e91f5d36c5b3f41e514` | Saklandı; SHA aynı |
| `test/clean-codex-bronze-nl-land-20261009` | `88d7966fedf5150c38e4c898da86c482f1284b70` | Kesin korunan; SHA aynı |
| `test/clean-codex-fix-20261009` | `649a45cdc50c86a86d4134ffb1668838275b19e8` | Kesin korunan; SHA aynı |
| `test/dizibox-king-mibox` | `3b10881e293391e06b9bf33e08e84549af5e4090` | Saklandı; SHA aynı |
| `test/land-fastplay-isolated-20261009` | `2eaf679c7bcc1dbefcb9230d7f4f127311cb435f` | Saklandı; SHA aynı |
| `test/nl-land-v75-isolated` | `2653bfdfc6d511c71561bde60af304cebecbcc61` | Silindi; yedek etikette korundu |
| `test/v63-bronze-cleanroom-20261008` | `c53ee48c7bbca37aa1bec85ca47668c13bcdd899` | Saklandı; SHA aynı |
