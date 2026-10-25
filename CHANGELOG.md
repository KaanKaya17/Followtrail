# Değişiklikler

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
