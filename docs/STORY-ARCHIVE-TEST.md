# Hikâye arşivi — Windows 0.2.37 deneysel altyapı

Bu sürüm **elle başlatılan** bir okuyucu ve yerel arşivdir. Android, otomatik hikâye kontrolü ve tam aylık takvim henüz eklenmedi. Tarihe göre filtrelenen günlük akış vardır. Canlı Instagram kabulü, yeniden paylaşım katmanları, müzik ve video kodekleri sentetik testlerden doğrulanmış sayılmaz.

## Kullanım ve canlı test

1. Önceki Windows uygulamasından tepsi menüsü → **Tamamen çık** ile çıkıp 0.2.37'yi aç. Aynı kasaya iki sürüm birlikte yazmasın.
2. Giriş yapılmış **izleyici test hesabı**, paylaşım yapan test hesabının hikâyelerini normal Instagram'da görebilmeli. Kayıtlı mola varsa bekle; hesap/parola veya çerez paylaşma.
3. Paylaşan test hesabını Tracker'da kayıtlı profillere ekle. Profil panelindeki **Hikâye arşivi · Deneysel** yalnız yerel kayıtları açar.
4. Başlangıçta yalnız tek fotoğraf hikâyesi paylaş. **Hikâyeleri kontrol et** düğmesine bir kez bas. Kaydı açıp fotoğrafı karşılaştır. 429 veya doğrulama durumunda dur; otomatik fallback/yeniden deneme yoktur.
5. Başarılıysa ayrı aşamalarda kısa video, kişi etiketi, bağlantı etiketi, gönderi/Reels paylaşımı ve izinli hikâye yeniden paylaşımını sına. Her aşamada görünen medya, ses, etiketlenen hesap ve kaynak gönderi ayrı karşılaştırılmalı. Yerel 6–10 saniyelik sıra ve mevcut hesap/cihaz bütçeleri korunur; bunlar Meta'nın güvenli kota garantisi değildir.
6. Arşivi kapatıp tekrar aç, uygulamayı yeniden başlat ve tarihe göre filtrele. Bunlar Instagram isteği göndermemeli. Aynı aktif hikâyeyi yeniden kontrol etmek, tamamlanmış dosyayı tekrar indirmemeli.
7. Hikâye süresi dolduktan sonra önceki kayıt korunmalı. Yeni okumanın boş gelmesi, kişinin hiçbir hikâye paylaşmadığının kanıtı değildir. **Arşivi temizle** yalnız seçilen profilin hikâyelerini siler.

İlk test için Android/emülatör gerekmez. Canlı test bu değişiklik hazırlanırken başlatılmadı; kullanıcı sonuçlarıyla ayrıca kaydedilecek.

## Okuma ve veri kapsamı

Tek seçilmiş mobil GET: `/api/v1/feed/user/{numeric-id}/story/`. Genel GraphQL, alternatif uç/fallback, görüldü, beğeni, yanıt, oy verme ve paylaşma işlemleri eklenmez. Mevcut `TrackerClient` gönderim kapısı, iptal ve 429 davranışı kullanılır. Kütüphanenin otomatik fallback yapan `user_stories` veya sosyal işlem yardımcıları çağrılmaz.

Kaydedilenler: hikâye/paylaşan hesap kimliği; varsa kaynak paylaşım/sona erme zamanı; uygulamanın kaydetme zamanı; medya türü; doğrulanmış kişi etiketleri; bağlantı/hashtag/konum; hikâyeye eklenen gönderi kimliği ve varsa kısa kodu. Kaynakta alan yoksa bilinmeyen, açık boş dizi varsa boş kalır. Etiket, orijinal hikâye sahibine dönüştürülmez. `resharedFrom` bu sürümde bilinmeyendir. Orijinal gönderi/Reels ayrıca sorgulanmaz veya indirilmez. Anket, müzik katmanı ve bütün sticker'ları yeniden çizme desteği yoktur. Metadata ve medya katmanları birebir görünüm garantisi değildir.

## Yerel saklama ve sınırlar

