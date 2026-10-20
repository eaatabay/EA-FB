# Alternatif başlık erişimi için uygulanmamış staging önerisi

Mevcut V83 Worker APPENDS movie/tv izin listesi alternative_titles ve translations içermiyor; bağımsız alternative_titles route da yok. Bu görevde Worker/D1/config dağıtımı değiştirilmedi. Gerçek izinli detay yanıtında original_name/original_title ve seasons alanları var; engine bunları kullanıyor. CatalogMatchMetadata alternatif başlık alanını sağlandığında okuyabilir, fakat sunucuda olmayan veriyi varmış gibi raporlamıyoruz.

İleride açık staging onayıyla yalnız staging gateway'de movie/tv alternative_titles append izni, schema/ID/type kontrolü, response alanı sınırı/cache ve mevcut gateway negatif testleri değerlendirilebilir. Production'a taşınması ayrıca onay gerektirir. İstemciye TMDb/OMDb anahtarı eklenmez. Bu öneri uygulanmış bir değişiklik değildir ve 8601 test kurulumu için kullanıcıya yeni adım yüklemez.
