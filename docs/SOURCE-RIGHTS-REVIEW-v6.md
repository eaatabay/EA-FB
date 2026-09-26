# EA-FB v6 — Kaynak izin ve adaptör onay protokolü

**Durum (26.09.2026): Gerçek video kaynağı onaylanmadı.**
`config/channels.json` içindeki yayın listesi boştur;
`ReviewedSourcePermits.bundled` boş kalır. Bu belge hiçbir siteye
bağlanma, video toplama, yayın tekrar sunma veya DRM atlatma izni vermez.

## Neden üç bağımsız onay gerekiyor?

1. **Yazılı hak/izin kaydı:** Yayıncı veya yetkili dağıtıcı, hangi
   içeriklerin hangi bölgede, hangi süre boyunca, hangi API/medya yöntemiyle
   sorgulanıp oynatılabileceğini belgelendirmeli. Sadece `authorized:true`,
   ulaşılabilen bir URL veya internette bulunması izin kanıtı değildir.
2. **Yayıma gömülü adaptör ve kaynak izni:** Onaylanan ID/sürüm, film-dizi
   kapsamı, tam HTTPS alan adları ve izinli yol öneki
   `ReviewedSourcePermits.bundled` içine **kod incelemesinden sonra**
   yerleştirilmeli. Kayıt en geç 366 gün içinde yenilenmeli; tarih dolunca
   kaynak kendiliğinden kapanır. Kod, ağdan yeni çalıştırılabilir adaptör almaz.
3. **İmzalı kaynak listesi:** Ayrı Worker yalnız onaylanan kaynağın geçerli
   adresini imzalayabilir. Android yeni aramada ancak imza, cihazdaki açık
   anahtar, gömülü adaptör sürümü ve gömülü izin kaydı **birlikte**
   uyuşursa kaynağı seçer. Kaynak listesi tek başına hukuki onay değildir.

## Her kaynak için hazırlanacak inceleme kaydı

Her kaynak için `rights/YYYY/<source-id>.md` yolunda ayrı bir dosya
açılacak. Gerçek izin yazısı veya sözleşme paylaşılmayacaksa yalnızca
güvenli belge referansı, izin sağlayan yetkili ve doğrulama tarihi
kaydedilecek; API anahtarı, oturum bilgisi ve özel medya URL'si GitHub'a
konmayacak.

| Alan | İncelemede istenen bilgi |
|---|---|
| Kaynak ID / adaptör sürümü | Sabit tanımlayıcı ve derlenecek sürüm |
| Hak sahibi / yetkili | Resmî site ve izin veren yetkili |
| İzin dayanağı | Lisans, resmî API koşulları veya yazılı izin referansı |
| İzin verilen işlem | Arama, detay, bölüm, metadata, medya oynatma ayrı ayrı |
| İçerik kapsamı | Film, dizi veya her ikisi |
| Coğrafi ve zaman kapsamı | İzinli bölge, başlangıç ve bitiş tarihi |
| İzinli ağ yüzeyi | Tam HTTPS host listesi, kesin URL yol öneki, yönlendirme |
| Kaynak istek bütçesi | Sıklık, eşzamanlılık, kota ve zaman aşımı |
| Sınırlamalar | Oturum, kullanıcı aboneliği, DRM ve yeniden dağıtım hükümleri |
| Test sonucu | İzinli fixture, ağ güvenliği, parser ve geri alma testleri |
| Kullanıcı onayı | Kaynak özelinde açık nihai onay ve tarih |

## Kanit referansi denetiminin siniri

`ReviewedSourcePermitPolicy`, `evidenceReference` alaninin yalnizca
`rights/YYYY/source-id.md` benzeri kanonik yerel yol bicimini ve
zaman/host/kapsam kaydini denetler. **Dosyanin gercekte varligini,
belgenin imzasini, hak sahibinin yetkisini, izin kapsaminda playback
bulunup bulunmadigini veya lisansin gercekligini dogrulamaz.**
Bu isler kaynak bazinda insan incelemesi, ayrica release onayi ve
kanit kaydinin release incelemesinde karsilastirilmasini gerektirir.
Yalnizca gecen bir Kotlin fixture testi, lisans kaniti degildir.
Production `ReviewedSourcePermits.bundled` halen bostur.

**Kayit kimligi baglayicidir:** Derlenmis izin kaydindaki `id` ile
`rights/YYYY/<id>.md` dosya adi ayni olmak zorundadir; yil dort
rakamli olmalidir. Baska kaynagin hak belgesine atif, ara klasor ve
kanonik olmayan yol reddedilir. Onceki yilin belgesi, kayitli
`validUntil` suresi henuz dolmamissa teknik olarak gecerliligini
koruyabilir. Bu denetim dosyanin gercekten var oldugunu veya hukuki
yetkiyi kanitlamaz; insan incelemesi halen zorunludur.

## Aktivasyon kontrol kapısı

- Yazılı hak doğrulaması ve kullanıcı onayı **yoksa** gerçek adaptör yok.
  Testlerde kullanılan `*.example.org` adresleri gerçek bir izin kaydı
  değildir ve hiçbir ağ isteğine tabi tutulmaz.
- Gerçek kaynak kontrolü sunucuda yapılacaksa DNS/rebind ve bütün yönlendirme
  hop'larında **gerçek bağlantı IP'si denetimi** ayrı çözülmüş olmalı.
  Sadece DNS ön kontrolüne veya sıradan Worker `fetch` davranışına güvenilmez.
- Önce ayrı test Worker + geçici D1 ve yerel/kurgusal adapter testleri
  geçmeli; gerçek kaynak testleri yalnızca yazılı erişim izni kapsamında.
- Daha sonra gerçek imza anahtarı, onaylanan tam host/yol öneki ve
  uygulama içine derlenen adaptörle Mi Box üzerinde yeni arama ve oynatma
  ayrı doğrulanmalı. Mevcut v5, `main`, canlı katalog Worker ve kullanıcı
  tarafından başlatılmış bir yayın bu süreçten etkilenmemeli.

## Şu andaki teknik durum

`ReviewedSourcePermitPolicy.kt` derleme sırasında yerleşik kalan izin
kayıtlarını tanır. Süresi dolan/gelecek tarihli/yinelenen kayıt,
yanlış sürüm, film-dizi kapsamı uyuşmazlığı, onay dışı host,
yol öneki dışına çıkış, kodlanmış yol geçişi ve IP literalini engeller.
Ayrı olarak imzalı `WatchdogApprovedAdapterBridge` bu izin denetiminden
geçmeden hiçbir adaptörü kullanıma vermez. İzin dizininin mevcut olması
veya buraya bir dosya eklenmesi **tek başına aktivasyon yapmaz**.
