# Android 0.1.40 — Giriş sayfası yükleme tanısı

29 Ekim 2026. Fiziksel telefonda 0.1.39 giriş formunu göstermeden dönen sayfa gözlendi; kullanıcı önceki 0.1.34 testinde formun açıldığını bildirdi. Kaynakta 0.1.35, LoginBrowserPolicy filtresini, servis çalışanı ağ kısıtını ve giriş sonrası yerel karşılama sayfasını ekler. Bu zaman aralığı bir gerileme hipotezidir; hangi kaynağın engellendiği ve bunun yüklemeyi bozduğu henüz canlı olarak kanıtlanmadı. Bilgisayardaki tarayıcı başarısı, telefondaki WebView için başarı kanıtı değildir.

## Kapsam

- Giriş ekranında Sayfa tanısı düğmesi, ekranı kapatmadan İstek bilgilerini açar. LP01 raporu yalnız son giriş sayfası açılışının zamanını, belge başlama/bitme sayısını, izinli/engelli GET/POST/diğer türlerin sabit kaynak kategorilerini, sayısal HTTP/bağlantı hatalarını ve giriş alanı görünürlüğünü saklar. Türler LoginBrowserPolicy sahibinin aynı kararıyla üretilir; yeni ağ izni verilmez.
- URL, alan adı, kullanıcı adı/ID, parametre, gövde, başlık, parola, kod, çerez ve sayfa metni kaydedilmez. DOM sorgusu yalnız kullanıcı adı/parola/e-posta giriş alanının görünür olup olmadığını döndürür; alan değerlerini okumaz. JavaScript köprüsü, fetch veya yeni profil isteği eklenmedi.
- Belge bitişi HTTP 200 veya hazır giriş formu olarak yorumlanmaz. Mevcut istek aralığı, 429 molası, genel GraphQL/sosyal işlem yasakları, TLS iptali ve servis çalışanı kısıtı korunur. ALLOWED_RESOURCE, yalnız yerel filtrenin iznidir; yanıtın uzaktaki Instagram sunucusundan geldiğini tek başına kanıtlamaz. Yerel engelli kaynağın 403'ü ayrı LOCAL_BLOCK etiketi alır.
- Rapor uygulamanın özel tercihlerinde kalır; dışarıya gönderilmez. İstek başına disk yazılmaz: sayaçlar bellekte birikir, belge tamamlanması/görünürlük sorgusu/tanı açılışı/ekrandan ayrılmada son rapor yazılır. Sayaçlar 999'da doyar; her sayaç sözlüğü en fazla 32 kategori tutar. Bu sınırdaki değerler tam trafik sayısı olarak yorumlanmaz.
- Görünürlük sorgusu sayfa olayından 12 saniye sonra bir kez çalışır. Ekran arka plana geçince bekleyen sorgu kaldırılır; geri gelince tek sorgu planlanır. Yeni sayfa nesli eski DOM cevabını kabul etmez. Kapanmış WebView'a ait tanı nesnesi geç olaylarla yeni raporu değiştiremez.

## Doğrulama

90 JVM testi, release lint ve release/debug/test derlemeleri geçti. 608 TR/EN kaynak/yer tutucu eşleşti. API 37 Small_Phone emülatöründe LoginPageTraceTest, WebLoginScreenTest, WebComparisonScreenTest, WebComparisonBoundaryTest, WebConnectionTest ve AccountSafetyBoundaryTest: 29 test geçti (97,16 saniye). Gerçek WebView/Keystore/özel tercihler ve sentetik hedef HTTP kullanıldı. Tanı testi yerel engelli GraphQL isteğini ve görünür boş giriş alanını yakalar; gövde içeriğinin kaydedilmediğini, profil sayacının artmadığını ve hesap kaydı oluşmadığını doğrular. Sınır testleri sayısal hataları, doyan sayaçları, bilinmeyen görünürlüğü ve eski sayfadan geç gelen olayların yeni rapora yazılmamasını kapsar. Var olan A/B bağlantısı, iptal, 429 ve sosyal POST engelleri geçti.

İlk emülatör test başlatması yanlışlıkla iki kez gönderildi. Çakışan koşular durduruldu; sonuçları başarı sayılmadı. Sonraki tek tur 29/29 geçti. Fiziksel telefona bu sırada test APK'sı yüklenmedi; üretim hesabı testlerde kullanılmadı.

Üretim APK 0.1.40/kod 41 mevcut anahtarla imzalandı ve verify-release.py paket/izin/imza denetiminden geçti. Debug, yedekleme ve cleartext kapalı. Bu sürüm girişin düzeldiği veya önceki 429 sorununun çözüldüğü beyanı değildir. Canlı LP01 sonucu ayrıca okunmalıdır; ağdaki izinleri tanıya bakmadan genişletmeyin. Windows 0.2.19 ve web önizlemesi değiştirilmedi.
