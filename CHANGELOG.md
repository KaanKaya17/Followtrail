# Değişiklikler

## 0.1.23 — 2026-10-26 (bilgisayarsız telefon girişi önizlemesi)

- Hesap bağla menüsünde ilk seçenek Telefondan bağlan: yerel kullanıcı adı/e-posta, parola ve gerektiğinde kod adımı. Bilgisayar/USB/oturum dosyası gerektirmez. Önceki çalışan bilgisayar aktarımı ve web seçeneği korunur.
- Windows prototipinde kullanılan instagrapi 3.2.0 CAA sözleşmesinin Android uyarlaması; şifreli parola hazırlama, kalıcı cihaz/imza kimliği, SMS/doğrulama uygulaması/yedek kod akışları. Instagram’ın bu uyarlamayı canlı kabulü henüz doğrulanmadı; resmî API/OAuth desteği iddiası yoktur.
- Bağlantı yalnız kimliği doğrulanmış oturumdan başarılı profil okumasıyla kaydedilir. Aynı hesabın kişiler/geçmişi korunur; başarısız/iptal giriş mevcut hesabı değiştirmez. Parola/kod saklanmaz; oturum ve cihaz kimliği Keystore ile şifrelenir. Giriş ekranı ekran görüntüsüne kapalıdır.
- Ekran döndürmede devam eden giriş bellekte korunur. İstek sırasında arka plana geçmek iptal eder; kod beklerken diğer uygulamaya gidilebilir. Ortak istek aralığı, 429/sunucu beklemesi ve yeni giriş molası korunur; giriş sırasında otomasyon bekler. Otomatik yeniden giriş yoktur.
- Türkçe/English yardım, gizlilik ve bağlantı yönlendirmeleri güncellendi. Yeni Android izni, servis veya bağımlılık eklenmedi. 0.1.22 kaynak/APK yedeği korunur. [Test adımları](docs/PHONE-LOGIN-TEST.md).

## 0.1.22 — 2026-10-26 (tek bakışta kontrol durumu)

- Kendi hesap özetinde, kişi kartlarında ve kişi analizinde dokunulabilir kontrol durumu: son başarılı profil okuması, kısa bekleme nedeni ve varsa liste ilerlemesi. İlk dakika Az önce, ilk saat dakika cinsinden; daha eski veya ileri cihaz saatli kayıtlar tam tarih/saatle gösterilir.
- Hızlı kontrol, planlanan/sıradaki kontrol, son karşılaştırmada fark olmaması, hata, bildirim izni, bağlantı, çevrimdışı ve Instagram molası ayrı gösterilir. Bir başka profilin hızlı oturumu sürerken normal otomatik kontrollerin beklediği de belirtilir. Kalıcı yarım liste, aktif ağ okuması diye sunulmaz.
- Dokununca başarılı profil saati, en erken yerel hedef, iki listenin ayrı kapsamı/alınan hesap sayısı/son sayfa zamanı ve varsa son hata açılır. Zaman çizelgesi ve Listeleri incele düğmeleri mevcut ekranları açar. Tamamlanmış listeler yeni sayısal değişim veya günlük süre yüzünden tekrar uygunsa Liste güncellemesi sırada görünür.
- Özet ekran görünürken yaklaşık 30 saniyede mevcut şifreli kayıtlardan güncellenir; yeni profil/liste isteği, bildirim, iş rezervasyonu veya kalıcı veri üretmez. Ekran yeniden kurulmadan yalnız metinler güncellenir; arka planda bu arayüz döngüsü durur. Mevcut otomasyon, istek molası ve bildirim/fark hesapları değişmedi. Türkçe/English desteklenir.

## 0.1.21 — 2026-10-26 (hızlı izleme ve işlem geçmişi)

