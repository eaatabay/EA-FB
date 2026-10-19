# Devam notları — 2026-10-19 (Europe/Istanbul)

Dal: test/ea-fb-series-name-codex-20261019. Temel/ilk HEAD: 16396702470eb1f81cc6dac4360e6b0ff84927d1.
Son kaydedilmiş aşama: 87344068c46877c720e8f1afa53084cc06400fab. Şimdiki aşama: kullanıcının kimlik araştırmasını yalnızca tanı testlerine eklemek.

## CI ve yerel durum
- CI run ID: **39309504108**, **success**, HEAD fb16e9ced070f1fe0c6d6cf93aaa863f79745099. https://github.com/eaatabay/EA-FB/actions/runs/39309504108
- API'deki bütün job adımları success. Ayrıntılı log indirimi results-receiver.actions.githubusercontent.com erişiminde engelli; loglar okunmuş sayılmaz.
- Yerel 12 Kotlin programı, 14 Python V83 testi, 45 ilk tanı assertion'ı ve 2 yeni koruma testi geçti. Kimlik eklenmiş son tanı testi **53/53 PASS**.
- Yerel Android gradle :EA-FB:make başarılı. Bu derleme değişmemiş V83 kimliğini taşıyor; yayımlanmadı, yeni test paketi sayılmaz.
- 312 V83 dosyasının tamamı byte-identical. V83 paket hash'i 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66.

## Kimlik kanıtı ve engel
Kullanıcı: Netflix başlığı Organize İşler (2026), 8 bölüm; IMDb tt38732131; Netflix 82047949. TMDb web TV 320754 ve season 1 alternatif ad Organize İşler: Karun Hazinesi doğrudan doğrulandı. Netflix/IMDb sayfaları HTTP 403; kullanıcı bilgisi olarak kayıtlı.
EA-FB katalog yanıtı henüz doğrulanmadı. Kodun kullandığı config güncel GitHub'dan okundu; tek onaylı relay ea-fb-catalog-v6-staging.eaatabay.workers.dev. /v1/tv/320754?language=tr-TR isteği proxy CONNECT 403. Bu adrese network izni taslakta kayıtlı; uygulanıp erişim sağlanınca aynı salt okunur istek tekrarlanmalı. name/original_name/first_air_date/id/external_ids/season episode bilgilerini ayırarak kaydet. Üretim Worker/D1 değişikliği yapma.

## UI / iki eklenti riski — yayın engeli
EAPlugin dört application lifecycle hook'u kuruyor; provider bazlı render kapıları var ancak listener kayıtları uygulama genelinde. Marker sabitleri aynı. Plugin unload sırasında listener kaldırma veya iki kurulumun cihaz testi kanıtlanmadı. Bu, V84 arızasının kesin nedeni değildir. Ayrı CS3 adı/ayar alanı tek başına yeterli kanıt sayılmaz. Kullanıcının son talimatıyla UI kodu değiştirilmeyecek ve paket yayımlanmayacak.
Yeni paket kimliği/sürüm/tercih alanı henüz atanmadı; aynı V83 kimlikli yerel derlemeyi Mi Box'a yükleme.

## Sonraki adım
Önce erişim sonrası gerçek EA-FB JSON'unu doğrula ve CI ayrıntılı loglarını oku. Mevcut V83'ün sağlanan uzun adı dört adaptörde desteklediği kanıtlandı; metadata eksikliği mi, arama reddi mi, bölüm/extractor sorunu mu gerçek yanıttan ayır. Genel substring kuralı veya tahmini kimlik eşleştirmesi oluşturma. Kod düzeltmesi/yayın, kullanıcının şu anki yalnızca test-teşhis kapsamının dışındadır.
Eski başarısız test/organize-isler-season-title-20261019 dalı artık silindi: aynı V83 HEAD'i, açık PR head/base yokluğu ve son ls-remote ile doğrulandı. Başka eski dal HEAD'i değişmedi.

## Commit envanteri
- `45956ae74fdc512fa838ed6e99112a41baba9187` — docs(series): record V83 baseline and rollback boundaries — https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187
- `069824f8433792f8f5dee2b7079390ee1f9e0374` — test(series): isolate V83 title rejection across four adapters — https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374
- `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` — test(series): enforce byte-level V83 baseline preservation — https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6
- `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` — ci(series): add branch-scoped diagnostics without publication — https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099
- `87344068c46877c720e8f1afa53084cc06400fab` — docs(series): record verified season alias and obsolete branch cleanup — https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab

Bu belgeyi kaydeden commit için git log -1 -- docs/series-name-codex; sonraki aşamada tam SHA eklenir.

Güncel yerel kimlik tanısı: **53/53 PASS**, dört adaptör; 312 korunan V83 dosyası byte-identical. UI/iki eklenti izolasyonu henüz kanıtlanmadı; yayın yok.
