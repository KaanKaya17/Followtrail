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

## 0.1.58 ayrı süreçli test ve gerçek işlem kimliği · 30 Ekim 16.41–16.49

Kullanıcı RAM boşalttığını bildirdi. 6.40 GiB host boş bellek ölçüldü; 4 GB guest açılmadı. Önceki toplu test birikimini tekrarlamak yerine 3 GB guest ile her vaka ayrı instrumentation sürecinde çalıştırıldı. `realRendererTerminationEndsTheLoginWithoutRetryingOrLosingThePause`: 1 test, 25.886 saniye, başarılı; gerçek WebView renderer terminate çağrısı işlendi, uygulama açık kaldı ve mola korundu. `unsupportedCaptchaClosesTheFormWithoutCallingItAWrongPasswordOrRetrying`: 1 test, 18.522 saniye, başarılı. Bu aşamada host boş belleği 3.43 ve 3.30 GiB idi. Toplu sınıflar yeniden başarılı ilan edilmedi.

Üretim emülatör paketi aynı anahtarlı 0.1.58 / code 59’a güncellendi. Mevcut giriş aralığı dolduktan sonra önceki ayrı test hesabıyla tek giriş gönderildi. Güvenlik sayfası daha önce kullanıcının onayladığı açıklama kapsamında açıldı. Yaklaşık 16.48 host saatindeki tanı: `AuthPlatformCodeEntryViewQuery`, belge `34414353874878894`; aynı işlem engellenen üç GraphQL denemesinde tek kimlik olarak kaydedildi. İlk giriş HTTP 200 / error 1348009, `authenticated=false`, `captchaNeeded=true`, `igLoginRecaptcha=false`, `challengeRoute=AUTH_PLATFORM`, oturum/sahip çerezleri yoktu. Yeni 429 yoktu; W01 satırı 29 Ekim’e ait eski kayıttı. Kod/CAPTCHA ve profil bağlantısı tamamlanmadı. İnceleme bitince giriş normal Vazgeç yoluyla kapatıldı ve emülatör snapshotsız kapatıldı; ikinci parola isteği gönderilmedi.

## 0.1.59 şema gözlemi

İşlem adı kod giriş ekranını işaret ediyor; gerçek değişken şeması ve sunucu seçimi, yalnız işlem adından türetilmiş kabul edilmiyor. Aynı belgenin anonim `/auth_platform/` sayfasında statik tanımı bulunamadı; bu anonim incelemede kimlik bilgisi veya GraphQL POST gönderilmedi, 429 dönmedi. Önceden arşivlenmiş public JavaScript dosyalarında da bu belge kimliği bulunamadı.

0.1.59 yalnız bilinen belge/işlem eşleşmesinin kök değişken adlarını ve STRING / NUMBER / BOOLEAN / OBJECT / ARRAY / NULL türünü tutar; iç nesne/array değerlerini tutmaz. En fazla sekiz geçerli protokol alan adı tutulur; eksik veya bozuk kapsam bayraklanır. Onaylanmış güvenlik sayfasında tek sabit GraphQL modülünün argüman ve kök alan isimleri okunur; `params.id`, `params.name` ve `operationKind=query` eşleşmesi aranır. Değişken varsayılan değerleri ve seçim argümanlarının değerleri okunmaz. Statik tanım bulunamazsa bilinmiyor kalır. Bu bilgi incelenmeden yeni GraphQL yetkisi verilmez.

0.1.59 masaüstü kontrolleri: 114 JVM testi, 8 yalıtılmış JavaScript testi başarılı; lint sıfır hata / 24 uyarı. İmzalı APK code 60, 2.257.635 bayt, SHA-256 `addc9e970870ce12b7a0aca3d88987a0320e2b3a80687409fb2ce82876f04353`. İmza/paket kontrolü geçti; mevcut izinler ve üretim güvenlik bayrakları korundu. Yeni şema cihaz testi derlendi, henüz çalıştırılmadı. Emülatörün tekrar açılışı için kullanıcıya soruldu; bu pakette yeni canlı giriş henüz gönderilmedi.

## 0.1.59 gerçek şema sonucu · 30 Ekim yaklaşık 17.10

