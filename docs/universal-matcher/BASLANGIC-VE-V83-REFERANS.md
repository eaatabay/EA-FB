# Temiz başlangıç / V83 referansı

GitHub V83 dalı fetch edilerek gerçek SourceSearchTitles kaynakları okundu. FETCH_HEAD=16396702470eb1f81cc6dac4360e6b0ff84927d1 doğrulandı; yeni test/ea-fb-universal-matcher-v83-20261020 dalı doğrudan bu commit'ten açıldı. Worktree kullanılmadı. 8503 dalından kaynak/commit taşınmadı; raporları karşılaştırma içindir. Başlangıç çalışma ağacı temizdi. Korunan uzak ref envanteri çalışma ortamında saklandı; yayın sonunda karşılaştırılacak.

## Kaynaktan görülen akış

1. EAProvider.loadLinks PlaybackData içindeki movie/tv TMDb ID ve sezon/bölümü ayrıştırır. Etkin adaptör yoksa ağ çağrısı yapmaz. İzinli staging catalogRelay üzerinden /v1/movie/ID veya /v1/tv/ID başlık/name ve release_date/first_air_date alınır. İstemci anahtar taşımaz.
2. V83 yalnız original_title/original_name alanını alternateTitles listesine koyar. TMDb alternative_titles toplanmaz. Worker APPENDS izin listesi alternative_titles/translations içermez; ilgili bağımsız route da yoktur. Production veya staging Worker kodu bu görevde değiştirilmez.
3. Sezon/bölüm PlaybackData/MediaQuery içinde vardır. Sezon adı detay/sezon JSON'unda bulunabilir fakat loadLinks bunu sorguya aktarmıyordu. İzinli /tv/ID/season/N route kullanılabilir.
4. SourceSearchTitles başlık+alternatifleri trim/normalize dedup ile en fazla üç aday yapar. Sezon birleşimi, kanıt türü ve geniş arama ayrımı yoktur.
5. Bronze movie V83: tür, yüklenen başlık ve yıl çelişkisi, yeni varyantlar için pozitif yıl, aynı aramadaki iki sayfa reddi. Birleşik yazım, The ve kontrollü iki dilli başlık desteği vardır. Series yolu tam ad/yıl çelişkisi/tam bölüm seçer; bilinmeyen yılı sorgudan doldurur. DiziYou tam AJAX/GET adı ve bölüm kartı; DiziBox tam arama adı ile kabul eder, bölümü resolve sırasında arar.
6. DiziBox/DiziYou MediaOffer'a sorgu yılı/TMDb ID'sini kopyalar. Engine.sameContent kopyalanmış alanları bağımsız kaynak kanıtından ayırmaz. Bu yüzden adı görmenin doğrulama yerine geçtiği durumlar vardır.
7. Doğru sayfa, oynatılabilir link demek değildir: DiziBox season/episode, King/Moly/Haydi/extractor, HLS gövdesi; DiziYou iframe/playerId/seçenek/master; Bronze derlenmiş loadLinks ve LAND loopback zinciri ayrıca çalışmalıdır. Arama teklifi, resolver linki ve Mi Box playback ayrı sonuçlardır.
8. MultiSourceEngine her adaptör aramasının TAMAMI için 20 saniye sınır koyar; her aday için yeni 20 saniye başlamaz. Resolve ayrı 20 saniye bütçesidir; geç altyazı/kısmi sonuç ve cancellation korunur. Dört kaynak paraleldir.

## 8503 ile fark

8503 ConfirmedSeriesAlias kodunda tek TMDb ID/yıl/sezon/bölüm aralığına ve özel başlığa bağlı alias içerir; yeni HTML kabulü belirli JSON-LD TVSeries şemasını şart koşar. Bunlar yeni motorun temeli değildir. Kullanıcının Ted Lasso/DiziYou/LAND başarıları gerçek cihaz gözlemi olarak kabul edilir; bu ortamda yeniden oynatma doğrulaması değildir.

V83/main/arşivler, eski paket/manifester, production Worker/D1, kısa kodlar ve kullanıcı verileri korunur. UI ve player/extractor değişikliği planlanmaz. Yeni test sürümü 8601, ayrı internalName/manifest/store ile yalnız mavi STABLE içindir.
