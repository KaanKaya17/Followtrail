# Değişiklikler

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
