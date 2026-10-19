# Geri alma planı — güncel test/teşhis aşaması

- Yeni dal: test/ea-fb-series-name-codex-20261019.
- Temel V83: 16396702470eb1f81cc6dac4360e6b0ff84927d1.
- Yeni workflow: .github/workflows/series-name-diagnostics.yml; yalnızca bu dal; contents:read; yayın/paketleme/push adımı yok.
- CI: 39309504108 success (fb16e9ced070f1fe0c6d6cf93aaa863f79745099); https://github.com/eaatabay/EA-FB/actions/runs/39309504108
- Yeni paket kimliği/sürüm, ayrı tercih deposu, staging manifesti ve CS3: **henüz oluşturulmadı**. Yerel EA-FB/build/EA-FB.cs3 değişmemiş V83 kimlikli derlemedir, kurulum/yayın için verilmedi.
- Mi Box'a hiçbir test kurulmadı; kullanıcı ayarlarına dokunulmadı.

## Yalnızca yeni çalışmayı kaldırma
1. Kurulu yeni test olmadığından V83'ü kaldırma veya uygulama verisini temizleme. İleride paket oluşturulursa kaldırma yönergesi ancak ayrı kimlik/tercih alanı ve UI izolasyonu doğrulandıktan sonra eklenir.
2. Yeni staging adresi yok; V83 depo adresini olduğu gibi bırak.
3. Gerekirse yeni dalı arşivle; sadece `git push origin --delete test/ea-fb-series-name-codex-20261019` çalıştır. V83'e reset/force-push/cherry-pick/revert gerekmez.
4. Yerelde yalnızca yeni eklenen .github/workflows/series-name-diagnostics.yml, core-tests/clean/SeriesNameDiagnosisTest.kt, scripts/test-series-name-codex.sh, scripts/verify-series-v83-baseline.py, tests/test_series_v83_baseline.py ve docs/series-name-codex/ dosyaları bu çalışmaya aittir. Var olan dosyaları, vendor/caches veya V83 dağıtımını gelişigüzel silme.
5. git ls-remote ile başlangıç envanterini karşılaştır: yeni dal ve kullanıcı tarafından silinmesi istenen eski başarısız dal dışında uzak ref değişmemeli. V83 HEAD 16396702470eb1f81cc6dac4360e6b0ff84927d1; CS3 SHA-256 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66.
6. main/Blue V5, V81/arşivler, dist-v83-test/, production Worker/D1, mevcut manifestler/kısa kodlar/workflow'lar ve kullanıcı tercihleri korunur.

Eski başarısız dal test/organize-isler-season-title-20261019, aynı V83 HEAD'i ve açık PR bağımlılığı olmadığı doğrulanarak silindi. Bu temizliği geri almak gerekirse yalnızca eski adı 16396702470eb1f81cc6dac4360e6b0ff84927d1 üzerine yeniden oluştur; V83'ü değiştirme.

## Tüm yeni commitler (bu kayıt commit'inden önce)
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab

Bu kayıt commit'inin kendi SHA'sı git log -1 -- docs/series-name-codex ile bulunur; sonraki aşama tam SHA'yı ekler.

Güncel yerel kimlik tanısı: **53/53 PASS**, dört adaptör; 312 korunan V83 dosyası byte-identical. UI/iki eklenti izolasyonu henüz kanıtlanmadı; yayın yok.

## Güncel aşama 7
UI-HOOK-RISKI.md ve DEX kayıtları eklendi; UI/runtimeda değişiklik yok. Risk çözülmüş sayılmıyor, yeni paket ve staging yayını yok.

Son mantıksal test commit'i: efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4. Kimlik tanısı 53/53 yerel PASS. Yeni CI run **39310538425**: https://github.com/eaatabay/EA-FB/actions/runs/39310538425 — son sorguda queued, başarı iddiası yok. Önceki 39309504108 success; ayrıntılı log erişimi hâlâ proxy 403. EA-FB katalog doğrulaması aynı ağ engeli nedeniyle bekliyor.

