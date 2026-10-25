# Profil okuma politikası — 0.1.7

Bu belge özel mobil/web profil okuyucularının sözleşmesidir. Bu uçlar resmî Graph API kotasıyla eş tutulmaz. 25 Ekim 2026'da kullanıcı fiziksel telefonda mobil sayaç ve ilk liste okumalarının çalıştığını bildirdi; aşağıdaki denetimler bir sorunsuzluk garantisi değildir. Kanıt kapsamı [VALIDATION.md](VALIDATION.md) içindedir.

## Dayanak ve bilinmeyenler

[RFC 6585, bölüm 4](https://www.rfc-editor.org/info/rfc6585/) HTTP 429'u istek sınırlaması olarak tanımlar; Retry-After zorunlu değildir. Sunucunun kullanıcıyı nasıl tanıdığı ve istekleri hangi kapsamda topladığı bu standartta belirlenmez. Dolayısıyla kendi uygulamamızda tek profil denemesi görülmesi, sunucunun sayacının da bir olduğunu göstermez. 429 tek başına hesabın ele geçirildiğini, parolanın yanlış olduğunu veya belirli bir saatte açılacağını kanıtlamaz.

[RFC 9110, bölüm 10.2.3](https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after) Retry-After için sıfır dahil negatif olmayan saniye veya HTTP tarihi tanımlar. Eksik, bozuk ve taşan değerler bilinmeyen olarak tutulur. Yerel güvenlik molası ayrıca hesaplanır; sunucu süresi diye sunulmaz. 429 yanıt gövdesi önbelleğe alınmaz; yalnız yerel sınırlama metaverisi korunur.

[Kullandığımız kütüphanenin rehberi](https://subzeroid.github.io/instagrapi/latest/usage-guide/best-practices/), web yolunun mobil yoldan bağımsız 429 alabileceğini; taze giriş döngüsü yerine oturum/cihaz durumunu korumayı ve kısıtlamalarda durmayı önerir. Yeni hesapları hemen yüksek hacimde kullanmama uyarısı da vardır. Bu, Meta'nın yayımladığı garanti veya bu proje için doğrulanmış bir güvenli kota değildir. Yeni hesap açmak, cihaz veya istemci değiştirmek sorunun giderildiğini kanıtlamaz.

Projedeki gözlem: Windows prototipinde ilk giriş ve kayıtlı oturumdan yeniden okuma başarılıydı; Android web prototipindeki tek kontrollü okuma da 429 verdi. Bunlar farklı istemci/oturum/taşıma yollarıydı. Mobil oturumun Android'e şifreli aktarımı doğrulandı. 25 Ekim'de kullanıcı APK'da kendi sayaçlarının, kendi takip edilen listesinin ve arkadaşının ilk takipçi sayfasının geldiğini bildirdi; bu tur bağımsız HTTP kaydı alınmadı. Resmî Instagram uygulamasında sayılar görülmesi de aynı API/oturumun taze isteğiyle eşdeğer ölçüm değildir. Mevcut kanıt belirli bir sunucu filtresini veya cihaz/IP/hesap kotasını teşhis etmeye yetmez.

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


## 0.1.8 liste okuyucusu

Takipçi ve takip edilen sayfaları aynı kalıcı RequestGate, tek etkin okuma, 15 saniyelik alt aralık ve HTTP/2 tek gönderim sözleşmesini kullanır. Her düğme yalnız bir friendships/v1 sayfası ister; otomatik cursor döngüsü, GraphQL/web yedeği veya giriş yoktur. İlk sayfa için istenen 50 kayıt araştırma tercihidir; Instagram kotası değildir. Liste başarısı önceki kısıtlamayı temizleyip otomasyonu açmaz. should_limit_list_of_followers, has_more ve next_max_id eksik/null/değer olarak korunur; listeye özel HTTP 200 sınırlaması HTTP 429 diye kaydedilmez. Bu durumda alınan sayfa sınırlı işaretlenir ve devam düğmesi sunulmaz. Genel HTTP 429/bekleme/feedback mevcut kalıcı politikayı uygular. Yeni canlı veri isteği bu geliştirmede yapılmadı.


## 0.1.14 isteğe bağlı liste otomasyonu (ilk sürüm)

Kullanıcının yeni talebiyle seçilen hedeflerde otomatik cursor devamı eklendi. Bu, 0.1.8'deki elle tek sayfa modunun yerine geçmez: tek çağrı halen tek sayfadır; yeni uygulama kuyruğu sayfalar arasında kalıcı duraklama/rezervasyon uygular. Her Android işinde bir profil veya bir liste sayfası; bütün otomatik işler için en az 15 dakika bütçe. Profil ve liste işlerinin ikisi de hazırsa dönüşümlü seçim yapılır. Üretim okuyucularının uç, sayfa boyutu, tek etkin istek ve HTTP yanıt sınırı değişmedi.

Güncel taramada cursor yalnız MORE durumunda ilerler; LIMITED/UNKNOWN/STALLED durumda otomatik devam yoktur. Liste sonu gözlenince veya beklenen bir okuma hatası alınca yeni döngü en erken 24 saat sonra hedeflenir; HTTP 429/oturum doğrulaması bundan bağımsız mevcut genel duraklamayı uygular. Bu 24 saat Instagram cursor ömrü veya güvenli kota garantisi değildir. Cursor sunucu tarafından reddedilirse aynı işte ilk sayfa/başka uç yedeği denenmez. İlk/yarım/sınırlı okumadan fark bildirimi çıkmaz.

Rezervasyon, mevcut oturum/hedef ve beklenen önceki sayfa gözlemiyle bağlanır. Hedef kaldırılır, otomasyon kapatılır, oturum değişir veya manuel yeni okuma araya girerse geç otomatik sayfa eski kaydı ezmez. Kapasite hatasında önceki liste korunur; kaydedilemeyen yönde yeni otomatik sayfa alınmaz. Rezervasyon/hata kaydı da sığmıyorsa kapasite molası aynı RequestGate'de ayrı bayrakla tutulur; liste temizliği yalnız bu bayrağı kaldırır, Instagram kısıtlamasını kaldırmaz.

Bu geliştirmede gerçek Instagram isteği gönderilmedi; testler sentetik taşıma kullanır. Önce az sayıda küçük, kullanıcının kontrolündeki hesapla fiziksel cihaz testi gerekir. Uzun taramalar sırasında üyelik değişebilir; END_OBSERVED yalnız gözlenen sunucu sayfalama sonudur, eksiksizlik veya atomiklik ispatı değildir.

## 0.1.15 aynı hedefte ardışık sayfalar

0.1.14'teki tek sayfa/tur sınırı değişti: bir tur tek profil veya aynı hedefte en fazla 6 liste sayfası okur. Takipçi/takip edilen yönleri uygunsa dönüşümlüdür; tur içinde başka hedefe geçilmez. Otomatik tur başlangıçları ortak en az 15 dakika bütçesini korur. İstek başlangıçları arasında mevcut kalıcı en az 15 saniye alt aralık ve tek etkin taşıma korunur. Turun ilk 90 saniyesinde yeni istek başlatılabilir; bu süre monotonik saatle ölçülür, devam eden istek kendi taşıma zaman aşımını kullanır. Android durdurması kalan sayfaları sonraki uygun işe bırakır. 6 sayfa/90 saniye yerel kaynak sınırlarıdır, sunucunun güvenli kotası değildir.

Her sayfa hemen şifreli kasaya kaydedilir. Turun az önce kaydettiği değişmemiş MORE kaydı, yalnız o tur içinde devam rezervasyonunun 15 dakikalık sonraki-iş hedefini beklemeden ilerleyebilir. Son sayfası gözlenmiş bir yönün 24 saatlik yeniden tarama hedefi bu yolla aşılamaz. Elle yeniden okuma, silme, oturum/seçim değişimi, iptal, izin kaybı veya genel mola sonraki isteği durdurur. Ortak taşıma başka okumayla meşgulse bu tur biter; bu yerel TOO_SOON sinyali bir 24 saatlik sunucu hatası diye kaydedilmez.

İlk beklenen hata veya HTTP 200 LIMITED/UNKNOWN/STALLED sonucu bütün turu durdurur; diğer yöne yedek istek yoktur. Alınan sayfalar korunur. 429, doğrulama ve kapasite politikası değişmedi. Uçlar, oturum aktarımı, sayfa boyutu tercihi ve fark bildirimi koşulları değişmedi. Canlı Instagram çok sayfalı davranışı bu geliştirmede ölçülmedi.
