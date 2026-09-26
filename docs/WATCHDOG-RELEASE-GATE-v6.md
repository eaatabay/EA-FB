# EA-FB v6 — Kaynak Bekçisi yerel sürüm kontrol kapısı

**Bu betikler yalnızca v6 geliştirme dalında çalışır.** Yayındaki v5, main,
ea-fb-catalog Worker ve Cloudflare'daki herhangi bir veritabanına dokunmaz.
Gerçek kaynaklara HTTP isteği göndermez, DRM/auth bypass yapmaz.

## Tek komut

Node.js 22+, Python 3 ve npm erişimi olan temiz EA-FB v6 checkout'unda:

```bash
cd source-watchdog
npm install
npm run test:release-local
```

`test:release-local` komutu bir aşama başarısız olursa derhal durur:

1. Node 22, `node:sqlite` ve Ed25519 desteği.
2. **Bütün** Node testleri (`npm test`): politika, gerçek SQLite uyum
   katmanı, Cron runner, admin JWT/işlem, imzalı snapshot ve ağ güvenliği.
3. Python ile şema/migration ve SQLite testleri.
4. Cloudflare Wrangler'ın **yalnızca yerel** workerd+D1 test ortamı:
   iki migration, 30 kurgusal kaynak, 8 ayrı 15 dakikalık Cron tetiklemesi,
   ardından aynı Cron zamanını bir kez daha çalıştırarak tekrarlı yazım testi.

## Başarı kriterleri

| Ölçüm | Beklenen |
|---|---:|
| Kurgusal kaynak | 30 |
| Sağlıklı kaynak | 29 |
| Admin incelemesi bekleyen | 1 |
| Onaylı test alan adına geçiş | 2 |
| Kaydedilen kaynak kontrolü | 59 |
| Audit olayı | 89 |
| Global veritabanı revizyonu | 89 |
| Açık kaynak kilidi | 0 |
| Aynı Cron zamanı tekrarında yeni yazım | 0 |

Testte `*.example.org` dışına ağ isteği yapılmaz; kurgusal adapterların
kendileri hiçbir ağ isteği yapmaz. `wrangler` işlemlerinin tamamında
`--local` ve rastgele oluşturulmuş geçici `--persist-to` dizini kullanılır.
Test sonunda yerel süreç kapatılır ve geçici dizin silinir. Tracked
`wrangler.jsonc` dosyasında **bütün çalışma bayrakları kapalıdır**.

**26.09 yerel doğrulama:** Güncel GitHub fixture üreticisi gerçek policy koduyla
çalıştırılıp 25.289 bayt SQL oluşturdu. Aynı çıktının uzunluğu, FNV32 ve
Adler32 toplamlarıyla birebir eşleşen SQL dosyası SQLite üzerinde
iki gerçek migration ile çalıştırıldı: **30 kayıt, 30 audit, 0 probe,
0 açık kilit, global revizyon 30**. Tekrar içe aktarıldığında
**sıfır ilave kayıt/audit** oluştu. Bu test gerçek SQLite üzerindedir;
Wrangler'ın workerd+D1 çalışma zamanı henüz doğrulanmadı.

**D1 uyumluluk düzeltmesi:** İlk fixture SQL üreticisi açık `BEGIN TRANSACTION` /
`COMMIT` yazıyordu. Cloudflare D1 dosya içe aktarması bunu desteklemediği
İçin komutlar çıkarıldı. Üretici artık yalnızca 30 `INSERT OR IGNORE` yazıyor;
regresyon testi transaction komutlarının geri gelmesini yasaklıyor.

**Yerel veritabanı güvenliği (26.09):** Eski `npm run test:local`
komutunun mevcut yerel D1 veritabanını değiştirme riski giderildi:
artık her zaman tek kullanımlık, geçici D1 kullanan aynı sürüm-kapısı
betiğini çağırıyor. GitHub'daki `make-local-config.mjs` dosyasının
birebir kopyası Node 22 üzerinde test edildi: 10/10 manipüle edilmiş
yerel/tracked yapılandırma reddedildi. Yerel config ilk oluşturulduğunda
dosya izni `0600`; ikinci oluşturma mevcut dosyanın üzerine yazmadı.
Wrangler alt süreçleri Cloudflare hesap tokenlarını miras almıyor,
tesadüfi gerçek D1 binding'i, ek route veya admin yazma yetkisi eklenmesi
tam yapı karşılaştırmasıyla engelleniyor.

