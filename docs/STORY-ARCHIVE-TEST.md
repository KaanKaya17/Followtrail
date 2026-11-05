# Hikâye arşivi — Windows 0.2.39 deneysel altyapı

Bu sürüm **elle başlatılan** bir okuyucu ve yerel arşivdir. 0.2.38'de aylık takvim, günlerde kayıt sayısı, güne basınca yerel kayıtları açma, önceki/sonraki ay ve son kayda dönme eklendi. Ay değiştirme ağ isteği göndermez. Günler UTC'ye göre gruplanır; boş gün yalnız kayıt olmadığını gösterir. Android ve otomatik hikâye kontrolü henüz eklenmedi. Tek gerçek fotoğraf indirmesi doğrulandı; yeniden paylaşım katmanları, müzik ve video kodekleri henüz canlı doğrulanmadı.

0.2.39'da ilk açılış tam takvimdir. Gün seçildiğinde takvim solda küçülür, ortada kayıtlı medya ve sağda o hikâyenin etiket/kaynak/bağlantı bilgileri açılır. Günün hikâyeleri üstteki seçimlerden değiştirilir. Yerel dosya seçimi için yeni Instagram isteği yoktur; videoda sesli otomatik oynatma yoktur. Yeniden paylaşımın özgün hikâye kaynağı halen bilinmeyen olarak sunulur; paylaşılan gönderi referansı ve kişi etiketi ayrı bilgiler olarak kalır.

Ay geçişindeki hata, ekranın genel worker snapshot'ındaki geçici `storyDetail` alanına dayanmasıydı. Başka bir kontrol/ayar işlemi bu alanı temizleyince açık takvimin tıklamaları çalışmıyordu. Ekran artık kendi hesap/target/medya bağlamına sahip bir controller kullanır; ay gezinmesi geçici snapshot'a bakmaz. Genel state'in gerçekten temizlendiği üretim IPC yolu paketli regresyonda sınanır. Medya yanıtı hesap/target/hikâye kimliğiyle doğrulanır; kapanmış veya başka hesaba geçmiş görünümün geç yanıtı gösterilmez. Ay değiştirmek mevcut oynatıcıyı yeniden okumaz; farklı hikâye, takvime dönüş veya kapanış kaynağı bırakır.

## Kullanım ve canlı test

1. Önceki Windows uygulamasından tepsi menüsü → **Tamamen çık** ile çıkıp 0.2.39'u aç. Aynı kasaya iki sürüm birlikte yazmasın.
2. Giriş yapılmış **izleyici test hesabı**, paylaşım yapan test hesabının hikâyelerini normal Instagram'da görebilmeli. Kayıtlı mola varsa bekle; hesap/parola veya çerez paylaşma.
3. Paylaşan test hesabını Tracker'da kayıtlı profillere ekle. Profil panelindeki **Hikâye arşivi · Deneysel** yalnız yerel kayıtları açar.
4. Başlangıçta yalnız tek fotoğraf hikâyesi paylaş. **Hikâyeleri kontrol et** düğmesine bir kez bas. Kayıtlı günü seçip otomatik açılan fotoğrafı karşılaştır. 429 veya doğrulama durumunda dur; otomatik fallback/yeniden deneme yoktur.
5. Başarılıysa ayrı aşamalarda kısa video, kişi etiketi, bağlantı etiketi, gönderi/Reels paylaşımı ve izinli hikâye yeniden paylaşımını sına. Her aşamada görünen medya, ses, etiketlenen hesap ve kaynak gönderi ayrı karşılaştırılmalı. Yerel 6–10 saniyelik sıra ve mevcut hesap/cihaz bütçeleri korunur; bunlar Meta'nın güvenli kota garantisi değildir.
6. Arşivi kapatıp tekrar aç, uygulamayı yeniden başlat ve tarihe göre filtrele. Bunlar Instagram isteği göndermemeli. Aynı aktif hikâyeyi yeniden kontrol etmek, tamamlanmış dosyayı tekrar indirmemeli.
7. Hikâye süresi dolduktan sonra önceki kayıt korunmalı. Yeni okumanın boş gelmesi, kişinin hiçbir hikâye paylaşmadığının kanıtı değildir. **Arşivi temizle** yalnız seçilen profilin hikâyelerini siler.

İlk test için Android/emülatör gerekmez. Canlı fotoğraf testinin sonucu aşağıdadır; yeni senaryolarda 429/bekleme korunur.

## 0.2.38 canlı fotoğraf ve görünürlük doğrulaması — 3 Kasım 2026

