# EA-FB V81 — V66 üzerine Bronze NL/LAND entegrasyonu

Bu belge yeni test dalının uygulama ve kanıt kaydıdır. İlk genel inceleme veya CLEAN karşılaştırma raporunun yerine geçmez. Yeni NL/LAND entegrasyonunda cihaz oynatması henüz doğrulanmadı.

## Başlangıç, izolasyon ve kullanıcı testi

- Temel: `test/clean-codex-fix-20261009`, HEAD `649a45cdc50c86a86d4134ffb1668838275b19e8`.
- V66 CS3: 244782 bayt; SHA-256 `89dfc4ac1d5f737364e1cbc21e62a2846e8cf6b5cc563df89c9e54c166be9480`; DEX SHA-256 `920f3b140c576b170ebd8c20e92bc2ddd41075230d5396e0f789d968d8d51bb9`. Manifest sürüm 66 ve iki adaptörlü kapsam doğrulandı.
- Yeni dal: `test/clean-codex-bronze-nl-land-20261009`, tam olarak bu V66 commit'inden oluşturuldu.
- Yeni sürüm **81**: incelenen V65–V80 hatlarının sürümleriyle çakışmaz; dal adlarının kronolojik sürüm sırası olduğu varsayılmadı.
- Ad/provider: `EA-FB CODEX BRONZE NL LAND TEST`; internalName/CS3: `EA-FB-CODEX-BRONZE-NL-LAND-20261009`.
- Yeni ayar alanı: `ea_fb_clean_bronze_nl_land_20261009`; önceki `ea_fb_clean_codex_fix_20261009` alanı yalnızca okunur. Kategori/sıralama ve DiziBox/DiziYou'nun açıkça kayıtlı tercihleri bir kez taşınır. NL/LAND yeni ve varsayılan kapalıdır.
- Ayrı dağıtım: `dist-clean-codex-bronze-nl-land-20261009/`; ayrı workflow: `.github/workflows/bronze-nl-land-isolated.yml`. Guard yalnızca bu depoyu ve bu yeni dalı kabul eder; yayın normal push kullanır, force push yoktur. Mevcut workflow dosyaları değiştirilmedi.
- Worker/D1, production feed/kısa kodlar, main/Blue V5, V66, eski CLEAN/V80 ve diğer test dalları üzerinde yazma yapılmadı.

**Kullanıcı bildirimi — 9 Ekim 2026:** V66 Mi Box testinde DiziBox 1, DiziYou 2 kaynak bulundu; toplam 3 kaynak listelendi ve oynatma başarılı bildirildi. Üç kaynağın her birinin ayrı ayrı oynatıldığı doğrulanmadı. Bu, DiziBox/DiziYou'nun ilk tarihsel oynatma başarısı değildir. Bu entegrasyonun cihaz testi olarak kullanılamaz.

## Referansların erişim durumu

BronzeCloud-master.zip (13582 bayt), ZIP commit yorumu `edab1db4cb275e3706b4749fbdb4ab1eb222b9f0`, yalnızca LICENSE, README, .gitignore, _config.yml ve repo.json içerir. Özgün adaptör Kotlin kaynakları yoktur. BronzeCloud-builds.zip (22172302 bayt), ZIP commit yorumu `7f680cd5ef2ad8be2dc579b1e46b53ba3e28d4cd`, CS3/DEX referanslarını sağlar. Aşağıdaki davranış kanıtları **DEX incelemesidir**, özgün Kotlin kaynak bulgusu değildir.