- Kendi hesabında veya kayıtlı kişide Hızlı izle: uygulama görünürken, tek hedef için 15 dakikalık oturum boyunca 60 saniyelik profil tazeliği hedefi. Süre ekran döndürme/arka plana geçişle uzamaz. Ekran görünmezken hızlı oturum duraklar; normal Android işi devam edebilir. Hızlı izleme sona erince seçilen hedefin 15 dakikalık profil/liste otomasyonu açık kalır.
- Otomasyon ayarlamak seçilen kişileri 15 dakikaya alır; Hızlı izle yalnız seçilen kişiyi 15 dakikaya alır. Diğer mevcut kişi aralıkları güncelleme sırasında korunur. Normal arka plan işi en erken 15 dakikalık Android hedefiyle, aynı turda en fazla altı profil/liste isteği ve 90 saniyelik başlatma penceresi kullanır; istekler en az 15 saniye aralıklıdır. Android, büyük listeler ve ağ geciktirebilir; kesin süre veya Instagram güvenli kotası değildir.
- Profil sayısı değişirse ilgili liste günlük hedefi beklemeden sıraya girer. Sayısı değişmeyen tamamlanmış liste günlük yeniden taramaya kadar korunur; aynı sayıda farklı kişiler olabileceği için günlük tarama kaldırılmadı. Yarım okuma kaldığı yerden sürer. Eski listelerde karşılaştırma dayanağı yoksa bir başlangıç taraması gerekebilir.
- Üç nokta menüsünden İşlem geçmişi ve profil ekranından Zaman çizelgesi: hesap filtresi, kontrol sonucu, profil/liste farkı, sayfa/kapsam bilgisi, işlem başlangıcı, verinin alındığı ve karşılaştırmanın kaydedildiği saat. İki gözlem arasındaki fark gösterilir; Instagram'daki kesin olay saati uydurulmaz. Eksik alanlar için önceki bilinen karşılaştırma tarihi korunur.
- Son 120 işlem ayrı şifreli kayıtta tutulur; liste farkının ilk 10 yeni ve ilk 10 görünmeyen hesabı ayrıntılarda bulunur. Hata türleri kaydedilir; parola, oturum ve ham yanıt yazılmaz. Geçmiş yazımı başarısız olursa başarılı profil/liste kaydı korunur ve geçmiş ekranında uyarı gösterilir. Kişi/bağlantı kaldırma ilgili geçmişi de temizler.
- 429, doğrulama, manuel mola, tek etkin taşıma ve bildirim izinleri korunur. Hızlı izleme ilk hatada/belirsiz liste sonucunda durur; kendiliğinden yeniden giriş veya mola aşma yoktur. Türkçe/English ve mevcut geri dönüş/fotoğraf temizliği korunur. Yeni servis, izin veya bağımlılık eklenmedi.

## 0.1.20 — 2026-10-26 (Türkçe ve İngilizce)

- Ana ekrandaki üç nokta menüsüne Dil / Language eklendi. Türkçe veya English seçimi yeniden açılışta korunur; Android 13 ve üzerinde sistemin uygulama dili ayarıyla eşleşir. Eski sürümlerde tercih ayrı bir yerel ayarda saklanır.
- Ekranlar, erişilebilirlik etiketleri, açıklamalar, hata mesajları ve yeni bildirimler iki dilde sunulur. Tarih ve sayı biçimi seçilen dile uyar. Biyografi, kullanıcı adı, not ve grup gibi kayıtlı içerik çevrilmez.
- Dil için ekran yeniden oluşturulurken açılış yenilemesi başlatılmaz; kayıtlar, favoriler, izleme aralıkları ve istek molaları korunur. Dil paketleri APK içinde bulunur; ek indirme gerekmez.

## 0.1.19 — 2026-10-26 (favori hizalaması)

- Ana ekrandaki favori kartlarında kullanıcı adı, profil görselinin altında ortalandı.

## 0.1.18 — 2026-10-26 (daha sade ve hızlı kullanım)

- Son incelenen en fazla 20 profil için 15 dakikalık, oturuma bağlı bellek önbelleği. Geri dönüp yeniden açma yeni istek gerektirmez; okuma zamanı yenilenmez. Kayıtlı kişi/own profilinin daha yeni gözlemi tercih edilir. Kişiyi veya bağlantıyı kaldırmak ilgili önbelleği de temizler. Uygulama süreci kapanınca bu geçici önbellek kaybolur.
- Listelerde tek Listeyi güncelle işlemi: aktif yönde en fazla altı sayfa, istek başlangıçları en az 15 saniye aralıklı ve 90 saniyelik yeni istek bütçesi. Her sayfa hemen kaydedilip gösterilir; arama/konum korunur. Hata, kapsam sınırlaması, iptal veya oturum/kayıt değişimi devamı durdurur. Tek sayfa/baştan okuma ve karşılaştırma Liste işlemleri menüsünde kalır.
- Normal kısa istek aralığında profil/liste yenilemeyi iptal edilebilir kuyruğa alma; saniyelik açıklama yerinde güncellenir. Ekrandan ayrılınca kuyruk iptal olur. 429, doğrulama, bağlantı veya diğer genel duraklamalar otomatik devam ettirilmez; uzayan kısa bekleme de 30 saniyede sonlandırılır.
- Kayıtlı ve geçici profilde ortak sayı/biyografi/son kayıt görünümü. Değişiklikleri izle, açık eylemle kişiyi kaydedip bildirim izni varsa profil izlemesini açar; yalnız görüntülemek izleme başlatmaz. Yalnız kişilere ekle ayrı seçenek olarak korunur.
- Ana ekranda son değişiklikler ve favorilere hızlı geçiş öne taşındı; otomasyon yönetimi kısa durum kartı ve menüye alındı. Fotoğraf temizliğinin arka planda yapılması ve çalışan bildirim/oturum/istek sınırlaması kuralları korunur.

