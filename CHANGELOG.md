# Değişiklikler

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