**Yerel Wrangler komut güvenliği:** Sürüm kapısında izin verilen D1 işlemleri
yalnızca geçici test veritabanına uygulanan iki migration, tek onaylı SQL seed
dosyası ve **iki sabit, salt okunur sayım sorgusudur**. Başka bir SELECT,
UPDATE, DELETE, `--remote`, `deploy`, farklı veritabanı, env/profile veya
komut tekrarı anında reddedilir. Wrangler'ın JSON çıktısı ve 30 kaynaklık
kabul sayaçları ayrı modülde tam olarak doğrulanır. Bu modülün GitHub'la
aynı SHA'ya sahip kaynak/test dosyaları Node 22 üzerinde **4/4** başarılı
çalıştırıldı; 18 zararlı/yanlış CLI argümanı senaryosu reddedildi.
Bu doğrulama **gerçek Wrangler sürecinin yerini tutmaz**.

**Test geçmeden** ayrı Cloudflare test hesabına deploy, gerçek kaynak
adapteri veya Mi Box güncellemesi yapılmamalıdır.

## GitHub üzerinden test (henüz çalıştırılmadı)

`.github/workflows/v6-watchdog-local-check.yml` ile ayrı bir **opt-in,
salt-okunur** test işi hazırlandı. Yalnızca aynı depodaki
`feature/detail-dual-ratings-v6` dalından `main` hedefine açılan PR'da
çalışır; `push` ve `schedule` tetikleyicisi yoktur. GitHub Actions
dakikası tüketmemek için şu aşamada bir PR açılmadı ve iş tetiklenmedi.
`workflow_dispatch` ileride workflow varsayılan dalda da bulunduğunda
kullanılabilir; mevcut özellik dalındaki dosyayı tek başına menüde
görünür kılmaz.

İş Node 22 / Python 3.13 kurar, bağımlılıkları indirir ve
`npm run test:release-local` komutunu çalıştırır. `contents: read`
yetkisi dışında GitHub izni veya Cloudflare sırrı verilmez. Gerçek
Cloudflare'a yükleme, uzaktan D1 oluşturma, Worker deploy ve gerçek
kaynak taraması içermez.

**Yeni sonuç:** GitHub dosyalarıyla SHA'sı eşleşen Node testleri, yapı
değişikliği/sahte işlem kontrolünde **4/4**, yalnızca PR/manual
tetiklenen Actions workflow güvenliğinde **3/3** geçti. YAML yapısı da
ayrı incelendi. Wrangler CLI'nin karma (bir hata + bir başarı) JSON
yanıtını artık kabul etmiyoruz. Bu sonuçlar gerçek workerd/D1
çalıştırıldığını göstermez.

## Sınırlar

Tam v6 doğrulaması olan `bash scripts/verify-v6.sh` da artık yerel Wrangler
D1/Cron testini **Kotlin derlemesi ve `.cs3` üretiminden önce** zorunlu olarak
çalıştırır. Wrangler kurulu değilse başarılı rapor vermek yerine durur.

Bu betiğin depoda bulunması çalıştırıldığı anlamına GELMEZ. Yeni Wrangler
paketi henüz bu çevrimdışı çalışma ortamında kurulu olmadığı için gerçek
workerd+D1 testini burada tamamlayamadık. Testin başarılı sonucu oluşunca
rapordaki sayaçlar terminalde `PASS LOCAL Wrangler+D1` olarak görünür.
İzinli gerçek kaynakların IP-pinned taşıma katmanı ve Android cihazdaki
uçtan uca playback testi **ayrı aşamalardır**.

