# EA-FB V6 — Kaynak, admin, erişim ve mahremiyet sözleşmesi

Durum: TASARIM / uygulama ve yayın onayı değildir. Bu belge yalnız `feature/detail-dual-ratings-v6` geliştirme dalına aittir. V46 bölüm başlığı/detay davranışı sabit; V5/main, mavi staging, canlı Worker ve canlı D1 değiştirilmez.

## PLT'den gözlenen mimari örnekler (uygulama kodu aktarılmaz)
- Kullanıcının gönderdiği `plt-stream.cs3` ZIP içinde `classes.dex` içerir; dizilerde `ClipBoxSourceDialog`, `ClipBoxSourceStore`, `ClipBoxPlayerInjector`, `ProviderRegistry`, `ExtractorRegistry`, `SourceCheckService`, `OpenSubtitlesService`, `SubtitleFile`, `DiziBoxExtractor`, `loadLinks` tanımları gözlendi. Bunlar davranışın uçtan uca doğrulandığı veya bu kaynakların çalıştığı anlamına gelmez.
- Kullanıcının gönderdiği `domains.json` içinde 18 site anahtarı bulunur; 18 canlı/izinli/çalışan kaynak olduğu anlamına gelmez.
- `plt-party` eşzamanlı izleme ve `plt-tv` canlı yayın ayrı ürünlerdir; mevcut kapsam dışı.
- ClipBox benzeri tek kaynak -> çoklu dil/çözünürlük seçenekleri açılan arayüz tasarımı örnek alınır; lisanslı kod veya korumalı içerik kopyalanmaz.

## Kaynak sözleşmesi
Bir sonuç bir **varyanttır**: `sourceId, mediaId, episodeId?, providerDisplayName, title, resolutionWidth?, resolutionHeight?, qualityLabel?, audioLanguage?, audioType (dub/original/unknown), subtitleLanguage?, subtitleType (embedded/selectable/external/unknown), verifiedAt?, expiryAt?, playbackUrl?, subtitles[]`.
- Liste aynı kaynağın her varyantını alt alta verir: `Dizibox · MobLand · 1920×1080 · Türkçe dublaj`, `Dizibox · MobLand · 1920×1080 · Türkçe altyazılı`, `Dizibox · MobLand · 1280×720 · Türkçe dublaj`.
- Kaynak yalnız 1080p etiketi bildiriyorsa `1920×1080` uydurulmaz; dil veya altyazı belirsizse bilinmiyor yazılır.
- Otomatik oynatma önceliği: doğrulanmış/erişilebilir Türkçe dublaj, bu grup içinde yüksek kalite; sonra Türkçe altyazılı ve yüksek kalite; sonra orijinal ses. Çalışmayan varyant bir alt uygun seçeneğe düşer. Kullanıcı her zaman elle başka varyant seçebilir.
- CloudStream yerel oynatıcı, ses/altyazı dili, webden altyazı, +/− kaydırma ve satırdan senkronlama arayüzü korunur. Gömülü altyazı ile ayrı .srt/.vtt ve seçilebilir akış altyazısı birbirine karıştırılmaz.
- ClipBox tipi gruplu arayüz opsiyoneldir: görünürde sağlayıcı tek satır, sağda tüm varyantlar; temel veri modeli yine varyant bazlıdır.

## Kaynak Bekçisi ve admin
- `sourceId` kalıcıdır. Durumlar: healthy/degraded/quarantined/admin_required/disabled; son kontrol, hata sınıfı, onarım denemesi, son çalışan revizyon ve zaman bilgisi saklanır.
- Geçici erişim hatası -> kontrollü yeniden dene. Süresi dolan geçerli URL -> sağlayıcının onaylı normal yönteminden yenile. Doğrulanmış izinli adres değişimi -> güvenli allowlist ile değiştir/test et. Yapısal değişim -> admin_required; rastgele kod üretme veya güvenlik/DRM atlatma yok.
- Onarım yalnız doğrulama geçerse yayına döner; bir kaynak arızası diğerlerinin aramasını/oynatmasını durdurmaz.
- Admin erişimi ve kullanıcı erişimi ayrı kimlik/rol sistemleridir. Mevcut admin Access JWT kontrolü ve denetim izi korunur. Admin butonları yazma özelliği gerçekten açık ve testli değilse çalışıyor gösterilmez.
- Adres düzenleme gibi yüksek riskli işlemler ancak ayrı inceleme ve atomik audit sonrası etkinleşir.

