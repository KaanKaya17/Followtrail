# Veri ve ağ sınırları

## Windows 0.2.17 / Android 0.1.37 başlangıç hazırlığı

Yeni bağlantıdan sonra kendi listelerinin okunması otomatik sıraya alınır; ayrı bir takip/beğeni/DM yetkisi yoktur. İlk bağlantı açıklaması ve ana ekrandaki hazırlık kartı bunu belirtir, duraklatma sunar. Windows'ta devam imleci ve döngü kontrolü için hash'leri mevcut hesap kasasında DPAPI ile saklanır. Ham devam bilgisi renderer durumuna, gözlem yedeğine veya loga eklenmez; yeni oturum ve geri yüklemede kullanılmaz. Android mevcut Keystore ile şifreli liste arşivini kullanır. Yeni sunucu veya telemetri yoktur.

İlk tarama yalnız başlangıçtır; geçmişteki takipten çıkışlar çıkarılamaz. Sonraki iki uygun kayıt arasındaki fark, kesin takipten çıkış veya kesin olay zamanı olarak sunulmaz. Bildirim izni kapalıyken yerel liste okuma devam edebilir; sistem bildirimi gönderilmez. [Kullanım ve sınırlar](AUTOMATIC-PREPARATION-TEST.md).

## Windows 0.2.15 / Android 0.1.35 hesap güvenliği

Windows'taki `login-identity.vault`, başarısız girişlerde de korunabilen cihaz bağlamını DPAPI ile şifreler. Cihaz UUID'leri, model/istemci ayarları, yerel bölge ve USDID cihaz imzalama bağlamı bulunabilir. Hesap çerezi, hesap yetkilendirmesi, parola ve doğrulama kodu bu kaydın alanları değildir. Başarılı hesap oturumları mevcut bağımsız şifreli hesap kasalarında kalır. Cihaz bağlamı yeni sunucuya/telemetriye gönderilmez; normal Instagram kimlik doğrulamasında kullanılır.

Kayan istek bütçesi Windows'ta mevcut şifreli ortak kapıda sayısal hesap kimliği ve istek zamanlarıyla; Android'de mevcut uygulamaya özel istek tercihlerinde yalnız zamanlarla tutulur. Android bütçe kaydı hesap kimliği veya oturum bilgisi içermez. Hesap silmek bütçeyi sıfırlamaz; eski zamanlar sonraki rezervasyonda 24 saat penceresine göre ayıklanır. Windows gözlem yedeği başka hesapların bütçe kimliklerini içermez.

Bağlantıyı kaldırmak yerel işlemdir; sunucuda oturum iptali değildir. Instagram giriş hareketleri resmî uygulamadan yönetilir. Telefonla paylaşılmış bir oturumu bilgisayardan otomatik iptal edecek yeni bir logout isteği eklenmedi. [Doğrulama ve tarayıcı sınırları](ACCOUNT-SAFETY-TEST.md).

## Windows 0.2.8 oturumsuz erişim karşılaştırması

Mevcut isteğe bağlı kaybolan takipçi incelemesinde veya elle erişim kontrolünde, sağlıklı bağlı oturumdan hedef kimliğe 404 dönerse son bilinen kullanıcı adı için `www.instagram.com/<username>/` adresine tek ayrı GET eklenir. Yeni oturumda hesap çerezi/Authorization yoktur; ortam proxy/netrc bilgisi kullanılmaz. Yönlendirme, otomatik tekrar, aracı servis ve başka hesaptan kontrol yoktur. Instagram hedef kullanıcı adını ve bağlantının IP adresini görebilir. Dönen çerezler ikinci istekte kullanılmaz; ham HTML ve başlıklar kaydedilmez. Yalnız yöntem, kullanıcı adı, varsa eşleşen kimlik, HTTP durumu ve zaman şifreli yerel kanıta girer.

Ortak 15 saniyelik aralık, iptal ve 429 molası bu isteğe de uygulanır. 429 sonrası diğer adreslere veya kayıtlı hesaba geçerek anonim kontrol sürdürülmez. Kimliği doğrulanamayan sayfa, giriş/doğrulama ekranı ve erişim hataları kapatılmış hesap olarak yorumlanmaz. Bu sürüm Android'e anonim karşılaştırma eklemez.

