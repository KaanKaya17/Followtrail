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

## 0.1.2

- Beş JVM testi geçti. Tarih biçimli Retry-After alanına eklenmiş geçersiz son ekler de reddediliyor.
- API 35 / Android 15 x86_64 emülatörde 14 instrumentasyon testi geçti: 8 API sınırı/iptal testi, 3 Keystore/manifest/istek aralığı testi ve 3 ekran akışı testi. Son kaynaklarla test APK'ları yeniden derlenip çalıştırıldı: `OK (14 tests)`, 9.88 saniye test süresi (derleme/kurulum hariç).
- Taşıma testleri gerçek `InstagramApi.read` akışını sahte HttpsURLConnection ile çalıştırır: sabit HTTPS adresi/başlıklar, yönlendirmeme, HTTP 429/401/403/302/500, challenge, bozuk/aşırı büyük gövde, bağlantı hazırlama hatası, zaman aşımı, bağlantı oluşurken ve yanıt beklerken iptal, birden fazla gate örneğinde tek istek rezervasyonu.
- Ekran testleri gerçek Activity'de bağlantısız ilk ekranı, sınırlama sırasında kayıtlı sentetik sayıları ve ekran yeniden oluşmasını, bozuk oturumda kurtarma seçeneğini sınar. Gerçek hesaba ağ isteği atılmaz. Testler normal release paketinden ayrı `.debug` paketinde çalışır.
- Debug/release/test APK derlemesi ve lint geçti. Son lint raporunda hata veya uyarı yok. Bağımlılık kilidi üretildi; kilidi değiştirmeden normal `testDebugUnitTest lintRelease assembleRelease` kontrol komutu da geçti.
- Önceki GitHub CI çalışmasının SDK kurulumu `Failed to find package 'tools'` hatasıyla testlere ulaşamadığı doğrulandı. Kaldırılmış paketin varsayılan kurulumuna dayanan action güncellendi; SDK paketleri açıkça seçildi. CI yeni commit'te ayrıca izlenecek.
- Release APK kontrol script'i derlenmiş manifestte yalnız INTERNET izni, debug/backup/cleartext kapalı durumu, API 24/36 hedefi ve native kütüphane bulunmadığını doğruladı. Bu kontrol şifreleme, gerçek hesap veya penetrasyon testinin yerine geçmez.
- GitHub `40079866546` / `d78ba8d` çalışmasında verify işi geçti. İki emülatör işi test başlamadan varsayılan 7372.80 MB userdata bölümü için yeterli disk bulamadı (yaklaşık 6.9–7.2 GB boş alan). Bunlar test başarısı sayılmadı. Emülatör veri bölümü yereldeki gibi 2048 MB ve RAM 1536 MB olarak açıkça sınırlandı; açılış zaman aşımı 300 saniye yapıldı.
