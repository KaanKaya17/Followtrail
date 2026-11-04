# Android web okuyucusunun masaüstü uygulama kimliği

Android 0.1.80. Windows 0.2.51 değişmez.

## Gözlem ve düzeltme

4 Kasım 2026'da [Instagram'ın resmî giriş sayfası](https://www.instagram.com/accounts/login/) oturumsuz, Windows masaüstü Chrome biçimindeki User-Agent ile okundu. HTTP 200 yanıtındaki gzip içerik açılıp `application/json` script blokları JSON olarak ayrıştırıldı. `CurrentUserInitialData` modülünün `APP_ID` alanı **936619743392459** idi. Ayrıştırılan sekiz `APP_ID` / `appId` / `app_id` alanının tamamı aynı değeri taşıyordu; eski değer bu yanıtta geçmiyordu.

- Kaynak istekte hesap çerezi, parola veya doğrulama kodu yoktu.
- Kullanılan UA: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36`.
- Açılmış yanıt: 505.421 bayt, tam okuma; SHA-256 `7d4a31416bf51d6bf65000331fca7e90f31640f920f4c8be48cfab3ee0a8d911`.
- Ham HTML veya yanıt çerezleri kalıcı kayda alınmadı. Buradaki sayılar ve modül/alan kimliği ayrıştırılmış gözlemlerdir; ham yanıt sonradan tekrar incelenebilir bir artifact değildir.
- İlk incelemede sıkıştırılmış baytlar metin gibi okunmuş ve alan bulunamamıştı. Bu sonuç uygulama kimliğinin yokluğuna kanıt sayılmadı; yukarıdaki açılmış yanıt ayrıştırılarak doğrulandı.

Uygulama `LoginBrowserResources.desktopUserAgent` ile giriş WebView'ını masaüstü biçiminde açıyordu. Buna karşılık `WebRequests`, 27 Ekim'deki genel oturumsuz sayfa gözleminden alınmış **1217981644879628** sabitini profil/liste başlığına yazıyordu. Bu eski gözlemin masaüstü API kabulünü kanıtlamadığı zaten belgelenmişti; tam `CurrentUserInitialData` modülü o kayıtta korunmamıştı.

Yeni sürüm, profil, kullanıcı adı araması, arama ve liste GET isteklerinin ortak üreticisi `WebRequests` içinde masaüstü değerini kullanır. Tarayıcı karşılaştırması da başlıklarını aynı üreticiden alır. Alternatif kimlik denemesi, uç nokta değiştirme, otomatik tekrar veya sınırlama sonrası başka kimlikle yeniden gönderim eklenmedi. Giriş formundan gelen başlıkları kullanan relay ve Android mobil oturum sözleşmesi değiştirilmedi.

## Kullanıcı akışı

Ana ekrandaki bağlantı düğmesi devam ekranını açar. İçerideki ağ isteği gönderen düğme artık **Profilimi kontrol et ve bağla** adını taşır; iki ayrı adım aynı etiketle gösterilmez. Devam ekranından doğrudan yerel **İstek bilgileri** açılabilir. Son sayaçlı HTTP, deneme/sınırlama zamanı, sunucu bekleme bilgisi ve web okuyucusunun uygulama kimliği görünür. Son sayaçlı yanıtın mutlaka o bekleyen hesaba ait olduğu iddia edilmez; çerez veya parola gösterilmez.

Bekleyen şifreli oturum, kayıtlı hesaplar, istek bütçeleri ve mevcut 429 molası korunur. Bu düzeltme bekleme süresini silmez veya kısaltmaz. Biriken 429 tekrar sayacının yerel molayı 24 saate çıkarabilmesi ayrı bir ürün politikasıdır; bu sürümde değiştirilmedi.

## Doğrulama sınırı

Masaüstü kimliğini bekleyen üretim sınırı testi düzeltmeden önce başarısız oldu: eski okuyucu 1217981644879628 gönderiyordu. Bu, kaynak sözleşmesi uyumsuzluğunu gösterir; Instagram sunucusundaki 429'un tek nedeninin bu olduğunu kanıtlamaz.

Canlı hesapla başarılı profil okuması henüz doğrulanmadı. Mevcut telefon molası dolduktan sonra aynı şifreli bekleyen oturumla tek açık profil kontrolü gerekir. HTTP 429 veya doğrulama hatasında durulmalıdır. Hesap bağlanmış sayılmak için kendi kimliği ve iki sayacı geçerli yanıtla doğrulanmalıdır.

## Çalıştırılan kontroller

- 154 Android JVM testi geçti; web isteğinin başlığına ilişkin beş üretim sınırı testi dahil.
- `test-web-connection-probe.mjs`: 21 çevrimdışı durum geçti; Instagram isteği gönderilmedi.
- Release APK, debug APK ve instrumentasyon APK'sı derlendi; lint 0 hata / mevcut 50 uyarı. Instrumentasyon testleri bu turda çalıştırılmadı; emülatör açılmadı.
- Dört dilde 730 native kaynak; yer tutucular ve çoğullar geçti. Ortak arama/tema/simge ve masaüstü kaynak kontrolleri geçti.
- `InstagramTracker-0.1.80.apk`: kod 81, 2.524.603 bayt; SHA-256 `0c9d5339bae127f1c146319a55425e6c6ec40775bda4eeca9184093cd9a996b9`. v2/v3 imza ve manifest kontrolleri geçti; izinler değişmedi.
- Fiziksel telefona veri silmeden güncellendi; paket yöneticisi 0.1.80 / kod 81 bildirdi. Bu kurulum sonucu canlı profil okuması başarısı değildir.
- Fiziksel telefonun yerel İstek bilgileri ekranında yeni `936619743392459` okuyucu kimliği görüldü. Son sayaçlı HTTP kaydı yükseltmeden önceki 429'a aitti; bu eski kayıt yeni sözleşmenin canlı sonucu olarak yorumlanmadı. Yeni devam ekranı instrumentasyon testi yalnız derlendi.
