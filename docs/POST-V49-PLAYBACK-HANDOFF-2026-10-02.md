# EA-FB — V49 sonrası oynatma işine dönüş (2026-10-02)

Bu belge, kullanıcının 02.10.2026 tarihli sohbet özetindeki oynatma kararlarının `develop/post-v49` dalındaki gerçek dosyalarla karşılaştırılmasıdır. V49 arşivi ve test paketi değiştirilmez. Eski `feature/detail-dual-ratings-v6` dalı bu aşamanın geliştirme hedefi değildir.

## Kronoloji

1. Sayfa/detay tasarımı ve bölüm yerleşimi geliştirildi.
2. Cennetin Doğusu hatasından ÖNCE merkezi başarılı-kaynak hafızası, onaylı kaynak önceliği, otomatik yedekleme, HMAC'li iç gözlemci kanıtı ve D1 saklama/temizleme kodları yazıldı. Önceki parkur raporu: temel `44de0956...`, son `39b25ad4...`; rapor kod/test ekleme içindi, canlı oynatma veya yayın kanıtı değildi.
3. Sonraki bölüm tarihi konusu araya girdi; V49'da kullanıcı Mi Box'ta geçmiş tarihli göstergeyi ve Türkçe bölüm başlığı düzeltmesini doğruladı.
4. V49 ayrı `archive/frozen-v49-20261002` dalında korunuyor. Geliştirme yalnız bu dalda sürüyor.

## Bugün dosya bazında doğrulanan mevcut altyapı

- `PlaybackData.kt`, `PlaybackQuery.kt`: film ve kesin sezon/bölüm kimlikleri.
- `SourceEngine.kt`: `MultiSourceEngine`, kesin bölüm eşleştirme, öncelikli kaynağı deneme, boş/hata/timeout sonrası diğer uygun teklife geçme; yalnızca çözümleme, oynatmanın başladığı kanıtı değil.
- `source-watchdog/migrations/0003_playback_success.sql`, `0004_playback_retention.sql`, `0005_playback_observer_receipts.sql`: merkezi aday, süre temizleme indeksi, tek kullanımlık olay fişi şemaları.
- `playback-success.mjs`, `playback-candidate-service.mjs`, `trusted-playback-recorder.mjs`, `playback-observer-proof.mjs`, `playback-observer-service.mjs`, `playback-retention.mjs`: özel/iç başarı hafızası ve doğrulama bileşenleri. Gerçek oynatma URL'si, token, çerez veya cihaz kimliği merkezi hafızaya yazılmayacak.
- `scripts/test-playback-v6.sh` ve `docs/PLAYBACK-CENTRAL-SUCCESS-RELEASE-GATE-v6.md`: test çalıştırıcısı ve açık yayın kapıları.
- `EAProvider.kt` içindeki `loadLinks`: bugün yalnız Big Buck Bunny örneği ve izinli canlı yolunu döndürüyor; normal `ea-fb:movie:` / `ea-fb:episode:` kimliklerini `PlaybackQuery` + `SourceEngine` ile gerçek adaptörlere bağlamıyor.

## Kararlaştırılan davranış

1. Gerçekten başlayan film/bölümün **kaynak kimliği ve varyantı** kısa süreli aday olarak saklanabilir. “Bağlantı bulundu” = “oynatma başladı” değildir.
2. Sonraki kullanıcı aynı kaynağı öncelikli deneyebilir; **oturumuna özgü taze bağlantıyı yeniden çözümler**. Başarı garantisi değildir.
3. Yalnız güncel olarak sağlıklı, açık, ayrı incelemeden geçmiş ve kullanım izni bulunan kaynaklar denenir; tarihsel başarı izinsiz/bozuk kaynağı etkinleştiremez.
4. Öncelikli kaynak boş/hatalı/zaman aşımına uğrarsa diğer uygun kaynaklar denenir. Başarısızlık yalnız ilgili film/bölüm/kaynak/varyant adayını etkiler.
5. Oynatma kanıtı bağımsız, güvenilir gözlemci tarafından oluşturulmalı; HMAC doğrulayıcı tek başına video başladığını kanıtlamaz. İmza anahtarı CS3'e konmaz.
6. Kaynak seçimi ve bağlantı sunumu sonrasında kaynak bekçisi ve admin paneli bağlanır. Üretim Worker/D1, mavi V5/main ve donmuş V49 değiştirilmez.

## Kalan işler — doğru sıra

- [ ] A. Normal film/bölüm için `loadLinks` ile `PlaybackQuery` ve onaylı adaptör çözümleme yolunun güvenli bağlantısını tasarlayıp kodlamak; geçerli hak/izin ve gerçek adaptör olmadan canlı üçüncü taraf bağlantısı uydurmamak.
- [ ] B. Yalnız onaylı kaynak kimliklerini döndüren, kimlik doğrulamalı, süre/sıklık sınırları olan **özel aday okuma** sözleşmesi; uygulamada kaynak aç/kapat ve kullanıcı dil/kalite tercihi.
- [ ] C. Cihazda taze URL çözümleme, ilk uygun kaynak, alternatifler ve manuel kaynak seçimi; dublaj/altyazı/kalite meta verisi ve gerçekten oynatma testi.
- [ ] D. Güvenilir player-start gözlemcisi ve özel başarı yazımı; sahte/replay/eskimiş olay testleri. Doğrulanmamış istemci olayıyla merkezi başarı yazılmamalı.
- [ ] E. İzole staging D1'de 0003–0005 migration ve gerçek Wrangler/D1 testi; otomatik süre temizliği, kaynak bekçisi ve admin yönetim akışı.
- [ ] F. Tam checkout Node/Python/Kotlin testleri, Android/Gradle CS3 üretimi, sonra Mi Box'ta gerçek film ve bölüm kabul testi. Kod, test, paket, yayın ve cihaz doğrulaması ayrı raporlanır.

**Şu anki kesin durum:** A–F henüz bitmiş değil. Kaynak motoru ve merkezi hafıza temeli var; normal içerikte `loadLinks` bağlantısı, canlı izinli adaptörler, gerçek oynatma kanıtı, uçtan uca test/yayın yok. Bu belge kod veya CS3 sürüm değişikliği değildir.
