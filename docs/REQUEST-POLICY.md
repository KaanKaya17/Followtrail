# Profil okuma politikası — 0.1.7

Bu belge özel mobil/web profil okuyucularının sözleşmesidir. Bu uçlar resmî Graph API kotasıyla eş tutulmaz. 25 Ekim 2026'da kullanıcı fiziksel telefonda mobil sayaç ve ilk liste okumalarının çalıştığını bildirdi; aşağıdaki denetimler bir sorunsuzluk garantisi değildir. Kanıt kapsamı [VALIDATION.md](VALIDATION.md) içindedir.

## 0.1.22 yerel durum sunumu

Profil durum özeti yalnız hesap/liste/işlem geçmişi kasalarını ve mevcut kapı/izin/ağ durumunu okur. Görünür ekranda yaklaşık 30 saniyelik yenileme yerel işçide yapılır; tek yenileme aynı ekrandaki bütün kartların kayıtlarını paylaşır. Ekrandan ayrılınca zamanlayıcı durur. Bu sunum profil/liste HTTP isteği göndermez, gate rezervasyonu almaz, otomatik işi eşlemez ve kayıt yazmaz. Durum ayrıntıları da salt okunurdur. Zaman çizelgesi yereldir; Listeleri incele yalnız mevcut liste ekranını açar. Görünür fotoğrafların önceki CDN/önbellek davranışı değişmez. Kontrol sıklığı ve 429/oturum molaları 0.1.21 ile aynıdır.

## 0.1.21 güncel otomatik kontrol sözleşmesi

Aşağıdaki sürüm notlarından farklı olarak güncel arka plan işi tek ortak 15 dakikalık rezervasyonda toplam en fazla altı profil/liste isteği çalıştırır. İlk hazır profil öne alınır, devamında mevcut adil profil/liste kuyruğu kullanılır. Bütün istek başlangıçları en az 15 saniye aralıklıdır; ilk 90 saniye içinde yeni istek başlatılabilir. Altı istek/90 saniye yerel kaynak bütçesidir. Kendi hesap profili için hedef 15 dakikadır. Kullanıcı otomasyon kurarken seçtiği kişileri 15 dakikaya alır; güncelleme diğer eski aralıkları değiştirmez.

Açık Hızlı izle eylemi yalnız bir hedefi, uygulama görünürken 15 dakika boyunca 60 saniyelik profil tazeliği hedefiyle okur. Bu yol o hedef için normal 15 dakika önbelleği yerine 60 saniyeyi kullanır; diğer ekranlar normal önbelleği korur. Aynı profil/liste okuyucuları, 15 saniyelik genel kapı, tek etkin taşıma, altı istek/90 saniye, kimlik/izin ve mola denetimleri kullanılır. Hızlı oturum sırasında arka plan rezervasyonu alınmaz; başlamış iş iptali görüp devam etmez. Ekrandan ayrılmak hızlı taşımayı iptal eder ve normal Android işini tekrar eşler; ayrı sürekli servis/wakelock yoktur. Oturum monotonik 15 dakika sonunda biter, yeniden oluşturma veya arka planda bekleme bunu uzatmaz. İlk hata veya belirsiz liste sonucu oturumu bitirir; 429/doğrulama molası temizlenmez. 60 saniye kesin süre veya güvenli Instagram kotası olarak doğrulanmadı.

Liste başlangıcında profil sayısı ve gözlem tarihi şifreli liste kaydına bağlanır. Daha yeni profil sayısı farklıysa tamamlanmış ilgili yön erkene alınabilir. Sayılar aynıysa tamamlanmış liste günlük hedefe kadar yeniden okunmaz; üyelik değişiminin sayı aynıyken de olabileceği kabul edilir ve günlük tarama korunur. Yarım liste devam eder; hata/kapasite molaları sayı değişimiyle aşılmaz. Eski kayıtta dayanak yoksa daha yeni profil gözlemi bir kez başlangıç taraması gerektirebilir. Bir turda listeler bütçeyi tüketirse profilin sonraki kontrolü gecikir.

İşlem geçmişi başarılı commit sonrası gözlem/karşılaştırma zamanlarını ve beklenen hata türünü ayrı şifreli kaydeder; isteğe bağlı geçmiş kaydı başarısızlığı başarılı profil/liste commit'ini geri almaz. Kaynak alan bilinmeyense tarih uydurulmaz. Eksik biyografi/fotoğraf alanlarından sonra fark görülürse önceki bilinen karşılaştırma tarihi korunur. Eski 0.1.13–0.1.18 alt bölümlerinin tek profil/saatlik kendi hesap ve erken tarama yok ifadeleri tarihsel davranıştır.

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

## 0.1.16 görünür profil fotoğrafları

