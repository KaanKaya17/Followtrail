# InstagramTracker

Yerel Android uygulaması: kendi hesabının özeti ve seçerek eklediğin kişilerin sayı/geçmiş analizi.

Geliştirme önizlemesi **0.1.18**. Açık tema ve canlı renkler; içeriği örtmeyen alt gezinme; kartlarda düzenlenmiş geçmiş, bildirimler ve notlar. Kayıt zamanı, liste kapsamı, arama sonucu ve çevrimdışı görüntüleme ayrı gösterilir. Profil geçmişinde kullanıcı adı, yanıtta varsa biyografi ve karşılaştırılabilir fotoğraf bilgisi mevcut profil yanıtından alınır; görünür profil fotoğrafları mevcut bağlantıdan oturum bilgisi gönderilmeden yüklenip özel cihaz önbelleğinde tutulabilir. İsteğe bağlı liste otomasyonu ayrıca aşağıda açıklanır. Eski oturum ve kayıtlar korunur. Çalışan 0.1.8 ve önceki 0.1.9 kaynak/APK yedekleri korunur. [Arayüzü inceleme](docs/UI-TEST.md) · [Doğrulama kaydı](docs/VALIDATION.md).

25 Ekim fiziksel telefon testinde kullanıcı, mobil bağlantıyla kendi sayaçlarının, kendi takip edilen listesinin ve arkadaşının ilk takipçi sayfasının geldiğini bildirdi. Bu canlı sonuç 0.1.8'e aittir; yeni arayüz kontrolleri ayrı debug paketi ve sentetik veri kullanır. Çok sayfalı canlı devam ve uzun süreli oturum henüz sınanmadı. [Liste testi](docs/FOLLOW-LISTS-TEST.md).

## Kullanım

Son incelenen profiller 15 dakika boyunca, en fazla 20 kayıtla süreç belleğinde yeniden kullanılabilir; geri açılış tazelik süresini uzatmaz. Normal kısa beklemeler iptal edilebilir sıraya alınır. 429/doğrulama molaları otomatik devam ettirilmez. Kayıtlı/geçici profilde ortak özet; Değişiklikleri izle ile açık kayıt/izleme eylemi ve Yalnız kişilere ekle seçeneği bulunur. Ana sayfada son değişiklikler/favoriler önde, otomasyon kısa durum kartındadır.

- **Profil bilgileri:** kayıtlı kişinin analizinde biyografi ve Profil geçmişi. Kullanıcı adı/biyografi değişimlerinde eski-yeni değerler, fotoğrafta dönen kimlik/varsayılan fotoğraf durumu karşılaştırılır. Yalnız URL farkıyla bildirim üretilmez; bildirim görsel benzerliği analizi yapmaz; ekranda gösterilen küçük görsel önbelleği ayrı tutulur. Eksik alan silinmiş sayılmaz, ilk bilinen değer başlangıçtır. Açık izleme mevcut 1/6/24 saat kurallarıyla bu değişimleri de bildirebilir. [Test adımları](docs/PROFILE-CHANGES-TEST.md).

- **Son değişiklikler:** ana sayfada kayıtlı kişilerin sayı farkları ve mevcut liste sayfaları, okuma zamanına göre gösterilir. İlk üç kayıt ve Tümünü gör ile en fazla 20 kayıt; analiz/liste açılışı yeni istek göndermez. Akış saklanan geçmişe dayanır, kesin takip/çıkış zamanı değildir.
- **Favoriler:** kişi kartı veya analizde yıldız. Favoriler kişi listesinin başında görünür; bayrak hesap/kişi kimliğine bağlı olarak şifreli saklanır. Açıp kapatmak okuma veya bildirim kontrolü başlatmaz. İzleme önceliği bu sürümde değişmez.

Bağlantı için Windows'taki mobil oturumu şifreli aktarma ve önceki uygulama içi HTTPS web girişi seçenekleri vardır. Mobil aktarım aşağıda açıklanır. Web girişinde uygulama parola alanını okumaz; gerekli oturum çerezlerini kullanır. Instagram giriş/doğrulama veya kısıtlama uygulayabilir. Kişisel hesap uçları resmî geliştirici API sözleşmesi değildir ve değişebilir.