## 0.1.17 — 2026-10-26 (uygulama içi profil ve geri dönüş düzeltmesi)

- Liste üyesine dokunmak uygulama içindeki profil ekranını açar. Liste kaydındaki kimlik/fotoğraf/ad gösterilir; taze kayıt varsa tekrar sorgulanmaz. İlk profil okuması mevcut istek/mola kuralları izin verirse yapılır; izin vermiyorsa kullanıcı Profili getir ile devam edebilir. Geri dönüş ve ekran döndürme otomatik tekrar başlatmaz. Instagram’da aç ayrı düğmedir.
- Kayıtlı olmayan kişi otomatik eklenmez veya izlemeye alınmaz. Kişilere ekle açık eylemdir; kayıtlı kişinin listeleri buradan açılabilir. Geçici profil ayrıntıları ekran ömründe tutulur, eski kişi/hesap kayıt biçimi değiştirilmez.
- Gerçek Android 13 telefon kaydında takipçi listesinden çıkarken fotoğraf istemcisinin TLS bağlantı havuzunu ana iş parçacığında kapatmasının NetworkOnMainThreadException ürettiği doğrulandı. Görsel iptali/istemci temizliği ortak arka plan sahibine taşındı; ekran kapanışı önce teslimi durdurur. Düzeltme aynı görsel sahibini kullanan bütün ekranları kapsar.

## 0.1.16 — 2026-10-25 (kişi silme, profil bağlantıları ve fotoğraflar)

- Kişi kartlarında erişilebilir silme düğmesi; mevcut analiz ekranıyla aynı onaylı kaldırma akışı. Yalnız seçilen kişinin profil geçmişi, favori/not/izleme ayarı, otomasyon seçimi, bildirimleri ve liste kayıtları kaldırılır; Instagram hesabında işlem yapılmaz.
- Takipçi/takip edilen listesinde üyeye dokunarak yanıttaki kullanıcı adıyla Instagram profilini uygulama veya tarayıcıda açma. Aranmış sonuçta doğru üye açılır; bu işlem uygulamanın kişi listesine ekleme veya profil sorgusu yapmaz. Kullanıcı adı bilinmiyorsa bağlantı uydurulmaz.
- Kendi hesap özeti, kişi kartı/analizi ve liste üyelerinde yanıttaki fotoğraf bağlantısından küçük görsel. Görünür alanlarda iki eşzamanlı yükleme, sınırlı kuyruk, Activity/geri dönüş/yeniden kullanılan satır için iptal. Profil/liste okuyucusundan ayrı ve oturum/çerez göndermeyen HTTPS CDN taşıması; bağlantı veya görsel hatasında harfli simge kalır.
- Özel cihaz önbelleğinde en çok 8 MiB/128 küçük görsel ve ekran başına 2 MiB bellek önbelleği. Fotoğraf kimliği/adresi değişirse ayrı kayıt; kişi kaldırmada ilgili önbellek, bağlantı kaldırmada bütün görseller temizlenir. Görsel önbelleği ayrıca şifreli değildir ve işletim sistemi tarafından temizlenebilir. Hesap/liste kayıt biçimi, kontrol aralıkları ve profil değişikliği bildirimi hesabı değişmedi.
- Değişikliklerden önce çalışan 0.1.15 GitHub'a push/etiket ve ayrı kaynak/APK yedeğiyle korundu. Yeni fotoğraf akışının canlı CDN/telefon davranışı kullanıcı testine açıktır.

## 0.1.15 — 2026-10-25 (ardışık liste sayfaları ve diyaloglar)

