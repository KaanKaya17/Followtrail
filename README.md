# InstagramTracker

Yerel Android uygulaması: kendi hesabının özeti ve seçerek eklediğin kişilerin sayı/geçmiş analizi.

Geliştirme önizlemesi **0.1.3**. Gerçek Instagram hesabıyla Android sayaç okuması henüz doğrulanmadı; önceki telefon denemesi sınırlama ekranında kaldı. Çevrimdışı emülatör testleri canlı erişimin kanıtı değildir. [Doğrulama kaydı](docs/VALIDATION.md).

## Kullanım

İlk bağlantı uygulama içindeki Instagram HTTPS web giriş sayfasında yapılır. Uygulama parola alanını okumaz; API için gerekli oturum çerezlerini kullanır. Instagram giriş/doğrulama veya kısıtlama uygulayabilir. Kişisel hesap uçları resmî geliştirici API sözleşmesi değildir ve değişebilir.

- **Özet:** kendi hesabının son başarılı takipçi/takip edilen sayıları ve gözlem zamanı. Açılışta önce kayıt gösterilir. Otomatik yenileme için kayıt en az 15 dakika eski olmalı; otomatik denemeler de en az 15 dakika aralıklıdır. Arka plan taraması yoktur.
- **Kişiler:** kullanıcı adı veya HTTPS Instagram profil bağlantısıyla kişi ekle. İlk sorgu tek profil isteğidir. Sonraki sorgular sabit hesap kimliğine göre yapılır; kullanıcı adı değişmesi başka kişinin geçmişiyle karışmaz. Liste ve analiz açılışında otomatik sorgu yapılmaz.
- **Analiz:** takipçi, takip edilen, varsa gönderi sayısı; önceki gözleme göre net fark; 7/30 gün veya tüm kayıtlar; zaman eksenli grafik; son 10 gözlemin metin dökümü; özel not ve grup. Eksik gönderi sayısı sıfır yapılmaz. Geçmiş ilk gözlemden başlar; gelen/giden kişilerin kimliği çıkarılamaz.
- Bu önizlemede cihaz kaydını sınırlamak için en fazla **20 kişi**, kişi başına son **90 başarılı gözlem**, 500 karakter not ve 40 karakter grup saklanır. Bunlar Instagram'ın güvenli istek limitleri değildir.
- Tek kişiyi kaldırma o kişinin geçmiş ve notunu siler. **Bağlantıyı kaldır** bu uygulamadaki oturumu ve bütün kişileri siler; diğer Instagram oturumlarını kapatmaz.

## İstekler ve sınırlama

Bütün profil okumaları aynı kalıcı en az 15 saniye aralığını kullanır. Toplu takipçi taraması, otomatik kişi yenilemesi, farklı uçlara tekrar deneme veya otomatik yeniden giriş yoktur. Düğmeler istek sırasında kapanır; ekranın eski isteği iptal edilirse geç yanıt kaydedilmez.

Instagram 429 veya tanınan bekleme yanıtı verirse otomatik okuma kalıcı olarak durur. İlk yerel mola en az 1 saat; tekrarlarında 2/4/8/16/24 saate çıkar. Sunucunun Retry-After değeri daha uzunsa korunur. Bu saat Instagram kısıtlamasının kalkış saati değildir. Mola bitince kendiliğinden deneme yapılmaz; başarılı bir açık kullanıcı yenilemesi normal önbellekli akışı açar. Önceki sürümde kaydedilen sınırlama da otomatik okumayı durdurur.

Ekran kaydedilmiş son sınırlama yanıtının zamanını ve yerel molayı ayırır. Eski uyarının görünmesi yeni bir Instagram isteğinin reddedildiğini tek başına göstermez. Gerçek sınırlamanın nedeni ve süresi bu uygulamadan kesin belirlenemez.

## Bilgisayarda aynı Android kodunu deneme

Önceden kurulu Android SDK/emülatör ve JDK 21 gerekir. Bu iş akışı yazılım kurmaz. Telefon sürümüyle aynı ana Kotlin ekranları, parser, istek kapısı ve şifreli kayıt kullanılır. Debug sürümündeki belirgin **DEMO · ÖRNEK VERİ · AĞ İSTEĞİ YOK** modu yalnız sentetik yanıt döndürür; gerçek hesaba bağlanmaz.

```powershell
.\gradlew.bat :app:assembleDebug
.\scripts\start-preview.ps1
```

Bu çalışma alanındaki TrackerQA AVD'si kullanılır. Başka bilgisayarda mevcut AVD'yi `-AvdName` ile seç. Emülatör zaten başsız çalışıyorsa görünür pencere için önce onu kapatıp script'i yeniden çalıştır. Debug karşılama ekranında **Çevrimdışı demoyu aç** da bulunur. Örnek iki kişinin geçmişi vardır; yeni kişi için `@efe.demo`, bulunamama senaryosu için `@bulunamadi.demo` denenebilir. Gerçek kullanıcı adı girilse de demo gerçek profil sorgulamaz.

Release APK farklı paket kimliği kullanır, demo taşımasını içermez ve `offline_preview` parametresini yok sayar. Demo başarısı canlı Instagram başarısı değildir. Gerçek hesap denemesi istenirse demo dışında ayrıca giriş gerekir; telefondaki oturum bilgisayara taşınmaz.

## Derleme ve doğrulama

Android 7.0+ / API 24; compile/target 36. JDK 21 ve build-tools 36.0.0 kullanılır. SDK'yı `ANDROID_HOME` veya izlenmeyen `local.properties` ile tanımla.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Windows: `gradlew.bat`. [Yayın hazırlama](docs/RELEASING.md), [katkı](CONTRIBUTING.md), [CI](https://github.com/KaanKaya17/InstagramTracker/actions/workflows/android.yml). Gradle dağıtımı SHA-256, action sürümleri commit SHA, bağımlılıklar lockfile ile sabittir. Test uygulaması `.debug` paketinde üretim verisinden ayrıdır. CI testleri gerçek Instagram oturumu kullanmaz.

## Gizlilik

Oturum, kişiler, geçmiş ve notlar AES-GCM / Android Keystore ile bu cihazda şifrelenir. Bulut yedekleme ve cihaz aktarımı kapalıdır. Kendi sunucusu, reklam, analitik, çökme toplama SDK'sı ve işlemciye özel native kütüphane yoktur. Canlı giriş/profil istekleri Instagram'a gider; verinin hiç ağdan çıkmadığı iddia edilmez. [Gizlilik ve sınırlar](docs/PRIVACY.md), [güvenlik bildirimi](SECURITY.md).

[Canva ilk arayüz taslağı](https://canva.link/g4rw1g4hoiufg4m) · [Sürümler](CHANGELOG.md)
