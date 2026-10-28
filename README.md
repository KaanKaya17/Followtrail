# InstagramTracker

Yerel Android uygulaması: kayıtlı profil ve liste gözlemlerini karşılaştırır. İki okumada yeni görülen/görünmeyen hesapları ve sayısal/profil farklarını gösterir; kesin takipten çıkış tespiti veya kesin olay saati vaat etmez.

**Windows 0.2.7:** [Masaüstü kullanım ve geliştirme](interface/DESKTOP.md). Windows ve web önizlemesinde Türkçe/İngilizce dil seçimi eklendi; çeviriler yereldir, kullanıcı içerikleri değişmez. Gizli/küçültülmüş pencerede ara sonuçlar arayüze gönderilmez; açılışta son durum tek seferde eşlenir. Kontrol aralığı her profile ayrı uygulanır; 3 profil ve 5 dakika seçimi yaklaşık 5 dakikalık kişi başına kontrol hedefidir. Hesaba/değişiklik türüne göre tercihler, kontrol başına tek bildirim ve eski/yeni değerleri gösteren **Bildirimler** kaydı eklendi. İsteğe bağlı kaybolan kayıtlı takipçi incelemesi ve kanıtlarıyla **Engellenme şüphesi** bildirimi eklendi; kesin engelleme tespiti değildir. 1/5 dakikalık kısa kontrol seçenekleri ve pencereyi kapatınca sistem tepsisinde çalışma eklendi. Hesap bağlantısı/kayıtlı oturum, profil gözlemleri, kişiler, favori/not/grup, takipçi ve takip edilen listesi taramaları, kapsamı belli karşılaştırmalar, grafik/zaman çizelgesi, şifreli yedek ve CSV aktarımı yerel masaüstü arayüzüne bağlandı. 27 Ekim canlı testinde mevcut Windows oturumu doğrulandı; kendi profil ve iki liste yönü HTTP 200 ile geldi. Bu hesapta her liste tek sayfaydı; çok sayfalı devam çevrimdışı test edildi. Yeni parola/2FA girişi bu sürümde canlı yeniden denenmedi. Windows 0.2.7 ve Android 0.1.31, başlangıçta kapalı **Profil zaman tüneli** ekler: kelime bazında biyografi farkı ve şifreli yerel avatar galerisi. Arşiv profil başına açılır; dönen sayaç geçmişinden bağımsızdır. Web önizlemesinde örnek kayıtlar gösterilir.

**0.1.30 ortak arayüz önizlemesi:** [Web arayüzü ve eski Windows tasarım önizlemesi](interface/README.md) Özet, Kişiler, Listeler, Zaman çizelgesi ve Ayarlar ekranlarıyla yerel olarak açılır. Tarayıcıdaki önizleme hâlâ örnek veri kullanır; Windows 0.2.7 ayrı gerçek işlev paketidir. Telefonun görsel dili aynı palet ve ortak tema kaynağına uyarlandı. Android'in mevcut işlevleri ayrıca korunur; aşağıdaki eski sürüm canlı testleri yeni web/masaüstü başarısı olarak yorumlanmaz.

Geliştirme önizlemesi **0.1.26**. Türkçe/English, görünür uygulamada 60 saniyelik hızlı kontrol hedefi ve saatli işlem geçmişi. Açık tema ve canlı renkler; içeriği örtmeyen alt gezinme; kartlarda düzenlenmiş geçmiş, bildirimler ve notlar. Kayıt zamanı, liste kapsamı, arama sonucu ve çevrimdışı görüntüleme ayrı gösterilir. Profil geçmişinde kullanıcı adı, yanıtta varsa biyografi ve karşılaştırılabilir fotoğraf bilgisi mevcut profil yanıtından alınır; görünür profil fotoğrafları mevcut bağlantıdan oturum bilgisi gönderilmeden yüklenip özel cihaz önbelleğinde tutulabilir. İsteğe bağlı liste otomasyonu ayrıca aşağıda açıklanır. Eski oturum ve kayıtlar korunur. Önceki çalışan 0.1.22 kaynak/APK yedeği ve Git etiketi korunur. [Arayüzü inceleme](docs/UI-TEST.md) · [Doğrulama kaydı](docs/VALIDATION.md).