- Aynı hedefin takipçi/takip edilen sayfaları bir otomatik turda dönüşümlü ilerler: en fazla 6 sayfa, istek başlangıçları arasında mevcut en az 15 saniye. İlk 90 saniye yeni istek başlatma bütçesidir; tur başlangıçlarının ortak 15 dakika aralığı korunur. Her sayfa kalıcıdır; kalan cursor sonraki turda sürer.
- İlk hata, sınırlı/belirsiz/takılmış sayfalama turu durdurur. İptal, seçim/oturum değişimi, manuel yeniden okuma ve kayıt temizleme sonraki sayfanın eski kaydı ezmesine izin vermez. Liste sonu sonrası 24 saat hedefi ve mevcut 429 molaları korunur.
- Bütün native diyaloglarda yumuşak mor yüzey, yuvarlatılmış köşeler, temaya uygun yazı/seçim/düğmeler. Kısa Başlat düğmesi; otomasyon durumunda yerel Yenile; liste temizleme işlemi açıklama ve ayrı onayın arkasına taşındı. Yeni izin, bağımlılık veya kayıt biçimi yok.
- Önceki 0.1.14 kaynak ve imzalı APK ayrı yedek/etikette korundu. Canlı Instagram çok sayfalı okuma ve uzun süreli Android pil/Doze davranışı kullanıcı testine açık.

## 0.1.14 — 2026-10-25 (kendi hesabı ve seçili kişilerde liste otomasyonu)

- Özet ekranından bir kez seçilen hedeflerde takipçi/takip edilen sayfalarını arka planda sırayla alma. Kaldığı cursor, rezervasyon ve hata durumu kalıcıdır; her sayfa için elle düğme gerekmez. Eski sürümlerde otomasyon varsayılan kapalıdır.
- Profil ve liste işleri ortak tek 15 dakikalık otomatik okuma bütçesini paylaşır; ikisi de uygunsa dönüşümlü çalışarak açlık önlenir. Kendi hesap profil kontrolü de seçilirse saatlik hedefle arka plana katılır. Seçili kişilerin mevcut profil bildirimleri açılır; favori, not, geçmiş ve kişi aralıkları korunur.
- Liste kasası son tek hedeften en fazla 21 hedeflik v2 arşive taşındı. Eski v1 kayıt ilk yazımda göç eder. Yön başına son uygun baseline ve güncel/yarım okuma tutulur; kapasite sınırı 512 KiB olarak kalır. Kapasite aşımında önceki kayıt korunur ve başarısız yönde otomasyon duraklatılır. Rezervasyon/hata kaydı bile sığmıyorsa ayrı kapasite molası alınır; bu molayı temizlemek 429 molasını kaldıramaz.
- İki ayrı okumada da liste sonu gözlenmişse kimlik tabanlı yeni görülen/görünmeyen hesap farkları ve özel Android bildirimi. İlk/yarım/sınırlı okuma olay değildir. Kesin takipten çıkış veya tam/atomik Instagram listesi garantisi verilmez. Bildirime dokunmak sahipliği kontrol edilen hedef listesini açar.
- Otomasyon durumu, kayıtlı listeden fark görüntüleme, ayrı hedef önbellekleri ve onaylı liste kaydı temizleme. Liste otomasyonunu durdurmak bağımsız kişi profil izlemelerini kapatmaz. Manuel liste okuması korunur; açılış ve yerel gezinme kendiliğinden istek göndermez.
- Ağ uçları, mobil oturum taşıması, sayfa boyutu, 429/oturum duraklatması ve Android görev sıklığı korunur; yeni servis, izin veya sunucu yoktur. Büyük listeler ve Android kısıtları tamamlanmayı geciktirir. Canlı uzun süreli Instagram davranışı ayrıca doğrulanmalıdır.

## 0.1.13 — 2026-10-25 (kontrol aralıkları ve takip durumu)

