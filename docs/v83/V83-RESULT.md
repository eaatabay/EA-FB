# EA-FB V83 TEST — Bronze NL/LAND film eşleştirme sonucu

Tarih: 10 Ekim 2026. V83 cihaz oynatması henüz test edilmedi.

## Temel ve kapsam

- Kaynak dal: `test/ea-fb-v82-name-cleanup`.
- GitHub üzerinden doğrulanan V82 temel commit: `79d17e9c4157bbf176a0e996cb843fc5c3db822b`.
- Yeni dal: `test/ea-fb-v83-bronze-search-fix`.
- Film düzeltmesi: `d5e211e64f312d4d630a83afd006a35cb9c3dec5`.
- V83 staging hazırlığı: `fb1020db62f7bfa7b83cfe6926a3dec0c200abbf`.
- Son kod/paketleme commit'i: `f014f4e544d14f9d33593edf26bd438a9ee06058`.
- Yayın commit'i: `0527f30e5eaff8e4d6759123d73d59e001220f12`. CI başarılı; GitHub'daki gerçek paket yeniden doğrulandı.

## Kanıtlanan problem ve sınırı

V82 `BronzeApiAdapter.kt:42` arama sonucu adının, `44–45` yüklenen sayfa adının adaylardan biriyle tam normalize eşleşmesini ister. `The Odyssey` / `Odyssey` adayları `Odyssey - The Odyssey` başlığıyla eşleşmez. Yeni otomatik film testi değiştirilmemiş V82 davranışında bu kabul senaryosunda başarısız oldu; V83 düzeltmesinden sonra geçti.

Bu, söz konusu başlık biçiminin wrapper tarafından elendiğinin kod/test kanıtıdır. Beş filmin cihazda aynı sebeple başarısız olduğunun veya V83'te oynatacağının kanıtı değildir.

Görev metnindeki `V82-Film-Test-2.txt` özeti Homecoming ve Odyssey için NL/LAND `offers=0` bildiriyor. Bu görevde ham log dosyası ayrıca yüklenmedi; özet kullanıcı tarafından sağlanan cihaz kanıtı olarak değerlendirildi. `offers=0` ham kaynak aramasının sıfır sonuç verdiğini tek başına söylemez; wrapper'ın filtrelediği sonuçlar da aynı çıktıyı üretir. Diğer üç filmin cihaz aşaması ölçülmedi.

Özgün NL/LAND paketleri önceki sabit Bronze referanslarıdır. Özgün Kotlin adaptör kaynakları mevcut değildir; çalışan derlenmiş DEX değiştirilmedi. Kullanıcının cihazındaki bağımsız paketlerin tam sürüm/hash eşitliği bu görevde ayrıca doğrulanmadı.

## Düzeltme

Değişiklik `BronzeApiAdapter.kt` içinde yalnızca `MediaKind.MOVIE` için ayrı arama yoludur. Seri/bölüm yolu ve `resolveIncrementally` korunur.

Film eşleştirmesi:

- Mevcut tam normalize başlık eşleşmesi korunur; bilinen yıl çelişkisi reddedilir.
- Başlangıçtaki İngilizce `The`, aynı sıralı harflerin farklı kelime sınırları ve aynı kelimelerin farklı sırası kontrollü varyant sayılır.
- Kelime sırası karşılaştırması kelime tekrar sayılarını da korur; basit substring/contains kullanılmaz.
- İki dilli başlık yalnızca boşluklu tire/en dash/em dash/dikey çizgi ile ayrılır. `Spider-Man` içindeki tire bölünmez. Her parça sorgudaki bilinen adlardan biriyle uyumlu olmalıdır; ilgisiz bir parça varsa sonuç reddedilir.
- Yeni/esnek eşleşmeler için katalog ve kaynak sayfasında yılın bulunması ve eşit olması zorunludur. Bilinmeyen yıl ile esnek kabul yapılmaz.
- Aynı arama cevabında iki ayrı uygun kaynak sayfası varsa `ambiguous-pages` ile reddedilir; ilk sonuç rastgele seçilmez. Kaynak sırası hâlâ katalog başlığı, ardından gerekirse özgün başlıktır; bütün olası site sayfalarının tarandığı iddia edilmez.
- Önceden çalışan tam başlık/eksik yıl davranışı korunur. Kaynak alan adı, MovieLoadResponse tipi ve boş playback data denetimleri korunur.
- Tek arama veya sayfa hatası alternatif ad/sonraki sonuçların değerlendirilmesini tamamen kesmez; iptal dışarı iletilir.

