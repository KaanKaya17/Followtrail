# 0.1.17 profil açma ve geri dönme

1. Güncellemeyi mevcut uygulamanın üzerine kur. Kendi takipçi veya takip edilen listende bir kişiye dokun: profil APK'nın içinde açılmalı. Instagram yalnız ayrı Instagram’da aç düğmesiyle açılmalı.
2. Önceden kaydedilmiş taze profil ek istek olmadan gösterilir. Kayıt yoksa veya eskiyse ilk açılışta aynı istek/mola kurallarıyla profil okunabilir. Hemen önce liste okunmuşsa 15 saniyelik aralık veya mevcut 429 molası nedeniyle yalnız bilinen liste bilgileri görünür; hazır olduğunda Profili getir düğmesini kullan. Eksik sayılar sıfır gösterilmez.
3. Profilin geri oku listeye dönmeli; arama ve sıra korunmalı. Fotoğraflar yüklenirken takipçi listesinin kendi Geri okunu ve sistem geri düğmesini de dene; uygulama kapanmamalı. Ana ekranın sistem geri düğmesiyle normal çıkışını çökme olarak değerlendirme.
4. Açtığın kişi otomatik olarak Kişiler listesine eklenmemeli. İstersen Kişilere ekle düğmesiyle kaydet; mevcut kişiler/notlar/favoriler kalmalı ve yeni kişinin otomatik izlemesi kapalı olmalı.
5. Ekranı döndürüp dış Instagram’dan geri gel: kendiliğinden ikinci profil isteği başlamamalı. Yeni profil kayıtlı değilse ayrıntılar kalıcı bir inceleme geçmişine eklenmez; ekran yeniden oluşturulurken bellek korunur, süreç ölümünde yeniden okuma için açık eylem gerekir.

## 0.1.16 kişi, liste ve fotoğraf kontrolleri

1. APK'yı uygulamayı kaldırmadan güncelle. Mevcut oturum, kişiler, favoriler ve otomasyon seçimlerini kontrol et.
2. Kişiler ekranında her kartın sağ üstündeki çöp kutusuna bas. Onayda doğru kullanıcı adı görünmeli. Önce Vazgeç de; kişi kalmalı. Yalnız kaldırmak istediğin test kişisinde Kaldır de; diğer kişiler ve ayarları kalmalı. Uygulamayı tekrar açınca kaldırılan kişi gelmemeli. Analiz altındaki Kişiyi kaldır aynı işlemi yapar.
3. Alınmış takipçi/takip edilen listesinde arama yapıp bir sonuca dokun. O kullanıcının Instagram profili uygun uygulama veya tarayıcıda açılmalı. Geri dönünce arama ve liste kalmalı; kişi otomatik eklenmemeli ve profil/liste okuma sayacı artmamalı. Görseller kendi ayrı CDN trafiğine sahiptir.
4. Yanıtta görsel bağlantısı varsa kendi hesabında, kişi kartlarında/analizinde ve görünür liste üyelerinde fotoğraf gelmeli. Bağlantı yoksa veya erişilemiyorsa harfli simge kalır; otomatik yeni profil isteği yapılmaz. Eski kayıtta URL yoksa ancak sıradaki normal başarılı okuma bunu sağlayabilir.
5. Fotoğraflar geldikten sonra interneti kapatıp aynı ekranları ve uygulamayı yeniden aç. Sistem önbelleği temizlemediyse aynı adres/kimlikteki görseller önbellekten görünmeli. Çevrimdışı açılış yeni profil gözlemi üretmemeli. Listeyi hızla kaydırıp farklı kişilerin fotoğraflarının karışmadığını kontrol et.
6. Büyük sistem yazısında silme/favori düğmelerini, uzun kullanıcı adlarını ve profil bağlantısına dokunmayı kontrol et. Bu sürümün önceki otomasyon/bildirim regresyon adımları ayrıca geçerlidir.

## 0.1.15 diyaloglarını inceleme