## Windows 0.2.7 / Android 0.1.31 profil zaman tüneli

Arşivleme profil başına açık tercihle başlar; biyografi ve indirilebilen küçük profil fotoğrafları değişiklik bazında saklanır. Windows'ta mevcut DPAPI deposu, Android'de ayrı `accountvisual.enc` Keystore/AES-GCM kaydı kullanılır. Android'in eski geçici avatar önbelleği bu kalıcı arşivin yerine kullanılmaz. Arşiv için yeni üçüncü taraf servis, telemetri veya hesap API isteği yoktur. Android'de arşivlemeyi açarken mevcut fotoğrafın izinli Meta CDN adresinden çerezsiz yüklenmesi gerekebilir; sonrasında normal kullanımda yüklenen küçük görseller yakalanır.

Arşiv varsayılan olarak kapalıdır. Duraklatma geçmişi korur; onaylı temizleme o profilin biyografi/avatar arşivini silip kaydı durdurur. Normal profil gözlemleri bundan ayrı korunur. Windows otomatik önceki depo kopyası temizlenir; kullanıcının daha önce dışa aktardığı şifreli yedekler değiştirilmez. Windows yedekleri bu arşivi içerir, hesap oturumunu içermez. Android bağlantısını kaldırmak ayrı görsel kaydını da temizler. Cihazlar arasında otomatik eşitleme yoktur.

## 0.1.27 cihazda web bağlantısı

Önerilen Instagram ile bağlan yolu, Instagram'ın HTTPS web formunu açar; uygulama bu formun parola/kod alanlarını okumaz. Alınan izinli oturum çerezleri ve WWW-Claim, cihazda Keystore/AES-GCM ile noBackupFilesDir içindeki iki web bankasında saklanır. Hesap referansı çerez içermez. WebView normal bitirme/iptal/kapanışta temizlenir; ani süreç ölümünün temizliği tamamladığı iddia edilmez. Bağlantıyı kaldır web bankalarını da siler.

Yeni API istekleri doğrudan sabit www.instagram.com HTTPS origin'ine gider; hazırlama/işleme için hiçbir üçüncü taraf backend, FollowMeter sunucusu veya analitik servisi kullanılmaz. WebView kaynakları Instagram/Meta CDN sınırını, fotoğraflar ayrı çerezsiz Meta CDN istemcisini korur. Yanıt URL'si/yönlendirme kullanıcı çerezini başka adrese taşıyamaz. Profil/liste yanıtı 1 MiB ile sınırlıdır; ham gövde/çerez tanıya veya işlem geçmişine yazılmaz. Eski mobil parola yolu seçilirse onun bellekte işleme sözleşmesi aşağıdaki 0.1.23 bölümünde geçerlidir.

Web oturumu ve mobil oturum birbirine dönüştürülmez; rastgele ve ayrı bağlantı kapsamlarıyla korunur. Başarılı profil kimliği doğrulaması sonrası hesap kasası v5'e yazılır; v1–v4 kayıtları okunur. Mevcut 429 molası güncellemeyle silinmez. Detay: [WEB-CONNECTION.md](WEB-CONNECTION.md).

## 0.1.26 son telefon giriş tanısı

Tek yerel SharedPreferences kaydında yalnız sabit işlem enumu (P01–P09), istek başlangıç saati ve varsa HTTP durum sayısı tutulur. Bu üç alan hesap/URL/token/parola/kod veya ham yanıt içermez; ayrıca şifrelenmez ve uygulamanın özel alanında kalır. Uygulamanın yedeklemesi kapalıdır. Kayıt yeni istek başladığında güncellenir, yanıt yoksa önceki HTTP durumu kaldırılır. Yerelde reddedilen veya mola nedeniyle gönderilmeyen deneme son istek tanısını değiştirmez. Bağlantıyı kaldır tanıyı temizler; Instagram molası ayrı kayıttır ve korunur. Eski sürümlerin yalnız bellekte tuttuğu P kodu kurtarılamaz. Giriş akışı/izin/servis/istek hızında değişiklik yoktur.

## 0.1.25 yerel kapsam ve kayıt kapasitesi

