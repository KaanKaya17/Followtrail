# Android 0.1.44 — web istek bağlamı

29 Ekim 2026. Fiziksel telefon ve emülatörde aynı kök 1357054 yanıtı normal giriş verisi içermiyordu. Parola kullanmayan bir kontrol yapıldı: IGMAAWebBloksFullPageRootQuery / 26855089457462527, giriş sayfasının kendisinin ürettiği ilk okuma isteğinden yakalandı; kimlik doğrulama veya sosyal işlem belgesi gönderilmedi. Aynı gövde ve yakalanan başlıklarla üç ölçüm:

- Tarayıcı: HTTP 200, data var, hata kodu yok.
- API taşıyıcısı / özgün yakalanan başlıklar: HTTP 200, data yok, kök hata 1357054.
- Aynı API taşıyıcısı / ek Sec-Fetch-Site: same-origin, Sec-Fetch-Mode: cors ve Sec-Fetch-Dest: empty: HTTP 200, data var, hata kodu yok.

API ölçümleri 15 saniye aralıklı, yönlendirme/yeniden deneme kapalı ve ilk 429’da duracak şekilde yapıldı; 429 görülmedi. Parola girilmedi. Çerez, token, gövde ve kullanıcı verisi diske yazılmadı; yalnız sonuç olguları tutuldu. Bu, belirtilen tek UI sorgusunda bağlam farkını ayırır; 1357054 kodunun tüm Meta uçlarında aynı anlama geldiğini kanıtlamaz. Başlıkların anlamı [W3C Fetch Metadata çalışma taslağında](https://www.w3.org/TR/fetch-metadata/) tanımlıdır.

WebRequestContext bu başlıkları sabit HTTPS www.instagram.com kökenine uygular; diğer köken/port/kimlik bilgili URL reddedilir. WebLoginRelay önce gerçek mesaj kökenini ve ana çerçeveyi, sonra şifreli giriş belgesini doğrular. WebRequests yalnız önceden var olan dar GET yollarını kullanır. Mobil sağlayıcıya uygulanmaz. Genel GraphQL, sosyal işlem, yönlendirme ve tekrar izni eklenmedi. Kök istek hatası geçerli caa_login_web sonucu değildir; INVALID_RESPONSE olarak ayrılır, yanıttaki çerezler uygulanmaz. UI bunu yanlış parola olarak sunmaz.

99 JVM testi geçti; lint 0 hata / 22 uyarı. Son derleme 4 dk 19 sn. API 37 emülatöründe 38 vaka / 37 başarılı, canlı form opt-in vakası 1 atlandı (175,048 sn). Kök 1357054 yanıtının parola sonucu gibi iletilmediği ve Set-Cookie uygulamadığı gerçek WebView/mesaj sınırında sentetik HTTP ile sınandı. A/B, iptal, 429 ve oturum devir testleri de geçti. Bu testler gerçek Meta hesap başarısının yerine sayılmadı.

İmzalı 0.1.44/kod 45, 2.200.216 bayt; SHA256 c6c5b5ff8a8692cf4a0af049424e1a5e41b95f44a046e2c04b1378fade6722ce. Paket/izin/imza/debug fixture dışlama denetimi geçti. Mevcut imza anahtarı kullanıldı.

Canlı emülatörde yerel aralık dolduktan sonra bir giriş yapıldı: HTTP 200, data OBJECT, caa_login_web var, authenticated=true, captcha=false, iki aşamalı yük yok, redirect var. Oturum ve hesap çerezleri alındı ve uygulandı; hata kodu yoktu. Kimlik ve çerez değerleri kaydedilmedi. Giriş başarılıdır, A/B profil okuması henüz doğrulanmadı: sayfa bitince 15 saniyelik aralık için düğme pasifleşiyor, fakat son tarihte yeniden değerlendirme planlanmadığı için pasif kalıyor. Kaynakta düğme hesabının birkaç tüketicide kopyalandığı görüldü. Bir sonraki değişiklik bu hesaplamayı tek sahipte birleştirip sayfa bitişinde tek zamanlayıcı kurmalıdır; ağ isteği otomatik başlatılmamalıdır. 0.1.44 bu nedenle tam çalışan bağlantı olarak yayınlanmadı ve fiziksel telefona kurulmadı.
