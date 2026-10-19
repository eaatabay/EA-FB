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
