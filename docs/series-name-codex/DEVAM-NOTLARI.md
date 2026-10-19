# Devam notları — 2026-10-19

Dal: **test/ea-fb-series-name-codex-20261019**. Temel / ilk HEAD: **16396702470eb1f81cc6dac4360e6b0ff84927d1**.
Son kaydedilmiş test aşaması: 22922df2f887c6c14cf687bb63bd13118476bb22. Bu kayıt gerçek metadata replay testini ekliyor; commit SHA'sı sonraki kayıt ile kesinleştirilecek.

## Güncel sonuç
EA-FB JSON'u CI runner üzerinden doğrulandı: TV 320754, IMDb tt38732131, name/original_name Organize İşler, 2026-10-16, sezon 1 Karun Hazinesi, 8 bölüm. Aynı aramada iki film ayrı movie kimlikleri: 30634 (2005) ve 577990 (2019). EA-FB-KATALOG-GOZLEM.json ve SONUC-TESHIS.md gerçek kanıtı içerir. Yerel proxy 403 sürse de bu artık katalog kimliği için engel değil; salt okunur CI probe'u kullanılabiliyor.
V83 aynı name/original_name'i tek kısa ad adayına düşürüyor. Canlı DiziYou sonucu uzun adı döndürüyor. Gerçek adlardan oluşan mevcut tam eşleşme kapısı reddi tanı testinde üretildi. DiziYou sayfasında sekiz bölüm etiketi var. Gerçek video, extractor veya Mi Box doğrulanmadı.

## Testler ve CI
- Yerel son tanı: **59/59 PASS**; gerçek kaydedilmiş güvenli metadata + sentetik adapter yanıtları.
- V83 12 Kotlin programı / 14 Python testi PASS; yeni Python tanı/koruma testleri 5/5 PASS.
- 39309504108 success, loglar okundu; 45 tanı / Android derleme. D8 metadata uyarıları raporda.
- 39310538425 success, loglar okundu; 53 tanı / Android derleme.
- 39311300009 success, loglar okundu; EA-FB katalog kimliği doğrulaması.
- 39311299938: son sorguda Android derlemesi sürüyordu; sonucu tekrar kontrol et, başarı varsayma.
Yeni test commit'i kendi tanı workflow'unu tetikler; run ID son kayıtta eklenecek.

## Kesin kapsam / yayın engeli
Kullanıcının son talimatı **yalnızca yeni dalda test ve teşhis**. Uygulama/UI/SourceSearchTitles/adapter/oynatıcı/ayar/kimlik değişmedi. 312 V83 dosyası byte-identical; V83 CS3 hash'i 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66.
Yeni sürüm/paket/tercih alanı ve staging manifesti henüz yok. UI-HOOK-RISKI.md: dört application callback ve provider kapısı var; iki eklenti/unload/marker/ayar izolasyonu cihazda kanıtlanmadı. Risk çözülmüş sayılmaz; yayın ve Mi Box kurulum önerisi yok.
Sonraki kod düzeltmesi ancak kullanıcının sonraki kapsamı içinde; kanıtsız genel contains/substring veya dizi-film eşleştirmesi üretme.

## Yeni workflow'lar
- .github/workflows/series-name-diagnostics.yml: yalnız yeni dal; test / kaynak koruması / değişmemiş runtime Android derlemesi; log artifact'i; yayın yok.
- .github/workflows/series-name-catalog-diagnosis.yml: yalnız yeni dal; GET ile sabit staging metadata tanısı; contents:read; güvenli JSON artifact'i; Worker/D1 yazması, üretim hostu veya yayın yok.

## Geri dönüş ve uzak dallar
Eski başarısız test/organize-isler-season-title-20261019 dalı kullanıcı talebiyle silindi; silmeden HEAD=V83 ve açık PR head/base yokluğu doğrulandı. Diğer 23 mevcut uzak dal HEAD'i değişmedi. Yeni test dalı kaldırılınca V83 üzerinde reset/force-push/cherry-pick gerekmez. GERI-ALMA-PLANI.md güncel.

## Bütün kaydedilmiş SHA'lar
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab
- `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` — test(series): track researched TV identity separately from films — https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4
- `63782dbd89fc52d4382a973c82fac67ca442ad75` — docs(series): audit V83 UI hooks and preserve diagnostic handoff — https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75
- `22922df2f887c6c14cf687bb63bd13118476bb22` — test(series): probe approved catalogue identity from read-only CI — https://github.com/eaatabay/EA-FB/commit/22922df2f887c6c14cf687bb63bd13118476bb22

Bu belge kayıt commit'i kendi SHA'sını içeremez; `git log -1 -- docs/series-name-codex` güncel kayıt SHA'sını verir, sonraki kayıt envantere ekler.
