# Android 0.1.48 — tek giriş sonucunun sahipliği

29 Ekim 2026. Fiziksel 0.1.47 tanısında yalnız 1 AUTH gönderilmişti. HTTP 200, `caa_login_web` var, `authenticated=false`, `captchaNeeded=false`, `errorCodes=[1348009]`, yönlendirme ve oturum/hesap çerezi yoktu. Sonraki 6 POST uygulamada engellendi. Kullanıcı ilk hesabı kesin hatırlamadığından bu yanıt tek bir isme atfedilemez; sonraki hesaplar için Instagram sonucu değildir. 09:25 tarihli eski 429 sinyali bu girişin HTTP yanıtı değildir. 1348009 koduna evrensel anlam atanmaz.

Telefon UTC'si ve yerel saat arasında yaklaşık bir saniye fark görüldü. Parolasız/çerezsiz tek Instagram giriş sayfası GET'i HTTP 200 ve Date döndürdü; sunucu–host farkı -0,1 saniye, ölçüm 0,35 saniye. Bu ölçümde saat kayması saptanmadı; hesapla yeni giriş yapılmadı.

## Değişiklik

- CAPTCHA veya iki aşamalı doğrulama gerektirmeyen reddedilmiş giriş, formda bırakılmaz. Native başlık sonucu açıklar ve kalıcı kapının sonraki zamanını gösterir. Kullanıcı İstek bilgileri'ni açabilir; native kod ham parola veya sunucu mesajı göstermez.
- Aynı sayfadaki ikinci gönderim yerel TOO_SOON olursa form kapatılır ve yeni isteğin gönderilmediği açıklanır. 15 dakikalık giriş aralığı, bütçe ve gerçek 429 molası değişmez. Yeni deneme otomatik başlamaz.
- 0.1.47'nin normal başarılı girişte tek profil okuması ve kullanıcı onaylı CAPTCHA kaynağı davranışları korunur. Gerçek Meta giriş reddinin altta yatan nedeni henüz doğrulanmadı; bu sürüm sunucu reddini veya profil 429'unu çözdüğünü iddia etmez.
- Android şu anda tek etkin SavedAccount tutar. Başarısız bağlantı mevcut hesabı değiştirmez; farklı numeric ID başarıyla bağlanırsa etkin kayıt değiştirilir ve önceki kişinin izleme listesi yeni hesaba taşınmaz. Çoklu hesap arşivi garantisi verilmez. Aynı hesap yeniden bağlanırsa numeric ID üzerinden kişi kayıtları korunur. Fiziksel kontrol mümkünse mevcut etkin hesapla yapılmalı; farklı hesap için bu davranış kullanıcıya açıklanmalıdır.

## Yürütülen kontroller

102 JVM testi geçti; release lint 0 hata / 22 uyarı. Derleme 2 dk 59 sn. Üretim imzası ve paket sınırları denetlendi: 0.1.48/kod 49, 2.212.504 bayt, SHA256 `7127bc34b782a6bc40eb45d53445232731c14120a6bf49900f5368ca07555ca9`. Yeni bir izin veya native kütüphane yok; yedekleme/açık HTTP/debug kapalı; sentetik auth fixture üretim DEX'inde yok.

Gerçek MainActivity/WebView'da reddedilen tek yanıtın formu kapattığı, hesap yazmadığı, LP01 olgularını ve giriş aralığını koruduğu yeni vaka ile sınanır. Son API 37 odaklı tur (WebLoginScreenTest + WebComparisonScreenTest): 19 vaka / 18 başarılı + 1 opt-in atlandı, 179,971 sn. Fiziksel telefona 0.1.48 henüz kurulmadı; 0.1.47 etkin. Önceki 0.1.47 turunda 41 başarılı emülatör vakası + 1 opt-in atlandı; CAPTCHA'nın gerçekten Google üzerinde tamamlanması kanıtlanmış değildir.
