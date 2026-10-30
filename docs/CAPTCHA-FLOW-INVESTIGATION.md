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

## 0.1.55 canlı adım · kullanıcı doğrulaması bekleniyor

Güncelleme sonrasında önceki giriş aralığı **13:35** olarak korundu. Emülatörün kendi saati 13:36’yı geçtikten sonra aynı test hesabıyla tek giriş gönderildi. Gerçek arayüzde **Instagram doğrulamasını göster** eylemi oluştu; önceki sürümlerdeki gibi form otomatik kapanmadı. Eyleme dokunularak Meta/Google CAPTCHA kaynaklarıyla ilgili gizlilik açıklaması açıldı. Bu pencerede kullanıcı onayı agent tarafından verilmedi; doğrulama sayfasının canlı yüklenmesi, CAPTCHA/2FA’nın tamamlanması ve profil bağlantısı henüz doğrulanmadı. Kullanıcıya onaylayıp insan doğrulamasını kendisinin tamamlaması için bilgi verildi. Yeni giriş gönderilmedi.

Kullanıcı açıklamayı açtıktan sonra giriş ekranı gördüğünü bildirdi. Sonraki incelemede `ALLOW:GET:CAPTCHA=1`, `documentsStarted=1`, `documentsFinished=3` görüldü. Giriş alanları devre dışıydı, CAPTCHA kutusu görünmüyordu. Yanıt tanısı `AUTH_PLATFORM`, `igLoginRecaptcha=false`, `captchaDataPresent=false`, `authenticated=false`, `challengeStatus=SUPPORTED`; oturum yoktu. Bu gözlem URL izninin oluştuğunu gösterir; asıl güvenlik belgesinin yüklendiğini kanıtlamaz. Geri yönlendirme veya eski belgenin istemci gezinmesi mevcut kayıtla kesin ayırt edilemedi. Yeni parola gönderilmedi.

## 0.1.56 gezinme ve görünürlük

Güvenlik açılışı sabit yerel `about:blank` belgesi üzerinden yapılır; eski giriş sayfasının JavaScript’i ve formu kaldırılır, çerezler silinmez. Sonra aynı açık girişte sunucunun verdiği güvenlik GET’i açılır. Yetkili bağlamda, WebView’in `isRedirect` ve `isForMainFrame` olguları gerçek HTTP yönlendirmesi gösterirse yalnız HTTPS `www.instagram.com/auth_platform/…` GET’i izlenebilir. Başka host, kullanıcı/script gezinmesi, POST, GraphQL veya sosyal işlem yetkisi verilmez. Son yönlendirilen adres yalnız bellektedir ve kapanışta unutulur.

`LoginPageTrace` format 3 yalnız STARTED / FINISHED / ALLOW / BLOCK, HTTP_REDIRECT / PAGE ve sonlu ekran sınıfları tutar. Gerçek URL/query/kimlik içermez. LOGIN ekranına oturumsuz geri dönüşte kullanıcıya tamamlanmadığı anlatılır. Bunun sunucudaki nedeni veya CAPTCHA’nın tamamlandığı, yalnız gezinme düzeltmesinden çıkarılamaz.

0.1.56: 109 JVM testi; 30 cihaz test vakası / 213,862 saniye / sıfır başarısızlık (29 çalışan sentetik, bir opt-in canlı form testi atlandı). APK 2.253.464 bayt, SHA-256 `ce8e71e4f7f1845f98c32b952160083f8c526b62e56e4d5f7792c6ec6d869be9`. 30 Ekim yaklaşık 14.04 host saatinde gerçek testte STARTED:BLANK / FINISHED:BLANK, ALLOW:HTTP_REDIRECT:AUTH_PLATFORM, STARTED:AUTH_PLATFORM ve FINISHED:AUTH_PLATFORM izlendi. Böylece güvenlik belgesinin gerçek açılışı doğrulandı; ancak bu belge üç GraphQL isteği gönderdi ve bu istekler engellendi. CAPTCHA ekranı/profil bağlantısı tamamlanmadı. Yeni 429 yoktu. Sistem Google Password Manager parola kaydetme önerisi **Not now** ile reddedildi. Kullanıcının daha önce kabul ettiği doğrulama kaynakları açıklaması onaylandı; CAPTCHA çözülmedi veya atlanmadı.