Uygulamanın vaadi kayıtlı gözlemleri karşılaştırmaktır; kesin takipten çıkış/olay saati değildir. Durum ve grafik kapsamı yalnız mevcut yerel kayıttan hesaplanır; bu arayüzler ek Instagram sorgusu yapmaz. Grafik aralığı gerçek ilk/son gözlem ve kayıt sayısıyla gösterilir; 7/30 gün filtresi ve 90 gözlem saklama sınırı takvim geçmişi garantisi değildir.

Kişi sınırı 100, kişi başına en çok son 90 başarılı gözlemdir. Hesap/kişi şifreli dosyasının açık veri bütçesi 2 MiB’ye çıkarıldı; şifre çözmeden önce aynı bütçe + 28 bayt sınırı kullanılır. Diğer EncryptedRecord kullanıcıları ve liste kayıtları varsayılan 512 KiB sınırını korur. Kapasite aşımı önceki dosyayı değiştirmez; çok veya benzersiz metaveri bütçeyi daha erken doldurabilir. Bu değerler Instagram sınırı değildir. Kişiler ilk 20 kartla gösterilir ve yerel eylemle genişler; yeni ağ işi başlatılmaz. Önceki dosya biçimi okunur, yeni biçim sürümü yoktur; 20’den fazla kişi içeren dosya eski APK’nın sınırına uymaz.

Hızlı izlemenin süre/istek kuralları değişmedi; arayüzde en erken hedef, ortak istek sırası ve görünmez ekranda duraklama açıklanır. Daha çok kaydedilmiş kişi daha çok eşzamanlı istek yetkisi vermez; aynı iş bütçesinde kontrol sırası uzayabilir. Yeni veri hedefi, izin, sunucu veya analitik yoktur. Aşağıdaki eski sürüm kapasite açıklamaları tarihsel sözleşmelerdir.

## 0.1.23 telefondan giriş

Yeni yerel giriş formunda kullanıcı adı/e-posta, parola ve istenirse doğrulama kodu cihaz belleğinde işlenir. Parola sunucunun verdiği anahtarla RSA/AES-GCM biçiminde hazırlanıp HTTPS üzerinden Instagram’a gönderilir; düz metin dosya, log, işlem geçmişi veya analitik kaydı üretilmez. Kod ve ara doğrulama bağlamı yalnız süreç belleğinde kalır. Sistem parola yöneticisi kullanıcı adı/parola için kullanılabilir; uygulama kod alanına otomatik doldurma ipucu vermez. Alanlar Android durum kaydına dahil edilmez. Giriş ekranı FLAG_SECURE ile ekran görüntüsü/kayıt koruması ister. İşletim sistemi veya parola yöneticisinin davranışı uygulamanın saklama vaadi değildir.

Giriş yalnız i.instagram.com ve b.i.instagram.com HTTPS hedeflerine gider. Uygulamaya ait aracı sunucu yoktur. İlk kurulumda rastgele ve kalıcı cihaz UUID/Android kimliği, USDID ve EC imza anahtarı oluşturulur; kayıt durumu ile birlikte Keystore/AES-GCM kaydında tutulur. Telefon numarası/SMS/rehber okuma, ek izin veya Instagram uygulamasının özel verisini okuma yoktur. Ağ/cihaz model bilgileri mevcut Android API’lerinden gelir. İsteklerde sabitlenmiş Instagram/Bloks sürüm metaverisi kullanılır. Bu üçüncü taraf uyarlaması resmî OAuth veya Instagram izin kapsamı ekranı değildir. Oturum, hesabın yetkileriyle kullanılır; arayüzde anlatılan profil/liste okumalarıyla sınırlı uygulama davranışı sunucu tarafından verilmiş dar bir izin kapsamı anlamına gelmez.

Başarılı oturum sahip kimliğiyle ve gerçek profil yanıtıyla doğrulandıktan sonra mevcut şifreli mobil kasaya alınır. Aynı hesabın kişi/geçmiş kayıtları korunur. Başarısız giriş eski bağlantıyı değiştirmez. Bağlantıyı kaldır, yerel oturumu ve yeni telefon kimliği/anahtar kaydını temizler; Instagram’daki diğer oturumları iptal etmez. Oturumun kesin son kullanma zamanı bilinmez.