25 Ekim fiziksel telefon testinde kullanıcı, mobil bağlantıyla kendi sayaçlarının, kendi takip edilen listesinin ve arkadaşının ilk takipçi sayfasının geldiğini bildirdi. Bu canlı sonuç 0.1.8'e aittir; yeni arayüz kontrolleri ayrı debug paketi ve sentetik veri kullanır. Çok sayfalı canlı devam ve uzun süreli oturum henüz sınanmadı. [Liste testi](docs/FOLLOW-LISTS-TEST.md).

## Kullanım

Son incelenen profiller 15 dakika boyunca, en fazla 20 kayıtla süreç belleğinde yeniden kullanılabilir; geri açılış tazelik süresini uzatmaz. Normal kısa beklemeler iptal edilebilir sıraya alınır. 429/doğrulama molaları otomatik devam ettirilmez. Kayıtlı/geçici profilde ortak özet; Değişiklikleri izle ile açık kayıt/izleme eylemi ve Yalnız kişilere ekle seçeneği bulunur. Ana sayfada son değişiklikler/favoriler önde, otomasyon kısa durum kartındadır.

- **Profil bilgileri:** kayıtlı kişinin analizinde biyografi ve Profil geçmişi. Kullanıcı adı/biyografi değişimlerinde eski-yeni değerler, fotoğrafta dönen kimlik/varsayılan fotoğraf durumu karşılaştırılır. Yalnız URL farkıyla bildirim üretilmez; bildirim görsel benzerliği analizi yapmaz; ekranda gösterilen küçük görsel önbelleği ayrı tutulur. Eksik alan silinmiş sayılmaz, ilk bilinen değer başlangıçtır. Açık izleme seçilen 15/30 dakika veya 1/6/12/24 saat hedefiyle bu değişimleri de bildirebilir. [Test adımları](docs/PROFILE-CHANGES-TEST.md).

- **Son değişiklikler:** ana sayfada kayıtlı kişilerin sayı farkları ve mevcut liste sayfaları, okuma zamanına göre gösterilir. İlk üç kayıt ve Tümünü gör ile en fazla 20 kayıt; analiz/liste açılışı yeni istek göndermez. Akış saklanan geçmişe dayanır, kesin takip/çıkış zamanı değildir.
- **Favoriler:** kişi kartı veya analizde yıldız. Favoriler kişi listesinin başında görünür; bayrak hesap/kişi kimliğine bağlı olarak şifreli saklanır. Açıp kapatmak okuma veya bildirim kontrolü başlatmaz. İzleme önceliği bu sürümde değişmez.

**Hesap bağla → Telefondan bağlan · Bilgisayar gerekmez** yeni bağlantı seçeneğidir. Kullanıcı adı/e-posta, parola ve gerekirse doğrulama koduyla yalnız telefonda giriş yapılır. Parola bu cihazda bellekte işlenir; dosyaya/loga yazılmaz. Başarılı oturum cihazda şifrelenir. Instagram kabulü bu sürüm için canlı hesapla henüz doğrulanmadı. [Telefon giriş testi](docs/PHONE-LOGIN-TEST.md). Windows aktarımı ve önceki web girişi ayrıca korunur; bilgisayar bu yeni yol için gerekmez. Instagram giriş/doğrulama veya kısıtlama uygulayabilir. Kişisel hesap uçları resmî geliştirici API sözleşmesi değildir ve değişebilir.

