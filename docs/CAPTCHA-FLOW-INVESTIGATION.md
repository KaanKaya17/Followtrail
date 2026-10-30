# CAPTCHA akışı · 30 Ekim 2026

## Canlı gözlem

0.1.53 ile kullanıcı tarafından ayrı verilen test hesabında tek önerilen web girişi: HTTP 200, `authenticated=false`, `recaptcha_needed=true`, `two_factor_result` yok, `redirect_uri` mevcut, `challengeStatus=UNSUPPORTED_PATH`. Oturum çerezi yok. Yeni 429 gözlenmedi; eski W01 kaydı 29 Ekim’e ait. Kullanıcı aynı bilgilerle normal Instagram’da yeni giriş yapabildiğini bildirdi; bu, kullanıcı beyanıdır, uygulamamızda başarılı giriş kanıtı değildir.

## Kaynak incelemesi

Anonim edinilmiş Meta JavaScript dosyaları yerel olarak, hedef kod çalıştırılmadan incelendi. `public-login-captcha-dialog/provenance.json` içindeki 11 JavaScript dosyasının SHA-256’sı kaynak kayıtlarıyla eşleşti. Ana giriş kodu `recaptcha_needed`, `is_ig_login_recaptcha`, `captcha_persist_data` ve `redirect_uri` alanlarını ayrı ele alıyor. `CAAIGLoginRecaptchaDialog.react`, challenge belirteçlerinden `/api/v1/challenge/web/…` okuması yapıp insanın çözdüğü CAPTCHA’yı challenge eylemine gönderiyor. Yalnız bir CAPTCHA bayrağını görmek, bu özel akışın döndüğünü kanıtlamıyor.

REA CLI 6.7 ile seçili `CAAIGLoginRecaptchaDialogUtils` ve `CAAIGLoginRecaptchaDialog.react` kaynakları tekrar analiz edildi: `ev_b6070ef5efa44ad73268e0976fdc3fa8bd3bd6af8f708827b603bcdcf4bce249`, hedef SHA-256 `b6845d6ae6e733d773c6a3a8f4976b28c03870d42d1d1dda681ea2fe8fd15dde`. Tam Evidence yerel, ignored `artifacts/rea-captcha-family-evidence.json` dosyasındadır. İki kaynak seçildi; envanterdeki diğer dosyaların ilişkileri ve dinamik modül yüklemeleri bilinmiyor. Semantik kapsam kısmidir. Bu inceleme gerçek CAPTCHA başarısı veya son sunucu yanıtındaki yolun kanıtı değildir. Tarayıcı araştırma aracının ayrı anonim giriş sayfası isteği 429 döndü; aynı kaynağa tekrar istek gönderilmedi. Bu sonuç emülatördeki giriş yanıtıyla karıştırılmaz.

## 0.1.54 dar kapsamlı tanı

Yanıt ayrıştırıcısı yalnız türü gerçekten Boolean olan `is_ig_login_recaptcha` değerini tutar. CAPTCHA bağlamının varlığı Boolean olarak tutulur; içerik tutulmaz. Challenge sahibi URL’yi bir kez doğruladıktan sonra yalnız ROOT / LOGIN / TWO_FACTOR / AUTH_PLATFORM / CHALLENGE / CHECKPOINT / OTHER ekran sınıfını verir. Bu sınıf ağ yetkisi değildir; başka alan adı veya yeni yol otomatik açılmaz.

Sonraki canlı deneme mevcut emülatör giriş aralığı dolduktan sonra tek kez yapılır. Amaç doğru doğrulama türünü belirlemektir. Sunucu mola isterse durulur; hesap değiştirerek veya kayıtları sıfırlayarak aşılmaz. CAPTCHA’yı kullanıcı tamamlar.

## Paket ve kontroller

0.1.54 / code 55: 108 JVM testi başarılı. `WebLoginRelayTest` ve CAPTCHA’nın açılabilir/açılamaz durumlarını kullanan iki ekran testi: 9 vaka, 35,366 saniye, sıfır başarısızlık. Release lint sıfır hata / 24 uyarı. APK 2.249.368 bayt; SHA-256 `f5e465e277009023d273d01c60728914cb164fd04209f6c95411fb6d95a48daa`. İmza ve paket kontrolü geçti, izinler değişmedi. Emülatöre güncelleme sonrası yeni giriş açılışı yerel olarak durduruldu ve **Yeni bağlantı denemesi · 13:18** mesajı görüldü; bu kontrol Instagram’a kimlik bilgisi göndermedi.

## 0.1.54 gerçek yanıt ve 0.1.55 değişikliği

Emülatör saati host saatinden yaklaşık 44 saniye gerideydi. Saat veya mola kaydı değiştirilmedi; cihazın kendi aralığı dolduktan sonra aynı hesapla tek giriş gönderildi. Yaklaşık 13.21 host saatinde HTTP 200, `authenticated=false`, `captchaNeeded=true`, `challengeStatus=UNSUPPORTED_PATH`, `challengeRoute=AUTH_PLATFORM`, `igLoginRecaptcha=false`, `captchaDataPresent=false` görüldü. Oturum/sahip çerezi ve yeni 429 yoktu. Bu, klasik `CAAIGLoginRecaptchaDialog` akışından ayrı bir Instagram güvenlik yönlendirmesidir.

0.1.55, yanıtın bu birleşimi için yalnız doğrulanmış `https://www.instagram.com/auth_platform/…` giriş bağlantısını bellekte tutar. Mevcut gizlilik açıklamasında onaydan sonra aynı WebView’in bu tam yol/query GET’ine izin verilir. Başka query veya yol, POST, genel GraphQL ve sosyal işlemler açılmaz. Bu, sunucunun güvenlik sayfasını açma desteğidir; sayfanın sonraki tüm doğrulama protokollerini desteklediğinin kanıtı değildir. Gerçek insan doğrulaması ayrı sınanır.

0.1.55 kontrolleri: 109 JVM testi başarılı; `WebLoginScreenTest` ve `WebLoginRelayTest` birlikte 26 vaka / 214,738 saniye / sıfır başarısızlık (25 çalışan sentetik vaka, açık opt-in verilmediği için atlanan bir canlı public-form testi). Yeni vaka aynı WebView’de rıza sonrası tam auth_platform GET’ini, rıza öncesi engeli, farklı query/yol ve POST engelini, tanıda belirteç tutulmamasını denetler. Release lint sıfır hata / 24 uyarı. İmzalı APK 0.1.55 / code 56, 2.249.368 bayt; SHA-256 `372c9d2da97416de5c79877d899ff93a56b2140e1c10636de676e24397eab369`. Paket kontrolünde izinler ve üretim güvenlik bayrakları değişmedi. Yalnız emülatör güncellendi; fiziksel telefon bağlı değildi.