Kullanıcı yeniden açılışı onayladı. 3 GB guest üzerinde tek çevrimdışı AuthPlatform/şema vakası ayrı süreçte geçti (35.161 saniye). Üretim paketi aynı anahtarla 0.1.59'a güncellendi; önceki giriş aralığı korunarak aynı test hesabıyla tek giriş gönderildi. HTTP 200, `authenticated=false`, `captchaNeeded=true`, `igLoginRecaptcha=false`, `challengeRoute=AUTH_PLATFORM`; oturum/sahip çerezleri yoktu. Yeni 429 yoktu.

Onaylanmış gerçek sayfada `34414353874878894` / `AuthPlatformCodeEntryViewQuery` statik modülü kimlik ve `kind=query` eşleşmesiyle bulundu. Kök argümanlar `apc`, `device_id`; kök alanlar `xfb_auth_platform_enter_code_content`, `xfb_auth_platform_afad_polling_interval`; kapsam eksik değildi. Engellenen formun değişken türleri `apc=STRING`, `device_id=NULL` idi. Değerler kaydedilmedi. Görüntüleme sorgusu hâlâ gönderilmedi; kod/CAPTCHA ve profil bağlantısı tamamlanmadı. İnceleme sonrası normal Vazgeç ile çıkıldı ve emülatör snapshotsız kapatıldı.

## 0.1.60 dar kapsamlı ekran okuması

Yalnız yukarıdaki kimlik/işlem ve iki değişken eşleşmesi kabul edilir. `apc`, onaylanmış mevcut güvenlik sayfasının tek APC query değeriyle bellekte karşılaştırılır; `device_id` yalnız null olabilir. Farklı belge, kod alanı, ek değişken, yinelenen form alanı veya farklı bağlam gönderilmez. Ortak form ayrıştırıcısı biçim kontrolünün tek sahibidir; ayrıştırma ağ yetkisi vermez. Native HTTP politikası değişmez URL ve form nesnesi kimliğini yeniden denetler.

Ekran okuması ilk girişin 15 dakikalık aralığından ayrıdır: kalan en fazla 15 saniyelik ortak okuma aralığını iptal edilebilir işte bekler, ardından ortak bütçeden bir okuma sayar. Uzun mola/bütçe engeli atlanmaz. Aynı açık akışta bir kez gönderilir; ilk giriş yeniden oynatılmaz veya giriş aralığı uzatılmaz. `securityView` tanısı yalnız HTTP durumu, JSON ayrıştırılması, beklenen kök verinin bulunması ve çerez uygulanması bilgisini tutar; giriş sonucunun üzerine yazmaz. Yanıt içeriği veya APC kaydedilmez. Kod gönderimi için yeni yetki verilmez; bu adımın protokolü henüz bilinmiyor.

0.1.60 derleme 2 dakika 54 saniyede tamamlandı; 118 JVM testi ve 8 yalıtılmış JavaScript testi başarılı. Release lint ve debug cihaz testleri derlemesi tamamlandı. Yeni cihaz vakaları henüz çalıştırılmadı; gerçek kod/CAPTCHA veya hesap bağlantısı başarısı yoktur.

Release lint sıfır hata / 24 uyarı; 646 TR/EN kaynağı ve biçim argümanları eşleşir. İmzalı APK code 61, 2.261.731 bayt, SHA-256 `041a4b5ba682ac194af2f94487fe04193ed59e7b82bc89c6ecd7edc89f28548c`; imza ve paket kontrolü geçti. Mevcut dört izin, kapalı backup/debuggable/cleartext ve sıfır native kütüphane korundu. Fiziksel telefon güncellenmedi; 0.1.57 ve mevcut mola kaydı korunur. Yaklaşık 5.3 GiB host boş bellek ölçüldü; önceki bellek sonlanması nedeniyle yeniden emülatör açılışı ve biraz RAM boşaltılması kullanıcıya soruldu.

## İkinci fiziksel telefon · 0.1.61 tanı görünümü

