# Devam notları — tamamlanan 8501 test sürümü

**Kod düzeltmesi, bulut testleri, paketleme ve yalnız yeni dala ait staging yayını tamamlandı.**

- Dal: `test/ea-fb-series-name-codex-20261019`. V83 temel/ilk HEAD: `16396702470eb1f81cc6dac4360e6b0ff84927d1`.
- Son kod/paketleme kaynak commit'i: `9aa829f1b494498762e02c16e5e6ad0f6c7de297`.
- Dağıtım commit'i: `eba59a87802695772750582c505b5fe0abf6d0c6`.
- CI **39316844187 success**: https://github.com/eaatabay/EA-FB/actions/runs/39316844187 . Tam log arşivi incelendi.
- Paket: `dist-series-codex-8501-test/EA-FB-SERIES-CODEX-8501-TEST.cs3`, 332876 byte.
- SHA-256: **f2b2d28a8a6adf5fbdfe8eae91fb70f36c346f658c3dbf7bcc91aae55fe12a3b**. GitHub raw indirimi ve manifest/DEX doğrulaması PASS.
- Yeni test depo URL'si: https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-series-name-codex-20261019/dist-series-codex-8501-test/repo.json

## Düzeltme
Doğrulanmış TV 320754 / IMDb tt38732131 / 2026 / S1 E1..8 için Organize İşler: Karun Hazinesi tam alternatif adayı eklendi. Film 30634/577990 ve başka diziler kapsam dışı; genel contains/substring veya kelime sırası kuralı yok. Yeni kaynak kabullerinde tür/yıl/başlık/tam bölüm ve belirsizlik denetleniyor. Film araması, UI kaynakları, EAProvider/EASettings/SourceEngine/PlaybackLinkBridge ve resolver zinciri korunuyor.

## Kanıt
- 59 replay +36 yeni kaynak güvenliği +37 profil/kurulum assertion PASS; dar alias sözleşmesi PASS.
- Mevcut 12 Kotlin programı ve 14 baseline Python testi PASS (eski V83 scope testleri değişmeden V83 arşivinde). Yeni üç paket testi PASS; gerçek UI DEX mutasyonu reddedildi.
- 2028 korunan V83 runtime metodu ve 250 özgün Bronze metodu korundu.
- Gerçek DiziYou lookup, dört HTTP isteği ile doğru tek S1E8 teklifi döndürdü. Extractor/stream isteği yapılmadı. Diğer kaynaklar fixture ile doğrulandı; canlı oynatma iddiası yok.
- Yeni D8 metadata warning sayısı 209; nonfatal, gizlenmedi. CI-8501-SONUC.json ayrıntılı kanıtı içerir.
- İlk ara CI 39314642510 dependency download HTTP404 nedeniyle failed; sonraki kaynak ve son CI'ler success. Artifact ikili indirmesinin blob hedefi ağda engellendi; CI logundaki SHA-256 ile yerel paket eşleşti ve yayımlanan raw dosya ayrıca indirildi/doğrulandı. Bulut tarafında açık engel kalmadı.

## Zorunlu test kurulum sınırı
**Ana Mi Box/Android profiline yüklemeyin; paket orada çalışmayı reddeder.** Yalnız ayrı Android kullanıcı profilindeki ayrı uygulama süreci/veri alanı ve başka EA-FB bulunmayan test kurulumu desteklenir. UID ve CS3 manifest envanter kapısı settings/UI/provider öncesinde çalışır; yeniden adlandırılmış eklenti de yakalanır. Farklı paket adı tek izolasyon kanıtı sayılmadı.
Aynı profilde iki EA-FB'nin birlikte güvenli çalıştığı iddia edilmez; test profilinde sonradan ikinci EA-FB kurmak desteklenmez. Mi Box'ta ayrı profil desteği, menü görünümü ve gerçek görüntü/ses ayrı ayrı **henüz doğrulanmadı**. V83'ü kaldırmak/veri veya cache temizlemek gerekmiyor ve önerilmiyor. TEST-KURULUMU.md / GERI-ALMA-PLANI.md yönergeleri geçerli.

## Koruma ve geri dönüş
V83 HEAD `16396702470eb1f81cc6dac4360e6b0ff84927d1`; V83 CS3 SHA 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66. Diğer 23 mevcut dalın HEAD'i değişmedi. Eski başarısız dal kullanıcı talebiyle temizlendi. main/V81/arşiv/production Worker/D1/manifest/kısa kodlara yazılmadı.
Sonraki çalışma ancak cihazdaki test sonucu veya yeni kullanıcı talimatına göre yürütülür; bulut yazılım aşaması bitmiştir.

## Tam commit envanteri
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

Bu nihai kayıt commit'i kendi SHA'sını içeremez; `git log -1 --format=%H` kesin son SHA'yı verir, sonuç mesajında da paylaşılır.
