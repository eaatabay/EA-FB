# EA-FB — Tek CloudStream Eklentisi

**Dağıtımdaki sürüm: v3. GitHub kodunda yeni v5 public katalog altyapısı hazır; Cloudflare dağıtımı, derleme ve Mi Box testi bekliyor.**

## Hiçbir kullanıcıdan TMDb anahtarı istenmeyen sistem

- TMDb Read Access Token yalnız Cloudflare Worker secret olarak saklanır; ne GitHub public koduna ne de indirilebilir .cs3 içine girer.
- Mi Box ve başka cihazlar aynı GitHub CloudStream deposundaki sürümü yükler ve normal eklenti güncellemesiyle yeni sürümü alır.
- Eklenti sadece config/backend.json içindeki herkese açık Worker URL'sini okuyup TMDb film, dizi ve bölüm metadatasını oradan alır.
- Codespaces secret yalnızca ilk Worker kurulumunda Cloudflare secret'ına otomatik aktarılır. Bu, TJK'deki gibi çalışma anında sunucuya tanımlı gizli anahtardır.
- Worker URL'si herkese açıktır. İstek yolları sınırlı, önbellekli ve istek limitlidir; anahtarı açıklamaz ama tüm olası kötüye kullanımı engellediği iddia edilmez.

## Bir kez kurulum, ardından GitHub güncellemesi

Codespaces'te hâlihazırda TMDB_READ_ACCESS_TOKEN tanımlı. Bir Cloudflare hesabı ile bağlantı kurulduğunda scripts/deploy-and-publish.sh Worker'ı dağıtacak, secret'ı aktaracak, sağlık kontrolünü yapacak, yalnız public URL'yi GitHub'a yazacak ve temiz v5 .cs3 dosyasını public dist/ içine derleyip yayımlayacak. GitHub Actions veya her cihazda anahtar girmek yok.

## Kapsam

- Yayındaki v3: Big Buck Bunny CC BY 3.0 afişi ve MP4 720p oynatma TV'de doğrulandı.
- v5 kaynak kodu: Türkçe TMDb film/dizi detayları, oyuncu fotoğrafları, yaratıcı/yönetmen, önerilenler, otomatik sezon/bölüm verileri ve planlanan yayın tarihleri. Mi Box v5 testi henüz yapılmadı.
- Gelecek işler: gerçek IMDb puanı için ayrı kaynak, afişlerin üzerinde yıl rozeti, ayrı Serinin Filmleri şeridi, video kaynak adaptörleri. TMDb bölüm puanı IMDb diye gösterilmez.
- Film/dizi metadata kartının bulunması oynatılabilir bir yayın kaynağı olduğu anlamına gelmez. Abonelik erişimi veya DRM koruması aşılmaz.

**TMDb attribution:** This product uses the TMDB API but is not endorsed or certified by TMDB. JustWatch kaynaklı platform verileri ayrıca ilgili atıf koşullarına tabidir.

Eski v3/v4 ayrıntılı plan ve kararlar: docs/ARCHIVE-2026-09-25.md.