Kullanıcı başka telefonda gerçek test yapmak istedi, mail kodu ekranı geldiğini ve kodu girdikten sonra beklediğini bildirdi. Yeni USB bağlantısındaki Android API 33 telefonda paket yöneticisi 0.1.60 / code 61'i doğruladı. Kullanıcının bildirimi henüz uygulama içi başarılı kod gönderimi veya oturum kanıtı değildir.

İstek bilgileri penceresi açıkken normal UIAutomator erişilebilirlik ağacında başlık/Tamam görünür, `android:id/message` metni boştu; LP01 okunamadı. Parola, kod veya sayfa içeriği okunmadı. Ekran görüntüsü koruması aşılmadı, özel uygulama dosyalarına erişilmedi ve yeni giriş gönderilmedi. 0.1.61 aynı kayıtları ayrı, seçilebilir metin bölümlerinde gösterir; LP01 JSON alanları ayrı biçimlendirilir. Alan değerlerine yeni tanı eklenmez. Güncellemenin canlı WebView'i kapatabileceği kullanıcıya bildirildi; kayıtlı tanı ve mola korunur.

0.1.61 derlemesi 4 dakika 30 saniyede tamamlandı; 118 JVM testi, release lint (sıfır hata / 24 uyarı) ve 646 TR/EN kaynak kontrolü geçti. İmzalı APK code 62, 2.261.731 bayt, SHA-256 `39f07344095e1c0b2fd5a145caaad8ddbfbc885905e9ca49189c1d102d09ed99`. Paket kontrolünde mevcut dört izin ve kapalı backup/debuggable/cleartext korundu. İkinci fiziksel telefona veri silmeden `install -r` başarılı oldu; kurulu sürüm paket yöneticisiyle doğrulandı. Uygulama açıldı, fakat bildirim perdesi üzerindeydi; kullanıcıdan perdeyi kapatıp İstek bilgilerini yeniden açması istendi. Yeni görünümün bu telefonda tanıyı okunabilir yaptığı veya kod sonrası bağlantıyı çözdüğü henüz doğrulanmadı. Emülatör açılmadı; yeni giriş/OTP gönderilmedi.

## 1 Kasım · ikinci telefondaki kayıt okundu

Android 0.1.61 kurulu ikinci telefonun açık İstek bilgileri ekranı, UIAutomator erişilebilirliği üzerinden parça parça okundu. Yeni giriş gönderilmedi. Son sayaçlı deneme 30 Ekim 17:52 / HTTP 200; LP01'de bir AUTH ve bir SECURITY_VIEW iletimi var. Yerel 403'ler `LOCAL_BLOCK` olarak ayrılıyor; Instagram 403'ü değiller.

Engellenen sabit belge/işlem çiftleri: `25017097917894476` / `useAuthPlatformSubmitCodeMutation`, `24626773746933809` / `AuthPlatformApproveFromAnotherDevicePollingMutation`. Ayrı `ConversationalSupportIGLRRChatExperienceQuery` de engellenmiş. Kod gönderiminin yerel filtreden geçemediği gözlendi; bu, diğer tüm giriş koşullarının başarılı olduğunu kanıtlamaz. Kod ekranı sorgusunun statik şeması mevcut. Ekranın sonuna kaydırılınca `securityView` bölümü arada kaldığından bu kaydın validData sonucu okunamadı.

İlk `authentication` yanıtı HTTP 200, `authenticated=false`, oturum/sahip çerezleri yok, kod 1348009, `captchaNeeded=true`, `igLoginRecaptcha=false`, `challengeRoute=AUTH_PLATFORM`, `challengeStatus=SUPPORTED` içeriyor. Bu ilk yanıttaki `BAD_LOGIN_INFORMATION` sınıfı, desteklenmeyen kod gönderiminden sonra Instagram'ın kodu reddettiğinin kanıtı değildir.

Kullanıcının kendi telefonuna geri dönüldüğünde 0.1.57 kurulu, önceki 429 30 Ekim 10:01 ve yerel mola dolmuş görüldü. İki telefonun kayıtları birbirine aitmiş gibi yorumlanmadı.

## 0.1.65 · doğrulama işlem şemaları

