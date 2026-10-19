# UI hook / iki eklenti risk incelemesi — yayın engeli

## Doğrudan görülen kanıt
Korunan V83 CS3 SHA-256 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66. classes.dex içindeki gerçek metot çağrıları UI-HOOK-INCELEMESI.json dosyasına yalnızca güvenli metot referanslarıyla çıkarıldı.

- FilmSeriesRail.install, DetailMetaRow.install, EpisodeUpcomingStyle.install ve EpisodeTitleStyle.install, Application.registerActivityLifecycleCallbacks çağırıyor.
- Render metotlarında apiName/provider kapıları var. Bu korumalar göz ardı edilmedi; hook'ların diğer provider ekranlarını mutlaka bozacağı iddia edilmiyor.
- Bu dört sınıfta unregisterActivityLifecycleCallbacks çağrısı görülmedi. EAPlugin kaynakta unload override tanımlamıyor. Uninstall sonrası host'un classloader/handler/listener temizliği ayrıca incelenmeli.
- Marker sabitleri ea-fb-film-series-row / ea-fb-detail-meta-* gibi sürüme göre ayrılmamış adlar taşıyor.
- Şu an yeni test kimliği üretilmedi: yerel derleme hâlâ V83 adı, STORE ve sınıf kimliklerini taşır. Bu dosyayı ikinci eklenti olarak yüklemek uygun değildir.

Bu bulgular V84 arızasının kesin nedeni değildir. Ayrı CS3/internalName veya yalnızca farklı SharedPreferences alanı yeterli izolasyon kanıtı sayılmaz. Mevcut V83 ve UI kodları byte düzeyinde korundu.

## Yayın öncesi gerekli kanıtlar (henüz çalıştırılmadı)
1. Ayrı runtime/package/provider/tercih kimliği; mevcut V83 tercihlerinin yazılmadığı ve otomatik taşınmadığı instrumentation testi.
2. Aynı Android uygulamasında iki yükleme sırası için provider kayıtlarının ve classloader çözümlemesinin doğrulanması.
3. Her dört UI hook'unun yalnızca kendi provider ekranını etkilediği, marker/view/focus/handler durumlarının karışmadığı testi.
4. Test eklentisi kaldırıldığında listener/handler referansları ile provider kaydının temizlenmesi; host restart öncesi ve sonrasında V83 rafları, detay, puanlar, sezon/bölüm, kaynak/oynatıcı ayarlarının korunması.
5. Mi Box'ta menü ve sezon/bölüm davranışı, ardından gerçek bölüm oynatması; bunlar ayrı sonuçlar olarak kaydedilmeli.

Ayrı Android kullanıcı profili/uygulama veri alanı olası bir izolasyon seçeneğidir, fakat bu cihazda kullanılabilirliği doğrulanmadı; güvenli kurulum yöntemi diye sunulmuyor.

## Karar
Kullanıcının son talimatıyla yalnızca test ve teşhis sürüyor. UI kodunda değişiklik yok. Risk çözülüp kanıtlanana kadar paket/staging manifesti yayımlanmayacak ve Mi Box kurulumu önerilmeyecek.
