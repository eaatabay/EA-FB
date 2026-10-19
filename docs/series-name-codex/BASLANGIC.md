# Başlangıç doğrulaması — 2026-10-19 (Europe/Istanbul)

- V83 dalı: test/ea-fb-v83-bronze-search-fix; gerçek GitHub HEAD: 16396702470eb1f81cc6dac4360e6b0ff84927d1.
- Yeni dal: test/ea-fb-series-name-codex-20261019; dalın ilk HEAD'i: 16396702470eb1f81cc6dac4360e6b0ff84927d1. V83'ten doğrudan açıldı; V84 kodu kullanılmadı.
- Başlangıç farkı: boş (git diff 16396702470eb1f81cc6dac4360e6b0ff84927d1); çalışma ağacı temizdi.
- V83 CS3: dist-v83-test/EA-FB-V83-TEST.cs3.
- SHA-256: 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66 (yerel gerçek byte kontrolü).
- main HEAD: d7de664092a9236da477783f4cc35b5f7bf895d3.
- V81 arşiv HEAD: 88d7966fedf5150c38e4c898da86c482f1284b70.
- Eski başarısız dal mevcut; HEAD V83 ile aynı. GitHub PR/dependent-work sorgusu ağda Forbidden olduğu için bağlı işlerin yokluğu henüz doğrulanmadı; silinmedi.

## İlk kod bulguları ve kanıt sınırı

EAProvider.loadLinks() metadata name/original_name'i PlaybackLinkBridge'e iletir; SourceSearchTitles en fazla üç aday döndürür. Bronze seri yolu search ve load adında tam normalize eşleşme, yıl çelişkisi kontrolü, TvSeriesLoadResponse ve tam sezon/bölüm filtrelemesi kullanır. DiziYou ve DiziBox kendi tam ad kapılarına sahiptir. Karun Hazinesi başlığının katalogda/alternatif adlarda bulunduğu henüz kanıtlanmadı. Cihaz logu yok; bağlantı bulunamadı hatasının kesin aşaması bilinmiyor.

V83 film çözümünden yalnızca doğrulanmış adaylar, tip/yıl/episode kontrolü, belirsizlik reddi ve güvenli tanı ilkeleri uyarlanabilir. Filmdeki kelime sıralama / The kaldırma kuralları dizilere otomatik aktarılmayacak. Sabit Organize İşler -> Karun Hazinesi tahmini eklenmeyecek.

EAPlugin dört global UI lifecycle hook'u kuruyor. Ayrı paket adı, aynı Android uygulamasında eşzamanlı güvenli çalışmayı kanıtlamaz. UI kaynakları korunacak. Aynı CloudStream kurulumunda V83 ile birlikte kurulum önerilmeyecek; ayrı Android kullanıcı profili veya ayrı uygulama veri alanı gerekecek. Mi Box uygunluğu ayrıca kullanıcı tarafından doğrulanmalı.
