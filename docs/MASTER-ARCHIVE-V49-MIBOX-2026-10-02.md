# EA-FB MASTER — V49 Mi Box doğrulama arşivi (2026-10-02)

## Kapsam ve korunacak durum
- Repo: `eaatabay/EA-FB`.
- V49 test dalı: `fix/season-title-refresh-v48`.
- V49 derlenmiş paket: `dist/EA-FB.cs3` (dal üzerinde).
- V49 test depo adresi: `https://raw.githubusercontent.com/eaatabay/EA-FB/fix/season-title-refresh-v48/dist/repo.json`.
- V48 STREAM kapalı/yedekte; V49 TEST açık; mavi sürüme dokunulmadı.
- V49'un mevcut kırmızı staging yayınına kalıcı terfisi **henüz yapılmadı**.

## Kullanıcının Mi Box üzerinde doğruladığı sonuçlar
1. **The Simpsons sezon/bölüm adları:** V48'de sezon seçilince İngilizce adlar kalıyor, sağa bölüm listesine odaklanınca Türkçe oluyordu. V49 TEST tek etkin kırmızı eklentiyken kullanıcı sezon seçimiyle bölüm adlarının hemen Türkçe geldiğini doğruladı. Bu, V49 sezon seçiminde yeniden çizim düzeltmesinin cihaz üzerindeki olumlu testidir.
2. **Cennetin Doğusu / gelecek bölüm:** Kullanıcı 2026-10-02'de V49 üzerinde önceki gün (2026-10-01) için takılı kalan `Gelecek Bölüm` bilgisinin artık görünmediğini doğruladı. Kullanıcının ifadesi: “Cennetin doğusuda tamam gitmiş gelecek bölüm”.
3. İki depo aynı `EA-FB` eklenti kimliğini kullandığı için ana sayfada iki ayrı kaynak çıkmıyor; V48 kapatılıp V49 tek etkin kırmızı eklenti yapılarak test edildi.

## Korunacak ürün kuralı
- Gelecek bölüm göstergesi yalnızca **henüz yayınlanmamış** gerçekten gelecek bölüm için görünmeli.
- Bölümün tarihi geçtiyse, yayınlanmışsa veya son bölümden sonra yeni bölüm yoksa `Gelecek Bölüm` gösterilmemeli.
- Aynı gün bütün bölümler yayınlandıysa ve başka gelecek bölüm yoksa gösterilmemeli.
- Yeni bir gelecek bölüm varsa ona geçilmeli.
- Bu test sonucu kullanıcı gözlemidir; tüm diziler için genel regresyon testinin yerine geçmez.

## Sonraki iş
- V49'u kırmızı staging kalıcı yayınına almak için mevcut yayın mekanizmasını ayrı doğrula; mavi `main` üretimini koru.
- V48 yedeğini ve test dalını terfi tamamlanmadan silme.
