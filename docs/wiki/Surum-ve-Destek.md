# Sürüm ve Destek

[Kılavuz ana sayfası](Home.md) · [Değişiklik kaydı](../../CHANGELOG.md)

**Son içerik denetimi: 11 Kasım 2026.** Bu sayfa, uygulanmış özelliklerle canlı ortamda doğrulanmış kapsamı ayırır.

## Windows 0.2.59

İki profilin kayıtlı takipçi veya takip edilen listelerindeki ortak hesaplar görülebilir. Her iki kaynağın tarihi ve kapsamı gösterilir. Karşılaştırma çevrimdışı çalışır.

## Windows 0.2.58

Gizli profillerde liste erişimi açıklaması eklendi. Mesaj bağlı hesabın son tam takip edilen kaydına dayanır. Liste sayısı son profil sayacıyla uyuşmuyorsa takip durumu belirsiz kalır.

## Windows 0.2.57 doğrulaması

Profil ilk kez kaydedilince iki liste için hazırlık kaydı açılır. Profil ve liste otomasyonu açıkken izleme tercihi kapalı olsa da ilk listeler hazırlanabilir. 112 seçili Python testi, 36 paketli Windows kontrolü, kaynak ve dört dil denetimi geçti. Canlı Instagram testi yapılmadı.

## 0.2.56 / 0.1.86 doğrulaması

Windows 0.2.56 / Android 0.1.86 için 160 seçili Python, 183 JVM, 6 profil paneli JavaScript testi ve 36 paketli Windows kontrolü geçti. Android lint 0 hata / 56 uyarı verdi; imza ve manifest denetlendi. Yeni Android cihaz testi çalıştırılmadı. Bunlar bütün testlerin bu sürümde yeniden çalıştırıldığı veya canlı Android girişinin çözüldüğü anlamına gelmez. [Ayrıntılar ve APK özeti](../RESPONSE-SHARING-TEST.md).

## Windows

**Güncel önizleme: 0.2.59 · kaynak etiketi: `desktop-v0.2.59`**

- Taşınabilir Windows x64 dağıtımıdır.
- Dağıtım klasörü: `InstagramTracker-Desktop-0.2.59`; içindeki `InstagramTracker.exe` çalıştırılır.
- Hesap/profil okuma ve kendi hesabının iki liste yönünde gerçek yanıtlar görülmüştür. Bu canlı örnekler bütün hesapları veya her büyüklükteki listeyi kapsamaz.
- Son Threads düzeltmesinde, bildirilen profilin gerçek Instagram rozeti okunup bağlantı kayda alınmıştır. Ayrı Threads sorgusu yapılmamıştır.
- Windows doğrulama ve kaynak ölçümü kapsamı [0.2.47 optimizasyon notunda](../DESKTOP-RESOURCE-OPTIMIZATION.md) bulunur. 0.2.48 saklama/silme güncellemesi ve sınırları [veri yaşam döngüsünde](../DATA-LIFECYCLE.md) bulunur.

0.2.49, Windows kısayolunda ve görev çubuğunda Tracker ICO simgesini açıkça kullanır; uygulamaya ait eski kısayolları yedekleyerek kaldırır.

## Android

**Güncel APK: 0.1.86 · sürüm kodu: 87 · kaynak etiketi: `android-v0.1.86`**

- Dosya: `InstagramTracker-0.1.86.apk`; Android 7.0 / API 24 ve üzeri hedeflenir.
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

Proje sahibi tarafından paylaşılan imzalı APK'yı veya tam Windows ZIP'ini kullanın. Bu depoda APK/EXE kaynak dosyası olarak saklanmaz. Bir Git etiketi, o etikete ait indirilebilir dosyanın ayrıca yayımlandığı anlamına gelmez.

Bu kılavuz, herkese açık indirme veya mağaza yayını yapılmış gibi bir bağlantı sunmaz. Dağıtım yeri değiştiğinde bu sayfa güncellenmelidir.

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

Resmî Instagram ZIP içe aktarma, işlevsel web hizmeti, geniş Threads/Facebook entegrasyonu ve AI içerik analizi henüz tamamlanmış özellikler değildir. [Geliştirme notları](../DEVELOPMENT-BACKLOG.md) güncel kapsamı korur; listedeki bir fikir yayın tarihi taahhüdü değildir.

## Son verimlilik güncellemeleri

0.2.54 / 0.1.84 ile otomatik profil işleri yerel bütçenin %80 eşiğinde ertelenir. 0.2.55 / 0.1.85 günlük sınırları hesap için 3.000, cihaz için 6.000 yaptı; saatlik sınırlar 120 / 240 olarak kaldı. 0.2.56 / 0.1.86 doğrulanmış profil bilgisini ve eşzamanlı görsel işlerini yeniden kullanır. [Ortak politika](../REQUEST-POLICY.md) · [Yanıt paylaşımı](../RESPONSE-SHARING-TEST.md).

## GitHub ve dağıtım durumu

5 Kasım 2026 denetiminde depo özel ve GitHub Wiki kapalıdır; kılavuz bu deponun `docs/wiki/` dizininde okunur. GitHub Releases bölümündeki son yayımlanmış paket `v0.1.46` önizlemesidir; güncel kaynak etiketleriyle aynı dağıtım değildir. Yeni APK/EXE dosyaları bu belge güncellemesiyle Releases'e yüklenmez. Güncel kaynak sürümleri yukarıda belirtilmiştir.

## Son eşitleme

[Android / Windows eşitlemesi ve test sınırları](../PLATFORM-PARITY.md): ortak istek politikası ve Android hikâye arşivi eklendi. Android cihaz/ekran ve yeni canlı giriş kontrolü ertelendi; eksiksiz platform eşitliği veya çözülmüş giriş iddiası yoktur.

Android 0.1.82 CAPTCHA içerik sorgusu ve tanı kopyalama düzeltmesi: [kanıt/test kapsamı](../ANDROID-CAPTCHA-CONTENT.md). Girişten profil okumaya canlı doğrulama henüz tamamlanmadı.

Android 0.1.83, CAPTCHA’nın gerçek içerik şemasını ve gözlenen sonuç gönderme adımlarını ekler; [canlı kanıt ve bekleyen doğrulama](../ANDROID-CAPTCHA-CONTENT.md).

0.1.83 fiziksel testinde CAPTCHA sonrasında şifreli bekleyen Instagram oturumu oluştu; sonraki profil okuması HTTP 429 aldı. Tracker profil/liste bağlantısı henüz tamamlanmadı. [Son canlı sonuç](../ANDROID-CAPTCHA-CONTENT.md).
