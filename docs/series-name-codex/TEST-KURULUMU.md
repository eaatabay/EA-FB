# Mavi CloudStream / Mi Box 8502 güvenli test kurulumu

Durum: yazılım kontrolleri ve CI sonucu ayrı raporlanır. Bu ortam Mi Box'a bağlı değil. Aşağıdaki cihaz kontrolleri kullanıcı tarafından yapılmadan cihaz izolasyonu/oynatma başarılı sayılmaz. Mavide V83 kurulu olduğu varsayımı kaldırılmıştır; eski Bunny test sürümü kaldırılacaktır.

1. Yalnız MAVİ CloudStream'de eski EA-FB/Bunny eklentisini ve onu yeniden yükleyebilecek eski EA-FB test deposunu kaldır. Aynı uygulamada başka EA-FB kalmasın. Diğer kaynak depolarına, diğer CloudStream'e ve V83'e dokunma. Mavi uygulamanın verilerini temizleme.
2. Mi Box Android Ayarları → Uygulamalar → görsel olarak teyit ettiğin MAVİ CloudStream → Zorla durdur. Yalnız maviyi durdur. Eski hook'ların süreçten çıkması için zorunlu; yalnız eklentiyi kaldırmak yeterli kabul edilmez. Maviyi yeniden aç.
3. Bilgisayarda resmî Android platform-tools (`adb`) ve Python 3 kullan. Mi Box'ta geliştirici/ADB erişimini aç ve yalnız kendi bilgisayarını yetkilendir. `adb devices -l` ile cihaz SERI değerini bul. Ağ ADB gerekiyorsa `adb connect MI_BOX_IP:5555`; portu dış internete açma.
4. Mavi açıkken `adb -s SERI shell dumpsys activity activities` çalıştır. `mResumedActivity` veya `topResumedActivity` satırındaki `/` öncesi gerçek paket adı MAVI_PAKET'tir. Diğer CloudStream'i normal şekilde açıp aynı salt okunur komutla DIGER_PAKET'i kaydet; sonra MAVİYİ tekrar öne getir. Renkten/stable-prerelease adından paket tahmin etme. İki uygulama da canlı kalmalı; script diğerini başlatmaz/durdurmaz.
5. Bu daldaki `scripts/verify-blue-isolation.py` dosyasını bilgisayara indir. Kaynak: https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-series-name-codex-20261019/scripts/verify-blue-isolation.py

```sh
python3 verify-blue-isolation.py --serial SERI --blue MAVI_PAKET --other DIGER_PAKET
```

Bu ilk komut salt okunur: primary user=0; foreground paket; ayrı ve paylaşılmayan UID'ler; ayrı private dataDir; iki ayrı canlı süreç aranır. Raporu sakla. Hata varsa KURMA, UID/kapı kontrolünü devre dışı bırakma. Bilinmeyen klon paketleri desteklenmez.

6. İlk kontrol geçtiyse aynı değerlerle açık yerel izin kaydını oluştur:

```sh
python3 verify-blue-isolation.py --serial SERI --blue MAVI_PAKET --other DIGER_PAKET --arm
```

Yalnız `/sdcard/Android/data/MAVI_PAKET/files/ea-fb-blue-isolation-8502.json` yazılır ve geri okunarak doğrulanır. Diğer uygulamaya hiçbir dosya yazılmaz. Bu işlem eski eklentiyi kaldırmaz; 1–2. adımlar ayrıca zorunludur.

7. Yalnız MAVİ CloudStream → Ayarlar → Eklentiler → Depo ekle bölümünde bu adresi kullan:

https://raw.githubusercontent.com/eaatabay/EA-FB/test/ea-fb-series-name-codex-20261019/dist-series-codex-8502-test/repo.json

Yalnız `EA-FB SERIES CODEX 8502 TEST` yükle. 8501 veya V83 yükleme. Paketin internalName değeri `EA-FB-SERIES-CODEX-8502-TEST` olmalı. Depo indirme/auth hatası izolasyon kapısını değiştirme gerekçesi değildir.

8. Eklenti kendi OS bilgilerini tekrar kontrol eder. Bozuk/eksik marker, UID/dataDir değişimi, ortak UID, farklı süreç, diğer pakete erişemeyen Android PackageManager veya aynı uygulamada başka EA-FB: yükleme engellenir. Bu güvenli ret başarılı cihaz testi değildir. Maviyi bir kez tamamen kapatıp aç; yalnız bir yeni EA-FB provider ve tek hook seti göründüğünü kontrol et. Tekrar başlangıçta çoğalma olmamalı.
9. Yalnız test paketinin kendi ayarlarında dört kaynağı önce tek tek, sonra birlikte dene: dizibox, diziyou, hdfilmcehennemi-nl, hdfilmcehennemi-land. Organize İşler (2026) S1E1 ve S1E8 doğru dizi/bölüm olmalı. Organize İşler (2005) ve Sazan Sarmalı (2019) film olarak ayrı kalmalı. Detay/bölüm ekranı ve ayarlar tekrar açılınca provider, sekme veya stil çoğalması olmamalı. Kaynak listesi ve gerçek oynatma sonucunu ayrı kaydet; bu ortamda oynatma denenmedi. Kaynak erişim hatasını oynatma başarısı diye kaydetme.
10. Diğer CloudStream'i normal açıp mevcut V83/ayar görünümünün korunduğunu kullanıcı olarak gözle. Hiçbir eklenti kurma/kaldırma, veri silme veya ayar değişikliği yapma. Cihaz raporu: iki paket/UID/dataDir/process, eski Bunny'nin kaldırılması ve soğuk mavi başlangıcı, 8502 yükleme durumu, UI/provider/ayar, her kaynak arama ve oynatma sonucu ayrı maddelerle saklanmalı.

Geri alma: yalnız MAVİDE 8502 ve yeni test deposunu kaldır; yalnız maviyi zorla durdur; marker dosyasını bilgisayardan `adb -s SERI shell rm /sdcard/Android/data/MAVI_PAKET/files/ea-fb-blue-isolation-8502.json` ile sil (MAVI_PAKET önceden doğrulanan sabit paket adı). Diğer CloudStream/V83/production'a işlem yapma. ADB testini bitirdiğinde kendi cihazında hata ayıklamayı kapat.