Yeni sorgu üretimi, TMDb alternative_titles ağ isteği, ekstra extractor, oynatıcı, timeout değişikliği veya yeni stream parser eklenmedi. EA-FB mevcut `title` ve `original_title` adaylarını kullanmaya devam eder. Bu adlar kaynakta yeterli değilse V83 hâlâ sonuç bulamayabilir.

Başlık/yıl kontrolleri kaynak sayfasından bağımsız TMDb ID doğrulaması değildir. Gerçek cihazda doğru filmin ve görüntünün kontrolü kullanıcı tarafından yapılmalıdır.

## Tanı logları

`EA-FB-Bronze` INFO loglarında provider, TMDb ID, incelenen sonuç sayısı, teklif sayısı ve şu ret gerekçeleri bulunur:

`search-title`, `source-origin`, `media-type`, `load-title`, `year-mismatch`, `variant-needs-year`, `empty-data`, `ambiguous-pages`.

Arama/load hataları yalnızca aşama, sağlayıcı ve hata sınıfıyla kaydedilir. Başlık, kaynak/stream URL'si, cookie, nonce, X-Sp ve opaque playback data yazılmaz. Bu gizli veri koruması fixture log kayıtlarında da sınandı.

Mi Box için mevcut logları temizlemeden:

```bash
adb logcat -v threadtime -T 1 EA-FB-Source:I EA-FB-Bronze:I '*:S' > ea-fb-v83-mibox.log
```

Bir çalışan film ve beş kontrol filmini sırayla EA-FB'nin kendi PLAY düğmesinden deneyin. Bağımsız eklenti arama sonucunu açmak V83 wrapper başarısı sayılmaz.

## Değişen dosyalar

- `EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt`: film eşleştirmesi ve güvenli ret logları.
- `core-tests/clean/BronzeFilmSearchTest.kt`: yeni kontrollü film senaryoları.
- `core-tests/clean/android/util/Log.kt`: yalnızca test stub'ında log yakalama.
- `scripts/test-bronze-nl-land.sh`: film regresyon programının eklenmesi.
- `EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt`: V83 kimlik, ayrı ayar alanı ve V82'den salt okunur seçim aktarımı.
- `EA-FB/build.gradle.kts`: sürüm 83.
- `scripts/package-v83.py`, `tests/test_v83_package.py`: izole dağıtım ve koruma kontrolleri.
- `.github/workflows/v83-test.yml`: yalnızca yeni V83 dalında test/derleme/paketleme/yayın.
- `docs/v83/SOURCE-LICENSE`, `docs/v83/SOURCE-NOTICE.txt`, bu rapor.
- `dist-v83-test/`: yalnızca V83'e ait CS3, repo/eklenti listesi, build-info ve kaynak atıfları.

## Korunan bileşenler

`DiziBoxAdapter.kt`, `DiziYouAdapter.kt`, ortak `SourceSearchTitles`, `SourceEngine`, `Domain`, `PlaybackLinkBridge`, `EASettings` ve `EAProvider` kaynakları V82 ile aynı. DiziBox/DiziYou arama ve oynatma kuralları değiştirilmedi.

V83'ün ayar alanı `ea_fb_v83_test`; V82 alanı `ea_fb_v82_test` yalnızca okunur. Mevcut kurulumların ayarlarına yazılmaz. Kaynak kimlikleri ve dört kaynak aç/kapat politikası değişmez. Kimlik const'larının derleyici tarafından EASettings ve UI/provider metotlarına gömülen değişiklikleri tam sabit/metot eşleştirmesiyle kontrol edilir; bu sınıfların algoritma komutları değiştirilmez.

