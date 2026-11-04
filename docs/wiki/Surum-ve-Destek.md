# Sürüm ve Destek

[Kılavuz ana sayfası](Home.md) · [Değişiklik kaydı](../../CHANGELOG.md)

**Son içerik denetimi: 4 Kasım 2026.** Bu sayfa, uygulanmış özelliklerle canlı ortamda doğrulanmış kapsamı ayırır.

## Windows

**Güncel önizleme: 0.2.50 · kaynak etiketi: `desktop-v0.2.50`**

- Taşınabilir Windows x64 dağıtımıdır.
- Dağıtım klasörü: `InstagramTracker-Desktop-0.2.50`; içindeki `InstagramTracker.exe` çalıştırılır.
- Hesap/profil okuma ve kendi hesabının iki liste yönünde gerçek yanıtlar görülmüştür. Bu canlı örnekler bütün hesapları veya her büyüklükteki listeyi kapsamaz.
- Son Threads düzeltmesinde, bildirilen profilin gerçek Instagram rozeti okunup bağlantı kayda alınmıştır. Ayrı Threads sorgusu yapılmamıştır.
- Windows doğrulama ve kaynak ölçümü kapsamı [0.2.47 optimizasyon notunda](../DESKTOP-RESOURCE-OPTIMIZATION.md) bulunur. 0.2.48 saklama/silme güncellemesi ve sınırları [veri yaşam döngüsünde](../DATA-LIFECYCLE.md) bulunur.

0.2.49, Windows kısayolunda ve görev çubuğunda Tracker ICO simgesini açıkça kullanır; uygulamaya ait eski kısayolları yedekleyerek kaldırır.

## Android

**Güncel APK: 0.1.78 · sürüm kodu: 79 · kaynak etiketi: `android-v0.1.78`**

- Dosya: `InstagramTracker-0.1.78.apk`; Android 7.0 / API 24 ve üzeri hedeflenir.
- Kod sonrası girişten profil kontrolüne geçildiği ve profil 429 aldığında oturumun şifreli devam ekranında korunduğu gerçek telefonda görülmüştür.
- **Telefon içinden uçtan uca başarılı hesap bağlantısı ve liste okuması henüz tamamlanmış sayılmıyor.** Araştırılan açık konu girişten sonraki profil okumasıdır.
- E-posta/kod akışı üzerinde çalışılmıştır; her CAPTCHA, TOTP ve başka cihazdan onay yönteminin desteklendiği varsayılmamalıdır. Cihaz onayı sonucunu periyodik sorgulayan yöntem hâlâ sınırlıdır.
- Yeni günlük geçmiş ekranı ve Keystore dosyaları için altı cihaz testi derlendi; cihazda çalıştırılmadı.
- 0.1.76: profil/özet/liste sahibi ve yerel Bildirimler/Zaman Çizelgesi görünümü güncellendi. 142 JVM testi, release derlemesi, lint ve imzalı paket kontrolü geçti; emülatör ekran testi tamamlanmadı. [Doğrulama ve kalan kapsam](../ANDROID-UI-ALIGNMENT.md).

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
