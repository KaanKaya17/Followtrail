# Windows mobil oturumunun Android'e taşınması — 0.1.6

Windows v0.3'te çalışan instagrapi 3.2.0 oturumunu yeni web girişine çevirmeden Android'de sınar. İlk yerel eşleştirme mevcut Windows oturumuyla yapılır; sonraki profil okumaları Android'dedir. APK içinde yeni CAA/parola girişi bu sürümde yoktur.

## Sözleşme ve farklar

Kaynaklar test edilmiş Windows ortamındaki instagrapi 3.2.0 `mixins/private.py`, `mixins/auth.py`, `mixins/attestation.py`, `mixins/user.py` ve `InstagramCounter/app.py` dosyalarıdır. [Kütüphane MIT lisanslıdır](https://github.com/subzeroid/instagrapi); atıf APK içindedir. REA APK gözlemleri ve kütüphane davranışı ayrı kanıtlardır.

- Masaüstü aracı `set_settings`/`private_headers` yorumunu kütüphaneye bırakır. Yalnız izinli mobil Authorization, cihaz/app/locale başlıkları, seçilmiş çerezler ve varsa kayıtlı USDID imza anahtarı alınır. Tam ayar dosyası, parola, doğrulama kodu ve ham yanıt aktarılmaz. USDID anahtarı gizli oturum verisidir; sadece şifreli zarf ve Keystore kasası içinde diske yazılır.
- Kendi profilinin `GET https://i.instagram.com/api/v1/users/{id}/info/` isteği Windows ile aynı `is_prefetch=false`, `entry_point=self_profile`, `from_module=self_profile`, `is_app_start=False` parametrelerini kullanır. Kişi araması `users/{username}/usernameinfo/`, yenilemesi sabit kimliğin info yoludur. Web'e otomatik dönüş yoktur.
- Beklenen profil kimliği, açık tamsayı sayaçlar ve en fazla 1 MiB yanıt gerekir. Başarılı yanıttaki izinli IG-Set/Set-Cookie değişiklikleri sonuç dönmeden önce şifreli kasaya yazılır. Yeni Authorization bağlı hesapla eşleşmelidir. Hatalı profil yanıtından güncelleme yapılmaz.
- USDID varsa aktarılmış anahtar aynı `id.expiry.base64url(DER-ECDSA-SHA256)` biçimini, 3600 saniye ömrü kullanır. Yeni cihaz kimliği, kayıt veya attestation isteği üretilmez.
- Android OkHttp 5.4.0 kullanır; incelenen 5.5.x AAR'ları SDK 37 gerektirdiğinden mevcut SDK 36 araç zinciri korunur. Android sistem sertifikalarına güvenir. HTTP/2 görüşülmezse kimlik doğrulamalı profil isteğinden önce durur. Android TLS/ClientHello uygulaması Windows curl_cffi/libcurl ve hybrid key agreement ile aynı değildir. Aynı API/oturum sözleşmesi, birebir aynı ağ izi anlamına gelmez.
- Bağlantı tekrarı, paralel alternatif ve yönlendirmeler kapalıdır. Ağ interceptor bütçesi 503/Retry-After:0 gibi takip isteklerini de ikinci iletimden önce reddeder. Tek GET ölçümü DNS/TLS trafiğini kapsamaz.

## Eşleştirme ve saklama

Dışarıya açık olmayan DesktopLinkActivity, Keystore'da RSA-3072 alıcı anahtarı ve 10 dakikalık rastgele istek kimliği oluşturur. Dış dosya dizinindeki `mobile-link-public.json` yalnız açık anahtar/paket/süre içerir. Windows AES-256-GCM anahtarını RSA-OAEP/SHA-256, MGF1/SHA-1 ile sarar; bu açık parametreler API 24 Keystore uyumluluğunu sağlar. GCM ek verisi sürümü/istek kimliğini bağlar.

Zarf yalnız ADB'den `desktop-session.iglink` olarak gider. Windows oturumu bellekte DPAPI'den çözülür; araca login/profile çağrısı eklenmemiştir. Komut satırı parola/oturum değeri almaz; çıktı istisna metni veya sır içermez. Aynı Windows kullanıcısı ve önceden kurulmuş ortam gerekir; araç yazılım kurmaz.

APK süreyi, istek kimliğini, GCM doğrulamasını, hesap kimliğini ve alan sınırlarını denetler. Başarılı aşamalandırmadan sonra zarf, açık istek ve tek kullanımlık anahtar temizlenir. Oturum `noBackupFilesDir` içinde AES-GCM/Keystore ile tutulur. İki banka mevcut hesabın oturumunun üzerine yazılmasını önler. Ana hesap v4 kaydı yalnız sabit banka bağlantısını taşır; gizli başlıkları çoğaltmaz. Aynı hesabın geçmişi korunur.

Başarılı içe aktarma sunucu oturumunun hâlâ geçerli olduğunu kanıtlamaz. Kaynak Windows oturumu da korunur; uzun süreli çift istemci davranışı doğrulanmış değildir.

## İstek ve yaşam döngüsü

Açılış/aktarım profil isteği başlatmaz. Ekran açıkken otomasyon kalıcı bekletilir; aktarım manuel moda alır. İlk bağlantı testi ekranda/yeniden oluşmada bir kez, yeni ekranlarda kalıcı en az 15 dakika aralıkla başlatılır. Önceki 429 ve daha uzun Retry-After korunur. Başarısız denemeden sonra eski web okuması veya giriş başlatılmaz. Başarılı açık okuma normal önbellek politikasına döner.

Arka plana geçme/bitirme etkin isteği iptal eder; geç yanıt ana hesabı bağlayamaz. Normal bitirme aktarım dosyalarını/anahtarını ve ekranın bekletmesini temizler. Süreç ölümü sonrası bekletme kalırsa ekranı yeniden açıp bitirmek kurtarma yoludur. Aşamalandırılmış fakat bağlanmamış şifreli banka en fazla iki kayıt içinde kalır; sonraki aktarım kullanımdaki bankayı korur. Bağlantıyı kaldırmak iki bankayı da temizler.

JVM testleri sözleşmeyi ve yerel TLS/HTTP2 ile tek gönderimi; Android testleri gerçek Keystore, banka, 429, iptal ve süreyi sınar. Python testleri sentetiktir. Bunlar gerçek Instagram kabulünü veya bütün telefonlardaki TLS davranışını kanıtlamaz. Güncel sonuçlar [VALIDATION.md](VALIDATION.md) içindedir.
