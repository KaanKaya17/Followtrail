# Doğrulama kaydı

24 Ekim 2026. Sürümler ayrı değerlendirilir; Windows prototipinin canlı başarısı Android başarısı olarak sayılmaz.

## 0.1.0

- `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintRelease` geçti. Dört JVM testi: sıfır/eksik/bozuk sayaç ve hesap kimliği, çerez başlık sınırı, HTTPS alan sınırları. Lint hata yok; Türkçe sabit metinlerle ilgili yerelleştirme uyarıları var.
- Release APK 685613 bayt. SHA-256: `146ea97979d2e6741029baccd947219d20e61967fe6571013c745c2d2a2f152b`. İmza v2/v3 doğrulandı; anahtar repoya alınmadı.
- Android 13, M2101K7BG fiziksel telefona kullanıcı tarafından kuruldu ve açıldı. İlk ADB yüklemeleri cihaz kısıtlamasıyla reddedildi; APK İndirilenler'e aktarılarak normal kurulum yapıldı.
- Kullanıcı Instagram girişini yaptığını, tamamla düğmesine birden fazla bastıktan sonra sınırlama mesajını gördüğünü ve sayı ekranına ulaşamadığını açıkça doğruladı. Android canlı sayaç ve otomatik yeniden açılış başarısı yoktur.
- Android 15 / API 35 x86_64 emülatörde ilk ekran açıldı; ekran görüntüsü incelendi. 1080×2340 ölçekte karşılama ekranında taşma görülmedi. Küçük ekran ve büyük yazı erişilebilirliği ayrıca sınanmalı.
- Android 15 emülatörde Android Keystore/izin instrumentasyonu yeniden çalıştırıldı: `OK (2 tests)`. Gerçek Android Keystore şifreleme/çözme, bozulan ciphertext'in reddi, silme ve INTERNET/backup manifest kontrolleri geçti. Bunlar sentetik oturumla test edildi; gerçek hesap erişimini doğrulamaz.
- Android 7 minimum, farklı üreticiler, 32-bit ARM, uzun oturum ömrü, gerçek Android girişten sonra yeni süreçte otomatik okuma ve çevrimdışı akış henüz doğrulanmadı. APK'da native işlemci kütüphanesi yok; bu uyumluluk hedefini kolaylaştırır, gerçek cihaz testinin yerine geçmez.

## 0.1.1

- Beş JVM testi geçti; önceki dört teste `Retry-After` saniye/tarih ve eksik/bozuk/taşan sürelerin yerel beklemeye dönüşmesi eklendi.
- Son kaynak için debug/release/test APK derlemesi ve `lintRelease` geçti. Türkçe sabit metinlerin yerelleştirilmesiyle ilgili lint uyarıları sürüyor.
- API 35 emülatörde son debug APK ve test APK kurularak `am instrument -w dev.kaankaya.instagramtracker.test/androidx.test.runner.AndroidJUnitRunner` çalıştırıldı: `OK (3 tests)`. Şifreleme/bozulma/silme ve manifest testlerine ek olarak istek aralığı ve 429 molasının yeni `RequestGate` örneğinde korunması, API'nin mola sırasında ağ açmadan reddetmesi ve süre sonrası açık bir çağrıya izin vermesi doğrulandı. Gerçek Instagram hesabına test isteği atılmadı.
- İmzalı release: 685613 bayt, SHA-256 `9a4f00c1e79bb691be12f69d89e519e09514a4afb5eaee6ff0a65fe2bf7439dc`. APK imzası v2/v3 doğrulandı. Telefonun `/sdcard/Download/InstagramTracker-0.1.1.apk` yoluna kopyalandı; kullanıcı kurulumunu doğrulayana kadar cihazda güncellenmiş sayılmaz.
- 0.1.0 geçmiş sınırlama zamanını saklamıyordu; güncelleme geçmişteki 429'u kendiliğinden bilemez. Kullanıcıya yeni giriş denemesi yapmaması bildirildi. 0.1.1 sonrası gözlenen sınırlamalar kalıcı mola uygular.
- Android'de gerçek sayaç yanıtı ve yeni süreçte otomatik yenileme hâlâ doğrulanmadı. Sınırlamayı aşmaya yönelik oturum değiştirme, IP döndürme veya otomatik yeniden giriş yapılmadı.