## Rights gate validation status (26 Sep 2026)
The independent Kotlin rights suite and bridge JVM suite are included in `scripts/test-core.sh`; new offline rights regressions and a Node bridge-wiring check have been committed on the v6 feature branch but not rerun here. These checks do not substitute for the outstanding real LOCAL Wrangler+D1/Cron smoke, Android Gradle packaging or Mi Box verification. No Actions, remote D1 or live-source requests were used.

**26.09 hak/izin test durumu:** `scripts/test-core.sh` izin politikasi ve kapali bridge JVM testlerini icerir. Kanit referansi, URL authority, yinelenen kaynak kimligi ve nokta-segment duzeltmeleriyle Kotlin izin dosyasi 62 senaryoya ulasti. Ilk 23 testin disindaki 39 senaryo tam GitHub checkout'unda henuz calistirilmadi; basari sonucu ancak yerel JVM cikti loguyla isaretlenecek. Gercek yerel Wrangler+D1/Cron kapisi ayri ve acik kalir.

## 26 Sep 2026: offline rights path regression and runner wiring
The rights gate now rejects `.` path segments in both signed base URLs and compiled approved path scopes; harmless filenames containing `..` remain valid. Three new Kotlin cases bring the committed rights suite to 62. `scripts/test-core.sh` now also invokes the offline Node bridge-wiring regression after the bridge JVM test and fails closed if Node is missing. These new tests and the complete runner remain **unexecuted against the exact v6 checkout**; do not count them as a passing release gate. No real source network requests, Actions, or live deployment occurred.

## 26 Sep 2026: empty-rights cache boundary

The bridge now returns immediately when the release-compiled rights list is empty, before consulting delivery configuration or restoring any cached snapshot. The source-wiring Node suite now contains five checks: reviewed snapshot wiring, empty production registry, early return for empty rights, rights-before-selection ordering, and early return for LIVE/invalid time. The SHA-matched bridge, policy and Node test files passed **5/5** in the isolated local Node 22 environment; the full repository release runner remains unexecuted. The release remains disabled for real sources; no live source or remote D1 request was made.

## 26 Sep 2026: bridge JVM coverage expansion

The exact repository bridge JVM test file now contains **11 assertions**: the original five production-off cases plus six negative-clock and empty-adapter cases across MOVIE, SERIES and LIVE. A separate isolated JVM harness exercised the SHA-matched bridge and policy with test-only dependencies across 18 combinations (**18/18 passed**, no cache access, no delivery configuration and no adapter selection). This is not a claim that the complete 11-case repository JVM suite or the 62-case Kotlin rights suite has run. Keep both release-gate items open until the exact checkout is executed.

## 26 Sep 2026: rights-record identity hardening

The compiled rights gate now requires an exact canonical evidence reference `rights/YYYY/<permit-id>.md`. It rejects references to another source's record, nested aliases and non-numeric years. A prior-year record remains syntactically valid when the separately recorded permit expiry has not elapsed. Four committed Kotlin regressions increase the checked-in rights suite from 62 to **66** cases. The exact updated policy file (Git blob `bf544b12`) passed **47/47 independent offline JVM checks** with a test-only snapshot model. This is not the exact 66-case repository test run, nor verification of actual legal rights or document existence. The 11-case bridge JVM suite and real local Wrangler+D1 release gate remain pending.

## 26 Sep 2026: review-lifetime boundary

The compiled permit gate now explicitly requires a positive lifetime of at most 366 days. Two new committed regressions cover a far-future validity interval and the exact 366-day accepted boundary; the checked-in Kotlin rights suite contains **68** cases. These two cases have not yet run as part of the exact repository JVM suite. The SHA-matched independent **47/47** run predates this change and must not be presented as verification of the new guard. The production rights list remains empty.

## 26 Sep 2026: offline preflight, scheduler and fixture validation

An isolated local reconstruction of selected GitHub source/test modules passed **32/32 Node tests** (bridge wiring 5, policy 12, fixture seed 2, network preflight 5, scheduler 4, D1 lease 4), plus **7/7 reconstructed SQLite migration tests**. The 68 rights-policy and 11 bridge cases also passed in local JVM harnesses using test-only snapshot/Android/adapter dependencies. The new rights-record identity rule initially invalidated the existing distinct-ID test fixture; its evidence reference was corrected in commit `cfea4dc4` and the reconstructed 68-case run then passed.

