# Veri ve ağ sınırları

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