Yukarıdaki iki belge kimliğiyle tam işlem adı eşleşirse, engellenen isteğin kök alanları ile bir düzey altındaki nesne alanlarının yalnız ad ve türleri tutulur. Kök en fazla 8, alt alanlar toplam en fazla 16 öğedir; kapsam aşılırsa eksik olduğu belirtilir. İç içe nesne/array içerikleri ve değerler tanılanmaz.

Aynı açık/onaylanmış güvenlik sayfasının statik modülü, belge numarası, tam işlem adı ve `operationKind=mutation` eşleşmesiyle okunur. Argüman/kök alan adları ortak şema ayrıştırıcısından geçirilir. Bu bilgi POST izni değildir. Kod gönderme ve cihaz onayı desteği, gerçek alanlar doğrulandıktan sonra ayrı sözleşmeyle uygulanacaktır. Bu ara sürümde girişin çözüldüğü iddia edilmez.

120 JVM testi ve 9 JavaScript kontrolü geçti; release lint 0 hata / önceki 24 uyarı. Release, debug ve cihaz test paketleri derlendi; yeni WebView ekran vakası cihazda henüz çalıştırılmadı. Son kaynak yeniden denetlenerek imzalanan 0.1.65 / code 66, 2.384.611 bayt; SHA-256 `476da3f42309a781715384a09458e1a8a045a69f524a4a9624a955fd563ce5b7`. v2/v3 imzaları ve paket kontrolü geçti. Kullanıcının kendi telefonuna eski 0.1.57'nin üzerine `install -r` başarılı oldu; 0.1.65 kurulu sürüm olarak doğrulandı. Veri silme, mola sıfırlama veya otomatik parola/kod gönderimi yapılmadı. Kullanıcıya tek açık giriş ve gerekiyorsa kendi gireceği kodla yeni tanı testi verildi; sonuç bekleniyor.

## 0.1.65 gerçek telefon kaydı · 1 Kasım 16:13

Kullanıcı tek giriş yaptı, Instagram doğrulamasını kendi onayıyla açtı ve kodunu bir kez girdi. Son sayaçlı HTTP 200, toplam başlatılan deneme 82 idi; önceki W02/429 30 Ekim 10:01 tarihli eski kayıttı. Yeni 429 gözlenmedi.

Engellenen submit-code isteğinin kök değişkeni `input=OBJECT`; bir düzey altındaki alan türleri `actor_id=STRING`, `client_mutation_id=STRING`, `code=STRING`, `device_id=NULL`, `encrypted_ap_context=STRING`. Değerler kaydedilmedi. Statik şema kimlik ve `kind=mutation` eşleşmesiyle mevcut; argüman `input`, tek kök `xfb_auth_platform_submit_code`, kapsam eksik değil.

Diğer cihaz onayının kök değişkenleri `apc=STRING`, `device_id=NULL`; statik argümanlar apc/device_id, tek kök `xfb_auth_platform_afad_approval_state`, kapsam eksik değil. Bu işlem ayrı olarak kapalı kalır. Kaydırma, telefonun uzaktan INJECT_EVENTS izni olmadığı için kullanıcı tarafından yapıldı; yalnız açık tanı metinleri okundu.

## 0.1.66 kod gönderim sözleşmesi

Sabit submit-code belge/işlem çifti, GraphQL form sahibinde ayrıştırılan aynı-origin yol ve yalnız `input` köküyle kabul edilir. Alt alan kümesi gözlenen beş alanla tam eşleşir. Anonim actor `0`, null device_id, 1–64 yazdırılabilir karakterli client_mutation_id ve 6–10 rakamlı kod kabul edilir. encrypted_ap_context, mevcut onaylanmış sayfanın APC değeriyle bellekte eşleşmelidir. Bu değer koşulları güvenli kabul koşullarıdır; eski tanı değerleri saklamadığı için gerçek bu girişteki değerlerin koşulları geçtiği henüz söylenmez.

Sözleşme kendi değişmez URL/gövde yetkisini üretir; platform taşıyıcısı aynı nesne kimliğini denetler. Kod ekranı sorgusu ve kod gönderimi için ayrı tek-gönderim durumu vardır. Kod gönderimi de ortak istek aralığını/bütçeyi tüketir, parola giriş aralığını uzatmaz, hata veya iptalden sonra otomatik tekrar etmez. Yanıt yalnız beklenen submit-code kökü ve hatasız GraphQL zarfıyla sayfaya döndürülür. Sonuçta oturum çerezi oluşursa mevcut profil doğrulama akışı devreye girer; bu, yalnız HTTP 200 ile bağlantı tamamlanması değildir.

