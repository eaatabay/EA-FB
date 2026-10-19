# Güncel devir: MAVİ CloudStream / 8502

Kullanıcı düzeltmesi: mavi uygulamada V83 yok; eski Bunny testi kaldırılacak. Aynı test dalı korunuyor. 8501 ana-profil yasağı körlemesine kaldırılmadı: 8502 yalnız ayrı Android kullanıcı veya açık ADB opt-in + tekrar doğrulanan ayrı paket/UID/dataDir/process ile yüklenir. Eksik veri, ortak UID, PackageManager görünürlük hatası veya aynı hostta başka EA-FB kapalı sonuçtur.

Yazılım testleri, yerel Android build, compiled gate sırası, 2028 V83 metodu ve 250 Bronze metodu, source/package hash kontrolleri geçti. Final CI: https://github.com/eaatabay/EA-FB/actions/runs/39323892158 (success). Hash CI logu ve yerel paket arasında aynı. Dört kaynak fixture testi arama/bridge/ayar korumasını doğrular; canlı Mi Box veya video oynatma değildir.

Paket: dist-series-codex-8502-test/EA-FB-SERIES-CODEX-8502-TEST.cs3
SHA256: 92fbfd0dd39028b5d4d6110ae305418f718ec98c4c4d0f1d910ea1103efa83bf

CİHAZ DURUMU: BEKLİYOR. Bu ortamda ADB ile Mi Box bağlantısı yok. UI hook'larının gerçek cihazda görünümü, sıcak/soğuk yeniden başlatma, her kaynak ve gerçek oynatma doğrulanmadı. Ayrı uygulama ölçümü cihazda script ile yapılacak; yalnız yazılım tasarımı/testleri tamamlandı.

TEST-KURULUMU.md kesin adımları içerir. Eski Bunny yalnız maviden kaldırılır, yalnız mavi zorla durdurulur; ardından salt okunur ADB raporu, açık --arm, yeni repo ve 8502 kurulumu. Gate hatasında bypass yok. Diğer CloudStream/V83/production'a işlem yok. Eski 8501 dağıtımı değişmez, yeni 8502 kimliği/store/repo ayrıdır.

CI-8502-SONUC.json, MAVI-8502-KANIT.json ve YAYIN-8502-DOGRULAMA.json kanıtlarını incele. Son yayın raw hash doğrulaması ayrı kayıtla eklenecek.

Son kayıt/yayın commit kendi SHA’sını içeremez; sonraki kayıt önceki SHA’yı ekler. En son SHA: `git rev-parse HEAD`. Önceki tam HEAD: 934ca5764ad3e4032cf5c753b89b9ac189fc5834.


| SHA | İşlem |
|---|---|
| `45956ae74fdc512fa838ed6e99112a41baba9187` | docs(series): record V83 baseline and rollback boundaries |
| `069824f8433792f8f5dee2b7079390ee1f9e0374` | test(series): isolate V83 title rejection across four adapters |
| `36a4820a4a3c2a43e83b87e5b4b1ef0d217f56b6` | test(series): enforce byte-level V83 baseline preservation |
| `fb16e9ced070f1fe0c6d6cf93aaa863f79745099` | ci(series): add branch-scoped diagnostics without publication |
| `87344068c46877c720e8f1afa53084cc06400fab` | docs(series): record verified season alias and obsolete branch cleanup |
| `efb5fe86b2e0c0edfcc0f6140356d35fa004a7b4` | test(series): track researched TV identity separately from films |
| `63782dbd89fc52d4382a973c82fac67ca442ad75` | docs(series): audit V83 UI hooks and preserve diagnostic handoff |
| `22922df2f887c6c14cf687bb63bd13118476bb22` | test(series): probe approved catalogue identity from read-only CI |
| `6433b1fcf5a88de40412bbe852112eb15bd90950` | test(series): replay confirmed catalogue identity through V83 title gates |
| `1aa79597420845beb18afa58849c917a9499beab` | docs(series): finalize confirmed diagnosis and authentication handoff |
| `f0abf3b1d800b344984167a5ffd77d6cc65fcb99` | docs(series): confirm restored access and successful final CI |
| `732d4613854042587a9fd0526cfd11163f6451c4` | test(series): reproduce missing confirmed season alias before correction |
| `1ac4de36b50b28c5313ea3c3d71e2b802f7e051a` | fix(series): add corroborated alias only for confirmed Organize Isler season |
| `9846add14a62fd118ccbfb874131d46fd6eb4c70` | test(series): reject ambiguous or uncorroborated alias source pages |
| `b0094da7a3daab69c45894ad2da4776678fd75eb` | feat(series-test): isolate version 8501 and reject primary-profile loading |
| `bb0dc17706c29ac3e3ac037253983553036d6493` | fix(series-test): detect renamed EA-FB plugins before initialization |
| `9aa829f1b494498762e02c16e5e6ad0f6c7de297` | build(series-test): verify isolated CS3 bytes and protected V83 DEX |
| `eba59a87802695772750582c505b5fe0abf6d0c6` | release(series-test): stage verified isolated 8501 package |
| `b0e18e62adb3a101daef65ae5235f8047e918325` | docs(series-test): record verified release and complete rollback handoff |
| `58a067b57a485dd126865664a818ce81da5f13f5` | docs(series-test): finalize report commit references |
| `ea1e7aa2b461552b3f42dafa87f239ce83276772` | fix(series-test): require verified app isolation for primary-profile 8502 |
| `49a11b4a8813cc406402bcb5d072323239dd71d2` | build(series-test): verify blue app opt-in and protect 8502 runtime gates |
| `934ca5764ad3e4032cf5c753b89b9ac189fc5834` | fix(series-test): report conditional primary-profile policy accurately |
