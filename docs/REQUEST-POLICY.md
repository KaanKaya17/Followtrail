# Profil okuma politikası — 0.1.7

Bu belge özel mobil/web profil okuyucularının sözleşmesidir. Bu uçlar resmî Graph API kotasıyla eş tutulmaz. Yeni test hesabında canlı kabul henüz doğrulanmadı; aşağıdaki denetimler bir sorunsuzluk garantisi değildir.

## Dayanak ve bilinmeyenler

[RFC 6585, bölüm 4](https://www.rfc-editor.org/info/rfc6585/) HTTP 429'u istek sınırlaması olarak tanımlar; Retry-After zorunlu değildir. Sunucunun kullanıcıyı nasıl tanıdığı ve istekleri hangi kapsamda topladığı bu standartta belirlenmez. Dolayısıyla kendi uygulamamızda tek profil denemesi görülmesi, sunucunun sayacının da bir olduğunu göstermez. 429 tek başına hesabın ele geçirildiğini, parolanın yanlış olduğunu veya belirli bir saatte açılacağını kanıtlamaz.

[RFC 9110, bölüm 10.2.3](https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after) Retry-After için sıfır dahil negatif olmayan saniye veya HTTP tarihi tanımlar. Eksik, bozuk ve taşan değerler bilinmeyen olarak tutulur. Yerel güvenlik molası ayrıca hesaplanır; sunucu süresi diye sunulmaz. 429 yanıt gövdesi önbelleğe alınmaz; yalnız yerel sınırlama metaverisi korunur.

[Kullandığımız kütüphanenin rehberi](https://subzeroid.github.io/instagrapi/latest/usage-guide/best-practices/), web yolunun mobil yoldan bağımsız 429 alabileceğini; taze giriş döngüsü yerine oturum/cihaz durumunu korumayı ve kısıtlamalarda durmayı önerir. Yeni hesapları hemen yüksek hacimde kullanmama uyarısı da vardır. Bu, Meta'nın yayımladığı garanti veya bu proje için doğrulanmış bir güvenli kota değildir. Yeni hesap açmak, cihaz veya istemci değiştirmek sorunun giderildiğini kanıtlamaz.

Projedeki gözlem: Windows prototipinde ilk giriş ve kayıtlı oturumdan yeniden okuma başarılıydı; Android web prototipindeki tek kontrollü okuma da 429 verdi. Bunlar farklı istemci/oturum/taşıma yollarıydı. Mobil oturumun Android'e şifreli aktarımı doğrulandı; mobil canlı GET başarısı doğrulanmadı. Resmî Instagram uygulamasında sayılar görülmesi kullanıcı bildirimidir; aynı API/oturumun taze isteğiyle eşdeğer ölçüm değildir. Mevcut kanıt belirli bir sunucu filtresini veya cihaz/IP/hesap kotasını teşhis etmeye yetmez.

## Uygulanan sözleşme

- Açılışta kendi hesabını otomatik yenileme varsayılan kapalıdır. Kullanıcı Seçenekler → Yenileme ayarları ile açabilir; bu seçim 429'u veya doğrulama duraklamasını kaldırmaz. Bu tercih kapalıyken ekran açmak profil okuması başlatmaz. Sekme değiştirmek, analiz veya yardım açmak da okuma başlatmaz.
- Kendi hesap ve kayıtlı kişiler için 15 dakika içindeki başarılı gözlem kullanılır. Ağ işleminden önce şifreli kasanın son hali tekrar okunur; ekrandaki eski kopya, arka planda kaydedilmiş taze gözlemi görmezden gelip yeni istek attıramaz. Önbellekten okuma yeni tarih/gözlem/bildirim üretmez; son gerçek kayıt zamanı gösterilir.
- Bütün taşımalarda ortak tek etkin okuma ve en az 15 saniyelik kalıcı alt aralık vardır. Bu alt aralık Instagram'ın güvenli kotası değildir. İlk bağlantı okumaları ayrıca kalıcı en az 15 dakika aralıklıdır; sayfa/Activity yenilemek bütçeyi sıfırlamaz.
- Web giriş sayfasının yüklenmesi veya girişin algılanması artık bizim profil GET'imizi otomatik başlatmaz. Devam et bir kez açık kullanıcı eylemidir; başarısızlıkta aynı ekran tekrar göndermez. Web sayfasının kendi kaynak/istek trafiği bu sayaca dahil değildir. Windows CAA girişi de birden çok protokol adımı içerebilir; tek giriş düğmesi tek HTTP isteği demek değildir.
- Mobil OkHttp taşımasında bağlantı tekrarı, paralel alternatif ve yönlendirme kapalıdır. 503/Retry-After:0 dahil takip istekleri ikinci ağ iletiminden önce durdurulur. HTTP/2 yoksa kimlik doğrulamalı profil GET'i gönderilmez. Sistem TLS'i Windows curl uygulamasıyla birebir aynı değildir.
- HTTP 429, bilinen bekleme mesajı ve feedback_required farklı sabit sinyaller olarak kaydedilir; hepsi otomasyonu durdurur. Oturumun bitmesi veya doğrulama gereksinimi ayrı hatadır. Özel sunucu hata metinleri loga/kasaya alınmaz. İki native okuyucu bu yorumu aynı InstagramResponsePolicy sahibinden alır; tarayıcı projeksiyonu da 429/bekleme/feedback ayrımını korur.
- İlk yerel kısıtlama molası en az 1 saattir; tekrarda 2/4/8/16/24 saate çıkar. Daha uzun geçerli Retry-After veya zaten kayıtlı daha uzun mola kısaltılmaz. Zaman dolunca otomatik deneme yapılmaz. Yeni hesap seçmek, uygulamayı açmak veya şifreli aktarım yapmak kaydı sıfırlamaz.
- Yalnız manuel moda almak artık Instagram sınırlaması gibi gösterilmez. Ayrıntılar, son sınırlama sinyali, varsa sunucu süresi ve uygulamanın kendi molasını ayırır. Başarılı bağlantı testi, yeni hesap ana kayda bağlanana kadar eski okuyucunun otomasyonunu açamaz.
- Kişi izleme ayrı ve açık kullanıcı tercihidir: izin varsa en az 1/6/24 saat hedeflenir; ortak otomatik bütçede en fazla 15 dakikada bir sıradaki kişi okunur. Doze/üretici/pil koşulları geciktirebilir. Anlık veya garantili teslim iddiası yoktur.

## Tanılama ve test sınırı

Seçenekler → İstek bilgileri, bu sürümden itibaren başlatılan mantıksal profil okuma denemelerini, zamanı ve bilinen son HTTP durumunu gösterir. Engel nedeniyle başlatılmayan tıklama sayılmaz. Yeni denemede önceki HTTP durumu temizlenir; henüz yanıt gelmediyse bilinmeyen kalır. Sayaç, sunucunun aldığı toplam istek, bütün cihaz trafiği veya resmî Instagram oturumu ölçümü değildir. Kullanıcı adı, URL, IP, parola, çerez ve ham gövde tutulmaz.

Doğrulama, ağsız profil/vault/gate testleri ile yerel TLS/HTTP2 sunucusu ve Android Keystore/ekran akışlarına dayanır. CI Instagram hesabı kullanmaz. Yeni canlı testte kullanıcı doğru hesabını kendisi bağlar; önceki yerel mola korunur ve yalnız açık bir okuma denenir. İlk hata durdurulur; hesap/IP/cihaz döndürme, otomatik tekrar veya farklı uçlara yedek sorgu başlatılmaz. Sonuçlar [VALIDATION.md](VALIDATION.md) içinde gözlem/çıkarım ayrımıyla tutulur.