- **Dil:** sağ üstteki Seçenekler → Dil / Language üzerinden Türkçe veya English seçilebilir. Tercih yeniden açılışta korunur. Ekranlar, yeni bildirimler, tarih ve sayı biçimi seçilen dili kullanır; profil içerikleri ve özel notlar çevrilmez.
- **Özet:** hesap, iki sayaç, son kayıt zamanı ve tek Yenile düğmesi. Son 15 dakikadaki kayıt tekrar kullanılır. Açılışta otomatik yenileme varsayılan kapalı; Seçenekler → Yenileme ayarları ile açılabilir. Kayıt en az 15 dakika eski olmalı ve sınırlama bulunmamalıdır.
- **Kişiler:** kullanıcı adı veya HTTPS Instagram profil bağlantısıyla kişi ekle. İlk sorgu tek profil isteğidir. Sonraki sorgular sabit hesap kimliğine göre yapılır; kullanıcı adı değişmesi başka kişinin geçmişiyle karışmaz. Liste ve analiz açılışı yeni profil/liste API sorgusu yapmaz; görünür fotoğraflar için ayrı CDN yüklemesi olabilir. Kişi kartındaki çöp kutusu ve analiz altındaki Kişiyi kaldır aynı onaylı silme akışını kullanır.
- **Analiz:** takipçi, takip edilen, varsa gönderi sayısı; önceki gözleme göre net fark; 7/30 gün veya tüm kayıtlar; zaman eksenli grafik; son 10 gözlemin metin dökümü; özel not ve grup. Eksik gönderi sayısı sıfır yapılmaz. Geçmiş ilk gözlemden başlar; gelen/giden kişilerin kimliği çıkarılamaz.
- **Listeler:** üyeye dokunmak uygulama içindeki profilini açar; Instagram’a geçiş ayrı Instagram’da aç düğmesidir. Arama sonucunda doğru üye açılır, kişi otomatik eklenmez. Taze profil kaydı varsa kullanılır; eski veya kayıtsız profilde mevcut aralık/mola izin verirse ilk açılışta tek profil okuması yapılabilir. Kendi hesabının ve kayıtlı kişilerin listeleri ayrı saklanır. Açılış/sekme değişimi istek göndermez. Listeyi güncelle aktif yöndeki devam sayfalarını kontrollü alır; tek sayfa/baştan okuma Liste işlemleri menüsündedir; otomasyon açıksa sayfalar ortak kuyrukta ilerler. Liste ekranında Kaydın kapsamı → Önceki okumayla farklar, iki uygun okuma arasındaki yeni görülen/görünmeyen hesapları gösterir. Eksik listeden kesin takipten çıkış sonucu çıkarılmaz.
- **Bildirim:** kişi analizindeki Takip durumu kartında izlemeyi aç; en az 15/30 dakika veya 1/6/12/24 saat aralığı seç. Takipçi/takip edilen sayıları, kullanıcı adı, yanıtta varsa biyografi ve karşılaştırılabilir fotoğraf bilgisindeki değişimler için yerel Android bildirimi gelir. İlk kayıt ve yalnız gönderi değişimi bildirim üretmez. Uygulama ekranı kapalıyken de JobScheduler çalışabilir; anlık bildirim değildir. Pil tasarrufu, Doze, üretici ayarları ve çok sayıda izlenen kişi gecikme yaratır. Zorla durdurma sonrası uygulamayı yeniden açmak gerekir.
- Bu önizlemede cihaz kaydını sınırlamak için en fazla **100 kişi**, kişi başına son **90 başarılı gözlem** (90 gün değildir), 500 karakter not ve 40 karakter grup saklanır. Profil/kişi geçmişi toplam 2 MiB bütçesini paylaşır; dolarsa önceki kayıt korunur. Liste kayıtları ayrı 512 KiB bütçesindedir. Bunlar Instagram'ın güvenli istek limitleri değildir.
- Tek kişiyi kaldırma o kişinin geçmiş ve notunu siler. **Bağlantıyı kaldır** bu uygulamadaki oturumu ve bütün kişileri siler; diğer Instagram oturumlarını kapatmaz.

