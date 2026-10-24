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
- [GitHub CI 40081063588](https://github.com/KaanKaya17/InstagramTracker/actions/runs/40081063588), `433563704d4f2397db56abf88f070bba1a042697` commit'i için tamamen geçti: verify, Android 7 / API 24 ve Android 15 / API 35 işleri başarılı. Her iki cihaz işinde de 14 instrumentasyon testi başladı ve tamamlandı; derleme başarılı döndü. Bu sonuçlar x86_64 emülatörlere aittir; farklı üretici/ARM donanımı veya gerçek Instagram hesabı testi değildir.
- Küçük ekran kontrolü: ayrı yerel emülatör 720×1280 / 320 dpi ve %150 yazı boyutuna ayarlanarak üç ekran testi ayrıca geçti; ilk ekranın görüntüsü incelendi. Bu, tam TalkBack/erişilebilirlik sertifikası değildir.
- Dağıtılan imzalı 0.1.2 APK'sı `d78ba8d408c459d285e774265ae99fc3c09c5d86` kaynağından üretildi. CI düzeltmesi olan `4335637` ile uygulama (`app/`) Git ağacı aynıdır. APK içindeki VCS metadata'sı d78ba8d ile eşleştiği için dağıtılmış dosya yeniden paketlenmedi. Boyut 685613 bayt; SHA-256 `81813347aaa03175092cb9e526324ad84cd8cebc8fc717a7bb184fca10b4c9d7`.
- İmzalı APK yayın kontrolünü geçti; debug APK aynı kontrol tarafından reddedildi. 0.1.0/0.1.1/0.1.2 sertifika SHA-256 değeri aynı: `51f1412a8c037617d98df7b0e1dce84a3e825765d4cdc442b9b59a8d8f00fb75`. Telefonun İndirilenler klasöründeki 0.1.2 dosyasının SHA-256 değeri bilgisayardakiyle eşleşti; otomatik kurulum yapılmadı. Son okunan kurulu sürüm 0.1.1 idi.
- Yerel test emülatörü kapatıldı. Bu geliştirme/test çalışması sırasında gerçek Instagram hesabına yeni istek gönderilmedi. Gerçek iki sayaç, oturumun uzun vadeli geçerliliği ve fiziksel telefonda yeniden açılış testi açık kalıyor.


## 0.1.3

- 11 JVM testi geçti: önceki beş sınır testi ve altı kişi/girdi/web profil/v1 geçişi/geçmiş kimliği/sınır/önbellek testi.
- Debug/release/test APK derlemeleri ve lint geçti; son raporda hata veya uyarı yok.
- API 35 / Android 15 x86_64 yerel emülatörde son kaynaklarla 21 instrumentasyon testi geçti: 10 API sözleşmesi, 4 Keystore/manifest/kalıcı istek politikası, 4 kişi ekranı/diyalog akışı ve 3 önceki ekran akışı. Son koşu 17.07 saniye (derleme/kurulum hariç).
- Cihaz testleri 720×1280, 320 dpi ve %130 yazı ölçeğinde çalıştı. Kişi ekleme → not/grup kaydı → Activity yeniden oluşturma → kişi silme gerçek Android diyalogları ve şifreli kayıtla geçti. Kişi/geçmiş ekranlarında gezinme ağ rezervasyonu yapmadı; açık yenileme tek gözlem ekledi. Tüm profil yanıtları sentetik taşıma üzerinden verildi.
- İlk ek diyalog testi, erişilebilirlik aramasının hint alanını ve büyük harfli diyalog düğmelerini bulamaması nedeniyle başarısız oldu. Test kontrol bulucusu gerçek pencere ağacını ve harf farklarını kullanacak şekilde düzeltildi; başarısız koşular başarı sayılmadı.
- Release paket kontrolü geçti: üretim kimliği, yalnız INTERNET, debug/backup/cleartext kapalı, API 24/36, native kütüphane yok. Debug sentetik taşıma işaretleri release DEX içinde bulunmadı. Bu statik kontrol bağımsız güvenlik denetimi değildir.
- Gerçek hesaba yeni profil isteği gönderilmedi. Fiziksel telefon bu çalışma sırasında ADB'de bağlı görünmedi; cihazın mevcut sınırlama kaydı okunmadı. Eski uyarının yeni sunucu reddi mi yoksa saklanan mola mı olduğu mevcut kanıtla belirlenemedi.
- Bilgisayar önizlemesi gerçek APK ana kodunu kullanır; demo taşıması ve örnek veriler debug varyantına özgüdür. Canlı kendi hesap/başka profil erişimi, oturum sürekliliği ve farklı ARM/üretici cihazları hâlâ doğrulanmadı.
- Bu commit için GitHub API 24/35 kontrolleri ayrıca izlenecek; yerel sonuç CI başarısı olarak sunulmaz.
