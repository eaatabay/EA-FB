# EA-FB v6 — MASTER CHECKLIST

> Durum: Bu dosyadaki [x], kodun v6 gelistirme dalina kaydedildigi anlamina gelir.
> Canli calisma/derleme/Mi Box dogrulamasi tamamlanmadan production bitti sayilmaz.
> v5/main ve canli ea-fb-catalog Worker bu gelistirmelerin disindadir.

## Katalog ve Mi Box arayuzu
- [x] Ayri v6 dalinda gelistirme; v5/main'i koruma.
- [x] TMDb/IMDb ayri puan veri yapisi ve v6 detay kodu.
- [x] Resmi TMDb film serisi kronolojisi pure FilmCollectionPolicy ile EAProvider'a baglandi; JVM 12/12 gecti.
- [x] Tekli seri, ayri reboot, duplicate ID, eksik/gecersiz tarih ve ayni gun vizyonu icin regresyon testleri eklendi.
- [x] Sari-lacivert ayarlar ve kategori siralama kodu.
- [ ] Buyuk afis yaninda IMDb ve TMDb puanlarini Mi Box'ta onaylama.
- [ ] Ayri film-serisi rafi: stok CloudStream eklenti API'si tek Onerilenler rafi sundugu icin istemci destegi gerekiyor; simdilik seri kartlari yil sirasiyla Onerilenler'in basinda.
- [ ] Mi Box'ta seri kartlarinin gercek TMDb koleksiyon kimligi ve yil sirasini test etme.
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
- [x] Eski test:local artik mevcut D1'e dokunmuyor; tek kullanimlik D1 release testini aciyor.
- [x] Yerel D1 config: 10/10 sahte binding/route/secret/worker degisikligi engellendi (Node 22).
- [x] Yerel Wrangler komut kontrati 4/4 Node test: --remote, farkli DB, env/profil, ek SQL ve sahte sayac reddi.
- [x] 26.09: Yerel D1 sayim sorgularinda --json zorunlu, fixture seed icin --json yasak; 3 yeni negatif regresyon senaryosu GitHub'a kaydedildi (commit 4835d414). Tam Node/Wrangler testi henuz calistirilmadi.
- [x] Smoke test artik yalnizca 2 sabit SELECT, exact seed dosyasi ve --local ile calisabilir.
- [x] 26.09: 0600 yerel config olusturma, ikinci denemede uzerine yazmama dogrulandi.
- [x] 26.09: D1'in desteklemedigi BEGIN/COMMIT fixture SQL'den cikarildi; tekrarini engelleyen test eklendi.
- [x] 26.09: GitHub fixture generator cikti fingerprint'iyle birebir ayni SQL, gercek SQLite migration 0001+0002 uzerinde 30/30 kayit ve 30/30 audit; tekrar 0 duplicate dogrulandi.
- [x] Tam v6 verify betigi D1 testini Kotlin ve cs3 derlemesinden ONCE zorunlu kiliyor.
- [x] Fixture kaydinin durum/candidate URL'leri de sadece onayli demo hostlarda kalabiliyor.
- [x] 8 Cron + 1 tekrar senaryosunda 30 kaynak ve audit beklentisi otomatik denetleniyor.
- [ ] Wrangler --local D1 migration/Cron entegrasyon testini gercekte kosma (paket bu ortamda kurulu degil).
- [x] Yalniz v6 PR veya opt-in manual ile calisan, Cloudflare sirri almayan read-only Actions testi eklendi.
- [x] CI YAML yapisi dogrulandi; 3/3 Node CI guvenlik testi gecti.
- [x] Wrangler JSON kanitinda karisik basarisiz/basarili ifadelerin PASS sayilmasi kapatildi (4/4 Node).
- [ ] Actions kotasi uygun oldugunda sadece yerel D1/Worker test job'unu gercekte calistirip log inceleme.
- [x] 26.09: Gercek repo koduyla V8 30 kaynak/59 kontrol, 2 tasinma, 1 admin bekleme, sifir tekrar dogrulandi.
- [x] 26.09: Kotu durum adresi iceren 5 fixture'in Cron'a girmeden reddedildigi dogrulandi.
- [x] 26.09: Derlemeye sabitlenen ayri izin/kapsam/host/yol/sure kaydi icin ReviewedSourcePermitPolicy eklendi; hicbir gercek kaynak onayli degil.
- [x] Birebir GitHub Kotlin/JVM izin politikasi 23/23 basarili; izin olmadan kaynak yok, host/yol/DRM ve abonelik haklari varsayilmiyor.
- [x] 26.09: Izin politikasi test dosyasi GitHub'da zaten mevcuttu; onayli host/yol segmenti, HTTPS origin, URL manipulasyonu ve film/dizi yetki kesisimi icin 24 yeni cevrimdisi regresyon senaryosu eklendi (d6074f72). Yeni testler henuz JVM'de calistirilmadi.
- [x] 5/5 mevcut bridge JVM testi yeni izin kapisiyla yeniden derlenip gecti (test-only Android/MediaSource stublari).
- [x] 26.09.2026: Izin testi 23 onceki + 24 yeni + 6 ek regresyonla 53 senaryoya genisletildi; LIVE/unknown signed media icin fail-closed hata duzeltildi (ac906b56, 20fbbdd). Testler scripts/test-core.sh icinde; yeni 30 senaryo henuz JVM'de calistirilmadi.
- [x] 26.09.2026: Bridge'in izin kisitlamasini adapter seciminden once uyguladigini ve production permit listesinin bos oldugunu koruyan Node kaynak-baglanti testi eklendi (5681ca14); henuz calistirilmadi.
- [x] 26.09.2026: Kanit referansindaki bos ve nokta yol bilesenlerini reddeden fail-closed duzeltme ve 4 cevrimdisi regresyon eklendi (6bbe2a84, c59b7d87). Kotlin izin testi toplam 57 senaryo; yeni senaryolar henuz JVM'de calistirilmadi.
- [x] 26.09.2026: Izole yerel ortamda, GitHub'dan okunan guncel izin politikasi mantiginin yeniden olusturulmus Kotlin kopyasiyla 38/38 bagimsiz cevrimdisi kontrol gecti. GitHub'in 57 test dosyasinin birebir JVM calismasi DEGIL.
- [x] 26.09.2026: GitHub'dan dogrudan okunan bridge, permit policy ve test dosyalari uzerinde 6/6 kaynak-kod kontrolu gecti: izin kapisi secimden once, reviewed snapshot kullaniliyor, production permit listesi bos, bilinmeyen medya reddi, kanit yolu segment denetimi ve 57 test kaydi.
- [x] 26.09.2026: Hak/izin kapisinda buyuk harfli noncanonical signed URL authority ve tekrarlanan signed kaynak kimligi icin ek fail-closed kontrol ve 2 regresyon eklendi (99b10dc0, a6fcf3e6); test dosyasi 59 senaryo, yeni ikisi henuz calistirilmadi.
- [x] 26.09.2026: URL ve izin kapsamindaki /./ yol segmenti engellendi; normal dosya adindaki a..b gecerliligi korundu. 3 yeni cevrimdisi test eklendi (0ba861c8, 58dc5b78). Kotlin izin dosyasi 62 senaryo; yeni testler calistirilmadi.
- [x] 26.09.2026: Node bridge kaynak-baglanti regresyonu scripts/test-core.sh sonuna eklendi; Node yoksa fail-closed durur (1fae33e1). Gercek checkout uzerinde tam betik henuz calistirilmadi.
- [x] 26.09.2026: MASTER dosyasindaki 3 yanlis literal satir-sonu kacisi duzeltildi (306d6b2b); Kotlin izin politikasindaki derlemeyi bozabilecek literal kacislar onceki 96831b25 commit'inde duzeltildi.
- [x] 26.09.2026: GitHub'daki guncel Kotlin/bridge/test-runner dosyalarinda 9/9 salt-okunur kaynak kontrolu gecti; izole eski Kotlin kopyasindaki 38/38 test yeniden calistirildi. Bunlar 62/62 tam repo JVM testi degildir.
- [x] 26.09.2026: Izole yerel Kotlin test kopyasi son host-authority, nokta-segment ve duplicate snapshot korumalariyla yeniden derlendi; 43/43 bagimsiz offline kontrol gecti. GitHub'daki 62 testin birebir calistirilmasi degildir.
- [x] 26.09.2026: Izole yerel bridge wiring Node testi 2/2 gecti; dosyalarin GitHub SHA eslesmesi teyit edilmediginden tam repo dogrulamasi degildir.
- [x] 26.09.2026: Yerel izole Kotlin kopyasi 43/43 ve Node bridge wiring 2/2 tekrar gecti. Bunlar GitHub'daki tam test dosyalarinin birebir calismasi degildir.
- [x] 26.09.2026: Kanit referansi denetiminin yalnizca yol sozdizimi oldugu; gercek lisans/hak sahibi/playback yetkisinin insan incelemesi ve ayrica release onayi gerektirdigi SOURCE-RIGHTS-REVIEW'de aciklandi (a284a8ac).
- [x] 26.09.2026: Derlemeye gomulu izin listesi bosken bridge, offline cache okumadan erken donuyor (806c721f). Node wiring dosyasina erken-donus ve izin-once-secim sirasi icin iki yeni regresyon eklendi (b22c47fb). Node wiring toplam 4 test; yenileri henuz gercek checkout'ta calistirilmadi.
- [x] 26.09.2026: GitHub'daki guncel bridge ve 4 testlik Node wiring kodunun yerel yeniden olusturulmus kopyasi Node 22 uzerinde 4/4 gecti; birebir SHA eslesmesi veya tam checkout testi degildir.
- [x] 26.09.2026: Bridge'e LIVE ve gecersiz saat icin cache okumadan erken donus eklendi (ec724267). Node wiring dosyasina besinci regresyon eklendi (47dea09f).
- [x] 26.09.2026: GitHub SHA'si birebir eslesen bridge (85b666c5), izin politikasi (4834a92c) ve Node test dosyasi (c7abae59) yerel Node 22'de 5/5 gecti. Ayni birebir Kotlin politika dosyasi, snapshot veri sinifi stub'uyla bagimsiz 43/43 JVM kontrolunu gecti. GitHub'daki 62 test dosyasi ve tam bridge JVM paketi henuz calistirilmadi.
- [x] 26.09.2026: SHA-eslesmeli gercek bridge ve izin politikasi kodu, test bagimlilik stublariyla JVM'de 18/18 runtime fail-closed kontrolunden gecti (MOVIE/SERIES/LIVE x 3 saat x 2 adapter durumu); 0 cache okumasi, 0 delivery-config girisi, 0 secim. Bu, gercek depodaki 5-case bridge entegrasyon testi degildir.
- [x] 26.09.2026: Gercek bridge JVM test dosyasina negatif saat (3 medya tipi) ve bos adapter paketi (3 medya tipi) icin 6 regresyon eklendi (b6f5e36c). Tam bridge JVM testi artik 11 senaryo; bu yeni 6 senaryo gercek repo checkout'unda henuz calistirilmadi.
- [x] 26.09.2026: Derlenmis izin kaydinin evidenceReference degeri tam olarak rights/YYYY/<kaynak-id>.md ile eslestirildi; baska kaynagin belgesi, ic ice alias ve yil olmayan klasor reddediliyor (6bcab6cf). GitHub test dosyasina 4 yeni regresyon eklendi (19789c82); toplam 66 senaryo.
- [x] 26.09.2026: SHA'si GitHub ile birebir ayni yeni Kotlin izin politikasi (bf544b12), izole JVM test bagimliliklariyla 47/47 bagimsiz cevrimdisi kontrolu gecti. Bu, GitHub'daki 66 test dosyasinin birebir calistirilmasi degildir.
- [x] 26.09.2026: Izin gecerlilik suresi pozitif ve en fazla 366 gun olarak acik aralik kontrolune alindi (1bfb39e5); cok uzak gelecek ve tam 366-gun siniri icin iki yeni regresyon eklendi (5e05f958). Kodda negatif reviewedAt zaten reddedildiginden bu esasen sinir netlestirmesidir; yeni testler henuz tam repo JVM'de calistirilmadi.
- [x] 26.09.2026: SHA-eslesmeli son izin politikasi (c186202d) + test-only snapshot modeliyle bagimsiz yerel Kotlin 49/49 gecti; ayni politika ve SHA-eslesmeli bridge (85b666c5) test-only runtime bagimliliklariyla 18/18 gecti. GitHub SHA-eslesmeli Node wiring (c7abae59) 5/5 gecti. Bu 68/68 tam repo izin veya 11/11 gercek bridge testi DEGIL.
- [ ] Genisletilmis 68/68 Kotlin izin testi ve 11/11 bridge JVM testini gercek v6 checkout'unda calistirip dogrula. Node wiring 5/5, onceki SHA-eslesmeli yerel dosyalarda gecti.
- [x] Kullanici acik kaynak-bazli onayi ve hak sahibi delili gerektiren docs/SOURCE-RIGHTS-REVIEW-v6.md olusturuldu.
- [ ] Gercek kaynagin izin kapsami, kanit referansi, rate limit, bolge ve yenileme tarihini tek tek onaylat.
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
- [x] Imzali snapshot ayni revision/daha yeni generatedAt TTL yenileme uyumu ve tam schema validation; izole V8 gercek GitHub kaynak/test koduyla 7/7.
- [ ] Degisiklikleri tam Node22 test paketi ve Android/Cloudflare entegrasyonunda tekrar dogrulama.
- [ ] Gercek public key'in Android eklenti surumune pinlenmesi.
- [x] 26.09: Imzali JSON ve revision/generatedAt tek atomic commit ile cihazda saklama; bozulan/expired cache offline fail-closed.
- [x] 26.09: GitHub ile birebir ayni JVM: 14/14 offline cache, 17/17 Android store (Context/JSON stubs), 23/23 signed HTTPS refresh ve 9/9 sahte HTTPS tasiyici testi basarili.
- [x] Dort offline/HTTPS test paketi scripts/test-core.sh icinde; birebir GitHub betigi bash -n gecti.
- [ ] Gercek Android SharedPreferences, uygulama kapanip acilma ve Mi Box offline TTL testi.
- [x] 26.09: Istege bagli HTTPS snapshot refresh motoru kodlandi: tam izinli origin/path, 32 KiB, JSON/UTF-8, imza, 15–60 dk backoff ve offline TTL.
- [x] Kotlin 1.9/BC 1.80/coroutines ile birebir GitHub kaynak kodu: 23/23 injected-transport JVM vakasi basarili; Actions kullanilmadi.
- [x] Android'e uygun TLS GET tasiyicisi eklendi: 4 sn baglanti/okuma siniri, redirect KAPALI, en fazla 32 KiB, kimlik bilgisi YOK.
- [x] Uygulama varsayilaninda endpoint, origin, anahtar ve adapter bos; otomatik ag baslatma YOK.
- [ ] Gercek Android'de transport/network-permission, surec yeniden baslatma ve signed TTL davranisini test etme.
- [x] WatchdogClientStore icine yalniz endpoint+key+adapter onayliyken istemci ureten ve otomatik ag acmayan factory eklendi.
- [x] 26.09: Imzali medyaya gore movie/series/both adapter secimi eklendi; LIVE ve yanlis surumler devre disi.
- [x] 26.09: 15/15 pure Kotlin secim testi + 5/5 kapali Android bridge JVM testi; GitHub blob SHA ile birebir.
- [x] WatchdogApprovedAdapterBridge: onayli HTTPS origin, pinned public key ve .cs3'e derlenmis TAM adapter surum eslesmesi olmadan kaynak acmiyor.
- [x] WatchdogApprovedAdapterBridge simdi ayri, sureli hak/izin/host/yol kaydi olmadan da hicbir kaynak adaptoru acmiyor (production listesi BOS).
- [x] Her iki yeni test scripts/test-core.sh'e eklendi; birebir betik bash -n gecti.
- [ ] EAProvider'a baglama yalniz gercek izinli kaynak adaptoru ve signing public key onayindan SONRA.
- [ ] Guvenli endpoint ve gercek imza pini onaylandiktan sonra ilk onayli adapter ile uygulama akisina baglama.
- [ ] Otomatik/manual yenilemeyi uygulama yasam dongusune guvenle baglama; canli yayin ve v5 etkilenmeyecek.
- [ ] Guvenli client yenileme agi, API endpoint baglantisi ve offline TTL (gercek servis ve cihaz testi).
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
Actions kotasi sifirken hak/izin kayitlarini birer birer incele ve kullanicidan onay al;
sonra EAProvider'a secure adapter bridge, tam Node 22 ve izole Wrangler D1,
MFA'li admin pilotu. Gercek v5/main ve canli Worker degismez.
