# Android 0.1.82: CAPTCHA içerik sorgusu

## Canlı gözlem — 5 Kasım 2026

Kullanıcının yeni bağladığı fiziksel cihaz, 0.1.61’den veri silmeden 0.1.81’e güncellendi. Önceki sayaç 2 / 30 Ekim, sonraki tek giriş denemesi 3 / 5 Kasım 09:44. Son HTTP 200, `authenticated=false`, `captchaNeeded=true`, oturum ve hesap kimliği çerezi alınmamış, hata kodu 1348009. Hem hatalı giriş metni sınıfı hem ek doğrulama işareti var; bu kayıt parola doğruluğunu veya başarıyla açılmış oturumu kanıtlamaz.

Kullanıcı “Instagram doğrulamasını göster” sonrasında Instagram’ın kendi sayfasında “Bir sorun oluştu” gördü. Ekrandaki uygulama açıklaması sonraki doğrulama işleminin desteklenmediğini bildiriyordu. Tanıda engellenen belge:

- `34422716160661050` / `AuthPlatformReCaptchaContentQuery`
- `securityView=null`, `codeSubmission=null`; bu içerik isteğinin uygulama taşıyıcısıyla gönderildiğine dair kayıt yok.
- `LOCAL_BLOCK:PART:403` yerel filtre yanıtıdır; Instagram HTTP 429 kanıtı değildir. Diğer engellenen kaynakların hepsinin gerekli olduğu varsayılmaz.
- Eski tanı bu bilinmeyen işlemin değişken türlerini toplamıyordu. CAPTCHA modülünün gerçek argüman ve yanıt alanları bu kayıttan çıkarılamaz.

## Yapılan değişiklik

Yalnız bu sabit belge/işlem çifti için okuma desteği eklendi. Giriş sayfasına enjekte edilen mevcut köprü, içerik sorgusu gerçekten istendiği anda aynı sayfanın `AuthPlatformReCaptchaContentQuery.graphql` modülünü okur. Ortak şema inceleyicisi yalnız belge/işlem/tür, argüman ve kök alan adlarını seçer; varsayılan değerleri, form içeriğini veya APC değerini tanıya almaz.

Native sınır, modülün aynı kimlikte eksiksiz bir **query** olduğunu doğrular. Argümanlar mevcut sayfanın APC’si ve varsa null `device_id` ile sınırlıdır; yabancı bağlam, eksik/fazla alan, başka belge veya mutation reddedilir. Yanıt kökleri, doğrulanan modülün sınırlı `xfb_auth_platform_…` alanlarıyla eşleşmeli ve CAPTCHA içeriği null olmamalıdır. İzin verilen URL/gövde nesnesi taşıyıcıda yeniden denetlenir. Genel GraphQL veya sosyal yazma yetkisi eklenmedi.

Önceki tek `securitySubmitted` bayrağı, izinli içerik sorgularını belgeye göre birer kez ayıran kümeye dönüştürüldü. CAPTCHA içeriği sonrasında mail kodu görünümüne geçmek ikinci bir parola gönderimi gerektirmez. Aynı içerik sorgusunun tekrarı gönderilmez. İnsan doğrulamasını uygulama çözmez; kullanıcı tamamlar. Sonraki tanınmayan doğrulama işlemi otomatik yetkilendirilmez.

Tanıya `captchaSchema` ve son görünüm yanıtının belge kimliği eklendi. Bilinmeyen AuthPlatform işlemlerinde sınırlı argüman/tek seviye input **ad ve türleri** kaydedilir; değerler ve ham istek kaydedilmez. “Tanıyı kopyala” yalnız kullanıcı dokunduğunda mevcut güvenli tanı metnini yerel panoya kopyalar; kendiliğinden paylaşım veya sunucuya yükleme yapmaz. Mevcut oturumlar, hesap kayıtları, 429 ve giriş aralığı korunur.

## Doğrulama sınırı

Sentetik modüller/yanıtlar yalnız sınır davranışını doğrular; mevcut canlı CAPTCHA modülünün gerçek alanlarını veya sorunsuz giriş sonucunu kanıtlamaz. Yeni gerçek cihaz denemesinde önce içerik isteğinin yerel filtreden geçtiği ve CAPTCHA’nın göründüğü, sonra doğrulama ve profil okuması ayrı kontrol edilmelidir. Sonraki CAPTCHA tamamlama işlemi de destek gerektirebilir. Başarıyla bağlanmış hesap veya çözülmüş profil 429 sorunu iddiası yoktur.

## Yapılan kontroller

- 171 Android JVM testi geçti. Yanlış/eksik/incomplete/mutation şemaları, APC uyuşmazlığı, başka belge/yöntem/URL/gövde, beklenmeyen yanıt alanları ve tanıda değer sızıntısı reddedildi. Şema alanları sentetiktir.
- 11 JavaScript köprü/şema testi geçti. Şema yalnız ilgili CAPTCHA sorgusu istenirken okunur; argüman varsayılanları okunmaz, diğer istek ve XHR/fetch sınırları korunur.
- 785 Android metni dört dilde tutarlı. `lintRelease`: 0 hata / 54 uyarı. Release ve instrumentasyon APK’ları derlendi. Yeni iki gerçek WebView instrumentasyon senaryosu bu turda çalıştırılmadı; derlenmeleri gerçek cihaz başarısı değildir.
- İmzalı 0.1.82 / 83 APK manifest/mimari kontrolü geçti: 2.610.619 bayt, SHA-256 `ebad4a365a37760f3d8a4738ba0b8cedf1d05e40e6c692ef057d345b88ab34b4`. Aynı bağlı test telefonuna `adb install -r` ile kuruldu; veri silme/mola sıfırlama komutu kullanılmadı. Yeni canlı giriş sonucu bekleniyor.
