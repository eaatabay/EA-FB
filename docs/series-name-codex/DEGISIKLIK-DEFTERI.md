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