Paketleyici özgün Bronze DEX'lerinden **250/250 metot komut dizisinin** korunduğunu doğruladı. Ayrıca V82'den **2022 korunan runtime metodunun** komutları, yalnızca izinli V83 kimlik sabitleri normalize edilerek doğrulandı. Buna DiziBox/DiziYou, ortak bağlantı zinciri ve Bronze `resolveIncrementally` dahildir. Kaynak alan adı, URL politikası, HLS, loopback, referer/header, çerez, Rhino, FastPlay, altyazı ve extractor davranışları korunur.

## Test ve başarısız kontroller

- Düzeltme öncesi: yeni Odyssey fixture'ı V82'de beklenen biçimde başarısız oldu. Önceki geçen testler ve bu hata çıktısı incelendi; hata gizlenmedi.
- Düzeltme sonrası: 12 Kotlin test programı geçti; DiziBox/DiziYou gerçek adaptör fixture'ları, dört kaynak köprüsü, ayarlar, kimlik, engine, timeout, headers ve altyazılar dahil.
- Yeni film testleri: NL ve LAND için ayrı ayrı 18 kontrollü kabul/red/fallback senaryosu (toplam 36) geçti.
- Python: 4 Bronze paket, 3 CLEAN paket ve 7 V83 paket/koruma testi geçti (14 test).
- DiziBox/DiziYou değişikliğini simüle eden koruma testleri beklenen reddi üretti; gerçek dosyalara test için yazılmadı.
- Yerel temiz Android derlemesi: `:EA-FB:clean :EA-FB:make`, Gradle 8.12 / Kotlin 2.4 / Android API 35; başarılı.
- Yerel paketleme ilk denemelerinde koruma kontrolü V83 const kimlik gömülmelerini ve derlenmiş `search$suspendImpl` değişikliğini beklenen kapsamda tanımadığı için durdu. Metot farkları incelendi; yalnızca tam kimlik sabitleri ve onaylı film search implementasyonu istisnaları eklendi. Kontroller tamamen kaldırılmadı; sonraki paketleme geçti. Yayın bu başarısız denemelerde yapılmadı.
- Bir araç çağrısında geçici `exec-server transport closed` hatası oldu; aynı işlem yeniden erişim doğrulandıktan sonra tamamlandı.
- Beş gerçek site araması denendi; tamamı ortam ağ geçidinde `Tunnel connection failed: 403 Forbidden` ile engellendi. Güncel site sonucu, video erişilebilirliği veya stream oynatması doğrulanmış değildir.
- Görev sırasında Mi Box testi yapılmadı.

Kontrollü beş başlık senaryosu: Homecoming için `Spiderman Homecoming` ve Türkçe/İngilizce birleşik ad; Far From Home için noktalama; No Way Home ve Brand New Day için kelime sırası; Odyssey için birleşik ad. Bunlar canlı katalogdan alınmış cevaplar değil, hata sınıfını sınayan fixture'lardır. Yanlış devam filmi, farklı yıl, eksik yıl, ilgisiz birleşik parça, farklı load adı ve birden fazla uygun sayfa reddedildi.

## Yayın doğrulaması