Kod yanıtının HTTP/JSON/kök doğruluğu ve çerez uygulanması `codeSubmission` bölümünde tutulur. Parola yanıtı ile kod-ekranı yanıtı üzerine yazılmaz. Bilinen ama desteklenmeyen isteğe bağlı cihaz onayı polling'i, e-posta kodu adımının üst açıklamasını sürekli genel hata ile değiştirmez; arayüz bu yöntemin desteklenmediğini açıkça anlatır.

123 JVM testi, 9 JavaScript kontrolü ve 650 TR/EN/ES/PT-BR kaynak denetimi geçti. Release lint 0 hata / önceki 24 uyarı; release, debug ve cihaz testi derlemeleri başarılı. Yeni sentetik WebView vakası kod ekranı ardından ayrı kod POST'u, çerezin yanıt öncesi uygulanması, ilk parola aralığının korunması ve ikinci kod POST'unun reddini sınar; ancak kendi telefonda debug APK kurulumu `INSTALL_FAILED_USER_RESTRICTED` ile reddedildiği için bu vaka çalıştırılamadı. Bu testin geçtiği iddia edilmez.

İmzalı 0.1.66 / code 67: 2.388.707 bayt, SHA-256 `7b47af45eb3e63d3949b98f3279022159f8f3845f1f9802c7a8e5d46ff6e4a6b`. v2/v3 imzası ve paket denetimi geçti. Ana üretim paketi aynı telefonda `install -r` ile veri silmeden güncellendi, sürüm doğrulandı. Telefon saati 1 Kasım 16:23:40 +03:00 iken önceki girişin 15 dakikalık aralığı korunuyordu; kullanıcıya uygulamanın gösterdiği saat dolduktan sonra aynı hesapla tek canlı deneme verildi. Sonuç henüz bekleniyor; başarılı hesap bağlantısı iddiası yok.

## 0.1.66 canlı sonuç · 1 Kasım 16:34

Kullanıcı yeni girişte e-posta kodunu bir kez gönderdi; ardından Hesap doğrulanamadı ekranı açıldı. Telefonun açık tanısından `codeSubmission.status=200`, `jsonParsed=true`, `validData=true`, `cookiesApplied=true` okundu. Son sayaçlı deneme 86 / 1 Kasım 16:34 / HTTP 429; hata ekranındaki yerel mola 2 Kasım 16:34. Yeni giriş veya ek okuma gönderilmedi.

Kaynakta normal girişin `checkLogin` yolu, `readLoginSession` ile sessionid ve sayısal ds_user_id biçimini doğrulamadan WebConnectionAttempt başlatmaz. Bu yolun sonraki isteği `WebRequests.profile` üzerinden profil doğrulamasıdır. Kod POST'unun 200 dönmesi ve son ayrı profil denemesinin 429 olması birlikte değerlendirildiğinde kod gönderiminin ilerlediği, bağlantının profil okumasında sınırlanmaya takıldığı sonucuna varılır. Tracker hesabı bağlantısı tamamlanmış değildir; oturum çerezi tek başına hesap kimliği/profil doğrulaması yerine geçmez.

Tanıdaki `authentication` ilk parola yanıtını korur (`authenticated=false`, 1348009, auth_platform yönlendirmesi). Bu alanın kod gönderiminden sonra aynı kalması yeni bir parola veya kod reddi değildir. Kod sonucunun sahibi ayrı `codeSubmission` bölümüdür. İlk ve sonraki adımlar birbirinin üzerine yazılmaz.

