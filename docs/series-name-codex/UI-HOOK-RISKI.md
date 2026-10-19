# 8503 / tek EA-FB test kurulumu

Kullanıcının son talimatıyla runtime izolasyon kapıları kaldırılmıştır; yeni bir güvenlik katmanı eklenmez. Hedef mavi STABLE'da eski Bunny EA-FB ve deposu kaldırıldıktan, uygulama yeniden açıldıktan sonra tek yeni EA-FB testidir. Kırmızı BETA ve V83'e dokunulmaz. Aynı uygulamada iki EA-FB birlikte çalıştığı iddia edilmez.

UI kaynakları, provider ve oynatma sistemi değiştirilmedi. Kaynak/DEX karşılaştırması UI, film arama ve resolver algoritmalarını korur; EAPlugin.load normal V83 yükleme talimatlarıyla tekrar aynıdır. Test adı/manifesti/ayar alanı 8503'e özeldir. Organize İşler düzeltmesi ve dört kaynak fixture/regresyon testleri korunur.

UI-HOOK-INCELEMESI.json eski V83 callback incelemesinin tarihsel kanıtıdır; gerçek Mi Box UI/oynatma kabulü değildir. Mi Box davranışı kullanıcı testine kadar doğrulanmamış kalır. Güncel kurulum TEST-KURULUMU.md, güncel geri alma GERI-ALMA-PLANI.md içindedir.
