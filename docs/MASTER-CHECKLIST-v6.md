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
- [x] 26.09.2026: 68 senaryolu Kotlin izin dosyasinin yerelde yeniden yazilmis birebir davranisli karsiligi, GitHub SHA-eslesmeli guncel izin politikasi ve test-only snapshot tipiyle 68/68 gecti. Ilk denemede yeni belge-kimligi kapisinin eski distinct-id fixture'ini gecersiz kildigi goruldu; fixture'in kendi evidenceReference'i duzeltildi (cfea4dc4). GitHub test dosyasinin birebir bayt kopyasi ve tam SourceSnapshotTrust bagimliligiyle kosulmus test olarak sayilmaz.
- [x] 26.09.2026: SHA-eslesmeli bridge kaynak kodu ve 11 senaryonun yerel transkripsiyonu, test-only Android/adapter bagimliliklariyla JVM'de 11/11 gecti; tam Android bagimliliklariyla gercek repo suite henuz acik.
- [x] 26.09.2026: Yerelde 24/24 Node (bridge wiring 5, policy 12, fixture seed 2, network preflight 5), 7/7 SQLite migration senaryosu gecti. Policy, fixture, network ve Python testleri GitHub kaynaklarindan yerel yeniden olusturuldu; tam repo npm test DEGIL.
- [x] 26.09.2026: Network preflight yalniz kanonik HTTPS authority, query/fragment olmayan URL, gecerli DNS label ve her hop yeniden DNS kontrolunu kabul ediyor; yeni negatif testler eklendi (8bdb3b82, 27f15e6d, 15d31fd4, f90e8d97, eadb295b, 5fec564c). IP-pinned gercek tasima henuz YOK.
- [x] 26.09.2026: Yereldeki secilmis Node paketi 32/32'ye ulasti (lease 4 eklendi); Python SQLite migration paketi 7/7. Kaynaklari GitHub'dan yeniden olusturulmus yerel kopyalardir; SHA-eslesmeli tam depo npm test sonucu DEGIL.
- [x] 26.09.2026: Yeniden olusturulmus policy+scheduler, fixture SQL ve gercek bellek ici SQLite migration tetikleyicileriyle 30 kurgusal kaynak 8x15dk cevrimdisi simule edildi: 59 probe, 89 audit, rev89, 29 healthy, 1 admin hold, 2 domain move, 0 lease ve ayni tick tekrarinda 0 yeni yazim. Gercek Worker/workerd/Wrangler D1 testi DEGIL.
- [x] 26.09.2026: Scheduler unknown status, kesirli/negatif/sonsuz due saati ve bozuk incident son-kontrol zamanini fail-closed reddediyor; yeni regresyon eklendi (c8248a2c, f13720cb). Yerel 4/4 scheduler gecti.
- [x] 26.09.2026: Ayni yerel 30-kaynak SQLite simule akisi admin inceleme/retest ve iki ayri basarili probe ile genisletildi: 29->30 healthy, 59->61 run, 89->92 audit/revision, 0 lease; ilk duzeltme probe'unda erken healthy ilan edilmiyor. Yerel yeniden olusturulmus kod, gercek Worker/workerd testi DEGIL.
- [x] 26.09.2026: Ag preflight'inda kullanilmayan query/fragment ayiraclari, buyuk harfli/encoded host ve acik :443 alias'i engellendi; DNS label boyutu ve bas/son tire kontrolleri eklendi; 192.88.99/24 6to4 anycast de ihtiyatli engel listesine alindi (15d31fd4, eadb295b, 6c107f19). Yerel network test 5/5 gecti; gercek IP-pinned HTTP transport acik is.
- [x] 26.09.2026: SourceSnapshotGate + WatchdogAdapterSelection ve 15 test senaryosu yerel test-only domain bagimliliklariyla JVM'de 15/15 yeniden calistirildi. Bu SHA-eslesmeli tam dosya kopyasi ve Android entegrasyon sonucu DEGIL.
- [x] 26.09.2026: Network preflight'a 18 URL authority varyantini DNS'e dokunmadan reddeden altinci regresyon eklendi (87c44283); yerel adversarial 23 URL + 21 IP + 2 gecerli URL kontrolu gecti. Yerel secilmis Node paketi 33/33 gecti; tum repo Node paketi hala acik.
- [x] 26.09.2026: Staging artik sadece internalName EA-FB ve tam integer version=6 kabul ediyor; v4/v5/v7/null/float/string/bool alias reddediliyor (09a88f2f, ab6008df). Yeni testle 6/6 yerel yeniden olusturulmus Python staging testi gecti. Gercek Gradle/.cs3 paketi henuz derlenmedi.
- [x] 26.09.2026: Network preflight 18 URL parser alias'i icin ek fail-closed regresyonla 6 teste ulasti (87c44283). Yerel secilmis Node suite 33/33; ayrica 23 kotu URL, 21 kotu IP ve 2 gecerli URL ile adversarial kontrol gecti.
- [x] 26.09.2026: Scheduler'da bozuk JSON status objesi diger saglam kaynaklarin planlamasini bozamiyor; status tipi hem scheduled hem incident yolunda acikca denetleniyor (d8124bd4, f1558b5f). Yerel 4/4 scheduler ve secilmis 33/33 Node suite yeniden gecti.
- [x] 26.09.2026 ikinci cevrim: Gercek GitHub blob SHA'siyle birebir ayni stage-release.py (d7760f20) + test_stage_release.py (ba06fc70) yerelde 9/9 calisti. ZIP icinde duplicate member, path traversal ve symlinkli .cs3 artik staging'de reddediliyor (bdfc540b, cefc7f3b). Bu sahte fixture ZIP'leriyle staging testidir, Android derlemesi degildir.
- [x] 26.09.2026 ikinci cevrim: GitHub blob SHA'siyle birebir ayni network-boundary kaynak (cc367122) ve 6 test (282876b9) yerelde 6/6 gecti. Scheduler kodu (3c6e37ad) ve 5 test (f206af92) de birebir SHA ile 5/5 gecti. Planlayici string 'false' enabled bayragini ve bozuk ID'leri fail-closed reddediyor (5baa1dcd, 6e29cc90). Secilmis karisik Node paketi 34/34 gecti; kalan dosyalarin bir bolumu yeniden olusturulmus yerel kopyalardir.
- [x] 26.09.2026 ikinci cevrim: Gercek GitHub blob SHA'si birebir ayni SourceSnapshotTrust.kt (d183231c), ReviewedSourcePermitPolicy.kt (c186202d) ve ReviewedSourcePermitPolicyTest.kt (052336ce), gercek BouncyCastle 1.80 ile dogrudan kotlinc/JVM'de 68/68 GECTI; artik yeniden olusturulmus test degil, bu uc dosya birebir gercek repo kodudur.
- [x] 26.09.2026 ikinci cevrim: Birebir gercek SourceSnapshotTrustTest.kt (98c4c972) 14/14; SourceSnapshotGate.kt (23aa7da6) + gercek testi (b9d1d386) 6/6; WatchdogAdapterSelection.kt (7ce3a89b) + gercek testi (ea421e2a) 15/15, yalniz domain/API tipi icin test-only JVM stub kullanildi. Dinamik JS WebCrypto Ed25519 imzasi birebir Kotlin verifier tarafindan 5/5 kabul/replay/tamper/expiry/version testinde dogrulandi.
- [x] 26.09.2026 ikinci cevrim: Birebir gercek bridge test dosyasi (e0a02815) + birebir bridge kodu (85b666c5) ve izin kodu ile 11/11 JVM gecti; Android/store/adapter bagimliliklari test-only stub. Tum gercek repo Kotlin bagimliliklariyla tek checkout derlemesi halen acik.
- [x] 26.09.2026 ikinci cevrim: Birebir gercek snapshot-crypto.mjs (d8b91960) + gercek 7 test (9433ccf1) 7/7; gercek network preflight 6/6, scheduler 5/5. Karisik secilmis yerel Node paketi 41/41; kalan policy/fixture/lease/bridge testleri hala yeniden olusturulmus yerel kopyalardir.
- [x] 26.09.2026 ikinci cevrim: Birebir gercek stage-release.py (25c8b449) + gercek 11 test (eea2c68d) 11/11. ZIP duplicate/path traversal/symlink ve gecersiz veya baska eklentiye ait manifest reddediliyor (bdfc540b, cefc7f3b, e41fce4d, 822789af). Sahte fixture .cs3, gercek Android build degil.
- [x] 26.09.2026 ikinci cevrim: JS/Kotlin imzali snapshot dogrulamasinda DNS label bas/son tire, 63 karakter ustu label, percent-encoded path ve cift slash path erken reddediliyor. JS kaynak 5d2731fb + 7 gercek test 4b416ce6 7/7; Kotlin kaynak d20dcc8d + 19 gercek test 4031a3f6 19/19. Yeni sinir testleri commitleri 6cbd9bce, 6e58d517, ed1e4d44, ba916f0f, 41ea5405, 4b5054fe, deb77d1c, ae9e3776. Ayrica her calistirmada gecici Node WebCrypto anahtariyla imzalanan fixture SHA-eslesmeli Kotlin BouncyCastle ile 5/5 dogrulandi (yalniz yerel test, repo dosyasi degil).
- [x] 26.09.2026 ikinci cevrim final: Birebir gercek GitHub blob SHA'lariyla Kotlin rights 68/68, trust 19/19, gate 6/6, selection 15/15, bridge 11/11 (son ucunde test-only domain/Android stub); gercek snapshot-crypto Node 7/7, network 6/6, scheduler 5/5, gercek staging Python 11/11. Karisik secilmis Node paketi 41/41, yeniden olusturulmus SQLite migration Python 7/7 ve 30-kaynak SQLite simulasyonu/recovery tekrar gecti. Tam checkout/gercek Wrangler/Gradle/cihaz testleri yapilmadi.
- [x] 26.09.2026 ikinci cevrim: Wrangler release-defaults testi eklendi (c3309874): tracked config workers_dev=false, mode=disabled, bes ayri aktiflestirme flag'i false, remote D1 binding/route yok. Gercek config ile yerel test gecti; secilmis Node paketi 42/42. Canli deploy veya gercek Wrangler testi degil.
- [x] 26.09.2026 ucuncu cevrim: Gercek SHA-eslesmeli stage-release.py (5c4ef40b; son ekleme: bozuk ZIP hatasi temiz CLI raporu) + test_stage_release.py (81c83c94) 17/17 yerelde GECTI. .cs3 staging 128 MiB toplam/64 MiB tek uye/512 uye siniri, sifreli ZIP, duplicate, dosya ve klasor traversal, arsiv ici symlink, dis dist ve cikti symlinklerini reddediyor. Bunlar fixture ZIP; gercek Android .cs3 degil.
- [x] 26.09.2026 ucuncu cevrim: Access JWT 24 saat azami imza suresi ve yas siniri; team domain ve admin write origin canonical DNS label kurali; yerelde yeniden olusturulmus Access JWT kod/testi 6/6 gecti. GitHub exact tam admin test paketi henuz kosulmadi.
- [x] 26.09.2026 ucuncu cevrim: Watchdog registry URL canonicalizasyonu (bare ?/#, encoded/double/dot path, default port, DNS label), tekrarlanan/uppercase allowlist reddi; yerel yeniden olusturulmus policy suite 12/12 + 11 ek URL sinir vakasi gecti. Hak onay referanslari URL/traversal/alias olamaz; izole 12/12 sinir vakasi gecti, tam registry+D1 suite henuz kosulmadi.
- [x] 26.09.2026 ucuncu cevrim: Metadata Worker TMDb (2 MB) ve OMDb (50 KB) cevaplarini STREAMING byte siniri ile okuyor; iptal hatasi limit reddini maskeleyemiyor. Izole birebir yardimci fonksiyon 7/7; gercek Worker tum testleri checkout'ta bekliyor. 4 yeni Worker testi GitHub'a eklendi.
- [x] 26.09.2026 ucuncu cevrim: Secilmis yerel Node testleri 48/48, birebir Kotlin rights 68/68, trust 19/19, gate 6/6, selection 15/15, bridge 11/11, JS->Kotlin imza 5/5, yerel staging 17/17, yeniden olusturulmus SQLite migration 7/7, 30-kaynak offline sim/recovery gecti. Node policy/admin ve bazi fixture modulleri yeniden olusturulmus kopyadir; tum GitHub checkout npm test yapilmadi.
- [x] 26.09.2026 ucuncu cevrim son duzeltme: Registry policy bozuk host objesini istisna tasirmadan reddediyor; initial/apply/retest saatleri safe integer ve negatif olmayan deger gerektiriyor. Yeni GitHub testi eklendi (713a0ad8, 2e884cf3); yerel reconstructed policy 12/12 + 13 ek negatif/sinir vakasi gecti. Tam exact Node checkout bekliyor.
- [ ] Tam checkout scripts/test-core.sh, tum npm test, Python tum suite, yerel Wrangler/workerd D1, gercek Gradle .cs3 ve Mi Box dogrulamasini calistir; Actions kotasi acilana kadar Actions'i kullanma.
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

## 26.09.2026 — 4. offline parkur (Actions kotasi kapaliyken)
- [x] Watchdog yayin snapshot'inda clock ve TTL safe-integer/overflow siniri; kesirli/eski saglik saatleri reddi; adapterVersion ust siniri 1.000.000.
- [x] Saglik state'indeki URL, onayli registry config URL'siyle birebir eslesmeden imzalanacak snapshot'a giremez. Domain allowlist tek basina yeterli degil; yarim CAS/durum uyusmazligi fail-closed.
- [x] Admin manuel yeniden test, son probe saatinden eski zamanla baslatilamaz; yeni regression testi eklendi.
- [x] Network preflight URL dot segment, encoded path, cift slash ve tek backslash aliaslarini DNS sorgusundan once reddeder; yeni regression testi eklendi.
- [x] Mevcut secilmis yerel test tekrar calistirildi: Kotlin rights 68/68 (reconstructed repository-equivalent), bridge 11/11, selection 15/15, Node 48/48, staging Python 17/17, SQLite fixture 59 probe/89 audit/rev89; admin recovery 61 probe/92 audit/rev92. Yerel policy+network 18/18; yeni 12/12 sinir kontrolleri ve yeniden olusturulmus snapshot 20/20 gecti.
- [ ] 4. parkurdaki yeni snapshot/policy/network regresyonlarini tam gercek checkout Node 22 test paketinde kosma; mevcut yerel snapshot ve bazi Node modulleri yeniden olusturulmus kopya.

## 26.09.2026 — 1. 30 dakikalik parkur (tam SHA eslesmeli secilmis testler)
- [x] Kod incelemesinde iki GERCEK hata bulundu ve duzeltildi: Watchdog policy DNS regex'indeki cift escape tum normal alan adlarini reddediyordu; metadata Worker Content-Length regex'indeki cift escape boyut on kontrolunu devre disi birakiyordu. Her ikisi icin regresyon testi var.
- [x] Cloudflare Access JWKS gercek streaming byte siniri 65.536, yalan Content-Length/UTF-8/iptal hatasi fail-closed; Access JWT gercek Node 8/8.
- [x] Imzali kaynak yayini sadece rights/YYYY/<kaynak-id>.md kimligine bagli belgeyi kabul eder; baska kaynagin izin belgesi allowlist icinde olsa bile odunc alinamaz.
- [x] Imzali kaynak yayini yeni pure rights guard ile her kaynak icin exact registry kimligi, adapterVersion, mediaKind, URL, enabled/integrationApproved ve hak kaniti dogrular. DEPLOYED DEFAULT rights allowlist BOS. Sahte izin kayitlari varsayilan signer'da 503; yalniz offline testlerde fixture izin listesi enjekte ediliyor. Guard gercek Node 4/4; tam signed endpoint+D1 suite bekliyor.
- [x] Metadata Worker TMDb/OMDb byte limit yardimcisi ayri modulle gercek Node 5/5, tam metadata Worker katalog/IMDb/cache testleri gercek Node 20/20; toplam metadata 25/25.
- [x] Watchdog bozuk persisted state/NaN/clock ve URL uyusmazligini reddeder; gercek SHA-eslesmeli policy 16/16, network preflight 7/7, scheduler 5/5, snapshot 10/10, snapshot crypto 7/7, Access 8/8, publication guard 4/4: SECILMIS Watchdog Node 57/57.
- [x] .cs3 staging Gradle build klasoru ve plugins.json symlinklerini de reddeder; gercek SHA-eslesmeli Python staging 19/19. Test fixture .cs3, gercek Android build DEGIL.
- [x] Gercek SHA-eslesmeli Kotlin rights 68/68, Kotlin JS->Android Ed25519 trust 19/19, resmi TMDb film serisi siralama 12/12; toplam Kotlin 99/99, gercek BC 1.80 ile JVM'de.
- [x] Secilmis gercek GitHub blob SHA eslesmeli testler: Node 82 + Python 19 + Kotlin 99 = TOPLAM 200/200. Bu TUM REPO suite veya gercek Wrangler/Gradle degildir.
- [ ] Tum source-watchdog npm test ve gercek registry/runner/admin-write SQLite suite; yerel Wrangler/workerd D1; gercek Android Gradle .cs3; Mi Box beta. GitHub Actions kotasi bitik: workflow tetikleme YOK.

## 26.09.2026 — 2. 30 dakikalik parkur (offline; Actions tetiklenmedi)
- [x] Gercek Watchdog hatasi giderildi: adaptorun exception/timeout/null cevabi eskiden runner_error ile audit DISINDA kaliyor, kaynak surekli tekrar deneniyordu. Artik lease/CAS uzerinden ayri adapter_error veya probe_timeout nedeni kaydedilir; iki basarisizlik sonrasi karantina ve geri cekilme. Zaman asimi sonucu gec gelen sahte healthy cevabi reddedilir.
- [x] Adaptorun probe_timeout hatasi veya runnerFailure alaniyla ic hata sebebini taklit etmesi engellendi. D1 CAS/lease hatalari ayri runner_error olarak kalir; sahte kaynak kesintisi yazilmaz.
- [x] Imzali yayin icin iki ayri release-pinned hak kapisi: kaynak-kimligine bagli belge referansi VE tarihli grant (tam HTTPS host, yol oneki, mediaKind, adapterVersion, reviewedAt, validUntil). Snapshot TTL izin bitisinden ileri gidemez. Yinelenen izinler, 32'den fazla grant, fazladan secret alanlari reddedilir. Her iki production listesi BOS.
- [x] Bos production izinleri ile imza anahtari yukleme/D1 okuma dahi baslatilmaz; bos snapshot bile imzalanmaz. Fixture'lar sadece offline test injection ile gecer.
- [x] Persisted Watchdog state'de bozuk basari/basarisizlik sayaclari, son iyi URL, nextCheckAt ve overflow saatleri fail-closed.
- [x] Gercek GitHub blob SHA eslesmeli Node policy 18/18 ve publication guard 9/9; exact runner ve Worker kaynak koduyla izole/mock testler 10+10, ek policy 14, grant 21. Node TOPLAM 82/82; gercek GitHub 0001/0002 SQL migration'lariyla yerel SQLite 8/8; toplam bu parkurda 90/90 secilmis test. Onceki 200 testin uzerine eklenen AYRI parkur, hepsi ayni full-suite degil.
- [ ] Yeni runner.test ve signed-endpoint.test dosyalarinin tamamini GERCEK SQLite registry ve tum Worker bagimliliklariyla tam checkout'ta calistir; bu parkurda exact Node policy/guard calisti, runner/Worker sadece izole testlerle dogrulandi.
- [ ] Gercek Wrangler/workerd+D1, Android Gradle .cs3, Mi Box Beta; Cloudflare Actions kotasi nedeniyle workflow tetikleme YOK.

## 26.09.2026 — 3. 30 dakikalik parkur (offline; Actions tetiklenmedi)
- [x] Watchdog parser sozlesmesi `adapter-contract.mjs` olarak ayrildi. Eksik search/detail/episode, string "true", bilinmeyen alan, getter/prototype veya adaptorun sahte runnerFailure alani dogrudan AUDIT'li `admin_required` durumuna gecer. Yalnizca gercek adapter crash/null/timeout `adapter_error` veya `probe_timeout` olarak geri cekilir. D1 CAS/lease hata turleri ayridir.
- [x] TOCTOU korumasi: adaptorun mutable nesnesi D1 commit'e verilmez; sadece izinli OWN primitive veri alanlari ve checks taze bir nesneye kopyalanir. Getter, sembol ve beklenmeyen gizli alan reddedilir. Kaynak URL veya hata metni loglanmaz.
- [x] Production fixture bayragi `WATCHDOG_FIXTURE_ENABLED` hem snapshot hem admin Access hem admin writes icin acikca `"false"` olmali. Eksik, `"TRUE"` veya `"0"` fail-closed. Admin POST URL'sinde bos `?`/`#`, credential, path alias ve canonical olmayan URL reddedilir.
- [x] IMDb/TMDb: OMDb gecici ariza verdiginde TMDb-only cevabi saatlerce dual-rating cache'inde tutmak yerine 60 sn TTL. Gecerli OMDb N/A normal TTL. Ayrica OMDb ag + body icin 3.5 sn hard deadline; TMDb detay sayfasi opsiyonel ikinci puan servisinin takilmasini beklemez.
- [x] TMDb film koleksiyonu vizyon sirasi icin sadece YYYY-MM-DD regex degil, gercek takvim gunu kontrolu eklendi; 2026-02-31 ve 2025-02-29 sona atilir, 2024-02-29 korunur.
- [x] OMDb rating parser ayri pure module: yalniz gercek eslesen IMDb ID, 1.0-10.0 arasi kanonik ondalik; N/A ile gecici ariza ayrilir. API key istemciye verilmez.
- [x] Yerel SHA-eslesmeli Node 22: exact adapter-contract source/test 6/6, exact runner source + yalniz test-only fake registry/lease/scheduler ile 8/8, exact OMDb policy source/test 3/3 = 17/17. Exact Kotlin FilmCollectionPolicy + test 15/15 (gecersiz Subat ve artik yil tarihleri dahil). Toplam bu parkurda 32/32 secilmis offline test. Bunlar tam repo Node suite/gercek D1/Worker/Gradle degildir.
- [ ] Bu parkurda degisen gercek `runner.test.mjs`, `admin-actions.test.mjs`, `signed-endpoint.test.mjs` tum bagimliliklarla full checkout'ta calistirilmali. Wrangler/workerd+D1, Gradle .cs3, Mi Box ve gercek kaynak haklari release kapisi kapali.
- [ ] Iki puanin detay sayfasinda CloudStream stok tema nedeniyle native hero puani yerine kaynak etiketli tags oldugu Mi Box uzerinde kontrol edilmeli; franchise kartlari resmi TMDb collection verisiyle vizyon sirasinda, ayrik native seri seridi degil Onerilenler basinda.

## 26.09.2026 — 4. 30 dakikalik parkur (offline; tam Worker Node suite)
- [x] GitHub blob SHA birebir eslesen gercek `worker/src/index.js`, `bounded-response.mjs`, `ratings-enrichment.mjs` ve 3 test dosyasi yerel Node22'de yeniden kuruldu. **Worker tam suite 35/35 PASS**; OMDb 3.5 sn deadline, 60 sn transient TTL, byte budget, cache, TMDb kimlik/JSON, canonical URL testleri dahil. Bu kez izole helper degil gercek Worker entrypoint calisti.
- [x] 2MB TMDb / 50KB OMDb body limitinde `reader.cancel()` asla beklenmez; asla cozulmeyen cancel() bile 502 byte-limit cevabini geciktiremez. Ayrica 1KB admin POST icin 413 ve 64KB Cloudflare Access JWKS icin boyut hatasi ayni sekilde fail-closed. Gercek SHA eslesmeli bounded-response 6/6, admin-auth 9/9, adapter-contract 6/6 gecti.
- [x] Admin URL regresyon testinde WHATWG Request'in `/../` yolunu daha parse asamasinda normalize ettigi goruldu; imkansiz beklenti duzeltildi. Gercek SHA eslesmeli admin-actions kaynagi + acikca test-only registry stub ile 9/9 izole HTTP sinir testi gecti. Tam admin-actions/Worker+D1 testleri bekliyor.
- [x] TMDb null/array/primitive JSON 502; beklenmedik TMDb film ID 502; upstream'den gelen `ea_fb_ratings` adli sahte IMDb alani siliniyor, yalniz dogrulanmis OMDb puani ekleniyor. Tekrarlanan/trailing slash, bos ?/# ve fragmentli katalog alias'lari upstream'e gidemiyor.
- [x] Mi Box bos kartlari icin TMDb poster yoksa backdrop kullaniliyor; ikisi de yoksa bos kart uretilmiyor. Resmi koleksiyon verisinde secili filme ait poster eksikse ayni filmin detay posterine dusuluyor; baska filmden afis odunc alinmiyor. Gercek SHA eslesmeli Kotlin CatalogCardPolicy 25/25; FilmCollectionPolicy 15/15.
- [x] Gercek SHA eslesmeli `0001_registry.sql`, `0002_source_leases.sql` ve Python migration suite **7/7**: CAS, trigger audit, duplicate run rollback, stale CAS, lease takeover ve idempotency.
- [x] Bu parkurun yerel secilmis test toplamı: **35 Worker + 9 Access JWT + 6 adapter + 9 izole admin + 7 SQLite + 25 katalog Kotlin + 15 seri Kotlin = 106/106**. Admin 9/9 ve Kotlin testleri Android derlemesi veya gercek Wrangler D1 anlamina gelmez.
- [ ] Gercek `source-watchdog/test/runner.test.mjs`, `admin-actions.test.mjs`, `signed-endpoint.test.mjs` ve tam Node suite'i tum gercek modullerle tek checkout'ta calistir.
- [ ] Android Gradle .cs3, Mi Box afis/puan/seri gorsel testi, Wrangler/workerd+D1 ve gercek hak/anahtar izinleri release kapisinda bekliyor. Main/v5/canli Worker'a dokunulmadi.

## Sonraki en yakin is
Actions kotasi sifirken hak/izin kayitlarini birer birer incele ve kullanicidan onay al;
sonra EAProvider'a secure adapter bridge, tam Node 22 ve izole Wrangler D1,
MFA'li admin pilotu. Gercek v5/main ve canli Worker degismez.


## 26.09.2026 — Yeni 30 dakikalik parkur: TMDb hard deadline + cache fallback
- [x] TMDb metadata relay: header gelmeyen veya header'dan sonra JSON stream'i asili kalan istegin tamaminda tek **12 saniyelik** AbortController/Promise.race son tarihi. HTTP yonlendirmesi hala elle reddediliyor; Bearer token baska host'a iletilmiyor.
- [x] Deadline asiminda yalniz `tmdb_connection_error:timeout` gibi sanitize hata kodu; 2 MB body siniri asimi `catalog_response_too_large`, bozuk JSON/UTF-8 `invalid_catalog_response`. Erken body hatasinda halen acik upstream fetch abort edilir.
- [x] Cloudflare edge cache okuma veya yazma arizasi metadata yanitini 500'e ceviremez: cache match/put best-effort. Secret exception mesajlari response'a verilmez.
- [x] Watchdog adaptor Proxy trap/getter kaynakli parser istisnalari `adapter_error` olarak yanlis karantinaya gitmek yerine tek denemede `anomaly_held` / audited `admin_required` yapilir. Yeni SQLite runner regresyon testi eklendi; tam D1 suite bekliyor.
- [x] Tam GitHub blob SHA-eslesmeli yerel secilmis testler: Worker bounded response **6/6**, ratings policy **3/3**, yeni deadline + gercek bounded stream **8/8**, Watchdog adaptor contract **8/8**, Kotlin film series **15/15**, Kotlin katalog kart **25/25** = **65/65**. Yeni tam `worker/test/catalog.test.mjs` entegrasyon testleri eklendi ancak tum Worker checkout ile bu parkurda yeniden calistirilmadi.
- [ ] Sonraki: tam exact Node 22 Worker + Watchdog suite; Wrangler/workerd+D1; Android Gradle .cs3; Mi Box afis, iki puan, bolum ve seri gorsel testi. GitHub Actions kotasi ve gercek kaynak haklari izinleri bekliyor; main/v5/canli Worker degismedi.

- Ek: Cloudflare edge cache match sonsuza kadar beklerse 1.5 saniye sonra TMDb origin'e duser; bu senaryo icin catalog.test.mjs entegrasyon regresyonu eklendi (tam suite henuz yeniden calistirilmadi).

- Son duzeltme: zorunlu olmayan adapter check alanlari da yalniz boolean olabilir; nesne, string veya gizli veri kontrol nesnesine gecemez. Exact SHA eslesmeli adapter testi 8/8.

## 26.09.2026 — Snapshot coroutine cancellation düzeltmesi
- [x] `WatchdogSnapshotRefresh.refresh`: offline cache restore ve imzalı JSON doğrulayıcı callback'lerinde `CancellationException` artık genel `Exception` içinde yutulmuyor; iptal çağırana iletiliyor.
- [x] Gerçek Kotlin refresh test dosyasına iki regresyon vakası eklendi: cache restore iptali ve imza doğrulama iptali.
- [ ] Bu iki yeni test dahil tüm Kotlin/JVM paketini ve gerçek Android lifecycle iptalini çalıştır; GitHub dosya değişiklikleri doğrulandı ancak test yürütme sonucu henüz yok.
- [ ] Tam Node/SQLite, Wrangler/D1, Gradle .cs3 ve Mi Box doğrulaması hâlâ bekliyor. Actions kotası doluyken workflow tetikleme yok; main/v5/canlı Worker/D1 değişmedi.

## 26.09.2026 — Cancellation zinciri ek kontrol
- [x] `SourceSnapshotOfflinePolicy.restore` parser iptalini artık yutmuyor; ayrı JVM regresyon vakası eklendi.
- [x] `WatchdogClientStore.acceptSignedJson` parser iptalini artık yutmuyor; ayrı JVM regresyon vakası eklendi.
- [x] Kotlin `try/catch` + Elvis sözdizimi yerel `kotlinc` ile bağımsız kontrol edildi; tam proje derlemesi yerine geçmez.
- [ ] Dört yeni iptal regresyonunu gerçek Kotlin/JVM paketinde çalıştır; Android yaşam döngüsü ve tam Gradle derlemesi hâlâ bekliyor.

## 26.09.2026 — JWKS ve admin hata güvenliği
- [x] Cloudflare Access JWKS isteğinin 4 saniyelik sınırı yalnız `fetch` başlıklarını değil, asılı kalan body stream okumalarını da kapsıyor (`loadJwks` yarış/zamanlayıcı + abort).
- [x] Asılı başlık, abort'u yok sayan body, normal JSON ve geçersiz deadline için Node regresyon vakaları eklendi.
- [x] Salt-okunur `/admin` yolunda Access doğrulayıcı istisnası artık kayıt okumadan önce 403 ile güvenli kapanıyor; ayrı regresyon testi eklendi.
- [x] Aynı JWKS deadline algoritması izole Node v22 yerel kontrolünde 4/4 senaryodan geçti; Kotlin iptal/Elvis kontrolü izole `kotlinc` ile geçti. Bunlar tam repo testinin yerine geçmez.
- [ ] GitHub'daki gerçek `source-watchdog` Node paketinin tüm testleri ve tam Kotlin/JVM paketi çalışma ortamında tekrar çalıştırılmalı.

## 26.09.2026 — Yönetici POST gövdesi zaman aşımı
- [x] En fazla 1024 baytlık yönetici JSON gövdesi artık ayrıca toplam 4 saniyelik okuma süresiyle sınırlı; takılan `reader.cancel()` beklenmiyor.
- [x] Asılı stream ve asılı cancel için 408 regresyon vakası eklendi.
- [x] İzole Node v22 kontrolünde normal/çok büyük/asılı body davranışı 3/3 geçti; tam `source-watchdog` test paketi çalıştırılmayı bekliyor.

## 26.09.2026 — Android/CloudStream iptal zinciri
- [x] `EAProvider` üç askıya alınabilir HTTP yolunda (`liveChannels`, `catalogRelay`, `getJson`) `CancellationException` iptalini üst katmana iletiyor; ağ/JSON hatalarının önceki fallback davranışı korunuyor.
- [x] `WatchdogApprovedAdapterBridge` gelecekte etkinleşen izinli cache okumasında iptal istisnasını yutmuyor.
- [x] Provider için Node kaynak-baglanti testi ve bridge için ek Node wiring testi eklendi; izole Kotlin stub'unda 3/3 iptal senaryosu geçti.
- [x] Yerel Node 22.16.0 SQLite ve Ed25519 runtime smoke geçti; gerçek repo testleri için checkout gerekiyor.
- [ ] Gerçek Android/CloudStream build, provider entegrasyonu, tam JVM ve Mi Box testi bekliyor.

## 26.09.2026 — .cs3 gömülü sürüm uyuşmazlığı
- [x] `stage-release.py` Gradle `plugins.json` v6 denetimine ek olarak, derlenmiş .cs3 içindeki `manifest.json` sürüm alanı varsa bunun da tam integer 6 olmasını şart koşuyor; olmayan alan eski uyum için kabul ediliyor.
- [x] Gömülü v4/v5/v7, string, float, boolean ve null sürümler için yeni Python regresyon testi eklendi; izole fixture mantığı 9/9 geçti.
- [ ] Gerçek Gradle tarafından üretilen .cs3 ile staging ve Android kurulum testi bekliyor.

## 26.09.2026 — Yönetici sağlık paneli tanı kodları
- [x] Dashboard yalnız önceden tanımlı `adapter_error` ve `probe_timeout` kodlarını da gösteriyor; ham adapter istisnaları gizli kalıyor.
- [x] İki izinli kod ve rastgele gizli hata metni için Node regresyon testi eklendi; izole whitelist kontrolü 4/4 geçti.

## 26.09.2026 — SHA eşleşmeli staging tam testi
- [x] GitHub'daki güncel `scripts/stage-release.py` blob SHA `3fc32b111b821ce93a5e01ae6291099e9a161064` ve `tests/test_stage_release.py` blob SHA `c5ac2ee169ac876cbaffd815f13315851d9a75b1` yerelde **birebir** yeniden oluşturulup SHA karşılaştırıldı.
- [x] Aynı iki dosyayla gerçek Python unittest staging paketi **20/20 geçti** (derlenmiş gerçek Android .cs3 yerine test ZIP fixture'ları kullanıldı).

## 26.09.2026 — SHA eşleşmeli Access JWT/JWKS tam testi
- [x] Güncel GitHub `source-watchdog/src/admin-auth.mjs` blob SHA `0eeb78ae0dc5b6d1690e9524510d61be6f4f70b4` ve `test/admin-auth.test.mjs` blob SHA `2c2e91f46105ea8fa7e90e96002b90954f14476c` yerelde birebir doğrulandı.
- [x] **Gerçek kaynak ve gerçek test dosyası** ile Node 22.16.0 Access JWT/JWKS paketi **12/12 geçti**; yeni asılı body, başlık timeout'u ve hatalı UTF-8 abort regresyonları dahil.
- [ ] Diğer watchdog Node/SQLite dosyalarının ve Wrangler/workerd'ın tam repo testi hâlâ bekliyor.

## 26.09.2026 — SHA eşleşmeli yönetici paneli tam testi
- [x] Güncel GitHub `admin-view.mjs` SHA `b645f11ce6480421bbc42ce452a48e89e6f2b365` ve `admin-view.test.mjs` SHA `83131b1e18adfed11db75536c34a568aa7d377ca` birebir yerel kopyalarla **6/6 Node testi geçti**. Güvenli tanı kodları, bilinmeyen durum alarmı, HTML escape ve ham hata gizleme dahil.

## 26.09.2026 — SHA eşleşmeli yönetici POST sınır testi
- [x] Güncel GitHub `admin-actions.mjs` SHA `2118c51c12afc0b97b2d18585af606733e7fb0dd` birebir yerel kopyayla doğrulandı; registry mutasyon importları test-only stub olduğundan gerçek D1/CAS testi sayılmaz.
- [x] Aynı gerçek modül üzerinde 7 bağımsız Node senaryosu **7/7 geçti**: kanonik JSON, 413 byte limiti, bozuk JSON, eylem/revizyon, origin, asılı cancel ile gerçek 4 saniyelik 408 ve admin kimliği hash'i.
- [ ] Gerçek `admin-actions.test.mjs` tam registry/Worker/SQLite bağımlılıklarıyla çalıştırılmalı.

## 26.09.2026 — SHA eşleşmeli Android refresh iptal testi
- [x] Güncel GitHub `WatchdogSnapshotRefresh.kt` SHA `a4575e6711a57b4a121486ac4293616b834ef0d9` yerelde birebir doğrulandı; gerçek kaynak Kotlin 1.9 + coroutines ile derlendi.
- [x] Test-only snapshot/trust modeli ve sahte HTTPS transport ile **9/9 izole JVM kontrolü geçti**: normal güncelleme, throttle, clock rollback, cache/verifier/transport iptali, varsayılan kapalı yapı, bozuk cache+network ve eşzamanlı mutex.
- [ ] GitHub'daki gerçek 25+ senaryolu `WatchdogSnapshotRefreshTest.kt` ve BouncyCastle/Android bağımlılıklarıyla tam test hâlâ açık.

## 26.09.2026 — SHA eşleşmeli gerçek offline snapshot testi
- [x] Güncel `SourceSnapshotTrust.kt` SHA `d20dcc8d3271b29dc79fdb9b7b7728b8036b5d4e`, `SourceSnapshotOfflinePolicy.kt` SHA `17a6a3be1c61d71e933ea18e9ed9c341802da762`, `SourceSnapshotOfflinePolicyTest.kt` SHA `32c4ff47df9f351862ff060de4efb5d0debc5984` birebir yerel kopyalarla doğrulandı.
- [x] **Gerçek üç Kotlin dosyası**, BouncyCastle 1.80 ve coroutines ile JVM'de **15/15 geçti**; yeni iptal regresyonu, imza, replay, TTL ve adapter sürümü dahil.

## 26.09.2026 — SHA eşleşmeli gerçek Kotlin refresh tam testi
- [x] Güncel `WatchdogSnapshotRefreshTest.kt` SHA `67c7dc09ddb02c5b020068f6f0ffac3c78447b84` birebir yerel kopyayla doğrulandı. Güncel gerçek trust/offline/refresh Kotlin kaynakları ve BC 1.80/coroutines ile **25/25 JVM test geçti**. İki yeni iptal regresyonu da dahil.
- [ ] Android gerçek SharedPreferences/HTTPS transport, Gradle .cs3 ve Mi Box testi hâlâ bekliyor.