Giriş sözleşmesi yerelde kurulu, projede önceden kullanılan instagrapi 3.2.0 kaynaklarından uyarlanmıştır; MIT bildirimi assets/licenses/instagrapi-MIT.txt içinde korunur. Giriş parametre şablonları assets/phone_login_contract.json dosyasındadır; yanıt içindeki Bloks ifadeleri çalıştırılmaz, yalnız beklenen statik değerler okunur. Instagram’ın donanım doğrulama anahtarı üretilmez/taklit edilmez: upstream uyumluluk hali anahtarın mevcut olmadığını bildirir. Sunucu reddederse akış durur. Desteklenmeyen checkpoint adımları otomatik geçilmez.

En az 15 saniye ortak istek aralığı, 15 dakika yeni giriş aralığı, 10 dakika/18 istek deneme sınırı ve mevcut 429 molası uygulanır. Giriş sırasında otomasyon bekler. İptal/ağ hatası/429 sonrasında otomatik tekrar yoktur. Tanı sayacı artık telefon giriş adımlarını da kapsar; yalnız deneme zamanı/HTTP durum sınıfı ve mevcut sabit hata bilgileri tutulur, ham istek/yanıt tutulmaz. Canlı Instagram kabulü ve bütün doğrulama çeşitleri bu sürümde doğrulanmadı.

## 0.1.21 kontrol ve karşılaştırma geçmişi

Kontrol edilen hedef kimliği/gözlenen kullanıcı adı, işlem başlangıcı/veri alımı/karşılaştırma tarihleri, önceki karşılaştırılabilir gözlem, sayısal farklar, değişen alan türleri, liste kapsamı/sayfa/hesap sayısı ve sabit hata türü ayrı Keystore/AES-GCM dosyasında tutulur. En fazla son 120 işlem; her liste karşılaştırmasında ilk 10 yeni ve ilk 10 görünmeyen hesabın kimliği/varsa kullanıcı adı saklanır. Ham yanıt, biyografinin kopyası, parola, token veya çerez bu geçmişe yazılmaz. Normal şifreli profil/liste arşivi ayrıca korunur. Kişiyi kaldırmak ilgili geçmişi; bağlantıyı kaldırmak tamamını temizler. Geçmiş ekranı yalnız yereldir, yeni profil isteği göndermez. Hesap değiştirilirse farklı sahibin geçmişi gösterilmez.

Hızlı izle açık kullanıcı eylemidir; seçilen hedefte görünür uygulama boyunca 15 dakikalık oturumda 60 saniyelik profil tazeliği hedefini ve ilgili liste otomasyonunu açar. Sonrasında normal 15 dakikalık arka plan hedefi kalır. Yeni ağ alanı, sürekli servis, izin veya analitik yoktur. Bildirimde gözlem/önceki karşılaştırma saati yer alır; kilit ekranı genel sürümü özel profil ayrıntısı içermez. Bunlar kesin Instagram olay saatleri değildir.

## 0.1.18 geçici profil önbelleği ve açık kullanıcı işlemleri

Son incelenen en fazla 20 profil, tam oturum referansı ve hedef kimliğiyle süreç belleğinde kısa süre tutulur. Tazelik başarılı gözlem zamanından 15 dakika hesaplanır; ekranda açmak süreyi uzatmaz. Disk/SharedPreferences veya dış sunucuya yeni profil arşivi yazılmaz. Kişiyi kaldırma ilgili owner/hedefi, bağlantıyı kaldırma bütün geçici profilleri temizler. Uygulama süreci kapanırsa önbellek kaybolur. Eski oturum/başka viewer verisi yeni oturum için kullanılmaz.

Değişiklikleri izle, kullanıcının açık eylemiyle kişiyi mevcut şifreli kasaya ekleyebilir ve bildirim izniyle profil izlemesini açar. Yalnız profil açmak bunu yapmaz. Kısa bekleme kuyruğu ekran belleğindedir, bir açık kullanıcı işlemini temsil eder; ekrandan ayrılma/iptal, 429/doğrulama ve genel duraklama onu sonlandırır. Listeyi güncelle aynı izinli uçtan aktif yönde kontrollü devam sayfaları alabilir. Yeni izin, reklam/analitik veya ağ hedefi eklenmedi.

## 0.1.17 uygulama içi üye profili

