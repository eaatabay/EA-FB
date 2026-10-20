# Ortak aday ve kimlik değerlendirme motoru

A: Catalog metadata başlık/orijinal/varsa doğrulanmış alternatifler, ilk yayın/vizyon yılı ve seçilen sezon adını MediaQuery'ye taşır. Dizi yılı sezon yılı ile değiştirilmez. Gateway alternative_titles erişimini engelliyorsa ek API tahmini/anahtar/Worker değişikliği yapılmaz; alan sağlanırsa parser kullanır, mevcut canlı erişim sınırlaması raporlanır. Sezon adı önce mevcut seasons listesinden, gerekirse izinli seçili sezon route'undan sınırlı sürede alınır.

B: SourceTitleMatcher deterministik en fazla altı arama adayı üretir: doğrudan adlar, anlamlı seçili sezonla birleşimler, güvenli normalizasyon, en sonda kontrollü geniş arama. Genel sezon etiketleri birleştirilmez. Geniş arama metni kabul edilebilir alias sayılmaz. Token/sayılar kaybedilmez; filmde V83'ün kontrollü birleşik yazım/The/sıra ve iki dilli ayraç kuralları pozitif yıl ile korunur; dizilere gelişigüzel kelime sırası kuralı uygulanmaz.

Kaynak kanıtı katalog kimliğinden ayrıdır: gerçek sayfa/API adı, yapım türü, ilk yayın/vizyon yılı, varsa dış ID ve gerçek sezon/bölüm. JSON-LD yalnız kanıt yollarından biridir; görünür başlık, yayın yılı alanları ve bölüm kartları da kullanılır. Sayfanın her yerindeki ilk dört haneli sayı yıl sayılmaz; oyuncu biyografisi/bölüm tarihi yıl yerine geçmez. Çelişen kanıt reddedilir, sorgu yılı kaynak yılı diye doldurulmaz.

Kararlar EXACT / VERIFIED_ALIAS / CORROBORATED / AMBIGUOUS / REJECTED. Bilinen tür/yıl/ID/sezon/bölüm çelişkisi reddedilir. Bilinmeyen yıl otomatik eşit sayılmaz; yeterli ek kimlik yoksa AMBIGUOUS. İki olası sayfa/bölüm sonuç sırasına göre seçilmez. Karar logu yalnız provider, sınıf ve neden taşır; URL/cookie/token/payload yazılmaz.

Adaptörler mevcut HTTP/AJAX/cookie/referer zincirlerini tutar; yalnız discovery/doğrulama sınırında ortak motoru çağırır. DiziBox bölüm tespiti search aşamasında mevcut helper ile kanıtlanır; King/Moly/Haydi ve resolver metotları değiştirilmez. Bronze orijinal DEX değişmez; Kotlin wrapper arama aşaması ortak kararı uygular. SourceEngine gerçek evidence içeren teklifleri tekrar ortak karardan geçirir. Oynatma zinciri, link kabulü ve UI aynı kalır.

Bütçe: 6 arama adayı, sınırlı sonuç/sayfa sayısı; tek 20 saniyelik adaptör search timeout'u korunur, 6x20 saniye yapılmaz. Sezon metadata fallback'i ayrıca kısa bir timeout alır; gateway mevcut cache kullanılır, kullanıcı verisi yazılmaz. Aday/kimlik kararları saf Kotlin test edilir. V83 ile aynı kayıtlı senaryolarda doğru kabul/yanlış kabul/yanlış ret ve ağ çağrı sayısı karşılaştırılır. Süre fixture ölçümü gerçek internet/Mi Box performansı diye sunulmaz.

Bilinmeyen rastgele başlık/ID/sezon testleri ve runtime kaynaklarında özel film/dizi literal/ID kuralları taraması zorunludur. Hiçbir cihaz yükleme kapısı, ADB/Python/UID/profil/marker şartı eklenmez.
