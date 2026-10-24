# InstagramTracker

Yerel Android uygulaması: kendi hesabının özeti ve seçerek eklediğin kişilerin sayı/geçmiş analizi.

Geliştirme önizlemesi **0.1.6**. Windows'ta çalışan kayıtlı mobil oturumun Android'e şifreli aktarımı ve mobil profil okuyucusu eklendi. Android'de gerçek sayaç okuması henüz doğrulanmadı; önceki web denemesi HTTP 429 döndürdü. Yerel mola korunur. Çevrimdışı testler canlı erişimin kanıtı değildir. [Doğrulama kaydı](docs/VALIDATION.md).

## Kullanım

Bağlantı için Windows'taki mobil oturumu şifreli aktarma ve önceki uygulama içi HTTPS web girişi seçenekleri vardır. Mobil aktarım aşağıda açıklanır. Web girişinde uygulama parola alanını okumaz; gerekli oturum çerezlerini kullanır. Instagram giriş/doğrulama veya kısıtlama uygulayabilir. Kişisel hesap uçları resmî geliştirici API sözleşmesi değildir ve değişebilir.

- **Özet:** kendi hesabının son başarılı takipçi/takip edilen sayıları ve gözlem zamanı. Açılışta önce kayıt gösterilir. Otomatik yenileme için kayıt en az 15 dakika eski olmalı; otomatik denemeler de en az 15 dakika aralıklıdır.
- **Kişiler:** kullanıcı adı veya HTTPS Instagram profil bağlantısıyla kişi ekle. İlk sorgu tek profil isteğidir. Sonraki sorgular sabit hesap kimliğine göre yapılır; kullanıcı adı değişmesi başka kişinin geçmişiyle karışmaz. Liste ve analiz açılışında otomatik sorgu yapılmaz.
- **Analiz:** takipçi, takip edilen, varsa gönderi sayısı; önceki gözleme göre net fark; 7/30 gün veya tüm kayıtlar; zaman eksenli grafik; son 10 gözlemin metin dökümü; özel not ve grup. Eksik gönderi sayısı sıfır yapılmaz. Geçmiş ilk gözlemden başlar; gelen/giden kişilerin kimliği çıkarılamaz.
- **Bildirim:** kişi analizinde izlemeyi aç; en az 1, 6 veya 24 saat aralığı seç. Takipçi/takip edilen sayısında gözlemlenen net değişim için yerel Android bildirimi gelir. İlk kayıt ve yalnız gönderi değişimi bildirim üretmez. Uygulama ekranı kapalıyken de JobScheduler çalışabilir; anlık bildirim değildir. Pil tasarrufu, Doze, üretici ayarları ve çok sayıda izlenen kişi gecikme yaratır. Zorla durdurma sonrası uygulamayı yeniden açmak gerekir.
- Bu önizlemede cihaz kaydını sınırlamak için en fazla **20 kişi**, kişi başına son **90 başarılı gözlem**, 500 karakter not ve 40 karakter grup saklanır. Bunlar Instagram'ın güvenli istek limitleri değildir.
- Tek kişiyi kaldırma o kişinin geçmiş ve notunu siler. **Bağlantıyı kaldır** bu uygulamadaki oturumu ve bütün kişileri siler; diğer Instagram oturumlarını kapatmaz.

## Bağlantı prototipi

### Windows'taki mobil bağlantıyı APK'ya taşıma

**Bilgisayardaki bağlantıyı aktar**, Windows prototipinde kurulmuş oturumu Android'e taşır. İlk eşleştirme bilgisayar/ADB gerektirir; başarılı okumadan sonra APK bağımsız çalışır. Bu sürüm Android içinde yeni mobil parola/CAA girişi uygulamaz; WebView girişi ayrı seçenek olarak kalır.