- **Takip durumu:** son başarılı profil okuması, seçilen aralık, sıradaki kontrol için en erken hedef ve bekleme nedeni. İzin, bağlantı, Instagram molası, ortak bütçe ve kişi sırası ayrı gösterilir. **Durumu güncelle** yalnız cihaz kaydını/sistem durumunu okur ve mevcut işi eşler; Instagram isteği göndermez. Ekran kendiliğinden canlı güncellenmez. [Kontrol ve bildirim testleri](docs/WATCH-STATUS-TEST.md).

## Tek bakışta kontrol durumu

Kendi hesabında, kişi kartlarında ve kişi analizinde üç satırlık özet: **Son kontrol**, **Veri kapsamı**, **Şu anki durum**. Ayrıntı için bu alana dokun: son başarılı profil okuması, mevcut bekleme nedeni, iki listenin ayrı okuma zamanı/kapsamı ve varsa son hata görünür. Yarım liste için Liste devamı bekliyor yazması o anda ağ isteği çalıştığı anlamına gelmez. İki liste sonunun gözlenmesi Instagram sonuçlarının eksiksizliğini kanıtlamaz. Güncelleme sırası ve profil karşılaştırma sonucu ayrı gösterilir.

Liste kapsamı otomasyon kapalıyken de kayıtlı veriden gösterilir; kısmi sayfa ile iki listenin sonunun gözlenmesi ayrıdır.

Bu alan ekran görünürken yaklaşık 30 saniyede yalnız cihazdaki kayıtları tekrar okur. Yeni Instagram sorgusu veya otomasyon ayarı oluşturmaz. Güncel durum açıldığında ağ ve Android işi değişmiş olabilir; saatler/yerel hedefler kesin çalışma sözü değildir. [Test adımları](docs/PROFILE-STATUS-TEST.md).

## Gözlem geçmişinin kapsamı

Grafikte 7/30 gün filtresi bir seçim penceresidir; o kadar veri bulunduğu anlamına gelmez. Filtrelenen gerçek kayıt sayısı, ilk/son tarih ve aradaki gün/saat/dakika gösterilir. Tek kayıttan bir zaman aralığı veya değişim üretilmez. Son 90 kayıt yoğun kontrolde yaklaşık 89 dakikayı bile kapsayabilir; çizgi aradaki bütün olayları göstermez. Kişiler ilk 20 kartla açılır, Daha fazla kişi göster ile 20’şer genişler; favoriler öndedir ve bu işlem ağ isteği göndermez.

## Hızlı izleme ve işlem geçmişi

Kendi hesap özetindeki veya kayıtlı kişi analizindeki **Hızlı izle**, görünür uygulamada tek hedef için **15 dakika boyunca 60 saniyelik profil kontrolü hedefi** başlatır. Mobil bağlantı ve bildirim izni gerekir; yeni giriş gerekmez. Sayı değişirse ilgili liste sıraya girer. Liste istekleri ve profil kontrolü aynı altı istek/90 saniye bütçesini paylaşır; büyük liste varken sonraki kontrol gecikebilir. 429/oturum molası aşılmaz ve ilk hatada hızlı oturum durur. Instagram için sorunsuz bir dakika kotası doğrulanmış değildir. Ekranda 60 saniyenin en erken hedef olduğu, ortak istek sırasında gecikme ve ekran görünmezken duraklama açıkça anlatılır.

Ekrandan ayrılınca hızlı okuma iptal edilir; oturum süresi uzamaz. Seçilen kişinin profil aralığı 15 dakika olur ve kendi/kişi liste otomasyonu da açılır. Hızlı oturum bitince normal arka plan hedefiyle devam eder; otomatik izlemenin tamamını kapatmak için profil izlemesini ve liste otomasyonunu ayrı kapatabilirsin. Android'in 15 dakikalık hedefi kesin çalışma veya bildirim süresi değildir.

