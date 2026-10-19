# Tanı sonucu — yalnızca test ve teşhis

## Doğrulanan kimlik
EA-FB'nin kullandığı güncel GitHub config'i ve sabit onaylı staging relay salt okunur CI içinde okundu. Run 39311300009 success; dört katalog GET isteği 200. TV id **320754**, name/original_name **Organize İşler**, first_air_date **2026-10-16**, external_ids.imdb_id **tt38732131**, sezon 1 **Karun Hazinesi**, sekiz bölüm doğrulandı.
Aynı katalog araması **30634 / movie / Organize İşler / 2005** ve **577990 / movie / Organize İşler: Sazan Sarmalı / 2019** döndürüyor. TV/film kimlikleri birleştirilmedi. Netflix 82047949 bağlantısı kullanıcının araştırması olarak ayrıca kayıtlı; doğrudan sayfa isteği 403 olduğu için bağımsız Netflix doğrulaması iddia edilmiyor.

## Aşama ayrımı
| Aşama | Kanıt | Sonuç |
|---|---|---|
| Katalog kimliği | Gerçek EA-FB JSON'u, external_ids ve sezon | TV kimliği ve bölüm kümesi doğru |
| Arama adayı üretimi | Aynı gerçek name/original_name alanlarıyla V83 PlaybackQuery/SourceSearchTitles | İki aynı ad tek kısa ad adayına düşüyor; uzun ad iletilmiyor |
| DiziYou araması | Canlı POST data_fetch HTTP 200 | Sonuç var: Organize İşler: Karun Hazinesi |
| Başlık filtresi | Gözlenen adların değiştirilmemiş V83 normalize kapısıyla karşılaştırılması | Kısa aday uzun sonucu reddediyor |
| Kaynak seri kimliği | Canlı DiziYou h1 ve TVSeries/startDate=2026 | Uzun ad ve yıl görülüyor; wrapper'ın genel tip/yıl doğrulama garantisi sayılmaz |
| Bölüm mevcudiyeti | Canlı DiziYou sayfasında S1 E1..8 etiketleri | İstenen bölüm etiketi mevcut; extractor başarısı değildir |
| Diğer kaynaklar | Gerçek dört adapter üzerinde kontrollü fixture'lar | Aynı ad kapısı davranışı üretildi; canlı DiziBox/Bronze sonucu iddia edilmiyor |
| Extractor / stream / Mi Box | Çalıştırılmadı | Bilinmiyor |

Bu, DiziYou için katalogdan kaynağa uzanan ad uyuşmazlığının somut kanıtıdır. Cihazdaki tüm başarısızlıkların tek nedeni veya dört kaynağın tamamında aynı canlı durum olduğu söylenmez. Açıkça verilen uzun ad, V83'ün mevcut aday arama yolu ile dört adapter fixture'ında çalışıyor. Bu nedenle filmin gevşek kelime eşleştirmesini tüm dizilere kopyalamak gerekmiyor.

## Test / cihaz ayrımı
- Son tanı programı: **59/59 PASS**, kaydedilmiş gerçek güvenli katalog/kaynak metadatası + sentetik adapter yanıtları. Oynatılabilir stream üretilmedi.
- İlgili V83 regresyonları: 12 Kotlin programı ve 14 Python paket/koruma testi PASS. Yeni Python tanı/koruma kontrolleri: 5 test PASS.
- Run 39309504108: success, tam loglar incelendi; 45 ilk tanı assertion'ı, derleme ve koruma geçti. D8 Kotlin metadata rewrite uyarıları (206) raporlandı; gizlenmedi.
- Run 39310538425: success, ayrıntılı loglar incelendi; 53 kimlik tanı assertion'ı ve Android BUILD SUCCESSFUL.
- Run 39311300009: success, gerçek EA-FB katalog kimliği doğrulandı; log arşivinden güvenli JSON kaydedildi.
- V83'ün 312 mevcut dosyası tamamen aynı; mevcut paket SHA-256 aynı.

## Şimdi yapılmayanlar ve sonraki sınır
Kullanıcının son talimatıyla otomatik/genel veya hedefli runtime eşleştirme kuralı eklenmedi. UI/SourceSearchTitles/EAProvider/adapter/oynatıcı/ayar/kimlik kodları değiştirilmedi. Yeni paket/tercih deposu/sürüm atanmadı; staging yayını veya Mi Box kurulumu yok.
UI-HOOK-RISKI.md içindeki iki eklenti / unload / provider / tercih izolasyonu kapıları henüz çözülüp kanıtlanmadı. Bu yüzden yayın engeli sürüyor. Gelecek kod çalışması, gerekirse yalnız doğrulanmış TV kimliği + yıl + sezon kapsamına daraltılmalı; yeni kabul edilen kaynak sayfalarında içerik türü, yıl, sezon/bölüm ve belirsizlik denetimleri ayrıca korunmalı. DiziBox/DiziYou'nun mevcut genel arama kodunda bu garantilerin tamamının bulunduğu varsayılmamalı.