Ek kaynak bulgusu: WebConnectionAttempt.finally, açıklamasında bekleyen kaydın korunacağını söylemesine rağmen `sessions.discardPending(reference)` çağırır; profil 429'unda henüz bağlanmamış yeni bankayı temizler. `leaveLogin` da WebView çerezlerini kaldırır. Bu nedenle mevcut 0.1.66 başarısız doğrulaması sonrasında yeni oturumu tekrar kullanma yolu yoktur. Daha sonraki iyileştirme, yalnız bağlantı bekleyen ayrı şifreli oturumun sahipliğini ve kullanıcının açık devam/silme eylemini tanımlamalı; hesabı doğrulanmış gibi eklememeli, diğer hesabı değiştirmemeli veya mola bitmeden istek göndermemelidir. Bu davranış henüz değiştirilmedi.

## 0.1.63 tam uygulama dili paketleri

İspanyolca (`es`) ve Brezilya Portekizcesi (`pt-BR`) Android uygulama diline tam paket olarak eklendi. Bölge etiketi korunur; `pt` olarak daraltılmaz. Her pakette İngilizce kaynakla aynı 648 anahtar bulunur; dil/tema/liste gezinmesi, otomasyon, bildirimler, oturum/güvenlik yönlendirmesi ve istek tanıları seçilen dilde görünür.

Bu çalışma giriş ağ isteği, WebView, oturum, profil okuması veya bekleme kaydını değiştirmez. Yerel kaynak denetimi dört dilin kaynak türü, anahtar kümesi, biçim parametreleri ve çoğullarını eşitlik olarak denetler; Android lint’in eksik çeviri denetimi de açıktır. Yeni Android cihaz testi, `pt-BR` etiketinin korunmasını ve iki dilde ana ekran etiketinin çevrildiğini sınar. Test APK’leri hazırlandı fakat fiziksel telefon bağlantısı test başlatılırken kesildi; bu cihaz vakası henüz çalıştırılmadı.

## 2 Kasım · Android 0.1.72–0.1.73

Kullanıcının bağlı telefonunda başlangıçta 0.1.68 kurulu ve son sayaçlı yanıt 86 / 1 Kasım 16:34 / 429 idi. Yeni istek gönderilmeden açık İstek bilgileri ekranı okundu. Önceki 0.1.66 kaydıyla uyumlu; yeni bir 429 olarak yorumlanmadı.

Kaynakta doğrulanan yaşam döngüsü hatası düzeltildi: `WebConnectionAttempt`, geçici hata/iptal sonrasında şifreli bekleyen web oturumunu korur. Yeni giriş mevcut bekleyen kaydı sessizce değiştiremez. Devam ekranı, mevcut bekleyen referansı yeniden sahnelemeden yalnız profil GET'i yapar; geçerli profil kimliği ve değişmemiş hesap seçimi olmadan hesap bağlanmaz. Mevcut mola, bütçe, tek etkin istek kuralı ve 15 dakikalık bağlantı aralığı korunur. Kullanıcı açıkça bekleyen bağlantıyı silebilir; kayıtlı hesaplar silinmez. Başarıda yalnız bekleyen işaretçi kaldırılır, bağlanan şifreli banka kalır.

Giriş/doğrulama yanıtındaki sınırlı ve geçerli `X-IG-Set-WWW-Claim` artık aynı sayfaya ve profil okuyucusunun şifreli oturumuna aktarılır; önceden ilk profil isteği her zaman varsayılan `0` ile hazırlanıyordu. Gerçek önceki yanıtta başlığın olup olmadığı veya 429'u açıklayıp açıklamadığı doğrulanmadı. Başlık değeri tanıya yazılmaz.

0.1.72 / code 73 veri silmeden kuruldu; imza/paket kontrolü başarılı, 2.426.299 bayt ve SHA-256 `57b018be3ba03f4b866268e50d0bdd549311bbf443b072d66e150600ba0e7d8c`. Kullanıcı bir giriş yaptı; yanlış parola girmiş olabileceğini ve 15 dakika beklediğini bildirdi. Açık kayıtta 2 Kasım 18:41 son denemesi için HTTP yanıtı yok; `authentication`, `securityView`, `codeSubmission` boş, `blockedDocuments` boş. Parola reddi, CAPTCHA/kod başarısı veya yeni 429 doğrulanmadı. Yeni parola/kod denemesi yapılmadı.

