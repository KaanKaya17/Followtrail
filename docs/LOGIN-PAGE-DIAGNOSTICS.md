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

## 0.1.41 adayının dayanağı

Fiziksel 0.1.40 LP01: 1 belge başlangıcı, 2 bitiş; formVisible=false; 7 POST/INSTAGRAM_OTHER, 1 POST/GRAPHQL, 3 GET/OTHER_HOST ve 2 GET/INSTAGRAM_OTHER engeli; toplam 13 LOCAL_BLOCK/PART/403; bağlantı hata sözlüğü boş. Bunlar Instagram 403 veya yeni 429 değildir. Tanı hangi URL'nin engellendiğini kaydetmediğinden tek başına nedensellik kurmaz.

Bilgisayarda ayrı, yeni ve hesapsız Edge bağlamlarıyla accounts/login sayfası karşılaştırıldı. Parola girilmedi, kullanıcı oturumu taşınmadı; genel POST'lar engellendi. Mobil WebView benzeri bağlamda IGMAAWebBloksFullPageRootQuery / api/graphql engellendi; giriş alanı oluşmadı. Yalnız iki bootstrap istisnası bu mobil sorunu çözmedi. Masaüstü bağlamda email ve pass alanları görüldü; mevcut filtreyle de görülebildikleri için iki bootstrap engelinin tek başına sorunun nedeni olduğu iddia edilmez. Linux/Windows UA metni tek başına değiştirilmiş mobil/istemci-ipucu bağlamları masaüstü başarı kanıtı değildir. Windows masaüstü istemci ipuçları ve masaüstü bağlamında alanlar görünür oldu. Bu sonuçlar gerçek Android WebView ölçümüyle tamamlanmalıdır.

İki bootstrap yolunun hesapsız HTTP 200 yanıtlarında yalnız üst düzey alan adları incelendi: bulk-route-definitions payload/payloads, sr_payload ve log_roots; bootloader-endpoint hrp ve serverGenTime döndürdü. Gövde/token değerleri saklanmadı. Bunlar sayfa kodu/rota yükleme izinleridir, genel POST veya GraphQL yetkisi değildir. WebView POST gövdelerini sunmadığından bilinmeyen GraphQL işlemleri açılmadı.

0.1.41 kurulu Chrome sürümünü içeren masaüstü sunumu, geniş görünüm ve yakınlaştırma ayarlarını kullanır; UA sayfa açılmadan ayarlanır. Oturum devrinde WebView'ın aynı UA'sı kullanılır. Yeni email/pass işaretlemesi dahil görünürlük kontrolü yalnız alanın varlığı/görünürlüğünü okur. Çerez, parola, kod, alan değeri ve sayfa metni tanıya eklenmez. 429 molası ve TLS/servis çalışanı kısıtları korunur.

Canlı form testi yalnız debug paketinde, live_login_page=true açık parametresiyle çalıştırılabilir; normal test turunda atlanır. Hiçbir giriş bilgisi yazmaz ve doğrula düğmesine basmaz. Formun görünmesi oturum açma, A/B profil okuma veya önceki 429'un çözüldüğünü kanıtlamaz.

### Uygulanan 0.1.41 doğrulamaları

92 JVM testi geçti; release lint: 0 hata, 21 uyarı. Release/debug/test derlemesi başarılı (3 dk 51 sn). Emülatörün izole debug paketinde WebLoginScreenTest, LoginPageTraceTest ve AccountSafetyBoundaryTest turu: 13 vaka, 12 başarılı, canlı opt-in vaka varsayılan olarak 1 atlandı (48,516 sn). Bootstrap GET/POST sözleşmeleri, yeni email/pass alanlarının görünürlük tanısı, şifreli oturum devri, iptal/429 ve sosyal işlem engelleri kapsandı.

İlk canlı emülatör denemesinde formun kendiliğinden görünmesi beklendi ve test başarısız oldu (33,375 sn). LP01 formVisible=false; yerel 403/10, bağlantı hatası yok, engelli GraphQL yok. Sayfanın uygulamaya yönlendiren başlangıç ekranı olduğu, public tarayıcıda Log in seçeneğiyle formun açılmasıyla ayrıldı. Kullanıcı akışını izleyen ikinci canlı testte önce görünür boş form aranır; yoksa yalnız tek ve açık Log in/Giriş yap giriş seçeneğine bir kez dokunulur. Form varsa submit'e dokunulmaz; alan değerleri okunmaz, parola yazılmaz, A/B başlatılmaz. Bu test gerçek emülatör WebView'ında geçti (1/1, 11,104 sn); sayaçlı profil isteği 0 ve hesap kaydı yok. İlk başarısız deneme başarı sayılmadı. Sentetik testler gerçek Instagram girişinin yerine kanıt gösterilmez.

APK 0.1.41/kod 42, üretim anahtarıyla imzalandı ve paket/izin denetimi geçti; SHA256 60b1179f32711669e110e3a0ced7c65168c8ef4d13fa1236d8039ddb794e493e. Debug/backup/cleartext kapalı. Fiziksel telefona install -r ile güncellendi, sürüm doğrulandı ve MainActivity açıldı. Kullanıcı fiziksel telefonda kullanıcı adı/parola alanlarının göründüğünü doğruladı. Bu, form açılışı doğrulamasıdır; giriş/A/B sonucu henüz bekleniyor. Windows sürümü değiştirilmedi; aday için herkese açık sürüm başarısı ilan edilmedi.

A/B tamamlayıcı emülatör turu WebComparisonScreenTest, WebComparisonBoundaryTest ve WebConnectionTest: 18/18 geçti (52,997 sn). Toplam çevrimdışı kapsam 30 başarılı vaka ve varsayılanda atlanan 1 canlı vaka; canlı vaka ayrıca açık parametreyle 1/1 geçti. Bu testler yalnız sentetik A/B yanıtlarını doğrular; gerçek hesap A/B sonucu kullanıcı testinden beklenir.
