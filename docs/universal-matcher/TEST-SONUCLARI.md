# Test sonuçları / 8601

Yerel: 373 saf aday/kimlik assertion PASS; 32 gerçek-adaptör discovery fixture vakası PASS; klasik 12 Kotlin regresyon paketi, film/oynatma-kimliği, settings/opt-in, gerçek resolver fixture'ları (King/Moly/Haydi, DiziYou player, Bronze delegation/subtitles/loopback/cancellation), 7 eski paket Python testi PASS. 2 genellik/soy testi ve 2 source guard testi PASS. 3 yeni paket testi, gerçek UI talimatı mutasyonu dahil PASS (skip yok). Immutable V83 arşivinde aynı discovery senaryoları ve özgün regresyon/scope testleri ayrıca çalıştırılır.

| Kaynak | V83 doğru kabul /3 | Yeni /3 | V83 yanlış kabul /5 | Yeni /5 | Fixture çağrısı V83 → yeni |
|---|---:|---:|---:|---:|---:|
| DiziBox | 1 | 3 | 5 | 0 | 14 → 20 |
| DiziYou | 1 | 3 | 4 | 0 | 18 → 22 |
| Bronze NL | 1 | 3 | 2 | 0 | 15 → 17 |
| Bronze LAND | 1 | 3 | 2 | 0 | 15 → 17 |

Bunlar sekiz kontrollü senaryonun her gerçek adaptöre uygulanmasıdır; saha başarı oranı değildir. Ted Lasso doğrudan ad, metadata sezon alias'ı ve daha önce görülmemiş ad pozitiftir. Yanlış yıl, eksik yıl, yanlış bölüm, iki güçlü sayfa ve film/dizi karışması negatiftir. Yılların ikisi de bilinmeyen salt film adı V83'te kabul edilirken yeni modelde bilinçli olarak belirsizdir. Pozitif eski HTML fixture'lerine gerçek kaynak başlığı/yılı/bölüm kanıtı eklendi; resolver/oynatma beklentileri kaldırılmadı.

Yeni doğrulama sayfa okuduğundan toplam fixture HTTP/API çağrısı artar; olumlu ve olumsuz sonuçları birlikte ölçer. Süreler KARSILASTIRMA.json içinde yalnız fixture ölçümüdür; internet veya Mi Box latency ölçümü değildir. Aynı bütçedeki çoklu aday için 150ms timeout testi yaklaşık 165ms'de 2 çağrıyla durdu; aday başına yeni 20 saniye açılmıyor. Gerçek engine search bütçesi adaptör başına 20sn, resolve ayrı 20sn olarak kaldı. Sezon adının metadata'da bulunduğu normal durumda ek çağrı yok; eksik seçili sezon fallback'i en fazla bir izinli route ve 4sn. Gateway cache mevcut; kullanıcı veri/cache alanına yazma yok.

Android build PASS. Derlenmiş pakette 1939 korunan V83 metodu, UI/film-helper/resolver algoritmaları dahil korunur; 250 özgün Bronze DEX metodu birebir kalır. Onaylı değişiklikler DTO/metadata/search sınırındadır. 298 V83 dosyası byte-identical; 14 baseline dosyasının tam hash'i ve search/metadata dışı bölgeler korunur. UI source, EASettings, PlaybackResolution ve plugin load akışı aynıdır; yalnız isim/store sabitleri 8601'e ayrıdır.

Genellik testi matching runtime'ında özel yapım literal'leri, bilinen fixture ID'leri ve sabit query.tmdbId/title dispatch'lerini reddeder. 80 yeni ad/ID ailesi ve Latin dışı adlar pozitif/negatif denenir. Sezon 2 adı, ilk yayın yılı ve sezon yılı farklılığı, remake, devam filmi/spin-off, farklı tür, eksik/yanlış bölüm ve ambiguity ayrı testlidir. Release yılı için tahmini ±1 tolerans yoktur.

CANLI-OKUMA.json: izinli staging katalog ve DiziYou search/sayfa HTTP200. Gerçek DiziYou adaptörü güncel HTTP yanıtlarıyla replay edildi; tek S1E8 offer, resolver/extractor/video çağrısı yok. İlk replay eski hardcoded capture URL'si nedeniyle başarısızdı; aramanın döndürdüğü güncel href takip edilerek düzeltildi. DiziBox HTTP403; bu ortamdan canlı bulma/oynatma başarı iddiası yok. NL/LAND canlı araması veya video oynatması bu ortamda doğrulanmadı. Alternative titles gateway tarafından sunulmuyor; parser var ama erişim uydurulmadı, production değişmedi. Staging önerisi uygulanmadı.

Mi Box: kullanıcıdan gelen V83/8503 cihaz gözlemleri tarihsel gerçek kanıttır. 8601 UI, Ted Lasso, Organize İşler, dört kaynağın gerçek link/video başarısı ve cihaz performansı kullanıcı testine kadar doğrulanmamıştır. CI 39348099263 başarılı: https://github.com/eaatabay/EA-FB/actions/runs/39348099263. CI logundaki paket hash’i yerel CS3 ile eşleşti: bf6b92ddf83392a52a92593a00d98e60e902e4451492b2baf56ecaafcab551d1. Tek CI çalışması yapıldı, başarısız CI yok. Yerel ABI guard hataları yayımdan önce düzeltilmişti. Yayımlanan raw dosya doğrulaması YAYIN-DOGRULAMA.json kaydına eklenecek.

Paketleme sırasında mevcut pinned Kotlin/D8 metadata uyarıları loglandı; checksum/TLS veya testler devre dışı bırakılmadı. Uyarı sayısı CI-SONUCU.json içindedir. Bu derleme başarısı Mi Box oynatması kanıtı değildir.
