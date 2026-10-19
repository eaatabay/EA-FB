<!--CURRENT-SERIES-STATUS-->
## Güncel uygulama aşaması
Son CI 39316844187 (source 9aa829f1b494498762e02c16e5e6ad0f6c7de297) test/derleme/paketleme/koruma adımlarının tamamında success. Tam log arşivi incelendi. Artifact ikili indirme yönlendirmesi Azure blob hostunda engellendi; güvenli alternatif olarak yerel yeniden üretilen paket CI logundaki iki bağımsız SHA-256 satırıyla eşleştirildi. CS3 332876 byte, SHA f2b2d28a8a6adf5fbdfe8eae91fb70f36c346f658c3dbf7bcc91aae55fe12a3b. Yeni dist-series-codex-8501-test dağıtımı yalnız yeni dala kaydediliyor. Ana Android profilinde çalışmaz; ayrı profil test paketi, Mi Box oynatması doğrulanmadı.
Önceki tam SHA: 9aa829f1b494498762e02c16e5e6ad0f6c7de297
Son kayıt commit’inin kendi SHA’sı `git log -1` ile bulunur; sonraki kayıt envantere ekler.

| SHA | İşlem | Bağlantı |
|---|---|---|
| `45956ae74fdc512fa838ed6e99112a41baba9187` | docs(series): record V83 baseline and rollback boundaries | [commit](https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187) |
| `069824f8433792f8f5dee2b7079390ee1f9e0374` | test(series): isolate V83 title rejection across four adapters | [commit](https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374) |
| `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` | test(series): enforce byte-level V83 baseline preservation | [commit](https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6) |
| `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` | ci(series): add branch-scoped diagnostics without publication | [commit](https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099) |
| `87344068c46877c720e8f1afa53084cc06400fab` | docs(series): record verified season alias and obsolete branch cleanup | [commit](https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab) |
| `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` | test(series): track researched TV identity separately from films | [commit](https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4) |
| `63782dbd89fc52d4382a973c82fac67ca442ad75` | docs(series): audit V83 UI hooks and preserve diagnostic handoff | [commit](https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75) |
| `22922df2f887c6c14cf687bb63bd13118476bb22` | test(series): probe approved catalogue identity from read-only CI | [commit](https://github.com/eaatabay/EA-FB/commit/22922df2f887c6c14cf687bb63bd13118476bb22) |
| `6433b1fcf5a88de40412bbe852112eb15bd90950` | test(series): replay confirmed catalogue identity through V83 title gates | [commit](https://github.com/eaatabay/EA-FB/commit/6433b1fcf5a88de40412bbe852112eb15bd90950) |
| `1aa79597420845beb18afa58849c917a9499beab` | docs(series): finalize confirmed diagnosis and authentication handoff | [commit](https://github.com/eaatabay/EA-FB/commit/1aa79597420845beb18afa58849c917a9499beab) |
| `f0abf3b1d800b344984167a5ffd77d6cc65fcb99` | docs(series): confirm restored access and successful final CI | [commit](https://github.com/eaatabay/EA-FB/commit/f0abf3b1d800b344984167a5ffd77d6cc65fcb99) |
| `732d4613854042587a9fd0526cfd11163f6451c4` | test(series): reproduce missing confirmed season alias before correction | [commit](https://github.com/eaatabay/EA-FB/commit/732d4613854042587a9fd0526cfd11163f6451c4) |
| `1ac4de36b50b28c5313ea3c3d71e2b802f7e051a` | fix(series): add corroborated alias only for confirmed Organize Isler season | [commit](https://github.com/eaatabay/EA-FB/commit/1ac4de36b50b28c5313ea3c3d71e2b802f7e051a) |
| `9846add14a62fd118ccbfb874131d46fd6eb4c70` | test(series): reject ambiguous or uncorroborated alias source pages | [commit](https://github.com/eaatabay/EA-FB/commit/9846add14a62fd118ccbfb874131d46fd6eb4c70) |
| `b0094da7a3daab69c45894ad2da4776678fd75eb` | feat(series-test): isolate version 8501 and reject primary-profile loading | [commit](https://github.com/eaatabay/EA-FB/commit/b0094da7a3daab69c45894ad2da4776678fd75eb) |
| `bb0dc17706c29ac3e3ac037253983553036d6493` | fix(series-test): detect renamed EA-FB plugins before initialization | [commit](https://github.com/eaatabay/EA-FB/commit/bb0dc17706c29ac3e3ac037253983553036d6493) |
| `9aa829f1b494498762e02c16e5e6ad0f6c7de297` | build(series-test): verify isolated CS3 bytes and protected V83 DEX | [commit](https://github.com/eaatabay/EA-FB/commit/9aa829f1b494498762e02c16e5e6ad0f6c7de297) |
<!--END-CURRENT-SERIES-STATUS-->






# Güncel devam notları

## Bağlantı yenilendikten sonraki kesin durum

GitHub API ve HTTPS Git erişimi yeniden çalışıyor. Yerelde kalan **1aa79597420845beb18afa58849c917a9499beab** yalnızca yeni test dalına normal push ile gönderildi. Önceki 401 engeli giderildi.

- **39311299938: success**, tam log arşivi incelendi; 53 assertion, Android BUILD SUCCESSFUL ve 312 dosya koruması PASS.
- **39311839830: success**, tam log arşivi incelendi; son **59 assertion**, Android BUILD SUCCESSFUL ve 312 dosya koruması PASS. Kaynak commit: **6433b1fcf5a88de40412bbe852112eb15bd90950**.
- Her iki derlemede 206 D8 Kotlin metadata rewrite uyarısı var; bunlar gizlenmedi, cihaz başarısı sayılmadı. Ayrıntılar SON-CI-INCELEMESI.json içinde.
- Güncel uzak dal kontrolünde V83/main/V81 ve diğer korunan HEAD'ler başlangıçla aynı; eski başarısız dal silinmiş durumda.
- Yalnızca test ve teşhis yapıldı. UI/runtime/eşleştirme kuralı değiştirilmedi, paket veya staging manifesti yayımlanmadı. UI/iki eklenti izolasyonu hâlâ kanıtlanmadı; yayın engeli sürüyor.

Sonraki adım: kullanıcının sonraki kapsamını bekle. Şimdiki yetki yalnızca test/teşhis; kod düzeltmesi veya yayın başlatma. Gerçek EA-FB kimliği ve DiziYou başlık kapısı tanısı SONUC-TESHIS.md içinde.

### Bütün gönderilmiş önceki commit SHA’ları
| Tam SHA | İşlem | Bağlantı |
|---|---|---|
| `45956ae74fdc512fa838ed6e99112a41baba9187` | docs(series): record V83 baseline and rollback boundaries | [commit](https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187) |
| `069824f8433792f8f5dee2b7079390ee1f9e0374` | test(series): isolate V83 title rejection across four adapters | [commit](https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374) |
| `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` | test(series): enforce byte-level V83 baseline preservation | [commit](https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6) |
| `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` | ci(series): add branch-scoped diagnostics without publication | [commit](https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099) |
| `87344068c46877c720e8f1afa53084cc06400fab` | docs(series): record verified season alias and obsolete branch cleanup | [commit](https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab) |
| `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` | test(series): track researched TV identity separately from films | [commit](https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4) |
| `63782dbd89fc52d4382a973c82fac67ca442ad75` | docs(series): audit V83 UI hooks and preserve diagnostic handoff | [commit](https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75) |
| `22922df2f887c6c14cf687bb63bd13118476bb22` | test(series): probe approved catalogue identity from read-only CI | [commit](https://github.com/eaatabay/EA-FB/commit/22922df2f887c6c14cf687bb63bd13118476bb22) |
| `6433b1fcf5a88de40412bbe852112eb15bd90950` | test(series): replay confirmed catalogue identity through V83 title gates | [commit](https://github.com/eaatabay/EA-FB/commit/6433b1fcf5a88de40412bbe852112eb15bd90950) |
| `1aa79597420845beb18afa58849c917a9499beab` | docs(series): finalize confirmed diagnosis and authentication handoff | [commit](https://github.com/eaatabay/EA-FB/commit/1aa79597420845beb18afa58849c917a9499beab) |

Bu güncelleme commit’inin SHA’sı git log -1 ile bulunur.

---
Aşağıdaki önceki kayıt tarihsel durumu içerir; yukarıdaki kesin durum geçerlidir.

# Devam notları — 2026-10-19 (Europe/Istanbul)

**Kapsam: yalnızca yeni dalda test ve teşhis. Uygulama / UI değişikliği ve yayın yok.**

- Dal: `test/ea-fb-series-name-codex-20261019`.
- V83 temel ve yeni dal ilk HEAD: `16396702470eb1f81cc6dac4360e6b0ff84927d1`.
- Son başarılı kod/test commit'i ve GitHub push: **6433b1fcf5a88de40412bbe852112eb15bd90950**.
- Bu nihai kayıt commit'i için `git log -1 --format=%H`; bağlantı yenilenene kadar yerel olabilir.

## Tanı sonucu
EA-FB'nin gerçek config ve staging katalog yanıtı salt okunur CI ile doğrulandı: TV **320754**, IMDb **tt38732131**, name/original_name **Organize İşler**, ilk yayın **2026-10-16**, sezon 1 **Karun Hazinesi**, **8 bölüm**. Aynı aramada filmler **30634 / movie / 2005** ve **577990 / movie / 2019** olarak ayrı. Kanıt: EA-FB-KATALOG-GOZLEM.json ve BASLIK-KANITI.json.
V83 aday üretimi aynı name/original_name'i tek kısa ada indiriyor; doğrulanmış uzun alternatif ad iletilmiyor. Canlı DiziYou araması uzun adı döndürüyor; gerçek adlarla mevcut normalize kapısında ret üretildi. DiziYou sayfasında S1 E1..8 etiketleri var. Dört adapter fixture'ında uzun ad açıkça verildiğinde mevcut arama yolu teklif üretiyor. Hiçbir runtime alias kuralı eklenmedi.
Gerçek DiziBox/Bronze araması, extractor/stream ve Mi Box oynatması doğrulanmadı. DiziYou adı uyuşmazlığı somut; bütün cihaz hatalarının tek nedeni denmiyor.

## Test durumu
- Son replay tanısı: **59/59 yerel PASS**; gerçek güvenli metadata + sentetik adapter yanıtları.
- İlgili V83 regresyonları: 12 Kotlin programı / 14 Python testi PASS.
- Yeni Python tanı/koruma: **5/5 PASS**; dokuz simüle korunan dosya mutasyonu reddediliyor.
- **312 V83 dosyası byte-identical**; paket SHA-256 **40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66**.
- CI **39309504108 success**: ilk 45 tanı / kaynak koruma / Android derleme; tam log arşivi okundu, 206 D8 Kotlin metadata rewrite uyarısı ayrıca kaydedildi.
- CI **39310538425 success**: 53 tanı / Android derleme; tam loglar okundu.
- CI **39311300009 success**: gerçek EA-FB katalog kimliği; tam loglar okundu, güvenli JSON kaydedildi.
- CI **39311299938** son sorguda in_progress; test/koruma/SDK adımları geçmişti, Android sonucu henüz doğrulanmadı.
- Son 6433b1f test commit'inin run ID/sonucu auth kesilmesinden sonra alınamadı; önceki yeşil CI bu commit'in CI sonucu diye sunulamaz.

## Somut dış engel ve sonraki işlem
Son başarılı push'tan hemen sonra GitHub API **401 Bad credentials**, native Git okuması da kimlik doğrulama hatası verdi. Bu ağ 403 sorunundan ayrıdır. Platformdaki mevcut GitHub bağlantısı yenilenmeli; sohbetten token/parola isteme.
Bağlantı düzeldiğinde:
1. `git ls-remote --heads origin` ile korunan dal uçlarını ve yeni dalı kontrol et. Kullanıcının/başka aracın yeni değişikliğini ezme; force-push yapma.
2. Bu yerel nihai kayıt commit'ini yalnızca yeni dala normal push et.
3. 39311299938 ve 6433b1f için workflow run ID/sonuç/loglarını oku; üç kalıcı kayda kesin durumları ekle.
4. `PYTHONDONTWRITEBYTECODE=1 python3 scripts/verify-series-v83-baseline.py` çalıştır; yalnızca yeni test/doküman dosyaları fark etmeli.
5. Kullanıcının şu anki test/teşhis kapsamını koru. Kod düzeltmesi veya paket yayınına kendiliğinden geçme.

## UI / iki eklenti ve yayın engeli
UI-HOOK-INCELEMESI.json gerçek V83 DEX'inden dört Application callback ve apiName render kapısını kaydeder. İki eklenti/classloader/provider/marker/ayar ve unload izolasyonu kanıtlanmadı; UI-HOOK-RISKI.md kapıları geçilmeden yayın yok. Yeni sürüm/paket/tercih alanı/staging manifesti oluşturulmadı. Yerel EA-FB/build/EA-FB.cs3 V83 kimlikli eski temel derlemesidir, ikinci eklenti olarak kurulmaz.

## Yeni workflow'lar
- `.github/workflows/series-name-diagnostics.yml`: sadece yeni dal; regresyon/koruma/Android derleme; log artifact'i; yayın yok.
- `.github/workflows/series-name-catalog-diagnosis.yml`: sadece yeni dal; onaylı staging'e GET; contents:read; güvenli JSON artifact'i; Worker/D1/production yazması veya yayın yok.

Eski başarısız `test/organize-isler-season-title-20261019` dalı aynı V83 HEAD'i ve açık PR head/base bağımlılığı olmadığı doğrulanarak kullanıcı talebiyle silindi. Son tam uzak karşılaştırmada diğer **23** mevcut dalın HEAD'i aynıydı. Bu çalışmada V83/main/V81/arşiv ref'leri değiştirilmedi. Geri alma planı güncel.

## Bütün başarılı uzak commit SHA'ları
| Tam SHA | İşlem | GitHub |
|---|---|---|
| `45956ae74fdc512fa838ed6e99112a41baba9187` | docs(series): record V83 baseline and rollback boundaries | [commit](https://github.com/eaatabay/EA-FB/commit/45956ae74fdc512fa838ed6e99112a41baba9187) |
| `069824f8433792f8f5dee2b7079390ee1f9e0374` | test(series): isolate V83 title rejection across four adapters | [commit](https://github.com/eaatabay/EA-FB/commit/069824f8433792f8f5dee2b7079390ee1f9e0374) |
| `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` | test(series): enforce byte-level V83 baseline preservation | [commit](https://github.com/eaatabay/EA-FB/commit/36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6) |
| `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` | ci(series): add branch-scoped diagnostics without publication | [commit](https://github.com/eaatabay/EA-FB/commit/fb16e9ced070f1fe0c6d6cf93aaa863f79745099) |
| `87344068c46877c720e8f1afa53084cc06400fab` | docs(series): record verified season alias and obsolete branch cleanup | [commit](https://github.com/eaatabay/EA-FB/commit/87344068c46877c720e8f1afa53084cc06400fab) |
| `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` | test(series): track researched TV identity separately from films | [commit](https://github.com/eaatabay/EA-FB/commit/efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4) |
| `63782dbd89fc52d4382a973c82fac67ca442ad75` | docs(series): audit V83 UI hooks and preserve diagnostic handoff | [commit](https://github.com/eaatabay/EA-FB/commit/63782dbd89fc52d4382a973c82fac67ca442ad75) |
| `22922df2f887c6c14cf687bb63bd13118476bb22` | test(series): probe approved catalogue identity from read-only CI | [commit](https://github.com/eaatabay/EA-FB/commit/22922df2f887c6c14cf687bb63bd13118476bb22) |
| `6433b1fcf5a88de40412bbe852112eb15bd90950` | test(series): replay confirmed catalogue identity through V83 title gates | [commit](https://github.com/eaatabay/EA-FB/commit/6433b1fcf5a88de40412bbe852112eb15bd90950) |

Bu belgeyi kaydeden son commit kendi SHA'sını içeremez; `git log -1 -- docs/series-name-codex` ile belirle. Sonraki kayıt bu tam SHA'yı ekler.

## Kullanıcının devam talimatı — düzeltme aşaması başladı
Önceki SHA f0abf3b1d800b344984167a5ffd77d6cc65fcb99. Kullanıcı düzeltme/derleme/sonuç raporu için devam yetkisi verdi; önceki yalnız tanı sınırı artık tarihsel kayıttır.
Yeni test SeriesAliasContractTest.kt mevcut V83 kodunda “Confirmed S1 alias is missing” ile beklenen şekilde başarısız oldu. İlk derleme denemesinde test Log stub'ı eksikti; stub eklenip gerçek assertion hatası görüldü. Koruma betiğine yalnız bu yeni test yolu eklendi. Runtime değişmedi.
Geri alma: yalnızca bu yeni test/izin listesi/doküman commit'ini yeni dalda revert et. Sonraki aşama doğrulanmış kimlikle sınırlı alternatif başlık düzeltmesi. Yeni test paketi için ayrı Android kullanıcı profili + aynı profilde başka EA-FB olmaması zorunlu olacak; ana profil ve birlikte kurulum fail-closed engellenecek. UI dosyaları korunacak.