Kullanıcının paylaştığı test hikâyesi önce HTTP 200 / `reel: null` döndürdü. Mevcut oturum kimliği tek GET kontrolünde sunucudan doğrulandı; hedef hesap kimliği de eşleşti. Kullanıcı yayıncı hesaptaki hikâye gizleme listesinden izleyici hesabı çıkardığını bildirdi. Aynı izleyici/hedef, aynı okuyucu ve aynı mevcut oturumla sonraki tek hikâye kontrolü **1 öğe** döndürdü; Meta CDN'den **9.746 bayt WEBP, 1080 × 1920** indirildi ve Windows DPAPI arşivine kaydedildi. Yerel yeniden açmada sahiplik/SHA-256 ve görsel biçimi doğrulandı. Yeni giriş, parola denemesi, sosyal yazma isteği veya 429 sonrası fallback yapılmadı. Gerçek görsel ve ham hesap yanıtı repoya/test paketine eklenmedi.

Bu önce/sonra sonucu, bu denemedeki boş yanıtın hikâye görünürlüğüyle ilişkisini gösterir; bütün boş yanıtların aynı nedenden geldiği iddia edilmez. Ekran artık sıfır sonuçta Instagram'ın veri döndürmediğini, bunun hikâye paylaşılmadığı anlamına gelmediğini açıklar. Normal uygulamada parola girişinin reddedilmesi, mevcut Tracker oturumunun da mutlaka geçersiz olduğunu göstermedi; bu olayda mevcut oturum sunucudan kabul edildi.

## Okuma ve veri kapsamı

Tek seçilmiş mobil GET: `/api/v1/feed/user/{numeric-id}/story/`. Genel GraphQL, alternatif uç/fallback, görüldü, beğeni, yanıt, oy verme ve paylaşma işlemleri eklenmez. Mevcut `TrackerClient` gönderim kapısı, iptal ve 429 davranışı kullanılır. Kütüphanenin otomatik fallback yapan `user_stories` veya sosyal işlem yardımcıları çağrılmaz.

Kaydedilenler: hikâye/paylaşan hesap kimliği; varsa kaynak paylaşım/sona erme zamanı; uygulamanın kaydetme zamanı; medya türü; doğrulanmış kişi etiketleri; bağlantı/hashtag/konum; hikâyeye eklenen gönderi kimliği ve varsa kısa kodu. Kaynakta alan yoksa bilinmeyen, açık boş dizi varsa boş kalır. Etiket, orijinal hikâye sahibine dönüştürülmez. `resharedFrom` bu sürümde bilinmeyendir. Orijinal gönderi/Reels ayrıca sorgulanmaz veya indirilmez. Anket, müzik katmanı ve bütün sticker'ları yeniden çizme desteği yoktur. Metadata ve medya katmanları birebir görünüm garantisi değildir.

## Yerel saklama ve sınırlar

- Her hesabın mevcut kasa dizininin altında ayrı `stories/` arşivi; Windows kullanıcı kapsamlı DPAPI. Dosyalar ve indeks şifrelidir; açık geçici medya dosyası oluşturulmaz.
- İndeks 2 MiB; hesap başına en fazla 200 hikâye, toplam şifreli medya 256 MiB; tek indirilen medya en fazla 16 MiB. Alan açmak kullanıcı eylemidir; otomatik geçmiş silme/sona erdirme yoktur.
- Yalnız Meta CDN HTTPS adresleri; ayrı çerezsiz/yetkilendirmesiz, ortam proxy'sini kullanmayan TLS taşıması. CDN indirmeleri de ortak sıraya/bütçeye yazılır. Yönlendirme izlenmez. Yanıt boyutu, dosya başlığı ve görsel boyutu doğrulanır; video kodeğinin oynatılabilirliği ayrıca sınanır.
- Başarılı dosya ve indeks yazımından sonra medya tamamlandı sayılır. Sonraki hata/iptal önceden kaydedilenleri silmez; indirilemeyen kaydın yalnız metadata'sı kalabilir. İndeks yazılamazsa yeni dosya temizlenir. Temizleme başarısızsa indeks sahipliği korunur ve temizleme tekrar denenebilir.
- Medya byte'ları normal snapshot/olaylarda bulunmaz. Kullanıcı gün/hikâye seçtiğinde tek dosya IPC üzerinden çözülür; yerel Blob oynatıcısı kullanılır. Başka worker işlemi sürüyorsa yerel açılış onun bitmesini bekler; Instagram molası aşılmaz veya yeni medya HTTP isteği yapılmaz. Video kullanıcı oynatmadan başlamaz. Kapatma/hikâye değiştirme/hesap geçişi kaynağı bırakır. Ay gezinmesi aynı medya kaynağını korur.
- Hikâyeler mevcut taşınabilir olmayan gözlem yedeğine veya CSV'ye dahil değildir. Arayüz bunu açıklar. Kişi kaydının kaldırılması diğer Windows geçmişleri gibi arşivi tutar; yeniden aynı ID eklenince erişilebilir. Hesabı tamamen silme ilgili hikâye dosyalarını da temizler; bağlantıyı kaldırma arşivi korur.

## Kaynak ve doğrulama

Kaynak sözleşmesi projede sabitlenmiş [instagrapi 3.2.0 story okuyucusu](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/mixins/story.py), [alan ayrıştırıcısı](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/extractors.py) ve [StoryMedia modeli](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/types.py). Bunlar Meta'nın resmî API erişim garantisi değildir. `test-fixtures/stories.json` yapaydır; gerçek hesaptan alınmış yanıt diye sunulmaz.