**⋯ → İşlem geçmişi** bütün hesapların son 120 yerel işlemini gösterir; kişi analizindeki **Zaman çizelgesi** o hesabı filtreler. Kayda dokunarak işlem başlangıcı, veri alımı, karşılaştırma kaydı, önceki karşılaştırılabilir gözlem ve liste farkı örneklerini gör. Bunlar cihazın gözlem saatleridir; Instagram'daki kesin değişim saati değildir. Önceki sürüm kayıtlarına eksik saatler eklenmez. [Test adımları](docs/QUICK-WATCH-TEST.md).

## Liste otomasyonu

Özet → kısa izleme kartındaki **⋯ → Otomasyonu ayarla**. Kendi hesabını ve izlenecek kayıtlı kişileri seçip **Başlat** de. Bir kez açıldıktan sonra İlk sayfa / Sonraki sayfa düğmelerine basmak gerekmez. Mobil bağlantı ve açık bildirim izni gerekir. Eski sürüm güncellemesinde varsayılan kapalıdır; mevcut kişi profil ayarları korunur.

- Kendi hesabın seçiliyse profili 15 dakikalık hedefle sıraya girer; seçili kişilerin profil bildirimleri 15 dakikalık aralıkla açılır. Güncelleme tek başına mevcut kişi aralıklarını değiştirmez.
- Takipçi/takip edilen sayfaları sırayla okunur; cursor ve rezervasyon cihazda kalır. Normal uygulama kapanışı sonrası sonraki iş kaldığı yerden devam edebilir. Zorla durdurma sonrası Android uygulamanın yeniden açılmasını gerektirebilir.
- Profil ve liste turları ortak en az 15 dakika aralığıyla başlar. İlk uygun profil kontrolünden sonra işler dönüşümlü ilerler; bir tur birden fazla kişinin profilini de okuyabilir. Profil ve liste okumaları birlikte en fazla 6 istek kullanır; istek başlangıçları arasında en az 15 saniye vardır. İlk 90 saniyeden sonra yeni istek başlatılmaz; devam eden istek kendi zaman aşımını kullanır. Büyük/çok listelerde tarama saatler veya günler sürebilir; 15 dakika her kişinin tam listesinin yenilenmesi değildir.
- Liste sonu gözlendikten sonra yeni tarama 24 saat sonrasına hedeflenir. Daha yeni profil gözleminde ilgili sayı değişmişse o liste erken sıraya girer; sayı aynı kaldığında günlük tarama korunur. İlk okuma başlangıçtır; iki ayrı okumada da liste sonu gözlendiyse fark bildirimi üretilebilir. Bu, Instagram sonuçlarının eksiksiz/aynı anda alındığını kanıtlamaz. Bildirimler kesin takip/takipten çıkış iddiası değildir.
- **Otomasyon durumu** ilerlemeyi/hataları gösterir; satırdan kayıtlı liste açılır. **Yenile** yalnız yerel durumu günceller. **Nasıl çalışır?** molayı/kapsamı açıklar ve onaylı liste temizliğini içerir. Liste otomasyonunu durdurmak kişilerin ayrı profil bildirimlerini kapatmaz.
- Önceki/güncel liste okumaları en fazla 101 hedef için aynı şifreli 512 KiB bütçeyi paylaşır. Dolarsa önceki kayıt korunur; kaydedilemeyen yönde tarama duraklar. Gerekirse onaylı **Liste kayıtlarını temizle** ile başlangıçları kaldırabilirsin; bu işlem Instagram 429 molasını sıfırlamaz.

[Test adımları ve sınırlar](docs/AUTOMATION-TEST.md).

## Bağlantı seçenekleri

### Yalnız telefonda bağlantı (0.1.23 önizleme)