Bu belirsizlik için 0.1.73, giriş bağlantı/depolama hatasında web formunu kapatıp uygulamanın gerçek hata açıklamasını gösterir. Aktarım hataları ayrı sonlu aşama/sınıf/SDK ağ koduyla kaydedilir; iptal ayrıca ayrılır. İstisna metni, URL, giriş bilgisi veya yanıt içeriği kaydedilmez. Tam canlı hesap bağlantısı henüz doğrulanmadı; aşağıdaki test ve sonraki canlı sonuç bu sınırlamayla değerlendirilmelidir.

0.1.73 / code 74 telefona veri silmeden kuruldu; 2.426.299 bayt, SHA-256 `9304f4872df63d4e69d0ee7e7bbf107a16f331bb76ef65649a26fecefab9bdde`. İmza/paket ve DEX'te yeni bekleyen ekran/aktarımı tanılama sınıfları doğrulandı. Yeni izin, açık debug/backup/cleartext veya native kitaplık yok. Son kaynak derlemesi 4 dk 33 sn; 130 JVM testi ve 9 relay JavaScript + 21 probe JavaScript kontrolü geçti. Dört dilde 664 kaynak eşleşir; lint 0 hata / 21 uyarı.

Pixel_7 / API 36 gerçek emülatöründe ilk geniş çevrimdışı tur 52 vaka: 50 başarılı, bir açık canlı smoke atlandı ve kod ekranı sorgusu zamanlama testi başarısızdı. Test, URL değişmesini yeni belgenin hazır olması gibi yorumluyordu; gerçek `FINISHED:AUTH_PLATFORM` olayı beklenerek düzeltildi. Son paketle hedeflenen 25 vakada bu test, kod/claim aktarımı, gerçek Keystore'da bekleyen oturumun yeniden açılması, 429/iptal, devam/silme ekranları ve aktarım hatası yönlendirmesi geçti. Tek ek başarısızlık, testte ortak okuyucu doluyken CAPACITY beklenmesiydi; üretici `RequestGate.claim` bu durumu TOO_SOON olarak bildiriyor. Test üretici sözleşmesine göre düzeltildi ve yeniden çalıştırılan vaka geçti; devamındaki 401'in bekleyen kaydı temizlediği de doğrulandı. Son hedeflenen küme: 24 başarılı, yalnız açık opt-in gerektiren anonim canlı smoke atlandı. Bu testler hesap/Instagram ağı kullanmaz.

0.1.73 canlı telefon denemesi 2 Kasım 19:01: HTTP 200, JSON/data OBJECT, `caaResultPresent=true`, `authenticated=false`. Hata kodu/GraphQL errors yok, oturum ve sahip çerezleri alınmadı/uygulanmadı. CAPTCHA, iki faktör yükü ve yönlendirme bildirilmemiş; `messageClass=NONE`, `exchangeFailures={}`. Bu kayıtta yerel aktarım hatası veya yeni 429 kanıtı yok; sunucu oturum vermedi, ancak ret nedeni belirlenemedi. Kullanıcı bu yeni test hesabıyla normal Instagram'da sıfırdan giriş yapmayı henüz kontrol etmediğini belirtti. Bir sonraki kontrol, aynı hesap bilgilerinin normal tarayıcıda çalışıp çalışmadığını ayırmaktır; peş peşe Tracker denemeleri yapılmaz. Parola/hesap kimliği/kod/ham yanıt kaydedilmedi. Canlı uçtan uca bağlantı hâlâ doğrulanmış değildir.

Karşılaştırma sonucu: Kullanıcı aynı bilgilerin normal tarayıcıda da reddedildiğini bildirdi ve bilgilerin yanlış olabileceğini belirtti. Bu hesapla son ret, uygulamaya özgü hata kanıtı değildir; önceki başarılı kod/sonraki profil 429 kaydı da bununla karıştırılmaz. Kullanıcı yeni hesapla test yapmayı önerdi. Önce normal tarayıcıda başarılı yeni giriş, ardından Tracker'ın mevcut yerel aralığı dolunca yalnız tek deneme planlandı. Bu karşılaştırmanın başarı sonucu henüz yoktur.

## 0.1.73 canlı devam ekranı · 2 Kasım 19:21

