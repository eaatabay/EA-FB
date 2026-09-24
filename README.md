# EA-FB — tek CloudStream eklentisi

**Durum (25.09.2026): EA-FB v3 yayımlandı; Android TV'de afişler, MP4 720p oynatma ve alternatif HLS kaynak listesi doğrulandı.** HLS'nin baştan sona oynatılması, TMDb katalog kurulumu ve gerçek film/dizi adaptörleri ayrıca test edilecek. Bu repo, film/dizi katalogları ve izinli canlı TV yayınlarını tek görünen `EA-FB` eklentisinde toplama amacıyla oluşturuldu.

## Kapsam

- Tek eklenti (`EAPlugin`) ve tek görünür CloudStream sağlayıcısı (`EAProvider`). Film, dizi ve canlı TV için ayrı eklentiler kurulmaz.
- Paylaşılan ana sayfadaki **28 kategori için veri tanımları**. Şu an TMDb yolu tanımlı kategoriler için API çağrısı iskeleti var; izlemeye devam et ve özel listeler henüz çalışmıyor.
- Film/dizi kimliklendirme, kanal adlarını normalleştirme, aynı kanalı birleştirme, alternatif linkleri tekilleştirme ve önceliklendirme için saf Kotlin modelleri.
- İleriki hedef: tek film kartında çoklu yetkili yayın, tek kanal kartında alternatif izinli canlı TV akışları, dil/kalite seçenekleri, otomatik kaynak seçimi. Şimdi **açık lisanslı bir deneme filmi** için oynatma hattı bağlıdır; ücretli platformlar için oynatma bağlantısı yoktur. Canlı TV kanalları henüz eklenmedi.
- Paylaşımlı TTL önbellek ve bağımsız Android APK **ikinci aşamada**.

## Geliştirme ve test

Projede GitHub Actions workflow'u yoktur. Android SDK + JDK 21 + internet üzerinden Gradle ve CloudStream bağımlılıklarıyla yerel derleme hedeflenir. Android derlemesi geçmeden `.cs3` yayımlamayın.

Android bağımlılıkları olmadan domain testleri:

```bash
kotlinc EA-FB/src/main/kotlin/com/eafb/Domain.kt core-tests/DomainTest.kt -include-runtime -d /tmp/ea-fb-tests.jar
java -jar /tmp/ea-fb-tests.jar
```

CloudStream derlemesi için Gradle eklentisinin sunduğu görevler SDK ve bağımlılıklar kurulunca doğrulanacaktır. Bu iskeletin henüz Android cihaz testi yapılmadı.

## Katalog ve yayın erişimi

`EAConfig.kt` içindeki TMDb token değeri bilerek boş bırakıldı. **API anahtarını veya kullanıcı çerezlerini bu public repoya commit etmeyin.** Paylaşılabilir sürüm için güvenli katalog erişimi ayrıca çözülecek. Netflix/Disney/Amazon vb. satırlar yalnız katalog sınıflandırmasıdır, bu platformların içeriklerini oynatmak için yetki vermez. Şimdiki `channels.example.json` yalnız örnek şemadır ve gerçek yayın bağlantısı içermez. İzinli kaynakların doğrulanmasından sonra adaptörler eklenecek.

## Proje ayrımı

Bu repo `TJK-BOT` ve `THORP-BIST50` projelerinden tamamen bağımsızdır. Bu projeye GitHub Actions eklenmeyecek.

## v0.2: ilk oynatma ve canlı TV bağlantısı (24.09.2026)

