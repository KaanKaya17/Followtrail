# Tracker — ortak web ve masaüstü arayüzü

**Güncel Windows uygulaması 0.2.73:** gerçek hesap işlemleri ve şifreli yerel kayıt için `../artifacts/InstagramTracker-Desktop-0.2.73/InstagramTracker.exe` açılır. Python kurmak gerekmez; uygulama klasörü bütün halinde tutulur. [Masaüstü kullanımı, kapsamı ve derleme](DESKTOP.md). `npm run desktop` bu işlevli uygulamayı başlatır; önce yerel worker derlenmelidir. Aşağıdaki bölüm, korunmuş **örnek verili web/tasarım önizlemesini** anlatır.

Ayarlar → Tema: Sistem / Açık tema / Koyu / OLED siyahı. Varsayılan Sistem canlı sistem değişimini izler; açık seçimler bu tarayıcıda saklanır. Aynı ortak palet Windows 0.2.11 ve Android 0.1.32’de de kullanılır.

Bu aşama, gerçek Instagram işlevlerinden önce hazırlanan **etkileşimli arayüz önizlemesidir**. Bütün hesaplar, sayılar, kayıtlar ve bildirimler örnektir. Mevcut InstagramCounter/Android oturumu okunmaz; kullanıcı adı/parola istenmez. Tarayıcı istekleri CSP ile yerel varlıklara sınırlandırılır; veri bağlantıları kapalıdır.

## Açma

- Web: `start-web.cmd` veya `npm run preview`, ardından `http://127.0.0.1:4173`. Yalnız loopback üzerinde çalışır. Durdurmak için terminalde Ctrl+C.
- Masaüstü: hazırlanmış `../artifacts/Tracker-Desktop-UI/InstagramTracker.exe`. Yanındaki dosyalar aynı klasörde kalmalı. Bu bir taşınabilir önizlemedir; sistem kurulumu veya kod imzası iddiası taşımaz.
- Kaynaktan tasarım önizlemesi: `npm ci`, `node node_modules/electron/install.js`, `npm run desktop:preview`. Electron **45.0.0** proje içinde sabittir; sistem Node/Electron kurulumu güncellenmez. Bu sürümün paketinde indirme otomatik postinstall olarak çalışmadığından binary adımı açıktır.

## Tasarım ve kapsam

Özet, Kaydedilen Profiller, Takip Listeleri, Zaman çizelgesi, Ayarlar ve profil/olay ayrıntıları hazırdır. Tarih aralığı, yerel arama, favoriler, örnek kişi ekleme, oturumluk örnek notlar, seçilen liste yönünün karşılaştırması, bildirim görünümü ve kompakt görünüm kullanılabilir. Örnek kişi/favori/notlar yeniden yüklemede sıfırlanır; kompakt görünüm ve dil tercihi localStorage'da kalır. Yenileme gerçek işlem taklidi yapmaz; bağlantının etkin olmadığını açıklar.

`../design/theme.json` ortak renklerin ve temel köşe ölçülerinin sahibidir. `npm run theme`, Android `UiPalette.kt` ile web `theme.css` üretir. `npm run check` eşleşmeyi doğrular. Android ana ekranı aynı palet, düz mor vurgular, 24 dp kartlar ve 18 dp düğmelerle uyumludur; platformlara özgü gezinme korunur.

Masaüstü aynı web dosyalarını yerel Electron penceresinde açar. Renderer'da Node kapalı, context isolation ve sandbox açık; yeni pencere/harici gezinme ve izin istekleri kapalıdır. Instagram'a veya üçüncü taraf backend'e bağlantı yoktur. Paket yalnız bu arayüzü içerir; Android/Python oturumu, kullanıcı verisi veya geliştirme bağımlılıkları pakete alınmaz.

## Kontroller ve paket

`npm run check` kaynak sözdizimini ve üretilen tema dosyalarını kontrol eder. Yerel sunucu açıkken `npm run check:ui`, kurulu Microsoft Edge'in ayrı headless profilinde arama/filtre/ayrıntı/durum ve 1440×1050 / 390×844 taşma kontrollerini yapar; rapor ve ekran görüntüleri `../artifacts/ui-0.1.30` altındadır. Kullanıcının tarayıcı profilini kullanmaz. `npm run check:desktop` paketlenmiş gerçek Windows uygulamasında yerel arama/profil penceresini ve renderer izolasyonunu kontrol eder; `-- --keep-open` pencereyi inceleme için açık bırakır.

`npm run package:desktop` taşınabilir klasörü ilk kez oluşturur. Önizlemenin web/main dosyaları değişirse `npm run package:desktop -- --refresh` önce eski uygulama varlıklarını zaman damgalı artifacts yedeğine kopyalar, ardından yalnız kimliği doğrulanan önizleme paketinin kendi uygulama varlıklarını günceller; mevcut klasör körlemesine silinmez. Bir Electron yükseltmesi için yeni runtime ile ayrı paket hazırlanmalıdır.

Bu örnek verili önizlemenin açılması Instagram erişiminin çalıştığını kanıtlamaz. Gerçek işlevler ayrı masaüstü paketindedir; tarayıcı önizlemesine hesap bağlantısı eklenmedi.

## Dil seçimi

Web önizlemesinde **Ayarlar → Arayüz dili**, Windows uygulamasında **Ayarlar → Uygulama dili** üzerinden **Türkçe / English / Español / Português (Brasil)** seçilir. Web tercihi bu tarayıcının localStorage alanında, Windows tercihi mevcut DPAPI deposunda saklanır. Hesap/izleme/mola/bildirim tercihleri değişmez; web önizlemesi yine yalnız örnek veridir.

`locales/en.json`, `locales/es.json` ve `locales/pt-BR.json` Türkçe kaynak mesajların karşılıklarını ve çoğul biçimlerini, `locales/runtime.js` yerel biçimlendiriciyi içerir. `npm run i18n`, web ve Node tarafından kullanılan `web/i18n.js` çıktısını üretir; `npm run check` her dilde aynı 999 anahtarı, yer tutucuları ve kaynak mesaj kapsamını denetler. Worker aynı JSON kataloglarını paketler. Çalışma sırasında çeviri servisi veya ek ağ isteği yoktur. Kullanıcı adı/biyografi/not/önceki-sonraki değerler çevrilmez.
