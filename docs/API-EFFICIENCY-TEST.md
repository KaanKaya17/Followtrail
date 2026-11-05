# İstek verimliliği — Windows 0.2.54 / Android 0.1.84

5 Kasım 2026. Bu not [araştırmanın](API-EFFICIENCY-RESEARCH.md) ardından uygulanan ilk paketi kapsar; yeni Instagram erişimi veya 429'un çözüldüğü iddiası değildir.

## Değişen davranış

- Windows otomatik liste başlangıcı, aynı hesap ve bağlantıda bu süreçte kaydedilmiş **60 saniyeden yeni** profil bilgisini paylaşır. En fazla 20 kayıtta yalnız ID, kullanıcı adı, iki sayı ve gerçek gözlem zamanı tutulur; fotoğraf/biyografi kopyalanmaz. Hesap değişimi, yeniden giriş, bağlantı/silme işlemleri kapsamı temizler. Süre aşımı ve saat geri alınması eski bilgiyi geçerli yapmaz. Elle başlatılan yeni tarama ve onarma sonundaki doğrulama hâlâ yeni profil sorgular. Önbellek isabeti yeni kontrol olayı üretmez ve gözlem zamanını ileri taşımaz. Android'in mevcut ListBasis paylaşımı korunur.
- İki platformda da otomatik profil işleri, hesabın ve cihazın hareketli bütçesinin **%80'ine** gelindiğinde ertelenir. Profil başlangıcı seçimi bu zamanı dikkate alır; listeler ve açık kullanıcı işlemleri normal toplam bütçe içinde devam edebilir. Ayrı/sıfırlanan sayaç yoktur: bütün işler aynı gerçek rezervasyon geçmişine yazılır. Kalan %20, listeler ile elle yapılan işlemlerin ortak kullanım payıdır; yalnız elle işlemlere ayrılmış garanti değildir.
- Toplam bütçe hesap başına 120/saat, 1200/24 saat; cihaz başına 240/saat, 2400/24 saat olarak kalır. Profil erteleme eşikleri 96/960 ve 192/1920 olur. 6–10 saniyelik sıra, mevcut uzun rezervasyon, iptal, 429 ve sunucu Retry-After denetimleri değişmez. Kontrol aralığı tercihi sessizce değiştirilmez; bütçenin gerektirdiği gecikme sonraki iş zamanına yansır.
- Ayarlarda kesintisiz profil kontrolünün yaklaşık günlük talebi ve yerel profil payı gösterilir. Windows kendi hesabı dahil izlenen profil sayısı ve ortak aralığı; Android etkin kişi aralıkları ve kendi hesabının arka plan tercihini kullanır. Android sistem arka plan işinin 15 dakika veya daha geç çalışabilmesi nedeniyle bu tahmin gerçekleşen istek sayısı değildir. Dört dilde metin eklendi.

## Çevrimdışı kanıt

`scripts/audit-api-efficiency.py` aynı sentetik listelerle açık yenilemeli başlangıç ve otomatik paylaşım yollarını karşılaştırır. Her senaryoda başlangıç profil kontrolü ve iki eksiksiz liste vardır; 50 benzersiz kayıt/sayfa, gerçek üretim Engine'i, geçici şifreli kasa ve sanal 8 saniyelik aralık kullanılır.

- 50/50: toplam **5 → 3** istek; profil sorgusu **3 → 1**.
- 82/50: toplam **6 → 4**; profil sorgusu **3 → 1**. Bu senaryoda yaklaşık %33 azalma.
- 3000/2000: toplam **103 → 102**; liste sorguları her iki yolda **100**. Uzun tarama 60 saniyelik pencereyi aştığından ikinci yön için yeniden profil okunur. Büyük listelerde küçük hesap oranı vaat edilmez.