Listeye dokunmak artık dış uygulamayı açmaz. Yeni, dışa kapalı profil ekranı bağlı viewer ve yerel listedeki üye kimliğini doğrular. Kayıtlı taze profil tekrar okunmaz; eski/kayıtsız profil ilk açılışta mevcut profil okuyucusuyla bir kez okunabilir. İstek aralığı, 429 ve bağlantı/doğrulama molaları korunur. Geri dönüş/yeniden oluşturma otomatik tekrar değildir. Instagram’a gitmek ayrı düğmedir; bu intent oturum bilgisi taşımaz.

Kayıtlı olmayan profil kendiliğinden kişi veya otomatik izleme hedefine dönüşmez. Ayrıntılar yalnız ekran belleğinde tutulur; Kişilere ekle seçilirse mevcut şifreli kişi kaydına açık kullanıcı eylemiyle eklenir. Kayıtlı kişide yeni başarılı gözlem mevcut geçmiş/bildirim kurallarını kullanır. Yeni izin, servis veya veri biçimi yoktur.

## 0.1.16 güncel görsel ve dış bağlantı davranışı

Görünür hesap fotoğrafları, mevcut profil/liste yanıtındaki HTTPS cdninstagram.com veya fbcdn.net bağlantısından indirilebilir. Ek üye/profil API sorgusu yapılmaz. Bu ayrı görsel istemcisine Instagram oturumu, Authorization veya çerez verilmez; otomatik yönlendirme ve bağlantı tekrarı kapalıdır. En çok iki görünür görsel yüklemesi ve sınırlı bekleme kuyruğu vardır. Ekrandan ayrılma/satırın yeniden kullanılması yüklemeyi iptal eder. Eksik, erişilemeyen, süresi bitmiş veya uygun biçimde olmayan görsel harfli simgeyle kalır; hesap/liste okuması bundan başarısız sayılmaz.

256 piksele kadar örneklenmiş küçük PNG görseller uygulamanın özel cacheDir alanında en fazla 8 MiB/128 dosya hedefiyle tutulur. Dosya adlarında kaynak URL, kullanıcı adı veya oturum bulunmaz; bağlı hesap, hedef, fotoğraf adresi/kimliği hash ile ayrılır. Bu geçici görsel dosyaları ayrıca şifrelenmez. Hesap/liste metaverileri önceki Keystore/AES-GCM kayıtlarında kalır; bulut yedekleme kapalıdır. Sistem önbelleği temizleyebilir. Harici depolamaya veya kendi sunucumuza fotoğraf gönderilmez. Kişiyi kaldırma ilgili görsel önbelleğini, bağlantıyı kaldırma bu uygulamanın görsel önbelleğini temizler.

Görsel kaydı aynı adres/kimlikle tekrar kullanılır; ayrı fotoğraf tazelik garantisi veya görsel benzerlik analizi yoktur. Profil fotoğrafı değişikliği bildirimi mevcut fotoğraf kimliği/varsayılan fotoğraf metaverisiyle hesaplanır. Görsel indirmenin başarısı yeni profil gözlemi/bildirim üretmez. CDN trafiği İstek bilgileri bölümündeki profil/liste deneme sayacına dahil değildir. Önceki sürümlere ait aşağıdaki “resim indirilmez” açıklamaları 0.1.16 için geçerli değildir.

Liste üyesine dokunmak, gözlenen kullanıcı adından sabit Instagram HTTPS profil bağlantısını sistemin uygun uygulaması/tarayıcısında açar. Bu dış uygulama kendi oturum ve ağ davranışını yönetir; APK'nın oturumu intent'e eklenmez. Eksik kullanıcı adında hesap kimliğinden veya görünen addan profil tahmin edilmez.

## Önceki sürümlerin kayıt ve taşıma sözleşmeleri

0.1.7 tanı kaydı yalnız başlatılan mantıksal profil okuma sayısı, zamanlar, HTTP durum sayısı ve sabit kısıtlama sınıfını tutar; hesap adı/kimliği, URL, IP, parola, çerez, ham istek/yanıt eklenmez. Bu sayaç giriş sayfası/Windows trafiğini ve Instagram'ın genel sayacını kapsamaz. Sunucunun bilinen Retry-After tarihi ile uygulamanın kendi molası ayrı alanlardır. Açılışta kendi hesap yenilemesi varsayılan kapalı; aynı profilin 15 dakika içindeki verisi kayıt zamanını değiştirmeden tekrar kullanılır. Kişi bazındaki açık izleme tercihi ayrı kalır.

