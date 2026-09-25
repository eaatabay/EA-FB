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
- [x] Tek komutlu Node/Python/yerel Wrangler+D1 release kapisi kodlandi.
- [x] 26.09: D1'in desteklemedigi BEGIN/COMMIT fixture SQL'den cikarildi; tekrarini engelleyen test eklendi.
- [x] 26.09: GitHub fixture generator cikti fingerprint'iyle birebir ayni SQL, gercek SQLite migration 0001+0002 uzerinde 30/30 kayit ve 30/30 audit; tekrar 0 duplicate dogrulandi.
- [x] Tam v6 verify betigi D1 testini Kotlin ve cs3 derlemesinden ONCE zorunlu kiliyor.
- [x] Fixture kaydinin durum/candidate URL'leri de sadece onayli demo hostlarda kalabiliyor.
- [x] 8 Cron + 1 tekrar senaryosunda 30 kaynak ve audit beklentisi otomatik denetleniyor.
- [ ] Wrangler --local D1 migration/Cron entegrasyon testini gercekte kosma (paket bu ortamda kurulu degil).
- [x] 26.09: Gercek repo koduyla V8 30 kaynak/59 kontrol, 2 tasinma, 1 admin bekleme, sifir tekrar dogrulandi.
- [x] 26.09: Kotu durum adresi iceren 5 fixture'in Cron'a girmeden reddedildigi dogrulandi.
- [ ] Ayrica onaylanmis, izinli gercek kaynak adapterlerini secip inceleme.
- [x] DNS ve her redirect hop'u icin KAPALI/cevirmdisi preflight kodu, Node 5/5.
- [x] GitHub ile birebir ayni migration dosyalari: SQLite smoke 4/4.
- [ ] Gercek HTTP baglantisinda IP-pinning, IPv6 ve DNS-rebinding guvenligi.
- [ ] Gercek kaynak probe istek kotasi / rate limit / network testleri.
- [ ] Gercek ag islevsel probe'lari (search/detail/episode/izinli playback).

## Imzali kaynak listesi ve Android
- [x] Ed25519 imzalama ve surum/zaman kontrolleri sunucu tarafi.
- [x] Imzali salt-okunur /v1/sources endpoint'i (varsayilan OFF).
- [x] Kotlin/BouncyCastle dogrulama, JSON ayrimci, replay saklama ve adapter gate kodu.
- [x] 25.09: Gercek GitHub Kotlin kaynaklariyla JVM imza testleri 14/14 ve adapter gate 6/6 basarili.
- [x] Bozuk ve yinelenen test-core.sh blogu duzeltildi; GitHub dosyasi bash -n testinden gecti.
- [x] Test-core shell syntax ve iki testin yalniz bir kez calismasi icin kalici Node regresyon testi eklendi.
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
- [x] Salt kod: admin disable/enable/retest/rollback endpointleri Access+origin+CAS ile hazirlandi.
- [x] Admin yazma bayragi ayri ve varsayilan OFF; yeni domain/onay endpoint'i YOK.
- [x] V8 test doubles: admin policy 12/12, Worker route 10/10; tam Node degil.
- [ ] Admin yazma Node/SQLite test dosyasini gercek checkout'ta tam calistirma.
- [ ] Audit'li admin izin/onay is akisi + panelde duzenleme butonlari (HENUZ YOK).
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
