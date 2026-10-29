# Android 0.1.33 — Web bağlantısını karşılaştır

29 Ekim 2026. Bu sürüm bilgisayarsız bağlantının hangi aşamada bozulduğunu ayıran kontrollü bir yol ekler. Canlı Instagram girişinin düzeldiği henüz doğrulanmadı. Windows 0.2.11 ve örnek web işlevleri değiştirilmedi.

## Telefonda yapılacaklar

1. APK'yı mevcut uygulamanın üzerine kur; uygulamayı silme, verisini temizleme veya önceki hesabın bağlantısını kaldırma. Yeni kurulum mevcut mola/hesap/geçmişi korur.
2. **⋯ → İstek bilgileri** ekranında kayıtlı molanın sürüp sürmediğine bak. Mola sürüyorsa giriş başlatma. Bitiş saati Instagram'ın erişim vereceğinin garantisi değildir.
3. **⋯ → Gelişmiş → Web bağlantısını karşılaştır** yolunu aç. Instagram'ın kendi formunda girişini ve varsa doğrulamayı kendin tamamla. Parola, doğrulama kodu veya çerez paylaşma.
4. Giriş sonrası **A · Tarayıcı içinde doğrula** düğmesine bir kez bas. Bu, bağlı sayfayla aynı WebView içinde kendi sayısal hesap kimliğin için bir profil GET isteğidir. Başarılı olursa iki sayaç gösterilir; hesap henüz uygulamaya bağlanmaz.
5. A başarılıysa ekranda kal. Mevcut en az 15 saniyelik istek aralığı dolunca **B · Uygulama ile bağlan** açılır. Bir kez bas. B aynı profil işlemini uygulamanın üretim okuyucusuyla doğrular; doğru hesap yanıtı alınırsa mevcut staging/şifreli kayıt yoluyla bağlantı tamamlanır. İkinci adım kendiliğinden başlamaz.
6. İlk 429, doğrulama veya bağlantı hatasında dur. Yeniden A/B deneme, alternatif giriş yoluna atlama veya mola temizleme yapma. **İstek bilgileri** bölümündeki **W01 / W02, sonuç ve HTTP** satırları yeterli tanıdır. Kullanıcı adı/sayaçları paylaşmak gerekmez.

A veya B sırasında başka sayfaya/arka plana geçmek devamı iptal eder; A başarısı tek başına bağlantı kabul edilmez. Giriş ekranındaki kodu almak için başka uygulamaya geçmek, A henüz başlatılmamışsa bu yeni iptal koşulunu çalıştırmaz. Süreç/döndürme devam belgesini kaybederse otomatik yeniden okuma yapılmaz; önceki kayıt ve mola kalır. Yeni karşılaştırma mevcut 15 dakikalık bağlantı aralığına uyar.

## Sonuçların anlamı

- W01 başarılı / W02 başarılı: Tarayıcı ve uygulama profil okuması kabul edildi, hesap bağlandı. Liste okuması, yeniden açılış ve arka plan oturumu bundan sonra ayrı doğrulanır; yalnız bu sonuçla tamamı çalışıyor denmez.
- W01 başarılı / W02 başarısız: Aynı profil işlemi iki bağlamda farklı sonuç verdi. Oturum/istemci farkı için somut karşılaştırma vardır; nedenin yalnız TLS, tek başlık veya tek çerez olduğu henüz çıkarılamaz.
- W01 başarısız: İkinci istek gönderilmez. 429, giriş/doğrulama, yanıt/kimlik ve bağlantı hataları ayrı sonuçlardır. Bu tek sonuç hesap/IP/WebView nedenlerini kesin ayırmaz.
- W02 yok / Başlatıldı / İptal: Tamamlanmış ikinci sonuç yoktur; başarısız HTTP yanıtı uydurulmaz. Uygulama kapanırsa önceki HTTP yeni denemeye taşınmaz.

## İstek, oturum ve veri sahipliği

`BrowserProfileProbe` ilk adımın tek etkin lease'ini, JavaScript slotunu, 20 saniyelik fetch / 25 saniyelik dış bütçeyi, iptali ve sonucu yönetir. `WebRequests.profile` her iki adımın sabit www.instagram.com HTTPS yolu ve uygulama başlıklarının sahibidir. Eski kullanıcı adı / farklı app-id deneyini tekrarlamaz; kendi sayısal kimliğine `/api/v1/users/{id}/info/` kullanır. Kullanıcıdan URL/başlık/istek tarifi alınmaz.

Tarayıcı Cookie/User-Agent/Referer ve taşımasını kendisi yönetir; uygulama okuyucusu mevcut izinli çerez kümesini ve yerel UA'yı kullanır. Bu yüzden karşılaştırma **aynı profil işlemi** üzerindedir, ağdaki iki iletimin bayt bayt aynı olduğu iddia edilmez. Yönlendirme, otomatik tekrar veya alternatif uç yoktur. Tarayıcı yanıttan yalnız kimlik, kullanıcı adı ve iki sayacı native koduna verir; ham gövde/biyografi/parola/kod/başlık tanıya taşınmaz. Yanıt 1 MiB ile sınırlıdır; eksik sayaç sıfır olmaz. Kimlik native parser ile yeniden doğrulanır.

