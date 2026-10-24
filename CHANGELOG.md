# Değişiklikler

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
