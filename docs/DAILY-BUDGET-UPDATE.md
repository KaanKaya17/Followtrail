# Günlük yerel bütçe — Windows 0.2.55 / Android 0.1.85

5 Kasım 2026. Kullanıcı günlük yerel sınırın artırılmasını açıkça istedi. Ortak `design/request-policy.json` üzerinden hesap günlük sınırı **3.000**, cihaz günlük sınırı **6.000** oldu. Saatlik sınırlar **120 / 240** kalır. Otomatik profil işleri aynı hareketli geçmişin %80 eşiğinde ertelenir: hesap için **96/saat, 2.400/gün**; cihaz için **192/saat, 4.800/gün**. Diğer işler tam toplam bütçeyi kullanır.

Bu ayar Meta'nın sunucu sınırını değiştirmez. İstek arası 6–10 saniye, hesap/cihaz kapsamı, mevcut rezervasyon, giriş aralığı, 429 ve Retry-After denetimleri korunur. Otomasyon, uygun bütçe olduğunda devam eder; yeni 429 alırsa durur. Yükseltme istek geçmişini veya sınırlama sayacını temizlemez. Önceki sürümde kullanıcı isteğiyle kaldırılmış yerel duruşlar bu sürümde genel olarak otomatik temizlenmez.

Kayıt kapasitesi eskiden 4.800 ile sınırlıydı. Daha yüksek bütçenin geçerli geçmişi depolama hatasına çevirmemesi için iki platformda `max(4800, günlük cihaz bütçesi)` kullanılır; güncel sınır 6.000'dir. Android JSON boyut sınırı değişmez; iki hesaba dağıtılmış tam cihaz gününün kodlayıcıdan kayıpsız geçtiği test edilir. Python'da tam geçmişin bütçe doluluğunda yeni rezervasyon eklenmeden durduğu doğrulanır. Eski 4.000 kayıt hâlâ hesap günlük sınırını doldurur; kayıtlar yaşlanmadan silinmez.

Doğrulama: 46 seçili Python testi ve 177 JVM testi geçti. Android release lint: 0 hata / 56 uyarı. Ortak politika üretim kontrolü ve 36 paketli Windows kontrolü geçti; paket testleri Instagram isteği göndermez. Android 0.1.85 / kod 86 APK’sı v2/v3 imzalı, manifest kontrolü geçti; 2.614.715 bayt, SHA-256 `a3f0a0a1953cf99aa4749b4ef6d268e4f45a1bb6caeb3e437385e2746d7d052d`.

Gerçek Windows devamı: Kullanıcının daha önce kapattığı ve tarafımızdan yeniden açılan, beklemedeki 0.2.54 süreci kimlik/yol ve boşta UI durumu doğrulanarak kapatıldı; 0.2.55 saat 16:49:37’de açıldı. 16:50:50 yerel okumada ortak ve hesap duruşu yoktu; uygulanan günlük politika 3.000/6.000, sayaç 7.950 → 7.953 ve son yanıt HTTP 200 idi. Sonraki otomatik iş 16:54:44’e planlanmıştı. Canlı sorgular yeni uygulamanın normal otomasyonuyla yapıldı; ayrı tanı istemcisinden ek Instagram isteği gönderilmedi. Hesap/oturum değerleri, yanıt gövdeleri ve kişi kimlikleri bu nota alınmadı. Gelecekte 429 gelmeyeceği veya listelerin tamamının verileceği iddia edilmez.

Telefon veya emülatörde yeni canlı Instagram girişi bu değişikliğin kapsamında değildir.
