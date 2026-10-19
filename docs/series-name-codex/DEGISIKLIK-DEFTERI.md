# Değişiklik defteri

Tarih: 2026-10-19 (Europe/Istanbul). Temel: `16396702470eb1f81cc6dac4360e6b0ff84927d1`. Dal: `test/ea-fb-series-name-codex-20261019`.
Her mantıksal aşama ayrı commit olur. Bir commit kendi SHA'sını kendi içine yazamaz; SHA, sonraki kayıt commit'inde yer alır. Git geçmişi kesin kaynaktır; kayıtlar geriye dönük değiştirilmez.

## 1 — Başlangıç inceleme ve geri alma planı
- Önceki SHA: 16396702470eb1f81cc6dac4360e6b0ff84927d1; yeni SHA: bu kaydı oluşturan commit (`git log --format=fuller -- docs/series-name-codex`).
- Dosyalar: bu klasördeki başlangıç, uzak dal uçları, değişiklik defteri, devam notları, geri alma planı.
- Gerekçe: V83'ü ve geri dönüş sınırını sabitlemek; uygulama kodu değişmedi.
- Test: gerçek git ls-remote --heads/--tags; V83 sha256sum; temiz git status ve başlangıç farkı.
- Sonuç: V83 HEAD ve paket hash'i kullanıcı referansıyla aynı. PR/API erişimi Forbidden; eski dal temizliği bekliyor.
- Geri alma: yalnızca bu yeni daldaki commit'i git revert ile geri al; V83'e hiçbir işlem uygulama.
- GitHub bağlantısı: push doğrulandıktan sonra tam SHA ile sonraki kayıtta yer alacak.

## 1 — Kesin commit kaydı
- Yeni SHA: 45956ae (tam SHA için git rev-parse 45956ae). GitHub push doğrulandı.
- Bağlantı: https://github.com/eaatabay/EA-FB/commit/45956ae
- Koruma planı, başlangıç ve geri alma belgeleri kalıcı.

## 2 — Değiştirilmemiş V83 üzerinde tanı fixture'ları
- Önceki SHA: 45956ae. Yeni SHA: bu aşamayı oluşturan commit; sonraki kayıt tam SHA'yı ekler.
- Dosyalar: core-tests/clean/SeriesNameDiagnosisTest.kt, scripts/test-series-name-codex.sh, bu defter ve DEVAM-NOTLARI.md.
- Gerekçe: gerçek adaptörlerde kısa/uzun ad kapısının hangi aşamada durduğunu ölçmek; bilinmeyen bir dizi kimliği tahmin edilmez.
- Etki: yalnızca test ve doküman; Android uygulama kaynakları, film yolu, UI ve paket kimliği değişmedi.
- Test: mevcut 12 Kotlin programı + 14 Python testi geçti; yeni tanı programı ayrıca çalıştırılır. Fixture gerçek site/cihaz kanıtı değildir.
- Geri alma: yalnızca yeni daldaki bu commit'e git revert; V83'e işlem yok.
- Yeni test sonucu: dört adaptörde **45/45 tanı assertion'ı PASS**. Bu, V83 davranışının ölçümüdür; yeni isim düzeltmesinin veya gerçek Organize İşler oynatmasının testi değildir.

## 2 — Kesin commit kaydı
- Önceki: 45956ae74fdc512fa838ed6e99112a41baba9187. Yeni: 069824f8433792f8f5dee2b7079390ee1f9e0374.
- https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- Push başarıyla doğrulandı. Test sonucu: 45/45 tanı assertion'ı; çalışma davranışında değişiklik yok.

## 3 — V83 dosya ve paket koruması
- Önceki SHA: 069824f8433792f8f5dee2b7079390ee1f9e0374. Yeni SHA: bu aşamanın commit'i, sonraki kayıt kesinleştirir.
- Dosyalar: scripts/verify-series-v83-baseline.py, tests/test_series_v83_baseline.py ve bu defter.
- Gerekçe: dar izin listesiyle yalnızca yeni tanı/test/doküman dosyalarına izin vermek. Mevcut 312 dosyanın **tamamı** byte düzeyinde V83 ile aynı olmalı. Hiçbir UI/adapter/runtime metodu istisna tutulmadı.
- Test: koruma betiği PASS; iki Python testi PASS; dokuz ayrı simüle UI/ayar/adapter/workflow/manifest/CS3 mutasyonu beklendiği gibi reddedildi. Gerçek dosyalara mutasyon yazılmadı.
- Etki: uygulama ve korunan dağıtım değişmedi.
- Geri alma: yalnızca yeni daldaki bu aşamayı git revert ile geri al. V83 dalına dokunma.

