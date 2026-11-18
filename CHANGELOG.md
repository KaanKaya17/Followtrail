# Değişiklikler

## Windows 0.2.92 — 2026-11-18

- **Hesap bağlamadan ZIP inceleme daha güvenilir:** Bozuk bir satır atlanıp sayılır; okunamayan dosya parçası yalnız kendi listesini **Kısmi** yapar, diğer liste etkilenmez. Önceden tek satır iki listeyi birden okunamaz yapıyordu.
- **Takip ilişkileri** (eski adı “ZIP takip karşılaştırması”) 512 MB üstü tam dışa aktarımları da okur ve açık ZIP’i kullanır; dosyayı yeniden sormaz.
- Son takipten çıktıkların, bekleyen ve gelen istekler, yakın arkadaşlar, engellediğin ve kısıtladığın hesaplar, hikâyeni gizlediklerin ve kaldırılan öneriler tarihleriyle gösterilir. **Seni takip etmeyen yakın arkadaşların** görünümü eklendi.
- **İki ZIP karşılaştır:** Kaynak kartlarında iki ZIP’in liste sayıları, sonuçlarda takip tarihleri, yöne göre başlıklar (**Yeni takipçiler**, **Takipten çıkanlar** …), aynı takip tarihine dayanan **Kullanıcı adını değiştirmiş olabilir** eşleşmesi, arama, tarih sıralaması, **CSV olarak kaydet** ve sürükle-bırak. Sonuç başka görünüme geçince kaybolmaz.
- Dosya adındaki hesap, ZIP’te hesap bilgisi yoksa “(dosya adından)” etiketiyle kullanılır. Özete yorumlar ve mesaj sohbet sayısı eklendi; çok büyük kayıtlar “Çok büyük” olarak görünür; eski klasör düzenleri okunur.
- Gizli kalması gereken **Daha fazla göster** düğmeleri artık görünmez; arama ve filtre satırı düzeltildi. [Ayrıntılar ve doğrulama](docs/ZIP-ARCHIVE-TEST.md).

## Windows 0.2.91 — 2026-11-18

- **Takip geçmişi:** Her takip listesi için kalıcı üyelik defteri. Kişinin ilk ve son görüldüğü tarama, listeden çıktığı aralık ve geri döndüğü tarama şifreli kasada saklanır; son altı tarama dönüşünden etkilenmez.
- **Takip Listeleri → İlişki filtresi** iki yeni görünüm sunar: **Geri gelenler** ve **Ayrılanlar**. Satırlarda geri dönüş zamanı, önceki çıkış aralığı, son görülme ve ilk görülme gösterilir. Normal listede geri dönen hesap **Yeni görüldü** yerine **Geri döndü** rozetini alır.
- Kişi panelinde **Takip geçmişi** bölümü, bağlı hesabın kendi iki listesindeki hareketleri gösterir. Liste değişikliği kaydı ve Windows bildirimi geri dönen hesap sayısını içerir.
- Zamanlar iki tam tarama arasını gösterir; kesin olay saati üretilmez. Kısmi taramalar defteri değiştirmez. Eski kasalar saklanan tam taramalardan bir kez doldurulur. Ayrıntılı geçmiş saklama süresi ve profil silme defteri de kapsar. Yeni Instagram isteği yoktur.
- Dil değiştirildiğinde yüklenmiş liste satırları artık seçilen dilde yeniden çizilir. [Tasarım ve doğrulama](docs/FOLLOW-HISTORY-TEST.md).

## Windows 0.2.90 ve Android 0.1.103 — 2026-11-18

- Hikâye fotoğraf ve videoları Instagram API sırasından ayrı, kendi medya şeridinde indirilir: dosyalar arasında 1–3 saniye, cihaz başına 400/saat ve 4.000/gün medya sınırı. Medya indirmesi API bütçesinden hak harcamaz.
- 10 öğelik bir hikâye kontrolü API bütçesinden 11 yerine 1 hak kullanır; çevrimdışı ölçümde bekleme 80 saniyeden 18 saniyeye indi. API istek aralığı (6–10 saniye), bütçeler ve 429 kuralları değişmedi.
- API molası sürerken medya indirilmez; CDN'den gelen HTTP 429 ortak molayı başlatır. Medya sınırı dolarsa hikâye bilgileri kaydedilir, eksik dosyalar sonraki kontrolde indirilebilir. [Ölçüm ve doğrulama](docs/MEDIA-LANE-TEST.md).

## Android 0.1.102 — 2026-11-18

- Android 7 ve öncesinde yaklaşık 100 KB'den büyük şifreli kayıtlar (hesap verisi, takip listesi arşivi, kontrol günlüğü, avatar önbelleği) Keystore'a parça parça verilir. Önceden bu boyuttaki kayıtlar açılamıyor ya da hatalı okunabiliyordu. Önceki sürümün Android 7'de yazdığı yaklaşık 512 KB üstü kayıtlar kurtarılamaz; bunlar için yerel verinin silinip listelerin yeniden hazırlanması gerekir.
- Avatar önbelleği doluyken aynı saniyede yazılan yeni fotoğraf hemen silinmez.
- Android cihaz testleri Android 7 (API 24) ve Android 16 emülatörlerinde geçti; eski WebView'da giriş köprüsü testleri atlanır.

## Android 0.1.101 — 2026-11-18

- Liste hazırlığı yarıda kalan takipçi listesini bitirmeden takip edilenlere geçmez; açıklandığı gibi önce takipçiler, ardından takip edilenler okunur.
- Uygulama açıkken yarıda kalan sonraki liste taraması 15 dakikalık arka plan aralığını beklemeden kaldığı sayfadan devam eder. Yeni tarama başlatma zamanı değişmedi.
- Android cihaz testleri güncel davranışa göre düzeltildi.

## Windows 0.2.89 — 2026-11-18

