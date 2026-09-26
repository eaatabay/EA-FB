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

**26.09 yerel doğrulama:** Güncel GitHub fixture üreticisi gerçek policy koduyla
çalıştırılıp 25.289 bayt SQL oluşturdu. Aynı çıktının uzunluğu, FNV32 ve
Adler32 toplamlarıyla birebir eşleşen SQL dosyası SQLite üzerinde
iki gerçek migration ile çalıştırıldı: **30 kayıt, 30 audit, 0 probe,
0 açık kilit, global revizyon 30**. Tekrar içe aktarıldığında
**sıfır ilave kayıt/audit** oluştu. Bu test gerçek SQLite üzerindedir;
Wrangler'ın workerd+D1 çalışma zamanı henüz doğrulanmadı.

**D1 uyumluluk düzeltmesi:** İlk fixture SQL üreticisi açık `BEGIN TRANSACTION` /
`COMMIT` yazıyordu. Cloudflare D1 dosya içe aktarması bunu desteklemediği
İçin komutlar çıkarıldı. Üretici artık yalnızca 30 `INSERT OR IGNORE` yazıyor;
regresyon testi transaction komutlarının geri gelmesini yasaklıyor.

**Yerel veritabanı güvenliği (26.09):** Eski `npm run test:local`
komutunun mevcut yerel D1 veritabanını değiştirme riski giderildi:
artık her zaman tek kullanımlık, geçici D1 kullanan aynı sürüm-kapısı
betiğini çağırıyor. GitHub'daki `make-local-config.mjs` dosyasının
birebir kopyası Node 22 üzerinde test edildi: 10/10 manipüle edilmiş
yerel/tracked yapılandırma reddedildi. Yerel config ilk oluşturulduğunda
dosya izni `0600`; ikinci oluşturma mevcut dosyanın üzerine yazmadı.
Wrangler alt süreçleri Cloudflare hesap tokenlarını miras almıyor,
tesadüfi gerçek D1 binding'i, ek route veya admin yazma yetkisi eklenmesi
tam yapı karşılaştırmasıyla engelleniyor.

**Yerel Wrangler komut güvenliği:** Sürüm kapısında izin verilen D1 işlemleri
yalnızca geçici test veritabanına uygulanan iki migration, tek onaylı SQL seed
dosyası ve **iki sabit, salt okunur sayım sorgusudur**. Başka bir SELECT,
UPDATE, DELETE, `--remote`, `deploy`, farklı veritabanı, env/profile veya
komut tekrarı anında reddedilir. Wrangler'ın JSON çıktısı ve 30 kaynaklık
kabul sayaçları ayrı modülde tam olarak doğrulanır. Bu modülün GitHub'la
aynı SHA'ya sahip kaynak/test dosyaları Node 22 üzerinde **4/4** başarılı
çalıştırıldı; 18 zararlı/yanlış CLI argümanı senaryosu reddedildi.
Bu doğrulama **gerçek Wrangler sürecinin yerini tutmaz**.

**Test geçmeden** ayrı Cloudflare test hesabına deploy, gerçek kaynak
adapteri veya Mi Box güncellemesi yapılmamalıdır.

## GitHub üzerinden test (henüz çalıştırılmadı)

`.github/workflows/v6-watchdog-local-check.yml` ile ayrı bir **opt-in,
salt-okunur** test işi hazırlandı. Yalnızca aynı depodaki
`feature/detail-dual-ratings-v6` dalından `main` hedefine açılan PR'da
çalışır; `push` ve `schedule` tetikleyicisi yoktur. GitHub Actions
dakikası tüketmemek için şu aşamada bir PR açılmadı ve iş tetiklenmedi.
`workflow_dispatch` ileride workflow varsayılan dalda da bulunduğunda
kullanılabilir; mevcut özellik dalındaki dosyayı tek başına menüde
görünür kılmaz.

İş Node 22 / Python 3.13 kurar, bağımlılıkları indirir ve
`npm run test:release-local` komutunu çalıştırır. `contents: read`
yetkisi dışında GitHub izni veya Cloudflare sırrı verilmez. Gerçek
Cloudflare'a yükleme, uzaktan D1 oluşturma, Worker deploy ve gerçek
kaynak taraması içermez.

**Yeni sonuç:** GitHub dosyalarıyla SHA'sı eşleşen Node testleri, yapı
değişikliği/sahte işlem kontrolünde **4/4**, yalnızca PR/manual
tetiklenen Actions workflow güvenliğinde **3/3** geçti. YAML yapısı da
ayrı incelendi. Wrangler CLI'nin karma (bir hata + bir başarı) JSON
yanıtını artık kabul etmiyoruz. Bu sonuçlar gerçek workerd/D1
çalıştırıldığını göstermez.

## Sınırlar

Tam v6 doğrulaması olan `bash scripts/verify-v6.sh` da artık yerel Wrangler
D1/Cron testini **Kotlin derlemesi ve `.cs3` üretiminden önce** zorunlu olarak
çalıştırır. Wrangler kurulu değilse başarılı rapor vermek yerine durur.

Bu betiğin depoda bulunması çalıştırıldığı anlamına GELMEZ. Yeni Wrangler
paketi henüz bu çevrimdışı çalışma ortamında kurulu olmadığı için gerçek
workerd+D1 testini burada tamamlayamadık. Testin başarılı sonucu oluşunca
rapordaki sayaçlar terminalde `PASS LOCAL Wrangler+D1` olarak görünür.
İzinli gerçek kaynakların IP-pinned taşıma katmanı ve Android cihazdaki
uçtan uca playback testi **ayrı aşamalardır**.

## Rights gate validation status (26 Sep 2026)
The independent Kotlin rights suite and bridge JVM suite are included in `scripts/test-core.sh`; new offline rights regressions and a Node bridge-wiring check have been committed on the v6 feature branch but not rerun here. These checks do not substitute for the outstanding real LOCAL Wrangler+D1/Cron smoke, Android Gradle packaging or Mi Box verification. No Actions, remote D1 or live-source requests were used.

**26.09 hak/izin test durumu:** `scripts/test-core.sh` izin politikasi ve kapali bridge JVM testlerini icerir. Kanit referansi yol normallestirme duzeltmesiyle Kotlin izin dosyasi 57 senaryoya ulasti. Yeni 34 senaryo henuz calistirilmadi; basari sonucu ancak yerel JVM cikti loguyla isaretlenecek. Gercek yerel Wrangler+D1/Cron kapisi ayri ve acik kalir.