## 3 — Kesin commit kaydı
- Önceki: 069824f8433792f8f5dee2b7079390ee1f9e0374. Yeni: 36a4820 (tam SHA git rev-parse 36a4820 ile).
- https://github.com/eaatabay/EA-FB/commit/36a4820
- 312 korunan dosya ve paket hash'i PASS; iki test / dokuz negatif mutasyon PASS.

## 4 — Yayınsız, yalnızca yeni dala ait CI
- Önceki: 36a4820. Yeni SHA: bu aşamanın commit'i; sonraki kesin kayıtta yer alır.
- Dosyalar: .github/workflows/series-name-diagnostics.yml ve bu defter.
- Gerekçe: dört adaptörün tanı fixture'larını, V83 regresyonlarını, kaynak korumasını ve Android derlemesini aynı GitHub kaydında çalıştırmak.
- Etki: yeni dala dar ref/path filtresi; contents:read. Eski workflow'lar değiştirilmedi. Otomatik paketleme/yayın/push yok; yalnızca log artifact'i var.
- Test: yerel kullanılan komutlar fixture ve kaynak korumasında geçti. Workflow GitHub'da henüz çalıştı/başarılı denmiyor; API erişimi Forbidden.
- Geri alma: bu yeni workflow'u yalnızca yeni daldan sil veya bu aşamayı revert et. V83/production'a işlem yok.
- Workflow: https://github.com/eaatabay/EA-FB/actions/workflows/series-name-diagnostics.yml?query=branch%3Atest%2Fea-fb-series-name-codex-20261019

## 4 — Kesin commit kaydı
- Önceki: 36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6. Yeni: fb16e9ced070f1fe0c6d6cf93aaa863f79745099.
- https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- Gerçek ilk CI: https://github.com/eaatabay/EA-FB/actions/runs/39309504108 (son sorguda in_progress, başarı iddiası yok).

## 5 — Resmî başlık kanıtı ve eski başarısız dal temizliği
- Önceki: fb16e9ced070f1fe0c6d6cf93aaa863f79745099. Yeni: bu belge commit'i.
- Dosyalar: BASLIK-KANITI.json ve bu defter; uygulama değişmedi.
- Ağ değişikliği sonrası GitHub API/TMDb/DiziYou erişimi yeniden denendi ve çalıştı. TMDb TV 320754 için uzun ad resmî alternatif başlıklar sayfasında season 1 olarak kayıtlı; sezon sayfası Karun Hazinesi, 2026 ve 8 bölüm ile doğrulandı. DiziYou TVSeries JSON-LD adı ve startDate=2026 aynı uzun adı doğruluyor. DiziBox HTTP 403, Bronze canlı arama henüz denenmedi.
- Bu kanıt, yalnızca TMDb 320754 / SERIES / 2026 / S1 / E1..8 için hedefli alternatif aday eklemeye yeterlidir. Diğer dizilerde genel ad gevşetmesi gerekmez.
- Eski test/organize-isler-season-title-20261019 HEAD'i tekrar 16396702470eb1f81cc6dac4360e6b0ff84927d1 olarak doğrulandı. Açık PR'larda head/base bağımlılığı bulunmadı. V83 ile aynı tree/history; korunacak özgün commit yok. Yalnızca bu eski dal, tam beklenen SHA koşullu silme ile silindi. Son git ls-remote sorgusunda eski dal yok, V83 HEAD değişmedi.
- Geri alma (eski dal temizliği): kullanıcı gerek görürse yalnızca eski adı V83 SHA'sına yeniden oluştur; V83 dalını değiştirme. Kod değişikliği yok.
- Android ortamı: gerekli build-tools 34.0.0 resmî sdkmanager ile kuruldu; gradle :EA-FB:make yerelde BUILD SUCCESSFUL (1m17s). Bu hâlâ V83 kimlikli yerel derlemedir, yeni paket değildir ve yayımlanmadı.

