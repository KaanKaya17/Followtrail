# Android 0.1.49 — platform web taşıyıcısı adayı

30 Ekim 2026. Telefon API 33; `build.version.extensions.s/t/r` değerleri 22. Android'in [HttpEngine API'si](https://developer.android.com/reference/android/net/http/HttpEngine) API 34 veya S extension 7 ile kullanılabilir. API sürümü, uzantı ve sınıf mevcudiyeti gönderimden önce seçimi belirler. Telefon–emülatör giriş sonucu farkında taşıyıcı etkisi bir varsayımdır; kök neden olarak doğrulanmış değildir. Mevcut özel Instagram protokolünün her hesapta desteklendiği iddia edilmez.

## Değişiklik ve yetki

- Uygun cihazlarda web giriş ve web profil/liste GET'leri aynı process-owned, tembel başlatılan HttpEngine fabrikasını kullanır. Desteklenmeyen cihazlar, herhangi bir istek göndermeden mevcut okuyucuyu seçer. Başarısız HTTP isteğinden sonra taşıyıcı/URL değiştirme veya uygulama tekrarı yoktur. Mobil oturum sağlayıcısı değiştirilmedi.
- Ek SDK, native kütüphane, izin veya dış veri sunucusu eklenmez. QUIC/Brotli ve disk cache kapalıdır; depolama yolu verilmez. TLS doğrulaması kaldırılmadı. [Chromium Cronet bağlam kaynağı](https://raw.githubusercontent.com/chromium/chromium/main/components/cronet/cronet_context.cc) cookie store'u kapatır; uygulama kendi birinci taraf çerezlerini gönderir ve yanıt çerezlerini mevcut doğrulanmış kayıt sahibine uygular. Bu kaynak incelemesi telefonun birebir native derlemesinin dekompilasyonu değildir.
- `WebLoginContract.authorize`, bir defa ayrıştırıp doğruladığı değişmez FormBody için yetki nesnesi üretir. Aynı nesne request tag'i ve body kimliğiyle taşıyıcıya gider; gövde değiştirilirse yetki geçmez. Genel GraphQL açılmaz. WebFrame köken/ana çerçeve kontrolü, sabit www.instagram.com alıcısı, dar okuma yolları, 429 ve bütçe kuralları korunur.
- Şifreli login yükü sınırlı boyuttadır. UploadDataProvider tekrar sarma isteğini reddeder; uygulama yeni parola isteği üretmez. HttpEngine'in idempotent GET'lerdeki dahili bağlantı/protokol toparlanması için OkHttp ağ interceptor'üyle aynı wire-level sayım garantisi verilmez. 429 başlığı alınır alınmaz yanıt gövdesi beklenmeden istek iptal edilir; uygulama mevcut kalıcı molayı uygular.
- Response başlıkları, gövde kapasitesi veya aktarım hatasında kaybolmaz; okunamayan gövde hata olarak taşınır, sahte JSON oluşturulmaz. Başarılı veri için HTTP/2 gerekir. Redirect takip edilmez. Genel süre 30 sn; timeout/iptal sonrası SDK terminal callback'i gelene kadar fabrika kaynağı sahipli kalır. Devam eden son temizlik varken başka native exchange başlatılmaz. Global fabrika ömrü Android process ömrüdür; ayrı test fabrikaları kapatılır ve kapanış başarısızsa kaynak tutulur.
- Native okuyucuyu A tarayıcı kontrolünden önce denemek için gelişmiş karşılaştırmada ayrı düğme vardır. Bu düğme tek native profil GET'i ile bağlantıyı dener; başarılıysa yerel kaydı bağlayabilir. Farklı numeric ID mevcut etkin hesabın yerini alacaksa önce açık onay istenir. İptal, mevcut hesabı korur. Aynı numeric ID için yeniden bağlantı kişi kayıtlarını korur; çoklu hesap arşivi eklenmedi.

## Doğrulama kapsamı

Üç ek JVM vakası yetki nesnesinin tam gövde kimliğini, anonim public smoke'un çerez/ek sorgu kabul etmediğini ve web host/yöntem sınırını denetler. Native test sınıfı şifreli upload baytlarının parçalı okuma boyunca aynı kaldığını, rewind reddini, close'da byte silmeyi, iptal/politika reddinin istek başlatmadığını ve Call metadata sözleşmesini sınar.

Canlı opt-in smoke yalnız çerezsiz/parolasız giriş sayfasına tek GET ve başlık okumasıdır; HTML indirilmez, hesap girişi veya profil isteği atılmaz. Gerçek hesap başarısının yerine sayılamaz.

### Çalıştırılan kontroller

- Son üretim derlemesi: `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest`, tek worker/in-process Kotlin. 2 dk 48 sn, 105 JVM testi başarılı; release lint 0 hata / 22 uyarı.
- API 37 emülatörde ilk cihaz turu 48 vaka / 193,775 sn: gerçek çerezsiz platform HTTP/2 smoke başarılı; yalnız hesap-değiştirme testinin başlangıç verisi, geçerli Cookie header yerine düz token yazıldığı için AccountCodec tarafından reddedildi. Bu hata üretim girişine veya koruma diyaloğuna ulaşmadan oldu. Test oturumu mevcut oturum biçimine düzeltildi; üretim APK'sı değiştirilmedi. `live_login_page` verilmediği için ayrı canlı form testi atlandı. Düzeltme sonrası karşılaştırma sınıfının **8 testi / 67,017 sn** başarılı; gerçek anonim GET tekrar edilmedi. Tam 48-vaka turu yeniden çalıştırılmış gibi raporlanmaz.
- TR/EN 629 kaynak eşleşiyor; değişkenler ve çoğullar geçerli. `git diff --check` temiz.
- İmzalı `InstagramTracker-0.1.49.apk`: versionCode **50**, **2.220.696 bayt**; SHA-256 `b9165649ee3f14b5298e2ebb4d17eb2d3b8629303ec02d05ee2436eb8b493265`. Mevcut üretim anahtarı; INTERNET, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, POST_NOTIFICATIONS. Debuggable/backup/cleartext kapalı, native kütüphane yok; debug test taşıyıcısı pakette yok.

### Telefon doğrulaması

0.1.49 üretim paketi bağlı API 33 telefona `adb install -r` ile kuruldu; package manager versionName 0.1.49 / versionCode 50 doğruladı ve MainActivity açıldı. Veri temizleme veya kaldırma yapılmadı.

30 Ekim 2026 yaklaşık 09:57 (Europe/Istanbul): kullanıcının açtığı LP01 tanısı USB üzerinden okundu. **Gerçek telefon girişi başarılı:** HTTP 200, `authenticated=true`, `sessionCookieReceived=true`, `ownerCookieReceived=true`, `cookiesParsed=true`, `cookiesApplied=true`; `captchaNeeded=false`, `twoFactorPayloadPresent=false`, `messageClass=NONE`, boş errorCodes. Kaynak sayacı bir `ALLOW:POST:AUTH` gösteriyor. Bu kayıttaki 14 `LOCAL_BLOCK:PART:403`, yerel filtre tarafından durdurulan yardımcı kaynaklardır; Instagram'ın giriş reddi değildir. Ham form, parola, oturum değeri, yanıt veya hesap ID'si alınmadı. Bu yalnız oturum açmayı doğrular; uygulamanın takipçi/takip edilen veya profil okumasının başarılı olduğu henüz kanıtlanmadı.

0.1.47 tanısındaki tek gerçek reddin ilk kullanıcı adı kesin değil; kullanıcı ilk hesabı hatırlamadığını belirtti. Sonraki yerel bloklar başka hesapların Instagram tarafından reddedildiğini kanıtlamaz. Yeni sürümdeki başarı, eski sürümle aynı anda ve aynı koşullarda karşılaştırma yapılmadığından, tek başına önceki reddin yalnız taşıyıcıdan kaynaklandığını kanıtlamaz.

Kullanıcı doğrudan native okuyucu düğmesine bir kez bastıktan sonra yaklaşık 10:00'da farklı hesap uyarısı gözlendi: sabit uyarı metni ve `web_login_replace_confirm` düğmesi görünür. Bu dal profil GET'inden ve kayıt değiştirmeden önce onay ister. Kullanıcının mevcut yerel kaydı değiştirme kararı bekleniyor; onaysız değiştirme yapılmadı. Bir sonraki adım, A tarayıcı okumasını araya sokmadan doğrudan native okuyucunun bir profil GET'iyle sınanmasıdır. Gerçek 429 halinde yeni deneme yapılmaz ve mevcut mola korunur.