## İleride davet kodu/şifre + cihaz kotası
- İstenen model: 1 davet/erişim hesabı -> en fazla 2 eşzamanlı kayıtlı cihaz. Bu **henüz uygulanmış değildir**.
- Paylaşılan tek statik şifre yerine kişi başına ayrı davet kodu; sunucuda kodun yalnız güçlü hash'i, süresi, iptal durumu ve 2 cihazlık kayıt defteri tutulur. Kod sızarsa iptal edilir. Yönetici hesabı aynı kodu kullanmaz.
- Cihaz kimliği rastgele kurulum ID'siyle başlar; sıfırlama/cihaz değiştirme yönetimi gerekir. Yalnız istemci ID'siyle 2 fiziksel cihaz sınırı kesin güvence verilemez. İstemci kimliği taklit edilebilir; sunucu tarafında kısa ömürlü token ve oturum yenileme gerekir.
- Eklenti içindeki statik bağlantı/kod asla tek başına güvenlik kabul edilmez. Mevcut dağıtım ve katalog erişim noktalarının hangilerinin erişim kapısına bağlanabileceği ayrıca incelenir.

## Kullanıcı etkinliği — görünür izin, veri minimizasyonu
- Admin panelinde yalnız kullanıcı açıkça bilgilendirilip etkinlik kaydını kabul etmişse: kullanıcı/cihaz takma kimliği, giriş-çıkış, son etkinlik, arama terimi, seçilen film/dizi/bölüm ve oynatma başlat/durdur olayları tutulabilir.
- **Gerçek izleme süresi** ancak CloudStream sürümü/eklenti API'si oynatıcı ilerlemesini güvenilir şekilde bildiriyorsa hesaplanır. Başlatılan oynatmayı gerçekten izlendi diye sayma; arka planda kaldı, ağ kesildi ve kapatma olayının gelmediği durumlar ayrı tutulur.
- Oynatıcı telemetrisi mümkün değilse panelde `son etkinlik` ve `başlatma` gösterilir, `izlediği dakika` gösterilmez.
- Önerilen asgari veri: olay türü, takma kullanıcı ID, kurulum ID, UTC zaman, içerik kimliği, isteğe bağlı arama sorgusu; **ham video URL, IP, parola, altyazı metni, oynatma token'ı ve secret kaydetme**.
- Arama geçmişi hassas olabilir: ayrı açık rıza, kapatma seçeneği, kısa saklama süresi (örnek 30 gün), kullanıcıya kayıt görüntüleme/silme olanağı, admin erişim günlüğü ve veri erişim sınırı.
- Kullanıcıdan habersiz izleme veya gizli telemetri yok. Yerel mevzuat/uygulama mağazası kuralları ayrıca gözden geçirilecek.

## Dağıtım/temizlik ve teslim
- Üretim dağıtımında yalnız gerekli `.cs3`, repo manifestleri ve açıkça ihtiyaç duyulan statik dosyalar; `dev`, test fixture, geçmiş debug çıktıları ve kullanılmayan derlemeler yayın dizinine kopyalanmaz.
- **Kaynak repo** testleri, güvenlik denetimi, migration ve geri alma belgelerini korur; bunları rastgele silmek üretim temizliği değildir.
- İş bittiğinde gerçek kullanılan dosyaların envanteri, hash'li dağıtım paketi, test raporu ve kullanıcının indirebileceği ZIP hazırlanır. Çalışan dosyalar silinmeden önce geri alma kopyası doğrulanır.
- Kaynak hakları, gerçek izinler ve uzak bağlantı testleri ayrı yayın kapısından geçer; `ReviewedSourcePermits.bundled` şu an boş. Hiçbir gerçek kaynak bu belgeyle aktif olmaz.

## Uygulama sırası
1. V46 koruma ve mevcut V6 test/branch baz çizgisi.
2. Kaynak varyantı veri modeli, adlandırma, dil/kalite sıralaması ve offline testler.
3. Kaynak Bekçisi durumundan arama/oynatma görünürlüğüne geçiş, admin salt okunur ve kontrollü eylemler.
4. İzinli gerçek adaptör bazında test ve ayrı kullanıcı onaylı staging.
5. Davet/cihaz erişim kapısı, rızalı olay toplama, saklama/silme ve yönetim.
6. Mi Box doğrulaması, yalnız gerekli dağıtım dosyaları, ZIP yedeği ve geri alma.