## 6 — Kullanıcının yeni kimlik bilgileri: yalnızca tanı testi
- Önceki SHA: 87344068c46877c720e8f1afa53084cc06400fab. Yeni SHA: bu aşamanın test commit'i; sonraki kayıt kesinleştirir.
- Dosyalar: SeriesNameDiagnosisTest.kt ve bu klasördeki üç kayıt dosyası ile BASLIK-KANITI.json.
- IMDb tt38732131 / Netflix 82047949 bağlam olarak eklendi; otomatik eşleştirme kuralı oluşturulmadı. TV adayı 320754, iki ayrı film kimliğinden (30634 ve 577990) PlaybackQuery üzerinden ayrılıyor. Bilinen uzun ad V83'te otomatik türetilmiyor.
- EAProvider'ın güncel config URL'si okunup CatalogRelayPolicy.approvedOrigin ile birebir eşleştirildi. Kullanıcının talep ettiği salt okunur katalog kontrolü staging relay'de CONNECT 403 ile engellendi; hiçbir Worker/D1 yazması veya deploy yok.
- CI 39309504108 tamamlandı: success; SHA fb16e9ced070f1fe0c6d6cf93aaa863f79745099. API'de tüm adımlar success: dosya koruması, regresyonlar/tanı, SDK, Android derleme ve log artifact'i. Log indirmenin yönlendirildiği sonuç sunucusu Forbidden; ayrıntılı log içeriği henüz okunamadı. Artifact 12215167805 (series-name-diagnostic-logs), 52184 byte, expired=false.
- İlk genişletilmiş test koşusunda sezon 0'ı geçersiz sayan yeni test beklentisi hatalıydı; V83 sezon 0'ı özel bölümler için destekliyor. Yalnızca test beklentisi negatif sezon olarak düzeltildi; uygulama değiştirilmedi.
- UI/kimlik/ayar/adapter/SourceSearchTitles/oynatıcı kodu değişmedi. Yeni paket, staging manifest veya yayın yok. Geri alma: yalnızca yeni daldaki bu test commit'ini revert et.

## Tam commit envanteri (bu kayıt commit'inden önce)
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab

Bu envanteri güncelleyen commit kendi SHA'sını dosya içinde içeremez; `git log -1 -- docs/series-name-codex` güncel kayıt commit'ini verir. Sonraki aşamada bu commit de tam SHA ile envantere eklenir. GitHub dal geçmişi kesin kaynaktır.

Güncel yerel kimlik tanısı: **53/53 PASS**, dört adaptör; 312 korunan V83 dosyası byte-identical. UI/iki eklenti izolasyonu henüz kanıtlanmadı; yayın yok.

## 7 — UI riskinin DEX incelemesi ve uzak dal koruması
- Önceki SHA: efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4. Yeni SHA: bu kayıt commit'i.
- Dosyalar: UI-HOOK-INCELEMESI.json, UI-HOOK-RISKI.md, UZAK-DALLAR-SON.tsv ve üç kalıcı kayıt dosyası.
- Gerekçe: kaynak incelemesini yayımlanmış V83 DEX metot referanslarıyla desteklemek; iki eklenti birlikte kullanımını kanıtsız güvenli saymamak.
- Etki: yalnızca inceleme/doküman; UI veya runtime dosyaları değişmedi.
- Test: dört Application lifecycle register çağrısı ve apiName render kapıları DEX'te görüldü. Cihaz/iki kurulum testi yapılmadı; izolasyon **belirsiz/yayın engeli**.
- Uzak refs: başlangıçtaki 23 korunan mevcut dalın HEAD'i değişmedi. Yalnızca yeni dal eklendi ve kullanıcı tarafından silinmesi istenen eski başarısız dal silindi.
- İnceleme aracı bir __pycache__ dosyası üretti; dar koruma kontrolü bunu reddetti. Yalnızca aracın ürettiği .pyc kaldırılıp kontroller yeniden çalıştırıldı; izin listesi gevşetilmedi.
- Geri alma: yalnızca yeni inceleme belgelerini bu daldan kaldır / bu kayıt commit'ini revert et. V83 veya kullanıcı ayarlarına işlem yok.

Son mantıksal test commit'i: efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4. Kimlik tanısı 53/53 yerel PASS. Yeni CI run **39310538425**: https://github.com/eaatabay/EA-FB/actions/runs/39310538425 — son sorguda queued, başarı iddiası yok. Önceki 39309504108 success; ayrıntılı log erişimi hâlâ proxy 403. EA-FB katalog doğrulaması aynı ağ engeli nedeniyle bekliyor.

### Güncel tam SHA envanteri
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab
- `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` — test(series): track researched TV identity separately from films — https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4

Bu doküman kayıt commit'inin SHA'sı git log -1 -- docs/series-name-codex ile bulunur; sonraki aşamada envantere eklenir.