- Kişi kontrollerinde 15/30 dakika ve 1/6/12/24 saat seçenekleri. Ortak otomatik bütçe en fazla 15 dakikada bir okuma ve her işte tek kişi olarak kalır; bu seçenekler her kişi için kesin süre taahhüdü değildir. Mevcut 1/6/24 saat tercihleri aynı sürede korunur.
- Kişi analizinde Takip durumu: son başarılı okuma, en erken kontrol hedefi, bekleme nedeni ve o anda zamanı gelmiş kişiler arasındaki sıra. Durumu güncelle yeni Instagram isteği göndermez.
- Aralıkların kaynak modeli dakika cinsindedir. Mevcut şifreli v4 kaydına isteğe bağlı intervalMinutes eklendi; eski intervalHours yalnız sürüm uyumluluğu için üretilir/okunur. Eski APK'ya dönüşte 15/30 dakika 1 saate, 12 saat 24 saate yuvarlanır ve eski sürüm yeniden yazarsa dakika tercihi kaybolur.
- Kullanıcı adı, biyografi ve karşılaştırılabilir fotoğraf bilgisi değişiklikleri 0.1.12'deki ortak bildirim yolunu kullanmaya devam eder. Sayılar aynıyken her alanın tek başına bildirimi ve gerçek Android arka plan işinin biyografi/fotoğraf bildirimi için test kapsamı genişletildi.
- Yeni ağ ucu, izin, servis, sunucu veya 1–2 dakikalık hızlı takip eklenmedi. 429/oturum duraklatmaları, 15 dakika önbellek, kişi başına son 90 kayıt ve favoriler korunur.

## 0.1.12 — 2026-10-25 (profil bilgisi değişiklikleri)

- Kayıtlı kişiler için kullanıcı adı, biyografi ve karşılaştırılabilir fotoğraf bilgisi değişiklikleri ortak ProfileChanges yorumundan üretilir. Sayılar değişmese de Son değişiklikler ve açık izleme bildirimleri olayı gösterir.
- Kişi analizinde Profil bilgileri ve Profil geçmişi. Kullanıcı adı/biyografi için önceki-yeni değer ve iki gözlem zamanı; fotoğraf için kimlik/varsayılan fotoğraf durumu kanıtı açıklanır.
- Eksik, null, açık boş metin ve alınamayan isteğe bağlı alan ayrılır. İlk bilinen alan başlangıçtır; bilinmeyen aralıktan sonra fark varsa önceki bilinen gözlem zamanı korunur. URL farkı fotoğraf olayı değildir; görsel indirme yoktur.
- Mevcut v4 şifreli hesap kaydına isteğe bağlı metadata/text havuzları eklendi. Yinelenen metinler bir kez yazılır. Eski oturum, geçmiş, favori ve izleme korunur; 512 KiB bütçesi aynı kalır, kapasite aşımında eski kayıt değiştirilmez.
- Mevcut profil GET'i, önbellek aralığı, istek kapısı, sayfalama ve oturum taşıması korundu. Profil alanları yalnız zaten yapılan başarılı okumada gelirse kullanılır. Canlı alan kapsamı ayrıca test edilmelidir.

## 0.1.11 — 2026-10-25 (son değişiklikler ve favoriler)

- Ana sayfada Son değişiklikler: mevcut kişi geçmişindeki takipçi/takip edilen net farkları ve mevcut liste kaydındaki sayfa okumaları gözlem zamanına göre gösterilir. İlk üç kayıt kartta, en fazla 20 kayıt tam akışta görünür. Kayda dokunmak ilgili analizi veya listeyi açar; istek başlatmaz.
- Kişi listesi ve analizde yıldızla favoriye ekleme/çıkarma. Favoriler listede önce gelir; kendi içlerinde mevcut kişi sırası korunur. Favori durumu şifreli kişi kaydına bağlıdır ve yenilemelerde korunur.
- Favori alanı mevcut v4 kayıt sözleşmesine isteğe bağlı Boolean olarak eklendi. Eski kayıtlarda varsayılan false; oturum, geçmiş, not ve izleme ayarları korunur. Favori değiştirmek bildirim işini yeniden planlamaz.
- Okuyucular, ağ istekleri ve mola kuralları değişmedi. Akış ayrı bir olay günlüğü tutmaz; saklanan gözlemlerden türetilir. Liste etkinliği son hedefin mevcut kaydıyla sınırlıdır.

## 0.1.10 — 2026-10-25 (arayüz ve durum açıklamaları)