The preflight now rejects query-bearing URLs (including empty delimiters), fragments, noncanonical literal HTTPS authorities (including uppercase, percent-encoded hostnames and explicit :443), invalid DNS-label boundaries and oversized labels. Scheduler now rejects unknown health states and malformed due/incident timestamps. New committed regressions cover these boundaries. All these runs are **selected isolated tests**, not the full exact repository `npm test` / `scripts/test-core.sh` / real Wrangler workerd+D1 release gate. No live network source, Actions or remote D1 was used.

## 26 Sep 2026: additional isolated validation and package-staging safety

Selected offline Node suites now pass **33/33** (bridge wiring 5, watchdog policy 12, fixture seed 2, preflight 6, scheduler 4, lease 4); reconstructed Python SQLite migration tests pass **7/7**. A separate in-memory SQLite + reconstructed policy/scheduler simulation completed eight 15-minute ticks across 30 fictional sources: 59 runs, 89 audits, revision 89, 29 healthy, one admin-held, two approved-domain moves and zero replay writes. Following explicit simulated admin retest and two fresh successful probes, it reached 30 healthy, 61 runs, 92 audits and revision 92 without leftover leases. These are NOT the actual Worker/workerd+D1 smoke or the full exact checkout test suite.

The v6 staging script now rejects every plugin version other than an exact integer 6; six reconstructed Python staging cases passed, including stale and type-coerced version rejection. This tests manifest and archive staging with fake fixture ZIPs, NOT a real Gradle-produced Android .cs3 or Mi Box installation. No GitHub Actions, real-source probes, live Worker, remote D1 or deployment occurred.

## 26 Sep 2026: second offline test session (exact-source evidence)

The following current GitHub blob-identical Kotlin files were compiled and executed locally: `SourceSnapshotTrust.kt` (d20dcc8d) + `SourceSnapshotTrustTest.kt` (4031a3f6) **19/19**; `ReviewedSourcePermitPolicy.kt` (c186202d) + `ReviewedSourcePermitPolicyTest.kt` (052336ce) **68/68** with the real BouncyCastle 1.80 and real trust model; `SourceSnapshotGate.kt` (23aa7da6) + its exact test (b9d1d386) **6/6**; `WatchdogAdapterSelection.kt` (7ce3a89b) + its exact test (ea421e2a) **15/15**; and `WatchdogApprovedAdapterBridge.kt` (85b666c5) + its exact test (e0a02815) **11/11**. The gate, selection and bridge compilations used test-only domain or Android/store stubs, not all production dependencies. A fresh ephemeral JS WebCrypto Ed25519 signature also passed **5/5** cross-language checks with the SHA-exact Kotlin trust implementation; no signing private key was committed.

Exact current Node crypto source (5d2731fb) + test (4b416ce6) passed **7/7**; exact preflight source/test passed **6/6**; exact scheduler source/test passed **5/5**. The selected combined Node suite passed **41/41**, but other selected policy, fixture, lease and bridge-wiring modules in the local harness remain reconstructed copies, so this is **not** a full checkout `npm test`. Exact current Python release staging source (25c8b449) + test (eea2c68d) passed **11/11**, rejecting stale v4/v5, duplicate ZIP entries, traversal, symlink and invalid/mismatched manifest. Reconstructed SQLite migration tests passed **7/7** and the separate offline 30-source fixture simulation/recovery repeated its prior success.

Outstanding release blockers remain the full exact checkout suites, actual Wrangler/workerd local D1 smoke, actual Android Gradle `.cs3` build, Mi Box verification, production rights evidence and separate live-source approval. Production permits, signing pins and adapters remain EMPTY/OFF. No Actions, deploy, real network source or remote D1 was used in this session.

The checked-in Wrangler config also has a new exact offline regression `source-watchdog/test/release-defaults.test.mjs` (7c9dd8e6): `workers_dev=false`, disabled mode, all five enable flags false, and no remote D1 binding or route. The selected local Node bundle now passes **42/42**. This protects tracked defaults but does not substitute for a real Wrangler/workerd deployment simulation.

