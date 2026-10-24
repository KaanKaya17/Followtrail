# InstagramTracker

Yerel Android uygulaması. İlk sürüm yalnız kendi hesabının takipçi ve takip edilen sayılarını gösterir.

Geliştirme sürümü 0.1.1. Android 13 telefona 0.1.0 kuruldu ve giriş denendi. Kullanıcı sayı ekranına ulaşmadan Instagram sınırlaması gördü; canlı Android sayaç okuması ve otomatik yeniden açılış henüz doğrulanmadı. 0.1.1 tekrarlı istekleri engeller. [Doğrulama](docs/VALIDATION.md).

## Temel kararlar
- Native Kotlin/Android arayüzü; Python, sunucu, reklam, analitik veya çökme toplama servisi yok.
- Android 7.0+ (API 24), işlemciye özel native kütüphane yok. Minimum sürüm desteği test kapsamıyla aynı şey değildir.
- İlk bağlantı uygulama içindeki Instagram web giriş sayfasında. Parola uygulama kodunca okunmaz. Sonraki açılışlarda oturumla API okuma hedeflenir.
- Oturum AES-GCM / Android Keystore ile cihazda şifrelenir. Yedekleme ve cihaz aktarımı kapalıdır.
- Ağ istekleri Instagram'a gider; kullanıcı verisinin hiç ağdan çıkmadığı iddia edilmez.

Bu ürün Meta/Instagram tarafından sunulmaz veya desteklenmez. Kullanılan kişisel hesap uçları resmî geliştirici API sözleşmesi değildir ve değişebilir.

## Kullanım

İlk açılışta **Instagram ile bağlan** üzerinden hesabına giriş yap. Giriş tamamlandığında uygulama oturum çerezleriyle kendi profilini bir kez okumayı dener. Otomatik geçiş olmadıysa **Giriş yaptım, bağlantıyı tamamla** düğmesi bir deneme başlatır; işlem sürerken düğme kapanır. Giriş/kod alanları Instagram sayfasınındır, sohbete bilgi gönderme.

Başarılı profil okumasından sonra oturum ve son sayılar şifreli saklanır. Sonraki açılış/ön plana gelişte bekleme süresi uygunsa otomatik yenilenir. Kayıtlı sayılar kendi gerçek gözlem zamanı ile gösterilir; hata yeni veya sıfır sayı üretmez. **Bağlantıyı kaldır** kayıtlı oturumu ve sayıları siler; diğer Instagram oturumlarını kapatmaz.

Sınırlama gelirse tekrar tekrar giriş yapma. Sunucu bekleme süresi bildirdiyse izlenir; bildirmediyse yerel 15 dakika mola uygulanır. Sürenin dolması kısıtlamanın kalktığı anlamına gelmez; süre dolunca otomatik istek gönderilmez. En az 15 saniye istek aralığı ve sınırlama molası uygulama yeniden başlatılsa veya bağlantı kaldırılsa da korunur. İlk 0.1.0 sürümü bu kaydı tutmadığından geçmiş kısıtlamayı 0.1.1 kendiliğinden bilemez.

## Derleme ve doğrulama

JDK 21, Android SDK 36 ve build-tools 36.0.0 kullanıldı. `ANDROID_HOME` veya izlenmeyen `local.properties` ile SDK yolunu tanımla.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Windows: `gradlew.bat`. Release imzalama için `scripts/sign-release.ps1`, `%LOCALAPPDATA%/InstagramTracker/signing/` altında yerel bir anahtar oluşturur; parola Windows kullanıcı DPAPI'siyle korunur. Anahtar veya parola repoya girmez. Aynı anahtar sonraki APK güncellemeleri için gerekir; kendi güvenli yedeğini tut. APK'lar izlenmeyen `artifacts/` klasörüne yazılır. CI imzasız release derler; burada CI geçti iddiası yerel testlerden çıkarılmaz.

## Tasarım ve gizlilik

[Canva arayüz taslağı](https://canva.link/g4rw1g4hoiufg4m) · [Gizlilik ve sınırlar](docs/PRIVACY.md) · [Sürümler](CHANGELOG.md)
