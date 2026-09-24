# Codespaces ile EA-FB derleme

Bu yöntem KEMAL'e Android SDK kurmadan CloudStream eklentisini GitHub Codespaces üzerindeki Linux bilgisayarda derlemek içindir. GitHub Actions kullanılmaz.

## İlk kurulum

1. GitHub'da EA-FB deposunu aç.
2. **Code → Codespaces → Create codespace on main** seç.
3. İlk açılışta Java 17, Gradle 8.12 ve Android command-line tools otomatik hazırlanır.
4. Terminal hazır olunca şu komutu çalıştır:

```bash
bash .devcontainer/install-android-sdk.sh
```

Android'in SDK lisansları ekranda gösterilir. Lisansları inceleyip istemleri kendin onayladıktan sonra API 35, build-tools 35.0.0 ve platform-tools kurulur.

## Derleme

Sonra:

```bash
bash scripts/build-codespace.sh
```

Başarılı olursa gerçek CloudStream Gradle görevleri çalışır ve `dist/` altında:

- `EA-FB.cs3`
- `plugins.json`
- `repo.json`

hazırlanır.

Bu işlem yalnız **derleme** yapar; dosyaları GitHub'a otomatik yüklemez ve GitHub Actions tüketmez. İlk Android cihaz testi geçmeden `dist/` yayımlanmaz.

## Neden Java 17?

CloudStream'in resmi TestPlugins şablonundaki derleme akışı JDK 17 kullanıyor. EA-FB de aynı AGP 8.7.3 / Kotlin 2.1.0 hattını izlediği için Codespaces tarafında JDK 17 sabitlendi.

## Sonraki derlemeler

Aynı Codespace duruyorsa yalnız:

```bash
bash scripts/build-codespace.sh
```

yeterlidir. Codespace silinirse yeni bir Codespace açıp Android SDK lisans/kurulum adımını bir kez daha yaparsın.

## Gradle eklentisi bağımlılığı

CloudStream'in Gradle eklentisinin JitPack'te hazır paket olarak bulunamaması üzerine derleme betiği artık resmi GitHub kaynağının `69fdb8fc4be2815cbdf5ecec7f407727001cc7c7` commit'ini `vendor/cloudstream-gradle/` klasörüne indirip yerel Gradle composite build kullanır. Bu klasör GitHub'a yüklenmez; proje açıldığında tekrar oluşturulabilir. Üçüncü taraf `jadb` bağımlılığı gibi başka ağ sorunları çıkarsa ayrı incelenecektir. Upstream Gradle kaynağı LGPL-3.0 lisanslıdır.

Mevcut Codespace terminalinde düzeltmeyi almak için:

```bash
git pull
bash scripts/build-codespace.sh
```

`bash scripts/build-codespace.sh` ilk çalıştırmada kaynak indirmesini kendisi yapar. Gerçek `.cs3` oluştuğunu terminaldeki başarı mesajıyla doğrula.