1. Güncellemeyi mevcut uygulamanın üzerine kur; kaldırma/veri temizleme yapma. Kayıtlar ve otomasyon seçimleri kalmalı.
2. Otomasyonu ayarla penceresi yumuşak mor zeminli, yuvarlatılmış köşeli olmalı. Seçimler ve Başlat/İptal/Nasıl çalışır? okunmalı; sistem yazısını büyütüp kaydırmayı da dene.
3. Otomasyon durumu → Yenile yalnız yerel kayıtları yeniden gösterir. Satıra basmak kayıtlı listeyi açar. Liste kayıtlarını temizle artık Nasıl çalışır? içinde; ayrı onayı İptal edince veri kalmalı.
4. Kişi ekle ve Not ve grup pencerelerini klavye açıkken dene. Alanlar, kaydırma ve Kaydet/Vazgeç düğmeleri erişilebilir olmalı. Kişiyi kaldır onayından vazgeç.
5. Değişiklik bildirimi ve Kaydın kapsamı pencerelerini aç. Uzun açıklamalar kaydırılmalı; sistem Geri düğmesi pencereyi kapatmalı.

Bu görsel inceleme için yeni Instagram verisi çekmek gerekmez. Çok sayfalı otomasyon denemesi [ayrı kılavuzda](AUTOMATION-TEST.md).

## Önceki ana ekran kontrolleri

1. `InstagramTracker-0.1.11.apk` dosyasını mevcut uygulamanın üzerine güncelleme olarak kur. Uygulamayı kaldırma veya verilerini temizleme. Yeni arayüz için hesabını yeniden bağlaman gerekmez.
2. Ana sayfada kendi kullanıcı adını ve önceki sayıları kontrol et. İki renkli sayı kartı, ilgili takipçi/takip edilen ekranını açar; bu geçiş okuma başlatmaz.
3. Alt çubuktan Kişiler ekranını ve kayıtlı bir kişinin analizini aç. Geçmiş, not, grup ve izleme ayarları aynı kayıtlardan gelir.
4. Önceden alınmış bir liste varsa arama alanına birkaç harf yaz. Arama yalnız bu kayıtları filtreler, ağ isteği göndermez. Klavye açıkken üst gezinme/alt işlemler yerine arama sonuçlarına alan açılmalı; Bitti ile kapatınca işlem düğmeleri geri gelmeli.
5. Liste ekranındaki bilgi düğmesinden Kaydın kapsamı açıklamasını aç. Gösterilen sayının alınan kayıtlara ait olduğu ve son sayfa görülmesinin tamlık garantisi vermediği açık olmalı.
6. Aramada eşleşmeyen bir ad dene; Aramana uygun hesap yok mesajı gelmeli. Temizle düğmesi kayıtları geri göstermeli. Bu, alınmış sayfada gerçekten sıfır hesap olmasıyla karışmamalı.
7. İnternet yokken önceden alınmış veriler açılmalı; Çevrimdışı görüntülüyorsun açıklaması gelmeli. Tarih ve sayılar kendiliğinden yenilenmemeli.
8. Daha büyük sistem yazı boyutunda başlıkları, sekmeleri ve kaydırmayı kontrol et. Geri düğmesiyle ana ekrana dön.

Bu inceleme için yeni veri çekmek gerekmez. Profil veya liste okuma düğmesine basarsan mevcut istek ve mola kuralları geçerlidir. Büyük listede devam, uzun süreli oturum ve liste karşılaştırması için önceki canlı test kılavuzu ayrıca geçerlidir.

Önceki 0.1.9 kaynak/APK yedeği de korunur. Çalışan 0.1.8, `checkpoint/0.1.8-live-20261025` etiketi ve ayrı yerel APK/kaynak/Git yedeğinde korunur. Bu sürüm yeni veri toplama özelliği veya veri biçimi geçişi eklemez.

## Son değişiklikler ve favoriler

- Kişiler ekranında bir kişinin yıldızına bas: favori listenin başına gelmeli. Uygulamayı yeniden açınca ve aynı kişiyi başarıyla yenileyince yıldız korunmalı. Yıldızı kapatınca kişi normal sırasına döner.
- Ana sayfada Son değişiklikler, kayıtlı gözlemler arasındaki sayı farklarını tarihle göstermeli. İlk kayıt veya yalnız gönderi değişikliği takipçi değişikliği diye sunulmaz.
- Akış kaydına dokunmak ilgili analizi/listeyi açmalı; Tümünü gör yalnız saklanan en son 20 girdiyi gösterir. Bu işlemler ve yıldızlar okuma sayacını artırmamalı.
- Liste etkinliği son hedefin eldeki sayfa kayıtlarıyla sınırlıdır. Sayfa kaydı, yeni takipçi veya takipten çıkma olayı değildir. Mevcut geçmişin dışında eski hareketler tahmin edilmez.
