# EA-FB — tek CloudStream eklentisi

**Durum: geliştirme temeli (v0.1), henüz kurulabilir `.cs3` veya çalışan yayın kaynağı yok.** Bu repo, film/dizi katalogları ve izinli canlı TV yayınlarını tek görünen `EA-FB` eklentisinde toplama amacıyla oluşturuldu.

## Kapsam

- Tek eklenti (`EAPlugin`) ve tek görünür CloudStream sağlayıcısı (`EAProvider`). Film, dizi ve canlı TV için ayrı eklentiler kurulmaz.
- Paylaşılan ana sayfadaki **28 kategori için veri tanımları**. Şu an TMDb yolu tanımlı kategoriler için API çağrısı iskeleti var; izlemeye devam et ve özel listeler henüz çalışmıyor.
- Film/dizi kimliklendirme, kanal adlarını normalleştirme, aynı kanalı birleştirme, alternatif linkleri tekilleştirme ve önceliklendirme için saf Kotlin modelleri.
- İleriki hedef: tek film kartında çoklu yetkili yayın, tek kanal kartında alternatif izinli canlı TV akışları, dil/kalite seçenekleri, otomatik kaynak seçimi. Güncel kodda **gerçek yayın bağlantısı/izleme yok**.
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