## 0.1.57 sınırlı işlem tanısı

Yeni tanı yalnız onaylanmış güvenlik sayfasından gelen, aynı-origin/ana çerçeve doğrulamasını geçen mesajda çalışır. Sunucuya istek göndermeden GraphQL `doc_id` ve `fb_api_req_friendly_name` teknik alanlarını doğrular; en fazla dört belge kimliği tutulur. Değişkenler ve diğer alan değerleri ayrıştırılmaz/kaydedilmez. Kopya, bozuk veya boyut sınırını aşan formda kimlik bilinmiyor kalır. Bu gözlem ağ yetkisi oluşturmaz; bilinmeyen güvenlik işlemleri engelli kalır. Kullanıcıya sayfanın açıldığı fakat sonraki adımın desteklenmediği açıklanır.

Köprü diğer bilinen aynı-origin GraphQL adreslerini de özgün yoluyla gözlemler; yalnız `/api/graphql` üzerindeki mevcut onaylı ilk giriş sözleşmesi sunucuya gönderilebilir. Farklı yolun aynı ilk giriş belgesini taşıması yeni ağ yetkisi oluşturmaz. Güvenlik sayfasında dört eşzamanlı gözlem mesajına izin verilir; normal giriş bir bekleyen mesajla sınırlıdır. Fetch/XHR özgün yolunu korur. Native kaynak politikası, köprüye girmeyen istekleri ayrıca engeller.

0.1.57 masaüstü kontrolleri: 111 JVM testi sıfır hata/başarısızlık, `node --test scripts/test-login-relay.mjs` beş başarılı yalıtılmış köprü testi; release lint sıfır hata / 24 uyarı; çevrilebilir 644 TR/EN anahtarı eşleşir. İmzalı APK code 58, 2.253.464 bayt, SHA-256 `dbdf4e962b7efd003cad25cc9f0f669aad9229ab4e5bb70c58de5d0a5c398226`; mevcut dört izin ve kapalı backup/debuggable/cleartext doğrulandı. 0.1.57’nin yeni cihaz testleri derlendi, fakat kullanıcı emülatörü kapattığı ve yeniden açmadan önce sorulmasını istediği için henüz çalıştırılmadı. Emülatör açılışı için kullanıcı yanıtı beklenir. Bu sürümde yeni canlı giriş henüz yapılmadı.

## 0.1.57 fiziksel telefon kurulumu · 30 Ekim 16.08

Kullanıcı emülatör testini yeterli RAM koşuluyla onayladı. 15.93 GiB toplam belleğin 1.90 GiB’ı kullanılabilir ölçüldüğü için emülatör açılmadı. Kullanıcı sonra fiziksel telefonda test/kurulum istedi. Mevcut üretim 0.1.51 / code 52, uygulama kaldırılmadan `install -r` ile aynı anahtarlı 0.1.57 / code 58’e güncellendi; kurulu sürüm paket yöneticisinden doğrulandı. Kullanıcı İstek bilgileri ekranını açtı; uzaktan dokunma veya yeni giriş yapılmadı.

Son sayılan profil denemesi ve W02 / HTTP 429 kaydı **30 Ekim 10.01**, uygulamanın yerel molası **31 Ekim 02.01**, sunucunun Retry-After bilgisi **belirtilmedi** olarak korundu. Eski LP01 format 2 kaydında `authenticated=true`, oturum/sahip çerezleri mevcut ve `cookiesApplied=true`, `captchaNeeded=false` vardı. Bu, güncelleme öncesi giriş yanıtıdır; 0.1.57’de başarılı yeni bağlantı veya CAPTCHA testi sayılmaz. Mola dolana kadar yeni fiziksel telefon giriş isteği gönderilmedi. Sonraki kullanıcı testi, normal Instagram’da çalıştığı kullanıcı tarafından doğrulanan ayrı test hesabıyla, aynı giriş aralığı ve 429 kuralları korunarak bir kez yapılır.

