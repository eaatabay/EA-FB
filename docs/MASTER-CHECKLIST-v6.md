# EA-FB v6 — MASTER CHECKLIST

> Durum: Bu dosyadaki [x], kodun v6 gelistirme dalina kaydedildigi anlamina gelir.
> Canli calisma/derleme/Mi Box dogrulamasi tamamlanmadan production bitti sayilmaz.
> v5/main ve canli ea-fb-catalog Worker bu gelistirmelerin disindadir.

## Katalog ve Mi Box arayuzu
- [x] Ayri v6 dalinda gelistirme; v5/main'i koruma.
- [x] TMDb/IMDb ayri puan veri yapisi ve v6 detay kodu.
- [x] Film koleksiyonu kronolojik listeleme icin temel kod.
- [x] Sari-lacivert ayarlar ve kategori siralama kodu.
- [ ] Buyuk afis yaninda IMDb ve TMDb puanlarini Mi Box'ta onaylama.
- [ ] Film serisini Onerilenler'den ayri raf olarak cihazda dogrulama.
- [ ] Kategori/ayar kaliciligi, D-pad ve eski Android cihaz testi.

## Kaynak Bekcisi: sunucu cekirdegi
- [x] Bagimsiz durum motoru (healthy/degraded/quarantined/admin_required).
- [x] Onayli domain, iki basarili kontrol ve otomatik geri katilma kurallari.
- [x] Ayri D1 kayit/sema/audit ve replay/CAS korumalari.
- [x] 15 dakika Cron uyandirici ve kaynak basina 6 saatlik genel kadans.
- [x] Ayni kaynagi iki kez taramayi engelleyen sureli D1 kilidi.
- [x] 30 kurgusal kaynak: 27 normal + 2 adres degisimi + 1 admin senaryosu.
- [x] Yanlislikla canliya cikmayan ayri Worker ve tum bayraklar OFF.
- [x] Yalniz yerel D1 konfigurasyon/seed/test komutlari (remote kaynak yok).
- [ ] Tum Node 22/SQLite test paketini gercek v6 checkout'unda kosma.
- [ ] Wrangler --local D1 migration/Cron entegrasyon testini kosma.
- [ ] Ayrica onaylanmis, izinli gercek kaynak adapterlerini secip inceleme.
- [ ] DNS ve her redirect hop'unda SSRF ve kota sinirlarini dogrulama.
- [ ] Gercek ag islevsel probe'lari (search/detail/episode/izinli playback).

## Imzali kaynak listesi ve Android
- [x] Ed25519 imzalama ve surum/zaman kontrolleri sunucu tarafi.
- [x] Imzali salt-okunur /v1/sources endpoint'i (varsayilan OFF).
- [x] Kotlin/BouncyCastle dogrulama, JSON ayrimci, replay saklama ve adapter gate kodu.
- [x] Paylasilan PUBLIC kriptografik test vektoru.
- [ ] Gercek public key'in Android eklenti surumune pinlenmesi.
- [ ] Guvenli client yenileme agi, API endpoint baglantisi ve offline TTL.
- [ ] Kotlin/Gradle/Dex/.cs3 ve Mi Box uctan uca testi.

## Admin paneli ve yetkilendirme
- [x] Cloudflare Access JWT RS256/imza, issuer, AUD, email allowlist dogrulama.
- [x] XSS-escaped, salt-okunur sari-lacivert admin saglik paneli.
- [x] GET /admin default OFF, yetkisiz 403 ve CSP/iframe korumalari.
- [x] Sahte JWT, yetki, XSS ve kapali varsayilan icin Node test dosyalari.
- [x] Exact GitHub Access JWT dosyasi ve Node 22 testleri: 5/5 gecti.
- [ ] Tum Node/Workers/D1 test paketini gercek v6 checkout'unda kosma.
- [ ] Kullanici tarafinda Cloudflare Access + MFA uygulamasi olusturma.
- [ ] Basarili yerel test sonrasinda admin kimligi ve AUD onayi.
- [ ] Audit'li admin yazma/onay/rollback akisi (su an KAPALI/yok).
- [ ] Telegram kritik alarm ve gunluk rapor (onayli hedef gerekli).

## Yayina cikmadan once
- [ ] Ayrica test Cloudflare Worker ve ayri D1 uzerinde canli-olmayan pilot.
- [ ] Gerekli entegrasyon/izinler ve kaynak adaptorlerini kullaniciya teyit ettirme.
- [ ] Tam CI + Kotlin + Worker + D1 + cihaz raporu.
- [ ] Mi Box kirmizi Beta onayi; sonra v6 yayin karari.
- [ ] Production v5 veya main'e gecis yalniz ayri onayla.

## Sonraki en yakin is
Node 22 testleri + yerel Wrangler D1 smoke; ardindan ilgili kaynaklar icin
hak/izin ve endpoint bilgileriyle ilk gercek adaptor, daha sonra MFA'li admin pilotu.