**Hesap bağla → Telefondan bağlan · Bilgisayar gerekmez**. Kullanıcı adı/e-posta ve parolayı gir; Instagram isterse bir sonraki ekranda kodu tamamla. SMS, doğrulama uygulaması ve yedek kod yöntemleri için ayrı seçim vardır. Desteklenmeyen ek doğrulama adımı otomatik aşılmaz; kullanıcı Instagram’da kontrol eder.

Bu, Windows’ta kullanılan sabitlenmiş instagrapi 3.2.0 CAA giriş sözleşmesinin yerel Android uyarlamasıdır; resmî OAuth/API izni değildir. İlk kurulum birkaç dakika sürebilir: adımlar arasında mevcut en az 15 saniye aralığı korunur. Bir giriş denemesi en çok 10 dakika/18 istekle sınırlıdır. Yeni parola ile giriş denemeleri en az 15 dakika aralıklıdır; 429 ve sunucu beklemesi daha uzun olabilir. Giriş ekranı açıkken otomasyon bekler. İstek sürerken arka plana geçmek iptal eder; kod beklerken başka uygulamaya gidip dönülebilir. Ekran döndürme devam eden denemeyi bellekte korur; süreç kapanması yeni deneme gerektirir.

Hesap yalnız sahibi doğrulanmış oturumdan başarılı profil okumasından sonra bağlanır. Aynı hesabın kişi/geçmiş kayıtları korunur; hata veya iptal eski hesabı değiştirmez. Instagram oturumu iptal ederse yeniden giriş gerekir; kesin bitiş tarihi bilinmez. Oturum, cihaz kimliği ve imza anahtarı Keystore ile şifrelenir; parola/kod saklanmaz. Instagram’ın telefonda açık oturumu başka uygulamadan alınmaz. Canlı Instagram’ın bu yolu kabulü henüz sınanmadı; emülatör testleri sentetik yanıtlardır. [Akış ve testler](docs/PHONE-LOGIN-TEST.md).

### Windows'taki mobil bağlantıyı APK'ya taşıma

**Hesap bağla → Bilgisayardan bağlan**, Windows prototipinde kurulmuş oturumu Android'e taşır. Bağlantı yönetimi, gizlilik, yenileme tercihleri ve istek bilgileri sağ üstteki **⋯** menüsündedir. İlk eşleştirme bilgisayar/ADB gerektirir; başarılı okumadan sonra APK bağımsız çalışır. Bu önceki yöntem alternatif olarak korunur; telefondan yeni giriş yukarıdaki ayrı seçenektir.

1. APK'da **Bağlantıyı hazırla**. Cihaza özel anahtar ve 10 dakikalık aktarım isteği oluşturulur.
2. Aynı Windows kullanıcısında `scripts/link-desktop.ps1` çalıştır. Önceden çalışan `InstagramCounter` ortamı ve DPAPI oturumu gerekir. Script oturumu yalnız bellekte açar, bu cihaza özel anahtarla şifreler ve ADB'den aktarır. Giriş/profil isteği, sunucu, parola/kod kopyalama yoktur. Birden fazla cihaz varsa `-Serial`; debug paket için `-DebugApp` kullanılır.
3. APK'da **Aktarımı al**. Bu adım Instagram'a bağlanmaz ve mevcut hesabı değiştirmez. Aktarım dosyası ve tek kullanımlık anahtar temizlenir; oturum Keystore ile şifreli kasaya alınır.
4. Yerel mola dolunca **Bağlan**. Yeni oturumdan kimliği doğrulanmış başarılı yanıt gelirse ana hesap bağlanır; aynı hesabın kişi/geçmiş kayıtları korunur. Özet, kişiler ve arka plan kontrolleri bu oturumu kullanır. İlk bağlantı denemeleri kalıcı en az 15 dakika aralıklıdır.