- **Dil:** sağ üstteki Seçenekler → Dil / Language üzerinden Türkçe veya English seçilebilir. Tercih yeniden açılışta korunur. Ekranlar, yeni bildirimler, tarih ve sayı biçimi seçilen dili kullanır; profil içerikleri ve özel notlar çevrilmez.
- **Özet:** hesap, iki sayaç, son kayıt zamanı ve tek Yenile düğmesi. Son 15 dakikadaki kayıt tekrar kullanılır. Açılışta otomatik yenileme varsayılan kapalı; Seçenekler → Yenileme ayarları ile açılabilir. Kayıt en az 15 dakika eski olmalı ve sınırlama bulunmamalıdır.
- **Kişiler:** kullanıcı adı veya HTTPS Instagram profil bağlantısıyla kişi ekle. İlk sorgu tek profil isteğidir. Sonraki sorgular sabit hesap kimliğine göre yapılır; kullanıcı adı değişmesi başka kişinin geçmişiyle karışmaz. Liste ve analiz açılışı yeni profil/liste API sorgusu yapmaz; görünür fotoğraflar için ayrı CDN yüklemesi olabilir. Kişi kartındaki çöp kutusu ve analiz altındaki Kişiyi kaldır aynı onaylı silme akışını kullanır.
- **Analiz:** takipçi, takip edilen, varsa gönderi sayısı; önceki gözleme göre net fark; 7/30 gün veya tüm kayıtlar; zaman eksenli grafik; son 10 gözlemin metin dökümü; özel not ve grup. Eksik gönderi sayısı sıfır yapılmaz. Geçmiş ilk gözlemden başlar; gelen/giden kişilerin kimliği çıkarılamaz.
- **Listeler:** üyeye dokunmak uygulama içindeki profilini açar; Instagram’a geçiş ayrı Instagram’da aç düğmesidir. Arama sonucunda doğru üye açılır, kişi otomatik eklenmez. Taze profil kaydı varsa kullanılır; eski veya kayıtsız profilde mevcut aralık/mola izin verirse ilk açılışta tek profil okuması yapılabilir. Kendi hesabının ve kayıtlı kişilerin listeleri ayrı saklanır. Açılış/sekme değişimi istek göndermez. Listeyi güncelle aktif yöndeki devam sayfalarını kontrollü alır; tek sayfa/baştan okuma Liste işlemleri menüsündedir; otomasyon açıksa sayfalar ortak kuyrukta ilerler. Liste ekranında Kaydın kapsamı → Önceki okumayla farklar, iki uygun okuma arasındaki yeni görülen/görünmeyen hesapları gösterir. Eksik listeden kesin takipten çıkış sonucu çıkarılmaz.
- **Bildirim:** kişi analizindeki Takip durumu kartında izlemeyi aç; en az 15/30 dakika veya 1/6/12/24 saat aralığı seç. Takipçi/takip edilen sayıları, kullanıcı adı, yanıtta varsa biyografi ve karşılaştırılabilir fotoğraf bilgisindeki değişimler için yerel Android bildirimi gelir. İlk kayıt ve yalnız gönderi değişimi bildirim üretmez. Uygulama ekranı kapalıyken de JobScheduler çalışabilir; anlık bildirim değildir. Pil tasarrufu, Doze, üretici ayarları ve çok sayıda izlenen kişi gecikme yaratır. Zorla durdurma sonrası uygulamayı yeniden açmak gerekir.
- Bu önizlemede cihaz kaydını sınırlamak için en fazla **20 kişi**, kişi başına son **90 başarılı gözlem**, 500 karakter not ve 40 karakter grup saklanır. Bunlar Instagram'ın güvenli istek limitleri değildir.
- Tek kişiyi kaldırma o kişinin geçmiş ve notunu siler. **Bağlantıyı kaldır** bu uygulamadaki oturumu ve bütün kişileri siler; diğer Instagram oturumlarını kapatmaz.

- **Takip durumu:** son başarılı profil okuması, seçilen aralık, sıradaki kontrol için en erken hedef ve bekleme nedeni. İzin, bağlantı, Instagram molası, ortak bütçe ve kişi sırası ayrı gösterilir. **Durumu güncelle** yalnız cihaz kaydını/sistem durumunu okur ve mevcut işi eşler; Instagram isteği göndermez. Ekran kendiliğinden canlı güncellenmez. [Kontrol ve bildirim testleri](docs/WATCH-STATUS-TEST.md).

## Liste otomasyonu

Özet → kısa izleme kartındaki **⋯ → Otomasyonu ayarla**. Kendi hesabını ve izlenecek kayıtlı kişileri seçip **Başlat** de. Bir kez açıldıktan sonra İlk sayfa / Sonraki sayfa düğmelerine basmak gerekmez. Mobil bağlantı ve açık bildirim izni gerekir. Eski sürüm güncellemesinde varsayılan kapalıdır; mevcut kişi profil ayarları korunur.

