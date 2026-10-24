# Veri ve ağ sınırları

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
