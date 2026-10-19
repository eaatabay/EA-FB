# Geri alma planı

- Yeni dal: `test/ea-fb-series-name-codex-20261019`.
- Temel V83 commit: `16396702470eb1f81cc6dac4360e6b0ff84927d1`.
- Yeni commitler: bu dalda `git log --reverse 16396702470eb1f81cc6dac4360e6b0ff84927d1..HEAD`; tamamlanan aşamalar DEGISIKLIK-DEFTERI.md içinde.
- Yeni iş akışı: henüz yok. Paket kimliği / ayrı tercih deposu: henüz atanmadı.
- Staging manifest / CS3: henüz oluşturulmadı veya yayımlanmadı.
- Silinebilir çalışma alanı: yalnızca docs/series-name-codex ve ileride bu defterde açıkça belirtilen yeni test dosyaları; korunan klasörleri silme.
- Korunan V83: dist-v83-test/ tamamı, tüm V83 commitleri ve uzak dal; main/Blue V5, V81/arşivler, production Worker/D1, manifest/kısa kodlar, mevcut workflow'lar ve kullanıcı ayarları.

## Kullanıcı denemeyi kaldırmak isterse

1. Mi Box'a henüz hiçbir test yüklenmedi. İleride paket verilirse yalnızca ayrı test kimliğini eklenti yöneticisinden kaldır; V83'ü kaldırma, uygulama verisini temizleme. Aynı uygulama içinde iki EA-FB'nin birlikte güvenli çalışması doğrulanmadığı için kurulum önerilmeyecek.
2. Test deposunun URL'sini test yapılan ayrı uygulama/profilden kaldır. V83 depo URL'sine dokunma.
3. Yeni daldaki ilerlemeyi gerekiyorsa arşivle; yalnızca bu tam dal adını sil: `git push origin --delete test/ea-fb-series-name-codex-20261019`. V83 üzerinde reset/force-push/cherry-pick/revert çalıştırma.
4. `git ls-remote --heads origin` sonuçlarını UZAK-DALLAR-BASLANGIC.tsv ile karşılaştır. Bu yeni dal ve kullanıcı tarafından silinmesi istenen eski başarısız dal dışında hiçbir HEAD değişmemeli.
5. V83 paket hash'inin 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66 ve V83 HEAD'in 16396702470eb1f81cc6dac4360e6b0ff84927d1 kaldığını doğrula.

Eski başarısız test/organize-isler-season-title-20261019 dalı ancak V83 ile aynı HEAD ve korunacak bağlı iş bulunmadığı doğrulandıktan sonra tek başına silinir. API erişimi şu anda engelli olduğundan bu adım bekliyor.