- Kendi hesabın seçiliyse profili yaklaşık saatte bir kontrol için sıraya girer; seçili kişilerin profil bildirimleri mevcut aralıklarıyla açılır.
- Takipçi/takip edilen sayfaları sırayla okunur; cursor ve rezervasyon cihazda kalır. Normal uygulama kapanışı sonrası sonraki iş kaldığı yerden devam edebilir. Zorla durdurma sonrası Android uygulamanın yeniden açılmasını gerektirebilir.
- Profil ve liste turları ortak en az 15 dakika aralığıyla başlar; ikisi de hazırsa dönüşümlüdür. Bir liste turunda aynı hedefin iki yönü en fazla 6 sayfa ilerler; istek başlangıçları arasında en az 15 saniye vardır. İlk 90 saniyeden sonra yeni istek başlatılmaz; devam eden istek kendi zaman aşımını kullanır. Büyük/çok listelerde tarama saatler veya günler sürebilir; 15 dakika her kişinin tam listesinin yenilenmesi değildir.
- Liste sonu gözlendikten sonra yeni tarama 24 saat sonrasına hedeflenir. İlk okuma başlangıçtır; iki ayrı okumada da liste sonu gözlendiyse fark bildirimi üretilebilir. Bu, Instagram sonuçlarının eksiksiz/aynı anda alındığını kanıtlamaz. Bildirimler kesin takip/takipten çıkış iddiası değildir.
- **Otomasyon durumu** ilerlemeyi/hataları gösterir; satırdan kayıtlı liste açılır. **Yenile** yalnız yerel durumu günceller. **Nasıl çalışır?** molayı/kapsamı açıklar ve onaylı liste temizliğini içerir. Liste otomasyonunu durdurmak kişilerin ayrı profil bildirimlerini kapatmaz.
- Önceki/güncel liste okumaları en fazla 21 hedef için aynı şifreli 512 KiB bütçeyi paylaşır. Dolarsa önceki kayıt korunur; kaydedilemeyen yönde tarama duraklar. Gerekirse onaylı **Liste kayıtlarını temizle** ile başlangıçları kaldırabilirsin; bu işlem Instagram 429 molasını sıfırlamaz.

[Test adımları ve sınırlar](docs/AUTOMATION-TEST.md).

## Bağlantı seçenekleri

### Windows'taki mobil bağlantıyı APK'ya taşıma

**Hesap bağla → Bilgisayardan bağlan**, Windows prototipinde kurulmuş oturumu Android'e taşır. Bağlantı yönetimi, gizlilik, yenileme tercihleri ve istek bilgileri sağ üstteki **⋯** menüsündedir. İlk eşleştirme bilgisayar/ADB gerektirir; başarılı okumadan sonra APK bağımsız çalışır. Bu sürüm Android içinde yeni mobil parola/CAA girişi uygulamaz; WebView girişi ayrı seçenek olarak kalır.

1. APK'da **Bağlantıyı hazırla**. Cihaza özel anahtar ve 10 dakikalık aktarım isteği oluşturulur.
2. Aynı Windows kullanıcısında `scripts/link-desktop.ps1` çalıştır. Önceden çalışan `InstagramCounter` ortamı ve DPAPI oturumu gerekir. Script oturumu yalnız bellekte açar, bu cihaza özel anahtarla şifreler ve ADB'den aktarır. Giriş/profil isteği, sunucu, parola/kod kopyalama yoktur. Birden fazla cihaz varsa `-Serial`; debug paket için `-DebugApp` kullanılır.
3. APK'da **Aktarımı al**. Bu adım Instagram'a bağlanmaz ve mevcut hesabı değiştirmez. Aktarım dosyası ve tek kullanımlık anahtar temizlenir; oturum Keystore ile şifreli kasaya alınır.
4. Yerel mola dolunca **Bağlan**. Yeni oturumdan kimliği doğrulanmış başarılı yanıt gelirse ana hesap bağlanır; aynı hesabın kişi/geçmiş kayıtları korunur. Özet, kişiler ve arka plan kontrolleri bu oturumu kullanır. İlk bağlantı denemeleri kalıcı en az 15 dakika aralıklıdır.

Aktarım ekranı açıkken otomatik kontroller bekler. Süreç kapanırsa bu ekranı yeniden açıp **Geri** ile bekletmeyi kaldır. Aktarım manuel moda alır; başarısız deneme sonrası eski bağlantıdan gizli yenileme yapılmaz. Başarılı açık okuma normal önbellekli akışı yeniden açar. [Sözleşme ve sınırlar](docs/MOBILE-PORT.md).

### Önceki web deneyi

**⋯ → Gelişmiş → Web bağlantı deneyi**, eski 429 kaydını koruyarak farklı bir erişim hipotezini sınar: giriş ve tek web profil okuması aynı WebView oturumunda kalır. Giriş/doğrulama sonrası kullanıcı adını girip **Tek okumayı başlat** seçilir. Sayfa açma ve profil okuma otomatik başlamaz; her deneme tek açık okuma ve en az 15 dakika aralığıyla sınırlıdır. Sonuç ana hesaba/izlenen kişilere aktarılmaz ve arka plan bildirimlerine bağlanmaz. Çalışacağı veya 429'u kaldıracağı garanti edilmez.

