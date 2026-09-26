# EA-FB v6: main detail title ratings (feature branch)

> **25.09.2026 update:** The first v6 candidate was compiled locally and tested on a Mi Box. A subsequent source-only update added the navy/yellow settings dialog, persistent categories, discovery sorting and duplicate-rating cleanup. **That updated package has NOT been rebuilt, deployed or installed.** Earlier dated notes below describe the pre-test development stages; current scope and remaining work are in [MI-BOX-REVIEW-AND-SETTINGS-v6.md](MI-BOX-REVIEW-AND-SETTINGS-v6.md).

Status: **source only**, NOT compiled or published. Production `main/dist` remains EA-FB v5.

## Requested UI and native CloudStream constraints

User wants film/series ratings near the release year (Silo: 2023): separate **IMDb** and **TMDb** scores, no mislabeling.

CloudStream's ordinary `MovieLoadResponse` and `TvSeriesLoadResponse` expose **one** native `score` next to other title metadata. Plugins cannot demand two independent same-row native rating badges. v6 therefore sets native score to independent IMDb when available, otherwise TMDb, and prints the explicitly labeled **IMDb x.x/10 | TMDb y.y/10** line immediately before the Turkish plot. A custom CloudStream app/UI modification would be necessary to guarantee two same-row badges.

## Real data provenance

- **TMDb**: `vote_average` only when `vote_count>0` from the TMDb detail response. The CloudStream episode scores in v5/v6 are also **TMDb's episode `vote_average`**, only for episodes with votes; the current description explicitly says `Bölüm puanı: TMDb`.
- **IMDb**: never derive from TMDb rating. TMDb `external_ids.imdb_id` identifies a movie or series only. Server-side Cloudflare Worker optionally requests **OMDb API** by a validated IMDb title ID using secret `OMDB_API_KEY`. Only a verified score with matching ID and `Response=True` is returned as `ea_fb_ratings.imdb`.
- No IMDb score is displayed when OMDb is unconfigured, fails, rate limited, or does not have a score. Native score and labeled line still show TMDb when available.
- OMDb API's free key currently has a **1,000/day** limit across our entire public service. Cloudflare caches the enriched title detail, but a public rollout may require higher quota. Review OMDb's applicable licensing/attribution terms before scaling. Neither the OMDb nor TMDb key goes to a user/device or public `.cs3`.

## Release checklist

1. Finish source and compile v6 in Codespaces **without overwriting main's stable v5**. Run `node --test worker/test/catalog.test.mjs` first, compile the CloudStream package, and verify the manifest says v6.
2. Owner obtains an OMDb key from https://www.omdbapi.com/apikey.aspx and stores it as a **Cloudflare Worker secret** (`OMDB_API_KEY`) for `ea-fb-catalog`; **do not** paste secrets into chat or commit them. No end user enters any key.
3. Deploy this branch's updated Worker source to the existing Cloudflare Worker.
4. Probe `/v1/tv/125988?append_to_response=external_ids&language=tr-TR` and verify the response has independently sourced IMDb and TMDb ratings; validate real live title ID before relying on this fixture. Do not publish if either is missing.
5. Release only after v6 Android/CloudStream compilation and successful live Worker check; then update GitHub `dist` and test on Mi Box. Existing v5 remains live until then.

## Extra display items being tracked independently

Homepage lower categories with empty cards or paging/navigation problem, simpler future-episode countdown, per-poster year and score overlays and genuine film/video adapters remain separate tasks. Do not conflate them with the dual-title-rating feature.

## Film serileri / koleksiyonlar (aynı geliştirme dalında)

25.09.2026 Mi Box fotoğrafı: 2026 tarihli Örümcek Adam filmi detayında oyuncuların altında “Çok yakında...” var, “Önerilen” altında başka filmler var ama ayrı “Serinin Filmleri” rafı yok. Bu eski v5'te hiç uygulanmamıştı.

- v6 feature branch'inde film `belongs_to_collection.id` kimliği okunuyor; Worker'ın zaten izin verdiği `/v1/collection/{id}` TMDb kaynağı çağrılıyor.
- Resmî koleksiyonun parçaları `release_date` ile eski→yeni sıralanıyor; filme ait koleksiyon en az iki üye içermiyorsa seri eklenmiyor. Gösterilen film dâhil tutuluyor; aynı film iki kez gösterilmiyor.
- Gelen koleksiyon filmleri stok CloudStream'in **mevcut tek “Önerilen” rafının başına** ekleniyor, normal öneriler daha sonra geliyor. Açıklamada ayrıca “Serinin Filmleri (vizyon tarihine göre)” listesi açıkça etiketleniyor. **Bu, istenen bağımsız, “Önerilen” üzerinde kendi başlıklı yatay rafın yerine geçmez.** Stok `MovieLoadResponse` ayrı başlıklı ikinci raf alanı sağlamıyor; kesin aynı tasarım ayrı CloudStream uygulama/UI değişikliği gerektirir. Kullanıcıdan bu fark gizlenmemeli.
- Resmî TMDb koleksiyonu başka Spider-Man yeniden çevrimlerini kendiliğinden birleştirmez. Farklı evrenleri tek büyük listede toplamak ayrıca belirlenmiş manuel veya düzenleyici veri gerektirir.
- Ekrandaki “Çok yakında...” başlığı koleksiyon rafı değildir. v5 film verisi metadata-only'dir; film oynatma kaynağı henüz sağlanmaz. Başlığı “Serinin Filmleri” olarak değiştirmek mevcut API'de mümkün değildir.
- Kod GitHub feature branch'ine kaydedildi; **Kotlin Android derlemesi ve Mi Box görsel testi yapılmadı, main'deki v5 değiştirilmedi.**

