# 8501 test paketi — kurulum sınırı

Bu paket **ana Android kullanıcı profilinde çalışmaz**. Çalışan V83'ün kurulu olduğu CloudStream'e ikinci EA-FB olarak yüklemeyin; V83'ü kaldırmayın, uygulama verisini/cache'ini veya tercihlerini temizlemeyin.

Desteklenen test düzeni: Android'in ayrı kullanıcı profiline ait ayrı uygulama süreci/veri alanı, yalnız bu EA-FB test eklentisi. Cihazınızda ayrı kullanıcı profili desteği yoksa bu paketi o cihazda kullanmayın. Mi Box'ta profil desteği ve gerçek oynatma bu çalışma sırasında doğrulanmadı; paket normal Mi Box kurulumuna hazır diye sunulmuyor.

Koruma kapısı `Process.myUid()` ile ana kullanıcıyı reddeder. Kurulu CS3 manifestleri de salt okunur kontrol edilir; yeniden adlandırılmış başka `com.eafb` giriş noktası reddedilir. Bu kontroller ayar, UI hook ve provider kaydından önce çalışır. Envanter okunamazsa kabul edilmez. Bu, aynı profilde iki eklentinin güvenle çalıştığı iddiası değildir; böyle bir kurulumu başlangıçta engeller.

- InternalName: `EA-FB-SERIES-CODEX-8501-TEST`
- Provider: `EA-FB SERIES CODEX 8501 TEST`
- Sürüm: `8501`
- Tercih alanı: `ea_fb_series_codex_8501_test`
- Boş başlangıç seed alanı: `ea_fb_series_codex_8501_seed`; V83/V82 tercih alanları okunmaz veya yazılmaz.
- Yeni manifest dizini: `dist-series-codex-8501-test/`
- V83 dağıtımı: `dist-v83-test/` değişmedi.

Ayrı profilde kurulabildiği kullanıcı tarafından doğrulanırsa aynı mevcut ayar ekranından kaynakları elle açın; Organize İşler S1E8, S1E1, önceden çalışan bir film/dizi, raflar/puanlar/sezon/bölüm ve kaynak/oynatıcı seçeneklerini ayrı ayrı deneyin. Bulunan teklif, extractor linki ve gerçek görüntü/ses sonuçlarını birbirine karıştırmayın. Cihaz doğrulamasından önce "kesin düzeldi" denmiyor.

Geri alma: yalnız test profilindeki bu internalName eklentisini ve yeni test deposu URL'sini kaldırın; test uygulamasını kapatıp açın. Ana V83 profilinde hiçbir işlem gerekmez. Ardından yalnız yeni GitHub test dalı istenirse silinebilir; V83'e reset/force-push/cherry-pick uygulanmaz.
