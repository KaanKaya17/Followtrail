# Telefondan giriş — 0.1.23 önizleme

Bilgisayar, USB, ek cihaz veya dosya aktarımı bu giriş yolu için gerekmez. APK mevcut uygulamanın üzerine güncellenir; uygulamayı silmek/verisini temizlemek gerekmez. Önceki çalışan 0.1.22 APK ve kaynak yedeği korunur. Android eski sürüme veri kaybetmeden doğrudan geri kurmaya izin vermeyebilir; yedek APK bulunması otomatik veri geri dönüşü demek değildir.

## Temel deneme

1. 0.1.23 APK’yı mevcut uygulamanın üzerine kur. Mevcut hesabın, kişiler ve geçmişin açıldığını kontrol et.
2. İlk kurulumda **Hesap bağla**; bağlı hesabın varsa **⋯ → Bağlantıyı yönet → Başka hesap bağla** seç. **Telefondan bağlan · Bilgisayar gerekmez** yolunu aç.
3. Erişim/parola/oturum açıklamalarını oku. Kendi kontrolündeki test hesabının kullanıcı adını veya e-postasını ve parolasını girip **Giriş yap** de. Parola/kodu geliştiriciye gönderme.
4. Ekranı açık tut. İlk kurulumun adımları mevcut istek aralığıyla ilerlediğinden yaklaşık iki dakika veya daha uzun sürebilir. Bekleme mesajı sıradaki adımın süresini gösterir. İşlem sürerken arka plana geçmek iptal eder.
5. Kod istenirse hesabında açık yöntemi seç (doğrulama uygulaması, SMS veya yedek kod); kod ekranı geldiğinde kodu gir. Kod almak için diğer uygulamaya gidip bu ekrana dönebilirsin. Kod adımında parola yeniden gönderilmez. Deneme en çok 10 dakika/18 istek sürer.
6. Başarılı profil doğrulamasından sonra **Hesap bağlandı** mesajı ve kendi hesabının iki sayacı gelmeli. Aynı hesap yeniden bağlandıysa kişiler, favoriler, notlar ve geçmiş korunmalı.
7. Kendi takipçi ve takip edilen listeni ayrı ayrı açıp **Listeyi güncelle** seç. Mevcut önbellek ve sıra/mola kuralları geçerlidir. Uygulamayı kapatıp açtığında tekrar parola istenmeden kayıtlı profil görünmeli; açık yenileme oturumu kullanmalı.

## Hata, iptal ve doğrulama

- İlk testi izole bir test hesabıyla yap. Instagram bu resmî olmayan girişi veya ek doğrulama adımını reddedebilir. Sunucu kabulü ve bütün hesap/doğrulama çeşitleri henüz canlı sınanmadı. Başarısızlığı yeni hesap açarak veya art arda giriş yaparak tekrar etme.
- 429/bekleme görürsen dur; **⋯ → İstek bilgileri** içindeki son HTTP durumu ve bekleme saatini kontrol et. Güncelleme mevcut molayı kaldırmaz. Yeni parola denemesi en az 15 dakika aralıklıdır; Instagram beklemesi daha uzun olabilir. Sürekli Giriş yap’a basmak gerekmez.
- Instagram ek onay istiyorsa **Instagram’da kontrol et** ile resmi uygulama/tarayıcıda duruma bak. Bu adım kendiliğinden girişe devam etmez. Girişin kesin ne zaman yenileneceği bilinmez; Instagram oturumu iptal ederse yeniden bağlanmak gerekir.
- Giriş sırasında **İptal** veya Geri: yeni hesap bağlanmamalı, eski hesap/kayıtlar kalmalı. Devam eden istek sırasında başka uygulamaya geçmek iptal eder. Kod bekleme ekranından ayrılmak ise kod bağlamını süreç belleğinde korur.
- Ekranı döndürmek denemeyi yeniden başlatmamalı. Süreç tamamen kapanırsa parola/kod geri yüklenmez; yeni deneme gerekir. Ana ekranda bekleyen bağlantı uyarısı kalırsa aynı bağlantı ekranını açıp Geri ile kapat.
- Boş giriş ağ isteği göndermeden alan uyarısı göstermeli. Kod yanlış/eksikse açık kod ekranında düzeltilebilmeli. Parola hiçbir kalıcı kayda/loga yazılmaz.

## Geliştirici doğrulama sınırı

Üretim giriş/parser/şifreleme/istek kapısı/Keystore kodları emülatörde sentetik Call.Factory yanıtlarıyla sınanır; testler Instagram’a istek göndermez. Bu sonuç, Instagram sunucusunun güncel kabulünü kanıtlamaz. Fiziksel cihazla canlı parola/kod girişi kullanıcı testidir. Windows’ın önceki canlı sonucu telefon uyarlamasının canlı sonucu diye sunulmaz.

Parametre şablonları mevcut Windows ortamında sabitlenmiş instagrapi 3.2.0 mixins/bloks.py içindeki 11 giriş adımından uyarlanmıştır. MIT lisansı uygulama varlıklarında korunur. Donanım attestation anahtarı yokluğu upstream biçimde bildirilir; sunucu kabulü zorlanmaz. Kapsam dışındaki doğrulama biçimleri açık hatayla durur; yeni uç/kimlik/cihaz denemesi yapılmaz.

Şablon kaynağı: yerelde kurulu instagrapi 3.2.0 mixins/bloks.py; SHA-256 `4270138a044eb45b3229a92abff26a4799d86f40affe1147c08ee11130e9a222`. Şablonlar kimlik/parola/kod içermez; bu alanlar yalnız çalışma sırasında doldurulur. Giriş beklerken ekranın kendiliğinden kapanması önlenir; form ve kod bekleme ekranında bu bayrak kaldırılır.