- Parolalı girişte şifreleme anahtarı, instagrapi 3.2.3 ile Instagram’ın güncel yapılandırma uç noktasından alınıyor.
- Windows 0.2.89’da gerçek hesapla giriş, profil doğrulaması ve iki bağlı hesap doğrulandı. İkinci hesabın ilk liste isteği HTTP 200 döndürdü. [Sürüm ve doğrulama](docs/wiki/Surum-ve-Destek.md#windows-0289-yerel-giriş-testi).

## Android 0.1.100

- Geri takip etmeyenler için hesap bazında şifreli istisnalar ve açık bir yerel veri silme etiketi eklendi.
- ZIP özeti dosya kapsamını gösterir. İki ZIP için kaynak önizlemesi, sıra değişimi ve kullanıcı adı uyarısı eklendi.
- ZIP kayıtlarında tam metin araması, aylık beğeni özeti ve içerik/etkileşim takvimi eklendi.

## Windows 0.2.84

- Seni geri takip etmeyenler filtresinde istisnalar ayrı bir alt bölümde görünür ve ana sonuç sayısına dahil edilmez.

## Windows 0.2.83

- İçerik ve hikâye takvimlerinde ay değiştirme okları düğmelerin ortasına hizalandı.

## Windows 0.2.82

- İçerik ve etkileşim takvimi, Instagram ZIP’indeki hikâyeleri, gönderileri ve Reels’leri yerel hikâye kayıtlarıyla birlikte gösterir. İçerik başına mevcut etkileşim sayıları ve medya önizlemeleri açılabilir.

## Windows 0.2.81

- İki ZIP karşılaştırmasında sonuçlardan önce dosya, hesap ve tarih bilgileri yan yana gösterilir. ZIP’lerin sırası değiştirilebilir ve kullanıcı adları farklıysa uyarı gösterilir.

## Windows 0.2.80

- ZIP arşivi dosya, hesap ve tarih bilgilerini üstte gösterir. Takip listelerinin ve mesajların kapsamını açıklar.
- Windows arşiv ekranına ZIP sürükleyip bırakarak açılabilir.

## Windows 0.2.79

- Instagram ZIP araması uzun mesajların tamamını tarar ve sonuçta eşleşmenin çevresinden kısa bir metin gösterir.
- Aylık beğeni özetinde aradaki boş aylar “ZIP’te kayıt yok” olarak gösterilir.

## Windows 0.2.78

- Ayarlar’da Windows yedeğini geri yükleme ve taşınabilir geçmişi arşiv olarak açma ayrı açıklamalarla gösterilir.
- Hesap eklenmeden önce Özet, Instagram ZIP’ini açma ve canlı takibi başlatma seçeneklerini gösterir. Yeniden bağlantı uyarısı kayıtlı hesaplar için gösterilir.

## Android 0.1.99

- Dosya seçici açıkken ekran yeniden oluşturulursa geçmiş yedeği ve Instagram ZIP karşılaştırması kaldığı yerden devam eder.
- Geçmiş yedeğinin parolası dosya konumu seçildikten sonra istenir.

## Windows 0.2.77 / Android 0.1.98

- Taşınabilir yedekler, Windows’ta kaydedilebilen 2.000 karakterlik notları Türkçe karakterler ve emojiyle birlikte korur.

## Windows 0.2.76 / Android 0.1.97

- Parolalı Tracker yedeği, kayıtlı profilleri, notları, favorileri, profil gözlemlerini, tamamlanmış takip listelerini ve günlük özetleri Windows ve Android arasında taşır. Geçmiş ayrı bir ekranda açılır.

## Windows 0.2.75 / Android 0.1.96

- Özet’teki ZIP kısayolu seçilen dosyayı tam arşiv panelinde açar. Android paneli arşiv özeti, veri kategorileri ve iki ZIP’in takip listelerini karşılaştırma seçeneği sunar.

## Windows 0.2.74 / Android 0.1.95

- Hesap bağlı değilken Özet ekranından doğrudan Instagram ZIP seçilebilir. Takip listeleri hesap bağlamadan açılır ve dışa aktarma adımlarına aynı ekrandan ulaşılır.

## Windows 0.2.73

- Veri Arşivi, ZIP’te bulunan önemli sayıları tıklanabilir kartlarda ve tarihli gönderi beğenilerini aylık grafikte gösterir.
- Tüm arşivdeki dosya adları ve okunabilir JSON kayıtları kategoriler halinde aranabilir.
- İki ZIP’in takipçi ve takip edilen listeleri cihazda karşılaştırılır. Sonuçlar kullanıcı adı ve arşiv kapsamına göre gösterilir.

## Windows 0.2.72

- Bildirimler menüsünde ve üstteki zil simgesinde okunmamış bildirim sayısı gösterilir. Kayıt açıldığında sayaç güncellenir.

## Windows 0.2.71

- Instagram Veri Arşivi, Hesaplar Merkezi bağlantısı ve cihaza JSON ZIP indirme adımlarını gösteren bir rehber içerir.

## Windows 0.2.70

- Arşivde takip ve etkinlik kayıtlarına tarih aralığı ve sıralama eklendi. Etkinlikler türe ve güne göre, profil değişiklikleri ve mesajlar güne göre gösterilir.
- Mesajlar aranabilir sohbet listesiyle açılır. Biyografi değişiklikleri vurgulanır, tarihli istatistikler grafik olarak gösterilebilir. Profil ve web bağlantıları varsayılan tarayıcıda açılır.

## Windows 0.2.69

- Arşiv arama alanı temaya uygun kenarlık ve klavye odağı kullanır. Profil değişiklikleri önceki ve yeni değerleri ayrı kutularda, istatistikler önemli sayıları öne çıkaran kartlarda gösterir.

## Windows 0.2.68

- Instagram Veri Arşivi, Zaman Çizelgesi’nin altında ayrı bir sekme oldu. Takipler, profil geçmişi, sohbetler, paylaşımlar, istatistikler, etkinlik ve rehber kayıtları aranabilir kartlarda gösterilir.
- ZIP takip karşılaştırması ve dosya tarayıcısı arşiv sekmesinden açılır. Bazı Instagram dışa aktarımlarında hatalı kodlanmış Türkçe karakterler okunabilir görünüme alınırken düzeltilir.

## Windows 0.2.67 / Android 0.1.94

- Instagram Veri Arşivi paneli, cihazdaki tam dışa aktarım ZIP'ini kategorilerle açar. Dosya ve klasör araması, JSON kayıtları arasında gezinme, yerel medya önizlemesi ve tekil dosya kaydetme eklendi.

## Windows 0.2.66 / Android 0.1.93

- Instagram JSON arşivindeki takip tarihleri ZIP ekranında gösterilir. Listeler en yeni veya en eski takip tarihine göre sıralanabilir ve bir tarih aralığına göre filtrelenebilir.
- Karşılıklı takiplerde arşivde varsa her iki yönün tarihi gösterilir.

## Windows 0.2.65 / Android 0.1.92 — 2026-11-12

- Instagram ZIP yükleme, JSON dosyalarının yanında HTML biçimindeki takipçi ve takip edilen listelerini de açar.
- Gerçek bir Instagram HTML arşivinden 3 takip edilen hesap Windows paketinde ve Android okuyucusunda açıldı. Arşivde takipçi listesi yer almıyordu. 39 Windows paket kontrolü, 4 Python arşiv testi ve 192 Android birim testi geçti. Android dosya seçicisi cihazda denenmedi.

## Windows 0.2.64 / Android 0.1.91 — 2026-11-12

- Instagram’dan JSON biçiminde alınan ZIP ile takipçi ve takip edilen listeleri açılabilir. Her iki liste varsa karşılıklı ve tek yönlü takipler kullanıcı adına göre karşılaştırılabilir.
- Arşiv görünümü cihazda çalışır ve Instagram hesabı bağlamayı gerektirmez. Arşivdeki listeler açık ekran için yüklenir.
- 39 çevrimdışı Windows paket kontrolü, 3 Python arşiv testi ve 191 Android birim testi geçti. ZIP akışı örnek arşivlerle sınandı. Android dosya seçici gerçek cihazda denenmedi.

## Windows 0.2.63 / Android 0.1.90 — 2026-11-11

- Profil ayrıntılarına Bu hesap hakkında bölümü eklendi. Instagram’daki profil açılabilir ve görünen katılım ayı/yılı, hesap ülkesi ile kullanıcı adı değişiklik sayısı isteğe bağlı olarak elle kaydedilebilir.
- Alanlar kayıtlı profile ve bağlı hesabın yerel şifreli deposuna aittir. Kaynak elle girilen kayıt olarak gösterilir. Kayıt düzenleme Instagram’a istek göndermez.
- 38 çevrimdışı Windows paket kontrolü ve 189 Android birim testi geçti. Android ekranı fiziksel cihazda denenmedi.

## Windows 0.2.62 / Android 0.1.89 — 2026-11-11

- Profil zaman tünelinde iki kayıt seçilip aynı profil yan yana karşılaştırılabilir. Biyografide silinen ve eklenen bölümler ayrı sütunlarda işaretlenir. Kayıtlı fotoğraf, ad, bağlantı ve sayaçlar mevcut olduğunda gösterilir.
- Karşılaştırma cihazdaki gözlem ve görsel arşiv verileriyle çalışır. Eksik alanlar kayıt yok olarak gösterilir.
- 4 JavaScript fark testi, 7 zaman tüneli kontrolü, 37 Windows paket kontrolü ve 188 Android birim testi geçti. Android arayüzü fiziksel cihazda denenmedi.

## Windows 0.2.61 / Android 0.1.88 — 2026-11-11

- Ortak hesaplarda kayıtlı profil fotoğrafları iki kaynak listeden kullanılabilir. Görsel gerekiyorsa mevcut fotoğraf yükleyicisi kullanılır.
- Bilinen takipçi ve takip edilen sayıları kullanıcı adının altında gösterilir. Sayısı bilinmeyen üyelerde boş sayaç satırı görünmez. Karşılaştırma satırları kendi başlarına profil okuması başlatmaz.

- 47 Python liste testi, 6 JavaScript profil paneli testi, 14 çevrimdışı arayüz kontrolü, 37 Windows paket kontrolü ve 187 Android birim testi geçti. Android ekranı fiziksel cihazda henüz denenmedi.

## Android 0.1.87 — 2026-11-11

- Yeni kaydedilen profillerin takipçi ve takip edilen listeleri otomatik liste hazırlığına katılır. İlk sayı uyuşmazlığında bir erken tekrar planlanır.
- Son tam takip edilen listesine göre takip edilmeyen gizli profiller için erişim açıklaması gösterilir. İki profilin ortak hesapları cihazdaki tam veya kısmi listelerden karşılaştırılabilir.
- Profil ve iki liste yönü için okuma tanısı, uyumluluk duraklatması ve başarısız devam bilgisinden ilk sayfaya dönüş eklendi.
- 187 Android birim testi, dört dilde 820 metin kaynağı ve 17 wiki sayfasındaki 229 bağlantı doğrulandı. Android cihaz test paketi derlendi. 0.1.87’nin yeni ekranları telefonda henüz denenmedi.
- 0.1.86 fiziksel testinde mevcut oturumla profil, iki liste ve kullanıcı tarafından bildirilen liste farkı bildirimi çalıştı. Bekleyen web oturumunu bağlama denemesi HTTP 429 aldı.

## Windows 0.2.60 — 2026-11-11

- Profil ve iki liste yönü için ayrı okuma tanısı. Aynı uyumluluk hatası üç kez üst üste görülürse ilgili otomatik okuma duraklatılır. İstek bilgileri gözlenen HTTP durumunu ve nedeni gösterir.
- Reddedilen devam bilgisi 400 veya 404 sonrasında kaldırılır. Uygun bir sonraki tarama ilk sayfadan ilerler. Son tam liste korunur. Başarılı elle okuma duraklatmayı kaldırır.
- 119 seçili Python testi, 37 çevrimdışı Windows paket kontrolü ve dört dil denetimi geçti.

## Windows 0.2.59 — 2026-11-11

- Takip Listeleri ekranında iki profil için ortak takipçi ve ortak takip edilen karşılaştırması. Eşleşmeler hesap kimliğiyle bulunur. Kısmi kaynaklar açıkça belirtilir ve kaydı olmayan bir liste sıfır ortak hesap olarak gösterilmez.
- Sonuçlar kayıtlı listelerden sayfalı ve aranabilir biçimde açılır. Karşılaştırma yereldir.
- 100 seçili Python testi, 8 JavaScript testi ve 37 çevrimdışı Windows paket kontrolü geçti.

## Windows 0.2.58 — 2026-11-07

- Gizli bir profil bağlı hesabın son tam takip edilen listesinde yoksa ve liste son sayaçla uyuşuyorsa, Takip Listeleri ekranı erişim şartını açıklar. Liste kapsamı veya sayısı hatasında aynı açıklama gösterilir.
- 73 seçili Python testi ve 36 çevrimdışı paket kontrolü geçti.

## Windows 0.2.57 — 2026-11-07

- Yeni kaydedilen profillerin iki liste yönü ilk hazırlık sırasına alınır. Kullanıcının otomasyon ve izleme tercihleri korunur. İlk tam kayıt oluştuğunda yalnız izlenen profiller düzenli taranmaya devam eder.
- İlk taramada sayı uyuşmazlığı için yaklaşık 30 dakika sonra bir erken tekrar planlanır. Uyuşmazlık sürerse günlük plan geçerlidir. Kısmi liste korunur ve fark hesabına katılmaz. Liste ekranı ilk kayıt durumunu ve varsa okunan/beklenen üye sayısını gösterir.
- 112 seçili Python testi, 36 paketli Windows kontrolü, ortak kaynak ve dört dil denetimi geçti. Canlı Instagram testi yapılmadı.

## Windows 0.2.56 / Android 0.1.86 — 2026-11-05

- Windows'ta başarılı erişim kontrolünün doğrulanmış hedef profili diğer profil tüketicilerine aktarılır. Eksik profil alanı geçerli görünürlük sonucunu bozmaz; sağlık sorgusu korunur.
- Panelde okunmuş liste kişisi kaydedilirken backend'in bağlantı kapsamlı bilgisi, ilk gözlem zamanı korunarak kullanılır; ek sorgu veya yeni kontrol bildirimi üretilmez. Açık yenileme yeni okuma yapar.
- Windows ve Android görsel yükleyicilerinde aynı kaynak için eşzamanlı tüketiciler tek indirmeyi paylaşır; iptaller bağımsızdır. Eski iş yeni aynı anahtarlı işi silemez. Android'in mevcut profil kaydetme aktarımı korunur.
- [Doğrulama ve cihaz testi sınırı](docs/RESPONSE-SHARING-TEST.md). Günlük/saatlik sınırlar ve 429 davranışı değişmedi.

## Windows 0.2.55 / Android 0.1.85 — 2026-11-05

- Kullanıcının isteğiyle hesap başına yerel günlük bütçe 1.200 → 3.000, cihaz toplamı 2.400 → 6.000 olarak ortak kaynaktan artırıldı. Saatlik 120/240 sınırları, otomatik profil işleri için %80 eşiği, 6–10 saniyelik sıra ve 429/Retry-After duruşu korunur. Bu değerler Meta kotası veya güvenli istek garantisi değildir.
- Sayaç geçmişi silinmez. İki platformun sınırlı kayıt kapasitesi günlük cihaz bütçesini ve eski kayıtları taşıyacak şekilde türetilir; tam günlük geçmişte sessiz kırpma veya ek isteğe izin verilmez. [Doğrulama](docs/DAILY-BUDGET-UPDATE.md).

## Windows 0.2.54 / Android 0.1.84 — 2026-11-05

- Windows otomatik liste başlangıcı aynı bağlantıda yeni kaydedilmiş profil sonucunu 60 saniye paylaşır; gereksiz yeni kontrol olayı üretmez. Elle yeni tarama, bağlantı değişimi ve eskimiş bilgi taze okuma gerektirir. Küçük sentetik 82/50 örneğinde 6 → 4, büyük 3000/2000 örneğinde 103 → 102 istek ölçüldü.
- İki platformda otomatik profil işleri hareketli yerel bütçenin %80'inde bekler; listeler ve elle yapılan işlemler aynı tam bütçeyi kullanır. Sayaç geçmişi, toplam kota, 6–10 saniyelik sıra ve 429/Retry-After davranışı korunur.
- Ayarlara Türkçe, İngilizce, İspanyolca ve Brezilya Portekizcesinde günlük profil talebi tahmini eklendi. Android'in mevcut profil/liste paylaşımı korunur; sistem arka plan aralığı değişmedi. [Doğrulama ve sınırlamalar](docs/API-EFFICIENCY-TEST.md).

## Android 0.1.83 — 2026-11-05

- 0.1.82 canlı tanısında görülen gerçek CAPTCHA içerik şemasına uyuldu: boş argümanlar ve `xfb_auth_platform_anti_scripting_content`. Önceki guard bu sorguyu reddediyordu; CAPTCHA görüntüsünün açılması içerik isteğinin geçtiği anlamına gelmiyordu.
- Gözlenen CAPTCHA render bildirimi ve insanın aldığı CAPTCHA sonucunu gönderme işlemlerine, yalnız tam belge/işlem/modül şeması ve açık doğrulama bağlamı kapsamında destek eklendi. Bu girişte tekrar gönderim durdurulur. Parola, kod, CAPTCHA tokenı veya opaque bağlam kaydedilmez; genel sosyal işlem/GraphQL yetkisi açılmaz.
- Eşzamanlı güvenlik istekleri mevcut dört mesaj sınırında sıraya alınır; sonraki işlem öncekinin istek sahipliği bırakıldıktan sonra başlar. CAPTCHA durum metinleri ve ayrı render/sonuç tanısı eklendi. [Gözlem, bağlama kuralı ve sınırlar](docs/ANDROID-CAPTCHA-CONTENT.md).
- Doğrulama: 174 JVM / 12 JavaScript testi geçti; dört dilde 789 Android kaynağı tutarlı. Lint 0 hata / 54 uyarı; imzalı APK ve test paketi derlendi, APK doğrulandı ve test telefonuna kuruldu. Yeni gerçek WebView senaryosu çalıştırılmadı; canlı giriş sonucu bekleniyor.

## Android 0.1.82 — 2026-11-05

- Yeni cihazdaki tanıda CAPTCHA sayfasının `AuthPlatformReCaptchaContentQuery` isteğinin yerel filtre tarafından engellendiği görüldü. Yalnız bu sabit okuma sorgusuna, aynı doğrulama sayfasının query şeması ve APC bağlamı eşleştiğinde destek eklendi. Genel GraphQL/mutation yetkisi açılmadı; gerçek giriş henüz doğrulanmadı.
- CAPTCHA ve mail kodu içerik sorgularına ayrı birer gönderim hakkı verildi; aynı sorgu tekrarlanmaz. Tanıya CAPTCHA şeması ve görünüm belgesi eklendi, bilinmeyen AuthPlatform parametrelerinde yalnız sınırlı ad/tür bilgisi tutulur.
- İstek bilgilerine dört dilde “Tanıyı kopyala” eklendi. Panoya yalnız kullanıcı dokunuşuyla güvenli tanı metni alınır; parola, kod ve çerez değerleri dahil edilmez. Mevcut molalar ve şifreli kayıtlar korunur. [Kanıt ve test sınırı](docs/ANDROID-CAPTCHA-CONTENT.md).
- Doğrulama: 171 JVM ve 11 JavaScript testi geçti; 785 Android kaynağı tutarlı; lint 0 hata / 54 uyarı. İmzalı APK ve instrumentasyon paketi derlendi, APK doğrulandı ve test telefonuna kuruldu. Yeni WebView instrumentasyon senaryoları çalıştırılmadı; canlı giriş sonucu henüz bekleniyor.

## Windows 0.2.53 — 2026-11-05

- Özet ekranında kendi hesabının son profil okumasıyla bütün profil/liste işlerinin en erken zamanı aynı kontrolün iki zamanı gibi gösteriliyordu. Alanlar “Hesabının son kontrolü” ve “Sıradaki otomatik işlem” olarak ayrıldı; dört dilde saniye hassasiyeti eklendi. Gerçek rutin profil aralığı ve API bütçeleri değiştirilmedi.
- Bir dakikalık otomasyonda aynı profilin 60 saniyeden önce yeniden planlanmadığı, farklı profillerin ortak istek sırasını kullandığı çevrimdışı motor testinde doğrulandı. Liste başlangıcındaki profil okuması ve elle kontroller ayrı işlemlerdir. Kullanıcının gerçek 09:27–09:29 istek kaydı incelenmedi; dakika gösterimi tek başına erken profil tekrarı kanıtı değildir.
- Doğrulama: 7 zamanlama testi, 70 JavaScript testi, kaynak/dört dil tutarlılığı, paketli Windows’ta 6 Özet + 36 işlev kontrolü geçti. Aynı dakikadaki 05/15 saniyelik farklı işleri ayıran UI senaryosu sınandı; Instagram isteği yapılmadı.

## Windows 0.2.52 / Android 0.1.81 — 2026-11-05

- Ortak JSON kaynağından üretilen 6–10 saniye istek rezervasyonu, hesap başına 120/1200 ve cihaz başına 240/2400 saat/gün bütçeleri; mevcut deneme kayıtları ve uzun molalar korunur. Android hesap bütçesi ayrıldı, bilinmeyen kimlikli giriş yanlış seçili hesaba yazılmaz.
- Ortak kontrol seçenekleri ve yeni 5 dakika varsayılanı. Android kendi hesabının aralığı ve ayrı arka plan tercihi eklendi; sistemin 15 dakika veya daha geç çalışabilme sınırı devam eder. Mevcut tercihler zorla açılmaz.
- Android'e şifreli yerel hikâye arşivi, ay geçişli takvim, gün görüntüleyicisi, fotoğraf/video ve bilinen etiket/bağlantı/paylaşılan içerik bilgileri eklendi. Açılış yereldir; okuma yalnız kullanıcı onayıyla yapılır. Görüldü/beğeni/yanıt işlemi yoktur. Saklama, profil/hesap silme ve iptal akışları arşivi kapsar.
- Windows sayı uyuşmazlığıyla biten aynı listeyi yarım saatte bir yeniden okumaz; günlük hedef veya yeni profil sayısı kanıtı beklenir. Kısmi kayıtlar/başlangıç listesi korunur. Hikâye ilk yakalanma zamanı ve tekrar sahipliği kontrolü düzeltildi; Ayarlar sürümü çalışan Electron paketinden gelir.
- Android 167 JVM testi geçti; lint 0 hata / 54 uyarı. İmzalı release APK ve instrumentasyon APK’sı derlendi; manifest/mimari doğrulama geçti. Cihaz testleri çalıştırılmadı. Bozuk hesap kaydının arşiv silmesini engelleyen bağımlılık kaldırıldı.
- Çevrimdışı doğrulama: Python 271, JavaScript 69, paketli Windows 36 işlev + 7 hikâye senaryosu geçti. Dört dilde 783 Android kaynağı tutarlı. Gerçek hesap kasası kullanılmadı, Instagram isteği gönderilmedi.
- [Eşitleme, kalan farklar ve doğrulama kapsamı](docs/PLATFORM-PARITY.md). Android cihaz/görsel/video ve canlı giriş testi ertelendi; telefon bağlantısının çözüldüğü iddiası yoktur.

## Android 0.1.80 — 2026-11-04 (Masaüstü web girişinden profil okumaya geçiş)

- Profil/liste/arama web GET isteklerinin ortak uygulama kimliği, giriş ekranının masaüstü UA biçimiyle eşleştirildi. Instagram'ın oturumsuz resmî giriş sayfasındaki `CurrentUserInitialData.APP_ID` gözlemi `936619743392459`; önceki sabit `1217981644879628` idi. [Birincil kaynak gözlemi ve test sınırı](docs/ANDROID-WEB-APP-ID.md).
- Devam ekranındaki isteği gönderen düğme “Profilimi kontrol et ve bağla” olarak ayrıldı. Aynı ekrandan yerel İstek bilgileri açılabilir; uygulama kimliği, son sayaçlı HTTP ve mola zamanları görülebilir. Parola/çerez gösterilmez.
- Mevcut şifreli bekleyen oturum, kayıtlı hesaplar ve 429 molası korunur. Süre, kota, endpoint veya mobil oturum kimliği değiştirilmedi. Bu düzeltme canlı giriş/profil 429 sorununun çözüldüğü iddiası değildir. Windows 0.2.51 değişmez.
- Doğrulama: 154 Android JVM testi, 21 çevrimdışı tarayıcı kontrolü ve dört dilde 730 kaynak kontrolü geçti. Release/test APK derlemeleri, lint (0 hata / mevcut 50 uyarı), v2/v3 imza ve manifest kontrolleri geçti. Fiziksel telefona veri silmeden kuruldu; yeni Instagram profil isteği gönderilmedi. Instrumentasyon testleri yalnız derlendi.

## Windows 0.2.51 / Android 0.1.79 — 2026-11-04 (Hesap bağlamı ve eksik liste tekrarları)

- Windows arka plan hesabı geçici açıldığında arama önbelleği gereksiz sıfırlanmıyor. Fotoğraflar ekranda seçili hesabın arama/listesi üzerinden çözülüyor; açık hesap değişimi ve silme sınırları korunuyor.
- Android sınırlı, belirsiz veya ilerlemeyen listeyi sayı değişince/günlük süre dolunca kendiliğinden tekrar taramıyor. Okunan kişiler saklanıyor; elle yeniden deneme ve yeni bağlantıyla devam mümkün. Liste bilgi penceresinde dört dilde kısa açıklama var.
- Aynı fotoğraf sorgusunu canlı çalışma hesabı üzerinden yorumlayan eski Engine yolu kaldırıldı; liste görünümü kendi fotoğraf kaynaklarının sahibi oldu.
- Üç hata düzeltmeden önce başarısız testle üretildi. [Kontrol kapsamı ve kalan sınırlar](docs/BUG-CHECK-2026-11-04.md).
- Doğrulama: 268 Python, 68 JavaScript ve 154 Android JVM testi; 36 paketli Windows kontrolü geçti. Android release/test APK derlemeleri, lint (0 hata / mevcut 50 uyarı), dört dil, v2/v3 imza ve manifest kontrolleri geçti. Android cihaz testi ve canlı Instagram girişi bu turda yapılmadı.

## Windows 0.2.50 / Android 0.1.78 — 2026-11-04 (Tekrar açılan aramada sonuçları kullanma)

- Profil ekleme aramasında aynı hesaba ait son 6 sorgu, 60 saniye içinde pencere kapatılıp açıldığında yeniden kullanılır. Sonuca bakmak süreyi uzatmaz; sorgu başına en fazla 20 aday, yalnız bellekte saklanır. Ortak politika dosyası ve üretim kontrolü iki platformun sınırlarını eşit tutar.
- Hesap/oturum değişimi, bağlantı kaldırma ve profil silme sonuçları temizler. Gecikmiş yanıtlar temizlenen önbelleği yeniden oluşturamaz. Hatalar saklanmaz; geçerli boş sonuçlar tekrar istek göndermez.
- Windows'ta önbellekten açılan adayın fotoğrafı, başka arama veya yerel işlemden sonra da geçerli hesap/belirteç bağlamıyla yüklenebilir. Arama önbelleğinde bütün uygulama durumu saklanmaz. Dosyaya arama geçmişi yazılmaz; giriş/429 ve tarama ayarları değişmedi.
- [Davranış, gizlilik ve test kapsamı](docs/SEARCH-CACHE-PARITY.md).
- Doğrulama: 266 Python, 68 masaüstü JavaScript ve 151 Android JVM testi; 13 arayüz ve 36 paketli Windows kontrolü geçti. Android release/lint ve imza/manifest kontrolleri geçti. Yerel Android diyalog testi derlendi; cihazda çalıştırılmadı.

## Windows 0.2.49 — 2026-11-04 (Windows kısayol ve görev çubuğu simgesi)

- Başlat kısayolu, EXE simge önbelleğine bağlı kalmak yerine paketteki Tracker ICO dosyasını kullanır. Pencereye Windows uygulama kimliği, yeniden açma adı/komutu ve ICO simgesi açıkça atanır.
- Aynı uygulama kimliği ve EXE adıyla doğrulanan eski InstagramTracker.lnk ve Electron.lnk kısayolları, güncel kısayol oluşturulduktan sonra yedeğe taşınır. Başka uygulamalara ait kısayollar korunur; tekrar çalıştırma değişiklik üretmez.
- Android 0.1.77 ve hesap/giriş/veri davranışı değişmedi.
- Doğrulama: paketli EXE'nin adı/sürümü ve yedi simge boyutu, gerçek Windows kısayolunun ICO yolu, iki eski adın yedekli kaldırılması, tekrar çalıştırma ve başka uygulamaların kısayollarının korunması geçti. Paketli uygulamadaki 36 genel kontrol de sentetik kayıtlarla geçti. Gerçek hesap kasası açılmadı.

## Windows 0.2.48 / Android 0.1.77 — 2026-11-04 (Veri saklama ve profil arşivini silme)

- Hesap başına ayrıntılı geçmiş ve günlük istatistikler için ayrı Ben silene kadar / 30 / 90 / 365 gün tercihleri eklendi. Onaydan sonra yerel temizlik uygulanır; güncel profil ve son liste kaydı korunur. Başarısız temizlik görünür ve yeniden denenebilir.
- Profil kaldırma ilgili geçmiş, günlük/görsel/bildirim ve kendi liste arşivlerini de temizler; Windows hikâyeleri dahildir. Başka hesapların liste üyeliği, dışa aktarılan dosyalar ve güvenlik molaları korunur. Windows otomatik kurtarma kopyası da temizlenmiş içerikle değiştirilir.
- Android geçici disk küçük resimleri ayrı Keystore/AES-GCM anahtarıyla şifrelendi; eski düz küçük resimler arka planda temizlenir. Mevcut oturum şifreleme biçimi korunur. Dört dil güncellendi.
- [Veri envanteri, silme kapsamı ve yayın öncesi açık işler](docs/DATA-LIFECYCLE.md) eklendi. Android canlı giriş ve yeni cihaz testleri sonraya bırakıldı; giriş/429 kuralları gevşetilmedi.
- Doğrulama: 258 Python, 146 Android JVM testi; 36 genel ve 5 yeni paketli Windows kontrolü geçti. Release/test APK derlemeleri, lint (0 hata / 50 uyarı), dört dil ve imza/paket kontrolleri geçti. Android cihaz testleri yalnız derlendi. [Ayrıntılı kanıt ve sınırlar](docs/DATA-LIFECYCLE.md#doğrulama-sınırı).

## Android 0.1.76 — 2026-11-04 (Mobil görünüm ve yerel bildirim geçmişi)

- Özet, profil ayrıntısı ve liste sahibi Windows düzeniyle uyumlu hale getirildi. Fotoğraf, kullanıcı adı/isim ve bilinen sayaçlar belirginleştirildi; profil geçmişi ve ikincil işlemler isteyerek açılır. Yerel günlük grafikte dönem net farkı gösterilir.
- Uygulama içi Bildirimler ekranı, okunmamış/tür/profil filtreleri, kalıcı okundu durumu ve hesap/değişiklik türüne göre sistem bildirimi tercihleri eklendi. Aynı profil kontrolünün değişiklikleri tek kayıtta tutulur; tercihleri kapatmak yerel geçmişi silmez.
- Zaman Çizelgesi günlere ayrıldı, rutin kontroller katlanır gruplara taşındı. Bilinen eski/yeni değerler gösterilir; eski kayıtlarda olmayan değerler üretilmez. Günlük en fazla 2000 olay / 2 MiB; bildirim görünümü son 500 değişikliktir. Kalıcı günlük istatistikler ayrı saklanır.
- Geçmiş satırları ListView ile yeniden kullanılır; günlük okumaları hesap yuvasına göre sınırlı önbellekten alınır. Aynı fotoğraf ID’sinin değişen CDN bağlantısı başarılı fotoğraf önbelleğini kullanabilir; başarısız eski URL yeni URL’yi engellemez. Dört dilde karşılıkları eklendi.
- 142 JVM testi, lint (0 hata / 48 uyarı), release/debug/test APK derlemeleri, dört dilde 716 kaynak ve imzalı paket kontrolleri geçti. **Emülatör ekran testleri tamamlanmadı**; bellek daralması sonrasında kullanıcı testi erteledi. Tam ekran ve canlı giriş doğrulaması vaat edilmez. [Kapsam ve açık testler](docs/ANDROID-UI-ALIGNMENT.md).
- Android giriş taşıyıcısı, 429 molaları ve Windows 0.2.47 paketi değişmedi. Hikâye arşivi gibi diğer Windows özelliklerinin tamamı Android'e taşınmış değildir.

## Windows 0.2.36 / Android 0.1.75 — 2026-11-03 (Kalıcı günlük istatistikler)

- Kendi hesabı ve kaydedilen profiller için ayrıntılı son kontrollerden bağımsız, şifreli profil/yıl arşivi eklendi. Günlük ilk/son/minimum/maksimum sayaçlar, gözlem zamanları ve kontrol sayısı tutulur. Eski ayrıntılar budanmadan önce eldeki kayıtlar aktarılır; günlük özetlerde süreye bağlı silme yoktur.
- Hafta/ay/yıl/tümü aralıkları ve takipçi/takip edilen seçimi eklendi. Eksik günler sıfıra çevrilmez; UTC günleri kullanılır. Uzun aralıklar kapsam korunarak en çok 720 grafik noktasına gruplandırılır. Son kontroller görünümü ayrıca korunur.
- Günlük temizleme yakın geçmişten otomatik geri aktarımı engeller. Windows yedeği günlük özetleri birleştirir; daha yeni günler korunur. Arşiv yıl bazında okunur/yazılır; başka profilin kontrolü kendi grafiğini gereksiz yenilemez. Ek Instagram isteği gönderilmez.
- Android atomik kayıt okuyucusu yalnız `.bak` kaldığında da kurtarma okuması yapar. Native günlük ekran ve Keystore için cihaz testleri eklendi/derlendi; kullanıcının isteğiyle cihaz çalıştırması sonraya bırakıldı.
- Doğrulama, saklama sınırları ve ölçüm: [günlük istatistikler](docs/DAILY-STATS-TEST.md).

## Windows 0.2.35 — 2026-11-02 (Kaydırdıkça yerel liste yükleme)

- Takip Listeleri, alt sınıra yaklaşınca sonraki 100 üyeyi otomatik yükler; normal sayfalama düğmesi kaldırıldı. Yeni satırlar mevcut satırları yeniden oluşturmadan eklenir.
- Arayüze bütün liste arşivleri yerine sayılar ve tarama bilgileri gönderilir. Arama, ilişki filtresi ve eski/yeni liste karşılaştırması cihazdaki tam kayıt üzerinde çalışır; yalnız sonuç sayfası aktarılır. Bu yol Instagram istemcisi açmaz, kota tüketmez veya kasayı yazmaz.
- Hesap, arama veya tarama değişince gecikmiş sayfa yanıtları kullanılmaz. Eksik/önceki liste, fotoğraf kaynakları, arka planda çalışan tarama ve bütün üyeleri içeren CSV korunur. Hatalı sayfa kendiliğinden tekrar denenmez.
- Android 0.1.74 ve mevcut giriş araştırması değişmedi. Doğrulama ve ölçüm kapsamı: [yerel liste sayfalama notu](docs/LOCAL-LIST-PAGING-TEST.md).

## Windows 0.2.34 / Android 0.1.74 — 2026-11-02 (Instagram Threads rozeti)

- Threads bağlantısı yalnız biyografi linklerinden aranıyordu. Instagram'ın ayrı Threads rozeti gözden kaçtığı için rozetli profilde düğme görünmüyordu. Etkin rozetin bildirdiği geçerli kullanıcı adı artık profil bağlantılarına eklenir; sırf Instagram kullanıcı adıyla hesap varsayılmaz. Açık Threads adresi varsa önceliklidir ve aynen korunur.
- Beş web sitesi bağlantısına ek Threads bağlantısı, şifreli kayıt/geri yükleme ve arayüz boyunca korunur. Gizli/eksik rozet, bozuk etiket ve geçersiz adreslerden bağlantı oluşturulmaz. Web/Windows ve Android aynı HTTPS/alan adı/port kurallarını uygular.
- Bildirilen profilin gerçek Windows okumasında biyografi linkleri boş, Threads rozeti açık ve etiket mevcut görüldü. Düzeltmeden sonraki tek normal profil okuması Threads bağlantısını şifreli kayda ekledi. Parola/çerez dışarı alınmadı; Threads'e doğrulama isteği gönderilmedi. Android'in mevcut giriş/profil molası değiştirilmedi.
- 201 Python, 47 Node, 132 Android JVM, 10 renderer ve paketli Windows EXE üzerinde 36 kontrol geçti. Beş site + Threads düğmesinin görünürlüğü ve doğrudan açılması sınandı. Android release/lint (0 hata / 21 uyarı) ve imzalı APK paket kontrolü başarılı. Yeni Android ayrıştırması gerçek telefonda ayrıca sınanmadı.

## Android 0.1.73 — 2026-11-02 (Bağlantı hatasını parola reddinden ayırma)

- Giriş taşıyıcısında bağlantı/depolama hatası olursa Instagram formu kapatılır ve uygulamanın gerçek hata açıklaması gösterilir. Yerel köprünün reddi nedeniyle formun yanıltıcı parola hatası göstermesi önlenir; otomatik tekrar gönderilmez.
- Giriş, kod ekranı ve kod gönderimi için ayrı son aktarım hatası tanısı eklendi. Yalnız sabit aşama/hata sınıfı, taşıyıcı aşaması ve varsa SDK'nın sayısal ağ hata kodu tutulur. İptal ayrı belirtilir; istisna mesajı, URL, parola, kod ve çerez değeri saklanmaz. Mevcut mola/bütçe korunur.
- Doğrulama: 130 JVM, 30 JavaScript kontrolü, dört dilde 664 kaynak ve lint (0 hata / 21 uyarı) geçti. API 36 emülatöründe bekleyen oturum, kod gönderimi/claim, iptal/429 ve hata ekranları sınandı; iki test beklentisi/zamanlaması düzeltilip yeniden doğrulandı. Gerçek telefonda son giriş yanıtı oturum vermedi; uçtan uca canlı hesap bağlantısı henüz doğrulanmadı.

## Android 0.1.72 — 2026-11-02 (Girişten sonra bağlantıya devam)

- Giriş/kod aşamasından sonra profil kontrolü beklemeye, bağlantı hatasına veya yerel bütçeye takılırsa yeni oturum artık silinmez. Mevcut şifreli bekleyen oturum deposu kullanılır; hesap doğrulanmadan kayıtlı hesaplara eklenmez. Ana ekrandaki “Bağlantıyı tamamla” ile yalnız profil okuması tekrar yapılabilir; parola/kod yeniden gönderilmez.
- Devam ekranı bekleme saatini gösterir, mola sırasında isteği kapatır ve yalnız kullanıcı eylemiyle ilerler. Bekleyen bağlantıyı silme işlemi kayıtlı hesapları veya bekleme kayıtlarını etkilemez. Yeni web girişi bekleyen oturumu sessizce değiştiremez. Geçersiz/süresi dolmuş oturumda yeniden giriş gerekir.
- Onaylanmış giriş/doğrulama yanıtında gelen `X-IG-Set-WWW-Claim`, aynı giriş sayfasına ve ardından şifreli oturuma aktarılır; profil okuyucusu bu değeri kullanır. Değer tanı kaydına yazılmaz. Bu başlığın önceki 429'un nedeni olduğu doğrulanmış değildir.
- Türkçe, İngilizce, İspanyolca ve Brezilya Portekizcesi metinleri eklendi. Android sürümü artar; Windows 0.2.33 değişmedi. Canlı uçtan uca sonuç aşağıdaki giriş araştırması kaydında ayrıca belirtilir.

## Windows 0.2.33 / Android 0.1.71 — 2026-11-02 (Yalnız açık Threads bağlantısı)

- Threads düğmesi yalnız Instagram profilinden alınan açık bir Threads profil bağlantısı varsa gösterilir. Kaynak adres, kullanıcı adı farklı olsa veya eski `threads.net` alanını kullansa da korunur. Kullanıcı adından tahmini adres oluşturulmaz; bağlantı yoksa düğme gizlenir, hesap yokluğu sonucu çıkarılmaz.
- Düğme doğrudan normal tarayıcıyı açar. Threads kontrol isteği, IPC komutu, sunucu/Android okuyucuları, önbellek ve doğrulanamadı mesajları kaldırıldı. Ek Threads isteği veya giriş yoktur. Otomatik doğrulama araştırması geliştirme listesinde ertelendi; eski uygulama Git geçmişinde durur.
- Web sitesi kartları, Instagram bağlantısı, şifreli kayıtlar ve biyografi satır sonu düzeltmesi korunur. Eski bağlantısız kayıtlar açılabilir; yeni bağlantı ancak sonraki normal profil okumasında gelirse görünür.
- Doğrulama: 199 Python, 46 Node ve 130 Android birim testi, 10 sentetik profil/liste ekran kontrolü, paketli EXE üzerinde 36 kontrol ve tema/dil/yerel ikon kaynak kontrolü geçti. Android release derlemesi, lint (0 hata, 21 uyarı), imza ve APK manifest kontrolü geçti. Yeni Android ekranı fiziksel cihazda sınanmadı; Threads’e yeni canlı istek gönderilmedi.

## Windows-web 0.2.32 — 2026-11-02 (Biyografi satır sonları)

- Profil biyografisinde sunucudan gelen satır sonları ve boşluklar masaüstü/web panelinde korunur. Önceki normal HTML akışı alt alta yazılmış metni yan yana gösteriyordu. Uzun sözcükler panel sınırında kırılır; kullanıcı metni HTML olarak çalıştırılmaz.
- Android TextView biyografi satır sonlarını zaten korur; Android 0.1.70 paketi değişmedi. Veri okuma, liste/hesap kayıtları, istek aralığı ve molalarda değişiklik yoktur.
- Ekran doğrulaması: üç ayrı biyografi satırının görsel dikey konumu ve HTML benzeri metnin düz metin kalması mevcut sentetik profil paneli kontrolüne eklendi; 10 kontrol geçti. Threads'in bildirilen profili, temiz ve çerez göndermeyen gerçek tarayıcıda HTTP 302 ile giriş sayfasına yönlendi. Bu durum profil yokluğu değildir; mevcut tarayıcının oturumu uygulamaya alınmadı ve ayrı Threads giriş akışı eklenmedi.

## Windows 0.2.31 / Android 0.1.70 — 2026-11-02 (Profil siteleri ve sosyal bağlantılar)

- Kişinin web sitesi artık **Profil bağlantısı** diye gösterilmez. Yerel site logosu/adı, bilinmeyen sitede alan adı/genel simge, fareyle üzerine gelince veya telefonda uzun basınca tam adres gösterilir. Birden fazla biyografi bağlantısı en fazla beş adresle desteklenir; eski kayıtlar korunur. İkonlar tek yerel kaynaktan üretilir; dış favicon/site başlığı hizmeti kullanılmaz.
- Instagram’da aç düğmesi eklendi. Site/Instagram adresleri kullanıcı eylemiyle normal tarayıcıda açılır. Electron renderer'ında uzak gezinme açılmadı; ayrı IPC geçerli HTTP(S) adresleri kabul eder, dosya/JavaScript/kimlik bilgili URL'leri reddeder.
- Threads’i kontrol et, açık Threads biyografi bağlantısını veya aynı kullanıcı adını çerezsiz tek GET ile kontrol eder. Profil verisi istenen ad/URL ile eşleşirse açılır; HTTP 404/410 bulunamadı, giriş/yönlendirme/erişim engeli veya tanınmayan yanıt doğrulanamadı olarak gösterilir. Bilinmeyen sonuçta tarayıcıda açma seçeneği vardır. Instagram ve Threads sahipliği birleştirilmez. Ortak aralık, bütçe ve 429 molası korunur. Windows sonucu 20 kayıt/beş dakika bellekte kullanır; hesap geçişi temizler. Android ekrandan ayrılınca çağrıyı iptal eder.
- Doğrulama: 201 Python, 46 Node, 131 Android birim testi, 10 sentetik arama/liste/panel ekran kontrolü; dört dilde 665 Android kaynağı ve 1.042 masaüstü mesajı eşleşti. Android release derlemesi ve lint geçti (0 hata, 21 uyarı). Threads'in bilinen herkese açık profiliyle iki oturumsuz canlı deneme HTTP 302 ile giriş sayfasına yöneldi; bu, yokluk olarak sunulmaz. Canlı başarılı profil varlığı/404 sonucu ve Android'in yeni ekranı fiziksel cihazda doğrulanmadı. Paketli EXE üzerinde 36 kontrol ve imzalı APK manifest/imza doğrulaması geçti.

## Windows 0.2.30 — 2026-11-02 (Listeden profil ayrıntılarına geçiş)

- Liste satırındaki fotoğraf sağ profil paneline taşınır; devam eden küçük resim indirmesi panelle paylaşılır, ikinci indirme gerekmez. Bilgi yanıtı fotoğrafı veya yazılmakta olan notu/odağı silmez.
- Panel açıldığında eksik/eski takipçi, takip edilen, gönderi ve gizlilik bilgileri için tek sayısal ID profil okuması mevcut ortak sıraya girer. Son beş dakikadaki başarılı bilgiler yeniden kullanılır; en fazla 20 geçici profil tutulur. Panel kapanınca henüz gönderilmemiş iş kaldırılır; geç yanıt paneli açmaz. Hesap değişimi geçici önbelleği temizler.
- Liste üyesi bu okumayla kaydedilen profillere eklenmez. Eksik alanlar gizlenir; gerçek sıfır sayıları ve açık/gizli değerleri korunur. Kendi/kaydedilen profilin mevcut şifreli gözlemi güncellenebilir. Otomatik panel okuması durdurulmuş otomasyonu açmaz; açık yenileme eski davranışı korur. 6–10 saniyelik sıra, bütçe ve 429 molası değişmez. Ayarlar'daki eski 15 dakikalık liste aralığı açıklaması mevcut bir dakikalık alt sınırla eşitlendi. Android 0.1.69 değişmedi.
- Doğrulama: 197 Python, 44 Node testi, dokuz sentetik arama/liste/panel ekran kontrolü, tema/dil/kaynak kontrolü ve paketli EXE üzerinde 36 kontrol. Kapsam: tek okuma, hesap/ID doğrulaması, eksik alanlar, sıfır değerleri, görüntü paylaşımı, not/odak korunması, beş dakikalık tazelik, geç yanıt, mola/429'da tekrar göndermeme ve paketli çevrimdışı sınır. Yeni profil paneli akışı canlı Instagram yanıtıyla henüz doğrulanmadı.

## Windows-web 0.2.29 — 2026-11-02 (Takip listelerinde profil fotoğrafları)

- Liste yanıtında varsa izinli Meta CDN profil fotoğrafı adresi şifreli üyelik kaydında tutulur. Takipçi/takip edilen listesindeki görünür ve yakın satırlar bu adresten küçük resim yükler; kişi başına profil API sorgusu gönderilmez. Önceden adresi saklanmamış kayıtlarda baş harf kalabilir; sonraki normal taramanın aynı ID için sağladığı adres kullanılabilir. Üyelik kayıtları birleştirilmez veya değiştirilmez, ek tam tarama zorlanmaz.
- Arama ve liste fotoğrafları tek sınırlı yükleyiciyi paylaşır: toplam iki indirme ve en fazla 20 çalışan/bekleyen iş. Mevcut çerezsiz, yönlendirmesiz, boyut/çözünürlük sınırlı CDN taşıyıcısı yeniden kullanılır. Liste adresi arayüze veya fark/bildirim üyelerine aktarılmaz; arayüz mevcut hesap/liste/tarama/kişi kimlikleriyle istekte bulunur. Keyfi URL ve başka hesap bağlamı reddedilir.
- İşçi 64 ve arayüz 32 küçük resmi bellekte önbellekler; en çok 15 dakika boyunca yeniden kullanılabilir, diske görsel yazılmaz. Gizleme/ekran değişimi ilgili işleri iptal eder; hesap değişimi bütün görsel işlerini ve önbelleği temizler. Yerel filtreleme/sayfalama odağı korunur, geç yanıt eski ekrana yazılmaz; görsel alınamazsa baş harf gösterilir. Android 0.1.69'daki liste görsel yükleyicisi zaten bağlıdır; Android paketi değiştirilmedi.
- Doğrulama: 193 Python, 38 Node, altı sentetik arama/liste görsel kontrolü, tema/dil/kaynak kontrolü, eski kısmi kayıtla paketli başlangıç ve paketli EXE'de 36 kontrol geçti. Bunlar kayıtlı üye/tarama/hesap kapsamı, adresin arayüz ve farklardan çıkarılması, eski kayda yeni sayfadan fotoğraf sağlama, iki indirme sınırı, hesap/URL kapsamlı önbellek, ayrı arama/liste iptali, geç yanıt, görünür satır yükleme, filtre odağı ve geçersiz görselde baş harfi korumayı sınar. Ağ yanıtları sentetiktir; yeni liste fotoğrafı akışı canlı Instagram/CDN yanıtıyla doğrulanmadı.

## Windows-web 0.2.28 — 2026-11-02 (Yerel istek bütçesini iki katına çıkarma)

- Hesap başına kayan bütçe 120/saat ve 1.200/24 saatten 240/saat ve 2.400/24 saate; bütün hesapların ortak bütçesi 240/saat ve 2.400/24 saatten 480/saat ve 4.800/24 saate çıkarıldı. Bunlar uygulamanın sınırlarıdır, Instagram'ın doğrulanmış güvenli kotaları değildir. Geçmiş istekler silinmez veya yeniden tarihlenmez; 6–10 saniye aralığı, kayıtlı rezervasyonlar, 429/doğrulama molaları ve eksik liste tekrar zamanı korunur.
- Bütçe bilgisi tek üreticiden arayüze ve takvim önbelleğine aktarılır. Politika değiştiğinde seçili olmayanlar dahil hesap takvimleri kendi kasalarından yeniden hesaplanır; eski bütçenin takvimde bıraktığı bekleme kaldırılabilir, gerçek sunucu molası kaldırılamaz. Güncelleme kendi başına Instagram isteği göndermez; açık otomatik kontrol normal işleyişinde sürebilir.
- Doğrulama: 188 Python ve 38 Node testi, tema/dil/kaynak kontrolü, paketli EXE üzerinde 11 ilerleme/bütçe kontrolü ve 36 genel kontrol geçti. Gerçek DPAPI ile eski 120 istekli geçmişin korunması, yeni sınırın uygulanması, saatlik/günlük kayan pencere, yeniden açılış, çoklu hesap takvimi, değişmeyen sunucu molası ve TR/EN yeni kota gösterimi sınandı. Bu testler Instagram'a istek göndermez; yeni kotanın canlı hesapta sınırlama etkisi doğrulanmadı. Android 0.1.69 değiştirilmedi.

## Windows-web 0.2.27 — 2026-11-02 (Kısmi listeyi koruma ve daha kısa istek aralığı)

- Elle yeniden kontrol, geçerli devam bilgisi olan kısmi taramayı kaldığı yerden sürdürür; düğme Taramaya devam et olur. Baştan okumak gerekiyorsa ve henüz tam liste yoksa önceki dolu kısmi liste ayrı tutulup tarihiyle gösterilir. Tekrarlanan başarısız başlangıçlar bu kaydı boş sayfayla değiştirmez; yeni tam liste bitince ayrı kayıt kaldırılır. Tam listeler korunur, kısmi üyeler birleştirilmez ve kısmi kayıttan fark/takipten çıkış üretilmez. Daha önce üzerine yazılmış kayıtları geri getirme garantisi yoktur.
- Windows normal hesap okumalarında rastgele aralık 12–18 saniyeden 6–10 saniyeye çekildi. Mevcut rezervasyonlar, saatlik/günlük bütçeler ve 429/doğrulama molaları korunur; bu aralık doğrulanmış Instagram kotası değildir. Android 0.1.69 değiştirilmedi.
- Profilde takipçi/takip edilen sayısının artması veya azalması ilgili listeyi öne alır. Otomatik tam taramalar arasındaki asgari aralık 15 dakikadan 1 dakikaya çekildi; sayı değişmese de günlük doğrulama korunur. Eski çoklu hesap takvimleri yeni plan sürümüyle yerel olarak yeniden hesaplanır.
- Doğrulama: 186 Python ve 38 Node testi, tema/dil/kaynak kontrolü (dört dilde 1.027 metin), iki Windows marka/kısayol kontrolü, eski kısmi kayıtla paketli başlangıç ve paketli EXE üzerinde 36 çevrimdışı kontrol geçti. Yeni kontroller kaldığı yerden sürdürme, art arda başarısız başlangıçlarda eski üyeleri koruma, yeniden açılış, sahiplik, tam sonucu atomik değiştirme, sayı artışı/azalışı ve değişmeyen sayılarda günlük taramayı kapsar. 76 kişilik önceki kısmi kaydın boş yeni tarama sırasında ekranda kalması paketli uygulamada doğrulandı. Yeni 6–10 saniyelik aralığın canlı Instagram sınırlama eşiği doğrulanmadı.

## Android 0.1.69 / Windows-web 0.2.26 — 2026-11-01 (Canlı profil arama ve fotoğraflar)

- Profil ekle penceresi en az iki karakter yazıldıktan ve yazmaya 650 ms ara verildikten sonra Instagram aramasını sıraya alır. Kayıtlı eşleşmeler hemen filtrelenir. Mevcut istek aralığı/bütçesi korunur; beklerken yalnız son sorgu tutulur ve kısa geri sayım gösterilir. Ağ isteği sırasında alan yazılabilir kalır, geç gelen başka sorgunun yanıtı sonuçları değiştirmez. Aynı pencerenin son altı sorgusu 60 saniye bellekte yeniden kullanılabilir; sınırlama yanıtı otomatik aramayı durdurur. Elle arama ve kullanıcı adıyla doğrudan ekleme korunur.
- Sonuçlarda profil fotoğrafları metinden bağımsız yüklenir; görsel gelmezse baş harf gösterilir. Mevcut çerezsiz Meta CDN taşıyıcısı ve görüntü sınırları yeniden kullanılır; en fazla iki indirme çalışır. Masaüstünde renderer adres seçemez, yalnız mevcut sonuç belirteci ve kullanıcı ID'sini iletir. Görseller ve arama önbelleği diske yazılmaz. Pencere kapanınca işler iptal edilir; Android arama görselleri kalıcı avatar önbelleği/zaman tüneline eklenmez.
- Doğrulama: 183 Python, 38 Node ve 128 Android birim testi geçti. Sentetik API ile dört arama ekranı kontrolü (otomatik arama, odak, fotoğraf, 429, geç yanıt), 12 dört dilli web kontrolü, iki Windows marka/kısayol kontrolü, eksik listeyle paketli başlangıç ve paketli EXE üzerinde 35 çevrimdışı kontrol geçti. Dört dilde 658 Android kaynağı ve 1.025 masaüstü metni eşleşir. Android release/debug/test APK derlemeleri ve release lint tamamlandı. Canlı Instagram arama/CDN yanıtı ve yeni Android arama ekranı fiziksel cihazda/emülatörde denenmedi; test APK'sı yalnız derlendi. Mevcut Instagram hesabında yeni giriş başlatılmadı.

## Windows-web 0.2.25 — 2026-11-01 (Windows uygulama adı ve logosu)

- Paketli EXE'nin Electron ürün/dosya bilgileri InstagramTracker adı ve uygulamanın sürümüyle değiştirilir. Mevcut yerel marka işareti yedi boyutta (16–256 piksel) EXE'ye gömülür; pencere ve tepsi aynı işareti kullanır. Düzenleme yalnız paket çıktısında, kilitli geliştirme bağımlılığı `resedit` 3.1.0 ile yapılır.
- Başlat menüsü kısayolu bildirim tercihi kapalıyken de açılışta oluşturulur/güncellenir; simgesi güncel EXE'den alınır. Güncel olmayan simge/çalışma klasörü/açıklama da güncellemeyi tetikler. Önceki kendi kısayolunun yedeği korunur; farklı sahibin kısayolu değiştirilmez. Kısayol oluşturmak bildirim iznini açmaz.
- Eski sürüme giden `Electron.lnk` aynı uygulama kimliğini taşıdığında Windows kataloğu doğru kısayol yerine onu seçebiliyordu. Doğru kısayol hazırlandıktan sonra yalnız bu uygulama kimliğini ve InstagramTracker EXE hedefini taşıyan eski Electron kısayolu veri klasörüne yedeklenerek menüden taşınır. Farklı uygulamanın Electron kısayolu korunur.
- Doğrulama: 32 Node testi, tema/dil/kaynak kontrolü, iki paketli Windows marka/kısayol kontrolü ve 34 paketli uygulama kontrolü geçti. Eski kısayol taşıma, yedekleme ve başka uygulamanın kısayoluna dokunmama ayrıca gerçek Windows kısayollarıyla test edildi. Windows'un dosya sürüm API'si ürün adını InstagramTracker ve sürümü 0.2.25 olarak okudu; EXE'den yerel Windows API'siyle çıkartılan simge görsel olarak kontrol edildi. Kullanıcının eski 0.2.3 kısayolu yedeklenip taşınınca Windows uygulama kataloğunda ad InstagramTracker Desktop oldu. Çevrimdışı testler canlı Instagram isteği göndermez. Android paketi değişmedi.

## Windows-web 0.2.24 — 2026-11-01 (Eksik liste sonrası açılış düzeltmesi)

- Profil sayısına ulaşmadan sona eren liste taraması boş devam işaretçisi kaydediyordu; sonraki açılışta depo doğrulaması bu kaydı reddediyordu. Yeni taramalar devam sayfası yokluğunu `null` olarak kaydeder. Eski boş işaretçiler depo okunurken aynı biçime dönüştürülür; kısmi üyeler, önceki tam listeler, tarama durumu ve mola/bütçe kayıtları korunur. Geçersiz işaretçi türleri kabul edilmez; eksik liste tamamlanmış sayılmaz.
- Yerel hizmetin belirli hata açıklaması, hemen ardından gelen genel süreç kapanma mesajıyla artık değiştirilmez.
- Doğrulama: 180 Python ve 32 Node testi, ortak tema/dil/kaynak kontrolü ve paketli EXE üzerinde 34 çevrimdışı kontrol geçti. Ayrı paketli hizmet testi eski boş işaretçili 76/82 kaydının başlangıç listesini ve güvenlik molasını koruyarak açıldığını doğruladı. Kullanıcının şifreli kayıtlarının ayrı yerel kopyası da yeni paketli hizmette çevrimdışı açıldı. Bu inceleme canlı Instagram isteği göndermedi; Android paketi değişmedi.

## Android 0.1.68 / Windows-web 0.2.23 — 2026-11-01 (Kısmi adla profil arama)

- Kayıtlı profillerde kısmi kullanıcı adı, @ad, Instagram profil bağlantısı, birden fazla kelime ve Türkçe karakterleri dikkate alan yerel arama eklendi/iyileştirildi. Android arama alanı yazarken yeniden oluşturulmaz; liste filtrelenir, odak korunur. Yeni profil okumalarında görünen ad şifreli kayda alınır; eski kayıtta ad yoksa kullanıcı adıyla arama sürer, ad sonraki profil okumasıyla gelir.
- Profil ekleme ekranında açık **Instagram’da ara** işlemi en fazla 20 aday gösterir. Yazarken Instagram isteği gönderilmez. Sonuç seçildiğinde gerçek profil sayısal kimliğiyle okunup kaydedilir; adayın takipçi sayısı uydurulmaz. Arama adayları kalıcı dosyaya yazılmaz ve hesap değiştirilince temizlenir. Kullanıcı adıyla doğrudan ekleme @ad ve paylaşılan HTTPS profil bağlantılarını da kabul eder.
- Yalnız sabit Instagram adresindeki `GET users/search/` okuması izinli isteklere eklendi. Mevcut mola, istek sırası, oturum sınırları ve sosyal işlem yasağı korunur. Windows'ta arama hatası girilen metni korur; kapatılmış pencere geç gelen yanıtla yeniden açılmaz. Android'de pencere kapanınca kendisine ait okuyucu iptal edilir.
- Doğrulama: 127 Android birim testi, release lint (0 hata, mevcut 24 uyarı), release/debug/test APK derlemeleri ve imzalı APK paket kontrolü geçti. Dört dilde 657 Android kaynağı ve 1.023 masaüstü/web metni eşleşir. 178 Python testi, 31 Node testi, 12 web kontrolü, sentetik API yanıtlarıyla 4 arama ekranı kontrolü ve paketli EXE üzerinde 34 kontrol geçti. Sonradan eklenen hesap bağlamı testi ayrıca sınandı. Canlı Instagram arama yanıtı doğrulanmadı. Fiziksel test APK’sı USB kurulumu reddedildiği için çalışmadı; kullanıcı ek telefon test kurulumu istemedi. Ana 0.1.68 APK kendi telefona veri silmeden kuruldu.

## Windows 0.2.22 — 2026-11-01 (İzlenen profillerin liste değişiklikleri)

- Sonraki hedefli doğrulama: kullanıcı 82 kişi beklenen listede 76 kayıt ve sayı uyuşmazlığı mesajı bildirdi. Aynı durum kendi hesap/izlenen profil için sentetik sayfalarda sınandı: kayıt kısmi kalıyor, önceki tam liste korunuyor, altı kişi için ayrılma sonucu veya liste farkı bildirimi üretilmiyor. Devam belirteci yoksa yeni tam deneme 30 dakika sonrasına sıraya alınıyor; 76 + 6 üyeli geçerli devam sayfalarıyla 82'ye ulaşılınca tamamlanıyor. Bu test geçti; canlı hesapta eksik altı kişinin neden dönmediği doğrulanmadı. Çalışma kodu değiştirilmedi.

- Otomatik takipçi/takip edilen taraması kendi hesabına ek olarak **İzlemeye al** seçili profilleri kapsar. Mevcut **Otomatik liste karşılaştırması** tercihi ayarlardan yönetilir. Eski çoklu hesap dizininin zamanlama bilgisi yeni liste kapsamıyla yeniden hesaplanır; etkin hesap, oturum, mola ve tercihler korunur.
- Başlangıç listeleri otomatik hazırlanır. Sayaç değişimi ilgili yönü yeniden taramaya alır; mevcut 15 dakikalık tekrar tarama alt sınırı korunur. Sayı aynı kaldığında günlük tarama sürer. Liste işleri profiller arasında sıra alır; profil kontrolüyle dönüşümlü çalışır ve ortak istek bütçesine uyar.
- İki tam listede eklenen ve artık görünmeyen hesapların sayısal kimlikleri, gözlenen adları ve kullanıcı adları olay/bildirim kaydında şifreli tutulur. Altı liste kaydı döndüğünde isim kanıtı kaybolmaz; eski yalnız sayısal kayıtlar için isim uydurulmaz. Yarım listeler fark üretmez. Başlangıç ve değişikliksiz liste taraması Windows bildirimi üretmez.
- Bildirim ayrıntısından veya zaman çizelgesinden isimler açılır; eklenen/eksilen filtreleri ve 100 satırlık sayfalama vardır. Windows bildirimi ilk birkaç kullanıcı adını ve kalan sayıyı gösterir. Kayıtlı profilin panelinden takip listeleri doğrudan açılabilir. İzlenen bir profilin taramasını iptal etmek otomatik liste taramalarını duraklatır; kendiliğinden hemen devam etmez.
- 173 çevrimdışı Python testi, 29 Node testi, 12 tarayıcı kontrolü ve paketli EXE üzerinde 33 kontrol geçti. Dört dilde 1.014 metin eşleşir. Bir dakikalık otomatik sayaç kontrolü, isim kanıtı, aynı sayıda üye değişimi, 429, iptal, eski dizin geçişi, adların HTML olarak çalışmaması, filtre/sayfalama ve dar ekran kontrol edildi. Canlı Instagram isteği veya gerçek Windows bildirim teslimi yeniden denenmedi. Kullanıcının önceki gecikmesinin canlı nedeni doğrulanmadı; mevcut kontrol aralığı ve izleme tercihleri otomatik değiştirilmedi.

## Android 0.1.67 / Windows-web 0.2.21 — 2026-11-01 (Daha sade çalışma alanı)

- Genel açıklamalar ve sonuçların sınırları ana ekranlardan **Kullanım notları** alanına taşındı. Windows/web sayfa altından ortak yardım penceresini, Android sayfa altından veya ⋯ menüsünden ayrı yardım ekranını açar. Gizlilik açıklamaları ayrıca erişilebilir kalır.
- Android Özet ekranında takipçi/takip edilen sayaçları durum kartlarının önüne alındı. Kalıcı tekrarlar, profil izleme ve saklama açıklamaları yardım ekranında toplandı. İlk kullanım yönlendirmeleri, canlı ilerleme, eksik tarama, hata ve güvenlik molaları görünür kalır.
- Windows/web'de sol menüdeki tanıtım bloğu ve zaman çizelgesinin açıklama sütunu kaldırıldı; kayıtlar tam genişlikte açılır. Sayfa başlıkları ve hazırlık kartı daha az yer kaplar. Aynı dört dil ve açık/koyu/OLED temalar korunur.
- Giriş işleyişi ve istek politikası değiştirilmedi. Profil 429 sonrasında geçici giriş oturumunu ayrı, şifreli saklayıp mola sonrasında sürdürme işi geliştirme notlarına alındı; henüz uygulanmadı.
- Doğrulama: 123 Android JVM testi, release lint (0 hata, mevcut 24 uyarı), release/debug/test APK derlemeleri ve imzalı APK paket kontrolü geçti. Android'de dört dilde 650 kaynak, Windows/web'de 1.003 çeviri eşleşiyor. 12 tarayıcı ve paketli EXE üzerinde 32 çevrimdışı kontrol geçti; Instagram isteği gönderilmedi. Windows yardım ve zaman çizelgesi ekran görüntüleri incelendi. Yeni Android yardım ekranı fiziksel cihazda veya emülatörde çalıştırılmadı; canlı giriş yeniden denenmedi.

## Android 0.1.66 — 2026-11-01 (Güvenlik kodunu gönderme)

- Kendi telefondaki yeni canlı 0.1.65 kaydında kod gönderiminin kök değişkeni `input`; alt alanları actor_id, client_mutation_id, code, device_id ve encrypted_ap_context olarak doğrulandı. Statik mutation şeması tek kök `xfb_auth_platform_submit_code` alanını gösterdi. Ayrı cihaz onayı sorgusunun alanları da gözlendi; o sorgu henüz açılmadı.
- Yalnız bu belge/işlem ve alan sözleşmesi, onaylanmış açık güvenlik sayfasının bağlamı ile eşleştiğinde bir kod POST'u gönderebilir. Genel GraphQL, sosyal işlemler, parola tekrarı ve kodun otomatik tekrar gönderimi açılmaz. Ortak aralık, bütçe ve 429 molası korunur.
- Kod yanıtı ilk parola yanıtı ve kod ekranı okumasından ayrı tanılanır. Yanıtın beklenen kökü doğrulanmadan çerez uygulanmaz; hesap bağlantısı ayrıca profil doğrulamasından geçer. Kod ve bağlam değerleri kaydedilmez. Dört dilde kod gönderiliyor açıklaması ve cihaz onayı sınırı eklendi.
- 123 JVM testi, 9 JavaScript kontrolü, dört dilde 650 kaynak denetimi, release lint ve release/debug/test derlemeleri geçti. Yeni WebView testi telefonun test APK kurulumunu reddetmesi nedeniyle çalıştırılamadı. İmzalı ana uygulama kendi telefonda veri silmeden 0.1.66'ya güncellendi; canlı kod gönderimi sonucu bekleniyor.

## Android 0.1.65 — 2026-11-01 (Kod gönderme sözleşmesini tanılama)

- İkinci telefonun 30 Ekim kaydında engellenen `useAuthPlatformSubmitCodeMutation` ve `AuthPlatformApproveFromAnotherDevicePollingMutation` işlemleri kimlikleriyle tanınır. Yalnız kök değişken ve bir düzey altındaki giriş alanlarının ad/tür bilgileri tutulur; kod, parola, oturum veya bağlam değerleri okunup kaydedilmez.
- Açık, onaylanmış güvenlik sayfasında bu iki sabit işlemin statik tanımı varsa kimlik, mutation türü, argüman ve kök alan adları doğrulanır. Şema ayrıştırması mevcut kod-ekranı sorgusuyla aynı sahibin kontrolündedir. Bu gözlem yeni ağ yetkisi oluşturmaz; sonraki kod gönderim desteğinin gerçek alanlarını belirlemek içindir.
- Tanı kaydının okuma sınırı, dört belgenin sınırlı giriş alanları ve iki ek şemanın genişlemesini karşılayacak 32 KiB'ye yükseltildi. Önceki kayıtlar korunur.
- 120 JVM testi, 9 JavaScript kontrolü, 648 kaynak denetimi, release lint ve release/debug/test derlemeleri geçti. Yeni ekran testi derlendi; cihazda çalıştırıldığı iddia edilmez. İmzalı paket kullanıcının kendi telefonuna mevcut 0.1.57'nin üzerine veri silmeden kuruldu; yeni canlı tanı sonucu ayrıca bekleniyor.

## Android 0.1.64 — 2026-11-01 (PT-BR ekran yaşam döngüsü)

- Ekranın öne gelmesindeki dil kontrolü, yalnız dil bölümünü (`pt`) tam tercih (`pt-BR`) ile karşılaştırıyordu. Kontrol artık ekran oluşturulurken alınan tam dil tercihiyle güncel tercihi karşılaştırır. PT-BR seçiliyken gereksiz yeniden oluşturma döngüsü önlenir; gerçek dil ve tema değişimleri ekranı yenilemeye devam eder.
- PT-BR ile ana ekranı açan, üç kez arka plana alıp geri getiren, aynı ekran örneğini, bölge etiketini, kayıtları ve 429 molasını kontrol eden çevrimdışı cihaz testi eklendi ve derlendi.
- 118 birim testi, dört dilde 648 kaynak denetimi, release lint ve APK imza/paket doğrulaması geçti. Cihaz testi çalıştırılmadı: telefon test kurulumu reddedildi ve kullanıcı bu telefona kurulum istemedi; derleme sonrası 3,2 GiB boş RAM bulunduğu için emülatör açılmadı. [Doğrulama kapsamı](docs/ANDROID-PTBR-LIFECYCLE-TEST.md).

## Windows/web 0.2.20 — 2026-10-31 (İspanyolca ve Brezilya Portekizcesi)

- Windows uygulaması ve aynı kaynakları kullanan web önizlemesine Español ile Português (Brasil) eklendi. Türkçe ve English seçenekleri korunur; eski yerel depolar Türkçe seçili olarak açılır.
- Dört katalog da aynı 999 uygulama metnini, çoğul biçimlerini ve yer tutucularını kapsar. Ekranlar, erişilebilirlik etiketleri, tepsi menüsü, yeni Windows bildirimleri, yerel hata açıklamaları ile sayı/tarih biçimleri seçilen dilde görünür.
- Dil tercihi cihaz genelindeki şifreli ayarda kalır; hesap kasaları, kullanıcı içerikleri, kanıt metinleri, istek bütçesi, 429 molası ve otomasyon değiştirilmez. Çeviri paketleri uygulamayla birlikte gelir; dil seçimi hiçbir ağ isteği başlatmaz.
- 167 çevrimdışı Python testi, 28 Node testi, 11 tarayıcı kontrolü ve gerçek paketli EXE üzerinde 31 kontrol geçti. Yeni dillerde altı ekranın taşması, yeniden açılış, kişisel metin ve mola korunumu doğrulandı. Canlı Instagram girişi ve gerçek Windows bildirim teslimi bu dil güncellemesinde yeniden denenmedi.

## Android 0.1.63 — 2026-10-31 (Tam İspanyolca ve Brezilya Portekizcesi)

- Uygulama dili seçimine tam İspanyolca ve Brezilya Portekizcesi eklendi. Bölgesel `pt-BR` etiketi Android'in uygulama dili ayarına aynen aktarılır; seçilen dil ve önceki kayıtlar korunur.
- İki paketin de 648 kaynak anahtarı vardır: dil, tema, menüler, takip listeleri, otomasyon, bildirimler, oturum/güvenlik yönlendirmesi ve istek tanıları çevrilidir.
- Dil paketi denetimi dört dilin kaynak türlerini, anahtar kapsamını, biçim parametrelerini ve çoğulları eşitlik olarak doğrular. Android lint’in eksik-çeviri denetimi de yeniden açıktır.
- 118 birim testi, kaynak denetimi ve release lint geçti. Telefon bağlantısı kesildiği için yeni fiziksel cihaz dil-geçiş testi henüz çalıştırılmadı.

## Android 0.1.62 — 2026-10-31 (İlk dil seçimi)

- İspanyolca ve Brezilya Portekizcesi için ilk uygulama dili seçimi sunuldu. Tam kaynak kapsamı ve tek-dilli kaynak çözümü 0.1.63'te tamamlandı.

## Android 0.1.61 — 2026-10-30 (Okunabilir istek tanısı)

- İstek bilgileri tek uzun ileti yerine ayrı, seçilebilir metin bölümleriyle gösterilir. Giriş tanısının her alanı ayrı biçimlendirilir; mevcut tanı ve bekleme kayıtları korunur. Bu değişiklik yeni ağ isteği veya giriş yetkisi oluşturmaz.
- İkinci fiziksel telefonda 0.1.60 tanı penceresi açıkken Android erişilebilirliği uzun ileti metnini boş döndürdü. Kullanıcının mail kodu sonrası bekleme bildiriminin gerçek istek sonucu bu aşamada henüz okunamadı; başarılı bağlantı iddiası yoktur.

## Android 0.1.60 — 2026-10-30 (Güvenlik kodu ekranını okuma · test sürümü)

- Canlı güvenlik sayfasında doğrulanan tek `AuthPlatformCodeEntryViewQuery` sorgusu desteklenir. Sorgu, onaylanmış açık sayfanın bağlamıyla eşleşmeli; yalnız gözlenen `apc` ve boş `device_id` değişkenlerini taşımalıdır. Başka belge, kod gönderimi veya sosyal işlem yetkisi verilmez.
- Ekran okuması ortak istek aralığı ve bütçesini kullanır, kısa bekleme iptal edilebilir. İlk girişin 15 dakikalık aralığı uzatılmaz; 429 molası korunur. Yanıt, giriş sonucu ve profil bağlantısından ayrı tanılanır; gizli içerik kaydedilmez.
- 118 JVM testi, 8 JavaScript kontrolü ve release lint geçti. Yeni cihaz testleri derlendi; gerçek kod/CAPTCHA tamamlanması ve hesap bağlantısı henüz doğrulanmadı.

## Android 0.1.59 — 2026-10-30 (Kod ekranı sorgusunun şeması · test sürümü)

- Gerçek emülatör kaydında engellenen `AuthPlatformCodeEntryViewQuery` / `34414353874878894` kimliği belirlendi. Yalnız bu sorgunun kök değişken adları ve türleri, değerleri kaydedilmeden tanılanır.
- Onaylanmış güvenlik sayfasındaki aynı statik sorgu tanımı okunabiliyorsa sorgu türü, argüman adları ve kök alan adları sınırlı olarak kaydedilir. Eksik kapsam belirtilir; bu gözlem otomatik ağ yetkisi oluşturmaz. Parola, kod, oturum ve CAPTCHA içeriği tutulmaz.
- WebView sonlanma callback’inin Android API açıklaması güncellendi. Gerçek kapanış ve açılamayan CAPTCHA ekranı 0.1.58’de ayrı emülatör testleriyle doğrulandı; genel test grubunun tamamı başarılı diye sunulmaz.

## Android 0.1.58 — 2026-10-30 (Giriş görüntüleyicisi kapanışı)

- Instagram giriş WebView’i sonlandığında etkin giriş/okuma işi iptal edilir ve kullanıcıya açıklama gösterilir. Kapanan görüntüleyiciye yeniden sayfa veya JavaScript gönderilmez; mevcut hesaplar ve bekleme kaydı korunur.
- 0.1.57 emülatör denemesinde 2 GB yapılandırmada test süreci, 3 GB yapılandırmada WebView süreçleri Android tarafından bellek baskısında kapatıldı. Üç kritik çevrimdışı senaryo geçti; test grubunun tamamı geçmedi ve gerçek Instagram girişi gönderilmedi. Bu düzeltme işletim sisteminin tüm uygulamayı bellek nedeniyle öldürmesini engellediği iddiasında bulunmaz.

## Android 0.1.57 — 2026-10-30 (Desteklenmeyen güvenlik adımını tanılama · test sürümü)

- Yetkilendirilmiş Instagram güvenlik sayfasının engellenen GraphQL istekleri yalnız teknik belge numarası ve işlem adıyla sınırlı, en fazla dört kayıt olarak tanılanır. Değişkenler, başlıklar, kodlar, oturum veya CAPTCHA içeriği kaydedilmez; istekler gönderilmez.
- Sayfa açılıp sonraki doğrulama işlemi desteklenmediğinde kullanıcıya açık yönlendirme gösterilir. Bu tanı yeni ağ yetkisi veya tamamlanmış CAPTCHA anlamına gelmez.

## Android 0.1.56 — 2026-10-30 (Güvenlik gezinmesini ayırma · test sürümü)

- Auth_platform sayfası açılmadan eski giriş belgesi temizlenir; aynı WebView ve çerezler korunur. Instagram’ın gerçek ana sayfa HTTP yönlendirmesi yalnız Instagram auth_platform GET’i olarak izlenebilir; sayfa kaynaklı farklı URL, POST ve sosyal istekler engelli kalır.
- Güvenlik sayfası oturum oluşmadan giriş ekranına dönerse tamamlanmadığı açıkça gösterilir. Tanıya URL yerine yalnız sabit belge/yönlendirme sınıfları eklenir; eski kayıtlar okunabilir.

## Android 0.1.55 — 2026-10-30 (Instagram güvenlik sayfası · test sürümü)

- Gerçek 0.1.54 yanıtında ayrı `/auth_platform/` akışı görüldü; klasik Instagram CAPTCHA işareti kapalıydı. Kullanıcı onayı sonrasında aynı giriş WebView’inde yalnız sunucunun döndürdüğü tam güvenlik sayfası GET’i açılabilir.
- Yol ve query yalnız bu açık girişin belleğinde bağlanır. Başka güvenlik bağlantısı, POST, genel GraphQL veya sosyal işlem yetkisi verilmez; iptal bu izni kaldırır. CAPTCHA veya hesap bağlantısının tamamlandığı henüz iddia edilmez.
- [Kaynak incelemesi ve canlı test kapsamı](docs/CAPTCHA-FLOW-INVESTIGATION.md).

## Android 0.1.54 — 2026-10-30 (CAPTCHA akışını ayırma · test sürümü)

- Giriş tanısına Instagram’ın `is_ig_login_recaptcha` işareti, CAPTCHA bağlamının bulunup bulunmadığı ve yönlendirmenin sabit ekran sınıfı eklendi. Eksik veya yanlış türde alan bilinmiyor olarak korunur; bağlantı, query, token ve CAPTCHA bağlamı kaydedilmez.
- Bu sınıflandırma yeni ağ izni vermez. Ayrı challenge bağlantısıyla sayfa içi doğrulamayı ayırmak içindir; gerçek CAPTCHA’nın tamamlandığını veya giriş sorununun çözüldüğünü iddia etmez.

## Android 0.1.53 — 2026-10-30 (Giriş adımlarını açıklama)

- Instagram girişi, yanıt bekleme, kod, CAPTCHA ve profil bağlantısı ayrı başlıklarla gösterilir. Kullanıcıdan işlem beklenirken ilerleme çubuğu durur; kod alanının değeri okunmaz.
- Açılamayan CAPTCHA adımında form kapatılır, girişin tamamlanmadığı ve yanlış parola sonucunun çıkarılamayacağı anlatılır. Instagram’da kontrol et ve İstek bilgileri seçenekleri sunulur. Sayfa yüklemesine süre sınırı eklendi.
- Tanıda doğrulama bağlantısı yerine yalnız sabit neden kodu tutulur; ağ izinleri genişletilmez. [Test kapsamı ve kullanıcı adımları](docs/LOGIN-GUIDANCE-TEST.md). Gerçek CAPTCHA ve iki faktörlü girişin uçtan uca tamamlanması henüz doğrulanmadı.

## Android 0.1.52 — 2026-10-30 (Giriş sonucunu koruma)

- Instagram'ın giriş/CAPTCHA sonucu, sonradan yerel olarak engellenen bir gezinmenin genel uyarısıyla değiştirilmez. Ağ engeli ve doğrulama bağlantısının mevcut izin sınırı korunur; desteklenmeyen bir güvenlik bağlantısının açıldığı iddia edilmez.
- Gerçek emülatör denemesinde eski parola ile CAPTCHA istendi; kullanıcı sonra parolayı düzeltti. Bu ilk deneme geçerli giriş bilgileriyle yapılmış test veya başarılı bağlantı sayılmaz. Yeni giriş mevcut aralığa tabidir.

## Android 0.1.51 — 2026-10-30 (Genel kontrol düzeltmeleri)

- Hesap değişiminden sonra açık kalan eski görsel arşiv ekranı diğer hesabın arşivini değiştiremez; yazım hesap sahibi kontrolü altında yapılır ve eski ekran kapanır.
- Bozuk şifreli katalog/kasada açılış hatası yakalanır; kayıtlar otomatik sıfırlanmaz. Hesap geçişinin hazırlığı istek zamanlayıcısı kurucusundan kayıt sahibine taşındı.
- Bilgisayardan aktarılan mobil bağlantıda profil okuması ile yerel kayıt arasına hesap seçimi/iptal kontrolü eklendi. Bu sırada seçilen başka hesabın yerine geç gelen bağlantı seçilmez. Şifreli yazım arayüz iş parçacığından bağlantı işçisine alındı.
- API 29 WindowInspector kullanan ekran testleri uygun Android sürümünde çalışır; çoklu hesabın diğer veri koruma testleri eski API'lerde de seçilebilir. Kullanım metni güncel bağlantı durumu ve çoklu hesap davranışına uyarlandı.
- [Genel kontrol, testler ve kalan eksikler](docs/CHECKUP-2026-10-30.md). Instagram'ın profil HTTP 429 sınırı bu sürümle çözülmüş olarak bildirilmez; telefonun güvenlik molası değiştirilmez.

## Android 0.1.50 — 2026-10-30 (Çoklu hesap)

- Hesaplarım menüsünden yeni hesap eklenebilir ve kayıtlı hesaplar arasında yeniden giriş yapmadan geçilebilir. Yeni hesap, önceki hesabın kaydını değiştirmez. Aynı numeric ID yeniden bağlandığında izlenen kişiler ve tarihli geçmiş korunur.
- Hesaplar, oturum bankaları, takip listeleri, görsel geçmiş ve günlükler ayrı Keystore kasalarında tutulur. Mevcut tek hesap ek katalogla taşınır; eski ana kayıt yeniden yazılmaz. Hesap silme yalnız seçili hesaba uygulanır; yarıda kalan silme sonraki açılışta tamamlanır.
- Bildirimler ait olduğu hesabı açar. Arka plan işleri tek sırada hesaplara dönüşümlü hizmet verir. İstek bütçesi, aralık ve 429 molası ortak kalır; hesap değiştirmek mola sıfırlamaz ve profil/liste isteği başlatmaz.
- [Doğrulama ve sınırlamalar](docs/ANDROID-MULTI-ACCOUNT-TEST.md). 0.1.49 ile gerçek telefon girişi başarılıydı; profil isteği HTTP 429 aldı. Bu özellik Instagram erişiminin açıldığını iddia etmez.

## Android 0.1.49 — 2026-10-30 (Platform web taşıyıcısı test adayı)

- Destekleyen cihazlarda web giriş ve okumaları Android HttpEngine kullanır. Seçim istekten önce yapılır; sunucu reddinden sonra otomatik eski taşıyıcıya dönüş yoktur. Ek SDK/izin/native kütüphane eklenmedi. Mobil sağlayıcı değişmedi.
- Girişte onaylanan değişmez gövde taşıyıcıya kimliğiyle bağlanır; genel GraphQL açılmaz. Redirect, süre, iptal, gövde kapasitesi ve 429 sınırları korunur. HTTP başlıkları gövde hatasında da kaybolmaz.
- Gelişmiş karşılaştırmada native okuyucu doğrudan denenebilir. Farklı bir hesabın mevcut etkin kaydı değiştirmesi için açık onay gerekir; iptal kaydı korur.
- [Kanıt, sınırlar ve testler](docs/PLATFORM-WEB-TRANSPORT-TEST.md). Taşıyıcının gerçek telefon giriş reddini çözüp çözmediği canlı kontrol gerektirir.

## Android 0.1.48 — 2026-10-29 (Tek denemenin sonucunu açık gösterme)

- Instagram oturum açılmadığını döndürdüğünde giriş formu kapatılır; native sonuç ve bir sonraki yerel deneme zamanı gösterilir. Yerel engelden sonraki denemeler sunucu reddi gibi algılanmaz. Mevcut 429, bütçe ve giriş aralığı değiştirilmedi.
- CAPTCHA ve iki aşamalı işlem insan doğrulamasında kalır; başarılı normal girişteki tek profil okuması korunur. Hesap doğrulanmadan etkin kayıt değiştirilmez.
- [Telefon tanısı, test kapsamı ve açık kalan giriş doğrulaması](docs/WEB-LOGIN-TERMINAL-RESULT-TEST.md). Bu değişiklik tek başına Instagram girişinin her cihazda çalıştığı anlamına gelmez.

## Android 0.1.47 — 2026-10-29 (Tek akışta giriş ve güvenlik doğrulaması)

- Girişin yanıtı native arayüzde gösterilir; CAPTCHA gereksinimi genel parola mesajından önce değerlendirilir. Sonuç dönmüşken kullanıcıya hâlâ giriş bekleniyormuş izlenimi verilmez.
- Önerilen bağlantıda başarılı oturumdan sonra kısa okuma aralığı beklenir ve tek profil isteğiyle bağlantı tamamlanır. Gelişmiş A/B karşılaştırması elle çalışır; otomatik yeni giriş veya başarısız profil okumasını tekrar deneme eklenmedi.
- Instagram'ın aynı giriş yanıtından gelen geçerli güvenlik bağlantısı, kullanıcının açıklamayı onaylamasıyla açılabilir. Yalnız bu challenge'ın okuması, güvenlik action yolu ve Meta/Google CAPTCHA kaynakları için geçici izin vardır. Instagram oturumu dış alan adlarına kopyalanmaz; izin giriş ekranı kapatılınca bırakılır. CAPTCHA'yı kullanıcı tamamlar.
- Telefonda son HTTP 200 yanıtında oturum yoktu ve CAPTCHA isteniyordu; önceki profil HTTP 429 sorunu ayrı kalır. [Kanıt, gizlilik sınırı ve doğrulama](docs/WEB-LOGIN-CHALLENGE-TEST.md).

## Android 0.1.46 — 2026-10-29 (Giriş sonrası doğrulama)

- Doğrulama düğmesi giriş sonrası kısa okuma aralığında kendiliğinden yeniden etkinleşir. Düğme hazırlığı tek yerde hesaplanır; zamanlayıcı ağ isteği göndermez veya hata mesajını silmez.
- Tarayıcı karşılaştırması, girişten sonraki yerel `/accounts/onetap/` karşılama sayfasını mevcut sayfa sözleşmesinden tanır. Giriş/doğrulama sayfaları ve genel ağ izinleri genişletilmez.
- 0.1.46 canlı girişte başarılı; A artık gönderiliyor ancak Instagram profil okumasına HTTP 429 döndürdü. B ve yeni girişler durduruldu. Tam hesap bağlantısı henüz doğrulanmadı; bu sürüm test önizlemesidir. 99 JVM testi ve 38 emülatör testi geçti (1 opt-in atlandı). 0.1.45 ayrı yayınlanmadı. [Kontroller ve canlı doğrulama kapsamı](docs/LOGIN-LANDING-TEST.md).

## Android 0.1.44 — 2026-10-29 (Web istek bağlamı)

- Aynı hesapsız giriş formu okuması tarayıcıda başarılı, dış taşıyıcıda HTTP 200 / 1357054 oldu; üç Fetch Metadata başlığı eklenince dış taşıyıcı da veri döndürdü. Giriş ve web profil/liste okuyucuları aynı sabit köken bağlamını kullanır. Başka kökenlere veya mobil oturumlara uygulanmaz; istek gövdesi/yol izinleri değişmez.
- Normal giriş sonucunu içermeyen kök sunucu hatası parola kararı olarak iletilmez; uygulama açık bir giriş yanıtı hatası gösterir ve bu yanıttaki çerezleri uygulamaz. Aynı ekrandaki ikinci gönderim biçim hatası yerine yerel bekleme sayılır.
- Canlı emülatörde authenticated=true ve iki oturum çerezinin uygulandığı doğrulandı. Ancak sonraki 15 saniyelik aralıkta doğrulama düğmesi yeniden etkinleşmedi; A/B henüz tamamlanmadı. Sonraki sürüm bu arayüz zamanlayıcısını ele alır. [Kanıt ve kapsam](docs/WEB-REQUEST-CONTEXT-TEST.md).

## Android 0.1.43 — 2026-10-29 (Giriş yanıtını ayıran tanı)

- LP01 rapor biçimi 2, HTTP yanıtı ile kimlik doğrulama sonucunu ayırır. Yalnız JSON şekli, sayısal hata kodları ve bilinen giriş belgesinin authenticated/captcha/iki aşamalı işlem/redirect varlığı işaretleri saklanır. Mesaj metni, hesap kimliği, nonce, URL, çerez değeri ve ham yanıt saklanmaz. Bilinmeyen biçim ve ayrıştırılmayan kodlar açık kalır.
- Set-Cookie tek kez ayrıştırılır; aynı olgular çerez kabulünde ve tanıda kullanılır. Oturum çerezi alınması ile çerez callback’inin tamamlanması ayrı gösterilir. Kamuya açık giriş belgesinin caa_login_web alanları temel alınır; HTTP 200 tek başına başarılı giriş sayılmaz.
- Tarayıcının gerçek giriş isteğinde görülen x-ig-max-touch-points başlığı dar aktarım listesine eklendi. Yalnız Instagram hedefi, şifreli gövde sözleşmesi, bütçe, 15 dakikalık giriş aralığı ve 429 durdurması korunur. Bu adayın giriş başarısı ayrıca canlı doğrulanmalıdır.

## Android 0.1.42 — 2026-10-29 (Şifreli web giriş sözleşmesi adayı)

- Yeni Instagram giriş formunun useCDSWebLoginMutation isteği, genel GraphQL engeli yüzünden gönderilmeden durabiliyordu. Instagram sayfasının gösterdiği yanlış giriş bilgileri mesajı bu durumda parola doğrulaması kanıtı değildi. Telefonda engelli GraphQL istekleri görüldü; hesapsız, sentetik giriş verisiyle işlemin adı/belge kimliği ve şifreli alan biçimi yakalandı; bu incelemede gerçek giriş gönderilmedi.
- Kaynağı yalnız instagram.com ana giriş çerçevesi olan, sabit belge/işlem/yol sözleşmeli yerel geçiş eklendi. Düz parola, şifrelenmemiş sürüm 0, başka GraphQL belgeleri, ek query alanları ve sosyal işlemler reddedilir. Genel tarayıcı GraphQL izni açılmaz. İstek/yanıt gövdeleri ve giriş bilgileri kaydedilmez; yalnız Instagram’ın sabit HTTPS adresi kullanılır.
- Tek giriş ekranından yalnız bir onaylı gönderim yapılır. Giriş için kalıcı 15 dakikalık yerel aralık ve ortak bütçe uygulanır; sonraki hesap doğrulaması kendi mevcut aralığını kullanır. 429, yönlendirme, iptal ve eski sayfadan gelen sonuçlar otomatik yeniden denenmez. Çerezler yalnız Instagram kapsamıyla kabul edilir.
- Desteklenmeyen istek veya WebView yeteneği uygulama tarafından açıklanır; görünürlük tanısı bu açıklamanın üzerine genel giriş yönergesi yazmaz. TR/EN gizlilik açıklaması şifreli giriş isteğini ve saklanmayan parola/kodu açıklar. Canlı hesap doğrulaması ayrıca gereklidir; bu sürüm tüm giriş/doğrulama varyantlarının desteklendiği vaadi değildir.

## Android 0.1.41 — 2026-10-29 (Giriş sayfası uyumluluğu adayı)

- Giriş WebView'ı masaüstü sayfa sunumunu ister; kurulu Chromium sürümü korunur, sayfayı yakınlaştırma desteklenir. Genel GraphQL izni açılmaz. Mobil giriş sayfasının engellenen GraphQL sorgusuna bağımlı kaldığı hesapsız tarayıcı karşılaştırmasında görüldü; bu değişiklik hesap bağlantısının tamamlandığı anlamına gelmez.
- Yalnız sayfa yükleyicisi GET /ajax/bootloader-endpoint/ ve rota tanımları POST /ajax/bulk-route-definitions/ için www.instagram.com üzerinde kesin yol/yöntem istisnası eklendi. Telemetri, Facebook adresleri ve sosyal işlemler engelli kalır; yerel hesap okuyucusunun sözleşmesi değişmez.
- LP01 görünürlük tanısı yeni email/pass alanlarını da tanır; form değerlerini okumaz. Test ve canlı doğrulama kapsamı docs/LOGIN-PAGE-DIAGNOSTICS.md dosyasına eklenir.

## Android 0.1.40 — 2026-10-29 (Giriş sayfası tanısı)

- Giriş formu gelmeden kalan WebView için Sayfa tanısı / LP01 eklendi: sabit kaynak kategorileri, izin/engel sayaçları, sayısal hata ve giriş alanı görünürlüğü. URL, parola, kod, çerez veya form içeriği tutulmaz; yeni ağ izni/isteği eklenmez.
- Tanı mevcut ekran kapatılmadan okunur. Yüklenen belge ile hazır giriş formu ayrılır; eski sayfa olayları yeni tanıya yazılmaz. 429 ve sosyal işlem engelleri korunur. Bu bir tanı sürümüdür; canlı giriş başarısı henüz doğrulanmadı. [Kapsam ve test](docs/LOGIN-PAGE-DIAGNOSTICS.md).

## Windows/web 0.2.19 / Android 0.1.39 — 2026-10-29 (Sade kullanıcı dili)

- Gözlem zamanı → Son kontrol; Kayıt kapsamı → Neler kontrol edildi?; Kısmi kayıt → Liste henüz tamamlanmadı. Sayaç, geçmiş, bildirim, liste ve profil durumlarındaki Türkçe/İngilizce açıklamalar günlük dile uyarlandı.
- Normal istek arası Kısa mola olarak gösterilir. Instagram kısıtlaması açıkça ayrılır; bekleme süresi mevcut gerçek son tarihten gelir. Güvenlik, kesin bitiş veya kesin takipten çıkış vaadi eklenmez.
- Windows sınırlama, liste eksikliği ve bağlantı hatalarında hata koduna göre sade açıklama gösterir. Özgün hata metni ve HTTP kodları İstek bilgileri/katlanır Teknik ayrıntılar altında korunur. Ana ekranın hata etkinliği sade başlık gösterirken geçmiş kaydının özgün tanısı açılabilir; arşivdeki kanıt ve tanılar değiştirilmez.
- Liste değişimleri Yeni görülen takipçiler / Son kontrolde görünmeyenler olarak anlatılır. Geri takip filtreleri yönüne uygun adlandırılır ve kaydedilen listelere dayandığı açıklanır. Kontrol aralıkları, ağ istekleri, oturumlar, bütçeler ve 429 durdurmaları değişmez. [Test ve sınırlar](docs/PLAIN-LANGUAGE-TEST.md).

## Windows/web 0.2.18 / Android 0.1.38 — 2026-10-29 (Anlaşılır gezinme)

- Kişiler bölümünün adı Kaydedilen Profiller, Listeler bölümünün adı Takip Listeleri oldu. Menüler, arama paleti, ekran açıklamaları ve ilgili erişilebilirlik metinleri Türkçe/İngilizce birlikte güncellendi. Kaydetmek ile Instagram’da takip etmek ayrımı korunur.
- Boş profil ekranı yalnız özellikle kaydedilen profilleri gösterdiğini açıklar; Profil ekle ve Takipçi listemi aç seçeneklerini sunar. Kendi liste kısayolu hesap/yön seçimini sıfırlar ve ağ isteği başlatmadan kayıtları açar. Bağlı hesap yoksa Windows kısayolu kapalıdır.
- Windows liste düğmesi ilk okumada Listeyi kontrol et, mevcut kayıtta Listeyi yeniden kontrol et der. Android devam sayfası varsa Listeyi okumaya devam et gösterir. Sayfalama ve istek molası açıklamaları, sayfa/hesap sayacı ve mevcut geri sayımı tamamlar; güvenlik veya kesin bitiş süresi vaat edilmez.
- Ortak boş ekran stili ve daha okunur, satıra bölünebilen mobil web menüsü eklendi. Web hâlâ örnek verili önizlemedir; hesap bağlantısı eklenmedi. Kontrol aralıkları, oturumlar, şifreli kayıtlar ve otomatik başlangıç davranışı korunur. [Test ve kapsam](docs/NAVIGATION-CLARITY-TEST.md).

## Windows 0.2.17 / Android 0.1.37 — 2026-10-29 (Otomatik başlangıç listeleri)

- Yeni hesap bağlantısı başarıyla doğrulanınca kendi takipçi/takip edilen listeleri otomatik hazırlanır. Başlangıç, ilerleme, bekleme, duraklatma ve hazır durumları ana ekranda görünür. Geçmişteki takipten çıkışların bilinmediği ve ilk kaydın yalnız başlangıç olduğu açıklanır. Mevcut hesaba yeniden giriş, kayıtlı kapalı otomasyon tercihini açmaz; önceki başlangıç kayıtları yeniden giriş yüzünden taratılmaz.
- Windows otomatik liste işi tur başına tek sayfa kaydeder. Devam bilgisi şifreli kasada tutulur, arayüze ve gözlem yedeğine aktarılmaz. Yeniden açılış/hesap geçişinde devam eder; eski veya reddedilen devam bilgisi güvenli yeni okumaya döner. Kısmi kayıtlar ve önceki tamamlanmış liste ayrı kalır. Profil işleriyle sıra paylaşır; hesaplar birbirinin kaydına yazmaz.
- Liste sayısı değiştiğinde ve yaklaşık günlük hedefte yeni taramalar sıraya girer. Windows'ta sayı değişikliği taraması için en az 15 dakikalık alt aralık vardır; gerçek zaman profil kontrolü, sayfalama ve ortak bütçeye bağlıdır. İlk taramadan fark/engellenme bildirimi üretilmez. Yarım, belirsiz veya sayı uyumsuz listeden yeni karşılaştırma yapılmaz.
- Android ilk hazırlığı görünür ekranda bildirim izni olmadan ilerletir; sayfa kayıtları ekran kapanınca korunur. Mevcut JobScheduler daha sonra kendi kısıtları içinde devam edebilir; anlık veya kesin bitiş garantisi yoktur. Kayan bütçe, HTTP 429 ve doğrulama durdurmaları korunur. Bildirim teslimi için izin hâlâ gereklidir; okuma durumunda yanlış izin bekleniyor mesajı düzeltilir.
- Türkçe/İngilizce birlikte güncellendi. Yeni veri paylaşımı veya sosyal işlem isteği yoktur. Canlı Instagram ve önceki Android giriş sorunu bu değişiklikte doğrulanmadı. [Kullanım, test ve sınırlar](docs/AUTOMATIC-PREPARATION-TEST.md).

## Windows/web 0.2.16 / Android 0.1.36 — 2026-10-29 (Görsel incelikler)

- Ana sayaçlar, kişi sayıları ve sayısal ayrıntılarda eşit genişlikli rakamlar kullanılır. Kullanıcı metinlerinin harf aralığı değiştirilmez. Android profil özetindeki uzun sayılar tek satırda uyarlanır; tam değer metinde korunur.
- Windows/web düğme, ikon, hesap seçici ve sayaç kartlarında kısa, bir piksellik basma geri bildirimi tutarlı hale getirildi. Kart hover hareketi hafifletildi. Azaltılmış hareket tercihinde hem geçiş hem hareket kapatılır. Android birincil düğmelerde açık, diğer yüzeylerde temaya uygun native ripple kullanır; yeni yaylanma animasyonu eklenmedi.
- Koyu/OLED kartlarda ortak paletten ince iç üst ışık, açık temada hafif derinlik eklendi. Mevcut kenarlık ve OLED siyah zemin korunur. Native Android kartlarının üst ışığı statiktir; bulanıklık veya sürekli çalışan yeni zamanlayıcı yoktur.
- Windows/web kaydırma çubukları temaya uyumlu ve 10 px tutma alanlıdır; yüksek kontrast modunda sistem görünümü kullanılır. Android ana ekran ve takip listelerinde temaya uygun, kaydırırken görünen native gösterge açıldı. 156 Python, 28 Node, 88 JVM testi; 16 Android emülatör testi; 51 Windows işlev/hesap/ilerleme kontrolü ve 8 Windows/web tema senaryo grubu geçti. [Doğrulama ve sınırlar](docs/VISUAL-POLISH-TEST.md).

## Windows 0.2.15 / Android 0.1.35 — 2026-10-29 (Hesap güvenliği sınırları)

- Ortak yerel API okuyucuları artık HTTPS alan adına ek olarak yöntem, gövde ve kesin yol sözleşmesini denetler. Takip, takipten çıkma, beğeni, DM ve bilinmeyen adresler gönderimden önce reddedilir. Giriş ve cihaz kaydı için gerekli POST'lar ayrı, dar kapsamlı istisnalardır; genel GraphQL yetkisi verilmez.
- Windows'ta başarısız girişten sonra cihaz kimliğinin yeniden üretilmesi önlendi. Yalnız cihaz bağlamı DPAPI ile saklanır; parola, doğrulama kodu ve hesap oturumu bu kayda eklenmez. Gerçek istemci kütüphanesinin kullandığı cihaz kaydı/attestation alan adları sınırlı sözleşmeyle desteklenir. Üç HTTP oturumu aynı kapıdan geçer; ortam proxy'leri kullanılmaz.
- Kalıcı, kayan saat/24 saat istek bütçesi eklendi. Windows: hesap başına 120/1200, ortak 240/2400; Android: cihazda 120/1200 sayaçlı uygulama isteği. Bunlar Meta'nın güvenli kotaları değildir. Bütçe, 429'dan ayrı gösterilir; yeniden açılış ve hesap geçişiyle sıfırlanmaz. Yoğun takip veya büyük liste taraması sonraki bütçe zamanını bekleyebilir.
- Android giriş tarayıcısı giriş/doğrulama sayfaları ve bilinen kaynaklarla sınırlandı; sosyal işlem adresleri, genel GraphQL ve servis çalışanı ağ yüklemeleri engellenir. Giriş sonrası ana sayfa yerine yerel boş karşılama kullanılır. Bilinmeyen giriş protokolleri otomatik genişletilmez.
- Bağlantıyı kaldırmanın Instagram sunucusundaki oturumu iptal etmediği TR/EN açıklanır. Veri aktaran yeni servis yoktur. 156 Python, 28 Node, 88 Android JVM ve 50 Android emülatör testi; paketlenmiş Windows'ta 30 işlev, 10 hesap ve 11 ilerleme kontrolü geçti. Canlı Instagram girişinin/önceki Android 429 sorununun çözüldüğü iddia edilmez. [Kapsam, testler ve kullanım](docs/ACCOUNT-SAFETY-TEST.md).

## Windows 0.2.14 — 2026-10-29 (12–18 saniyelik istek aralığı)

- Sayaçlı API ve oturumsuz profil isteklerinin ortak kapısı, her giden istek için bir kez 12–18 saniyelik eşit olasılıklı süre seçer. Sonraki istek zamanı şifreli kaydedilir; hesap geçişi, yeniden açılış, iptal ve yerel düzenlemeler süreyi yeniden seçmez. Önceki daha uzun rezervasyonlar korunur.
- Türkçe/İngilizce istek, erişim ve zamanlama açıklamaları sınırları doğrudan backend politikasından alır. Mevcut geri sayım seçilen mutlak zamana uyar; yeni periyodik zamanlayıcı eklenmez. Beş dakikalık profil aralığı, 429/Retry-After ve 15 dakikalık parola giriş aralığı değişmez.
- Beklenen seçilmiş süre ortalaması 15 saniyedir; gerçek istek aralığı daha uzun olabilir. Bot tespitinden veya 429’dan korunma garantisi değildir. Android ve örnek web zamanlayıcısı değiştirilmedi. [Doğrulama ve sınırlar](docs/REQUEST-JITTER-TEST.md).

## Windows 0.2.13 — 2026-10-29 (Ortak molayı uzatmayan yerel ret)

- 0.2.12’de hesap değişiminden sonra mola sürerken elle yenilemenin, yeni HTTP yanıtı olmadan yeni sınırlama gibi yorumlanıp süreyi uzatması düzeltildi. Yerel mola `cooldown`, yeni Instagram sınırlaması `limited` olarak ayrılır. Erişim ve anonim inceleme aynı ayrımı kullanır.
- Yeni regresyon, iki ardışık elle yenilemede HTTP gönderilmediğini; bitiş, sayaç ve sınırlama serisinin değişmediğini ve yeniden açılışta korunduğunu doğrular. Önceki 0.2.12 ZIP’i korunur; güncel önerilen önizleme 0.2.13’tür.

## Windows 0.2.12 — 2026-10-29 (Çoklu hesap)

- Sol altta hesap seçici ve Hesap ekle eklendi. Sayısal Instagram kimliğine göre bağımsız DPAPI kasaları kullanılır; geçiş Instagram isteği veya parola girişi göndermez. Mevcut ilk hesap ve oturumu yerinde korunur.
- Kişiler, notlar, listeler, geçmiş, profil zaman tüneli, bildirim ve kontrol ayarları hesaplara ayrıldı. Dil, tema ve kompakt görünüm cihaz genelindedir. Silme/bağlantıyı kaldırma yalnız seçili hesabı etkiler; farklı hesaba ait yedeğin üzerine yazılması reddedilir.
- Tek worker ve istek sırası bütün hesaplara hizmet verir. Arka planda işlenen hesabın sonuçları kendi kasasına yazılır, görünür hesap korunur. Bildirim ilgili hesabı belirtir ve doğru hesabın kaydına gider. İşlem sürerken geçişe izin verilmez. Ortak 429 molası, giriş aralığı ve istek rezervasyonu korunur.
- Windows önizlemesidir. Gerçek iki hesap girişi/uzun süreli izleme ve native bildirim teslimi canlı doğrulanmadı. Android 0.1.34 ve webin örnek veri kapsamı değişmedi. [Doğrulama ve kullanım](docs/MULTI-ACCOUNT-TEST.md).

## Android 0.1.34 — 2026-10-29 (429 kaynağını doğru gösterme)

- WebView'deki farklı bir Instagram kaynağının 429 yanıtının, A sürerken W01 profil yanıtı gibi yazılması düzeltildi. Profil isteğinin sahibi artık etkin HTTPS origin/yolu eşleştirir; gerçek profil 429'u ortak sonuç yolundan W01 ve sayaçlı HTTP kaydına bir kez işlenir.
- W00 sayfa kaynağı / W01 tarayıcı profili / W02 uygulama ayrımı eklendi. Başka kaynak 429 verirse W00 tutulur, bekleyen profil iptal edilir ve HTTP sonucu uydurulmaz. İlk sınırlamada durma, mevcut mola ve hesap korunur; otomatik tekrar veya alternatif ağ yolu eklenmedi.
- Eski 0.1.33 tanıları korunur ve kaynak belirsizliği açıklanır. Türkçe/İngilizce etiketler güncellendi. Bu sürüm tanı düzeltmesidir; canlı Instagram girişinin düzeldiği henüz doğrulanmadı. Windows 0.2.11 ve web önizlemesi değişmedi. [Doğrulama ve sınırlar](docs/WEB-429-ORIGIN-TEST.md).

## Android 0.1.33 — 2026-10-29 (Web bağlantısı karşılaştırması)

- ⋯ → Gelişmiş → **Web bağlantısını karşılaştır** eklendi. Kullanıcının tamamladığı Instagram girişinden sonra A adımı profili aynı WebView içinde okur; B ayrı açık kullanıcı eylemiyle uygulama okuyucusunu doğrular. İkisi mevcut üretim profil istek tanımını kullanır. A tek başına hesabı bağlamaz; doğru hesapla B başarısı mevcut şifreli bağlantı yolunu tamamlar.
- Mevcut 15 saniyelik istek aralığı ve 15 dakikalık karşılaştırma turu rezervasyonu korunur. B yalnız başarılı A'nın süreli/tek kullanımlık devamıyla açılır; ikinci istek otomatik gönderilmez. İlk 429/doğrulama/iptalde durur. Arka plan veya gezinme devamı iptal eder; ana hesap ve kısmi olmayan mevcut kayıt korunur. Yeni sunucu, telemetri veya oturum paylaşımı eklenmedi.
- İstek bilgileri'ne W01 tarayıcı / W02 uygulama tanısı eklendi. Yalnız sabit adım, başlama saati, sonuç ve HTTP saklanır; parola, çerez, kimlik, kullanıcı adı veya ham yanıt yoktur. Eksik/tamamlanmamış sonuç ve eski HTTP ayrı tutulur; Türkçe/İngilizce desteklenir. Web okuyucusu hesabın bağlantısı tamamlanmadan otomatik kontrolleri yeniden başlatmaz.
- Bu, kontrollü doğrulama için Android önizleme sürümüdür; canlı Instagram sorununun çözüldüğü henüz doğrulanmadı. Windows 0.2.11 ve web önizlemesi değiştirilmedi. [Telefon test adımları ve doğrulama kapsamı](docs/WEB-CONNECTION-COMPARISON.md).

## Windows/web 0.2.11 ve Android 0.1.32 — 2026-10-28 (Koyu ve OLED tema)

- Sistem / Açık / Koyu / OLED siyahı tema seçimi eklendi. Sistem varsayılandır ve açık/koyu değişimini izler; açık seçim sistemden bağımsız kalır. Windows'ta Ayarlar → Tema, Android'de ⋯ → Tema, örnek webde Ayarlar üzerinden seçilir. Tercih her cihazda yerel saklanır; Windows şifreli ayarları/yedekleri, Android özel görünüm tercihi ve web tarayıcı tercihi kullanılır. Hesap verisi paylaşılmaz; tema için ağ servisi veya periyodik kontrol yoktur.
- Ortak `design/theme.json`, CSS değişkenlerini, Android paletini ve native pencere/dialog renklerini üretir. Arka plan, kart, kenarlık, metin, form, grafik, bildirim, arama paleti ve biyografi farkları koyu renklerle uyumludur. Düğme zemini ve okunur vurgu metni ayrı renklerdir. OLED ana zemini siyah, kartları hafif ayrışan koyudur. Profil görsellerine renk ters çevirme uygulanmaz.
- Tema değişimi hesap/kontrol/bildirim tercihlerini, son hatayı ve 429 molasını korur. Eski Windows deposu ve yedekler Sistem tercihiyle açılır. Android'de dil ve tema yeniden oluşturması ortak arayüz yaşam döngüsünde ele alınır; yalnız görünüm değişimi nedeniyle yeni profil kontrolü yapılmaz. Türkçe/İngilizce etiketler birlikte güncellendi.
- Windows 0.2.11 ve Android 0.1.32 önizleme sürümleridir. Web mevcut örnek veri önizlemesidir; yeni gerçek hesap işlevi eklenmedi. Test kapsamı ve canlı doğrulama sınırları [tema doğrulama kaydındadır](docs/APPEARANCE-TEST.md).

## Windows 0.2.10 — 2026-10-28 (Şeffaf tarama ilerlemesi)

- Liste ilerlemesi; hesap, yön, sıradaki/alınan sayfa, kaydedilen benzersiz hesap sayısı ve son profil sayısıyla gösterilir. Bu bağlam profil hazırlığı, istek aralığı ve ağ yanıtı boyunca korunur. Toplam sayfa/yüzde tahmin edilmez; profil sayısının tarama sırasında değişebileceği belirtilir.
- Mevcut 15 saniyelik yerel istek aralığı için saniyelik geri sayım ve süre çubuğu eklendi. Sıfırda yeni istek üretmez; sonraki aşama backend olayından gelir. HTTP 429 ayrı duran mola durumudur; kayıtlı en erken saat ve İstek bilgileri gösterilir. Otomatik tekrar veya erişim garantisi verilmez.
- Tek yerel zamanlayıcı yalnız görünür beklemede çalışır; tepsiye gizleme, küçültme, süre sonu, iptal, işlem sonucu ve hizmet hatasında durur. Windows görünürlük olayı açıkça aktarılır; yeniden açılış eski saniyeleri oynatmadan güncel duruma döner. İptal isteği geciken ilerleme olaylarıyla geri alınmaz; kısmi kayıtlar korunur.
- Türkçe ve İngilizce birlikte güncellendi. 116 offline backend testi, 25 Node testi, 9 yeni ilerleme senaryosu, 30 mevcut masaüstü ve 10 kısayol/boş ekran kontrolü geçti. Gerçek EXE ve worker sentetik depolarla sınandı; canlı Instagram/429 tekrarı yapılmadı. Windows önizleme sürümüdür; Android 0.1.31 ve örnek web işlevleri değiştirilmedi.

## Windows 0.2.9 — 2026-10-28 (Kısayollar ve yönlendiren boş ekranlar)

- `Ctrl+K` / `/`, kayıtlı kişiler, kendi hesabın ve uygulama ekranları için yerel arama paletini açar. Ok tuşları ve Enter ile seçim yapılır; `@` yalnız profilleri arar. Türkçe karakterli adlar ASCII sorgularla da eşleşir. Arama Instagram isteği göndermez; en fazla 12 sonuç gösterilir ve toplam sonuç sayısı belirtilir.
- `Ctrl+R`, açık kişi profilini, Listeler'de seçili kayıtlı hesabı veya Özet'te kendi hesabını mevcut yenileme yoluyla kontrol eder. Yerel mola, devam eden işlem, bağlantı ve form odağı korunur. Native pencere yenilemesi yerine profil işlemi yapılır; tuş basılı tutulunca tekrar komut üretilmez.
- `1–5` sırasıyla Özet, Kişiler, Listeler, Bildirimler ve Ayarlar'ı açar. Zaman çizelgesi menüde ve palet aramasında kalır. Yazı alanlarında sekme kısayolları devreye girmez. `Esc` en üst paleti/modalı kapatır; palet açıkken alttaki taslak korunur.
- Kişiler ve Bildirimler'in boş durumlarına kısa açıklamalar, adımlar ve çalışan yönlendirme düğmeleri eklendi. İlk kullanım, arama/filtre sonucu bulunamaması ve okunmamış bildirim kalmaması farklı anlatılır. Bildirim ve otomatik kontrol ayarları kendiliğinden açılmaz. Türkçe ve İngilizce metinler birlikte güncellendi.
- Hızlı sekme geçişinden geciken hash olayının yeni açılan paneli kapatması ve palet kapanışında form odağının kaybolması giderildi. Bu sürüm Windows kapsamındadır; Android 0.1.31 ve örnek web arayüzünün işlevleri değiştirilmedi.

## Windows 0.2.8 — 2026-10-28 (Oturumsuz erişim karşılaştırması)

- Sağlıklı bağlı oturumda hedef ID için gerçek 404 alındığında son bilinen kullanıcı adına tek çerezsiz profil sayfası isteği eklendi. Aynı sayısal kimlik oturumsuz sayfada doğrulanır ve tam takipçi kaydındaki yoklukla birleşirse **Güçlü engellenme şüphesi** kaydedilir. Bu kesin engelleme tespiti değildir.
- Anonim 404/410, iki kontrolde de bulunamama olarak gösterilir; dondurma, silme ve ad değişikliğiyle engelleme ayrıştırılmaz. Giriş ekranı, genel HTTP 200, kimlik uyuşmazlığı, doğrulama, bağlantı hatası ve 429 belirsiz kalır. Bu sonuçlar engellenme bildirimi üretmez.
- Kanıt ekranı ve bildirim günlüğü kontrol edilen adı, iki erişim bağlamını, HTTP durumlarını, anonim kontrol zamanını ve sınırlamaları Türkçe/İngilizce gösterir. Eski kayıtlar anonim doğrulanmış gibi sunulmaz. Önceki zayıf şüphe aynı olayda güçlü kanıta yükselirse bir kez yeni bildirim üretilebilir.
- Oturum/Authorization aktarılmaz; ortam proxy/netrc bilgisi kullanılmaz, yönlendirme veya otomatik tekrar yoktur. Ortak 15 saniyelik aralık ve 429 molası korunur. Ham HTML ve çerezler saklanmaz; yalnız küçük sonuç metaverisi şifreli depoya/yedeklere girer.
- Anonim ID çözümleme ve kullanıcı adı değişikliği için ilerleme notları [geliştirme notlarına](docs/DEVELOPMENT-BACKLOG.md) eklendi. Bu özellik Windows kapsamındadır; Android 0.1.31 ve örnek web sürümü için yeni hesap işlevi eklenmedi.

## Windows/web 0.2.7 ve Android 0.1.31 — 2026-10-28 (Profil zaman tüneli)

- Kendi hesabı ve kayıtlı kişiler için ayrı açılan, başlangıçta kapalı **Profil zaman tüneli** eklendi. Biyografi farkları kelime bazında yeşil/`+` ve kırmızı/`−` ile gösterilir; silinen metin ayrıca üstü çizili görünür. İlk bilinen biyografi başlangıç kaydıdır; okunamayan alan boş biyografi sayılmaz.
- Yerel avatar galerisi aynı görseli tek kopya tutar, ilk/son gözlem tarihini gösterir. Açılmadan önceki fotoğraflar yeniden oluşturulmaz; indirilemeyen yeni fotoğraf yerine eski fotoğraf gösterilmez. Android'de yalnız normal kullanımda yüklenen küçük fotoğraflar eklenir.
- Değişiklik arşivi dönen sayaç geçmişinden ayrıdır: Windows'ta 366 gözlem sınırı bu arşivi silmez. Profil başına 200 değişiklik / 24 farklı küçük görsel; Windows'ta toplam 8 MiB görsel, Android'de toplam 1,8 MiB arşiv bütçesi vardır. Sınırda eski kayıtlar korunur ve uyarı gösterilir.
- Windows arşivi DPAPI deposuna ve oturumsuz şifreli yedeklere dahildir. Android arşivi ayrı Keystore/AES-GCM kaydındadır. Duraklatma geçmişi korur; onaylı temizleme arşivi silip kaydı durdurur. Windows otomatik önceki depo kopyası da temizlenir; önceden dışa aktarılan yedekler kullanıcıda kalır. Yeni sunucu/telemetri veya hesap API isteği eklenmedi.
- Türkçe/İngilizce metinler ve ortak görsel dil korundu. Web tasarım önizlemesinde aynı fark görünümü örnek kayıtlarla gösterilir; gerçek web hesap bağlantısı eklenmedi.

## Windows/web 0.2.6 — 2026-10-28 (Türkçe ve İngilizce)

- Windows'a Ayarlar → Uygulama dili, örnek web arayüzüne Ayarlar → Arayüz dili eklendi. Türkçe/English seçimi Windows DPAPI deposunda veya tarayıcı tercihinde korunur; eski Windows kayıtları Türkçe açılır.
- Ortak yerel katalog; ekranlar, mobil gezinme, erişilebilirlik etiketleri, tepsi, uygulama iletişimleri, bildirimler ve bilinen hata metinleri çevrilir. Tarih/sayı ve İngilizce tekil/çoğul biçimleri dile uyar. Hesap/kontrol/mola tercihleri ve kullanıcı içerikleri değişmez. Geçmiş kanıtlar yeniden yazılmaz; yeni dile yalnız gösterimde uyarlanır.
- Yalnız dil değiştirmek son hatayı silmez ve Instagram isteği göndermez. Üçüncü taraf çeviri servisi kullanılmaz. Android'de mevcut Türkçe/İngilizce kaynak ve seçim akışı korundu.
- 88 offline backend, 18 Node, 30 paketlenmiş Windows kontrolü ve 8 iki dilli tarayıcı kontrolü geçti. İngilizce yeniden açılış, Türkçeye dönüş, kişisel metin/kanıt ve mola korunumu, 6 Windows rotası, webde 5 rotanın masaüstü/mobil taşması ve yerel ağ sınırı doğrulandı. Gerçek Instagram/Windows toast teslimi bu dil değişikliğinde canlı denenmedi.

## Windows 0.2.5 — 2026-10-28 (gizli pencere iş yükü)

- Gizli veya küçültülmüş pencereye ara sonuç/ilerleme/veri kopyaları gönderilmez. Gözlemler ve Windows bildirimi seçimi arka planda sürer; açılışta güncel durum bir kez eşlenir. Kod beklemesi, hizmet hatası ve son bildirim uyarısı korunur; restore sırasında gelen bildirim tıklaması güncel kayıttan sonra açılır.
- Tepsi menüsü her ilerleme olayı yerine yalnız kullanıcı eylemini etkileyen durum değişince oluşturulur. Kontrol aralığı ve API molaları korunur.
- 84 backend, 14 Node ve 29 paketlenmiş EXE kontrolü geçti. Standart ve büyük sentetik depolarla önce/sonra süreç ailesi kaynak ölçümü eklendi; gerçek kullanıcı hesabına ek Instagram isteği gönderilmedi. Bellek tamamen serbest bırakılmaz; Electron arayüzü ve Python hizmeti açık kalır.

## Windows 0.2.4 — 2026-10-28 (her profil için ayrı kontrol aralığı)

- Seçilen aralık her profilin kendi son kontrolüne uygulanır. 3 profil ve 5 dakika artık yaklaşık 5 dakikalık kişi başına kontrol hedefidir; aralık profil sayısıyla çarpılmaz. İstekler sırayla ve en az 15 saniye arayla gönderilir. Çok sayıda profil, uzun liste taraması ve sunucu molaları hedefi uzatabilir.
- Otomatik denemeler kimliğe göre şifreli saklanır: hata/iptal/yeniden başlatma hemen tekrar döngüsü oluşturmaz, diğer süresi dolan kişiler ilerler. Elle başarılı okuma o profilin süresini yeniler; uyku sonrası geçmiş kontroller tekrar oynatılmaz. Ek erişim/liste incelemeleri kendi aralığı ve profil sırasıyla birlikte planlanır.
- 84 offline backend, 9 Node ve 27 gerçek paketlenmiş EXE kontrolü geçti; üç profilli süre senaryosu sahte HTTP ile doğrulandı. Yeni çok hesaplı canlı Instagram hız ölçümü yapılmadı.
- Ayarlar açıklaması kişi başına süreyi gösterir. Önceki hesap, bildirim tercihleri ve kontrol aralığı korunur. Android ve örnek web önizlemesinin zamanlayıcısı bu masaüstü değişikliğinde değiştirilmedi.

## Windows 0.2.3 — 2026-10-28 (hesaba göre bildirim türleri ve tek kontrol kaydı)

- Kendi hesabı ve kayıtlı kişiler için ayrı bildirim tercihi: hesap genelinde aç/kapat, kullanıcı adı, görünen ad, biyografi, fotoğraf, takipçi/takip edilen/gönderi sayısı, bağlantı, gizlilik, doğrulanma, liste ve engellenme şüphesi türleri. Tercihler şifreli saklanır; genel Windows bildirim anahtarı ayrıca korunur.
- Aynı kontrolün değişiklikleri tek kayıt ve en fazla tek Windows bildirimi olarak gruplanır. Bildirime tıklama uygulamada o kontrolün bütün eski/yeni değerlerini açar. Bildirimi kapalı türler de kayıtta kalır; hangi değişikliklerin bildirim için seçildiği gösterilir.
- **Bildirimler** sayfası, okunmamış filtresi, zil göstergesi ve kalıcı okundu bilgisi eklendi. Son 500 grup saklanır; önceki zaman çizelgesi ayrıca korunur, eski olaylardan yapay bildirim üretilmez. Geçmiş seçim/kanıtlar yeni tercihlerle yeniden yazılmaz.
- Bildirim seçimi ve gruplaması backend'de tek sahipte yapılıp masaüstüne hazır kayıt olarak iletilir. Ana süreçte eski ham olay filtrelemesi kaldırıldı; okuma/tercih değişikliği veya açılış eski bildirimi tekrar göndermez. Aynı kontroldeki normal farklar, engellenme şüphesini gizlemez.
- 78 backend, 9 ana süreç/bildirim ve 27 gerçek EXE kontrolü geçti. Mevcut hesapla açılışta kimlik/kişiler/genel tercihler korundu, 12 türün arayüzü açıldı ve API istek sayısı artmadı. Gerçek Windows toast teslimi/tıklaması bu sürümde canlı doğrulanmadı; tıklama IPC yolu gerçek uygulamada test edildi.

## Windows 0.2.2 — 2026-10-27 (engellenme şüphesi ve erişim kanıtları)

- Kişi ayrıntısına **Erişimi kontrol et**, kendi takipçi listesine **Engellenme şüphesi / Erişim kontrolleri**, zaman çizelgesine erişim filtresi eklendi. Aynı oturumdaki kendi profil sağlığı ve hedefin kayıtlı hesap kimliği okunur; kullanıcı adı araması/başka hesap/anonim tarama kullanılmaz.
- İki tamamlanmış kendi takipçi kaydında eksilen kayıtlı kişi için erişilemeyen profil (HTTP 404) gözlenirse **Engellenme şüphesi** kaydı üretilir. Bu sonuç hesap kapanması/silinmesinden veya sizin uyguladığınız engelden kesin ayrıştırılamaz; kesin “engelledi” iddiası yoktur. Kullanıcı adı değişip aynı kimlik erişilebiliyorsa profil erişilebilir olarak gösterilir.
- Ayarlar'da **Kaybolan kayıtlı takipçileri incele** isteğe bağlıdır. Otomatik kontrol de açıksa sayaç düşüşü sonrası mevcut tam başlangıç kaydı üzerinden takipçi taraması, ardından eksilen kayıtlı kişilere erişim incelemesi sıraya girer. Başlangıç kaydı yoksa kullanıcıya gösterilir; eksik liste/başarısız kontrol otomatik tekrar döngüsüne girmez. 429/oturum/doğrulama kuralları korunur.
- Şüphe olayının önceki/sonraki liste ve HTTP/zaman kanıtları şifreli depoda korunur; geçmiş olaylar sonraki incelemelerle yeniden yorumlanmaz. Aynı kaybolma için tekrar uyarı üretilmez; listede yeniden görülüp tekrar kaybolursa yeni olay başlar. Windows bildirimleri açıksa şüphe başlığı öne çıkar, tıklama kanıt panelini açar.
- 62 backend, 6 ana süreç/bildirim ve 25 gerçek paketlenmiş Windows kontrolü geçti. Canlı erişilebilir hedef ve kendi profil HTTP 200, toplam 15,66 saniye doğrulandı. Gerçek engellenmiş hesap/Windows bildirim teslimi bu sürümde canlı doğrulanmadı; hesap ilişkisi değiştirilmedi.

## Windows 0.2.1 — 2026-10-27 (kısa kontrol aralığı ve sistem tepsisi)

- 1 ve 5 dakikalık kontrol seçenekleri eklendi; önceki tercihler, şifreli hesap ve geçmiş korundu. Her aralıkta tek profil kontrol edilir. Ayarlar, profil sayısını ve yaklaşık tam tur süresini açıklar.
- X ile pencereyi kapatma sistem tepsisine gizler. Tepsi menüsünde pencereyi açma, otomatik kontrolü değiştirme ve Tamamen çık vardır. Programdan çıkış sürmekte olan işlemi iptal eder ve yerel worker'ı kapatır.
- Arka plan sırası ana süreçte 5 saniyede bir, backend'in hesapladığı uygun zamana göre değerlendirilir. Görünmez pencere çalışmayı durdurmaz. Kaçırılan kontroller topluca gönderilmez; 15 saniyelik istek aralığı ve Instagram molası korunur.
- 39 backend, 4 zamanlayıcı/tepsi ve 23 gerçek EXE kontrolü geçti. Canlı tek profil okuması 1,28 saniye; pencere gizliyken iki otomatik kontrol HTTP 200, istek başlangıç aralığı 60,11 saniye ölçüldü. Bunlar bu bağlantıdaki kısa ölçümlerdir; kalıcı hız veya uzun süreli bildirim teslimi garantisi değildir.

## Windows 0.2.0 — 2026-10-27 (gerçek işlevler ve şifreli yerel kayıt)

- Windows arayüzü gerçek hesap bağlantısına bağlandı: parola/kod alanı, aynı doğrulama bağlamından devam, DPAPI oturumu ve eski Windows prototipindeki oturumu doğrulayarak kopyalama. Eski prototip dosyası değiştirilmez; açılış yalnız yerel gözlemleri gösterir.
- Kendi/seçilen profil gözlemleri, sayısal/metin/fotoğraf kimliği farkları, kişi araması, favori, izleme, not/grup, grafik ve zaman çizelgesi eklendi. Fotoğraflar çerezsiz doğrudan Meta CDN bağlantısından küçük yerel görsel olarak alınabilir.
- Takipçi/takip edilen için ayrı, iptal edilebilir sayfalı tarama; tam/kısmi kapsam; korunmuş eski kayıtlar; iki tamamlanmış gözlem arasında kimlik karşılaştırması; iki yön arasında ilişki filtresi ve seçilen kaydın CSV aktarımı eklendi. Eksik sayfa ve belirsiz kapsamdan kesin takipten çıkış üretilmez.
- Şifreli, oturumsuz gözlem yedeği/geri yükleme; yerel bağlantı/veri kaldırma; istek ve mola bilgileri; uygulama açıkken isteğe bağlı sırayla profil kontrolü ve Windows bildirimi eklendi. Başlat kısayolu yalnız bildirim tercihi açıldığında hazırlanır.
- Bağlantı yerel worker'a özel borularla gider; renderer'da ağ/Node erişimi kapalı, IPC ana çerçeveye ve sabit işlemlere sınırlı. TLS/yönlendirme/tekrar/mola kuralları taşıma sınırında uygulanır. Python içeren taşınabilir paket ayrı `InstagramTracker-Desktop-0.2.0` klasöründedir. Android ve örnek web sürümü korunur.
- 36 çevrimdışı backend testi ve 21 paketlenmiş Windows kontrolü geçti. Canlı kayıtlı oturum, kendi profil ve iki liste yönü HTTP 200 ile doğrulandı; bu hesapta listeler birer sayfaydı. Yeni parola/2FA, çok sayfalı canlı liste, Windows bildirim teslimi ve uzun süreli çalışma ayrıca canlı doğrulanmadı.

## 0.1.30 — 2026-10-27 (web, masaüstü ve telefon için ortak görünüm)

- Yerel web ve Windows masaüstü arayüz önizlemesi eklendi: Özet, Kişiler, Listeler, Zaman çizelgesi, Ayarlar ve ayrıntı pencereleri. Arama, filtreler, favoriler ve notlar yalnız örnek verilerle çalışır; gerçek hesap girişi/veri okuma sonraki aşamadır.
- Telefonun ana ekranı düz mor vurgular, açık renkli 24 dp kartlar, sayaç başlıkları ve favori/gezinme yüzeyleriyle aynı görsel dile uyarlandı. Ortak `design/theme.json`, Android ve web tema dosyalarını üretir; eşleşme denetlenir. Mevcut Android işlevleri, veri biçimleri, oturum ve istek politikası korunur.
- Masaüstü aynı web dosyalarını yerel, Node erişimi kapalı ve sandbox açık Electron penceresinde kullanır. Hiçbir kullanıcı oturumu/verisi yeni önizlemeye aktarılmaz; canlı yenileme ve tarama taklit edilmez. Kullanım: [interface/README.md](interface/README.md).

## 0.1.29 — 2026-10-27 (telefon girişinde anahtar bağlamı)

- P05 parola şifreleme anahtarı sorgusu, uyarlanan instagrapi 3.2.0'ın ayrı anonim istemcisiyle aynı oturum ayrımına alındı. Özel giriş çerezleri/cihaz başlıkları bu sorguya gitmez; anahtar yanıtı P06'nın çerez/MID/claim bağlamını değiştiremez.
- Mevcut mola, istek aralığı, tek iletim, TLS denetimi, 405 anahtar istisnası ve 429 sonrası durma korunur. Bu kaynak uyumsuzluğu düzeltmesinin canlı P06/429 sorununu çözdüğü henüz doğrulanmadı; yeni otomatik deneme eklenmedi.

## 0.1.28 — 2026-10-27 (görünür web doğrulama sonucu)

- Başarısız web doğrulaması giriş sayfasını kapatır; hata nedeni, bu denemenin varsa HTTP durumu ve geçerli mola açık bir sonuç penceresinde gösterilir. İstek bilgileri doğrudan açılabilir; yeniden deneme otomatik başlatılmaz.
- Süresi dolmuş eski mola yeni doğrulama/ağ hatasını örtmez. İstek protokolü, oturum şifrelemesi ve mola süreleri değişmez. 0.1.27'nin fiziksel telefon denemesinde HTTP 429 gözlendi; bu arayüz düzeltmesi sunucu sınırlamasını çözdüğü iddiası taşımaz.

## 0.1.27 — 2026-10-27 (cihazda web bağlantısı)

- Önerilen Instagram ile bağlan yolu, Instagram'ın kendi web girişini kullanır. Uygulama web parola alanını okumaz; çerez ve claim cihazda Keystore/AES-GCM ile ayrı iki bankada saklanır. Hesap doğrulanmadan kaydedilmez; başarısız/iptal bağlantı önceki hesabı korur.
- Web oturumları için doğrudan Instagram profil/takipçi/takip edilen okuyucusu eklendi. Özel backend, FollowMeter servisi, analitik veya yeni bağımlılık/izin yoktur. Web ve mobil bağlantılar ayrı kalır; hatada protokol/uç değiştirilmez. Web uygulama kimliği açık Instagram sayfasındaki güncel sabitle eşlendi.
- Staging/şifreli banka ve bounded HTTP yanıt/iptal/yenileme sahipliği iki protokol arasında ortaklaştırıldı. Liste, otomasyon ve ekran yeniden oluşumu web bağlantı kapsamını tanır. 429/doğrulama ve mevcut istek aralıkları korunur.
- Hesap v5, eski v1–v4 kayıtlarını okur; eski APK yeni hesap kaydını okuyamaz. 0.1.26 APK ve kaynak yedeği korunur. Ayrıntı ve canlı doğrulama sınırı: [WEB-CONNECTION.md](docs/WEB-CONNECTION.md), [VALIDATION.md](docs/VALIDATION.md).

## 0.1.26 — 2026-10-26 (kalıcı telefon giriş tanısı)

- Son telefon giriş isteğinin sabit P kodu, başlangıç saati ve varsa HTTP durumu artık cihazda saklanır; ⋯ → İstek bilgileri içinde yeniden açılışta görülebilir. Tek kayıt tutulur. Ham istek/yanıt, URL, hesap bilgisi, parola veya kod saklanmaz. Bağlantıyı kaldırmak tanıyı da temizler; 429 molasını kaldırmaz.
- Yerelde reddedilen boş giriş ve mola nedeniyle gönderilmeyen deneme önceki tanıyı değiştirmez. Yeni bir istek başlamış ama yanıt gelmemişse eski HTTP durumu ona taşınmaz. Eski sürümde yalnız bellekte kalan P kodu geriye dönük bulunamaz.
- Giriş protokolü veya istek sıklığı değiştirilmedi; 429’un nedeni henüz doğrulanmadı ve canlı giriş denenmedi. Sonraki kullanıcı denemesindeki tanı, hangi adımın araştırılacağını belirleyecek.

## 0.1.25 — 2026-10-26 (gözlem kapsamı ve anlaşılır durum)

- Karşılama, ana ekran ve tanıtım metni kayıtlı profil/liste gözlemlerini karşılaştırmayı vaat eder; kesin takipten çıkış/olay saati iddiası yoktur.
- Durum kartları her profilde Son kontrol, Veri kapsamı, Şu anki durum satırlarına ayrıldı. Otomasyon kapalı olsa da kayıtlı liste kapsamı görülür; liste devamı/güncelleme sırası ayrıntıda kalır. Kayıtlı 429 molası otomasyon kapalı etiketinin önüne alınır.
- Hızlı izleme kartında 60 saniyenin en erken hedef olduğu, gecikme, ortak istek sırası ve ekran görünmezken duraklama belirtilir. Zamanlayıcı veya istek bütçesi hızlandırılmadı.
- Grafik filtrelerinde gerçek kayıt sayısı, ilk/son tarih ve gün/saat/dakika aralığı; 90 gözlemin 90 gün olmadığı ve çizginin aradaki bütün olayları göstermediği açıklaması.
- Kişi kapasitesi 100’e çıkarıldı; en çok 90 gözlem/kişi. Profil/kişi dosyası 2 MiB, liste dosyası aynı 512 KiB bütçesinde kalır. Kapasite aşımında eski dosya korunur. Hesap biçimi aynı; metaveri referansları tekrar tekrar aramak yerine bir kez indekslenir. Kişiler 20’şer kartla açılır, favoriler öndedir; genişletme ekran yeniden oluşunca korunur.
- Ortak altı istek/90 saniye tur bütçesi, 15 saniye istek aralığı, bütün 429/doğrulama molaları ve telefon giriş akışı korunur. Daha fazla hedef kontrollerin daha geç sıraya gelmesi anlamına gelebilir.

## 0.1.24 — 2026-10-26 (giriş anahtarı yanıtı)

- Kullanıcı 0.1.23 telefon girişinde yaklaşık bir dakika sonra HTTP 405 ve genel giriş adımı uyarısı bildirdi. Şifreleme anahtarı sorgusundaki 405/HTML + geçerli anahtar başlıkları aynı hatayı emülatörde yeniden üretti. Canlı yanıt/başlıklar alınmadığı için kullanıcının yanıtının kesin içeriği bilinmiyor.
- Mevcut instagrapi kaynak sözleşmesine uygun olarak yalnız parola anahtarı sorgusunda geçerli anahtar başlıkları bulunan 2xx/405 yanıtı kullanılabilir. Anahtar olmadan, 429/doğrulama yanıtında veya gerçek parola girişindeki 405’te devam edilmez. 405 sonrası farklı uç/yöntem denemesi ya da otomatik yeniden giriş eklenmedi.
- Cihaz kaydı GraphQL hedefi mevcut kaynaktaki b.i.instagram.com adresiyle eşlendi. Hata ekranına yalnız son giriş adımının sabit tanı kodu (P01–P09) ve HTTP durumu eklendi; parola/kod/ham yanıt veya hesap bilgisi bu tanıya dahil edilmez ve tanı süreç belleğindedir.
- Önceki oturum/kayıtlar ve 429 molası korunur; yeni izin veya bağımlılık yoktur. Güncel canlı Instagram kabulü kullanıcı testiyle doğrulanacaktır.

## 0.1.23 — 2026-10-26 (bilgisayarsız telefon girişi önizlemesi)

- Hesap bağla menüsünde ilk seçenek Telefondan bağlan: yerel kullanıcı adı/e-posta, parola ve gerektiğinde kod adımı. Bilgisayar/USB/oturum dosyası gerektirmez. Önceki çalışan bilgisayar aktarımı ve web seçeneği korunur.
- Windows prototipinde kullanılan instagrapi 3.2.0 CAA sözleşmesinin Android uyarlaması; şifreli parola hazırlama, kalıcı cihaz/imza kimliği, SMS/doğrulama uygulaması/yedek kod akışları. Instagram’ın bu uyarlamayı canlı kabulü henüz doğrulanmadı; resmî API/OAuth desteği iddiası yoktur.
- Bağlantı yalnız kimliği doğrulanmış oturumdan başarılı profil okumasıyla kaydedilir. Aynı hesabın kişiler/geçmişi korunur; başarısız/iptal giriş mevcut hesabı değiştirmez. Parola/kod saklanmaz; oturum ve cihaz kimliği Keystore ile şifrelenir. Giriş ekranı ekran görüntüsüne kapalıdır.
- Ekran döndürmede devam eden giriş bellekte korunur. İstek sırasında arka plana geçmek iptal eder; kod beklerken diğer uygulamaya gidilebilir. Ortak istek aralığı, 429/sunucu beklemesi ve yeni giriş molası korunur; giriş sırasında otomasyon bekler. Otomatik yeniden giriş yoktur.
- Türkçe/English yardım, gizlilik ve bağlantı yönlendirmeleri güncellendi. Yeni Android izni, servis veya bağımlılık eklenmedi. 0.1.22 kaynak/APK yedeği korunur. [Test adımları](docs/PHONE-LOGIN-TEST.md).

## 0.1.22 — 2026-10-26 (tek bakışta kontrol durumu)

- Kendi hesap özetinde, kişi kartlarında ve kişi analizinde dokunulabilir kontrol durumu: son başarılı profil okuması, kısa bekleme nedeni ve varsa liste ilerlemesi. İlk dakika Az önce, ilk saat dakika cinsinden; daha eski veya ileri cihaz saatli kayıtlar tam tarih/saatle gösterilir.
- Hızlı kontrol, planlanan/sıradaki kontrol, son karşılaştırmada fark olmaması, hata, bildirim izni, bağlantı, çevrimdışı ve Instagram molası ayrı gösterilir. Bir başka profilin hızlı oturumu sürerken normal otomatik kontrollerin beklediği de belirtilir. Kalıcı yarım liste, aktif ağ okuması diye sunulmaz.
- Dokununca başarılı profil saati, en erken yerel hedef, iki listenin ayrı kapsamı/alınan hesap sayısı/son sayfa zamanı ve varsa son hata açılır. Zaman çizelgesi ve Listeleri incele düğmeleri mevcut ekranları açar. Tamamlanmış listeler yeni sayısal değişim veya günlük süre yüzünden tekrar uygunsa Liste güncellemesi sırada görünür.
- Özet ekran görünürken yaklaşık 30 saniyede mevcut şifreli kayıtlardan güncellenir; yeni profil/liste isteği, bildirim, iş rezervasyonu veya kalıcı veri üretmez. Ekran yeniden kurulmadan yalnız metinler güncellenir; arka planda bu arayüz döngüsü durur. Mevcut otomasyon, istek molası ve bildirim/fark hesapları değişmedi. Türkçe/English desteklenir.

## 0.1.21 — 2026-10-26 (hızlı izleme ve işlem geçmişi)

- Kendi hesabında veya kayıtlı kişide Hızlı izle: uygulama görünürken, tek hedef için 15 dakikalık oturum boyunca 60 saniyelik profil tazeliği hedefi. Süre ekran döndürme/arka plana geçişle uzamaz. Ekran görünmezken hızlı oturum duraklar; normal Android işi devam edebilir. Hızlı izleme sona erince seçilen hedefin 15 dakikalık profil/liste otomasyonu açık kalır.
- Otomasyon ayarlamak seçilen kişileri 15 dakikaya alır; Hızlı izle yalnız seçilen kişiyi 15 dakikaya alır. Diğer mevcut kişi aralıkları güncelleme sırasında korunur. Normal arka plan işi en erken 15 dakikalık Android hedefiyle, aynı turda en fazla altı profil/liste isteği ve 90 saniyelik başlatma penceresi kullanır; istekler en az 15 saniye aralıklıdır. Android, büyük listeler ve ağ geciktirebilir; kesin süre veya Instagram güvenli kotası değildir.
- Profil sayısı değişirse ilgili liste günlük hedefi beklemeden sıraya girer. Sayısı değişmeyen tamamlanmış liste günlük yeniden taramaya kadar korunur; aynı sayıda farklı kişiler olabileceği için günlük tarama kaldırılmadı. Yarım okuma kaldığı yerden sürer. Eski listelerde karşılaştırma dayanağı yoksa bir başlangıç taraması gerekebilir.
- Üç nokta menüsünden İşlem geçmişi ve profil ekranından Zaman çizelgesi: hesap filtresi, kontrol sonucu, profil/liste farkı, sayfa/kapsam bilgisi, işlem başlangıcı, verinin alındığı ve karşılaştırmanın kaydedildiği saat. İki gözlem arasındaki fark gösterilir; Instagram'daki kesin olay saati uydurulmaz. Eksik alanlar için önceki bilinen karşılaştırma tarihi korunur.
- Son 120 işlem ayrı şifreli kayıtta tutulur; liste farkının ilk 10 yeni ve ilk 10 görünmeyen hesabı ayrıntılarda bulunur. Hata türleri kaydedilir; parola, oturum ve ham yanıt yazılmaz. Geçmiş yazımı başarısız olursa başarılı profil/liste kaydı korunur ve geçmiş ekranında uyarı gösterilir. Kişi/bağlantı kaldırma ilgili geçmişi de temizler.
- 429, doğrulama, manuel mola, tek etkin taşıma ve bildirim izinleri korunur. Hızlı izleme ilk hatada/belirsiz liste sonucunda durur; kendiliğinden yeniden giriş veya mola aşma yoktur. Türkçe/English ve mevcut geri dönüş/fotoğraf temizliği korunur. Yeni servis, izin veya bağımlılık eklenmedi.

## 0.1.20 — 2026-10-26 (Türkçe ve İngilizce)

- Ana ekrandaki üç nokta menüsüne Dil / Language eklendi. Türkçe veya English seçimi yeniden açılışta korunur; Android 13 ve üzerinde sistemin uygulama dili ayarıyla eşleşir. Eski sürümlerde tercih ayrı bir yerel ayarda saklanır.
- Ekranlar, erişilebilirlik etiketleri, açıklamalar, hata mesajları ve yeni bildirimler iki dilde sunulur. Tarih ve sayı biçimi seçilen dile uyar. Biyografi, kullanıcı adı, not ve grup gibi kayıtlı içerik çevrilmez.
- Dil için ekran yeniden oluşturulurken açılış yenilemesi başlatılmaz; kayıtlar, favoriler, izleme aralıkları ve istek molaları korunur. Dil paketleri APK içinde bulunur; ek indirme gerekmez.

## 0.1.19 — 2026-10-26 (favori hizalaması)

- Ana ekrandaki favori kartlarında kullanıcı adı, profil görselinin altında ortalandı.

## 0.1.18 — 2026-10-26 (daha sade ve hızlı kullanım)

- Son incelenen en fazla 20 profil için 15 dakikalık, oturuma bağlı bellek önbelleği. Geri dönüp yeniden açma yeni istek gerektirmez; okuma zamanı yenilenmez. Kayıtlı kişi/own profilinin daha yeni gözlemi tercih edilir. Kişiyi veya bağlantıyı kaldırmak ilgili önbelleği de temizler. Uygulama süreci kapanınca bu geçici önbellek kaybolur.
- Listelerde tek Listeyi güncelle işlemi: aktif yönde en fazla altı sayfa, istek başlangıçları en az 15 saniye aralıklı ve 90 saniyelik yeni istek bütçesi. Her sayfa hemen kaydedilip gösterilir; arama/konum korunur. Hata, kapsam sınırlaması, iptal veya oturum/kayıt değişimi devamı durdurur. Tek sayfa/baştan okuma ve karşılaştırma Liste işlemleri menüsünde kalır.
- Normal kısa istek aralığında profil/liste yenilemeyi iptal edilebilir kuyruğa alma; saniyelik açıklama yerinde güncellenir. Ekrandan ayrılınca kuyruk iptal olur. 429, doğrulama, bağlantı veya diğer genel duraklamalar otomatik devam ettirilmez; uzayan kısa bekleme de 30 saniyede sonlandırılır.
- Kayıtlı ve geçici profilde ortak sayı/biyografi/son kayıt görünümü. Değişiklikleri izle, açık eylemle kişiyi kaydedip bildirim izni varsa profil izlemesini açar; yalnız görüntülemek izleme başlatmaz. Yalnız kişilere ekle ayrı seçenek olarak korunur.
- Ana ekranda son değişiklikler ve favorilere hızlı geçiş öne taşındı; otomasyon yönetimi kısa durum kartı ve menüye alındı. Fotoğraf temizliğinin arka planda yapılması ve çalışan bildirim/oturum/istek sınırlaması kuralları korunur.

## 0.1.17 — 2026-10-26 (uygulama içi profil ve geri dönüş düzeltmesi)

- Liste üyesine dokunmak uygulama içindeki profil ekranını açar. Liste kaydındaki kimlik/fotoğraf/ad gösterilir; taze kayıt varsa tekrar sorgulanmaz. İlk profil okuması mevcut istek/mola kuralları izin verirse yapılır; izin vermiyorsa kullanıcı Profili getir ile devam edebilir. Geri dönüş ve ekran döndürme otomatik tekrar başlatmaz. Instagram’da aç ayrı düğmedir.
- Kayıtlı olmayan kişi otomatik eklenmez veya izlemeye alınmaz. Kişilere ekle açık eylemdir; kayıtlı kişinin listeleri buradan açılabilir. Geçici profil ayrıntıları ekran ömründe tutulur, eski kişi/hesap kayıt biçimi değiştirilmez.
- Gerçek Android 13 telefon kaydında takipçi listesinden çıkarken fotoğraf istemcisinin TLS bağlantı havuzunu ana iş parçacığında kapatmasının NetworkOnMainThreadException ürettiği doğrulandı. Görsel iptali/istemci temizliği ortak arka plan sahibine taşındı; ekran kapanışı önce teslimi durdurur. Düzeltme aynı görsel sahibini kullanan bütün ekranları kapsar.

## 0.1.16 — 2026-10-25 (kişi silme, profil bağlantıları ve fotoğraflar)

- Kişi kartlarında erişilebilir silme düğmesi; mevcut analiz ekranıyla aynı onaylı kaldırma akışı. Yalnız seçilen kişinin profil geçmişi, favori/not/izleme ayarı, otomasyon seçimi, bildirimleri ve liste kayıtları kaldırılır; Instagram hesabında işlem yapılmaz.
- Takipçi/takip edilen listesinde üyeye dokunarak yanıttaki kullanıcı adıyla Instagram profilini uygulama veya tarayıcıda açma. Aranmış sonuçta doğru üye açılır; bu işlem uygulamanın kişi listesine ekleme veya profil sorgusu yapmaz. Kullanıcı adı bilinmiyorsa bağlantı uydurulmaz.
- Kendi hesap özeti, kişi kartı/analizi ve liste üyelerinde yanıttaki fotoğraf bağlantısından küçük görsel. Görünür alanlarda iki eşzamanlı yükleme, sınırlı kuyruk, Activity/geri dönüş/yeniden kullanılan satır için iptal. Profil/liste okuyucusundan ayrı ve oturum/çerez göndermeyen HTTPS CDN taşıması; bağlantı veya görsel hatasında harfli simge kalır.
- Özel cihaz önbelleğinde en çok 8 MiB/128 küçük görsel ve ekran başına 2 MiB bellek önbelleği. Fotoğraf kimliği/adresi değişirse ayrı kayıt; kişi kaldırmada ilgili önbellek, bağlantı kaldırmada bütün görseller temizlenir. Görsel önbelleği ayrıca şifreli değildir ve işletim sistemi tarafından temizlenebilir. Hesap/liste kayıt biçimi, kontrol aralıkları ve profil değişikliği bildirimi hesabı değişmedi.
- Değişikliklerden önce çalışan 0.1.15 GitHub'a push/etiket ve ayrı kaynak/APK yedeğiyle korundu. Yeni fotoğraf akışının canlı CDN/telefon davranışı kullanıcı testine açıktır.

## 0.1.15 — 2026-10-25 (ardışık liste sayfaları ve diyaloglar)

- Aynı hedefin takipçi/takip edilen sayfaları bir otomatik turda dönüşümlü ilerler: en fazla 6 sayfa, istek başlangıçları arasında mevcut en az 15 saniye. İlk 90 saniye yeni istek başlatma bütçesidir; tur başlangıçlarının ortak 15 dakika aralığı korunur. Her sayfa kalıcıdır; kalan cursor sonraki turda sürer.
- İlk hata, sınırlı/belirsiz/takılmış sayfalama turu durdurur. İptal, seçim/oturum değişimi, manuel yeniden okuma ve kayıt temizleme sonraki sayfanın eski kaydı ezmesine izin vermez. Liste sonu sonrası 24 saat hedefi ve mevcut 429 molaları korunur.
- Bütün native diyaloglarda yumuşak mor yüzey, yuvarlatılmış köşeler, temaya uygun yazı/seçim/düğmeler. Kısa Başlat düğmesi; otomasyon durumunda yerel Yenile; liste temizleme işlemi açıklama ve ayrı onayın arkasına taşındı. Yeni izin, bağımlılık veya kayıt biçimi yok.
- Önceki 0.1.14 kaynak ve imzalı APK ayrı yedek/etikette korundu. Canlı Instagram çok sayfalı okuma ve uzun süreli Android pil/Doze davranışı kullanıcı testine açık.

## 0.1.14 — 2026-10-25 (kendi hesabı ve seçili kişilerde liste otomasyonu)

- Özet ekranından bir kez seçilen hedeflerde takipçi/takip edilen sayfalarını arka planda sırayla alma. Kaldığı cursor, rezervasyon ve hata durumu kalıcıdır; her sayfa için elle düğme gerekmez. Eski sürümlerde otomasyon varsayılan kapalıdır.
- Profil ve liste işleri ortak tek 15 dakikalık otomatik okuma bütçesini paylaşır; ikisi de uygunsa dönüşümlü çalışarak açlık önlenir. Kendi hesap profil kontrolü de seçilirse saatlik hedefle arka plana katılır. Seçili kişilerin mevcut profil bildirimleri açılır; favori, not, geçmiş ve kişi aralıkları korunur.
- Liste kasası son tek hedeften en fazla 21 hedeflik v2 arşive taşındı. Eski v1 kayıt ilk yazımda göç eder. Yön başına son uygun baseline ve güncel/yarım okuma tutulur; kapasite sınırı 512 KiB olarak kalır. Kapasite aşımında önceki kayıt korunur ve başarısız yönde otomasyon duraklatılır. Rezervasyon/hata kaydı bile sığmıyorsa ayrı kapasite molası alınır; bu molayı temizlemek 429 molasını kaldıramaz.
- İki ayrı okumada da liste sonu gözlenmişse kimlik tabanlı yeni görülen/görünmeyen hesap farkları ve özel Android bildirimi. İlk/yarım/sınırlı okuma olay değildir. Kesin takipten çıkış veya tam/atomik Instagram listesi garantisi verilmez. Bildirime dokunmak sahipliği kontrol edilen hedef listesini açar.
- Otomasyon durumu, kayıtlı listeden fark görüntüleme, ayrı hedef önbellekleri ve onaylı liste kaydı temizleme. Liste otomasyonunu durdurmak bağımsız kişi profil izlemelerini kapatmaz. Manuel liste okuması korunur; açılış ve yerel gezinme kendiliğinden istek göndermez.
- Ağ uçları, mobil oturum taşıması, sayfa boyutu, 429/oturum duraklatması ve Android görev sıklığı korunur; yeni servis, izin veya sunucu yoktur. Büyük listeler ve Android kısıtları tamamlanmayı geciktirir. Canlı uzun süreli Instagram davranışı ayrıca doğrulanmalıdır.

## 0.1.13 — 2026-10-25 (kontrol aralıkları ve takip durumu)

- Kişi kontrollerinde 15/30 dakika ve 1/6/12/24 saat seçenekleri. Ortak otomatik bütçe en fazla 15 dakikada bir okuma ve her işte tek kişi olarak kalır; bu seçenekler her kişi için kesin süre taahhüdü değildir. Mevcut 1/6/24 saat tercihleri aynı sürede korunur.
- Kişi analizinde Takip durumu: son başarılı okuma, en erken kontrol hedefi, bekleme nedeni ve o anda zamanı gelmiş kişiler arasındaki sıra. Durumu güncelle yeni Instagram isteği göndermez.
- Aralıkların kaynak modeli dakika cinsindedir. Mevcut şifreli v4 kaydına isteğe bağlı intervalMinutes eklendi; eski intervalHours yalnız sürüm uyumluluğu için üretilir/okunur. Eski APK'ya dönüşte 15/30 dakika 1 saate, 12 saat 24 saate yuvarlanır ve eski sürüm yeniden yazarsa dakika tercihi kaybolur.
- Kullanıcı adı, biyografi ve karşılaştırılabilir fotoğraf bilgisi değişiklikleri 0.1.12'deki ortak bildirim yolunu kullanmaya devam eder. Sayılar aynıyken her alanın tek başına bildirimi ve gerçek Android arka plan işinin biyografi/fotoğraf bildirimi için test kapsamı genişletildi.
- Yeni ağ ucu, izin, servis, sunucu veya 1–2 dakikalık hızlı takip eklenmedi. 429/oturum duraklatmaları, 15 dakika önbellek, kişi başına son 90 kayıt ve favoriler korunur.

## 0.1.12 — 2026-10-25 (profil bilgisi değişiklikleri)

- Kayıtlı kişiler için kullanıcı adı, biyografi ve karşılaştırılabilir fotoğraf bilgisi değişiklikleri ortak ProfileChanges yorumundan üretilir. Sayılar değişmese de Son değişiklikler ve açık izleme bildirimleri olayı gösterir.
- Kişi analizinde Profil bilgileri ve Profil geçmişi. Kullanıcı adı/biyografi için önceki-yeni değer ve iki gözlem zamanı; fotoğraf için kimlik/varsayılan fotoğraf durumu kanıtı açıklanır.
- Eksik, null, açık boş metin ve alınamayan isteğe bağlı alan ayrılır. İlk bilinen alan başlangıçtır; bilinmeyen aralıktan sonra fark varsa önceki bilinen gözlem zamanı korunur. URL farkı fotoğraf olayı değildir; görsel indirme yoktur.
- Mevcut v4 şifreli hesap kaydına isteğe bağlı metadata/text havuzları eklendi. Yinelenen metinler bir kez yazılır. Eski oturum, geçmiş, favori ve izleme korunur; 512 KiB bütçesi aynı kalır, kapasite aşımında eski kayıt değiştirilmez.
- Mevcut profil GET'i, önbellek aralığı, istek kapısı, sayfalama ve oturum taşıması korundu. Profil alanları yalnız zaten yapılan başarılı okumada gelirse kullanılır. Canlı alan kapsamı ayrıca test edilmelidir.

## 0.1.11 — 2026-10-25 (son değişiklikler ve favoriler)

- Ana sayfada Son değişiklikler: mevcut kişi geçmişindeki takipçi/takip edilen net farkları ve mevcut liste kaydındaki sayfa okumaları gözlem zamanına göre gösterilir. İlk üç kayıt kartta, en fazla 20 kayıt tam akışta görünür. Kayda dokunmak ilgili analizi veya listeyi açar; istek başlatmaz.
- Kişi listesi ve analizde yıldızla favoriye ekleme/çıkarma. Favoriler listede önce gelir; kendi içlerinde mevcut kişi sırası korunur. Favori durumu şifreli kişi kaydına bağlıdır ve yenilemelerde korunur.
- Favori alanı mevcut v4 kayıt sözleşmesine isteğe bağlı Boolean olarak eklendi. Eski kayıtlarda varsayılan false; oturum, geçmiş, not ve izleme ayarları korunur. Favori değiştirmek bildirim işini yeniden planlamaz.
- Okuyucular, ağ istekleri ve mola kuralları değişmedi. Akış ayrı bir olay günlüğü tutmaz; saklanan gözlemlerden türetilir. Liste etkinliği son hedefin mevcut kaydıyla sınırlıdır.

## 0.1.10 — 2026-10-25 (arayüz ve durum açıklamaları)

- Alt gezinme içerik akışından ayrıldı; büyük yazıda kendi yüksekliğini alır ve sayfa içeriğini örtmez. Grup etiketleri metne göre boyutlanır; geçmiş, bildirim ve notlar ayrı kartlarda gösterilir.
- Kayıt zamanı öne alındı. Listeye Kaydın kapsamı açıklaması, arama eşleşme sayısı ve aramayı temizleme eklendi. Aramada sonuç bulunamaması ve alınmış boş sayfa ayrı anlatılır.
- Yerel bağlantı durumundan çevrimdışı kayıt açıklaması; sunucuya bağlantı sınaması veya yeni ağ isteği eklenmedi. Yükleme ve mola bilgileri belirginleştirildi.
- Klavye açıkken üst gezinme ve alt işlemler gizlenerek arama sonuçlarına alan açılır; klavye kapatılınca geri gelir. Başlık erişilebilirlik işaretleri ve klavye odağı çerçevesi eklendi.
- Oturum, veri okuyucuları, kayıt sözleşmeleri ve istek kapısı korundu. Çalışan sürümler ayrı etiket/APK/kaynak yedeğindedir. Kontrol kapsamı `docs/VALIDATION.md` içindedir.

## 0.1.9 — 2026-10-25 (arayüz önizlemesi)

- Sıcak açık zemin, beyaz yüzeyler, mor/mercan/yeşil vurgular ve ortak görsel bileşenler. Ana sayfa, kişi kartları, profil analizi, listeler ve bağlantı ekranları yenilendi.
- Sayı kartından ilgili listeye doğrudan erişim; belirgin alt gezinme, isim baş harfleriyle profil satırları ve daha sade işlem alanları.
- Yalnız alınmış hesaplarda yerel arama. Arama yeni istek göndermez; yazı ve filtre ekran yeniden oluşturulduğunda korunur. Klavye açıkken sonuçlara alan bırakılır.
- Bağlantıda üç aşamalı ilerleme; kısıtlama ekranında gün ve saat içeren açık Yerel mola metni.
- Okuyucular, sayfalama sözleşmesi, oturum kasaları ve istek kapısı değiştirilmedi. Çalışan 0.1.8 kaynak/APK yedeği korundu. Yeni sürümün cihaz ve görsel doğrulama kapsamı `docs/VALIDATION.md` içindedir.

## 0.1.8 — 2026-10-24 (elle liste okuma önizlemesi)

- Seçilen hedefin takipçi/takip edilen listesini tek açık istekle sayfa sayfa okuma; gizli sayfalama, web/GraphQL yedeği veya otomatik giriş yok.
- Devam ve sınırlama alanlarında eksik/null/değer ayrımı; sabit kimlikle tekilleştirme; döngü/boş devam/çelişkili yanıt ayrımı. Liste sonu eksiksizlik garantisi sayılmaz.
- Son hedefin iki yönlü kayıtlarını Keystore ile şifreli saklama; yeniden açılışta istek göndermeden gösterme; alınan iki listede gözlenen ortak ve tek yönde bulunan hesapları karşılaştırma.
- Profil ve liste okuyucuları aynı tek gönderim, 429/yanıt politikası, oturum güncelleme ve iptal sahibini kullanır. Liste başarısı otomasyonu yeniden açmaz.
- Debug örnek liste akışı ve üretim sınırında testler. 25 Ekim'de kullanıcı fiziksel telefonda kendi sayaçları, kendi takip edilen listesi ve arkadaşının ilk takipçi sayfası için canlı başarı bildirdi; çok sayfalı devam henüz canlı sınanmadı. Yeni Android mobil parola/CAA girişi eklenmedi. Canlı test adımları `docs/FOLLOW-LISTS-TEST.md` içinde.

## 0.1.7 — 2026-10-24 (sade arayüz ve istek denetimi)

- Ana ekranda hesap, iki sayaç, son kayıt ve sabit tek yenile düğmesi; alt gezinme. Bağlantı yönetimi, gizlilik, teknik ayrıntı ve deney araçları üç nokta menüsüne taşındı. Kişi analizinde uzun açıklamalar/kayıt listesi isteğe bağlı ayrıntı oldu; bildirim tek anahtarla yönetiliyor.
- Bağlantı aktarımı aynı anda tek adım gösteriyor; hazırlık ve bekleyen aktarım yeniden açılışta bulunuyor. Başka hesaba geçiş etkin hesabı ve molayı silmeden bekleyen aktarımı kaldırıyor.
- Açılışta kendi hesabını otomatik yenileme varsayılan kapalı. Aynı profilin 15 dakikalık kaydı kullanılıyor; ağdan önce son şifreli kayıt yeniden denetlenerek eski ekranın taze kayda rağmen istek göndermesi engelleniyor.
- Web girişi artık sayfa yüklenince profil GET'i başlatmıyor. Devam et tek açık deneme; bağlantı testleri ortak kalıcı 15 dakika bütçesinde. Başarılı yanıt yeni hesap bağlanmadan eski otomasyonu açmıyor.
- Native yanıt sınıflandırması tek sahibi paylaşıyor; HTTP 429, bekleme, feedback ve doğrulama ayrılıyor. Sunucu Retry-After süresi ile yerel mola ayrıldı; bilinmeyen süre bilinmeyen kalıyor. Yerel okuma sayacı yalnız zaman/durum/sabit sinyal tutuyor.
- 429 dayanakları, kapsamı ve test protokolü REQUEST-POLICY.md içinde. Yeni hesabın 429'dan etkilenmeyeceği veya erişimin kesin açılacağı iddia edilmez. Bu geliştirmede yeni canlı hesap isteği yapılmadı.

## 0.1.6 — 2026-10-24 (mobil oturum taşıma önizlemesi)

- Windows instagrapi 3.2.0 oturumundan cihaza özel RSA-OAEP/AES-GCM şifreli yerel ADB aktarımı; aktarım sırasında Instagram isteği veya parola girişi yok.
- Mobil profil okuyucusu: i.instagram.com, Windows kendi profil sorgu parametreleri, gerçek Authorization/cihaz durumu, kayıtlı USDID anahtarıyla imza; doğrulanmış yanıt alanlarıyla şifreli oturum güncellemesi.
- HTTP/2 zorunluluğu; yönlendirme, bağlantı tekrarı ve 503 gibi durumlardaki ek gönderimlerin engellenmesi. Önceki 429 molası ve tek etkin istek korunur.
- Başarılı yanıt öncesinde eski hesabı değiştirmeyen iki şifreli banka; v1-v3 hesap verisini koruyan v4 biçimi. Bağlandıktan sonra özet, kişi analizi ve arka plan aynı mobil oturumu kullanır.
- Şifreleme, Keystore, oturum dönüşümü ve gerçek yerel TLS/HTTP2 tekrar/yönlendirme testleri.

İlk bağlantı Windows'taki mevcut oturumu gerektirir. APK içinde yeni CAA/parola girişi yoktur. Kotlin/OkHttp taşıması Windows curl/TLS uygulamasıyla birebir aynı değildir; canlı Android başarısı henüz doğrulanmadı.

## 0.1.5 — 2026-10-24 (bağlantı prototipi)

- REA ile oturum başlıklarının üretimi/yanıtlarla güncellenmesi incelendi; bulgular ve belirsizlikler belgelendi. 429'un kesin nedeni bulunduğu iddia edilmez.
- Ayrı prototip ekranında aynı WebView oturumundan tek kontrollü web profil okuması; otomatik giriş/yenileme yok. Mevcut 429 ve daha uzun sunucu beklemesi korunur; denemeler en az 15 dakika aralıklıdır.
- Açık deneme sayacı, HTTP durumları ve değer içermeyen yerel tanılama. Cookie/parola/ham yanıt kaydı veya native'e aktarımı yok.
- İptal/arka plana geçiş/sayfa değişiminde geç sonuç reddi; normal kapanışta tarayıcı temizliği. Prototip süresince arka plan okumaları bekletilir.
- Debug fixture üzerinden gerçek WebView/fetch ve tarayıcı JS sözleşmesi testleri; fixture release paketine alınmaz.

Bu prototip ön plandaki bağlantıyı sınar; kalıcı şifreli tarayıcı oturumu ve arka plan bildirimlerine bağlanmış yeni taşıma değildir.

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
