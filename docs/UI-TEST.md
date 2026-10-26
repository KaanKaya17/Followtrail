# 0.1.20 dil seçimi kontrolleri

1. Mevcut kurulumun üzerine güncelle. Sağ üstteki üç nokta → Dil / Language → English seç. Ana ekran At a glance, alt sekmeler Home / People olmalı. Menü Language / Dil üzerinden Türkçe'ye dönülebilmeli; seçili dil işaretli görünmeli.
2. İngilizce seçiliyken kapatıp yeniden aç. Dil korunmalı. Kişi analizi, liste araması/işlem menüleri, bağlantı ekranı, gizlilik ve istek bilgilerini aç. Büyük sistem yazısında menüler ve düğmelere kaydırmayla ulaşılabilmeli.
3. Önceki kişiler, not, gruplar, favoriler, biyografiler ve izleme aralıkları aynı kalmalı. Örneğin İngilizce sayı 2,043, Türkçe 2.043 olmalı; kullanıcı adı ve biyografi çevrilmemeli.
4. Dil değiştirirken İstek bilgileri sayacı artmamalı. Mevcut 429 molası varsa saat ve manuel bekleme korunmalı; dil değiştirme mola kaldırmamalı. Açılışta otomatik yenileme tercihi açık olsa bile yalnız dil için yeniden oluşturma profil okumamalı.
5. Kontrolündeki hesapta normal izleme akışıyla yeni bildirim oluştuğunda bildirim ve kanal adı seçilen dili kullanmalı. Önceden gösterilen bildirimlerin geçmiş metni yeniden yazılmaz. Bildirim izni/kanalının açık-kapalı durumu dil değiştirme ile sıfırlanmamalı.
6. Android 13+ üzerinde sistemin uygulama dili ayarı ile aynı tercih görünmeli. Android 7–12'de uygulama içi seçim ve yeniden açılış ayrıca kontrol edilmeli. Bu eski Android çalışma zamanı kontrolü yerel API 35 testi yerine geçmez.

## 0.1.18 basitlik, kullanım ve hız kontrolleri

1. Mevcut kurulumun üzerine güncelle. Kişiler, notlar, favoriler, bağlantı ve izleme aralıkları kalmalı. Ana sayfada son değişiklikler daha yukarıda, favoriler yatay kısa geçişlerde görünmeli. Favoriye dokunmak kayıtlı kişinin analizini açmalı; yeni istek göndermemeli.
2. Kısa izleme kartındaki üç noktadan Otomasyonu ayarla / Otomasyon durumu / Liste otomasyonunu durdur seçeneklerine ulaş. Duraklama varsa İzleme açık yerine gerçek bekleme nedeni görünmeli. Liste otomasyonunun açık tercihi ile o anda çalışabilir olması aynı şey değildir.
3. Bir liste üyesinin profilini açıp normal başarılı okuma al. Geri dönüp aynı profili 15 dakika içinde tekrar aç: ayrıntılar hemen gelmeli ve ek profil isteği olmamalı. Bu geçici önbellek süreç belleğindedir; uygulamayı zorla durdurup süreci kapatmak onu temizler. Son kayıt zamanı ileri alınmamalı.
4. Normal kısa aralıkta açılan profil veya bastığın Yenile/Listeyi güncelle işlemi için İşlemin sırada kartı ve geri sayım görünmeli. Süre dolunca bir kez devam etmeli; Bekleyen işlemi iptal et veya geri çıkış yeni okumayı önlemeli. 429/doğrulama molasında bu otomatik devam açılmamalı. Mola/sayaç sıfırlama yapma.
5. Listeyi güncelle düğmesiyle aktif yöndeki sayfalar alınırken arama yap. Yeni sayfa gelince arama metni ve kaydırma korunmalı; hesap/sayfa sayısı ilerlemeli. Tek tur en fazla altı sayfadır; kalan varsa tekrar güncelleme veya seçili arka plan otomasyonu gerekir. İlk hata/sınırlamada tur durmalı; alınanlar kalmalı.
6. Liste işlemleri menüsünde ayrıntı, tek sayfa okuma ve uygunken karşılaştırma korunur. İlk sayfayı yeniden okumak mevcut kayıt varsa onay ister. Yeni üye profili yine APK içinde, Instagram yalnız ayrı düğmeyle açılmalı.
7. Geçici ve kayıtlı profilin sayı/biyografi düzenini karşılaştır. Yeni kişide Değişiklikleri izle seçilirse bildirim izni ardından yalnız o kişi kaydedilip profil izlemesi açılmalı. Yalnız kişilere ekle izlemesi kapalı kayıt oluşturmalı. Mevcut kişiler/favoriler/aralıklar kalmalı.
8. Büyük sistem yazısı, çevrimdışı görüntüleme, profil/listeden geri dönüş ve fotoğraf yüklenirken çıkış kontrollerini tekrarla. Bildirim ve uzun süreli arka plan kontrolleri için önceki otomasyon testleri geçerlidir.

## 0.1.17 profil açma ve geri dönme

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


## 0.1.21 hızlı kontrol ve zaman çizelgesi

[Hızlı izleme testleri](QUICK-WATCH-TEST.md): Türkçe/English, görünür kalan süre, bitirme, gözlem/işlenme zamanları ve ayrıntı penceresi; 720×1600 ve büyük yazı ölçeğinde kaydırma/düğmeler. Geçmiş açma/filtre/geri yeni profil isteği üretmemeli. Hızlı oturum, görünür ekran dışına çıkınca duraklamalı; ilk başlangıçtan 15 dakika geçince kart kapanmalı.