## Kaynak istatistikleri — admin öncelikli
- İzleme süresi öncelik dışı; oynatıcı telemetrisi olmadan kesin süre iddiası yok.
- Kaynak başına dönem seçimi: son 24 saat / 7 gün / 30 gün / tüm zamanlar; olay zamanları UTC saklanır, panelde yerel saat gösterilebilir.
- Kullanım hunisi ayrı sayılır: `searchResultShown`, `sourceSelected`, `playbackStartAttempt`, `playbackStartConfirmed` (istemci gerçekten doğrulayabiliyorsa), `playbackStartFailed`. Arama sonucu gösterimini izleme olarak sayma.
- Hata sayısı ve oranı: bağlantı çözümlenemedi, zaman aşımı, bozuk URL, oynatma başlatma hatası, içerik eşleşmeme, kullanıcı iptali (hata değildir) ayrı sınıflandırılır. Hata oranının paydası aynı dönemdeki ilgili denemelerdir. Yetersiz örneklem ayrıca belirtilir.
- Otomatik müdahale: deneme sayısı, başarı/başarısızlık, son deneme, ortalama düzelme süresi, rollback sayısı ve onarım sonrasında tekrar arıza oranı.
- Manuel müdahale: disable/enable/retest/rollback ve onaylı adres değişimi gibi eylemler ayrı sayılır; aktör takma kimliği, UTC zaman, kaynak revizyonu ve sonuç audit kayıtlarına bağlanır. Kullanıcıya ait gizli anahtar veya ham erişim bilgisi gösterilmez.
- Operasyonel göstergeler: halen arızalı kaynaklar, admin_required bekleme süresi, son sağlıklı kontrol, art arda başarısızlık, en sık arıza nedeni, en çok seçilen ve en yüksek başarısızlık oranlı kaynaklar.
- Kapsam/sınır: mevcut watchdog kaynak durumlarını ve bazı admin eylemlerini kaydeder; istemciden kaynak seçimi/oynatma doğrulaması telemetrisi henüz yok. Panel yeni sayaçları veri akışı bağlanmadan sıfır gerçek kullanım gibi sunmamalı: `veri toplanmıyor` demeli.
- Kullanıcı etkinliği analitiği açıkça bilgilendirilerek ve uygun izin/tercih seçenekleriyle yapılır; admin için kaynak düzeyinde anonim toplamlar tercih edilir. İzleme süresi için ayrı kapsam açılmadıkça geliştirme yapılmaz.

## Ses/altyazı tercih istatistikleri
- Kaynak ve dönem bazında `audioType` (Türkçe dublaj, orijinal dil, diğer dil, bilinmiyor) ve `subtitleType` (Türkçe altyazı, başka dilde altyazı, altyazısız, bilinmiyor) kırılımı. Bir kaynak seçimi tek bir ses sınıfında sayılır; ses ve altyazı ayrı boyutlardır, birbirinin toplamı gibi sunulmaz.
- Dublajlı ve altyazılı varyantın kaç kez seçildiği, doğrulanmış oynatma başlangıcı mevcutsa kaç kez gerçekten başladığı ayrı sayılır. Çözünürlük (4K/1080p/720p/bilinmiyor) ile çapraz filtrelenebilir.
- Gömülü, akış içi seçilebilir ve haricî altyazı dosyası ayrı teknik etiketlerdir. Kullanıcının oynatıcı içinde sonradan değiştirdiği altyazı/ses yalnız desteklenen olay API'si varsa ölçülür; aksi hâlde ilk seçilen kaynak varyantı sayılır.
- Film/dizi, kaynak, gün/hafta/ay, ses dili, altyazı dili ve çözünürlük filtreleri; düşük örneklem ve `bilinmiyor` değerleri saklanmadan gösterilir.
- Kaynak kullanım analitiği için mümkünse kimliksiz toplulaştırılmış sayaç kullanılır; kişiye özel izleme alışkanlıklarını gereksiz yere kaydetme. Telemetri etkin değilse sıfır yerine `veri toplanmıyor` gösterilir.

## İçerik bazında kaynak ve dil istatistikleri
- Sayaçlar TMDb içerik kimliği + film/dizi türü ile ilişkilendirilir; dizide varsa sezon ve bölüm kimliği ayrıca tutulur. Başlık değişse de kimlik değişmez.
- Admin raporları: film/dizi -> kaynak -> ses türü (TR dublaj / orijinal / diğer / bilinmiyor) -> altyazı dili ve türü -> çözünürlük; ayrıca kaynak -> en çok seçilen içerikler.
- Kullanıcının araması, kaynak seçmesi ve doğrulanmış oynatma başlangıcı farklı olaylardır; başlatma doğrulanamıyorsa yalnız seçim sayısı gösterilir. Aynı denemenin tekrarlanan bildirimleri çift sayılmaz.
- Kişi bazında ayrıntılı izleme dökümü varsayılan değildir; toplu içerik istatistikleri tercih edilir. İçerik adı hassas olabileceğinden kullanıcı bilgilendirmesi, tercih ve saklama süresi şartları geçerlidir.

