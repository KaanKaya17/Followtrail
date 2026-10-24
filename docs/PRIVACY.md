# Veri ve ağ sınırları

Uygulama kendi sunucusuna veri göndermez; analitik, reklam ve çökme toplama SDK'sı içermez. Instagram girişini ve profil okumasını ağ üzerinden Instagram'a yapar. "Veri hiçbir şekilde cihazdan çıkmaz" iddiası doğru değildir.

Uygulama kodu parola alanını okumaz; giriş Instagram'ın HTTPS web sayfasında yapılır. WebView bu sırada Instagram, cdninstagram.com ve fbcdn.net alanlarından içerik yükleyebilir. Üst seviye gezinme Instagram HTTPS alanlarıyla sınırlıdır. Facebook üzerinden ayrı giriş bu sürümde desteklenmez. WebView'e JavaScript köprüsü veya parola alanı enjeksiyonu eklenmez. TLS hatasında devam edilmez, dosya/içerik erişimi ve üçüncü taraf çerezler kapalıdır.

Giriş sayfasından alınan oturum çerezleri hesabın profil API'sini çağırmak için kullanılır. Oturum, son sayılar, seçilmiş kişiler, gözlem geçmişleri, özel notlar ve gruplar, uygulamanın `noBackupFilesDir` dizininde Keystore anahtarıyla AES-GCM şifrelenir. Parola ve doğrulama kodu ek bir kayda alınmaz. Giriş sırasında WebView kendi tarayıcı verilerini oluşturabilir; normal tamamlanma/iptal/Activity kapanışında çerezler, WebStorage ve önbellek temizlenir. İşletim sistemi süreci aniden öldürürse bu temizliğin tamamlandığı garanti edilemez; sonraki giriş başlarken tekrar temizlenir. Uygulama verilerinin silinmesi, uygulama içindeki tüm kaydı kaldırır.

Oturum açıkken hesabın verisi bellekte bulunur. Keystore, uygulama verilerine erişebilen her olası saldırıyı veya ele geçirilmiş/root yetkili cihazı güvenli hale getirmez. Release sürümü debug edilemez ve ekran görüntüsü/son uygulamalar görüntüsüne karşı FLAG_SECURE kullanır; bu da mutlak gizlilik garantisi değildir.

Manifestte tek izin INTERNET'tir. Yedekleme/cihaz aktarımı hariç tutma kuralları açıkça tanımlanmıştır. API çerezleri sabit Instagram HTTPS adresine gönderilir ve HTTP yönlendirmeleri takip edilmez. API yanıtı 1 MiB ile sınırlıdır; yanlış hesap ve eksik/bozuk sayaç reddedilir. Ham istek/yanıt/token uygulama tarafından loglanmaz.

Kaynaklar: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview), [yedekleme](https://developer.android.com/identity/data/autobackup).


0.1.3: Kişiler, oturumla aynı şifreli dosyada tutulur; eski kayıt biçimi okunur. Dosya şifre çözmeden önce en fazla 512 KiB + şifreleme başlığı/etiketi boyutunda okunur. Listede 20 kişi, her geçmişte 90 gözlem, notta 500/grupta 40 karakter sınırı doğrulanır. Kişi kimliği ve gözlem zamanları geçersizse kayıt reddedilir; kayıttaki sorun sessizce boş listeye dönüştürülmez.

Kişi adıyla ilk sorgu yalnız sabit Instagram HTTPS profil ucuna yapılır; kullanıcının verdiği URL'ye istek veya çerez gönderilmez. İzlenen hesaplar mevcut oturumdan erişilebilen profil verileriyle sınırlıdır; gizli içerik erişimi vaat edilmez. Takipçi listesi, mesaj, rehber, medya veya profil fotoğrafı indirilmez. İstek aralığı/sınırlama metadata'sı çerez içermez ve uygulamanın özel SharedPreferences alanında kalır; bağlantıyı kaldırmak sınırlama kaydını sıfırlamaz.

Debug çevrimdışı demo, ayrı previewaccount şifreli dosyası ve preview_gate tercihleri kullanır. Yalnız derlemeye gömülü sentetik veriler gösterir; parola veya gerçek oturum istemez. Bu taşıma release kaynaklarında bulunmaz. Release ekran koruması, tek INTERNET izni, yedekleme/TLS sınırları korunur.