### Güncel tam SHA envanteri
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab
- `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` — test(series): track researched TV identity separately from films — https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4

Bu doküman kayıt commit'inin SHA'sı git log -1 -- docs/series-name-codex ile bulunur; sonraki aşamada envantere eklenir.

## Aşama 8 güncellemesi
yerel relay CONNECT 403 sorununu GitHub runner üzerinden ayırmak; EA-FB'nin gerçekten kullandığı config/JSON'u ölçmek.
- Kapsam: yalnızca GET; GitHub contents:read token'ı sadece sabit GitHub config API adresine gider. Relay yalnızca sabit onaylı staging origin. Yönlendirme izlenmez. TV ayrıntısı, external_ids, sezon 1 ve ad araması; video/oynatma isteği yok. Başka Worker/D1/production host veya yazma/deploy işlemi yok.
Yeni workflow: .github/workflows/series-name-catalog-diagnosis.yml, salt okunur staging tanısı; scripts/diagnose-series-catalog.py ve tests/test_series_catalog_diagnosis.py yalnız yeni çalışmaya aittir. UI/adapter/runtime değişmedi.
CI 39309504108 tam log arşivi indirildi ve incelendi; eski log erişim engeli çözüldü. D8 uyarıları CI-39309504108-INCELEME.json içinde; yeni cihaz veya yayın başarısı iddiası yok.

### Güncel bütün SHA kayıtları
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab
- `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` — test(series): track researched TV identity separately from films — https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4
- `63782dbd89fc52d4382a973c82fac67ca442ad75` — docs(series): audit V83 UI hooks and preserve diagnostic handoff — https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75

Kayıt commit'i kendi SHA'sını içeremez; güncel SHA için git log -1 -- docs/series-name-codex. Sonraki kayıt bu SHA'yı ekler.

## Aşama 9 — güncel ekler
EA-FB katalog doğrulaması 39311300009 ile success; 39310538425 de success ve tam logları okundu. Güncel tanı 59/59 yerel PASS, yalnızca test/doküman değişikliği.
Yeni güvenli kanıt dosyaları EA-FB-KATALOG-GOZLEM.json, DIZIYOU-GOZLEM.json, CI-39309504108-INCELEME.json, SONUC-TESHIS.md bu test klasörüne aittir. Yeni script/test ve her iki workflow da yalnızca bu yeni çalışma kapsamında kaldırılabilir.
Paket/tercih alanı/staging manifesti hâlâ yok; Mi Box'a hiçbir test yüklenmedi. UI çakışması çözülmeden yayın yapılmayacak.
### Güncel bütün commit SHA'ları
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab
- `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` — test(series): track researched TV identity separately from films — https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4
- `63782dbd89fc52d4382a973c82fac67ca442ad75` — docs(series): audit V83 UI hooks and preserve diagnostic handoff — https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75
- `22922df2f887c6c14cf687bb63bd13118476bb22` — test(series): probe approved catalogue identity from read-only CI — https://github.com/eaatabay/EA-FB/commit/22922df2f887c6c14cf687bb63bd13118476bb22

Bu kayıt commit'i için git log -1 -- docs/series-name-codex; sonraki aşama tam SHA'yı ekler.