## İleri aşama: kullanıcı deneyimi ve kendi kendine iyileştiren sıralama (öneri)
- İstemci oynatma başlangıç zamanını güvenilir bildiriyorsa kaynak başına açılış gecikmesinin p50/p95 değerlerini topla; aksi halde yalnız resolver yanıt süresini ölç ve oynatma süresi diye etiketleme.
- Aranıp sonuç bulunamayan içerikleri film/dizi TMDb kimliği bazında toplulaştır; yazım hatalı sorgular ve katalogda bulunmayan başlıklar ayrıştırılır.
- Kullanıcının kısa sürede başka kaynağa geçişini `quickSwitch` olarak say; bunu tek başına hata veya kalite kusuru sayma.
- Otomatik onarım başarısını anlık test ve 10/60 dakika sonra kalıcılık olarak ayrı değerlendir. Yeniden arıza oranını raporla.
- Saatlik hata ve başarısız deneme oranları, veri yeterliliği ve kaynak bazında kontrol aralıkları gösterilsin.
- Kaynak sıralamasında önce dil tercihi ve doğrulanmış kalite, sonra sağlık/başarılı başlangıç oranı/yanıt süresi kullanılabilir; az örneklemli yeni kaynaklar sonsuza kadar geri plana düşmesin, bozuk kaynak için güvenli cooldown ve otomatik geri dönüş olsun. Admin açıklanabilir karar gerekçesini görsün.
- Admin önerileri yalnız önceden doğrulanmış işlemlerden gelsin: kontrollü tekrar deneme, onaylı son sağlıklı adrese rollback, geçici karantina; otomatik kod üretme, onaysız alan adı ekleme veya DRM/giriş koruması atlatma yok.
- Kullanıcı etkinliklerinden çıkarılacak ölçümler için şeffaf bilgilendirme ve veri minimizasyonu ilkeleri korunur; bütün ölçümler 'uygulanmış' değil 'planlanmış' statüsündedir.

## Kaynak yok / yanlış içerik bildirimleri: otomatik teşhis ve olay yönetimi
- Kullanıcı bildirimi doğrudan admin e-postası değildir. İstemci önce internet/servis erişimini, bağlantı zaman aşımını ve kaynak çözümleme sonucunu ayırt eder; kişiye özgü ağ hatasını küresel kaynak arızası saymaz.
- İlgili TMDb içerik kimliği ve varsa sezon/bölüm için kaynaklar tekrar aranır. Sağlayıcı sayısı, dönen bağlantı sayısı, izinli sağlık kontrolünden geçen bağlantı sayısı ayrı metriklerdir; `34 sağlayıcı çalışıyor` ifadesi yalnız gerçekten doğrulanan sağlayıcılar için ve ölçüm zamanı ile gösterilir.
- Bağımsız servis kontrolleri ve birden fazla farklı cihazdan aynı tip başarısızlık, tekrar eden raporların ağırlığını artırır. Tek kullanıcının çok sayıda tıklaması ayrı bağımsız şikâyet sayılmaz; aynı içerik/kaynak/hata penceresi için deduplikasyon ve cooldown uygulanır.
- Otomatik kontrol güvenli ve onaylı erişimlerle sınırlıdır; DRM/giriş koruması atlatma, rastgele alan adı tarama veya kaynak URL'sini herkese açma yok. Oynatma cihazına özgü arızalar uzaktan kesin doğrulanamayabilir; sonuç `belirsiz` olarak işaretlenir.
- Kullanıcıya tanısal ve dürüst yanıt: `Bu içerik için X bağlantı bulundu, Y bağlantı kontrolü geçti; cihazınızda ağ/oynatıcı sorunu olabilir` veya `Kaynaklarda genel arıza doğrulandı, otomatik kontrol sürüyor`. Sağlık kontrolü başarılı diye oynatma garantisi verilmez.
- Olay durumu: `reported -> checking -> local_issue / inconclusive / confirmed -> auto_repair -> resolved / admin_required`. Tekrarlı ve bağımsız olarak doğrulanmış, otomatik düzeltilemeyen olay admin kuyruğuna alınır. E-posta yalnız önem eşiği/süre aşımı sonrası, tekrar bildirim sınırlamasıyla gönderilir; e-postada içerik ve kaynak kimliği, test sonucu, kaç bağımsız rapor ve admin panel bağlantısı bulunur.
- Admin raporlarında gürültü ölçümleri: toplam bildirim, tekilleştirilmiş olay, kullanıcı bağlantısı kaynaklı olası hata, doğrulanmış genel arıza, otomatik kapanan olay, elle müdahale gerektiren olay ve ortalama çözüm süresi. Kullanıcıyı `beceriksiz` olarak sınıflandırma yok.
- Bu akış mevcut uygulamada doğrulanmış çalışan özellik değildir; istemci ölçümü, watchdog kontrolü, olay veritabanı, e-posta sağlayıcısı ve gerçek cihaz testleriyle aşamalı uygulanır.