- Alt gezinme içerik akışından ayrıldı; büyük yazıda kendi yüksekliğini alır ve sayfa içeriğini örtmez. Grup etiketleri metne göre boyutlanır; geçmiş, bildirim ve notlar ayrı kartlarda gösterilir.
- Kayıt zamanı öne alındı. Listeye Kaydın kapsamı açıklaması, arama eşleşme sayısı ve aramayı temizleme eklendi. Aramada sonuç bulunamaması ve alınmış boş sayfa ayrı anlatılır.
- Yerel bağlantı durumundan çevrimdışı kayıt açıklaması; sunucuya bağlantı sınaması veya yeni ağ isteği eklenmedi. Yükleme ve mola bilgileri belirginleştirildi.
- Klavye açıkken üst gezinme ve alt işlemler gizlenerek arama sonuçlarına alan açılır; klavye kapatılınca geri gelir. Başlık erişilebilirlik işaretleri ve klavye odağı çerçevesi eklendi.
- Oturum, veri okuyucuları, kayıt sözleşmeleri ve istek kapısı korundu. Çalışan sürümler ayrı etiket/APK/kaynak yedeğindedir. Kontrol kapsamı `docs/VALIDATION.md` içindedir.

## 0.1.9 — 2026-10-25 (arayüz önizlemesi)

- Sıcak açık zemin, beyaz yüzeyler, mor/mercan/yeşil vurgular ve ortak görsel bileşenler. Ana sayfa, kişi kartları, profil analizi, listeler ve bağlantı ekranları yenilendi.
- Sayı kartından ilgili listeye doğrudan erişim; belirgin alt gezinme, isim baş harfleriyle profil satırları ve daha sade işlem alanları.
- Yalnız alınmış hesaplarda yerel arama. Arama yeni istek göndermez; yazı ve filtre ekran yeniden oluşturulduğunda korunur. Klavye açıkken sonuçlara alan bırakılır.
- Bağlantıda üç aşamalı ilerleme; kısıtlama ekranında gün ve saat içeren açık Yerel mola metni.
- Okuyucular, sayfalama sözleşmesi, oturum kasaları ve istek kapısı değiştirilmedi. Çalışan 0.1.8 kaynak/APK yedeği korundu. Yeni sürümün cihaz ve görsel doğrulama kapsamı `docs/VALIDATION.md` içindedir.

## 0.1.8 — 2026-10-24 (elle liste okuma önizlemesi)

- Seçilen hedefin takipçi/takip edilen listesini tek açık istekle sayfa sayfa okuma; gizli sayfalama, web/GraphQL yedeği veya otomatik giriş yok.
- Devam ve sınırlama alanlarında eksik/null/değer ayrımı; sabit kimlikle tekilleştirme; döngü/boş devam/çelişkili yanıt ayrımı. Liste sonu eksiksizlik garantisi sayılmaz.
- Son hedefin iki yönlü kayıtlarını Keystore ile şifreli saklama; yeniden açılışta istek göndermeden gösterme; alınan iki listede gözlenen ortak ve tek yönde bulunan hesapları karşılaştırma.
- Profil ve liste okuyucuları aynı tek gönderim, 429/yanıt politikası, oturum güncelleme ve iptal sahibini kullanır. Liste başarısı otomasyonu yeniden açmaz.
- Debug örnek liste akışı ve üretim sınırında testler. 25 Ekim'de kullanıcı fiziksel telefonda kendi sayaçları, kendi takip edilen listesi ve arkadaşının ilk takipçi sayfası için canlı başarı bildirdi; çok sayfalı devam henüz canlı sınanmadı. Yeni Android mobil parola/CAA girişi eklenmedi. Canlı test adımları `docs/FOLLOW-LISTS-TEST.md` içinde.

## 0.1.7 — 2026-10-24 (sade arayüz ve istek denetimi)

- Ana ekranda hesap, iki sayaç, son kayıt ve sabit tek yenile düğmesi; alt gezinme. Bağlantı yönetimi, gizlilik, teknik ayrıntı ve deney araçları üç nokta menüsüne taşındı. Kişi analizinde uzun açıklamalar/kayıt listesi isteğe bağlı ayrıntı oldu; bildirim tek anahtarla yönetiliyor.
- Bağlantı aktarımı aynı anda tek adım gösteriyor; hazırlık ve bekleyen aktarım yeniden açılışta bulunuyor. Başka hesaba geçiş etkin hesabı ve molayı silmeden bekleyen aktarımı kaldırıyor.
- Açılışta kendi hesabını otomatik yenileme varsayılan kapalı. Aynı profilin 15 dakikalık kaydı kullanılıyor; ağdan önce son şifreli kayıt yeniden denetlenerek eski ekranın taze kayda rağmen istek göndermesi engelleniyor.
- Web girişi artık sayfa yüklenince profil GET'i başlatmıyor. Devam et tek açık deneme; bağlantı testleri ortak kalıcı 15 dakika bütçesinde. Başarılı yanıt yeni hesap bağlanmadan eski otomasyonu açmıyor.
- Native yanıt sınıflandırması tek sahibi paylaşıyor; HTTP 429, bekleme, feedback ve doğrulama ayrılıyor. Sunucu Retry-After süresi ile yerel mola ayrıldı; bilinmeyen süre bilinmeyen kalıyor. Yerel okuma sayacı yalnız zaman/durum/sabit sinyal tutuyor.
- 429 dayanakları, kapsamı ve test protokolü REQUEST-POLICY.md içinde. Yeni hesabın 429'dan etkilenmeyeceği veya erişimin kesin açılacağı iddia edilmez. Bu geliştirmede yeni canlı hesap isteği yapılmadı.