İlk karşılaştırma koşusu sırasıyla açık/otomatik yollar için 0,2266/0,1425; 0,2492/0,1806; 5,7197/4,8650 saniye çalışma bölümü ölçtü. CPU süreleri 0,0781/0,0312; 0,0469/0,0156; 2,1562/1,8906 saniyeydi. Tek Python süreci tepe çalışma kümesi 60.903.424 bayttı. Bu zamanlar ağ beklemesi değildir. Açık yol her çiftte önce çalıştı; sıcak sıra rastgeleleştirilmedi, soğuk/sıcak kazancı ayrıştırılmadı. Süreç ailesi, gerçek TLS/ağ, Android çalışma zamanı ve ajan token maliyeti ölçülmedi. Sonraki küçük temsil değişikliği önbellekten gereksiz fotoğraf/biyografi kopyalarını kaldırdı; bu sayıların nihai kod için bir hızlanma garantisi olduğu iddia edilmez.

Liste hazırlığı testlerinin yanıt üreticisi kaynak türüne göre yanıt verecek şekilde güncellendi; önbelleğin atladığı profil gövdesi sonraki liste isteğine verilmez. İptal/eksik liste kontrolünde yalnız rastgele bir ayrıştırma hatasının testi geçirmesi engellendi ve `list_count` sonucu açıkça doğrulandı. Değişmiş sayıyı bekleyen örneklerde tazelik süresinin dolması belirtildi.

Yeni üretim sınırı regresyonları yeniden kullanımda tam liste sayıları ve eski gözlem zamanını; süre aşımı/bağlantı değişimi/elle taramada yeni okumayı; 429'da tek gönderim ve mola; saat/gün/cihaz baş boşluğu; farklı hesap ve saat geri alınması; sınırlı önbellek boyutu ve gereksiz alanların tutulmamasını kapsar. JVM testleri ortak hareketli bütçe ve listeler çalışırken profillerin ertelenmesini kapsar. Doğrulama: 117 seçili Python üretim sınırı testi, 12 JavaScript testi, 176 JVM testi ve 36 paketli Windows kontrolü geçti. Kaynak ayrıştırma, ortak politika üretim eşleşmesi ve dört dilin 1169 mesaj eşleşmesi doğrulandı. Android release lint: 0 hata / 56 uyarı; APK v2/v3 imzası ve paket manifesti doğrulandı. İlk artımlı release derlemesi `userMessage` çözümleme hatası verdi; artımlı Kotlin derlemesi kapalı tam release derlemesi geçti. Kaynak workaround eklenmedi. İmzalı APK: 2.614.715 bayt; SHA-256 `607dcb0aaa84e2955a2d470edfe4748eb242ae8dee053c86e36d638e8c7397d9`. Windows 0.2.54 gerçek kullanıcı kasasıyla açıldı; otomasyon tercihi açık, yerel 429 duruşları kaldırılmış durumda. Kayıtlı günlük bütçe ayrı denetlenir; bu doğrulama canlı sunucu erişiminin açıldığını iddia etmez.

## Yerel Windows işlemi

Kullanıcı açıkça istediği için, uygulama tamamen kapandıktan sonra şifreli yedek alınıp yerel `blockedUntil`, seçili hesabın `manualOnly` duruşu ve katalogdaki ortak `limitedHold` kaldırıldı; otomatik kontrol açıldı. O anda gelecekte geçerli bir sunucu Retry-After kaydı yoktu. İstek geçmişleri, deneme/sınırlama sayaçları ve son sınırlama tarihi değiştirilmedi. Yeni bir HTTP 429 aynı normal duruşu yeniden kurar. Günlük bütçenin ayrıca dolu olabileceği kullanıcıya belirtildi; bu işlem bütçe sıfırlaması değildir. Yedek ve gerçek hesap kayıtları yalnız yerel `artifacts/` altında, Git dışında kalır.

Android'de canlı giriş, telefon/emülatör ekran testi ve Meta sunucu davranışı bu paketle yeniden denenmedi; kullanıcıyla sonraki canlı doğrulamaya bırakıldı.