Prototip açıkken otomatik kontroller duraklar. **Denemeyi bitir** tarayıcı verilerini temizleyip bu bekletmeyi kaldırır; önceki 429'u kaldırmaz. Süreç kapanıp bekletme kalırsa prototipi yeniden açıp bitir. Debug demoda **Bağlantı prototipi · çevrimdışı** aynı JS/WebView akışını sentetik yanıtta sınar. [REA bulguları, yöntem ve sınırlar](docs/CONNECTION-PROTOTYPE.md).

## İstekler ve sınırlama

Bütün profil okumaları aynı kalıcı en az 15 saniye aralığını ve tek etkin bağlantıyı kullanır. Kayıtlı aynı profilin 15 dakika içindeki başarılı gözlemi yeni istek göndermeden kullanılır. Bu süreler Instagram güvenli kota garantisi değildir. [İstek politikası, 429 dayanakları ve test sınırı](docs/REQUEST-POLICY.md). Otomatik okumalar ortak 15 dakikalık bütçeyi paylaşır; her arka plan çalışmasında sıradaki bir profil veya aynı hedefin en fazla 6 liste sayfası sorgulanır (en az 15 saniye aralıklı, 90 saniyelik yeni istek başlatma bütçesi). 20 kişi açıkken herkesin saatte bir kontrol edilmesi beklenmemelidir. Kişi bazındaki süre başarısız istekten önce de ayrılır. Sınırsız tarama, farklı uçlara tekrar deneme veya otomatik yeniden giriş yoktur. İlk hata veya sınırlı/belirsiz sayfa liste turunu durdurur. Düğmeler istek sırasında kapanır; ekranın eski isteği iptal edilirse geç yanıt kaydedilmez.

Bildirim izni/kanalı kapalıysa arka plan sorgusu yapılmaz. Oturum veya doğrulama hatasında otomatik çalışma yeniden bağlantıya kadar durur. İzlemeyi kapatmak, kişiyi silmek veya bağlantıyı kaldırmak ilgili işi/bildirimi iptal eder. Bildirim ayrıntıları kilit ekranında gizli olarak işaretlenir; son görünüm Android ve kullanıcının sistem ayarlarına bağlıdır. Bildirim yalnız iki gözlem arasındaki net farkı gösterir; aradaki bütün hareketleri göstermez.

Instagram 429 veya tanınan bekleme yanıtı verirse otomatik okuma kalıcı olarak durur. İlk yerel mola en az 1 saat; tekrarlarında 2/4/8/16/24 saate çıkar. Sunucunun Retry-After değeri daha uzunsa korunur. Bu saat Instagram kısıtlamasının kalkış saati değildir. Mola bitince kendiliğinden deneme yapılmaz; başarılı bir açık kullanıcı yenilemesi normal önbellekli akışı açar. Önceki sürümde kaydedilen sınırlama da otomatik okumayı durdurur.

Ekran kaydedilmiş son sınırlama yanıtının zamanını ve yerel molayı ayırır. Eski uyarının görünmesi yeni bir Instagram isteğinin reddedildiğini tek başına göstermez. Gerçek sınırlamanın nedeni ve süresi bu uygulamadan kesin belirlenemez.

## Bilgisayarda aynı Android kodunu deneme

Önceden kurulu Android SDK/emülatör ve JDK 21 gerekir. Bu iş akışı yazılım kurmaz. Telefon sürümüyle aynı ana Kotlin ekranları, parser, istek kapısı ve şifreli kayıt kullanılır. Debug sürümündeki belirgin **DEMO · ÖRNEK VERİ** modu yalnız sentetik yanıt döndürür; gerçek hesaba bağlanmaz.

```powershell
.\gradlew.bat :app:assembleDebug
.\scripts\start-preview.ps1
```

Bu çalışma alanındaki TrackerQA AVD'si kullanılır. Başka bilgisayarda mevcut AVD'yi `-AvdName` ile seç. Emülatör zaten başsız çalışıyorsa görünür pencere için önce onu kapatıp script'i yeniden çalıştır. Debug sürümünde **⋯ → Çevrimdışı demoyu aç** da bulunur. Örnek iki kişinin geçmişi vardır; yeni kişi için `@efe.demo`, bulunamama senaryosu için `@bulunamadi.demo` denenebilir. Gerçek kullanıcı adı girilse de demo gerçek profil sorgulamaz. Demo bildirimleri de ayrı debug servisiyle sentetik taşıma kullanır; bildirimde DEMO etiketi bulunur.

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
