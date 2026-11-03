# Sürüm ve Destek

[Kılavuz ana sayfası](Home.md) · [Değişiklik kaydı](../../CHANGELOG.md)

**Son içerik denetimi: 3 Kasım 2026.** Bu sayfa, uygulanmış özelliklerle canlı ortamda doğrulanmış kapsamı ayırır.

## Windows

**Güncel önizleme: 0.2.36 · kaynak etiketi: `desktop-v0.2.36`**

- Taşınabilir Windows x64 dağıtımıdır.
- Paket: `InstagramTracker-Desktop-0.2.36.zip`; çıkarılan klasörde `InstagramTracker.exe` çalıştırılır.
- Hesap/profil okuma ve kendi hesabının iki liste yönünde gerçek yanıtlar görülmüştür. Bu canlı örnekler bütün hesapları veya her büyüklükteki listeyi kapsamaz.
- Son Threads düzeltmesinde, bildirilen profilin gerçek Instagram rozeti okunup bağlantı kayda alınmıştır. Ayrı Threads sorgusu yapılmamıştır.
- Son sürümde 224 Python, 54 Node, 10 arayüz kontrolü ve paketli EXE üzerinde 36 genel, 4 büyük liste, 5 günlük geçmiş ve 10 çoklu hesap kontrolü geçmiştir. Bu testlerin büyük bölümü sentetik veri kullanır.

## Android

**Güncel APK: 0.1.75 · sürüm kodu: 76 · kaynak etiketi: `android-v0.1.75`**

- Dosya: `InstagramTracker-0.1.75.apk`; Android 7.0 / API 24 ve üzeri hedeflenir.
- Kod sonrası girişten profil kontrolüne geçildiği ve profil 429 aldığında oturumun şifreli devam ekranında korunduğu gerçek telefonda görülmüştür.
- **Telefon içinden uçtan uca başarılı hesap bağlantısı ve liste okuması henüz tamamlanmış sayılmıyor.** Araştırılan açık konu girişten sonraki profil okumasıdır.
- E-posta/kod akışı üzerinde çalışılmıştır; her CAPTCHA, TOTP ve başka cihazdan onay yönteminin desteklendiği varsayılmamalıdır. Cihaz onayı sonucunu periyodik sorgulayan yöntem hâlâ sınırlıdır.
- Yeni günlük geçmiş ekranı ve Keystore dosyaları için altı cihaz testi derlendi; cihazda çalıştırılmadı.
- 0.1.75'te 138 JVM testi, release derlemesi, lint ve imzalı paket kontrolleri geçmiştir. Yeni Threads ayrıştırması gerçek telefonda ayrıca sınanmamıştır.

## Web

Örnek verilerle arayüz önizlemesidir. Gerçek hesaba bağlanma, sürekli arka plan kontrolü ve cihazlar arasında veri eşitleme hizmeti değildir.

## Günlük istatistikler

Windows ve Android için ayrı şifreli günlük arşiv eklendi. Son kontroller silinse de günlük özetler otomatik silinmez. [Kullanım ve sınırlar](Gunluk-Istatistikler.md).

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