## 0.1.6 — 2026-10-24 (mobil oturum taşıma önizlemesi)

- Windows instagrapi 3.2.0 oturumundan cihaza özel RSA-OAEP/AES-GCM şifreli yerel ADB aktarımı; aktarım sırasında Instagram isteği veya parola girişi yok.
- Mobil profil okuyucusu: i.instagram.com, Windows kendi profil sorgu parametreleri, gerçek Authorization/cihaz durumu, kayıtlı USDID anahtarıyla imza; doğrulanmış yanıt alanlarıyla şifreli oturum güncellemesi.
- HTTP/2 zorunluluğu; yönlendirme, bağlantı tekrarı ve 503 gibi durumlardaki ek gönderimlerin engellenmesi. Önceki 429 molası ve tek etkin istek korunur.
- Başarılı yanıt öncesinde eski hesabı değiştirmeyen iki şifreli banka; v1-v3 hesap verisini koruyan v4 biçimi. Bağlandıktan sonra özet, kişi analizi ve arka plan aynı mobil oturumu kullanır.
- Şifreleme, Keystore, oturum dönüşümü ve gerçek yerel TLS/HTTP2 tekrar/yönlendirme testleri.

İlk bağlantı Windows'taki mevcut oturumu gerektirir. APK içinde yeni CAA/parola girişi yoktur. Kotlin/OkHttp taşıması Windows curl/TLS uygulamasıyla birebir aynı değildir; canlı Android başarısı henüz doğrulanmadı.

## 0.1.5 — 2026-10-24 (bağlantı prototipi)

- REA ile oturum başlıklarının üretimi/yanıtlarla güncellenmesi incelendi; bulgular ve belirsizlikler belgelendi. 429'un kesin nedeni bulunduğu iddia edilmez.
- Ayrı prototip ekranında aynı WebView oturumundan tek kontrollü web profil okuması; otomatik giriş/yenileme yok. Mevcut 429 ve daha uzun sunucu beklemesi korunur; denemeler en az 15 dakika aralıklıdır.
- Açık deneme sayacı, HTTP durumları ve değer içermeyen yerel tanılama. Cookie/parola/ham yanıt kaydı veya native'e aktarımı yok.
- İptal/arka plana geçiş/sayfa değişiminde geç sonuç reddi; normal kapanışta tarayıcı temizliği. Prototip süresince arka plan okumaları bekletilir.
- Debug fixture üzerinden gerçek WebView/fetch ve tarayıcı JS sözleşmesi testleri; fixture release paketine alınmaz.

Bu prototip ön plandaki bağlantıyı sınar; kalıcı şifreli tarayıcı oturumu ve arka plan bildirimlerine bağlanmış yeni taşıma değildir.

## 0.1.4 — 2026-10-24 (önizleme)

- Kişi bazında isteğe bağlı 1/6/24 saatlik arka plan kontrolü ve net sayı değişimi bildirimi. Android JobScheduler; bildirim izni kapalıyken sorgu yok.
- Ortak otomatik istek bütçesi, tek etkin bağlantı, 429 ve doğrulama sonrasında kalıcı duraklama. Her işte tek kişi; anlık bildirim garantisi yok.
- Şifreli v3 izleme tercihleri; v1/v2 geçişinde izleme kapalı. Ekran/arka plan atomik güncellemeleri; silinen, izleme dışı veya oturumu değişmiş hesaba geç yanıt kaydedilmez.
- Kilit ekranı için genel bildirim görünümü; kişiye dokunarak analiz açma. Sunucu/Firebase/e-posta yok. Debug sürümünde gerçek Android işi üzerinden sentetik bildirim testi.

Son canlı denemede kullanıcı girişten sonra HTTP 429 bildirdi. Gerçek Android profil okuması ve canlı değişiklik bildirimi doğrulanmadı; tekrar deneme durduruldu.

