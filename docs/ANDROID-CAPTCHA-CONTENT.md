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

## 0.1.83: gerçek içerik şeması ve sonuç gönderimi

0.1.82 telefon testinde kullanıcı CAPTCHA görüntülerini gördü fakat tekrar eden görevler bildirdi. Paylaşılan güvenli tanı, içerik sorgusunun hâlâ yerel filtrede reddedildiğini gösterdi: gerçek modülün argümanları **boş**, kök alanı **`xfb_auth_platform_anti_scripting_content`**. 0.1.82’nin APC/device ve captcha içeren kök alan beklentisi gerçek modüle uymuyordu. Görüntünün açılması, bu sorgunun gönderildiği anlamına gelmiyordu; sayaç yalnız yeni parola isteği için 3→4 olmuştu.

Aynı tanıda şu iki engellenen işlem gözlendi:

- `28372617359013204` / `AuthPlatformReCaptchaRenderOutcomeMutation`: `render_succeeded` boolean, `encrypted_context` string, `failure_reason` null, `device_id` string.
- `9960725864052044` / `AuthPlatformReCaptchaSubmitCaptchaMutation`: `captcha_response`, `encrypted_context`, `device_id` string.

Bu kayıt, CAPTCHA sonucunu iletme isteğinin Instagram’a uygulama taşıyıcısından gönderilmediğini kanıtlar. Google’ın her görsel turunu kabul ettiği veya tek döngü nedeninin bu olduğu çıkarılmaz. Önceki `authenticated=false` / CAPTCHA işareti aynı kayıtta kalır; hesap hâlâ bağlanmış sayılmaz.

0.1.83 içerik sorgusunu gözlenen boş argüman/kök alanla düzeltir. Yalnız yukarıdaki iki kimlik doğrulama işlemini ekler. Her işlem, aynı izin verilmiş sayfadaki kendi derlenmiş modülünün tam ID/ad/`mutation` türü, gözlenen argüman kümesi ve sınırlı auth_platform kök alanlarıyla eşleşmelidir. Başka GraphQL belgelerine yetki verilmez. Yanıt alanları modülle eşleşir; `validData` yalnız şekil denetimidir, CAPTCHA veya giriş başarısı değildir.

CAPTCHA’nın opaque `encrypted_context` ve `device_id` çifti, o açık ve onaylanmış sayfanın ilk izinli bildiriminden yalnız bellekte bağlanır; bunların URL’deki APC ile aynı olduğu varsayılmaz. Sonraki sonuç aynı sayfa bağlamına/çifte ait olmalıdır. Bağ kapanınca referans temizlenir. CAPTCHA cevabı, bağlam, cihaz ID’si ve çerez değerleri tanıya veya kalıcı kayda yazılmaz. Render ve sonuç işlemleri bu girişte birer kez gönderilir; sonuç tekrarında sayfa kapatılıp açık durma açıklaması gösterilir.

Köprü ayrıca eşzamanlı güvenlik isteklerini düşürmek yerine mevcut en fazla dört bekleyen mesaj sınırında sıraya alır. İptal edilen sıradaki mesaj, daha sonraki mesajın aktif isteğin önüne geçmesine neden olmaz. İlk girişin 35 saniyelik köprü süresi korunur; dört güvenlik isteğinin sıra/ağ süreleri için üst süre 180 saniyedir. Native istek kapısı, 6–10 saniyelik rezervasyon, bütçe ve 429 kuralları değişmez. Yanıt yayımlanmadan önce native istek sahipliği bırakılır. CAPTCHA hazırlanıyor / sonucu gönderiliyor / sonuç gönderildi durumları mail kodu aşamasından ayrılır.

Mutasyon yanıtlarının gerçek kök alanları önceki tanıda yoktu; gönderim ancak aynı sayfanın tam işlem modülü bunları sağlarsa yapılır. Testlerdeki mutasyon kökleri sentetiktir. Bu değişiklikler henüz canlı giriş veya çözülmüş profil 429 kanıtı değildir.

### 0.1.83 doğrulaması

- 174 JVM testi, 12 JavaScript testi geçti. Gerçek içerik şemasını yansıtan boş argüman/anti_scripting kök testi ve iki gözlenen CAPTCHA işleminin tip/sayfa-bağlama/istek-gövdesi yetkisi sınandı. Başka belge, yanlış şema, yanlış alan/tür ve değişen bağlam/cihaz çifti reddedildi.
- Sıradaki isteğin iptalinin aktif isteği atlatmadığı JavaScript testinde doğrulandı. CAPTCHA içerik + render isteklerinin sıraya alınması ve insan sonucunun yalnız bir kez gönderilmesi için gerçek WebView instrumentasyon senaryosu derlendi; cihazda çalıştırılmadı. Sentetik ağ/kök alanları gerçek Meta sonucunun kanıtı değildir.
- 789 Android kaynağı dört dilde tutarlı. Lint: 0 hata / 54 uyarı. Release ve instrumentasyon APK’ları derlendi. İmzalı 0.1.83 / 84 APK manifest/mimari doğrulaması geçti: 2.614.715 bayt, SHA-256 `f1d9e454b6fd0691cc61e061f3387f8a6caef5f8c0568721814ca4875cdb140c`.
- Yeni test telefonuna veri silmeden kuruldu. Kayıtlı giriş aralığı, 429 veya bütçe geçmişi sıfırlanmadı. Yeni canlı CAPTCHA/giriş sonucu kullanıcıdan bekleniyor.

### 0.1.83 fiziksel cihaz sonucu — 5 Kasım 2026, 10:32

Kullanıcı yeni sürümde “Son adım: hesabını bağla” ekranına ulaştığını bildirdi. USB üzerinden okunan devam ekranı, şifreli bekleyen Instagram oturumunun bulunduğunu ve profil okuma düğmesinin mola nedeniyle kapalı olduğunu gösterdi. Bu, ilk CAPTCHA içerik ekranından ileriye ve oturum saklamaya geçildiği gözlemidir; bütün hesaplarda uyumluluk iddiası değildir.

Aynı devam ekranındaki salt yerel İstek bilgileri: toplam sayaç **9**, son sayaçlı HTTP **429**, son deneme ve son sınırlama **5 Kasım 10:32:29**, sunucu bekleme bilgisi **Belirtilmedi**, uygulamanın yerel molası **5 Kasım 11:32:29**. Taşıyıcı Android HttpEngine / Chromium, uygulama kimliği `936619743392459`. Önceki denemede sayaç 4 idi; bu fark tek başına her aşamanın ayrı HTTP durumunu kanıtlamaz. Son 429, pending bağlantı akışında profil okumasının tamamlanamadığı aşamadadır.

Yeni parola/CAPTCHA denemesi, alternatif endpoint/taşıyıcıya geçerek sınırlamayı aşma veya mola sıfırlama yapılmadı. Yeni giriş istemeden mevcut şifreli pending oturum korunur. Yerel saat erişim garantisi değildir; süre bitince aynı pending oturumla yalnız bir profil denemesi yapılmalı. Tracker hesabı, sayılar ve listeler henüz başarıyla bağlanmış sayılmaz. İlk sorun olan doğrulama isteklerinin engellenmesiyle sonraki profil 429’u ayrı bulgular olarak tutulur.