## Son durum — 59 assertion / nihai kayıt
Son başarılı uzak test commit'i: **6433b1fcf5a88de40412bbe852112eb15bd90950**. GitHub kimlik doğrulaması bundan hemen sonra 401 verdiği için nihai kayıt commit'i yerelde kalabilir; mevcut SHA `git log -1` ile okunur. Bağlantı yenilenmeden push/son CI sonucu doğrulanmış sayılmaz.
Katalog doğrulaması 39311300009 success; ilk iki test/derleme CI'si 39309504108 ve 39310538425 success, loglar incelendi. 39311299938 ve son test commit'inin CI sonucu henüz kesinleştirilmedi. Yerel son tanı 59/59 ve Python 5/5 PASS.
Yeni çalışma dosyalarının tam listesi `git diff --name-only 16396702470eb1f81cc6dac4360e6b0ff84927d1..HEAD` ile alınır; 312 V83 dosyasının hiçbirinde fark olmamalı. SON-DURUM.json bu son durumu içerir.
Geri alma hâlâ yalnızca yeni dal ve yeni tanı dosyalarıyla sınırlı; Mi Box'ta yeni paket kurulu değil. Paket, yeni tercih alanı veya staging manifesti yok. V83'e reset/force-push/cherry-pick/düzeltme commit'i gerekmez.

### Bütün başarılı uzak commit SHA'ları
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

Bu son yerel kayıt commit'ini kendi SHA'sıyla geri almak gerekirse `git log -1 --format=%H` kullan. Sonraki aşamada bu SHA da kalıcı envantere eklenir.

## Bağlantı yenilendikten sonraki kesin durum

GitHub API ve HTTPS Git erişimi yeniden çalışıyor. Yerelde kalan **1aa79597420845beb18afa58849c917a9499beab** yalnızca yeni test dalına normal push ile gönderildi. Önceki 401 engeli giderildi.

- **39311299938: success**, tam log arşivi incelendi; 53 assertion, Android BUILD SUCCESSFUL ve 312 dosya koruması PASS.
- **39311839830: success**, tam log arşivi incelendi; son **59 assertion**, Android BUILD SUCCESSFUL ve 312 dosya koruması PASS. Kaynak commit: **6433b1fcf5a88de40412bbe852112eb15bd90950**.
- Her iki derlemede 206 D8 Kotlin metadata rewrite uyarısı var; bunlar gizlenmedi, cihaz başarısı sayılmadı. Ayrıntılar SON-CI-INCELEMESI.json içinde.
- Güncel uzak dal kontrolünde V83/main/V81 ve diğer korunan HEAD'ler başlangıçla aynı; eski başarısız dal silinmiş durumda.
- Yalnızca test ve teşhis yapıldı. UI/runtime/eşleştirme kuralı değiştirilmedi, paket veya staging manifesti yayımlanmadı. UI/iki eklenti izolasyonu hâlâ kanıtlanmadı; yayın engeli sürüyor.

### Güncel bütün gönderilmiş önceki commitler
| Tam SHA | İşlem | Bağlantı |
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

Yeni eklenen SON-CI-INCELEMESI.json da yalnızca bu test çalışmasına aittir. Geri alma hâlâ yalnız yeni dal ve yeni dosyalarla sınırlı; Mi Box kurulumu, yeni tercih alanı veya staging yayını yok. Bu son belge commit’inin SHA’sı git log -1 ile bulunur.

## Kullanıcının devam talimatı — düzeltme aşaması başladı
Önceki SHA f0abf3b1d800b344984167a5ffd77d6cc65fcb99. Kullanıcı düzeltme/derleme/sonuç raporu için devam yetkisi verdi; önceki yalnız tanı sınırı artık tarihsel kayıttır.
Yeni test SeriesAliasContractTest.kt mevcut V83 kodunda “Confirmed S1 alias is missing” ile beklenen şekilde başarısız oldu. İlk derleme denemesinde test Log stub'ı eksikti; stub eklenip gerçek assertion hatası görüldü. Koruma betiğine yalnız bu yeni test yolu eklendi. Runtime değişmedi.
Geri alma: yalnızca bu yeni test/izin listesi/doküman commit'ini yeni dalda revert et. Sonraki aşama doğrulanmış kimlikle sınırlı alternatif başlık düzeltmesi. Yeni test paketi için ayrı Android kullanıcı profili + aynı profilde başka EA-FB olmaması zorunlu olacak; ana profil ve birlikte kurulum fail-closed engellenecek. UI dosyaları korunacak.