Kullanıcı daha sonra normal tarayıcıda başarılı giriş yaptığını, parolayı yanlış hatırlamış olabileceğini bildirdi. Tracker'ın yerel aralığı dolduktan sonraki denemede doğrulama kodunu kendisi gönderdi ve “Instagram oturumu açıldı” açıklaması geldiğini belirtti. Açık diyalogda son adım/profil okuması ile Instagram sınırlaması açıklaması birlikte gözlendi.

Diyalogdaki “Bağlantıyı tamamla”, yalnız devam Activity'sini açtı; kaynakta bu eylem ağ isteği başlatmaz. Gerçek telefonun açık devam ekranında şifreli bekleyen oturum açıklaması, “Yerel mola · 3 Kas · 19:21” ve pasif tamamlama düğmesi okundu. Bekleyen bağlantı silinmedi. Bu ekranın bekleyen referansı şifreli `WebSessions.pending()` sahibinden okuduğu kaynakla doğrulanır; özel kasa/çerez dosyaları dışarı alınmadı.

Ardından kullanıcı yalnız İstek bilgileri ekranını açtı: deneme 92, son HTTP 429, son deneme 2 Kasım 19:21. Normal kaynak akışı, oturum/hesap çerezi biçimi doğrulandıktan sonra profil GET'ini çalıştırdığından, yeni engel girişten sonraki profil okumasıdır. Bu turda kod yanıtının ayrıntıları yeniden okunmadı; kullanıcının kod gönderimi bildirimi ile profil kontrolü aşamasına geçildiği ayrı kanıtlardır. Profil veya takipçi listesi okuması başarılı sayılmadı.

0.1.66'daki oturumu silip yeniden parola/kod isteme hatasının düzeltmesi canlı devam ekranıyla doğrulandı. Sunucunun profil 429'u çözülmüş değildir. Kayıtlı mola korunur; yeni giriş, alternatif uç/taşıyıcı denemesi veya mola öncesi yeniden profil isteği yapılmadı. Sonraki izinli deneme ekranda 3 Kasım 19:21 görünür; bu saat sunucunun isteği kabul edeceği garantisi değildir.

## 3 Kasım · saklanan oturumla tek devam denemesi

Fiziksel telefonda kurulu sürüm 0.1.73 / kod 74 olarak ADB paket bilgisinden doğrulandı. İlk açık İstek bilgileri kaydı hâlâ deneme 92 / 2 Kasım 19:21 / HTTP 429 idi. Saat 19:08'de devam ekranında bekleyen oturum açıklaması, 3 Kasım 19:21 mola bitişi ve pasif tamamlama düğmesi okundu. Yeni parola/kod istenmedi; özel oturum deposu dışarı alınmadı.

Kullanıcı mola sonrasında tamamlama düğmesine bastığını bildirdi. Sonuç ekranında yeni sınırlama mesajı, 4 Kasım 19:29 mola bitişi ve yine pasif düğme görüldü. Ardından kullanıcının açtığı İstek bilgileri ekranında **deneme 93 / 3 Kasım 19:29 / HTTP 429** okundu. Önceki 92 kaydına göre bir sayaçlı deneme artışı vardır; yeni sonuç eski 30 Ekim W02 karşılaştırma kaydıyla karıştırılmadı. Seçili taşıyıcı Android HttpEngine / Chromium olarak göründü. Bu, profil okumasının başarıyla tamamlandığı veya oturumun sunucuda geçerli olduğu kanıtı değildir.

Kaynakta devam düğmesi, saklanan referansla `WebConnectionAttempt.resume` → `WebRequests.profile` üzerinden tek profil GET'i gönderir. Giriş/kod POST'u bu yolun parçası değildir. Yeni 429 sonrasında ek profil isteği, parola tekrarı, alternatif uç/taşıyıcı veya mola kaldırma yapılmadı. Ekranlar yalnız erişilebilirlik metni üzerinden okundu; ham ağ gövdesi, çerez veya parola alınmadı. Sunucunun 429 nedeni henüz belirlenemedi. Önceki uygulama kimliği/başlık aktarımı hipotezi de bu sonuçla doğrulanmış sayılmaz.
