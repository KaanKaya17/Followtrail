# Doğrulama kaydı

24 Ekim 2026. Sürümler ayrı değerlendirilir; Windows prototipinin canlı başarısı Android başarısı olarak sayılmaz.

## 0.1.0

- `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintRelease` geçti. Dört JVM testi: sıfır/eksik/bozuk sayaç ve hesap kimliği, çerez başlık sınırı, HTTPS alan sınırları. Lint hata yok; Türkçe sabit metinlerle ilgili yerelleştirme uyarıları var.
- Release APK 685613 bayt. SHA-256: `146ea97979d2e6741029baccd947219d20e61967fe6571013c745c2d2a2f152b`. İmza v2/v3 doğrulandı; anahtar repoya alınmadı.
- Android 13, M2101K7BG fiziksel telefona kullanıcı tarafından kuruldu ve açıldı. İlk ADB yüklemeleri cihaz kısıtlamasıyla reddedildi; APK İndirilenler'e aktarılarak normal kurulum yapıldı.
- Kullanıcı Instagram girişini yaptığını ve tamamla düğmesine birden fazla bastıktan sonra sınırlama mesajını gördüğünü bildirdi. Bu beyan canlı iki sayaç okumasını ve otomatik yeniden açılışı henüz doğrulamaz.
- Android 15 / API 35 x86_64 emülatörde ilk ekran açıldı; ekran görüntüsü incelendi. 1080×2340 ölçekte karşılama ekranında taşma görülmedi. Küçük ekran ve büyük yazı erişilebilirliği ayrıca sınanmalı.
- Android Keystore/izin instrumentasyon paketi derlendi ve başlatıldı; önceki araç oturumundan tamamlanma sonucu alınamadığı için geçti sayılmadı. Yeniden çalıştırılacak.
- Android 7 minimum, farklı üreticiler, 32-bit ARM, uzun oturum ömrü, gerçek Android girişten sonra yeni süreçte otomatik okuma ve çevrimdışı akış henüz doğrulanmadı. APK'da native işlemci kütüphanesi yok; bu uyumluluk hedefini kolaylaştırır, gerçek cihaz testinin yerine geçmez.
