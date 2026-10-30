# Android 0.1.53 giriş yönlendirmesi

## Davranış

- Giriş ekranında sayfa açılışı, bilgileri girme, Instagram yanıtı, kod istemi, CAPTCHA ve profil bağlantısı ayrı başlıklarla anlatılır. Kod veya CAPTCHA beklenirken ilerleme çubuğu çalışmaz. Kod alanının görünmesi yalnız alan türü/görünürlüğüyle belirlenir; değeri okunmaz.
- Instagram CAPTCHA istediğinde aynı yanıtta desteklenen bir challenge bağlantısı varsa kullanıcıya doğrulamayı açma düğmesi gösterilir. Meta/Google kaynakları mevcut açık rıza adımından önce kapalıdır. Kullanıcı CAPTCHA’yı kendisi tamamlar; otomatik çözüm yoktur.
- CAPTCHA işaretiyle birlikte desteklenmeyen/eksik bağlantı gelirse form kapatılır. Girişin tamamlanmadığı ve bunun yanlış parola kanıtı olmadığı açıklanır. Instagram’da kontrol et düğmesi yalnız sabit Instagram ana adresini açar; oturum veya yanıt bağlantısı aktarılmaz. Instagram’daki doğrulama Tracker’a otomatik oturum taşımaz.
- İstek bilgilerine yalnız sabit `challengeStatus` sınıflandırması eklenir. URL, query, token veya kullanıcı kodu kaydedilmez. Ağ izinleri genişletilmez. Mevcut mola, tek giriş ve hesap kayıtlarını koruma kuralları sürer.
- Kod istenince Instagram sayfasında kod girme/onaylama açıklanır; profil doğrulama düğmesi erken gösterilmez. Gerçek bir kod alanı bulunmadığında parolayı tekrar göndermek önerilmez.
- Sayfa yüklemesi 45 saniye tamamlanamazsa form kapatılır ve yönlendirme gösterilir. Ana sayfa yükleme hatası ilerleme çubuğunu durdurur. Sonradan engellenen kaynak veya gezinme önceki CAPTCHA sonucunu değiştiremez.

## Doğrulama kapsamı

30 Ekim 2026 yerel kontrolleri: 106 JVM testi, sıfır hata/başarısızlık; release lint sıfır hata, 24 uyarı. Çevrilebilir 642 TR/EN kaynak anahtarı eşleşir. İmzalı APK 0.1.53 / code 54, 2.249.368 bayt; SHA-256 `5a0b8c792a96c5440e555d605379701d9cf5f5fb773cf59c5843ea2e53e935b9`. Paket kontrolünde dört mevcut izin, kapalı backup/debuggable/cleartext ve sıfır native kütüphane doğrulandı.

API 37 Small_Phone emülatöründe `WebLoginScreenTest`: 18 vaka, sıfır başarısızlık, 169,806 saniye. Bunların 17’si çalıştırılan sentetik WebView senaryosu, biri açıkça etkinleştirilmediği için atlanan canlı genel giriş-formu testidir. Açılabilen CAPTCHA ve rıza sınırı, açılamayan CAPTCHA’da formun kapanması, kod başlığı/alanı ve değerlerin kaydedilmemesi, 45 saniyelik takılma sınırı, otomatik profil bağlantısı ve gezinme engeli doğrulandı. İlk turda form görünürlüğünü kaynak engelinden önce bekleyen eski tanı testi başarısızdı; test, kendi gönderdiği isteğin engel kaydını bekleyecek şekilde düzeltildi ve sınıfın tamamı tekrar geçti. Üretim ağ politikası gevşetilmedi.

Yalıtılmış `.debug` paketi ve sentetik yanıtlarla emülatör testleri kullanılır. Gerçek Instagram CAPTCHA / iki faktörlü girişinin uçtan uca tamamlandığı bu testlerden çıkarılamaz. 0.1.52 gerçek emülatör denemesinde CAPTCHA işareti vardı, desteklenen bağlantı yoktu ve hesap bağlanmadı. Bu sürümün kullanıcı yönlendirmesi o engeli görünür kılar; Meta’nın tüm CAPTCHA biçimlerini desteklediğini iddia etmez.

## Kullanıcı testi

1. Hesaplarım → Hesap ekle → Instagram ile bağlan yolunu aç. Kayıtlı hesapları kaldırma.
2. Bir kez giriş yap. Yanıt beklenirken tekrar gönderme.
3. Kod istenirse yalnız Instagram sayfasındaki alana gir veya istenen onayı Instagram’da ver. Kodu sohbete gönderme.
4. Güvenlik doğrulamasını açma düğmesi varsa açıklamayı okuyup kabul ediyorsan aç, CAPTCHA’yı kendin tamamla.
5. Doğrulama açılamadı açıklaması veya 429/güvenlik molası varsa yeni giriş başlatma. İstek bilgileri ekranı ağ isteği göndermez; tanı için açık bırakılabilir.

Fiziksel telefonda kayıtlı mevcut mola güncelleme nedeniyle silinmez. Mevcut telefon molası sırasında gerçek giriş denenmez.