Profil/liste API'sinin RequestGate ve kontrol süreleri değişmedi. Yeni görsel istemcisi yalnız mevcut yanıttaki izinli HTTPS CDN bağlantısını okur; Instagram oturumunu kullanmaz ve profil/liste sayacına dahil değildir. Sayfa açmak artık görünür ve önbellekte olmayan fotoğraflar için ayrı CDN GET'leri başlatabilir; “yeni istek yok” ifadeleri profil/liste verisi sorgusunu anlatır. Arama yeni üye profili sorgulamaz; görünür hale gelen mevcut üyelerin görselleri yüklenebilir. Görsel başına en çok 1 MiB kabul edilir; 256 piksele örneklenir. Yükleme hatası aynı ekran ömründe sınırlı başarısız-adres kaydında tutulur; aynı fotoğraf için kaydırmayla anında tekrar döngüsü yapılmaz. Redirect/bağlantı tekrarı kapalıdır; kimlik doğrulamalı veya başka host üzerinden yedek okuma yapılmaz.

Bu görseller kullanıcı arayüzüne aittir; JobScheduler fotoğraf indirmez, görsel indirimi profil fotoğrafı değişikliği hesabını veya bildirim sıklığını değiştirmez. CDN adresi/kimliği aynı kaldığında önbellekteki görselin güncelliği garanti edilmez. Canlı görsel erişimi kullanıcı testinde doğrulanmalıdır.

## 0.1.17 uygulama içi profil

Bir liste üyesini açmak ilk kez profil bilgisi okumak için açık gezinme eylemidir. Bilinen taze kayıt önbellekten kullanılır; aksi halde bir mevcut readId çağrısı yapılabilir. Ortak 15 saniye alt aralığı ve bütün mevcut mola/kimlik denetimleri korunur. Aralık uygun değilse ilk açılış sorgu yapmaz; Profili getir kullanıcının devam eylemidir. Ekrana geri dönüş ve yeniden oluşturma otomatik tekrar başlatmaz. Geçici profil okumaları kişinin otomatik izleme seçimini değiştirmez.

Fotoğraf taşımasının iptali ve istemci/socket temizliği UI yaşam döngüsünü çağıran ana iş parçacığında çalıştırılmaz. İptal işareti ve geç teslimi engelleme eşzamanlıdır; socket kapatma arka plan sahibinde tamamlanır. Böylece TLS close_notify yazımının NetworkOnMainThreadException ile ekran kapanışını çökertmesi önlenir.

## 0.1.18 kullanıcı isteğiyle akıcı devam

Kısa bekleme kuyruğu yalnız normal alt aralığı en fazla 15 saniye kalan bir açık işlemi kabul eder. Bir işlem tutar, tekrar basmak ikinci işlem eklemez. Bir saniyelik metin güncellemesi ağ isteği veya yeni gözlem değildir. İşlem başlamadan önce güncel gate ve oturum/sayfa bağlamı yeniden denetlenir. Bekleme 30 saniyeye uzarsa iptal edilir. 429 kaydı (süresi dolmuş ama henüz başarılı manuel okumayla temizlenmemişse de), doğrulama/bağlantı veya genel duraklama kuyruğa alınmaz; var olan kuyruk bu durumlarda sonlanır. Android arka plan görev sıklığı değişmedi.

Listeyi güncelle, aktif yönde MORE varsa kaldığı yerden; aksi halde ilk sayfadan kullanıcı isteğiyle en fazla altı sayfa okur. Her istek aynı RequestGate tek etkin okuma ve en az 15 saniyelik kalıcı alt aralığına tabidir. İlk 90 saniyeden sonra yeni sayfa başlatılmaz; devam eden istek kendi taşıma zaman aşımını kullanır. Her sayfa hemen yazılır. Bir başka manuel/arka plan okuması mevcut run'ı değiştirirse eski sonuç commitCurrent karşılaştırmasından geçemez. LIMITED/UNKNOWN/STALLED, ilk hata, iptal, oturum/hedef değişimi veya kayıt temizliği devamı durdurur. Liste işlemleri menüsünde tek sayfa denemeleri korunur.

Yeni profil önbelleği gözlem süresini uzatmaz veya bildirim üretmez. Okuma başlamadan hemen önce kayıtlı/önbellekteki daha yeni taze profil tercih edilir; yeniden açılış ve araya giren başarılı okuma ek HTTP isteği gerektirmeyebilir. Ortak ReadRuntime yalnız aynı saat/iptal edilebilir bekleme sözleşmesini paylaşır; arka plan seçim/bütçe kuralları değiştirilmedi.

Genel manuel mod veya henüz temizlenmemiş eski 429 kaydı varsa, yerel mola dolduktan sonraki açık kullanıcı denemesi yalnız bir sayfa alır; devam sayfasına otomatik geçilmez. Bu sayfa başarısı önceki kısıtlama kaydını temizlemez.
