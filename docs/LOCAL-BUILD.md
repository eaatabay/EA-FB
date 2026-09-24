# EA-FB — GitHub Actions kullanmadan yerel derleme

EA-FB, CloudStream içinde **tek eklenti** olarak tasarlanmıştır. Derleme ve ilk cihaz testi tamamlanmadan kurulabilir sürüm ilan edilmez.

## Windows gereksinimleri

JDK 21, Gradle 8.12, Python 3 ve Android SDK (API 35 ve build-tools) kur. ANDROID_HOME veya ANDROID_SDK_ROOT tanımlı olsun. Java, Gradle ve Python komutlarının PATH üzerinde çalıştığını doğrula.

Proje kök dizininde PowerShell açıp şu komutu çalıştır:

    powershell -ExecutionPolicy Bypass -File .\scripts\build-windows.ps1

Bu betik CloudStream Gradle eklentisinin EA-FB:make ve makePluginsJson görevlerini yerel bilgisayarda çalıştırır. Ardından scripts/stage-release.py, gerçekten oluşturulmuş tek .cs3 arşivini ve Gradle manifestini kontrol ederek dist/ klasörüne aktarır. Yalnızca başarılı sonuçta dist/EA-FB.cs3, dist/plugins.json ve dist/repo.json birlikte oluşturulur.

GitHub Actions kullanılmaz. İlk Gradle çalıştırması Android ve CloudStream bağımlılıklarını internetten indirecektir.

Paketleme betiğinin bağımsız testleri:

    python -m unittest discover -s tests -v

Yukarıdaki testler, Android SDK ile gerçek derleme veya telefon/TV üzerinde oynatma testi değildir. Cihaz testinde Big Buck Bunny deneme filmi ve tek EA-FB sağlayıcısı kontrol edilmelidir. Canlı TV için config/channels.json şimdilik boş bırakılmıştır; yayın hakları ve çalışma durumu doğrulanmış kaynaklar daha sonra eklenecek.

Testlerden sonra dist/ klasöründeki üç dosya aynı GitHub commit'inde yayımlanabilir. Oluşacak repo URL'si:

    https://raw.githubusercontent.com/eaatabay/EA-FB/main/dist/repo.json

Bu URL henüz etkin değildir: dist/ içinde gerçek ve test edilmiş derleme bulunmuyor. ea-fb kısa kodu da henüz kayıtlı değildir.

TMDb erişim anahtarlarını veya kişiye özel yayın adreslerini public repoya yükleme.