1. APK'da **USB aktarımını hazırla**. Cihaza özel anahtar ve 10 dakikalık aktarım isteği oluşturulur.
2. Aynı Windows kullanıcısında `scripts/link-desktop.ps1` çalıştır. Önceden çalışan `InstagramCounter` ortamı ve DPAPI oturumu gerekir. Script oturumu yalnız bellekte açar, bu cihaza özel anahtarla şifreler ve ADB'den aktarır. Giriş/profil isteği, sunucu, parola/kod kopyalama yoktur. Birden fazla cihaz varsa `-Serial`; debug paket için `-DebugApp` kullanılır.
3. APK'da **Şifreli aktarımı al**. Bu adım Instagram'a bağlanmaz ve mevcut hesabı değiştirmez. Aktarım dosyası ve tek kullanımlık anahtar temizlenir; oturum Keystore ile şifreli kasaya alınır.
4. Yerel mola dolunca **Bir kez sayıları oku**. Yeni oturumdan kimliği doğrulanmış başarılı yanıt gelirse ana hesap bağlanır; aynı hesabın kişi/geçmiş kayıtları korunur. Özet, kişiler ve arka plan kontrolleri bu oturumu kullanır. İlk bağlantı denemeleri kalıcı en az 15 dakika aralıklıdır.

Aktarım ekranı açıkken otomatik kontroller bekler. Süreç kapanırsa bu ekranı yeniden açıp **Bitir ve ana ekrana dön** ile bekletmeyi kaldır. Aktarım manuel moda alır; başarısız deneme sonrası eski bağlantıdan gizli yenileme yapılmaz. Başarılı açık okuma normal önbellekli akışı yeniden açar. [Sözleşme ve sınırlar](docs/MOBILE-PORT.md).

### Önceki web deneyi

Ana ekrandaki **Bağlantı prototipini aç**, eski 429 kaydını koruyarak farklı bir erişim hipotezini sınar: giriş ve tek web profil okuması aynı WebView oturumunda kalır. Giriş/doğrulama sonrası kullanıcı adını girip **Tek okumayı başlat** seçilir. Sayfa açma ve profil okuma otomatik başlamaz; her deneme tek açık okuma ve en az 15 dakika aralığıyla sınırlıdır. Sonuç ana hesaba/izlenen kişilere aktarılmaz ve arka plan bildirimlerine bağlanmaz. Çalışacağı veya 429'u kaldıracağı garanti edilmez.

Prototip açıkken otomatik kontroller duraklar. **Denemeyi bitir** tarayıcı verilerini temizleyip bu bekletmeyi kaldırır; önceki 429'u kaldırmaz. Süreç kapanıp bekletme kalırsa prototipi yeniden açıp bitir. Debug demoda **Bağlantı prototipi · çevrimdışı** aynı JS/WebView akışını sentetik yanıtta sınar. [REA bulguları, yöntem ve sınırlar](docs/CONNECTION-PROTOTYPE.md).

## İstekler ve sınırlama

Bütün profil okumaları aynı kalıcı en az 15 saniye aralığını ve tek etkin bağlantıyı kullanır. Otomatik okumalar ortak 15 dakikalık bütçeyi paylaşır; her arka plan çalışmasında yalnız sıradaki bir kişi sorgulanır. 20 kişi açıkken herkesin saatte bir kontrol edilmesi beklenmemelidir. Kişi bazındaki süre başarısız istekten önce de ayrılır. Toplu takipçi taraması, farklı uçlara tekrar deneme veya otomatik yeniden giriş yoktur. Düğmeler istek sırasında kapanır; ekranın eski isteği iptal edilirse geç yanıt kaydedilmez.

Bildirim izni/kanalı kapalıysa arka plan sorgusu yapılmaz. Oturum veya doğrulama hatasında otomatik çalışma yeniden bağlantıya kadar durur. İzlemeyi kapatmak, kişiyi silmek veya bağlantıyı kaldırmak ilgili işi/bildirimi iptal eder. Bildirim ayrıntıları kilit ekranında gizli olarak işaretlenir; son görünüm Android ve kullanıcının sistem ayarlarına bağlıdır. Bildirim yalnız iki gözlem arasındaki net farkı gösterir; aradaki bütün hareketleri göstermez.

