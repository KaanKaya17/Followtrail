# Sürüm ve Destek

[Kılavuz ana sayfası](Home.md) · [Değişiklik kaydı](../../CHANGELOG.md)

**Son içerik denetimi: 15 Kasım 2026.** Bu sayfa, uygulanmış özelliklerle canlı ortamda doğrulanmış kapsamı ayırır.

## Windows 0.2.89 yerel giriş testi

15 Kasım’da 0.2.89 yerel test paketinde parolalı giriş ve profil doğrulaması tamamlandı. Sonraki denemede ikinci hesap kaydedildi ve ilk liste isteği HTTP 200 döndürdü. İki hesap da bağlı kaldı.

Eski parola anahtarı adresi parolasız kontrolde HTTP 404 döndürdü. instagrapi 3.2.3’ün kullandığı yeni adres, aynı bilgisayarda HTTP 200 ile geçerli bir anahtar verdi. Bu değişiklik Windows paketine aittir. [Kütüphane düzeltmesi](https://github.com/subzeroid/instagrapi/pull/2923)

0.2.89 yerel test paketidir. Genel indirme durumu aşağıdaki bölümde takip edilir.

## Windows 0.2.84 önizlemesi ve Android 0.1.100 adayı

Windows 0.2.84 herkese açık önizleme olarak yayımlandı. Android 0.1.100 (sürüm kodu 101) test adayıdır. [İndirmeler](#kurulum-dosyasını-nereden-alabilirim).

- Windows 0.2.84 yeniden paketlendi. 45 çevrimdışı paket kontrolü geçti. Windows EXE’si Authenticode imzası taşımıyor.
- Android 0.1.100 için 195 birim testi, release derlemesi, manifest ve imza kontrolü geçti. Lint 0 hata ve 71 uyarı bildirdi. APK’nın imza sertifikası GitHub’daki 0.1.92 APK’sıyla eşleşiyor. Fiziksel telefonda Instagram oturumu oluştu, ardından profil okuması HTTP 429 aldı. İlk listelere kadar yeni bağlantı henüz doğrulanmadı.

Taşınabilir geçmiş, aynı parolalı dosyayı Windows ve Android’de açar. Windows’ta kaydedilebilen 2.000 karakterlik notlar Türkçe karakterler ve emojiyle birlikte taşınabilir. [Yedekleme ve cihaz değiştirme](Gizlilik-ve-Veriler.md).

Geri takip etmeyenler için istisnalar, ZIP kapsamı, iki ZIP önizlemesi ve içerik takvimi her iki yerel pakette vardır. [Ortak özellikler ve platform farkları](../PLATFORM-PARITY.md).

## Windows 0.2.75 ve Android 0.1.96 yerel paketleri

Özet’te **Instagram ZIP’ini aç** ile dosya seçildiğinde arşiv özeti ve veri kategorileri açılır. **İki ZIP karşılaştır**, iki dosyada bulunan takip listelerini kullanıcı adına göre karşılaştırır. [İlk kullanım](Ilk-Kullanim.md).

## Windows 0.2.73 ve Android 0.1.94 adayı

- Windows: Özet kartları, aylık beğeni grafiği, arşivde arama ve iki ZIP’in takip listelerini karşılaştırma. 40 paketli kontrol geçti. Tam boyutlu bir Instagram JSON ZIP’i yerelde okundu. [Arşiv rehberi](Takip-Listeleri.md#instagram-zip-arşivi).
- Android: 0.1.94 (sürüm kodu 95) tam ZIP için kategori, dosya arama, JSON gezinme ve desteklenen yerel medya önizlemesi sunar. Birim testleri, release derlemesi ve imza denetlendi. Önceki GitHub cihaz testlerinde hatalar var; 0.1.94 için yeni cihaz doğrulaması tamamlanmadığından yayımlanan APK 0.1.92 olarak kalıyor.

## Windows 0.2.65 ve Android 0.1.92

HTML biçimindeki Instagram ZIP arşivleri de açılabilir.

## Windows 0.2.64 ve Android 0.1.91

JSON biçimindeki Instagram ZIP dosyasından takipçi ve takip edilen listeleri cihazda açılabilir. [ZIP kullanımı](Takip-Listeleri.md#instagram-zip-arşivi).

## Windows 0.2.63 ve Android 0.1.90

Profil ayrıntılarında **Bu hesap hakkında** bölümü açılır. Instagram’da görünen katılım ayı/yılı, hesap ülkesi ve kullanıcı adı değişiklik sayısı elle yerel kayda eklenebilir.

## Windows 0.2.62 ve Android 0.1.89

Profil zaman tünelinde iki kayıt seçilerek yerel fotoğraf, profil bilgileri ve sayaçlar yan yana görülebilir. Biyografide çıkarılan ve eklenen metinler ayrı sütunlarda işaretlenir.

## Windows 0.2.61 ve Android 0.1.88

Ortak hesaplarda kayıtlı fotoğraflar ve eldeki takipçi/takip edilen sayıları görünür. Sayısı bilinmeyen hesaplarda sayaç satırı gizlenir.

## Windows 0.2.60

İstek bilgileri profil ve iki liste yönünün okuma tanısını gösterir. Aynı uyumluluk hatası üç kez görülürse ilgili otomatik okuma durur. Başarılı bir elle kontrol duraklatmayı kaldırır.

## Windows 0.2.59

İki profilin kayıtlı takipçi veya takip edilen listelerindeki ortak hesaplar görülebilir. Her iki kaynağın tarihi ve kapsamı gösterilir. Karşılaştırma çevrimdışı çalışır.

## Windows 0.2.58

Gizli profillerde liste erişimi açıklaması eklendi. Mesaj bağlı hesabın son tam takip edilen kaydına dayanır. Liste sayısı son profil sayacıyla uyuşmuyorsa takip durumu belirsiz kalır.

## Windows 0.2.57 doğrulaması

Profil ilk kez kaydedilince iki liste için hazırlık kaydı açılır. Profil ve liste otomasyonu açıkken izleme tercihi kapalı olsa da ilk listeler hazırlanabilir. 112 seçili Python testi, 36 paketli Windows kontrolü, kaynak ve dört dil denetimi geçti. Canlı Instagram testi yapılmadı.

## 0.2.56 / 0.1.86 doğrulaması

Windows 0.2.56 / Android 0.1.86 için 160 seçili Python, 183 JVM, 6 profil paneli JavaScript testi ve 36 paketli Windows kontrolü geçti. Android lint 0 hata / 56 uyarı verdi; imza ve manifest denetlendi. Yeni Android cihaz testi çalıştırılmadı. Bunlar bütün testlerin bu sürümde yeniden çalıştırıldığı veya canlı Android girişinin çözüldüğü anlamına gelmez. [Ayrıntılar ve APK özeti](../RESPONSE-SHARING-TEST.md).

## Windows

**GitHub’da belgelenen önizleme: 0.2.73 · kaynak etiketi: `desktop-v0.2.73`**

- Taşınabilir Windows x64 dağıtımıdır.
- Dağıtım klasörü: `InstagramTracker-Desktop-0.2.73`; içindeki `InstagramTracker.exe` çalıştırılır.
- Hesap/profil okuma ve kendi hesabının iki liste yönünde gerçek yanıtlar görülmüştür. Bu canlı örnekler bütün hesapları veya her büyüklükteki listeyi kapsamaz.
- Son Threads düzeltmesinde, bildirilen profilin gerçek Instagram rozeti okunup bağlantı kayda alınmıştır. Ayrı Threads sorgusu yapılmamıştır.
- Windows doğrulama ve kaynak ölçümü kapsamı [0.2.47 optimizasyon notunda](../DESKTOP-RESOURCE-OPTIMIZATION.md) bulunur. 0.2.48 saklama/silme güncellemesi ve sınırları [veri yaşam döngüsünde](../DATA-LIFECYCLE.md) bulunur.

0.2.49, Windows kısayolunda ve görev çubuğunda Tracker ICO simgesini açıkça kullanır; uygulamaya ait eski kısayolları yedekleyerek kaldırır.

## Android

**GitHub’da belgelenen APK: 0.1.92 · sürüm kodu: 93 · kaynak etiketi: `android-v0.1.92`**

- Dosya: `InstagramTracker-0.1.92.apk`; Android 7.0 / API 24 ve üzeri hedeflenir.
- Android 0.1.87 yeni kayıtlar için ilk liste hazırlığını, gizli hesap açıklamasını, ortak hesap karşılaştırmasını ve okuma tanısını içerir. [Kullanım ve doğrulama kapsamı](../PLATFORM-PARITY.md).
- 11 Kasım 0.1.86 cihaz testinde mevcut oturumla profil ve iki liste yenilendi. Kullanıcı liste farkı bildirimi aldığını doğruladı. Bekleyen web oturumuyla ayrı profil isteği HTTP 429 aldı.
- Kod sonrası girişten profil kontrolüne geçildiği ve profil 429 aldığında oturumun şifreli devam ekranında korunduğu gerçek telefonda görülmüştür.
- **Telefon içinden uçtan uca başarılı hesap bağlantısı ve liste okuması henüz tamamlanmış sayılmıyor.** Araştırılan açık konu girişten sonraki profil okumasıdır.
- E-posta/kod akışı üzerinde çalışılmıştır; her CAPTCHA, TOTP ve başka cihazdan onay yönteminin desteklendiği varsayılmamalıdır. Cihaz onayı sonucunu periyodik sorgulayan yöntem hâlâ sınırlıdır.
- Yeni günlük geçmiş ekranı ve Keystore dosyaları için altı cihaz testi derlendi; cihazda çalıştırılmadı.
- 0.1.76: profil/özet/liste sahibi ve yerel Bildirimler/Zaman Çizelgesi görünümü güncellendi. 142 JVM testi, release derlemesi, lint ve imzalı paket kontrolü geçti; emülatör ekran testi tamamlanmadı. [Doğrulama ve kalan kapsam](../ANDROID-UI-ALIGNMENT.md).

0.1.81: Android web okuyucusunun uygulama kimliği, resmî masaüstü giriş yapılandırmasıyla eşleştirildi. [Gözlem, değişiklik ve canlı test sınırı](../ANDROID-WEB-APP-ID.md).

0.1.79 / Windows 0.2.53: arka planda hesap kontrolü sırasında arama/listenin fotoğrafları korunur; sınırlanan Android listesi açık bir yeniden deneme bekler. [Hata düzeltmeleri ve kapsam](../BUG-CHECK-2026-11-04.md).

0.1.78 / Windows 0.2.50: profil aramasında pencereyi yeniden açınca aynı hesabın kısa süreli sonuçları kullanılır. [Ortak davranış ve test kapsamı](../SEARCH-CACHE-PARITY.md).

## Web

Örnek verilerle arayüz önizlemesidir. Gerçek hesaba bağlanma, sürekli arka plan kontrolü ve cihazlar arasında veri eşitleme hizmeti değildir.

## Günlük istatistikler

Windows ve Android için ayrı şifreli günlük arşiv eklendi. Varsayılan günlük özetleri kullanıcı silene kadar korur; yeni Veri saklama bölümünde süre seçilebilir. [Kullanım ve sınırlar](Gunluk-Istatistikler.md).

## Dil ve görünüm

Türkçe, English, Español ve Português (Brasil) paketleri bulunur. Sistem, açık, koyu ve OLED görünüm seçenekleri vardır. Paketlerdeki çeviriler kullanıcı adı, biyografi ve özel not gibi kullanıcı içeriklerini çevirmez.

## Kurulum dosyasını nereden alabilirim?

- [Windows 0.2.84 herkese açık ZIP](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.84)
- [Windows 0.2.73 eski önizlemesi](https://github.com/KaanKaya17/InstagramTracker/releases/tag/desktop-v0.2.73)
- [Android 0.1.92 imzalı APK](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.92)

[Tüm önizlemeler](https://github.com/KaanKaya17/InstagramTracker/releases) ana deponun Releases bölümünde listelenir. Windows 0.2.84 sürümünde ayrıca SHA-256 dosyası yer alır.

<a name="destek-icin-hangi-bilgiler-gerekir"></a>

## Destek için hangi bilgiler gerekir?

Sorunu yeniden oluşturacak kısa adımlarla şu bilgileri iletin:

```text
Platform ve sürüm:
Beklenen sonuç:
Görülen sonuç:
Son deneme zamanı:
İstek bilgileri / varsa P adımı ve HTTP durumu:
Görünen yerel bekleme zamanı:
Sorunun başladığı işlem:
```

Parola, doğrulama/kurtarma kodu, çerez, oturum yedeği, anahtar veya gerçek ham API yanıtı eklemeyin. Kullanıcı adı, not, biyografi ve görselleri paylaşmadan önce gözden geçirin; mümkünse sentetik bir örnek kullanın.

Normal hata bildirimi için proje sahibinin belirttiği destek kanalını kullanın. Güvenlik konusu için [özel bildirim politikasını](../../SECURITY.md) izleyin. Bu kılavuz, varlığı doğrulanmamış bir e-posta adresi veya destek hizmeti tanımlamaz.

## Sonraya bırakılan geliştirmeler

Yerel indirme klasörünü otomatik izleme, işlevsel web hizmeti, geniş Threads/Facebook entegrasyonu ve AI içerik analizi sonraya bırakıldı. [Geliştirme notları](../DEVELOPMENT-BACKLOG.md) güncel kapsamı korur; listedeki bir fikir yayın tarihi taahhüdü değildir.

## Son verimlilik güncellemeleri

0.2.54 / 0.1.84 ile otomatik profil işleri yerel bütçenin %80 eşiğinde ertelenir. 0.2.55 / 0.1.85 günlük sınırları hesap için 3.000, cihaz için 6.000 yaptı; saatlik sınırlar 120 / 240 olarak kaldı. 0.2.56 / 0.1.86 doğrulanmış profil bilgisini ve eşzamanlı görsel işlerini yeniden kullanır. [Ortak politika](../REQUEST-POLICY.md) · [Yanıt paylaşımı](../RESPONSE-SHARING-TEST.md).

## GitHub ve dağıtım durumu

Kılavuz bu deponun `docs/wiki/` dizinindedir. Windows ve Android önizlemeleri kendi sürüm etiketlerine bağlı GitHub Releases sayfalarında dağıtılır.

## Son eşitleme

[Android / Windows eşitlemesi ve test sınırları](../PLATFORM-PARITY.md): ortak istek politikası ve Android hikâye arşivi eklendi. Android cihaz/ekran ve yeni canlı giriş kontrolü ertelendi; eksiksiz platform eşitliği veya çözülmüş giriş iddiası yoktur.

Android 0.1.82 CAPTCHA içerik sorgusu ve tanı kopyalama düzeltmesi: [kanıt/test kapsamı](../ANDROID-CAPTCHA-CONTENT.md). Girişten profil okumaya canlı doğrulama henüz tamamlanmadı.

Android 0.1.83, CAPTCHA’nın gerçek içerik şemasını ve gözlenen sonuç gönderme adımlarını ekler; [canlı kanıt ve bekleyen doğrulama](../ANDROID-CAPTCHA-CONTENT.md).

0.1.83 fiziksel testinde CAPTCHA sonrasında şifreli bekleyen Instagram oturumu oluştu; sonraki profil okuması HTTP 429 aldı. Tracker profil/liste bağlantısı henüz tamamlanmadı. [Son canlı sonuç](../ANDROID-CAPTCHA-CONTENT.md).
