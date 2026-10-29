# Android 0.1.43 — giriş yanıtı kanıtı

29 Ekim 2026. LP01 biçim 2 yalnız sınırlı, hassas olmayan sonuç olguları saklar: gerçek taşıyıcı HTTP durumu, JSON/data/errors şekli, en çok sekiz 32 bit sayısal hata kodu, eksik ayrıştırma işareti, oturum/hesap çerezi alındı mı ve uygulandı mı. Kamuya açık useCDSWebLoginMutation.graphql seçim ağacından caa_login_web.error_code, ig_authenticated, recaptcha_needed, two_factor_result ve redirect_uri varlığı ayrıldı. Null/bilinmeyen değerler başarı veya yanlış parola sayılmaz. Ham mesaj, hesap ID, nonce, URL, çerez değeri ve yanıt gövdesi saklanmaz. Set-Cookie bir kez ayrıştırılır; aynı olgular çerez kabulüne ve tanıya gider. x-ig-max-touch-points gerçek tarayıcı isteğinde görüldüğü için dar aktarım listesine alındı; genel GraphQL izni değişmedi.

98 JVM testi geçti; lint 0 hata / 22 uyarı. Son derleme 3 dk 40 sn. Gerçek WebView mesaj/çerez sınırında WebLoginRelayTest, LoginPageTraceTest ve WebLoginScreenTest: 17 vaka / 16 başarılı, varsayılan canlı form vakası 1 atlandı (78,221 sn). Sentetik HTTP kullanıldı; bu sonuç canlı hesap girişi kanıtı değildir. Testte messageClass anahtarıyla ham message alanının karıştığı alt dize kontrolü, gerçek özel değerleri ve yapısal alanları ayrı denetleyecek şekilde düzeltildi.

İmzalı 0.1.43/kod 44, 2.200.216 bayt; SHA256 539d3aaefbdb0a238a671c8d33f2db1b7ba31622c9c9fad7fdd91567bb5d79b5. Paket/izin/imza/debug fixture dışlama denetimi geçti. Mevcut anahtarla fiziksel telefona güncellendi. Windows değiştirilmedi.

## Gözlenen canlı sonuç

Emülatörde önceki yerel aralık dolduktan sonra yalnız bir yeni giriş denendi. HTTP 200, üst düzey hata kodu 1357054; data ve errors alanları yok; caa_login_web yok; oturum veya hesap çerezi alınmadı. 16 yerel kaynak 403’ü ayrı kayıtlıdır, bunlar Instagram HTTP 403’ü değildir. Captcha/iki aşamalı doğrulama veya authenticated hakkında bilinmeyen değerler korunur. A/B profil okuması yapılmadı.

Kullanıcı aynı bilgilerle normal Instagram girişinin çalıştığını doğruladı. Fiziksel telefonda da HTTP 200 / 1357054, normal giriş sonucu ve oturum çerezi yok. Bir AUTH gönderimi ve daha sonraki iki yerel GraphQL engeli gözlendi; kullanıcı birkaç hesabı aynı sayfada denediğini bildirdi, ancak bu kayıt yalnız bir gönderimi kanıtlar. Bu nedenle tüm hesapların Instagram tarafından ayrı ayrı reddedildiği söylenemez. Parola yanlışlığı kanıtlanmadı; istek aktarımı/bağlamı açık araştırma konusudur. Kodun kesin resmi anlamı doğrulanmadı. Cihaz saatleri UTC denetimiyle yaklaşık 1 saniye içinde eşleşti.

Planlanan bağımsız bilgisayar tarayıcı kontrolü, fiziksel cihazdaki denemeyle çakışmaması için 21:10 gönderim eşiğinden önce iptal edildi; kimlik doğrulama göndermedi ve sonucu başarı sayılmadı. Kontrol sürecinin yalnız bu iş için açılmış Node/Edge süreç ailesi kapatıldı. Parola/oturum dışa aktarımı yapılmadı.

Giriş tamamlandı iddiası yoktur. Bir sonraki değişiklik normal tarayıcı fetch/XHR bağlamının aktarımını ve kök istek hatasının arayüzde parola hatasıyla karışmamasını ele almalıdır; 429 ve yerel aralıklar korunmalıdır.
