# EA-FB v6 — Kaynak Bekçisi yerel sürüm kontrol kapısı

**Bu betikler yalnızca v6 geliştirme dalında çalışır.** Yayındaki v5, main,
ea-fb-catalog Worker ve Cloudflare'daki herhangi bir veritabanına dokunmaz.
Gerçek kaynaklara HTTP isteği göndermez, DRM/auth bypass yapmaz.

## Tek komut

Node.js 22+, Python 3 ve npm erişimi olan temiz EA-FB v6 checkout'unda:

```bash
cd source-watchdog
npm install
npm run test:release-local
```

`test:release-local` komutu bir aşama başarısız olursa derhal durur:

1. Node 22, `node:sqlite` ve Ed25519 desteği.
2. **Bütün** Node testleri (`npm test`): politika, gerçek SQLite uyum
   katmanı, Cron runner, admin JWT/işlem, imzalı snapshot ve ağ güvenliği.
3. Python ile şema/migration ve SQLite testleri.
4. Cloudflare Wrangler'ın **yalnızca yerel** workerd+D1 test ortamı:
   iki migration, 30 kurgusal kaynak, 8 ayrı 15 dakikalık Cron tetiklemesi,
   ardından aynı Cron zamanını bir kez daha çalıştırarak tekrarlı yazım testi.

## Başarı kriterleri

| Ölçüm | Beklenen |
|---|---:|
| Kurgusal kaynak | 30 |
| Sağlıklı kaynak | 29 |
| Admin incelemesi bekleyen | 1 |
| Onaylı test alan adına geçiş | 2 |
| Kaydedilen kaynak kontrolü | 59 |
| Audit olayı | 89 |
| Global veritabanı revizyonu | 89 |
| Açık kaynak kilidi | 0 |
| Aynı Cron zamanı tekrarında yeni yazım | 0 |

Testte `*.example.org` dışına ağ isteği yapılmaz; kurgusal adapterların
kendileri hiçbir ağ isteği yapmaz. `wrangler` işlemlerinin tamamında
`--local` ve rastgele oluşturulmuş geçici `--persist-to` dizini kullanılır.
Test sonunda yerel süreç kapatılır ve geçici dizin silinir. Tracked
`wrangler.jsonc` dosyasında **bütün çalışma bayrakları kapalıdır**.

**Test geçmeden** ayrı Cloudflare test hesabına deploy, gerçek kaynak
adapteri veya Mi Box güncellemesi yapılmamalıdır.

## Sınırlar

Bu betiğin depoda bulunması çalıştırıldığı anlamına GELMEZ. Yeni Wrangler
paketi henüz bu çevrimdışı çalışma ortamında kurulu olmadığı için gerçek
workerd+D1 testini burada tamamlayamadık. Testin başarılı sonucu oluşunca
rapordaki sayaçlar terminalde `PASS LOCAL Wrangler+D1` olarak görünür.
İzinli gerçek kaynakların IP-pinned taşıma katmanı ve Android cihazdaki
uçtan uca playback testi **ayrı aşamalardır**.
