# Android 0.1.50 — çoklu hesap

30 Ekim 2026. Masaüstünde mevcut bağımsız şifreli kasa modeli Android'e de uygulanır. Ana ekran ve ⋯ menüsündeki **Hesaplarım**, kayıtlı hesaplar arasında seçim ve **Hesap ekle** sunar. Tema ve dil cihaz tercihi olarak ortaktır.

## Veri ve işlem sahipliği

- Şifreli `AccountCatalog`, hesapları sabit Instagram numeric ID ile tanımlar. Kullanıcı adı görüntü bilgisidir; değişince başka hesap yaratılmaz. Her hesap, Keystore AES-GCM ile şifrelenmiş ayrı ana kayıt, günlük, görsel arşiv ve liste arşivi kullanır. Web/mobile oturumlarının iki staging bankası da hesap başına ayrıdır. Katalog ve geçici bağlantı sahibi işaretçisi şifrelidir; parola tutulmaz.
- Eski tek hesap, eski `account` dosyası ve yardımcı dosyaları yerlerinde bırakılarak kataloğa eklenir. Eski oturum bankaları idempotent bir geçişle kendi hesap alanlarına kopyalanır; tamamlandı işaretçisi eski kopyaların yeniden canlı oturum sayılmasını engeller. Eski şifreli kopyalar ilgili hesabı silerken temizlenir.
- Bağlantı yalnız başarılı profil okumasından sonra yeni hesabı kaydeder ve seçer. İptal, sunucu hatası veya 429 önceki seçimi değiştirmez. Aynı hesaba yeniden bağlanma kişileri ve ayarlarını korur; eski tarihli liste gözlemleri korunur, önceki oturumun sayfalama cursor'ü kullanılmaz.
- Ekran işlemlerinin `expected Session` kontrolü sürer. Eski seçimin geç gelen sonucu başka hesaba yazılmaz. Arka plan ve resim arşivi işleri hesap sahibine sabitlenir; arka plan kaydı görünür seçimi değiştirmez. Bildirim bağlantısı doğru hesabı seçer ve mevcut hedefi açar.
- Silme önce şifreli katalogda işaretlenir. Oturumlar, listeler, ana kayıt, günlük ve görsel arşiv temizlendikten sonra katalog girdisi çıkarılır. Yarıda kalırsa sonraki RequestGate başlangıcı temizliği tekrarlar. Diğer hesaplar korunur; cihaz istek/molası kayıtları silinmez.

## Zamanlama ve güvenlik

- Hesap seçimi profil/liste isteği göndermez; yeniden oluşturulan ekranın otomatik profil yenilemesi ve ilk liste hazırlığı bu yerel geçişte başlatılmaz. Önceden izin verilmiş Android arka plan zamanlaması ayrıca devam edebilir. Mevcut genel CDN avatar gösterimi hesabın oturumunu kullanmaz.
- Her hesabın otomatik liste takibi ve izlenen kişi ayarları kendi kasasındadır. Yeniden bağlantı gereksinimi, liste kapasite durması ve açılışta yenileme tercihi hesaplara ayrıdır. İlk sürümde tema/dil ve bildirim kanalı cihaz genelindedir.
- Tek periyodik Android işi; 90 sn / en çok 6 işlem sınırı, dönüşümlü hesap sırası ve ortak RequestGate. Kayıtlı bütün hesaplar her turda kontrol edilecek diye garanti verilmez. Android'in periyodik iş alt sınırı ve çalışma koşulları değişmedi. API sayfalama aralıkları ve bütçe artırılmadı.
- HTTP 429, sunucu Retry-After, yerel mola, kimlik doğrulama aralığı ve saatlik/günlük istek bütçesi bütün hesaplar arasında paylaşılır. Hesap değiştirerek sınırlamayı kaldırma yoktur. Bir hesabın oturumu geçersizse ilgili hesabın otomatik işi durur; geçerli diğer hesaplar kendi sırasında kalır.
- 0.1.49 gerçek telefon testi: giriş başarılı, W02 profil HTTP 429; yerel mola 31 Ekim 02:01 (Europe/Istanbul). 0.1.50 geliştirmesinde yeni gerçek hesap girişi veya profil denemesi yapılmadı. Çoklu hesap özelliği bu kısıtın çözüldüğünü kanıtlamaz.

## Doğrulama