- `EAPlugin` yalnızca **bir** EA-FB sağlayıcısı kaydeder. Eklenti ayarlarına kişisel TMDb API Read Access Token girilir; anahtar GitHub'a gitmez, cihazdaki normal Android SharedPreferences'da (şifrelenmeden) saklanır. TMDb gerektiren kataloglar token girilene kadar boş kalabilir.
- **Big Buck Bunny (Blender Foundation)** açık lisanslı örnek film kartı ve MP4 oynatma bağlantısı eklendi. [Lisans: CC BY 3.0](https://peach.blender.org/about/). Tam filmin orijinal jeneriği korunmalıdır. Kaynak URL'nin Android cihazda güncel çalışırlığı henüz doğrulanmadı.
- `config/channels.json` kendi onaylı kanal listemiz için merkezi uç noktadır. **Başlangıçta boştur.** Her kaynak için gerçek yayın hakkı ayrı kontrol edilir ve `authorized: true` ancak onay sonrası eklenir. Aynı kanala ait alternatifler tek kartta birleşir; `.m3u8` ve `.mp4` desteklenir.
- Filmler, diziler, canlı yayınlar aynı eklenti içinde aratılır; herhangi bir ücretli platformun DRM koruması aşılmaz. Film/dizi kaynak adaptörleri henüz bağlanmadı.

### Çekirdek testler (GitHub Actions kullanmadan)

```bash
bash scripts/test-core.sh
```

Çekirdek testleri Android/CloudStream derlemesinin yerine geçmez. İlk `.cs3` Codespaces üzerinde üretildi ve v3 Android TV'de MP4 720p ile denendi; yeni kod değişiklikleri yeniden derlenip cihazda test edilmelidir.

## GitHub Actions olmadan yerel derleme

Tek eklentilik `.cs3` dosyasını Windows bilgisayarda derlemek, `dist/plugins.json` ve `dist/repo.json` üretmek için [yerel derleme talimatları](docs/LOCAL-BUILD.md) hazırlandı. `scripts/stage-release.py` yalnız gerçek Gradle çıktısından paket hazırlar; İlk `.cs3` derlemesi, GitHub üzerinden yayımlama ve v3 Android TV oynatma testi tamamlandı.

## Deneme filmi görselleri

Big Buck Bunny afişi ve arka planı Wikimedia Commons üzerinden gösterilir. Afiş: https://commons.wikimedia.org/wiki/File:Big_buck_bunny_poster_big.jpg ; arka plan: https://commons.wikimedia.org/wiki/File:Bbb-splash.png . Her ikisi de Blender Foundation'ın CC BY 3.0 eserleridir. Atıf: © Blender Foundation | www.bigbuckbunny.org .

İlk test sürümünün deneme afişi eksikti; geliştirme kodunda kart ve detay sayfası için afiş ile detay arka planı eklendi. Bu düzeltme CloudStream'e ancak yeniden `.cs3` derlenip yayımlandıktan sonra ulaşır. TMDb'ye bağlı diğer film/dizi kategorileri için cihazdaki EA-FB ayarlarına kişisel TMDb tokenı girilmesi gereklidir; henüz otomatik katalog anahtarı yoktur.

## Çift puan rozeti — IMDb ve TMDb (planlanan)

- 25.09.2026 karar güncellemesi: Film ve dizi afişlerinde **iki ayrı puan** göster: üstte sarı yıldızlı gerçek IMDb, hemen altında farklı renkli TMDb puanı. Her biri 10 üzerinden tek ondalık basamaklı. Yerleşim hedefi afişin sağ üst köşesinde kompakt iki rozet. Film detay sayfasında da kaynaklarıyla ayrı ayrı sun.
- IMDb ve TMDb puanları kesinlikle birbirinin yerine geçmez. Elde olmayan veya güvenilir şekilde doğrulanamayan puanı gizle; örnek sayı üretme. IMDb puanının gerçek veri kaynağı ve kullanım koşulları ayrıca doğrulanacak; TMDb ortalaması TMDb API'den alınabilir. Sağlayıcı sitenin etiketsiz puanını IMDb sayma.
- **Teknik kısıt:** Mevcut CloudStream kart arayüzü yalnız bir `SearchResponse.score` alanı ve tek yerleşik rozet sunuyor. Bu nedenle iki ayrı markalı rozeti doğrudan eklenti verisi göndererek oluşturamıyoruz. Seçenekler: dinamik hazırlanmış poster görselinde iki rozet (görsel kullanım koşulları, ölçekleme, önbellek ve güncellik kontrolü gerekir), ya da bir yerleşik rozet + detay sayfasında ikinci puan; gelecekteki bağımsız Android APK'da iki gerçek UI rozeti.
- İlk prototipte rozetleri dinamik görsel üstünde üretmenin güvenilirlik, görüntü kalitesi ve yükleme hızı testleri yapılacak. Bulunan film için yıl/IMDb kimliği eşleşmesi doğrulanmadan puan aktarılmayacak. Özel afiş hazırlanırsa CloudStream'in yerleşik puan rozeti aynı anda açılıp çakışmamalı.
- **Durum:** Ürün isteği kaydedildi; henüz kodlanmadı veya Android TV'de test edilmedi. Yayındaki EA-FB v3 değişmedi.

## İçerik mimarisi — 25.09.2026 son kararı (önceki metadata planlarını geçersiz kılar)

**Ürün kararı:** Ortak katalog ve görsellerin birincil kaynağı TMDb. Film/dizi siteleri, kendilerinden film başlığı/afiş/açıklama toplamak yerine **yalnızca izinli oynatma sayfası veya yayın URL'sini** çözmek için kullanılacak. Kaynak URL'leri filme güvenli şekilde IMDb/TMDb kimliği, tür ve çıkış yılıyla bağlanacak. Kaynaklar aynı oynatılabilir URL ise tekilleştirilecek; farklı altyazı/dublaj/kalite seçenekleri korunacak. Film kartı tek, kaynak seçenekleri çok olacak.

**TMDb metadata (öncelikli):** `language=tr-TR` ile Türkçe ad ve `overview`; `poster_path` ve `backdrop_path`; `images?include_image_language=tr,en,null` ile mümkünse Türkçe yazılı afiş, yoksa orijinal/İngilizce afiş; tarih/yıl, tür, oyuncular ve dizi sezon/bölüm verileri. Türkçe açıklama boşsa İngilizce alternatif, boşsa "Açıklama bulunamadı"; otomatik uydurma çeviri yok. TMDb kaynak atıf ve görsel kullanım şartlarına uy.

**Puan önceliği:** İlk afiş rozeti **gerçek IMDb puanı**. TMDb `external_ids.imdb_id` veya film detayındaki IMDb kimliği eşleştirme içindir; TMDb API IMDb *puanını* döndürmez. IMDb puanı için kullanım hakkı uygun ayrı puan API'si/verisi (örneğin uygun anahtarla OMDb) bağlanmalı. IMDb puanı bulunamazsa sahte puan gösterme. Önceki iki rozet talebi ikinci aşamada korunur: TMDb `vote_average` ayrı bir TMDb etiketiyle ikinci rozet/detay bilgisi olabilir; tek `SearchResponse.score` alanı nedeniyle yerleşik CloudStream posterinde iki rozet doğrudan mümkün değil. Veri kaynaklarını karıştırma, tarih ve önbellek tutarlılığını kontrol et.

**Netflix / Prime Video / Disney+ gibi platformlar:** TMDb `watch/providers` ile hedef ülkede hangi hizmette *mevcut olduğunu* gösterebilir ve `discover` ile platform kategorileri oluşturabilir. Bu uç nokta Netflix/Amazon'a ait tam film URL'si, katalogdaki dahili başlık kodu veya oynatılabilir yayın URL'si döndürmez. Hizmetin kendisinin kamuya açık ve izin verilen resmî başlık bağlantısı bulunursa uygulamaya yönlendirme ekle; abonelik gerektiren yayınları DRM aşmadan üçüncü taraf oynatıcıda açmaya çalışma. JustWatch kaynak atfı gereklidir. Bölgesel katalog farkları ve farklı film/dizi kimlikleri dikkate alınacak.

**Uygulama sırası:** (1) kişisel TMDb anahtarıyla Türkçe katalog/afiş/özet ve büyük görseli tamamla; (2) TMDb'den IMDb kimliğini al, ayrı uygun puan kaynağı üzerinden gerçek IMDb skorunu getir ve kartın ilk rozeti yap; (3) bölgeye göre hizmet/platform bilgilerini ve mümkünse resmî yönlendirmeleri göster; (4) izinli yayın adaptörleriyle yalnız gerçek film/bölüm oynatma bağlantılarını bulup tekilleştir; (5) ikinci TMDb rozetini yerleşim testinden sonra ekle.

**Bugünkü kod durumu:** v3'te TMDb token ayar ekranı, TMDb poster ve Türkçe `overview` çağrıları mevcut ama kullanıcının TMDb anahtarını cihaza girip katalogları denemesi henüz doğrulanmadı. Büyük TMDb arka planı, dış IMDb kimliği, gerçek IMDb puanı, platform bilgileri, gerçek film/dizi kaynak adaptörleri henüz uygulanmadı. Çalışan Big Buck Bunny açık lisanslı deneme kaynağını koru. Bu doküman değişikliği yeni bir `.cs3` üretmez ve GitHub Actions eklemez.

Kaynaklar: https://developer.themoviedb.org/reference/movie-details ; https://developer.themoviedb.org/reference/movie-external-ids ; https://developer.themoviedb.org/reference/tv-series-external-ids ; https://developer.themoviedb.org/reference/movie-watch-providers ; https://developer.themoviedb.org/reference/movie-images ; https://www.omdbapi.com/

## Film/dizi detay ve bölüm ekranları — CloudStream yerleşik arayüzü

- Kullanıcı görsel hedefi (25.09.2026): Afişe tıklanınca örnek Silo ekranındaki gibi geniş TMDb arka planı, Türkçe başlık/özet, doğrulanmış IMDb puanı ve sezon/bölüm seçimi; dizi ve filmlerde altında oyuncu görselleri/isimleri ve yönetmen/yaratıcı bilgileri. Dizinin “Bölüm” düğmesiyle CloudStream'in yerleşik sezon ve bölüm listesi açılacak: bölüm görseli, Türkçe bölüm adı/açıklaması, ilk yayın tarihi, bölüm puanı, süre; henüz yayımlanmamış bölümde tahmini/planlı yayın tarihi gösterilecek, oynatma seçeneği açılmayacak.
- CloudStream'in kendisi bunların çoğuna hazır: `TvSeriesLoadResponse.episodes`, `seasonNames`, `nextAiring`, `actors`, `backgroundPosterUrl`, `score`; `Episode.posterUrl`, `score`, `description`, `date`, `runTime`. `EpisodeAdapter`, bölüm puanını/özetini/yayın tarihini gösterir, gelecek bölümün oynat simgesini gizler; TV ayrıntı görünümü oyuncu listesini ve sıradaki yayını işleyebilir. İkinci bir ekran ve satırların el ile hazırlanması yerine **mevcut CloudStream şablonuna veri eşlenecek**. Kullanıcı örneğindeki her görsel ve tarih elle girilmeyecek.
- TMDb `GET /tv/{id}?language=tr-TR&append_to_response=aggregate_credits,images,external_ids` ile dizi üst düzey meta ve oyuncu; her sezon için `GET /tv/{id}/season/{season}?language=tr-TR` ile `episodes[]` (ad/özet/yayın tarihi/bölüm görseli/süre/`vote_average`). Film `GET /movie/{id}?language=tr-TR&append_to_response=credits,images,external_ids` ile benzer meta. `next_episode_to_air` gibi gelecekteki tarih alanı varsa sıradaki yayını aktar. TMDb'de olmayan, ertelenen veya doğrulanmayan yayına kesin tarih uydurma.
- **Önemli:** TMDb bölümdeki `vote_average` değeri IMDb bölüm puanı değildir; bölüm listesinde “TMDb bölüm puanı” diye işaretle veya CloudStream'in etiketsiz yerleşik bölüm puanını kullanırken ayrıntıda kaynak açıklaması koy. IMDb genel dizi/film puanı ayrı uygun kaynaktan gelecek. Oyuncular `actors` alanıyla, yönetmen/yaratıcı ise varsa meta/ekiple birlikte gösterilecek; CloudStream standart TV ekranının sıfır kodla özel “Yönetmen” bölümü açacağı varsayılmamalı.
- İçerik sitelerine bölüm/film başına elle başlık, afiş, açıklama, yayın tarihi girilmeyecek. İzinli adapter yalnız katalogdaki TMDb kimliği ve SxxExx eşleştirmesine göre oynatma bağlantısı verir. TMDb'de görünen ancak henüz yayınlanmamış ya da erişilebilir yetkili kaynak bulunmamış bölüm için yanlış oynatma linki sunulmaz.
- Eksik uygulama: EA-FB v3'te halen `newTvSeriesLoadResponse(..., emptyList())` var. Bu satır gerçek TMDb sezon/bölüm verileriyle değiştirilecek; TV'de çalışan örnek UI henüz EA-FB'de tamamlanmadı. Mevcut yayın v3 ve Big Buck Bunny testinin değişmemesi gerekiyor. GitHub Actions yok.

Kaynak kod referansları: https://github.com/recloudstream/cloudstream/blob/master/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt ; https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/result/EpisodeAdapter.kt ; https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/result/ResultFragmentTv.kt ; https://developer.themoviedb.org/reference/tv-series-details ; https://developer.themoviedb.org/reference/tv-season-details