## 0.1.3 — 2026-10-24 (önizleme)

- Seçilen kişileri kullanıcı adı/profil bağlantısıyla ekleme; sabit kimlikle tek kişi yenileme; silme, not ve grup.
- Takipçi/takip edilen ve varsa gönderi sayısı; net değişim, zaman eksenli gözlem grafiği, 7/30 gün filtreleri. En fazla 20 kişi ve kişi başına son 90 gözlem.
- Şifreli v2 kayıt biçimi, v1 oturumunu kaybetmeden geçiş; geçmişlerde kimlik/zaman/boyut doğrulaması.
- Kişi ekranını açmak istek atmaz. Kendi hesap açılışında 15 dakika önbellek; otomatik denemeler de 15 dakika aralıklı.
- Sınırlama sonrası otomatik istek kapalı. Yerel mola 1 saatten başlayıp tekrarlarında 24 saate kadar uzar; daha uzun sunucu süresi korunur. Uyarı, saklanan yanıt zamanı ile yerel molayı ayırır.
- Aynı Android ana koduyla bilgisayarda debug emülatör demosu. Sentetik taşıma yalnız debug kaynaklarında; release APK'ya alınmaz.

Canlı Android kendi hesap/başka profil erişimi doğrulanmamıştır. Test başarısı Instagram kısıtlamasının kalktığı anlamına gelmez.


## 0.1.2 — 2026-10-24

- Bağlantı hazırlama hataları, zaman aşımı ve iptal tek hata akışında ele alınıyor; iptal edilen isteğin geç yanıtı kabul edilmiyor.
- Ekran yeniden oluştuğunda eski yenileme işlemi oturum kaydını sonradan değiştirmiyor. Birden fazla Activity örneği ortak istek aralığını atomik ayırıyor.
- Ayrı `.debug` test paketi, 8 taşıma sözleşmesi testi ve 3 ekran akışı testi. Gerçek hesaba test isteği yok.
- Sayaç kartlarının yüksekliği sistem yazı boyutuna uyum sağlıyor; lint'in bildirdiği metinler kaynak dosyasına taşındı.
- Kilitli Android bağımlılıkları, sabit action commit'leri, SDK kurulum düzeltmesi, Android 7/15 CI işleri ve derlenmiş release APK güvenlik kontrolü.
- Katkı, güvenlik bildirimi, hata bildirimi ve önizleme yayın süreci belgeleri.

Bu sürüm de önizlemedir. Android'de canlı iki sayaç ve kayıtlı oturumla yeni süreçte okuma doğrulanmış değildir.

## 0.1.1 — 2026-10-24

- Bağlantıyı tamamlama isteği sürerken düğme devre dışı. İstekler arasında cihazda kalıcı en az 15 saniye aralık var; yeniden açılış bu sınırı sıfırlamaz.
- HTTP 429 ve tanınan sınırlama yanıtlarında `Retry-After` saniye/tarih değeri izlenir. Süre verilmemişse uygulama yerel olarak 15 dakika bekler; bunun Instagram kısıtlamasının kalkış saati olduğu iddia edilmez.
- Bekleme süresi dolunca kendiliğinden istek gönderilmez. Beklerken tekrar giriş veya bağlantıyı kaldırma süreyi sıfırlamaz.
- Uygulama ön plana geldiğinde geçerli bağlantı için zaman aralığı uygunsa sayılar yeniden okunur.

## 0.1.0 — 2026-10-24

- Türkçe yerel Android arayüzü: bağlantı, iki sayaç, son okuma zamanı, yenileme ve bağlantıyı kaldırma.
- Instagram web giriş sayfasıyla bir kez bağlantı; sonraki açılışta kayıtlı oturumla API okuma.
- Android Keystore / AES-GCM ile yerel oturum; yedekleme ve cihaz aktarımı kapalı.
- HTTPS alan sınırları, yönlendirmeyen API istemcisi, hesap/sayaç doğrulama.
- Dört JVM testi ve Android üzerinde şifreleme/izin testleri için test paketi.

Bağlı Android 13 telefona kuruldu ve kullanıcı uygulamayı açtı. Kullanıcı girişten sonra sınırlama mesajı bildirdi; Android canlı sayaç ve yeniden açılış başarısı henüz doğrulanmadı. Test sonuçları `docs/VALIDATION.md` içinde tutulur.