## 26 Sep 2026: third offline test session

Exact GitHub SHA-matched release staging `scripts/stage-release.py` (5c4ef40b; clean BadZipFile CLI handling added) and `tests/test_stage_release.py` (81c83c94) passed **17/17** locally. New checks reject oversized/encrypted archives, duplicate members, unsafe directory entries, embedded ZIP symlinks, symlinked dist and output targets, bad manifest JSON, wrong plugin identity and non-integer v6 metadata. The test artifacts are synthetic ZIPs; Android Gradle packaging remains unverified.

Signed Cloudflare Access admin JWTs now have an explicit maximum 24-hour session age and issuance-to-expiry span, and both team and admin-write origins require canonical DNS labels. The locally reconstructed Access module/test passed **6/6**; exact full-repo admin tests have not run. Registry approval evidence now rejects external URLs, traversal and ambiguous paths (isolated 12/12 boundary checks; exact registry/D1 suite pending). The source policy rejects URL parser aliases and malformed host allowlists (reconstructed policy 12/12 plus 11 additional boundary cases; exact full suite pending). Admin body overflow remains HTTP 413 even when stream cancellation throws.

The separate public metadata Worker now reads TMDb and optional OMDb response bodies using bounded streaming byte limits (2,000,000 and 50,000 bytes respectively); an isolated copy of its exact helper passed **7/7**, while four new full Worker regression tests are committed but have not yet been executed in an exact checkout. No real TMDb/OMDb request was made. The selected local Node bundle passed **48/48**, SHA-exact Kotlin rights/trust/gate/selection/bridge passed **68/68, 19/19, 6/6, 15/15, 11/11** respectively, and JS-signed Kotlin Ed25519 interop passed **5/5**. The reconstructed SQLite migration suite passed **7/7** and the offline 30-source simulation/recovery repeated its earlier counters. These selected results do not substitute for full checkout `npm test`, actual local Wrangler/workerd+D1, a real Android Gradle `.cs3`, or Mi Box verification. Production approvals and signing pins remain empty; tracked Wrangler flags remain OFF. No GitHub Actions, deployment or remote D1 access occurred.

Third-session follow-up: the registry policy additionally rejects malformed allowlist objects and non-safe-integer/negative clocks (GitHub commits 713a0ad8 and 2e884cf3). The reconstructed local policy suite remained 12/12 and 13 extra negative-input checks passed; the new exact checked-in regression is pending full-checkout execution. Rechecked feature branch release flags: reviewed permits EMPTY, public signing pins EMPTY, installed adapter map EMPTY, Android delivery `enabled=false`, and tracked Wrangler mode disabled with all five activation flags false and no remote D1 binding. This is source/config verification, not deployment verification.

## 26 Sep 2026: fourth offline parkur

The client snapshot publisher now requires safe-integer generation/TTL/health clocks and a safe, bounded expiry, caps adapterVersion at 1,000,000, and refuses to publish a healthy state whose URL differs from the registry-approved configuration URL (even when both hosts are on the allowlist). This prevents an incomplete state/config CAS transition from becoming a signed delivery snapshot. Three regression tests were committed to the exact repository. The reconstructed local publisher boundary passed **20/20**; the exact full-checkout snapshot suite is still pending.

Admin manual release now rejects time rollback before the last probe; the network preflight now rejects dot segments, encoded paths, duplicate slashes and single backslashes before any DNS lookup. Two new repository regressions were committed. Locally reconstructed policy+preflight existing suites passed **18/18**; the added boundary smoke passed **12/12**, with zero DNS calls for rejected aliases. The selected existing local Node bundle was rerun **48/48**, rights **68/68**, bridge **11/11**, selection **15/15**, Python staging **17/17**, SQLite fixture **59 runs/89 audits/revision 89**, and admin recovery **61 runs/92 audits/revision 92**. These local copies are not a complete checkout or real workerd/D1. No Actions, real-source requests, remote database writes or deployment occurred.