0.1.6 mobil aktarımı: Windows'ta kullanıcının önceden oluşturduğu oturum yalnız bellekte DPAPI'den açılır, APK'nın Keystore açık anahtarına RSA-OAEP/AES-GCM ile şifrelenip ADB'den aktarılır. Parola/kod veya düz metin oturum dosyası aktarılmaz. Aktarım Instagram isteği başlatmaz. APK izinli mobil yetkilendirme/cihaz/çerez durumunu ve varsa kayıtlı USDID imza anahtarını Keystore ile şifreli iki yerel bankada tutar. Başarılı içe aktarma sonrası dış dizindeki şifreli zarf, açık istek ve geçici alıcı anahtar temizlenir; normal bitirme de temizlik yapar. Süreç ölümü temizliği tamamlamayabilir; ekranı yeniden açıp bitirmek kurtarma yoludur. Başarılı profil yanıtından sonra mobil HTTPS okumaları doğrudan i.instagram.com'a gider. Windows kaynak oturumu korunur. Ayrıntılar [MOBILE-PORT.md](MOBILE-PORT.md).

Uygulama kendi sunucusuna veri göndermez; analitik, reklam ve çökme toplama SDK'sı içermez. Instagram girişini ve profil okumasını ağ üzerinden Instagram'a yapar. "Veri hiçbir şekilde cihazdan çıkmaz" iddiası doğru değildir.

Uygulama kodu parola alanını okumaz; giriş Instagram'ın HTTPS web sayfasında yapılır. WebView bu sırada Instagram, cdninstagram.com ve fbcdn.net alanlarından içerik yükleyebilir. Üst seviye gezinme Instagram HTTPS alanlarıyla sınırlıdır. Facebook üzerinden ayrı giriş bu sürümde desteklenmez. WebView'e JavaScript köprüsü veya parola alanı enjeksiyonu eklenmez. TLS hatasında devam edilmez, dosya/içerik erişimi ve üçüncü taraf çerezler kapalıdır.

Giriş sayfasından alınan oturum çerezleri hesabın profil API'sini çağırmak için kullanılır. Oturum, son sayılar, seçilmiş kişiler, gözlem geçmişleri, özel notlar ve gruplar, uygulamanın `noBackupFilesDir` dizininde Keystore anahtarıyla AES-GCM şifrelenir. Parola ve doğrulama kodu ek bir kayda alınmaz. Giriş sırasında WebView kendi tarayıcı verilerini oluşturabilir; normal tamamlanma/iptal/Activity kapanışında çerezler, WebStorage ve önbellek temizlenir. İşletim sistemi süreci aniden öldürürse bu temizliğin tamamlandığı garanti edilemez; sonraki giriş başlarken tekrar temizlenir. Uygulama verilerinin silinmesi, uygulama içindeki tüm kaydı kaldırır.

Oturum açıkken hesabın verisi bellekte bulunur. Keystore, uygulama verilerine erişebilen her olası saldırıyı veya ele geçirilmiş/root yetkili cihazı güvenli hale getirmez. Release sürümü debug edilemez ve ekran görüntüsü/son uygulamalar görüntüsüne karşı FLAG_SECURE kullanır; bu da mutlak gizlilik garantisi değildir.

Manifest izinleri: INTERNET (Instagram HTTPS), ACCESS_NETWORK_STATE (ağ koşullu iş), RECEIVE_BOOT_COMPLETED (yeniden başlatma sonrası kalıcı Android işi) ve POST_NOTIFICATIONS (Android 13+ kullanıcı izni). Yedekleme/cihaz aktarımı hariç tutma kuralları açıkça tanımlanmıştır. API çerezleri sabit Instagram HTTPS adresine gönderilir ve HTTP yönlendirmeleri takip edilmez. API yanıtı 1 MiB ile sınırlıdır; yanlış hesap ve eksik/bozuk sayaç reddedilir. Ham istek/yanıt/token uygulama tarafından loglanmaz.

