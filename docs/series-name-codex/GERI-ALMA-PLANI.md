<!--CURRENT-SERIES-STATUS-->
## Güncel uygulama aşaması
Yeni kullanıcı düzeltmesi: mavi CloudStream V83 içermiyor; eski Bunny sürümü kaldırılacak. Primary UID engeli yalnız açık yerel kayıt ve OS paket/UID/private data/process kontrolleriyle aşılabilir. Cihaz davranışı doğrulanmadı. UI kaynakları değişmedi, eski 8501 yayınları hash ile donduruldu.
Önceki tam SHA: 58a067b57a485dd126865664a818ce81da5f13f5
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
| `eba59a87802695772750582c505b5fe0abf6d0c6` | release(series-test): stage verified isolated 8501 package | [commit](https://github.com/eaatabay/EA-FB/commit/eba59a87802695772750582c505b5fe0abf6d0c6) |
| `b0e18e62adb3a101daef65ae5235f8047e918325` | docs(series-test): record verified release and complete rollback handoff | [commit](https://github.com/eaatabay/EA-FB/commit/b0e18e62adb3a101daef65ae5235f8047e918325) |
| `58a067b57a485dd126865664a818ce81da5f13f5` | docs(series-test): finalize report commit references | [commit](https://github.com/eaatabay/EA-FB/commit/58a067b57a485dd126865664a818ce81da5f13f5) |
<!--END-CURRENT-SERIES-STATUS-->

# Geri alma planı — 8501

- Yalnız yeni dal: `test/ea-fb-series-name-codex-20261019`. Temel V83: `16396702470eb1f81cc6dac4360e6b0ff84927d1`.
- Kod kaynak SHA: `9aa829f1b494498762e02c16e5e6ad0f6c7de297`; yayın SHA: `eba59a87802695772750582c505b5fe0abf6d0c6`.
- CI: 39316844187 success.
- Paket kimliği: `EA-FB-SERIES-CODEX-8501-TEST`; provider `EA-FB SERIES CODEX 8501 TEST`; version 8501.
- Ayrı tercih alanı `ea_fb_series_codex_8501_test`, ayrı boş seed `ea_fb_series_codex_8501_seed`; V83/V82 tercihleri taşınmaz/yazılmaz.
- Test manifesti: https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-series-name-codex-20261019/dist-series-codex-8501-test/repo.json
- CS3: https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-series-name-codex-20261019/dist-series-codex-8501-test/EA-FB-SERIES-CODEX-8501-TEST.cs3
- CS3 SHA-256: `f2b2d28a8a6adf5fbdfe8eae91fb70f36c346f658c3dbf7bcc91aae55fe12a3b`.
- Yeni workflow'lar: `.github/workflows/series-name-diagnostics.yml` (test/build/paket artifact'i, otomatik push yok), `.github/workflows/series-name-catalog-diagnosis.yml` (salt okunur staging GET). Eski workflow'lar korunur.

## Cihazdan yalnız test sürümünü kaldırma
1. Bu paket ana Android profiline kurulmaz/çalışmaz. Ayrı test profilinde kurulduysa yalnız yukarıdaki internalName ve bu yeni depo URL'sini kaldırın; test uygulamasını kapatıp açın.
2. Ana V83 profiline, mevcut eklentiye, ayarlara, kayıtlı tercihlere veya uygulama verisi/cache'ine dokunmayın. Bu çalışma sırasında cihaz kurulumu yapılmadı; profil desteği/gerçek oynatma henüz doğrulanmadı.
3. Aynı profilde iki EA-FB'yi birlikte çalıştırma yöntemi sunulmadı; ayrı profil koşulu kaldırılamaz diye varsaymayın, ancak bu sürümde zorunludur.

## GitHub'dan yalnız bu denemeyi kaldırma
1. Gerekirse yeni dalı dışarı arşivleyin.
2. Yalnız `git push origin --delete test/ea-fb-series-name-codex-20261019` çalıştırın. V83'e force-push/reset/cherry-pick/revert/düzeltme commit'i gerekmez.
3. Yalnız yeni `dist-series-codex-8501-test/` dağıtımı ve yeni test/koruma/paketleme dosyaları bu denemeye aittir. Yeni dalın değişen kaynakları yalnız bu dalda kalır; dal silinince V83'ün yedi dosyasını geri taşıma gerekmez. Eski `dist-v83-test/`, `dist/`, V81/arşivler ve mevcut workflow'ları silmeyin.
4. Kalan tüm uzak ref'leri UZAK-DALLAR-BASLANGIC.tsv ile karşılaştırın: yeni dal ve kullanıcı talebiyle silinen eski başarısız dal dışında önceki 23 HEAD aynı olmalı. V83 `16396702470eb1f81cc6dac4360e6b0ff84927d1` ve paket SHA 40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66 korunmalı.

## Tam yeni commit envanteri
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
| `1aa79597420845beb18afa58849c917a9499beab` | docs(series): finalize confirmed diagnosis and authentication handoff | [commit](https://github.com/eaatabay/EA-FB/commit/1aa79597420845beb18afa58849c917a9499beab) |
| `f0abf3b1d800b344984167a5ffd77d6cc65fcb99` | docs(series): confirm restored access and successful final CI | [commit](https://github.com/eaatabay/EA-FB/commit/f0abf3b1d800b344984167a5ffd77d6cc65fcb99) |
| `732d4613854042587a9fd0526cfd11163f6451c4` | test(series): reproduce missing confirmed season alias before correction | [commit](https://github.com/eaatabay/EA-FB/commit/732d4613854042587a9fd0526cfd11163f6451c4) |
| `1ac4de36b50b28c5313ea3c3d71e2b802f7e051a` | fix(series): add corroborated alias only for confirmed Organize Isler season | [commit](https://github.com/eaatabay/EA-FB/commit/1ac4de36b50b28c5313ea3c3d71e2b802f7e051a) |
| `9846add14a62fd118ccbfb874131d46fd6eb4c70` | test(series): reject ambiguous or uncorroborated alias source pages | [commit](https://github.com/eaatabay/EA-FB/commit/9846add14a62fd118ccbfb874131d46fd6eb4c70) |
| `b0094da7a3daab69c45894ad2da4776678fd75eb` | feat(series-test): isolate version 8501 and reject primary-profile loading | [commit](https://github.com/eaatabay/EA-FB/commit/b0094da7a3daab69c45894ad2da4776678fd75eb) |
| `bb0dc17706c29ac3e3ac037253983553036d6493` | fix(series-test): detect renamed EA-FB plugins before initialization | [commit](https://github.com/eaatabay/EA-FB/commit/bb0dc17706c29ac3e3ac037253983553036d6493) |
| `9aa829f1b494498762e02c16e5e6ad0f6c7de297` | build(series-test): verify isolated CS3 bytes and protected V83 DEX | [commit](https://github.com/eaatabay/EA-FB/commit/9aa829f1b494498762e02c16e5e6ad0f6c7de297) |
| `eba59a87802695772750582c505b5fe0abf6d0c6` | release(series-test): stage verified isolated 8501 package | [commit](https://github.com/eaatabay/EA-FB/commit/eba59a87802695772750582c505b5fe0abf6d0c6) |

Bu son doküman kaydı için `git log -1 --format=%H`; kendi SHA'sını kendi içeriğine gömmek mümkün değildir. Sonuç mesajındaki kesin SHA kullanılabilir.

Nihai yayın/geri alma raporu GitHub commit’i: [b0e18e62adb3a101daef65ae5235f8047e918325](https://github.com/eaatabay/EA-FB/commit/b0e18e62adb3a101daef65ae5235f8047e918325). Bu son belge biçim düzeltmesi uygulama veya paket içeriğini değiştirmez.