Üretim Keystore/katalog/kasa sınırları ve gerçek MainActivity seçim ekranı örnek verilerle emülatörde sınanır; gerçek Instagram'a bağlantı açılmaz. Testler geçişte byte değişmezliği, bağımsız notlar, üç web oturumu ve rotasyon, başarısız bağlantı, geç sonuç, hesap bazlı silme, ortak 429, dosyalarda düz metin bulunmaması, kesilen silme/staging işlemlerinin toparlanması, eski cursor'ün kullanılmaması, bildirim sahibinin seçimi ve dönüşümlü arka plan işlemlerini kapsar.

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`: son üretim derlemesi 2 dk 54 sn; 105 JVM testi / 0 başarısız; release lint 0 hata / 22 mevcut uyarı. Son küçük arayüz farkını debug APK ile eşitleyen derleme ve 105 JVM testi de 49 sn'de başarılı.
- TR/EN 634 kaynak eşleşiyor; değişken/çoğul sözleşmeleri ve `git diff --check` geçerli.
- İlk yeni özellik turundaki 10 çoklu hesap testi başarılı. Doğrudan bağlantı testi, kasaya yazımdan sonra arayüz günlüğünün de tamamlanmasını bekleyecek şekilde düzeltildi; üretim bağlantı sonucu değiştirilmedi.
- İlk geniş tur **80 test / 278,907 sn**, iki test beklentisi başarısız: yeniden bağlantı durumu artık yeni hesaba ait olduğundan eski ortak bayrak beklentisi; API 37 platformunun INTERNET iznine eklediği örtük ACCESS_LOCAL_NETWORK. Her iki beklenti ilgili yeni sınırda düzeltilir. [Android'in split permission açıklaması](https://developer.android.com/privacy-and-security/local-network-permission) SDK 36 hedefleyen uygulamalar için örtük izni açıklar; APK manifestine bu izin eklenmedi. Testte yalnız bu bilinen platform genişlemesi kabul edilir; diğer ekstra izinler yine reddedilir.
- İmzalı üretim `InstagramTracker-0.1.50.apk`, versionCode **51**, **2.241.176 bayt**, SHA-256 `e9763470356d25bb17628ab7ad682892159948b9fe3ebf5d1e2fa29b34d1ac45`. Mevcut üretim imzası doğrulandı. `scripts/verify-release.py` gerçek APK'da yalnız INTERNET / ACCESS_NETWORK_STATE / RECEIVE_BOOT_COMPLETED / POST_NOTIFICATIONS bulunduğunu; backup, debuggable, cleartext ve debug taşıyıcısının kapalı olduğunu doğruladı. Native kütüphane eklenmedi.

- Son düzeltilmiş tur **81 test / 230,652 sn / 0 başarısız**: MultiAccountTest (14), WebComparisonScreenTest, WebConnectionTest, MobileConnectionTest, PhoneLoginFlowTest, WebLoginScreenTest, DevicePrivacyTest, AccountSafetyBoundaryTest, InitialPreparationFlowTest. Gerçek API 37 emülatör, ağ kapalı; Keystore ve WebView gerçek, sunucu yanıtları sentetik. Gerçek Instagram hesabı başarısı olarak yorumlanmaz.
- Üretim paketi 30 Ekim yaklaşık 11:03 (Europe/Istanbul) API 33 telefona `adb install -r` ile kuruldu. Paket yöneticisi versionName 0.1.50 / versionCode 51 doğruladı; MainActivity başlatıldı ve uygulama süreci çalışıyor. Kaldırma/veri temizleme veya yeni giriş/yenileme komutu gönderilmedi.
- Kullanıcının açtığı İstek bilgileri yaklaşık 11:05'te USB üzerinden okundu: son sayaçlı işlem ve W02 hâlâ **30 Ekim 10:01 / HTTP 429**, seçilen web taşıyıcısı Android HttpEngine / Chromium, sunucu bekleme bilgisi belirtilmemiş; kalıcı yerel mola aynı şekilde **31 Ekim 02:01**. Güncelleme molayı sıfırlamadı ve son istek zamanı ilerlemedi. Telefonun hesap seçicisine uzaktan dokunulmadı; gerçek telefonda iki hesabın başarılı profil bağlantısı hâlâ doğrulanmadı.

Gerçek telefonda birden çok başarılı profil bağlantısı, sunucu sınırlaması nedeniyle henüz doğrulanmış değildir. Oturumlar Instagram tarafından geçersiz kılınırsa ilgili hesabın yeniden bağlanması gerekir.
