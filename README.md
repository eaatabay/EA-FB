# EA-FB — tek CloudStream eklentisi

**Durum: geliştirme ön izlemesi (v0.2), henüz kurulabilir `.cs3` veya Android cihaz testi yok.** Bu repo, film/dizi katalogları ve izinli canlı TV yayınlarını tek görünen `EA-FB` eklentisinde toplama amacıyla oluşturuldu.

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

Bu testler **Android/CloudStream derlemesi veya gerçek yayın testi yerine geçmez**. Kurulabilir `.cs3` için Gradle ve Android SDK ile yerel derleme ayrıca yapılacak.

## GitHub Actions olmadan yerel derleme

Tek eklentilik `.cs3` dosyasını Windows bilgisayarda derlemek, `dist/plugins.json` ve `dist/repo.json` üretmek için [yerel derleme talimatları](docs/LOCAL-BUILD.md) hazırlandı. `scripts/stage-release.py` yalnız gerçek Gradle çıktısından paket hazırlar; Android cihaz testi ve gerçek `.cs3` derlemesi henüz tamamlanmadı.

## Deneme filmi görselleri

Big Buck Bunny afişi ve arka planı Wikimedia Commons üzerinden gösterilir. Afiş: https://commons.wikimedia.org/wiki/File:Big_buck_bunny_poster_big.jpg ; arka plan: https://commons.wikimedia.org/wiki/File:Bbb-splash.png . Her ikisi de Blender Foundation'ın CC BY 3.0 eserleridir. Atıf: © Blender Foundation | www.bigbuckbunny.org .

İlk test sürümünün deneme afişi eksikti; geliştirme kodunda kart ve detay sayfası için afiş ile detay arka planı eklendi. Bu düzeltme CloudStream'e ancak yeniden `.cs3` derlenip yayımlandıktan sonra ulaşır. TMDb'ye bağlı diğer film/dizi kategorileri için cihazdaki EA-FB ayarlarına kişisel TMDb tokenı girilmesi gereklidir; henüz otomatik katalog anahtarı yoktur.

## IMDb puanları — afiş sağ üst köşe (planlanan)

- Ürün kararı (25.09.2026): Film ve dizi afişlerinde sağ üst köşede 10 üzerinden gerçek IMDb puanı gösterilecek; veri yoksa rozet boş kalacak. TMDb ortalaması asla IMDb etiketi altında gösterilmeyecek.
- CloudStream uygulamasında afiş kartları `SearchResponse.score` alanını `Score.from10(...)` ile okuyabiliyor ve afişin sağında yerleşik puan rozeti var. Kullanıcının CloudStream afiş ayarlarında puan görünürlüğü açık olmalı. Yerleşik rozet yalnız sayı gösterir; görsel üzerinde ayrıca IMDb markalı rozet istenirse özel görsel işleme ya da bağımsız APK gerekip gerekmediği ayrıca değerlendirilecek.
- Bağlanacak ve kullanım izni doğrulanmış site adaptörleri IMDb kimliğini ve varsa gerçek IMDb puanını ayrı alanlarda getirmeli. Başlık+yıl+IMDb kimliği eşleşmesi olmadan yanlış filmin puanı aktarılmamalı. Birden çok adayda kaynağı ve güncellik tarihini saklayıp güvenilir değeri seç; ortak kaynak puanları ayrı tut.
- Film kartı ve detay sayfası aynı doğrulanmış IMDb puanını kullanmalı. Büyük tanıtım alanında puan için ek UI ihtiyacı gerçek cihaz testinden sonra değerlendirilecek.
- **Durum:** İstek kaydedildi, kaynak adaptörleri ve puan aktarımı henüz kodlanmadı. Yeni sürüm derlenmedi; mevcut yayın v3 ve Big Buck Bunny deneme filmi çalışıyor.
