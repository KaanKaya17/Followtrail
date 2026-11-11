# Geliştirme notları

## En yüksek öncelik · Instagram API alan adlarının otomatik keşfi

İlk çevrimdışı prototip hazır. İki HAR kaydını karşılaştırıp gözlenen alan adı ve istek şablonu adaylarını çıkarır. Chrome'da takip edilenlerin ilk iki sayfası gerçek kayıtla doğrulandı. Takipçilerin web kaydı ve Android kayıt yolu açık. [Kullanım ve doğrulama kapsamı](API-HOST-DISCOVERY.md).

Instagram'ın profil, takipçi ve takip edilen isteklerini hangi sunucu alan adına gönderdiğini bir geliştirici aracı otomatik belirlemeli. İlk kapsam iki liste yönü. Araç, test hesabında Instagram'ın kendi arayüzünde liste açılırken ve sonraki sayfa yüklenirken oluşan ağ olaylarını karşılaştırır. Alan adı, yol, HTTP yöntemi ve varsa GraphQL sorgu kimliğini ayrı ayrı gösterir. Web ve Android bulgularının hangi istemciden geldiği belirtilir.

- Çıktı, eski ve yeni alan adını ve değişen istek parçalarını gösterir. Tanı raporu yalnız istek şablonu, yanıt alanları ve sayfalama kanıtını içerir. Oturum değerleri test cihazında kalır.
- İlk ve sonraki sayfanın doğru hesabın listesine ait olduğu, devam bilgisinin çalıştığı ve kısıtlı yanıtın tam liste sayılmadığı doğrulanır. Tracker doğrulanmış alan adı ve istek şablonunu sürüm güncellemesindeki izin listesiyle kullanır.
- Windows 0.2.60, okuma tanısı ve ilgili okuyucuyu duraklatmayı uygular. Uç nokta değişikliği şüphesi geliştirici aracında bir test kaydıyla doğrulanır. [Okuma tanısı](wiki/Sorun-Giderme.md#otomatik-okuma-duraklatıldı).

## Güncel durum — 5 Kasım 2026

Windows 0.2.56 / Android 0.1.86: [profil yanıtı ve eşzamanlı görsel paylaşımındaki üç boşluk kapandı](RESPONSE-SHARING-TEST.md). Ortak bütçe, %80 otomatik profil eşiği ve 6–10 saniye rezervasyonu uygulandı. Android CAPTCHA sonrası oturum oluşması görüldü; profil HTTP 429 aldığı için uçtan uca bağlantı/liste okuması ve yeni cihaz testleri açık. Aşağıdaki tarihli notlar karar geçmişini korur; uygulama durumu için [sürüm sayfası](wiki/Surum-ve-Destek.md) önceliklidir. Resmî ZIP içe aktarma ve işlevsel web hizmeti henüz yapılmadı.

## Resmî Instagram arşivi ve veri taşınabilirliği — 1 Kasım 2026

Kullanıcı bu konuya daha sonra dönülmesini istedi; ZIP içe aktarma veya resmî veri taşıma entegrasyonu henüz geliştirilmedi. Seçili takipçi/takip edilen JSON dosyalarını yerel okuyup arşivler arasında karşılaştırma değerlendirilecek. Kendi hesabının dışa aktarımı, izlenen başka kişilerin bütün listelerini sağlamaz. Arşivlerde sayısal kimlik ve tarih anlamları gerçek dosyadan doğrulanmalı; mevcut API kimlikleriyle kullanıcı adına bakarak sessizce birleştirilmemeli. Meta Data Portability için uygulama/onay süreci var; takipçi/takip edilen kapsamı ile tamamen cihazda çalışma uyumluluğu doğrulanmadı. Kaynak: https://developers.facebook.com/docs/data-portability .

## Android bağlantısı — 2 Kasım 2026 güncel durum

0.1.73'te bekleyen web oturumunu etkin hesaplardan ayrı ve şifreli koruma, açık devam/silme ekranı, geçersiz oturum temizliği ve aktarım hatalarını ayırma uygulandı; kaynak ve sürüm etiketi GitHub'dadır. 2 Kasım canlı telefon testinde kod sonrası profil aşamasına geçildiği, profil GET'inin HTTP 429 aldığı ve bekleyen oturumun silinmeden devam ekranında kaldığı doğrulandı. Önceki “bu değişiklik henüz uygulanmadı” notu artık geçerli değildir. Tam hesap bağlantısı ve canlı liste okumaları başarılı sayılmaz.

3 Kasım devam testi: kurulu 0.1.73'te bekleyen oturum korunmuştu. Kullanıcının mola sonrası tek tamamlama eylemi, sayacı 92'den 93'e çıkardı; yeni son yanıt 3 Kasım 19:29 / HTTP 429 oldu. Devam ekranı yeni mola bitişini **4 Kasım 19:29** gösterdi. Yeni parola/kod veya alternatif istek denenmedi; tam bağlantı hâlâ doğrulanmış değildir.

Kalan işler: profil GET'inin adres/başlık/oturum aktarımını incelemek ve 429'un nedenini ayırmak; yalnız aynı bekleme/deneme döngüsünü tekrarlamak çözüm sayılmaz. Profil okuması başarılı olursa kendi profil/liste okumaları, izlenen profil ve çoklu hesap geçişi gerçek Android akışında doğrulanmalı. Güncel kaynak incelemesinde girişin sayfadan gelen uygulama kimliğini aktarabildiği, profil okuyucusunun ise sabit bir uygulama kimliği kullandığı görüldü; gerçek değerlerin farklı olduğu veya bunun 429'u açıkladığı doğrulanmadı. Mola kaldırılmaz ve başarısız istekten sonra alternatif uç/taşıyıcıyla tekrar yapılmaz. Ayrıntılar CAPTCHA-FLOW-INVESTIGATION.md'de.

## Threads / Facebook genişlemesi — 29 Ekim 2026

Kullanıcı ileride değerlendirmek üzere saklanmasını istedi; şu anda geliştirme başlatılmadı.

- Önce Threads'te dar kapsamlı bağlantı ve veri erişim prototipi: hangi profil/sayaç/içerik alanları gerçekten alınabiliyor doğrulanmalı. Instagram'daki tam liste karşılaştırmasının diğer platformlarda da mümkün olduğu varsayılmamalı.
- Meta'nın ortak hesap/giriş deneyimi, uygulamamıza bütün ürünler için ortak veri erişim yetkisi vermez. Her platformun yetkilendirmesi ve erişim yaşam döngüsü ayrı ele alınmalı. Kullanıcı her bağlantıda yeniden parola yazmak zorunda olmayabilir; platformun desteklediği resmî giriş kullanılmalı.
- Facebook Sayfa erişimi ile kişisel hesap arkadaş/takipçi takibi ayrı kapsamdır. Önce veri yetkileri doğrulanmalı; erişilemeyen alanlar için özellik sözü verilmemeli.
- Arayüz, yerel geçmiş, bildirimler ve şifreli kayıt altyapısı yeniden kullanılabilir; platform okuyucuları, kimlikler, oturumlar ve izinler ayrı sahiplenilmeli. Mevcut yalnız okuma ve kullanıcı verisini paylaşmama şartı korunmalı. Uygulama sırrı gerektiren OAuth adımları, tamamen yerel dağıtım hedefiyle ayrıca tasarlanmalı; APK/EXE içine sır gömülmemeli.
- Kaynaklar: [Meta Account duyurusu](https://about.fb.com/news/2026/04/meta-account/), [Meta Threads API](https://www.postman.com/meta/threads/documentation/dht3nzz/threads-api), [Meta Facebook API](https://www.postman.com/meta/facebook/documentation/r56bjfd/facebook-api).

## Görsel incelik önerileri — 29 Ekim 2026

Kullanıcının paylaştığı altı CSS önerisi mevcut kaynaklar ve kayıtlı açık/koyu ekran görüntüleriyle değerlendirildi. Kullanıcının uygulama yetkisinden sonra Windows/web 0.2.16 ve Android 0.1.36'da seçici sayısal hizalama, ölçülü geri bildirim, kart üst ışığı ve tema uyumlu kaydırma çubukları uygulandı. Blur ve sürekli nabız eklenmedi. [Görsel doğrulama](VISUAL-POLISH-TEST.md); CPU/bellek kıyası yapılmadı.

- Öncelik: ana sayaçlar ve sayısal sütunlarda seçici `tabular-nums`; basma geri bildirimini mevcut 1px düğme hareketiyle tutarlı kullanmak; koyu/OLED kartlarda mevcut yüzey ayrımını koruyan hafif kenar/üst ışık; okunur tema uyumlu scrollbar.
- Geri sayımda tabular rakamlar, düğme basma geçişi ve işlem sırasında nabız zaten var. Yeni davranış bunları çoğaltmamalı. Sabit rakam genişliği, basamak sayısı artışındaki genişlemeyi tek başına çözmez.
- Cam efekti isteğe bağlı görsel deneme olabilir; mevcut sabit yan menünün arkasında detay olmadığı için kazancı sınırlı olabilir. CSS blur, native Mica değildir. OLED'de katı siyah yüzey tercih edilmeli; ek GPU maliyeti ölçülmeden yok sayılmamalı.
- Önerilen "inner highlight" CSS'i inset içermez; dış gölge ve kenarlık değiştirir. Sabit beyaz yüzde 8 kenarlık mevcut koyu kenardan daha zayıf olabilir. Sabit renk yerine ortak tema kaynağı kullanılmalı; Sistem koyu modu da kapsanmalı.
- Nabız yalnız gerçekten yürüyen işlem için; izleme açık fakat bir sonraki kontrol beklenirken sürekli çalışıyor izlenimi vermemeli. Görünürlük ve azaltılmış hareket tercihine uymalı. Scrollbar gizlenmemeli veya tutması zor hale getirilmemeli.
- CSS örneklerindeki bazı sınıflar mevcut arayüzde yok. Web/Windows CSS değişikliği native Android'e otomatik geçmez; görsel dil Android bileşenlerinde ayrıca eşlenmeli.

## Anonim erişim karşılaştırması — 28 Ekim 2026

Windows 0.2.8'de ilk adım: sağlıklı bağlı oturumda hedef ID bulunamadığında son bilinen kullanıcı adına tek çerezsiz profil sayfası isteği. Pozitif sonuç için aynı sayısal kimlik doğrulanır. İki tarafın da bulunamaması nedeni açıklamaz; giriş ekranı, kimliği doğrulanamayan HTTP 200 ve sınırlama belirsizdir. Arayüz yapılan yöntemi ve sınırlamaları açıklar.

Sonraki geliştirmeler:

- **Anonim ID → güncel kullanıcı adı:** Kurulu kütüphanenin `user_info_v2_gql` yolu, oturum olmadan sorgu hazırlayabildi; sunucunun anonim isteği kabul ettiği doğrulanmadı. Üretime eklenmedi. Kullanıcı adı değişikliği + engelleme sorununu çözebilmesi için aynı ID ile canlı, tekrarlanabilir yanıt gerekir. Giriş/sınırlama durumunda kayıtlı oturum enjekte edilmemeli ve alternatif adreslerle sınırlama aşılmamalı.
- **Çerezsiz GET ve tarayıcı görünümü:** Kullanıcının oturumsuz tarayıcıda profil görmesi, ham HTTP GET'in aynı kimliği döndürdüğünü garanti etmiyor. Mevcut ayrıştırıcı yalnız doğrulanabilen profil JSON'u / metaverisiyle olumlu sonuç verir. Yeni biçimler format temelli kanıt ve testle eklenmeli; önerilen hesap listelerinden eşleşme çıkarılmamalı. Gerekirse ileride yerel, ayrı oturumlu tarayıcı desteği değerlendirilir; mevcut kullanıcı tarayıcısı veya hesap çerezi kullanılmaz.
- **Kalıcı ID / kullanıcı adı geçmişi:** Başarılı mevcut gözlemlerden öğrenilen ad değişikliklerini dönen sayaç geçmişinden ayrı saklama. İki kontrol arasında hem adını değiştirip hem engelleyen kişinin yeni adı hiç gözlenmemiş olabilir; bu sınır açık kalmalı.
- **Bağımsız kontrollü doğrulama:** Yetkili test hesaplarıyla normal görünürlük, yalnız engelleme, yalnız ad değişikliği, ikisi birlikte ve geçici hesap kapatma senaryoları. Kişisel hesabı dondurmak test önkoşulu yapılmamalı. Başarı yüzdesi ancak yeterli ölçümle raporlanmalı.
- **Platform kapsamı:** Bu erişim karşılaştırması Windows'tadır. Android'e işlev taşınırken aynı sınıflandırma, oturum ayrımı, yerel mola ve Türkçe/İngilizce açıklamalar korunmalı. Web tasarım önizlemesi gerçek anonim kontrol yapıyor gibi sunulmamalı.

## Uzun dönem sayaç analizi — uygulandı (2026-11-03)

Windows 0.2.36 ve Android 0.1.75'te günlük sayaçlar profil/yıl başına ayrı şifreli dosyalarda saklanır; ayrıntılı geçmişin budanması günlük kayıtları silmez. UTC günleri, bilinen sayaçların ilk/son/minimum/maksimum değerleri ve kontrol sayısı korunur. Yerel aralık sorgusu, 720 nokta sınırı, temizleme işareti ve Windows yedeği eklendi. [Doğrulama ve ölçüm](DAILY-STATS-TEST.md). Android cihaz testi sonraya bırakıldı; canlı giriş sorunu tamamlanmış sayılmaz. Parçalı/büyük arşiv yedeği henüz uygulanmadı; mevcut tek dosyalık yedek sınırı korunur.


## Threads otomatik doğrulama — ertelendi (2026-11-02)

Şimdilik Instagram profilindeki açık Threads adresi veya etkin Threads rozetinde bildirilen kullanıcı adı gösterilir. 0.2.34/0.1.74'te rozet desteği, gerçek Instagram profil yanıtında `show_text_post_app_badge=true` ve kullanıcı adı etiketinin geldiği doğrulandıktan sonra eklendi; ek Threads isteği yoktur. İleride özel API `pk`/Instagram kimliği ilişkisi, resmî API uygulama kapsamlı kimliklerinden ayrı doğrulanmalı. 2023 tarihli [özgün araştırma](https://github.com/m1guelpf/threads-re) ortak ID kullanımını belgeliyor; mevcut hesapta canlı kimlik eşleşmesi kanıtlanmadı. Çerezsiz profil sayfası girişe yönlendi; tek profil JSON GET denemesi HTTP 429 verdi ve tekrar yapılmadı. Ayrı Threads girişinin kesin gerekli olduğu henüz sonucuna varılmamalı.

## Paylaşım profili ve video analizi — ertelendi (2026-11-02)

Kullanıcı Reels/repost ve erişilebilir Threads paylaşımlarından ilgi alanları, içerik türleri ve gözlenen anlatım tarzını özetleyen bir özelliğe daha sonra dönmek istiyor. Her yorumun içerik dayanağı, tarih aralığı ve incelenen örnek sayısı gösterilmeli; repostun benimseme anlamına geldiği veya sabit kişilik/sağlık özelliği kanıtladığı varsayılmamalı. Kullanıcı verisinin dışarı gönderilmemesi şartı korunarak yerel model seçeneği değerlendirilmeli. Uygulamada henüz medya okuyucu veya AI analiz entegrasyonu yok; bağlantısı kullanıcı tarafından verilen tek herkese açık Reels'te oturumsuz tarayıcıdan üç video karesi okunabildi, ses ve repost yapan hesap tespiti sınanmadı. Öncelik Android girişinin tamamlanmasıdır.
# Hikâye arşivi — 3 Kasım 2026

0.2.39: Arka plan sonucu açık takvimin geçici verisini temizlediğinde ay düğmelerinin durması giderildi; arşiv görünümü kendi hesap/target bağlamını korur. Yeni görüntüleyicide gün seçilince solda küçük takvim, ortada yerel medya, sağda etiket/kaynak/gönderi referansları açılır. Kullanım notları katlanır bölüme taşındı. API okuyucu/otomasyon davranışı değiştirilmedi; özgün yeniden paylaşım kaynağı henüz canlı doğrulanmadı.

Windows 0.2.37'de elle başlatılan deneysel GET okuyucusu ve şifreli arşiv hazırlandı. 0.2.38'de aylık takvim/gün sayıları/yerel günlük açılım eklendi. İki test hesabında hikâye gizlemesi kaldırılınca aynı geçerli oturumla 1 gerçek WEBP fotoğrafının indirilmesi, şifreli kaydı ve yerel bütünlüğü doğrulandı. Boş sonuç açıklaması güncellendi. Gerçek MP4/ses/sticker katmanları, etiketli yeniden paylaşım ve izleyenler listesi yan etkisi henüz doğrulanmadı; otomasyon kapalı. Android 0.1.81 ortak takvim/görüntüleyici ve şifreli arşivi içerir; cihaz ve yeni canlı senaryolar [test planında](STORY-ARCHIVE-TEST.md) bekler. Kişi etiketinden yeniden paylaşım kaynağı uydurulmaz; erişilemeyen/gizli/geçmişte kaçırılan hikâyeler için erişim vaadi yoktur. Arşiv mevcut yedeğe dahil değildir; medya yedeği ileride değerlendirilecek. Her iki platformda ayrıntılı geçmiş saklama tercihi hikâyelere de uygulanır.
