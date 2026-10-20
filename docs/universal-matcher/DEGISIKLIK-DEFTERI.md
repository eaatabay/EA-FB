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

## İlk yayın yılı / sezon yılı ayrımı

TVSeries startDate mevcutsa belirsiz görünür .year alanının sezon yılı olması onu geçersiz kılmıyor; açık ilk-yayın etiketleri yine çelişki kontrolüne katılır. Şema yokken görünür yayın metadata'sı yolu korunur. Türkçe/orijinal alternatif sınıfları, farklı sezon adı, sezon yılı çelişkisi ve daha önce görülmemiş Latin dışı başlıklar için ek assertion'lar eklendi. Genel içeriklerin sabit TMDb/title dispatch'i olmadığını test eden tarama genişletildi.

## Yerel doğrulama ve CI/paket altyapısı
Yeni offline source/DEX guard, özel yapım koşulu taraması, ayrı test paketi ve yalnız yeni dal CI workflow eklendi. Son yerel build, 373 assertion, 32 adaptör vakası, 12 Kotlin regresyonu, V83 arşivindeki özgün testler ve UI DEX mutasyonu dahil 3 paket testi PASS. D8 sadece yeni sınıflar için synthetic lambda/backport üretti; yalnız bu yeni sınıf aileleri kaynak hashleriyle onaylandı. Eski resolver/player kodu muaf tutulmadı. Tek CI çalışması bu tamamlanmış kod için başlatılacak.

| Tam SHA | İşlem |
|---|---|
| 1e8a9668a85fceb18351616e964e123807b78e26 | docs(universal): record clean V83 baseline and general matching design |
| 998d4cc668debbf69349b4370c2b00a0b41dc193 | feat(universal): match metadata-derived titles and source identity across four adapters |
| 4c5a26b0ea1bab7f6a1290c29edb94eeb9b8ce11 | fix(universal): preserve playback query call compatibility |
| 4784668e6a42d9ea246e238bc3f0ae9b4c3e275c | fix(universal): retain existing media offer constructor ABI |
| 097cf50fee2addef6124b385d647fc3b9c73a071 | fix(universal): report ambiguous page and episode decisions without URLs |
| 14754e50262ef89af1250ef7797665ad76e83679 | fix(universal): distinguish series premiere evidence from season year |

## Başarılı CI ve 8601 test yayını
Kaynak SHA: 9d56ba95a86c93097e9761958fa406841d87ddb1; CI 39348099263 success. Paket SHA256 bf6b92ddf83392a52a92593a00d98e60e902e4451492b2baf56ecaafcab551d1. Yalnız yeni dalın dist-universal-8601-test dosyaları yayımlanır; V83/main/8503/arşivler ve production değişmez. Mi Box oynatması doğrulanmadı.

| Tam SHA | İşlem |
|---|---|
| 1e8a9668a85fceb18351616e964e123807b78e26 | docs(universal): record clean V83 baseline and general matching design |
| 998d4cc668debbf69349b4370c2b00a0b41dc193 | feat(universal): match metadata-derived titles and source identity across four adapters |
| 4c5a26b0ea1bab7f6a1290c29edb94eeb9b8ce11 | fix(universal): preserve playback query call compatibility |
| 4784668e6a42d9ea246e238bc3f0ae9b4c3e275c | fix(universal): retain existing media offer constructor ABI |
| 097cf50fee2addef6124b385d647fc3b9c73a071 | fix(universal): report ambiguous page and episode decisions without URLs |
| 14754e50262ef89af1250ef7797665ad76e83679 | fix(universal): distinguish series premiere evidence from season year |
| 9d56ba95a86c93097e9761958fa406841d87ddb1 | build(universal): verify baseline, compare regressions and package 8601 in CI |
