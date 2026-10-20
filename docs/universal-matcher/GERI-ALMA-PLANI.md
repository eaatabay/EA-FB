# Geri alma

Yalnız mavi CloudStream STABLE'daki EA-FB UNIVERSAL 8601 TEST eklentisini kaldır.
Kırmızı BETA V83'e, kullanıcı verilerine, production'a ve diğer uygulamaya işlem yoktur. Önceki dağıtımlar silinmez/üzerine yazılmaz. Kod geri alma yalnız yeni test dalındaki ilgili commit'leri ters sırayla git revert ile yapılır; V83 veya diğer dallarda reset/force-push yapılmaz.

Doğrulanmış test yayını: ed63dcdb5cf6d6ddf67b62d1777bd71d650171ff. Kaynak/CI: 9d56ba95a86c93097e9761958fa406841d87ddb1. Paket hash ve 24 korunan ref sonucu YAYIN-DOGRULAMA.json içinde. Tüm kod değişikliklerinin tam SHA’ları DOSYA-VE-COMMIT-ENVANTERI.md dosyasında; kod geri alma yalnız yeni test dalına uygulanır. Cihazda geri alma için tek işlem yeni test eklentisini MAVİDEN kaldırmaktır.