Aktarım ekranı açıkken otomatik kontroller bekler. Süreç kapanırsa bu ekranı yeniden açıp **Geri** ile bekletmeyi kaldır. Aktarım manuel moda alır; başarısız deneme sonrası eski bağlantıdan gizli yenileme yapılmaz. Başarılı açık okuma normal önbellekli akışı yeniden açar. [Sözleşme ve sınırlar](docs/MOBILE-PORT.md).

### Önceki web deneyi

**⋯ → Gelişmiş → Web bağlantı deneyi**, eski 429 kaydını koruyarak farklı bir erişim hipotezini sınar: giriş ve tek web profil okuması aynı WebView oturumunda kalır. Giriş/doğrulama sonrası kullanıcı adını girip **Tek okumayı başlat** seçilir. Sayfa açma ve profil okuma otomatik başlamaz; her deneme tek açık okuma ve en az 15 dakika aralığıyla sınırlıdır. Sonuç ana hesaba/izlenen kişilere aktarılmaz ve arka plan bildirimlerine bağlanmaz. Çalışacağı veya 429'u kaldıracağı garanti edilmez.

Prototip açıkken otomatik kontroller duraklar. **Denemeyi bitir** tarayıcı verilerini temizleyip bu bekletmeyi kaldırır; önceki 429'u kaldırmaz. Süreç kapanıp bekletme kalırsa prototipi yeniden açıp bitir. Debug demoda **Bağlantı prototipi · çevrimdışı** aynı JS/WebView akışını sentetik yanıtta sınar. [REA bulguları, yöntem ve sınırlar](docs/CONNECTION-PROTOTYPE.md).

## İstekler ve sınırlama

Bütün profil okumaları aynı kalıcı en az 15 saniye aralığını ve tek etkin bağlantıyı kullanır. Normal gezinmede kayıtlı aynı profilin 15 dakika içindeki başarılı gözlemi yeni istek göndermeden kullanılır; açık Hızlı izle oturumu seçilen hedefi 60 saniyelik tazelik hedefiyle kontrol eder. Bu süreler Instagram güvenli kota garantisi değildir. [İstek politikası, 429 dayanakları ve test sınırı](docs/REQUEST-POLICY.md). Otomatik okumalar ortak 15 dakikalık bütçeyi paylaşır; her arka plan çalışmasında toplam en fazla 6 profil/liste isteği yapılır (en az 15 saniye aralıklı, 90 saniyelik yeni istek başlatma bütçesi). Çok kişi açıkken herkesin saatte bir kontrol edilmesi beklenmemelidir. Kişi bazındaki süre başarısız istekten önce de ayrılır. Sınırsız tarama, farklı uçlara tekrar deneme veya otomatik yeniden giriş yoktur. İlk hata veya sınırlı/belirsiz sayfa liste turunu durdurur. Düğmeler istek sırasında kapanır; ekranın eski isteği iptal edilirse geç yanıt kaydedilmez.

Bildirim izni/kanalı kapalıysa arka plan sorgusu yapılmaz. Oturum veya doğrulama hatasında otomatik çalışma yeniden bağlantıya kadar durur. İzlemeyi kapatmak, kişiyi silmek veya bağlantıyı kaldırmak ilgili işi/bildirimi iptal eder. Bildirim ayrıntıları kilit ekranında gizli olarak işaretlenir; son görünüm Android ve kullanıcının sistem ayarlarına bağlıdır. Bildirim yalnız iki gözlem arasındaki net farkı gösterir; aradaki bütün hareketleri göstermez.

Instagram 429 veya tanınan bekleme yanıtı verirse otomatik okuma kalıcı olarak durur. İlk yerel mola en az 1 saat; tekrarlarında 2/4/8/16/24 saate çıkar. Sunucunun Retry-After değeri daha uzunsa korunur. Bu saat Instagram kısıtlamasının kalkış saati değildir. Mola bitince kendiliğinden deneme yapılmaz; başarılı bir açık kullanıcı yenilemesi normal önbellekli akışı açar. Önceki sürümde kaydedilen sınırlama da otomatik okumayı durdurur.

