# InstagramTracker

[Türkçe](README.md) · [English](README.en.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md)

**Instagram çevrenizi tek bir yerden takip edin.**

Takipçi ve takip edilen listelerini karşılaştırın, seçtiğiniz profillerdeki değişiklikleri inceleyin ve gözlemlerinizi zaman çizelgesinde görün. Kayıtlarınız cihazınızda şifreli saklanır.

[Kullanım kılavuzu](docs/wiki/Home.md) · [ZIP arşivi ve karşılaştırma](docs/wiki/Instagram-Veri-Arsivi.md) · [Kurulum](docs/wiki/Kurulum.md) · [Sürüm durumu](docs/wiki/Surum-ve-Destek.md)

![Örnek verilerle InstagramTracker Windows Özet ekranı](docs/wiki/assets/ozet.png)

*Windows arayüzü, örnek verilerle gösterilmiştir.*

## Amaç ve kullanım sınırları

InstagramTracker, Instagram arşivinizi incelemeyi ve erişim yetkiniz olan hesaplarda zaman içindeki değişiklikleri anlamayı kolaylaştırır. ZIP dosyaları, parolalar ve hesap kayıtları proje ekibine gönderilmez. Uygulama kendi sunucusuna kullanım verisi toplamaz. Canlı takip ve doğrulama için telefonunuz veya bilgisayarınız Instagram/Meta ile bağlantı kurar.

Tracker’ı yalnızca erişim yetkiniz olan verilerle, geçerli kurallara ve başkalarının gizliliğine saygı göstererek kullanın. Proje izinsiz hesap erişimini, tacizi ve yasa dışı kullanımı desteklemez.

## Hesap bağlamadan deneyin

İlk açılışta **Özet → Arşivini incele → Instagram ZIP’ini aç** yolunu izleyin ve Instagram’dan indirdiğiniz ZIP’i seçin. Açılan arşivde verilerinizi inceleyebilir veya aynı hesaba ait ikinci bir ZIP’le takip listelerini karşılaştırabilirsiniz. [ZIP’i alma, açma ve karşılaştırma rehberi](docs/wiki/Instagram-Veri-Arsivi.md).

![Windows ilk açılışındaki Arşivini incele kartı. Mor Instagram ZIP’ini aç düğmesi ve ZIP alma rehberi görünüyor.](docs/wiki/assets/hesap-baglamadan-zip-ac.png)

## Neler sunar?

- Takipçi/takip edilen kayıtları ve uygun tam listeler arasında üye karşılaştırması. Windows’ta takipten çıkıp geri gelenler, ayrılanlar ve kişinin listede ilk/son görüldüğü zaman aralığı; kaydırdıkça yüklenen listeler ve bütün yerel listede arama.
- Windows’ta kayıtlı listelerden ortak takipçileri ve ortak takip edilenleri karşılaştırma.
- Kaydedilen profillerde arama, favoriler, notlar ve izleme.
- Sayaç, kullanıcı adı, biyografi ve avatar değişikliklerinin geçmişi.
- [İsteğe bağlı hikâye arşivi](docs/wiki/Hikaye-Arsivi.md): yerel takvim ve kaydedilmiş fotoğraf/video görüntüleme.
- [Kalıcı günlük istatistikler](docs/wiki/Gunluk-Istatistikler.md): şifreli yıllık arşiv ve hafta/ay/yıl grafikleri.
- Windows'ta kontrol başına gruplanan değişiklik kayıtları ve bildirim tercihleri.
- Birbirinden ayrı şifreli kayıtlarla çoklu hesap altyapısı.
- Türkçe, English, Español ve Português (Brasil); açık, koyu, OLED ve sistem görünümü.

İlk liste başlangıç kaydıdır. Uygulama geçmişte hiç kaydetmediği takipten çıkışları, profil ziyaretçilerini veya kesin olay saatini üretemez. [Sık sorulan sorular](docs/wiki/Sik-Sorulan-Sorular.md).

## İndirmeler ve sürüm durumu