- Her hesabın mevcut kasa dizininin altında ayrı `stories/` arşivi; Windows kullanıcı kapsamlı DPAPI. Dosyalar ve indeks şifrelidir; açık geçici medya dosyası oluşturulmaz.
- İndeks 2 MiB; hesap başına en fazla 200 hikâye, toplam şifreli medya 256 MiB; tek indirilen medya en fazla 16 MiB. Alan açmak kullanıcı eylemidir; otomatik geçmiş silme/sona erdirme yoktur.
- Yalnız Meta CDN HTTPS adresleri; ayrı çerezsiz/yetkilendirmesiz, ortam proxy'sini kullanmayan TLS taşıması. CDN indirmeleri de ortak sıraya/bütçeye yazılır. Yönlendirme izlenmez. Yanıt boyutu, dosya başlığı ve görsel boyutu doğrulanır; video kodeğinin oynatılabilirliği ayrıca sınanır.
- Başarılı dosya ve indeks yazımından sonra medya tamamlandı sayılır. Sonraki hata/iptal önceden kaydedilenleri silmez; indirilemeyen kaydın yalnız metadata'sı kalabilir. İndeks yazılamazsa yeni dosya temizlenir. Temizleme başarısızsa indeks sahipliği korunur ve temizleme tekrar denenebilir.
- Medya byte'ları normal snapshot/olaylarda bulunmaz. Kullanıcı **Aç** dediğinde tek dosya IPC üzerinden çözülür; yerel Blob oynatıcısı kullanılır, otomatik oynatılmaz. Kapatma/filtre değiştirme/hesap geçişi oynatıcı kaynağını bırakır. Ağdan akış veya arşiv ekranında yeni profil isteği yoktur.
- Hikâyeler mevcut taşınabilir olmayan gözlem yedeğine veya CSV'ye dahil değildir. Arayüz bunu açıklar. Kişi kaydının kaldırılması diğer Windows geçmişleri gibi arşivi tutar; yeniden aynı ID eklenince erişilebilir. Hesabı tamamen silme ilgili hikâye dosyalarını da temizler; bağlantıyı kaldırma arşivi korur.

## Kaynak ve doğrulama

Kaynak sözleşmesi projede sabitlenmiş [instagrapi 3.2.0 story okuyucusu](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/mixins/story.py), [alan ayrıştırıcısı](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/extractors.py) ve [StoryMedia modeli](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/types.py). Bunlar Meta'nın resmî API erişim garantisi değildir. `test-fixtures/stories.json` yapaydır; gerçek hesaptan alınmış yanıt diye sunulmaz.

Çevrimdışı kontroller `test_stories.py` ve paketli `check-stories-desktop.mjs`: gerçek DPAPI/üretim taşıma sınırı, sadece GET, yanlış sahip, bilinmeyen/boş alan, medya başlığı/kapasite/yönlendirme/kesik indirme, yeniden başlatma, deduplikasyon, önceki arşivin korunması, 429 sonrası tek deneme, iptal, disk/temizleme hatası, medya dosyası kimliği ve hesap ayrımı. JPEG görsel testi gerçekten çözülüp gösterilen sentetik görseldir; birim testindeki MP4 başlığı oynatılabilir video kanıtı değildir.

3 Kasım 2026'da çalıştırılan son kaynak/paket kontrolleri: **235 Python testi** (11 hikâye testi dahil; 36,112 saniye), **57 Node testi**, `npm run check`, Windows worker derlemesi ve **52 paketli kontrol** (6 hikâye + 36 genel + 10 çoklu hesap) geçti. Paketli hikâye ekranında gerçek şifreli sentetik JPEG açıldı; dört dil, tarih filtresi, koyu tema, temizleme, hesap sınırı, Blob kaynağını bırakma ve ağsız test modu doğrulandı. JavaScript hatası veya renderer HTTP isteği görülmedi. Ekran görüntüleri ve özet yerel `artifacts/desktop-0.2.37-qa/stories/` dizinindedir. Canlı hikâye, gerçek MP4/ses ve sunucu davranışı doğrulanmadı. Uygulama ailesi CPU/RAM, uzun süreli pil/depolama maliyeti ve önceki sürümle performans karşılaştırması ölçülmedi.