## 25.09.2026: Ana sayfa büyük/küçük afiş puanları

- Kullanıcının açık isteği: **ana sayfadaki büyük film/dizi afişlerinde iki ayrı etiket (IMDb + TMDb), küçük afişlerde sığarsa iki etiket sol/sağ, sığmıyorsa bir gerçek puan.**
- Mevcut CloudStream TV ana sayfasında tek yerleşik sayısal `score` ve ayrı `tags` çipleri bulunuyor. v6 kaynak kodu, büyük afişin detay verisine gerçek IMDb geldiğinde **IMDb ve TMDb çiplerini türlerden önce** koyuyor. Yerleşik sayısal puanda IMDb varsa IMDb, aksi durumda TMDb yer alıyor. *Cihazdaki tam görsel yerleşim derleme sonrası test edilmelidir.*
- `SearchResponse` küçük kartlarına TMDb listelerinden gelen, oylaması olan **gerçek TMDb puanı** atandı; CloudStream uygulamasında "puanları göster" ayarı açık olduğunda tek yerleşik sayısal rozet görünür. IMDb puanı kategori listelerinde bağımsız kaynaktan gelmediği için `IMDb` gibi yanlış etiketlenmez; toplu sayıda OMDb isteği yapılmaz.
- CloudStream'in stok küçük poster kartı iki bağımsız sol/sağ puan rozeti sağlamıyor. İki bağımsız rozet veya posterin üzerinde yıl bindirmesi için ayrı görüntü işleme ya da CloudStream UI özelleştirmesi gerekir. Kullanıcı bir puana açıkça izin verdiğinden mevcut güvenilir tercih **tek TMDb puan rozeti**. Ayrı IMDb kaynağı gerçek veriyle sunucu tarafında hazır olduğunda büyük afişte ikinci puan görünür.
- v6 henüz **derlenmedi ve Mi Box'ta doğrulanmadı**. OMDb Worker secret `OMDB_API_KEY` henüz kurulduğu doğrulanmadı. Public v5 değiştirilmedi.

## 26.09.2026 — Ucuncu offline parkur: ikinci puan kesinti dayanimi

- TMDb detay yaniti, OMDb gecici 503/ag/JSON/kimlik hatasi nedeniyle kaybolmaz. OMDb-mode cache'in TMDb-only gecici yaniti **60 saniye** saklanir; uzun sureli normal cache TTL'siyle IMDb puaninin saatlerce gorunmemesi onlenir.
- OMDb `Response=True` + tam IMDb ID eslesmesi + `imdbRating=N/A` gercek bir "puan yok" yanitidir: normal detay cache TTL'si kullanilir. Gecersiz sayi, 0/11, bilimsel gosterim, farkli IMDb ID, HTML veya string olmayan puan reddedilir.
- Opsiyonel OMDb fetch VE streaming body icin **3500 ms hard deadline**; AbortController + Promise.race. Zaman asimi TMDb detayini engellemez. OMDb API anahtari yalniz Worker secret'inda kalir; istemciye veya cache anahtarina girmez.
- Pure `ratings-enrichment.mjs` + tam GitHub SHA-eslesmeli test 3/3 yerel Node22 gecti. `worker/test/catalog.test.mjs` icine 60 sn gecici hata, N/A ve takilan OMDb entegrasyon testleri eklendi; bunlar tam exact Worker checkout ile henuz calistirilMADI. Gercek OMDb key ayarlanmadi, canli metadata Worker degistirilmedi.
- TMDb film serilerinde `FilmCollectionPolicy` artik regex'e ek olarak gercek takvim gununu kontrol eder. 2026-02-31 ve 2025-02-29 gibi imkansiz tarihler sona gider; 2024-02-29 gecerlidir. Tam GitHub SHA-eslesmeli Kotlin politika/test 15/15 gecti. Stock CloudStream ayri franchise seridi sunmadigindan resmi koleksiyon kartlari Onerilenler'in basinda gorunur; Mi Box Beta gorsel dogrulama bekliyor.
