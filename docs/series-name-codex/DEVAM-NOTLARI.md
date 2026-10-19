# Devam notları — 2026-10-19

Dal: `test/ea-fb-series-name-codex-20261019`. Temel: `16396702470eb1f81cc6dac4360e6b0ff84927d1`.
Son kalıcı commit: `45956ae74fdc512fa838ed6e99112a41baba9187` (ilk kayıt); bu tanı aşamasının SHA'sı sonraki kayıt commit'inde yazılacak.
CI run ID: yok; API erişimi Forbidden, CI çalıştırıldığı iddia edilmiyor.

## Çalıştırılanlar
- Değişmemiş V83: 12 Kotlin test programı ve 14 Python paket/koruma testi geçti.
- `bash scripts/test-series-name-codex.sh`: dört gerçek adaptörde 45 tanı assertion'ı geçti. Yanıtlar sentetik; TMDb kimliği 987654321 test kimliğidir.
- Kısa adla uzun başlıklı sentetik sonuç dört adaptörde seri sayfası / episode / extractor öncesi reddediliyor. Aynı uzun ad query.alternateTitles içine açıkça verildiğinde dört adaptör de mevcut V83 koduyla teklif üretiyor. Bu deney TMDb'nin bu adı sağladığını kanıtlamaz.
- Film/dizi türü, yanlış yıl, sezon, bölüm ve benzer başlık için Bronze retleri; DiziYou sezon/bölüm ve DiziBox resolve aşaması tanı kontrolleri geçti. Kaynaklardan gerçek oynatılabilir link üretilmedi.
- Android derlemesi ilerledi fakat AGP'nin ayrıca istediği build-tools 34.0.0 ve lisans kurulumu eksik. Ortam sorunu inceleniyor; derleme başarılı değil.

## Kesinleşmeyenler
Cihazdaki gerçek TMDb ID/name/original_name, kaynakların gerçek yanıtları ve hata logu yok. TMDb web, kaynak siteleri ve GitHub API CONNECT 403; mevcut Git HTTPS okuma/yazma çalışıyor. Gerekli alan adları ortam ayarları taslağına kaydedildi, runtime'a uygulanmış değil.

## Sonraki adım
Gerçek kimlik ve başlık kanıtı geldikten sonra metadata eksikliği ile adapter reddini ayır. Mevcut kod zaten verilen tam alternatif adı desteklediğinden, kanıtsız genel gevşetme yapma. V83 kaynaklarını koruyan yeni testlerde sorun tekrar üretilmeden çözüm iddia etme.
Uygulama kodu, UI, kimlik, ayarlar, korunan workflow ve dağıtımlar değiştirilmedi. Yeni paket/sürüm numarası atanmadı; staging veya Mi Box kurulumu yok. Global lifecycle hook'ları nedeniyle aynı CloudStream kurulumunda birlikte kullanım güvenli sayılmaz.
Eski başarısız dal HEAD'i V83 ile aynı; korunacak PR/bağlı iş yokluğu API engeli yüzünden doğrulanamadığından silinmedi.
