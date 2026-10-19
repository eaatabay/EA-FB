# Değişiklik defteri

Tarih: 2026-10-19 (Europe/Istanbul). Temel: `16396702470eb1f81cc6dac4360e6b0ff84927d1`. Dal: `test/ea-fb-series-name-codex-20261019`.
Her mantıksal aşama ayrı commit olur. Bir commit kendi SHA'sını kendi içine yazamaz; SHA, sonraki kayıt commit'inde yer alır. Git geçmişi kesin kaynaktır; kayıtlar geriye dönük değiştirilmez.

## 1 — Başlangıç inceleme ve geri alma planı
- Önceki SHA: 16396702470eb1f81cc6dac4360e6b0ff84927d1; yeni SHA: bu kaydı oluşturan commit (`git log --format=fuller -- docs/series-name-codex`).
- Dosyalar: bu klasördeki başlangıç, uzak dal uçları, değişiklik defteri, devam notları, geri alma planı.
- Gerekçe: V83'ü ve geri dönüş sınırını sabitlemek; uygulama kodu değişmedi.
- Test: gerçek git ls-remote --heads/--tags; V83 sha256sum; temiz git status ve başlangıç farkı.
- Sonuç: V83 HEAD ve paket hash'i kullanıcı referansıyla aynı. PR/API erişimi Forbidden; eski dal temizliği bekliyor.
- Geri alma: yalnızca bu yeni daldaki commit'i git revert ile geri al; V83'e hiçbir işlem uygulama.
- GitHub bağlantısı: push doğrulandıktan sonra tam SHA ile sonraki kayıtta yer alacak.