Kaynaklar: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview), [yedekleme](https://developer.android.com/identity/data/autobackup).


0.1.3: Kişiler, oturumla aynı şifreli dosyada tutulur; eski kayıt biçimi okunur. Dosya şifre çözmeden önce en fazla 512 KiB + şifreleme başlığı/etiketi boyutunda okunur. Listede 20 kişi, her geçmişte 90 gözlem, notta 500/grupta 40 karakter sınırı doğrulanır. Kişi kimliği ve gözlem zamanları geçersizse kayıt reddedilir; kayıttaki sorun sessizce boş listeye dönüştürülmez.

Kişi adıyla ilk sorgu yalnız sabit Instagram HTTPS profil ucuna yapılır; kullanıcının verdiği URL'ye istek veya çerez gönderilmez. İzlenen hesaplar mevcut oturumdan erişilebilen profil verileriyle sınırlıdır; gizli içerik erişimi vaat edilmez. Takipçi listesi, mesaj, rehber, medya veya profil fotoğrafı indirilmez. İstek aralığı/sınırlama metadata'sı çerez içermez ve uygulamanın özel SharedPreferences alanında kalır; bağlantıyı kaldırmak sınırlama kaydını sıfırlamaz.

Debug çevrimdışı demo, ayrı previewaccount şifreli dosyası ve preview_gate tercihleri kullanır. Yalnız derlemeye gömülü sentetik veriler gösterir; parola veya gerçek oturum istemez. Bu taşıma release kaynaklarında bulunmaz. Release ekran koruması, yedekleme/TLS sınırları korunur.


0.1.4: İzleme tercihi ve kişi bazındaki bir sonraki kontrol zamanı aynı şifreli v3 kayıtta saklanır. v1/v2 kayıtları izleme kapalı olarak taşınır. Ekran ve arka plan güncellemeleri ortak kilit altında mevcut oturumu ve kişinin hâlâ izlenmesini doğrular. İş parametrelerine çerez/parola eklenmez. Yerel Android bildirimleri için Firebase, sunucu, e-posta veya Gmail erişimi kullanılmaz. Kilit ekranı için genel bir publicVersion ve VISIBILITY_PRIVATE kullanılır; Android/kullanıcı ayarları son görünümü belirler.

Kayıt yazımı bildirimden önce tamamlanır. Bu iki adım arasında süreç kapanırsa bildirim kaçabilir; kalıcı bildirim kuyruğu veya tam bir kez teslim garantisi yoktur. Android planlaması ve üretici pil kısıtları nedeniyle zamanında teslim garantisi de yoktur. Bildirim izni kapalıyken arka plan ağ isteği yapılmaz.


0.1.5 bağlantı prototipi: ayrı ve dışarıya açık olmayan Activity, kullanıcının başlattığı WebView oturumunda tek same-origin fetch çalıştırır. evaluateJavascript ile yalnız profil kimliği/adı/sayılar ve durum projeksiyonu alınır; parola/form/cookie okuma ve JavaScript-native köprüsü yoktur. Profil sonucu ana şifreli kayda veya loga yazılmaz; yalnız ekranda kalır. Tanılama zaman, event enum'u, sayısal HTTP durumları ve sayaçlardan oluşur; URL/query ve ham gövde içermez. Tarayıcı kendi uygulama özel verisini oluşturabilir. Normal bitirme, açılmış tarayıcının cookie/cache/WebStorage verisini temizler; ani süreç ölümü bu temizliği garanti etmez. Henüz kalıcı şifreli tarayıcı oturumu veya yeni arka plan taşıması olarak sunulmaz. Ayrıntılar CONNECTION-PROTOTYPE.md belgesindedir.


## 0.1.8 liste deneyi

Yalnız son incelenen hedefin iki yöndeki alınmış sayfaları noBackupFilesDir içinde Keystore/AES-GCM ile şifrelenir. Kimlik, yanıtta bulunan kullanıcı/görünen ad ve isteğe bağlı resim adresi/gizlilik/doğrulanma alanları saklanır. Resimler indirilmez, üye başına profil isteği yapılmaz. Ham yanıt, Authorization veya çerez liste kaydına/tanıya yazılmaz. Liste kaydı mevcut 512 KiB bütçesini kullanır; kapasite hatası eski sayfaları korur. Başka hedefin başarılı ilk okuması bu kaydı değiştirir. Kişiyi kaldırma ilgili hedefi; bağlantıyı kaldırma liste kaydını siler. Liste okumaları açık kullanıcı eylemidir; arka plan liste toplaması yoktur. [Kapsam ve test](FOLLOW-LISTS-TEST.md).

## 0.1.11 favoriler ve akış

Favori tercihi, ilgili kişinin mevcut şifreli kaydında Boolean olarak saklanır. Ana sayfa akışı ayrı bir sunucuya veya olay dosyasına gönderilmez; cihazdaki profil geçmişi ve mevcut liste sayfalarından üretilir. Kişiyi kaldırmak onun türetildiği geçmişi ve favorisini de kaldırır. Favoriler farklı Instagram hesapları arasında birleştirilmez.

## 0.1.12 profil bilgileri

Normal profil okumasında sunucu gönderirse biyografi metni, profil fotoğrafı kimliği/adresi ve varsayılan fotoğraf bilgisi alınır. Alanların bilinirlik durumu da saklanır. Bu alanlar mevcut sayı geçmişiyle aynı şifreli hesap kaydında, yinelenen metinler ortaklaştırılarak ve mevcut 512 KiB bütçesiyle tutulur. Yalnız son 90 gözlem kapsamındadır; kişi veya bağlantı kaldırılınca ilgili kayıt silinir. Sunucu bu alanları göndermeyebilir.

Fotoğraf binary dosyası indirilmez; harici CDN isteği eklenmez. Fotoğraf kimliği farkı, aynı görselin yeniden yüklenmesini de kapsayabilir. Biyografi ve eski kullanıcı adları uygulama içindeki geçmişte görülebilir. Bildirim yalnız değişiklik türünü taşır; tam biyografi veya eski/yeni değerler bildirime yazılmaz. Kilit ekranı public görünümü kişi adını ve profil metnini içermez; son görünüm Android ayarlarına da bağlıdır.


## 0.1.14 liste otomasyonu

Kullanıcı bir kez açıp hedef seçtiğinde kendi hesabının ve seçili kişilerin takipçi/takip edilen sayfaları arka planda Instagram'dan okunabilir. Profil işleriyle aynı bütçe ve mobil oturum kullanılır. Yeni izin, üçüncü taraf sunucu, reklam/analitik veya medya indirme eklenmedi. Önceki 0.1.8'in yalnız elle/son hedef açıklaması bu yeni isteğe bağlı modun kapsamını anlatmaz; manuel mod ayrıca korunur.

Liste v2 arşivi viewer/mobil binding/hedef kapsamını korur; kullanıcı kimlikleri, yanıtta varsa kullanıcı adları/tam adlar/fotoğraf bağlantıları ve bilinirlik/sayfalama alanları, önceki uygun okuma ile güncel okuma için şifreli tutulur. Binary fotoğraf indirilmez. En fazla 21 hedef tek 512 KiB liste dosyası bütçesini paylaşır. Daha eski liste okumaları kalıcı bir olay günlüğü olarak tutulmaz; yeni döngü önceki baseline/güncel ikilisini değiştirir. Profildeki 90 gözlem saklama kuralı liste geçmişi kuralı değildir.

Kısmi/sınırlı listeden eksik kişi bildirimi üretilmez; iki liste sonu gözlemi de atomik veya eksiksiz veri garantisi değildir. Bildirim kişi üyelerinin isimlerini değil fark sayılarını taşır; public görünüm hedef adını gizler. Bildirim tıklamasında bağlı hesap sahipliği kontrol edilir. Kesin takip/takipten çıkış veya olay saati iddiası yoktur.

Liste kayıtlarını temizleme bütün liste başlangıçlarını ve liste farklarını kaldırır; profil geçmişi, favori, not ve oturum korunur. Bu işlem mevcut 429/bekleme molasını kaldırmaz. Kişiyi kaldırmak o kişinin liste kayıtlarını da kaldırır. Bağlantıyı kaldırmak arşivin tamamını siler. Yeni v2 liste dosyasını eski APK'lar okuyamaz; geri dönmeden önce yeni sürümde liste önbelleği temizlenmelidir. Hesap kaydı v4'e eklenen isteğe bağlı otomasyon alanlarını eski APK yeniden yazarken kaybedebilir.