Çevrimdışı kontroller `test_stories.py` ve paketli `check-stories-desktop.mjs`: gerçek DPAPI/üretim taşıma sınırı, sadece GET, yanlış sahip, bilinmeyen/boş alan, medya başlığı/kapasite/yönlendirme/kesik indirme, yeniden başlatma, deduplikasyon, önceki arşivin korunması, 429 sonrası tek deneme, iptal, disk/temizleme hatası, medya dosyası kimliği ve hesap ayrımı. JPEG görsel testi gerçekten çözülüp gösterilen sentetik görseldir; birim testindeki MP4 başlığı oynatılabilir video kanıtı değildir.

0.2.37 kaynak/paket kontrolleri: **235 Python testi** (11 hikâye testi dahil; 36,112 saniye), **57 Node testi**, `npm run check`, Windows worker derlemesi ve **52 paketli kontrol** (6 hikâye + 36 genel + 10 çoklu hesap) geçti. Sentetik JPEG, dört dil, filtre, koyu tema, temizleme, hesap sınırı ve ağsız test modu sınandı. Gerçek MP4/ses henüz doğrulanmadı. Uygulama ailesi CPU/RAM, uzun süreli pil/depolama maliyeti ve önceki sürümle performans karşılaştırması ölçülmedi.

0.2.38: **11 hikâye Python testi**, **58 Node testi**, `npm run check`, worker derlemesi ve **6 paketli hikâye/takvim kontrolü** geçti. Artık tarih girişi yerine aylık takvim/gün sayıları/gün açılımı/son kayıt kısayolu sınanır; şubatın 29 günü, pazartesi hizası, yıl sınırı ve boş sunucu sonucu da kapsanır. Dört dil/açık-koyu tema, yerel görsel açma, hesap sınırı ve temizleme doğrulandı. Blob bırakma kontrolü CSP'nin engellediği `fetch` sonucuna dayanmaz; gerçek `URL.revokeObjectURL` çağrısını gözler. Paketli testlerde JavaScript hatası ve renderer HTTP isteği yoktur. Sentetik ekranlar `artifacts/desktop-0.2.38-qa/stories/` altındadır; gerçek fotoğraf testi bunlardan ayrı, yukarıdaki dar kapsamla raporlanır.

0.2.39: **61 Node testi**, `npm run check`, worker derlemesi ve **53 paketli kontrol** (7 hikâye görüntüleyici + 36 genel + 10 çoklu hesap) geçti. Açık arşivde gerçek ayar işlemiyle `storyDetail=null` üretildikten sonra iki yönde çoklu ay geçişi, medya kaynağının korunması, gün seçerek otomatik yerel açılış, etiket/gönderi/bilinmeyen kaynak sunumu, küçük takvim–medya–bilgi yerleşimi, dosya seçimi/boş gün/takvime dönüş, hesap geçişinde temizleme ve dört dil sınandı. 960×680 yerel pencere için içerik ölçüsü Windows çerçevesinin dış ölçüsünden ayrı alınır; yatay taşma yoktur. Controller testleri ayrıca meşgul kuyruğu, geç hesap yanıtı ve yanlış medya kimliğinde tekrarsız hatayı kapsar. Görsel doğrulama sentetik JPEG ile yapıldı; bu sürüm çalışmasında yeni canlı Instagram isteği gönderilmedi. Ekranlar `artifacts/desktop-0.2.39-qa/stories/` altındadır.

## Android 0.1.81

Native Android arşivi, takvim/ay geçişi, gün seçimi, yerel fotoğraf ve video oynatımı ile etiket/bağlantı/paylaşılan gönderi alanlarını içerir. Özet menüsü kendi hesabının arşivini, kayıtlı profilin Diğer menüsü o profilin arşivini açar. Açılış ve gezinme yereldir; kontrol düğmesi ve onayı olmadan Instagram isteği gönderilmez. Windows ile aynı UTC gün sınırı ve ortak boyut/kabiliyet sözleşmesi kullanılır. Android dosyaları Keystore/AES-GCM ile hesap bazında şifrelenir; oynatma için şifresiz geçici dosya veya dış oynatıcı kullanılmaz. Profil/hesap silme ve saklama süresi, hikâye dosyalarını da kapsar.

Saf ayrıştırıcı, kodek, takvim ve byte-window birim testleri eklendi. Gerçek Keystore/disk, silme sırasında geç yanıt ve iptal testleri instrumentasyon paketine eklendi; bu paket derlenir fakat kullanıcı cihaz testi istemediği için çalıştırılmaz. Emülatör açılmadı; Android görsel düzeni, video kodek uyumluluğu, ağ ve canlı hikâye kapsamı doğrulanmış sayılmaz. Bu testlerde Instagram isteği yapılmadı.
