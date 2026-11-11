# InstagramTracker

**Instagram çevrenizi tek bir yerden takip edin.**

Takipçi ve takip edilen listelerini karşılaştırın, seçtiğiniz profillerdeki değişiklikleri inceleyin ve gözlemlerinizi zaman çizelgesinde görün. Kayıtlarınız cihazınızda şifreli saklanır.

[Kullanım kılavuzu](docs/wiki/Home.md) · [Kurulum](docs/wiki/Kurulum.md) · [Sürüm durumu](docs/wiki/Surum-ve-Destek.md) · [Sorun giderme](docs/wiki/Sorun-Giderme.md)

![Örnek verilerle InstagramTracker Windows Özet ekranı](docs/wiki/assets/ozet.png)

*Ekran örneği sentetik verilerle oluşturulmuştur; gerçek bir hesabı temsil etmez.*

## Neler sunar?

- Takipçi/takip edilen kayıtları ve uygun tam listeler arasında üye karşılaştırması. Windows'ta kaydırdıkça yüklenen listeler ve bütün yerel listede arama.
- Windows’ta kayıtlı listelerden ortak takipçileri ve ortak takip edilenleri karşılaştırma.
- Kaydedilen profillerde arama, favoriler, notlar ve izleme.
- Sayaç, kullanıcı adı, biyografi ve avatar değişikliklerinin geçmişi.
- [İsteğe bağlı hikâye arşivi](docs/wiki/Hikaye-Arsivi.md): yerel takvim ve kaydedilmiş fotoğraf/video görüntüleme.
- [Kalıcı günlük istatistikler](docs/wiki/Gunluk-Istatistikler.md): şifreli yıllık arşiv ve hafta/ay/yıl grafikleri.
- Windows'ta kontrol başına gruplanan değişiklik kayıtları ve bildirim tercihleri.
- Birbirinden ayrı şifreli kayıtlarla çoklu hesap altyapısı.
- Türkçe, English, Español ve Português (Brasil); açık, koyu, OLED ve sistem görünümü.

İlk liste başlangıç kaydıdır. Uygulama geçmişte hiç kaydetmediği takipten çıkışları, profil ziyaretçilerini veya kesin olay saatini üretemez. [Sık sorulan sorular](docs/wiki/Sik-Sorulan-Sorular.md).

## Güncel önizleme

**Windows 0.2.61:** Ortak hesaplar, kayıtlı fotoğrafları ve eldeki profil sayaçlarını gösterir. Yeni kaydedilen profillerin ilk takipçi ve takip edilen listeleri otomatik hazırlık sırasına alınır. Gizli bir hesabı takip etmediğin son tam listende görülüyorsa liste ekranı bunu açıklar. [Sürüm ve doğrulama durumu](docs/wiki/Surum-ve-Destek.md).

**Android 0.1.88 / kod 89:** İmzalı APK. Ortak hesaplarda kayıtlı fotoğraflar ve eldeki profil sayaçları gösterilir. İlk liste hazırlığı, gizli hesap açıklaması, ortak hesap karşılaştırması ve okuma tanısı eklendi. Android 0.1.86 fiziksel testinde mevcut oturumla profil ve iki liste okundu. Bekleyen web oturumunu bağlama adımı HTTP 429 aldı. [Platform kapsamı ve doğrulama](docs/PLATFORM-PARITY.md).

**Web:** Sentetik verili arayüz önizlemesi; gerçek hesabı sürekli kontrol eden bir web hizmeti değildir.

Paket adları, doğrulama kapsamı ve açık işler [sürüm sayfasında](docs/wiki/Surum-ve-Destek.md) açıklanır. APK/EXE'ler kaynak depoya eklenmez; bir kaynak etiketi tek başına indirilebilir paket anlamına gelmez.

Ortak kontrol politikası, Android hikâye arşivi ve kalan platform farkları: [Android / Windows eşitlemesi](docs/PLATFORM-PARITY.md).

## Gizlilik

Her iki platformda hesap başına veri saklama süresi ve profil arşivini silme kontrolleri bulunur. [Kapsam, veri envanteri ve açık işler](docs/DATA-LIFECYCLE.md).

Windows'ta DPAPI, Android'de Keystore/AES-GCM ile yerel şifreleme kullanılır. Uygulamanın kendi veri sunucusu, reklam veya analitik SDK'sı yoktur. Canlı istekler Instagram/Meta'ya gider; isteğe bağlı güvenlik sayfaları ve açtığınız bağlantılar ayrıca ağ erişimi gerektirir.

Takip etme, takipten çıkma, beğeni ve DM gönderme özelliği sunulmaz. Giriş/doğrulama işlemleri ayrı dar izinlerle yürür. [Kullanıcı gizlilik rehberi](docs/wiki/Gizlilik-ve-Veriler.md) · [Teknik notlar](docs/PRIVACY.md) · [Güvenlik bildirimi](SECURITY.md).

## Geliştiriciler için

Android: JDK 21, Android SDK platform 36 ve build-tools 36.0.0. Kilitli bağımlılıklar ve Gradle wrapper kullanılır.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Windows'ta `gradlew.bat` kullanın. Masaüstü geliştirme/paketleme adımları [Windows rehberinde](interface/DESKTOP.md), genel kurallar [CONTRIBUTING.md](CONTRIBUTING.md) dosyasındadır. [Teknik dokümantasyon dizini](docs/README.md).

Kılavuz bağlantılarını ve Wiki hazırlığını denetlemek için:

```sh
python scripts/prepare-wiki.py --check
```

Kullanıcı kılavuzu şu anda bu özel depoda tutulur. [Wiki'ye taşıma](docs/wiki/Wikiye-Tasima.md) ayrı bir yayın adımıdır; depo görünürlüğü otomatik değiştirilmez.

[Değişiklik kaydı](CHANGELOG.md) · [Önceki README ve sürüm tarihçesi](docs/history/README-2026-11-02.md)

*InstagramTracker, Instagram ve Meta'dan bağımsız bir uygulamadır.*