Instagram 429 veya tanınan bekleme yanıtı verirse otomatik okuma kalıcı olarak durur. İlk yerel mola en az 1 saat; tekrarlarında 2/4/8/16/24 saate çıkar. Sunucunun Retry-After değeri daha uzunsa korunur. Bu saat Instagram kısıtlamasının kalkış saati değildir. Mola bitince kendiliğinden deneme yapılmaz; başarılı bir açık kullanıcı yenilemesi normal önbellekli akışı açar. Önceki sürümde kaydedilen sınırlama da otomatik okumayı durdurur.

Ekran kaydedilmiş son sınırlama yanıtının zamanını ve yerel molayı ayırır. Eski uyarının görünmesi yeni bir Instagram isteğinin reddedildiğini tek başına göstermez. Gerçek sınırlamanın nedeni ve süresi bu uygulamadan kesin belirlenemez.

## Bilgisayarda aynı Android kodunu deneme

Önceden kurulu Android SDK/emülatör ve JDK 21 gerekir. Bu iş akışı yazılım kurmaz. Telefon sürümüyle aynı ana Kotlin ekranları, parser, istek kapısı ve şifreli kayıt kullanılır. Debug sürümündeki belirgin **DEMO · ÖRNEK VERİ · AĞ İSTEĞİ YOK** modu yalnız sentetik yanıt döndürür; gerçek hesaba bağlanmaz.

```powershell
.\gradlew.bat :app:assembleDebug
.\scripts\start-preview.ps1
```

Bu çalışma alanındaki TrackerQA AVD'si kullanılır. Başka bilgisayarda mevcut AVD'yi `-AvdName` ile seç. Emülatör zaten başsız çalışıyorsa görünür pencere için önce onu kapatıp script'i yeniden çalıştır. Debug karşılama ekranında **Çevrimdışı demoyu aç** da bulunur. Örnek iki kişinin geçmişi vardır; yeni kişi için `@efe.demo`, bulunamama senaryosu için `@bulunamadi.demo` denenebilir. Gerçek kullanıcı adı girilse de demo gerçek profil sorgulamaz. Demo bildirimleri de ayrı debug servisiyle sentetik taşıma kullanır; bildirimde DEMO etiketi bulunur.

Release APK farklı paket kimliği kullanır, demo taşımasını içermez ve `offline_preview` parametresini yok sayar. Demo başarısı canlı Instagram başarısı değildir. Gerçek hesap denemesi istenirse demo dışında ayrıca giriş gerekir; telefondaki oturum bilgisayara taşınmaz.

## Derleme ve doğrulama

Android 7.0+ / API 24; compile/target 36. JDK 21 ve build-tools 36.0.0 kullanılır. SDK'yı `ANDROID_HOME` veya izlenmeyen `local.properties` ile tanımla.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
node scripts/test-connection-probe.mjs
```

Windows: `gradlew.bat`. [Yayın hazırlama](docs/RELEASING.md), [katkı](CONTRIBUTING.md), [CI](https://github.com/KaanKaya17/InstagramTracker/actions/workflows/android.yml). Gradle dağıtımı SHA-256, action sürümleri commit SHA, bağımlılıklar lockfile ile sabittir. Test uygulaması `.debug` paketinde üretim verisinden ayrıdır. CI testleri gerçek Instagram oturumu kullanmaz.

## Gizlilik

Oturum, kişiler, geçmiş ve notlar AES-GCM / Android Keystore ile bu cihazda şifrelenir. Bulut yedekleme ve cihaz aktarımı kapalıdır. Kendi sunucusu, reklam, analitik, çökme toplama SDK'sı ve işlemciye özel native kütüphane yoktur. Canlı giriş/profil istekleri Instagram'a gider; verinin hiç ağdan çıkmadığı iddia edilmez. [Gizlilik ve sınırlar](docs/PRIVACY.md), [güvenlik bildirimi](SECURITY.md).

[Canva ilk arayüz taslağı](https://canva.link/g4rw1g4hoiufg4m) · [Sürümler](CHANGELOG.md)