Windows 0.2.94 ve Android 0.1.103 önizlemesinde (sürüm kodu 104) hesap bağlamadan ZIP arşivi inceleme, iki ZIP karşılaştırma ve parolalı Tracker geçmişi dosyası açma kullanılabilir. [Platform kapsamı ve doğrulama](docs/PLATFORM-PARITY.md).

**GitHub’da indirilebilen önizlemeler:**

[Windows 0.2.94 herkese açık ZIP](https://github.com/KaanKaya17/InstagramTracker/releases/latest)

[Android 0.1.103 APK](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.103) · [Windows 0.2.93 önceki ZIP’i](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.93) · [Android 0.1.92 önceki APK’sı](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.92)

[Tüm Windows ve Android önizlemeleri](https://github.com/KaanKaya17/InstagramTracker/releases)

**Windows 0.2.94:** Instagram bir okuma yolunu değiştirirse Windows bildirimi gelir, eski kayıtlar korunur ve hesap verisi içermeyen bir rapor GitHub’da paylaşılabilir.

**Windows 0.2.93:** Güncel dışa aktarımlardaki yakın arkadaşlar, engellenenler, istekler ve son takipten çıkılanlar okunur (0.2.92’de 0 görünüyordu). İki gerçek tam dışa aktarımla doğrulandı.

**Windows 0.2.92:** Hesap bağlamadan ZIP incelemede bozuk satır artık bütün listeyi düşürmez; büyük tam dışa aktarımlar okunur. Son takipten çıktıkların, bekleyen istekler, yakın arkadaşlar ve engellenenler gösterilir. İki ZIP karşılaştırması takip tarihi, muhtemel ad değişikliği, arama ve CSV sunar. [ZIP inceleme](docs/ZIP-ARCHIVE-TEST.md).

**Windows 0.2.91:** Takip Listeleri’nde **Geri gelenler** ve **Ayrılanlar** görünümleri; kişinin listede ilk/son görüldüğü, çıktığı ve geri döndüğü tarama aralığı yerelde saklanır. Kişi panelinde **Takip geçmişi** bölümü vardır. Yeni Instagram isteği yoktur. [Takip geçmişi](docs/FOLLOW-HISTORY-TEST.md).

**Windows 0.2.90 / Android 0.1.103:** Hikâye fotoğraf ve videoları API sırasından ayrı, kendi medya şeridinde indirilir; hikâye kontrolü API bütçesinden yalnız 1 hak harcar ve daha kısa sürer. API aralığı ve bütçeler değişmedi. [Medya şeridi](docs/MEDIA-LANE-TEST.md).

**Windows 0.2.73:** Instagram Veri Arşivi, ZIP içindeki kayıtları kategorilere ayırır. Özet kartları ve aylık beğeni grafiği, tüm arşivde arama ve iki ZIP’teki takip listelerini karşılaştırma eklendi. Bildirimler menüsünde ve zil simgesinde okunmamış kayıt sayısı görünür. [Arşiv rehberi](docs/wiki/Takip-Listeleri.md#instagram-zip-arşivi).

**Android 0.1.103 / kod 104:** Yayımlanmış imzalı APK; tam ZIP arşiv tarayıcısı, iki ZIP karşılaştırma ve taşınabilir geçmiş içerir. Hikâye medyası ayrı şeritte indirilir; Android 7’de büyük şifreli kayıtlar doğru okunur. İmza 0.1.92 ile aynıdır, mevcut kurulumun üzerine güncellenebilir. Yeni Android hesap bağlantısının uçtan uca durumu [sürüm sayfasında](docs/wiki/Surum-ve-Destek.md) açıklanır.

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

Kullanıcı kılavuzunun kaynağı bu depodadır. [Wiki yayınlama adımları](docs/wiki/Wikiye-Tasima.md).

[Değişiklik kaydı](CHANGELOG.md) · [Önceki README ve sürüm tarihçesi](docs/history/README-2026-11-02.md)

*InstagramTracker, Instagram ve Meta'dan bağımsız bir uygulamadır.*

[MIT lisansı](LICENSE)