Ekran kaydedilmiş son sınırlama yanıtının zamanını ve yerel molayı ayırır. Eski uyarının görünmesi yeni bir Instagram isteğinin reddedildiğini tek başına göstermez. Gerçek sınırlamanın nedeni ve süresi bu uygulamadan kesin belirlenemez.

## Bilgisayarda aynı Android kodunu deneme

Önceden kurulu Android SDK/emülatör ve JDK 21 gerekir. Bu iş akışı yazılım kurmaz. Telefon sürümüyle aynı ana Kotlin ekranları, parser, istek kapısı ve şifreli kayıt kullanılır. Debug sürümündeki belirgin **DEMO · ÖRNEK VERİ** modu yalnız sentetik yanıt döndürür; gerçek hesaba bağlanmaz.

```powershell
.\gradlew.bat :app:assembleDebug
.\scripts\start-preview.ps1
```

Bu çalışma alanındaki TrackerQA AVD'si kullanılır. Başka bilgisayarda mevcut AVD'yi `-AvdName` ile seç. Emülatör zaten başsız çalışıyorsa görünür pencere için önce onu kapatıp script'i yeniden çalıştır. Debug sürümünde **⋯ → Çevrimdışı demoyu aç** da bulunur. Örnek iki kişinin geçmişi vardır; yeni kişi için `@efe.demo`, bulunamama senaryosu için `@bulunamadi.demo` denenebilir. Gerçek kullanıcı adı girilse de demo gerçek profil sorgulamaz. Demo bildirimleri de ayrı debug servisiyle sentetik taşıma kullanır; bildirimde DEMO etiketi bulunur.

Release APK farklı paket kimliği kullanır, demo taşımasını içermez ve `offline_preview` parametresini yok sayar. Demo başarısı canlı Instagram başarısı değildir. Gerçek hesap denemesi istenirse demo dışında ayrıca giriş gerekir; telefondaki oturum bilgisayara taşınmaz.

## Derleme ve doğrulama

Android 7.0+ / API 24; compile/target 36. JDK 21 ve build-tools 36.0.0 kullanılır. SDK'yı `ANDROID_HOME` veya izlenmeyen `local.properties` ile tanımla.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
node scripts/test-connection-probe.mjs
```

Windows: `gradlew.bat`. [Yayın hazırlama](docs/RELEASING.md), [katkı](CONTRIBUTING.md), [CI](https://github.com/KaanKaya17/InstagramTracker/actions/workflows/android.yml). Gradle dağıtımı SHA-256, action sürümleri commit SHA, bağımlılıklar lockfile ile sabittir. Test uygulaması `.debug` paketinde üretim verisinden ayrıdır. CI testleri gerçek Instagram oturumu kullanmaz.

## Gizlilik

Oturum, kişiler, geçmiş ve notlar AES-GCM / Android Keystore ile bu cihazda şifrelenir. Bulut yedekleme ve cihaz aktarımı kapalıdır. Kendi sunucusu, reklam, analitik, çökme toplama SDK'sı ve işlemciye özel native kütüphane yoktur. Canlı giriş/profil istekleri Instagram'a gider; verinin hiç ağdan çıkmadığı iddia edilmez. [Gizlilik ve sınırlar](docs/PRIVACY.md), [güvenlik bildirimi](SECURITY.md).

[Canva ilk arayüz taslağı](https://canva.link/g4rw1g4hoiufg4m) · [Sürümler](CHANGELOG.md)

Telefon girişinin son P adımı, istek başlangıç saati ve HTTP durumu **⋯ → İstek bilgileri** içinde yerel olarak korunur. Bu sürümden önceki P kodu kurtarılamaz. Parola/kod/ham yanıt tanıya yazılmaz. 429 molası güncellemeyle kaldırılmaz.
