# Windows / Android arama önbelleği

Windows 0.2.50 / Android 0.1.78. Profil ekleme araması, pencere kapatılıp yeniden açıldığında aynı sonuçları kısa süreyle tekrar kullanır. Kaydedilen profillerdeki yerel filtreleme bundan ayrı kalır.

## Ortak davranış

- Tek kaynak: `design/search-cache.json`. Üç platform dosyası `node interface/scripts/generate-search-policy.mjs` ile üretilir; masaüstü kaynak kontrolü `--check` ile tutarlılığı doğrular.
- Başarılı sonuçlar **60 saniye**, en fazla **son 6 farklı sorgu** ve sorgu başına **20 aday** için tekrar kullanılabilir. Aynı sonuca bakmak süreyi uzatmaz. Sorgular, mevcut arama giriş parser'ından geçtiği biçimde eşleştirilir; farklı harf/aksan sorguları keyfi olarak birleştirilmez.
- Arama penceresini kapatmak önbelleği silmez. Aynı hesapla süre içinde tekrar aramada Instagram arama API'sine yeni istek yapılmaz; mevcut bütçe/mola süresi tüketilmez. Süre dolduktan sonra mevcut geciktirme ve istek kapısı geçerlidir.
- Geçerli boş sonuçlar da tutulur. HTTP, doğrulama ve taşıma hataları sonuç olarak önbelleğe yazılmaz. Eski bir pencerenin gecikmiş yanıtı, temizlenmiş veya farklı hesaba ait önbelleği yeniden dolduramaz.
- Hesap/oturum değişiminde, bağlantı kaldırıldığında veya profil silme işlemi başlatıldığında sonuçlar temizlenir. Android'de ana ekranın yaşam döngüsü sona erdiğinde, Windows'ta arayüz/worker yeniden açıldığında bellek önbelleği yenidir. Kapanan uygulama veriyi diske taşımaz.

## Sahiplik ve gizlilik

Windows arayüzü yalnız aday kimliği, kullanıcı adı, profil adı ve sınırlı görsel erişim belirtecini tutar; bütün uygulama durumunu arama önbelleğine kopyalamaz. Worker aynı süre/kapasiteyle adayları ve özel Meta CDN kaynaklarını bellekte saklar. Başka bir işlem yapılması eski arama fotoğrafını kendiliğinden bozmaz; görsel erişimi hâlâ hesap + opak belirteç + üye kimliğiyle doğrulanır. Kaynak adresi arayüze aktarılmaz. Süresi dolan belirteç kabul edilmez.

Android önbelleği ana ekrana aittir; birbirini izleyen arama diyaloglarına aynı bellek nesnesi verilir. Anahtar mevcut `Session` bağlamıdır. Arama küçük resimleri mevcut geçici görsel yükleyicisini kullanır; yeniden gösterim için Meta CDN yüklemesi gerekebilir. Arama API isteğinin atlanması, hiçbir görsel HTTP isteği olamayacağı anlamına gelmez.

Süre erişim sırasında doğrulanır. Eski girdiler sonraki erişim/yer değiştirme veya yaşam döngüsü temizliğinde bellekten çıkarılır; saniyesinde fiziksel bellek üzerine yazma iddiası yoktur. Sonuçlar şifreli hesap kasasına, günlük arşive veya dosyaya yazılmaz. Hesaplar arası arama geçmişi aktarımı ya da yeni analitik servisi yoktur.

## Kapsam

Bu güncelleme arama davranışını eşitler. Giriş akışı, sunucunun liste kısıtları, Windows/Android arka plan zamanlama farkları ve istek bütçeleri bu özellikten bağımsızdır. Yeni bir canlı Instagram testi gerektiren sayfa boyutu veya giriş değişikliği yapılmadı.

## Doğrulama

Saf JavaScript/Kotlin testleri süre, kapasite, boş sonuçlar, hesap/oturum ayrımı, temizleme sonrası gecikmiş yanıt ve saat gerilemesini kapsar. Üretim Engine/DPAPI testleri, yeniden kullanılan sorgunun HTTP istemcisi açmadığını ve istek bütçesini değiştirmediğini; fotoğraf kaynağının başka işlemlerden sonra da kendi hesabıyla erişilebilir kaldığını sınar. Arayüz senaryosu, diyalogu kapatıp aynı sorguyu yeniden açtığında tek arama çağrısıyla adayların/fotoğrafların gösterilmesini kontrol eder.

Android yerel diyalog testi sentetik arama işleviyle eklendi; cihaz/emülatör çalıştırması ayrıca belirtilmelidir. Derlemek, bu ekranın cihazda çalıştırıldığını göstermez. Bu değişikliğin uygulamanın toplam günlük isteklerini belirli bir yüzdeyle azaltacağı ölçülmüş değildir; tasarruf aynı sorgunun süre içinde tekrarına bağlıdır.

4 Kasım 2026'da çalıştırılan kontroller:

- Python backend tam takımı: **266 test geçti**. Sekiz yeni test, cache süresi/kapasitesi, boş sonuçlar, hesap ayrımı, görsel belirteçleri, gerçek Engine/DPAPI akışı ve hata/silme/yeniden açılış davranışını kapsar.
- Masaüstü JavaScript: **68 test geçti**; bunların 10'u arama ve önbellek davranışını kapsar. Kaynak arayüzde **13 senaryo**, paketli Windows 0.2.50'de **36 genel kontrol** geçti. Arama diyalogunun kapatılıp yeniden açılması ve fotoğrafların korunması sentetik yanıtlarla sınandı; canlı Instagram isteği gönderilmedi.
- Android: **151 JVM testi geçti**; release derlemesi ve lint (0 hata / 50 uyarı) başarılı. Beş yeni saf cache testi ve bir yeni yerel diyalog testi bulunur. Yerel diyalog testi yalnız derlendi; emülatör/telefonda çalıştırılmadı.
- Ortak politika/tema/simge, dört dil (727 Android kaynak / 1166 masaüstü ileti), JavaScript kaynakları ve Wiki bağlantı kontrolleri geçti.
- İmzalı APK: `InstagramTracker-0.1.78.apk`, kod 79, 2.520.507 bayt; SHA-256 `9c04ff7cc74f4e5a645df8b6c474dd6c25516a74d2437f4d01772144b4fd8f9f`. v2/v3 imza ve manifest/izin kontrolleri başarılı. Yeni izin/bağımlılık eklenmedi.
