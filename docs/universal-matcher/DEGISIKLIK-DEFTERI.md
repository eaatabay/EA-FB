# Değişiklik defteri

Başlangıç: 16396702470eb1f81cc6dac4360e6b0ff84927d1 (GitHub V83 ile doğrulandı). Yeni dal bu commit'ten açıldı; 8503 kaynak tabanı kullanılmadı. İlk aşama: gerçek kod incelemesi ve genel tasarım. Kod değişmedi. Her sonraki kayıt bir önceki tam commit SHA'sını ve test sonucunu içerir; son kaydın kendi SHA'sı git log -1/final rapor ile bulunur.

## Ortak motor ve entegrasyon

Önceki tasarım commit'i: 1e8a9668a85fceb18351616e964e123807b78e26.
SourceTitleMatcher/SourceIdentity/MatchDecision, SourcePageIdentity ve CatalogMatchMetadata eklendi. Query'ye seçili seasonTitle; Offer'a bağımsız sourceIdentity geldi. EAProvider yalnız loadLinks metadata bölümünde değişti; UI metotları aynı. Gateway alternative_titles engeli korunuyor; canlı payload orijinal ve seçili sezon adını sağladı. SourceEngine gerçek evidence'ı tekrar doğrular; resolver/oynatma kodları aynı. DiziBox/AJAX, DiziYou/AJAX+GET ve Bronze search ortak karara bağlandı. Yeni test kimliği 8601, private store/seed; EAPlugin normal, UID/ADB/profil/marker katmanı yok.

Testler: 364 saf assertion; 32 gerçek adaptör fixture vakasında her kaynak 3/3 pozitif ve 0/5 yanlış kabul; özgün player/extractor, film, opt-in, settings, cancellation ve loopback regresyonları geçti. V83 aynı fixturelerde DiziBox 1/3 ve 5/5 yanlış kabul, DiziYou 1/3 ve 4/5, NL/LAND 1/3 ve 2/5. Kontrollü fixture ölçümüdür, saha oranı değildir. İlk eski fixture'ler yıl/sayfa başlığı içermediğinden reddedildi; pozitif fixture'lere gerçek kanıt alanları eklendi. Bilinmeyen yıl+salt film adı kabulü bilinçli olarak negatif teste çevrildi. Eski V83 testleri immutable arşivde değişmeden ayrıca geçti.

Canlı salt okunur: izinli staging katalog TV metadata'sı sezon adını doğruladı. DiziYou AJAX 200; dönen güncel URL önceki path'ten farklı olduğundan ilk capture replay mapping'i başarısızdı; gerçek AJAX href takip edilerek düzeltildi, gerçek adaptör 2 kayıtlı HTTP yanıtıyla tek S1E8 offer üretti. Resolver/extractor/video çağrılmadı. DiziBox HTTP403; canlı başarı iddiası yok. NL/LAND canlı oynatma bu ortamda denenmedi.

## Binary çağrı uyumluluğu

Ortak motor commit'i: 998d4cc (tam SHA sonraki envanterde). İlk paket DEX koruması, yeni optional seasonTitle parametresinin değiştirilmemiş PlaybackResolution çağrısının derlenmiş descriptor'ını etkilediğini yakaladı. PlaybackResolution/player dosyasını değiştirmek veya korumayı gevşetmek yerine eski PlaybackQuery.fromData imzası overload ile korundu. Yeni metadata çağrısı açık beş parametreli overload kullanır. Paket koruması tekrar çalıştırılacaktır. Bu bir local guard başarısızlığıydı; erken CI/yayın yapılmadı.

DEX guard ikinci incelemede MediaOffer'a eklenen evidence alanının kurulmamış DiziMom adaptörünün constructor çağrısını da etkilediğini gösterdi. DiziMom koduna dokunmak veya test kapsamını açmak yerine MediaOffer eski constructor descriptor'ları secondary constructor ile korundu. DTO kopyaları hâlâ evidence alanını taşır. Player ve ek kaynak algoritmaları koruma dışında bırakılmadı.

## Belirsizlik nedenlerinin loglanması

Birden fazla sayfa/bölüm retlerine AMBIGUOUS ve sabit neden kodları eklendi; URL/cookie/opaque payload loglanmaz. Arama dışındaki kod değişmedi. DTO ABI düzeltmeleri sonrası korunan mevcut runtime metotları DEX karşılaştırmasından geçti; yeni SourceIdentity boolean hashCode'u için D8'in ürettiği synthetic backport ayrıca yeni sınıf ailesi olarak doğrulanacaktır.