- [GitHub Actions run 38008632529](https://github.com/eaatabay/EA-FB/actions/runs/38008632529): **Success**.
- [Job 114083199715](https://github.com/eaatabay/EA-FB/actions/runs/38008632529/job/114083199715): testler, Android derlemesi, paket doğrulaması, artifact yükleme ve yalnızca V83 staging yayını başarılı.
- [Yayın commit'i](https://github.com/eaatabay/EA-FB/commit/0527f30e5eaff8e4d6759123d73d59e001220f12): `0527f30e5eaff8e4d6759123d73d59e001220f12`.
- CS3: `dist-v83-test/EA-FB-V83-TEST.cs3`, **326914 bayt**.
- CS3 SHA-256: `40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66`.
- DEX SHA-256: `6299ab457279aebe3c48093ce2ffb51d3d6e7765276c2ab8061a1caac04e28fd`.
- İç kimlik: `EA-FB-V83-TEST`; sürüm: 83; görünen ad: `EA-FB V83 TEST`.
- GitHub raw repo.json, plugins.json, build-info.json ve CS3 okundu. Manifest/sürüm/kimlik/URL, byte boyutu, SHA-256 ve DEX checksum doğrulandı. Son paket üzerinde özgün 250 ve korunan 2022 metot karşılaştırması yeniden geçti.
- GitHub'daki CI paketi yerel doğrulanmış paketle byte-for-byte aynı. Yayın yalnızca CI tarafından yapıldı.
- CI build-info.sourceCommit: `f014f4e544d14f9d33593edf26bd438a9ee06058`; rapor commit'i yeni bir derleme başlatmaz.

Staging kurulum adresi:

`https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-v83-bronze-search-fix/dist-v83-test/repo.json`

Workflow yalnızca V83 dalını ve `dist-v83-test/` dosyalarını normal push ile günceller; force push, production deploy, Worker/D1 veya mevcut workflow değişikliği içermez. Sonuç raporu yolu workflow tetikleme listesine alınmadığından rapor commit'i ikinci bir paket yayını başlatmaz.

## Korunan uzak dallar

Yeni V83 dışındaki **22 mevcut uzak dalın tamamının** başları başlangıç/son `git ls-remote` karşılaştırmasında değişmedi.

| Dal | Değişmeyen commit |
|---|---|
| `main` | `d7de664092a9236da477783f4cc35b5f7bf895d3` |
| `archive/frozen-v49-20261002` | `c3544d1d9dd346ac8737d0fcf21c67e3cd1da13a` |
| `test/clean-codex-fix-20261009` | `649a45cdc50c86a86d4134ffb1668838275b19e8` |
| `test/clean-codex-bronze-nl-land-20261009` | `88d7966fedf5150c38e4c898da86c482f1284b70` |
| `archive/frozen-v81-mibox-working-20261009` | `88d7966fedf5150c38e4c898da86c482f1284b70` |
| `test/ea-fb-v82-name-cleanup` | `79d17e9c4157bbf176a0e996cb843fc5c3db822b` |

V81 paketinin GitHub'daki sabit commit'ten okunan boyutu 322124 bayt, SHA-256 `e9ebd976fa0f083752689d76735330a4e50892bc8a828479c46b78bb32bd9942`; beklenen değerle aynı. Korunan dağıtım dosyaları ve eski workflow'lar V82 git ağacıyla aynı kaldı. Production Worker/D1, manifest, kısa kod veya yayın adreslerine yazma/dağıtım yapılmadı; production servislere test isteği gönderilmedi.

## Son durum

Başlık eşleştirme kusuru kontrollü testte üretildi ve NL/LAND film wrapper'ında düzeltildi. Cihazdaki beş filmin tamamının çözüldüğü veya V83'te oynatıldığı iddia edilmiyor. Eksik yıl, tanınmayan alternatif ad, kaynak alan adı değişimi, timeout ve canlı site davranışı yeni tanı loglarıyla ayrılmalıdır.

## Sabit kod kanıtları

- [V82 arama ve load başlık kapıları](https://github.com/eaatabay/EA-FB/blob/79d17e9c4157bbf176a0e996cb843fc5c3db822b/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L31).
- [V83 yalnızca film dispatch](https://github.com/eaatabay/EA-FB/blob/f014f4e544d14f9d33593edf26bd438a9ee06058/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L33).
- [V83 film arama ve ret nedenleri](https://github.com/eaatabay/EA-FB/blob/f014f4e544d14f9d33593edf26bd438a9ee06058/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L69).
- [V83 yıl doğrulaması ve belirsiz sonuç reddi](https://github.com/eaatabay/EA-FB/blob/f014f4e544d14f9d33593edf26bd438a9ee06058/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L94).
- [Kontrollü isim eşleştirmesi](https://github.com/eaatabay/EA-FB/blob/f014f4e544d14f9d33593edf26bd438a9ee06058/EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt#L155).
- [Film regresyonları](https://github.com/eaatabay/EA-FB/blob/f014f4e544d14f9d33593edf26bd438a9ee06058/core-tests/clean/BronzeFilmSearchTest.kt#L21).
- [Kaynak/runtime koruma kontrolü](https://github.com/eaatabay/EA-FB/blob/f014f4e544d14f9d33593edf26bd438a9ee06058/scripts/package-v83.py#L130).
- [İzole workflow](https://github.com/eaatabay/EA-FB/blob/f014f4e544d14f9d33593edf26bd438a9ee06058/.github/workflows/v83-test.yml).