| Referans | Sabit paket | SHA-256 |
|---|---|---|
| Bronze NL v52 | [HDFilmCehennemi_v52.cs3](https://raw.githubusercontent.com/dr-octagon/Cloudstream-BronzeCloud/7f680cd5ef2ad8be2dc579b1e46b53ba3e28d4cd/HDFilmCehennemi_v52.cs3) | `62069a271af4837d84131a11c274ee5248b5ff4329f86855a7c15168feca501a` |
| Bronze LAND v4 | [HDFilmcehennemiLand_v4.cs3](https://raw.githubusercontent.com/dr-octagon/Cloudstream-BronzeCloud/7f680cd5ef2ad8be2dc579b1e46b53ba3e28d4cd/HDFilmcehennemiLand_v4.cs3) | `3342b06358b64b32a6c5f8b463958d2d40faa4f69359447eb1d88d05b38b8c80` |

Yaklaşık 20 MB Claude inceleme paketi ve önceki cihaz test logları erişilebilir değildir. 22 MB Bronze builds arşivi Claude paketi olarak gösterilmedi. V66 cihaz sonucu kullanıcı bildirimidir.

## Yardımcı eski EA-FB hatları

Önceki CLEAN raporunda incelenmiş kod/paket bulguları kullanıldı; genel araştırma tekrarlanmadı:

| Hat | Commit | Bu entegrasyona etkisi |
|---|---|---|
| V65 LAND — `test/land-fastplay-isolated-20261009` | [2eaf679c7bcc1dbefcb9230d7f4f127311cb435f](https://github.com/eaatabay/EA-FB/tree/2eaf679c7bcc1dbefcb9230d7f4f127311cb435f) | `HDFilmCehennemiAdapter.kt`, `LandFastPlayResolver.kt`, `LandFastPlayCodec.kt`: özel FastPlay/X-Sp var, Bronze LocalHlsServer zinciri yok. Kısmi port kullanılmadı. |
| V75 adlı NL/LAND izole dal, kaynak sürümü V79 — `test/nl-land-v75-isolated` | [2653bfdfc6d511c71561bde60af304cebecbcc61](https://github.com/eaatabay/EA-FB/tree/2653bfdfc6d511c71561bde60af304cebecbcc61) | HDF ortak adaptörü yardımcı referanstır; dal adından V75 çalışma sürümü çıkarılmadı. |
| V80 dört-adaptör testi — `test/bronze-direct-integration-v79` | [4658aaaa12e54ef5b02e3e91f5d36c5b3f41e514](https://github.com/eaatabay/EA-FB/tree/4658aaaa12e54ef5b02e3e91f5d36c5b3f41e514) | NL Rhino/file ve generic extractor yolları var; LAND, V65 veya Bronze'un tam eşdeğeri değil. Eski HDF adaptörü kopyalanmadı. |

V64/V66'da NL/LAND mevcutmuş gibi davranılmadı. Eski sürümlerin Mi Box'ta doğrulanmamış algoritmaları yeni başarı kanıtı değildir.

## Bronze'dan korunan davranışlar ve DEX kanıtı

Aşağıdaki `code_offset` özgün referans paketinin `classes.dex` dosyasındaki bayt konumudur; `+` değerleri 16-bit komut birimleridir. Birleştirilmiş DEX'in dosya konumları farklıdır.

| Akış | NL v52 / `com.keyiflerolsun.HDFilmCehennemi` | LAND v4 / `com.keyiflerolsun.HDFilmcehennemiLand` |
|---|---|---|
| Arama | `search`, `0x69ac`: `/search?q=` +0x55, `X-Requested-With: fetch` +0x65/+0x67 | `search`, `0xd1a0`: UTF-8 +0x68, `/?s=` +0x7b, Chrome124 UA +0x8b; article/result selectors |
| Film/dizi ve bölüm | `load`, `0x4e44`: h1.section-title, year-country, div.seasons; bölüm regex +0x3e3, sezon +0x411 | `load`, `0xa364`: h1, yıl alanları, ul.episodios/#seasons ve sezon/bölüm metin/URL regexleri |
| Player/iframe | `loadLinks`, `0x5a34`: alternative-links, data-lang/data-video; `/video/` +0x32e, JSON html ve iframe data-src/src | `loadLinks`, `0xb104`: nonce/#fimcnt/postid/player_name/part_key; `/wp-admin/admin-ajax.php`, POST `action=get_video_url`; FastPlay +0x7c0, generic extractor +0x98f/+0xc79 |
| Kodlanmış URL | `decryptPlayerUrl`, `0x6d04`: unpack +0xcb, Rhino Context.enter +0x151, evaluateString +0x168/+0x170, exit +0x188/+0x195 | `extractFastPlay`, `0x3cd4`: fastplay.mom, SPG.cerceve +0xcc8, XOR/base64 ve sp/spT/stream/src/manifest yolları |
| Header ve token | `invokeLocalSource`, `0x3d38`: Accept, Chrome124 User-Agent, Referer, caption tracks ve newExtractorLink +0x78a. İncelenen sabitlerde Origin/Cookie gereksinimi varsayılmadı. | `extractFastPlay$getStreamHeaders`, `0xdb10`: Referer/UA, X-Sp +0x18, generateXSpToken +0x1a. `generateXSpToken`, `0xd62c`: özgün nonce/timestamp/hash algoritması kullanılır. |
| HLS/audio/altyazı | Özgün track/subtitle ve extractor callback zinciri | `extractFastPlay`, `0x3cd4`: audio_/video_/master_ playlistleri; Türkçe dublaj/altyazı/orijinal seçimleri; LocalHlsServer.setPlaylist +0x1b0a/+0x1fff/+0x23a4 ve devamı |
| Yerel sunucu | Kullanılmaz | `LocalHlsServer.start`, `0xe7d0`: açıkça 127.0.0.1 +0x1e ve InetAddress alan ServerSocket +0x26; ephemeral port. `setPlaylist`, `0xe90c`: http://127.0.0.1: +0x10 |

LAND `LocalHlsServer.handleConnection`, `0xea30`, ConcurrentHashMap.get +0x73 ile saklanan playlist metnini seçer; HTTP 200 HLS Content-Type +0x90 ve body write +0xb1 vardır. Burada uzak segment HTTP istemcisi çağrısı görülmez. **LocalHlsServer tüm uzak segmentleri proxy'liyor veya her segment için yeni X-Sp üretiyor denmedi.** Özgün FastPlay playlist hazırlığı korunmuştur; uzak segmentlerin, alt playlistlerin, audio/subtitle seçimlerinin ve token ömrünün cihazdaki gerçek erişimi ayrıca doğrulanmalıdır.

## Entegrasyonun gerçek kaynak kodu

Uygulama commit'i: [94671e13a3e51960b61089f76bb471e08a80f3c4](https://github.com/eaatabay/EA-FB/commit/94671e13a3e51960b61089f76bb471e08a80f3c4), yeni dal.

- [BronzeApiAdapter.kt L33](https://github.com/eaatabay/EA-FB/blob/94671e13a3e51960b61089f76bb471e08a80f3c4/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L33): ana/orijinal başlık adaylarıyla özgün MainAPI.search ve load çağrısı; normalize edilmiş tam başlık, bilinen yıl ve içerik türü kontrolü. Dizide sezon/bölüm tam eşleşir. Filmde MovieLoadResponse.dataUrl; dizide Episode.data aynen tutulur.
- [BronzeApiAdapter.kt L67](https://github.com/eaatabay/EA-FB/blob/94671e13a3e51960b61089f76bb471e08a80f3c4/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L67): opaque playback verisi özgün MainAPI.loadLinks'e aktarılır. URL, extractor türü, kalite, başlıklar, referer ve geç gelen altyazılar korunur; subtitle headers ayrıca taşınır. Sabit sitelerde yeni Cookie/Origin uydurulmaz.
- [BronzeApiAdapter.kt L102](https://github.com/eaatabay/EA-FB/blob/94671e13a3e51960b61089f76bb471e08a80f3c4/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L102): aynı plugin classloader'ından özgün Bronze MainAPI sınıfı oluşturulur. Bronze plugin giriş noktaları pakette bulunsa da çağrılmaz; ek bağımsız provider kaydı yapılmaz.
- [Domain.kt L119](https://github.com/eaatabay/EA-FB/blob/94671e13a3e51960b61089f76bb471e08a80f3c4/EA-FB/src/main/kotlin/com/eafb/Domain.kt#L119): HTTP istisnası yalnızca LAND, doğrulanmış provenance bayrağı, HLS türü ve `http://127.0.0.1:<port>/master_*.m3u8` içindir; userInfo/query/fragment kabul edilmez. localhost, LAN IP, diğer provider ve keyfi HTTP reddedilir. LAND subtitle istisnası vtt/srt için aynı loopback ile sınırlıdır.
- [SourceEngine.kt L168](https://github.com/eaatabay/EA-FB/blob/94671e13a3e51960b61089f76bb471e08a80f3c4/EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt#L168): ortak URL policy, callback kilitleme, zaman aşımı öncesinde alınmış linklerin korunması. Opaque veri teklif tekilleştirmesinde hesaba katılır.
- [PlaybackLinkBridge.kt L95](https://github.com/eaatabay/EA-FB/blob/94671e13a3e51960b61089f76bb471e08a80f3c4/EA-FB/src/main/kotlin/com/eafb/PlaybackLinkBridge.kt#L95): dört bundled adapter; yalnızca kullanıcının açtığı kaynaklar aranır. Kaynak hata/LinkageError durumu diğer kaynakları kapatmaz.
- [EAProvider.kt L966](https://github.com/eaatabay/EA-FB/blob/94671e13a3e51960b61089f76bb471e08a80f3c4/EA-FB/src/main/kotlin/com/eafb/EAProvider.kt#L966): altyazı headers ve video headers/referer/HLS callback aktarımı; NL/LAND ayrı etiketleri.
- [Ayar kimliği/migration commit'i](https://github.com/eaatabay/EA-FB/commit/00a771ab72f998a1ed47694e4d410ea0edc073ce): yeni provider/internalName/store ve V66 store'a yazmayan migration.

DiziBoxAdapter.kt ve DiziYouAdapter.kt V66 temel commit'iyle kaynak düzeyinde byte-for-byte aynıdır. Önceki AES King/Moly/Haydi, GET/AJAX fallback, episode parser, altyazı, orijinal başlık, adaptive HLS ve kısmi timeout özellikleri korunmuştur.

## Paketleme ve testlerin sınırı

`package-bronze-nl-land.py`, NL52/LAND4 CS3'lerini sabit commit adreslerinden alır; SHA-256, ZIP, manifest sürümü, DEX SHA1/adler integrity ve namespace/çakışma kontrollerinden sonra Android build-tools **35.0.0 D8** ile EA-FB DEX'ine birleştirir. Tanımlı sınıfların üç giriş DEX'inin tam birleşimi olduğu kontrol edilir. İki Bronze paketindeki **250 metodun komut dizileri**, değişen string/type/field/method tablo indeksleri normalize edilerek karşılaştırılır; bir fark yayını durdurur. Bu kontrol byte-for-byte aynı DEX veya cihaz uyumluluğu iddiası değildir.

Nihai DEX'te gerekli tanımlar: `com.eafb.DiziBoxAdapter`, `DiziYouAdapter`, `BronzeNlAdapter`, `BronzeLandAdapter`, `BronzeApiAdapter`, `PlaybackLinkBridge`, `EAProvider`, `EAPlugin`; ayrıca özgün `com.keyiflerolsun.HDFilmCehennemi`, `HDFilmcehennemiLand`, `LocalHlsServer`. Salt string tablosu değil class_def tablosu incelenir.

`BRONZE-LICENSE` ve `BRONZE-NOTICE.txt` hem dağıtıma hem CS3'e eklenir. Yüklenen GPL-3.0 metni korunur; erişilemeyen özgün Kotlin kaynakları sunulmuş gibi gösterilmez.

Çevrimdışı testler:

- V66'nın 9 Kotlin suite'i: Domain, SourceEngine, PlaybackData, PlaybackQuery, DiziYouEpisodeParser (10/10), PlaybackSourceHealth, CleanSettings, CleanEngine, CleanAdapters.
- NL ve LAND için ayrı wrapper regresyonları: arama/orijinal ad, yanlış isim/domain/yıl/sezon/bölüm reddi, film ve doğru episode opaque payload, referer/Origin/UA/headers/HLS, geç altyazı headers, kısmi timeout, search/resolve LinkageError izolasyonu.
- Gerçek dört-adaptörlü PlaybackLinkBridge'in ayrı opt-in ve birlikte sonuç listesi: **Bronze sınıfları test-only MainAPI fixture'larıdır**, özgün DEX'in JVM üzerinde canlı çalıştığı iddia edilmez. DiziBox/DiziYou gerçek adaptör kodu HTML/extractor fixture'larıyla çalışır.
- V66 paket doğrulamasının 3 Python testi korunur ve yeni runner tarafından da çalıştırılır.
- 4 yeni Python dağıtım testi: doğru metadata; yanlış bytes/size; V66 kimliği/yanlış provider; yanlış sürüm/URL reddi.
- Gerçek Android `:EA-FB:make` ve DEX birleştirme başarılı. Yerel derlemede 203 D8 Kotlin-metadata rewrite uyarısı, Gradle/SDK ve deprecation uyarıları vardı; sıfır uyarı iddiası yoktur.

Bu görevin seçilen testleri başarılıdır; testler sırf yeşil yapmak için etkisizleştirilmedi. Önceki V66 görevinde legacy `test-playback-v6.sh` Node/SQLite bölümü geçti, Kotlin bölümü Android Log stub eksikliğiyle başarısız oldu; bu eski başarısızlık temizlenmiş sayılmadı. Yeni test runner gerekli stub'larla ayrı olarak çalışır.

Kontrollü canlı NL/LAND video/segment testi yapılmadı: izinli canlı test fixture'ı ve Mi Box erişimi yok. Rhino/CloudStream/NiceHttp/OkHttp host bağımlılıklarının gerçek cihaz uyumluluğu, HTTP loopback'in kullanılan Android/CloudStream sürümünde kabulü, HLS segment/anahtar erişimi, token yenilenmesi ve uzun süreli oynatma hâlâ cihaz kontrolüdür. DEX kanıtı bunları garanti etmez.

## Mi Box doğrulama listesi

Yeni repo/provider'ı seçin; V66 provider'ına dokunmadan yeni ayarlarda NL ve LAND'i açın. Önce ayrı ayrı, sonra dört kaynak birlikte deneyin. Film ve bilinen sezon/bölümde doğru içerik, kaynak sayısı ve provider etiketi; video/ses; Türkçe dublaj/orijinal ses; TR/EN altyazı; kalite değiştirme; ileri/geri sarma; uzun süreli oynatma/token süresi; source timeout sırasında diğer kaynakların korunması; DiziBox/DiziYou regresyonu ayrı kaydedilmelidir. LAND loopback master/alt playlist/uzak segment erişimi ve NL Rhino/extractor çalışma hataları cihaz loglarıyla ayırt edilmelidir. Loglarda imzalı URL, Cookie, nonce ve X-Sp değerleri paylaşılmamalıdır.
