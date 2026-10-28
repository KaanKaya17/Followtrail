# Windows 0.2.10 — Tarama ilerlemesi doğrulaması

2026-10-28. Önizleme sürümü. Gerçek paketlenmiş EXE ve worker, sentetik şifreli `TrackerQA-*` depoları ve Instagram bağlantısını reddeden `--offline` test kipi kullanıldı. Gerçek kullanıcı deposu, hesabı veya çerezleri kullanılmadı.

## Çalıştırılan kontroller

- `python -m unittest discover -s interface/desktop/backend -p 'test_*.py' -q`: 116 test geçti. Yeni beş test üretim `Engine.scan`, `before_request` ve `after_response` sınırlarında sahte HTTP/ilerleyen saatle çalışır: profil ön hazırlığı, çok sayfa ve tekrar eden ID'lerin benzersiz sayımı, 15 saniyelik bekleme boyunca bağlamın korunması, eski olayların sonradan değişmemesi, iptal ve kısmi kayıt, 429'da durma/elle devam, ilk depo yazımı hatasında istemci temizliği.
- `npm run check`: ortak tema, 826 mesajlı Türkçe/İngilizce katalog ve JavaScript sözdizimi geçti.
- `node --test desktop/*.test.cjs`: 25 test geçti. Yeni kontroller iptal niyetinin geciken ilerlemeyle bozulmamasını, hizmet hatasında eski geri sayımın temizlenmesini, pencerenin gizlenme/küçültülme olayında askıya alınıp yeni arka plan verisi gelmese de tek güncel durumla açılmasını sınar.
- `node scripts/check-progress-desktop.mjs`: 9 yeni gerçek EXE senaryo grubu geçti. Saniyelerin azalması, aynı iptal düğmesinin korunması, yalnız tek 1 saniyelik timer, sayfa/hesap bilgilerinin korunması, tray ve native minimize/restore, gizliyken sıfır timer çağrısı ve sıfır durum çubuğu DOM değişikliği, sıfırda yeni komut gönderilmemesi, ağ yanıtı/alınan sayfa aşamaları, iptal/sonuç/hata temizliği, 429 için ayrı statik durum, Türkçe/İngilizce ve 960 piksel pencere genişliği sınandı. Geri sayım ekran okuyucuya her saniye yeniden duyurulmaz (`aria-live="off"`).
- `npm run check:functional`: 30 mevcut paketlenmiş masaüstü kontrolü geçti. `node scripts/check-navigation-desktop.mjs`: 10 kısayol/boş ekran senaryo grubu geçti. Yeni ilerleme akışı mevcut gizli pencere, yerel depo, bildirim, liste, kısayol ve dil davranışlarıyla birlikte doğrulandı.

Arayüz testleri yerel ana süreçteki gerçek `Backend.message` sınırına sentetik olaylar verir; iptal ve ayarlar mevcut IPC/worker yolunu kullanır. Üretime test endpoint'i veya test IPC'si eklenmedi. Arayüz dış isteği ve JavaScript hatası: **0**. Test raporu ve ekran görüntüleri yerel `artifacts/progress-ui/` altındadır; Git'e eklenmez.

## Düzeltilen bulgu ve kapsam sınırı

İlk EXE testinde tepsiye gizlenen Windows penceresinde `document.hidden` zamanında değişmedi; yalnız DOM görünürlüğüne bağlı geri sayım çalışmayı sürdürüyordu. Görünürlüğün sahibi olan ana süreç artık native hide/minimize olayında tek küçük askıya alma olayı yollar. Açılışta mevcut backend durumunu bir kez gönderir. Bu davranış hem Node sınırında hem gerçek pencereyle doğrulandı.

Sayfa toplamı bilinmediğinde tahmini toplam veya tamamlanma yüzdesi gösterilmez. Süre çubuğu yalnız yerel istek aralığını ölçer. 429 molası erişim garantisi değildir; otomatik tekrar başlatılmaz. Canlı liste taraması, gerçek Instagram 429 yanıtı, yeni giriş/2FA ve Windows toast teslimi bu değişiklikte yeniden doğrulanmadı. Önceki sınırlamayı sınamak için gerçek hesaba yeni istek gönderilmedi. Android 0.1.31 ve örnek web hesap işlevleri bu Windows değişikliğine dahil değildir.