İlk adım mevcut `beginPrototype` ile bir karşılaştırma turunu ve 15 dakikalık yeniden bağlantı aralığını rezerve eder. Doğrulanmış tarayıcı sonucu aynı gate/hesap/UA için bellekte tutulan, süreli ve tek kullanımlık `BrowserConnectionProof` oluşturur. İkinci açık düğme bu turun devamıdır: aynı 15 saniyelik gate ve 429 durdurması kullanılır; yeni tur açılmaz. Yanlış kimlik, süre bitimi, önceki kullanım, iptal veya yeni mola ile yeni okuma yapılamaz. B öncesinde `gate.succeeded` çalışmaz; ana hesap yalnız native kimlik kontrolü ve mevcut atomik bağlantı tamamlandıktan sonra güncellenir.

`WebConnectionJournal` yalnız sabit aşama/sonuç enumu, başlama saati ve varsa HTTP durumunu uygulamanın özel tercihlerinde tutar. Çerez, parola, kod, hesap kimliği, kullanıcı adı, sayaç, URL veya ham yanıt kabul eden alanı yoktur. Sonuç İstek bilgileri'nde Türkçe/İngilizce gösterilir; bağlantıyı kaldırmak kaydı temizler. Bilinen 429 bir kez uygulanır ve sonraki adım gönderilmez. A başarısı 429/otomatik kontrol duraklamasını kaldırmaz. Normal bağlantı, mobil giriş ve eski deney ayrı korunur.

## Yerel doğrulama

`node scripts/test-web-connection-probe.mjs`: 21 offline durum geçti. Sabit origin/yol, tek GET, tarayıcının oturum sahipliği, 0 sayacı, kimlik/sayı biçimleri, 401/403/404/429, gövde sinyalleri, 1 MiB sınırı, timeout/iptal ve ham kişisel alanların sonuçtan çıkarılması sınandı. Önceki `test-connection-probe.mjs`: 13 durum geçti. Yeni script CI doğrulama işine eklendi; bu kayıt uzak CI geçti iddiası değildir.

Android emülatör testleri ayrı debug paketi ve sentetik WebView/API fixture'ları kullanır. Üretim sürümü debug fixture'ını etkinleştiremez. Canlı kabul, liste sayfalaması, uzun oturum, farklı fiziksel cihazlar ve yeni parola/2FA kabulü bu yerel testlerin yerine geçmez. Gerçek telefon test sonucu ayrıca kaydedilmeden "giriş sorunu çözüldü" beyanı verilmez.

- `:app:testDebugUnitTest`: 84 test, 0 hata. `:app:lintRelease`: 0 hata / 14 uyarı. Release/debug/test APK derlemeleri başarılı. `scripts/check-locales.py`: 575 eşleşen Türkçe/İngilizce kaynak.
- API 37, 3072 MiB emülatörde `WebComparisonBoundaryTest` (4), `WebComparisonScreenTest` (5), `WebConnectionTest` (7), `WebLoginScreenTest` (4): **20 test geçti**. Gerçek `MainActivity`, WebView `fetch`, Keystore, gate ve üretim native okuyucusu; ağ yerine yalnız debug yanıtları kullanıldı. B öncesinde ana kaydın oluşmadığı, ekrana geri dönünce yeni okuma yapılmadığı, 15 saniyelik aralığın ve 15 dakikalık tur rezervasyonunun korunduğu, devamın tekrarlanamadığı, yanlış kimlik/sona erme/yeni molada HTTP açılmadığı ve metadata alanlarının sınırlı olduğu doğrulandı. Önceki web bağlantısının dört UI ve yedi taşıma/oturum regresyonu da geçti.
- İlk UI koşusunun bir kontrolü, arka plan kaydı yazıldığı anda ana süreçteki sonuç sunumu henüz tamamlanmadığından erken okudu. Test artık kullanıcıya sunulmuş terminal sonucu bekler; son koşu `OK (20 tests)` / 56,525 saniyedir. Ekranların sentetik görüntüleri ve tanı çıktısı yerel artifacts altında tutulur; kişisel telefon verisi rapora eklenmez.
- İmzalı APK paket kontrolü: uygulama kimliği aynı, 0.1.33 / versionCode 34; debug/yedek/açık metin trafik kapalı, izinler aynı, native kütüphane yok ve üretim DEX'lerinde debug fixture'ı yok. Telefon ADB'de bağlı ve eski kurulum 0.1.30 olarak görüldü; gerçek hesaba yeni giriş/profil isteği başlatılmadı. Mola/oturum özel deposu root veya veri çıkarma yoluyla okunmadı.