## 8 — Katalog engelini ayıran salt okunur CI tanısı
- Önceki SHA: 63782dbd89fc52d4382a973c82fac67ca442ad75. Yeni SHA: bu aşamanın commit'i, sonraki kayıt kesinleştirir.
- Dosyalar: scripts/diagnose-series-catalog.py, tests/test_series_catalog_diagnosis.py, .github/workflows/series-name-catalog-diagnosis.yml, yeni üç yolu açıkça tanıyan mevcut tanı koruma betiği; bu klasörde CI incelemesi, DiziYou gözlemi ve üç kayıt dosyası.
- Gerekçe: yerel relay CONNECT 403 sorununu GitHub runner üzerinden ayırmak; EA-FB'nin gerçekten kullandığı config/JSON'u ölçmek.
- Kapsam: yalnızca GET; GitHub contents:read token'ı sadece sabit GitHub config API adresine gider. Relay yalnızca sabit onaylı staging origin. Yönlendirme izlenmez. TV ayrıntısı, external_ids, sezon 1 ve ad araması; video/oynatma isteği yok. Başka Worker/D1/production host veya yazma/deploy işlemi yok.
- Üç negatif/kapsam testi PASS: yanlış film/IMDb, yanlış sezon veya bölüm kümesi reddi; ilgisiz credential/playback alanlarının rapordan çıkarılması. 312 V83 dosya koruması PASS. Mevcut UI/adapter/SourceSearchTitles/ayar kodu değişmedi.
- Yeni workflow sadece bu dal ve kendi dosya yollarında; contents:read; yalnızca güvenli JSON artifact'i üretir. Otomatik paket/yayın yok.
- CI 39309504108 log arşivi alternatif desteklenen API ile indirildi ve ayrıntılı okundu. Testler ve derleme PASS; D8 Kotlin metadata rewrite uyarıları CI-39309504108-INCELEME.json içinde kayıtlı. Önceki log erişim engeli bu yöntemle aşıldı; gizli URL/ham log repoya konmadı.
- Canlı DiziYou POST data_fetch/keyword=Organize İşler yanıtı HTTP 200 ve yalnız uzun başlığı döndürdü. Gerçek seri sayfasında TVSeries/startDate=2026 ve S1 E1..8 etiketleri görüldü. DIZIYOU-GOZLEM.json yalnızca güvenli başlık/episode etiketlerini içerir; iframe/çerez/nonce/player verisi yok. Gerçek stream veya cihaz testi yapılmadı.
- Geri alma: yalnızca yeni tanı workflow/script/test ve yeni dokümanları yeni daldan kaldır/revert et; eski V83 dalına veya UI'ya işlem yok.

### Güncel bütün SHA kayıtları
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab
- `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` — test(series): track researched TV identity separately from films — https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4
- `63782dbd89fc52d4382a973c82fac67ca442ad75` — docs(series): audit V83 UI hooks and preserve diagnostic handoff — https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75

Kayıt commit'i kendi SHA'sını içeremez; güncel SHA için git log -1 -- docs/series-name-codex. Sonraki kayıt bu SHA'yı ekler.

## 9 — Gerçek EA-FB yanıtını tanı testine bağlama
- Önceki SHA: 22922df2f887c6c14cf687bb63bd13118476bb22. Yeni SHA: bu test commit'i; sonraki kayıt kesinleştirir.
- Dosyalar: core-tests/clean/SeriesNameDiagnosisTest.kt; EA-FB-KATALOG-GOZLEM.json, BASLIK-KANITI.json, SONUC-TESHIS.md ve üç kalıcı kayıt dosyası.
- Gerekçe: kullanıcı tarafından verilen TV/IMDb kimliğini artık gerçek EA-FB JSON'uyla doğrulamak ve başlık kaybını gerçek adlar üzerinden tekrar üretmek.
- Değişiklik: query artık kaydedilmiş gerçek id/name/original_name/first_air_date alanlarından V83 PlaybackQuery ile kuruluyor. Kaynak uzun adıyla mevcut normalize kapısındaki ret ve açıkça verilen alternatif adın kabulü karşılaştırılıyor. İki film gerçek arama yanıtındaki movie kimliklerinden okunuyor.
- Test: yerel 59/59 tanı assertion'ı PASS; yeni tanı/koruma Python testleri 5/5 PASS; 312 mevcut dosya koruması PASS. JSON yalnız güvenli metadata içeriyor; adapter yanıtları sentetik kalıyor.
- CI: 39310538425 success (53 eski assertion ve Android derleme); 39311300009 success (gerçek katalog GET'leri ve IMDb/sezon/episode kimliği). İkisinin de tam log arşivi API ile indirildi ve incelendi.
- Etki: yalnızca test ve belgeler. Yeni runtime eşleştirme kuralı veya UI değişikliği yok.
- Geri alma: bu test/doküman commit'ini sadece yeni dalda git revert et; V83'e işlem yok.

### Güncel tam SHA envanteri
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab
- `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` — test(series): track researched TV identity separately from films — https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4
- `63782dbd89fc52d4382a973c82fac67ca442ad75` — docs(series): audit V83 UI hooks and preserve diagnostic handoff — https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75
- `22922df2f887c6c14cf687bb63bd13118476bb22` — test(series): probe approved catalogue identity from read-only CI — https://github.com/eaatabay/EA-FB/commit/22922df2f887c6c14cf687bb63bd13118476bb22