## 0.1.57 emülatör testi · 30 Ekim 16.28–16.33

Kullanıcı emülatör testine yeniden izin verdi. Host belleği 15.93 GiB toplam / 5.93 GiB boştu. 2 GB / iki çekirdekli, snapshotsız mevcut Small_Phone açıldı. Yalıtılmış debug paketi güncellendi; test süreci Android tarafından LOW_MEMORY nedeniyle kapatıldı (`ApplicationExitInfo`, yaklaşık 219 MB RSS). Test başarılı sayılmadı. Üretim uygulaması, telefon ve bekleme kayıtları değiştirilmedi.

Emülatör kapatılınca hostta 6.25 GiB boş bellek ölçüldü. Aynı AVD 3 GB / iki çekirdekle yeniden açıldı. Bütün sınıfı tekrarlamak yerine dört kritik vaka seçildi. İki relay vakası ve bir ekran vakası geçti; dördüncü tamamlanmadan WebView süreçleri LOW_MEMORY ile kapandı ve test süreci SIGKILL / status 9 ile sonlandı. Android crash buffer’da Java hata kaydı yoktu. Hostta kalan boş RAM 1.42 GiB idi. Test grubu tamamen başarılı sayılmaz. Emülatör normal yoldan kapatıldı; gerçek giriş isteği gönderilmedi. Yerel mola kodu değiştirilmedi.

## 0.1.58 WebView sonlanması

Giriş ekranının sahibi `onRenderProcessGone` ile sonlanan WebView’i kaldırıp yok eder. İstek köprüsü aynı iptal/lease sahibini kullanır; sonlanmış sayfaya cevap veya bridge kaldırma çağrısı yapmaz. Tarayıcı profil karşılaştırması da iptal edilirken sonlanmış sayfada JavaScript çalıştırmaz. Bekleyen zamanlayıcılar iptal edilir; yeni bağlantı veya otomatik tekrar başlatılmaz. Mevcut kasalar ve RequestGate kaydı korunur. Kayıtta renderer crash / sonlandırma için sabit sayısal tanı vardır; sayfa içeriği yoktur.

Dayanak: [Android WebView termination](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination). Bu işlem, işletim sistemi ana uygulama sürecini doğrudan öldürürse çalışacağını garanti etmez. Test için yalnız yalıtılmış debug WebView’in `WebViewRenderProcess.terminate()` çağrısıyla gerçek görüntüleyici sonlanmasını tetikleyen çevrimdışı vaka eklendi; üretimde test tetikleyicisi yoktur.

0.1.58 derleme başarılı; 111 JVM testi sıfır hata/başarısızlık. Release lint sıfır hata / 26 uyarı; yeni iki uyarı callback üzerindeki `@TargetApi(26)` yerine `@RequiresApi(26)` tercih edilmesiyle ilgilidir. İmzalı APK code 59, 2.257.560 bayt, SHA-256 `960f98c55aaf36778d641a849ebae5c0958b77f7914cb2f0f234fbb912f96a08`. İmza/paket kontrolü geçti; mevcut dört izin, kapalı backup/debuggable/cleartext ve sıfır native kütüphane korundu. Yeni renderer sonlanma vakası derlendi fakat cihazda henüz çalıştırılmadı. Fiziksel telefon 0.1.57’de; 0.1.58 telefona veya üretim emülatör paketine kurulmadı. Gerçek Instagram giriş denemesi yapılmadı.

3 GB guest yapılandırmasında host boş belleği yaklaşık 4,8 GiB azaldı. Bu ölçüme dayanarak, daha önce başarılı koşul olan 4 GB guest ile yeniden denemeden önce yaklaşık 8 GiB host boş bellek hedefleniyor (guest/ek yük ve host payı için yaklaşık değer; evrensel bir Android minimumu değildir). Emülatör kapalı tutuluyor; yeniden açılış için kullanıcıdan RAM boşaltması/onayı istenir.
