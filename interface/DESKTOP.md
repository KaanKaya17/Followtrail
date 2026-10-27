# InstagramTracker Windows 0.2.3

`artifacts/InstagramTracker-Desktop-0.2.3/InstagramTracker.exe` açılır. ZIP dağıtımını önce bütünüyle çıkart; yanındaki dosyaları ayırma. Bu sürüm taşınabilir, imzasız Windows x64 uygulamasıdır; Python kurulumu istemez. Web tasarım önizlemesi ve Android APK ayrıca korunur.

## Kullanım

1. **Hesap bağlantısı:** kullanıcı adı ve şifreyi uygulamadaki alana gir. Desteklenen e-posta/2FA kodu istenirse aynı pencereden gönder. Kod beklemesi 5 dakika ve iptal edilebilir. Resmî Instagram güvenlik kontrolü istenirse orada tamamla. Parola otomatik tekrar gönderilmez.
2. Önceki Windows InstagramCounter prototipinde bir oturum varsa **Önceki Windows oturumunu kullan** görünür. Eski dosya değiştirilmeden kopya okunur; hesap kimliği canlı kendi profil yanıtıyla doğrulanmadan yeni depoya kabul edilmez. Uygulama açılışı önbelleği gösterir; otomatik parola girişi yapmaz. Önceden açılmış otomatik profil kontrolü varsa kayıtlı oturumla süre ve mola kurallarına göre devam eder.
3. **Özet:** Gözlemi yenile kendi profilini okur. Takipçi/takip edilen sayıları, varsa gönderi, biyografi, kullanıcı adı, görünür ad, bağlantı, gizlilik ve doğrulanma bilgileri kaydedilir. Profil fotoğrafında karşılaştırılabilir kimlik varsa değişim kaydedilir; yalnız CDN URL değişikliği fotoğraf değişikliği sayılmaz. Grafik ve geçmiş ilk gözlemden başlar.
4. **Kişiler:** kullanıcı adıyla profil ekle, favori/izleme durumunu, grup ve özel notunu düzenle. Liste üyesini yerel kaydetmek profil isteği göndermez. Ayrıntıdaki **Profili kontrol et** canlı gözlem alır; ayrıntıyı açmak ve aramak ağ isteği göndermez. Kişilerden kaldırma not/tercihleri kaldırır; eski gözlem ve liste kayıtları korunur.
5. **Listeler:** hesap ve yön seçip **Listeyi tara**. Takipçi ve takip edilen ayrı ayrı okunur; sayfalarda tek tek ilerleme, ortak 15 saniyelik aralık ve iptal vardır. Tarama başındaki profil sayısı, kayıt sayısı ve sunucunun sayfalama sonu uyuşursa tamamlanmış gözlem kaydedilir. Sunucu kısıtlaması, belirsiz son, sayı uyuşmazlığı, döngü veya iptal kısmi kayıt bırakır; eski tamamlanmış kayıt korunur. Kısmi tarama açılışta sürdürülmez; yeni tarama baştan başlar. Bu ölçütler tarama boyunca değişen Instagram listesinin mutlak eksiksizliğini garanti etmez.
6. İki tamamlanmış kaydı seçip **Farkları göster**. Yeni görülen ve son kayıtta olmayan kimlikler ayrılır; bunlar kesin takipten çıkış/engelleme tespiti değildir. İki yön tamamlandıysa iki listede ortak veya diğer listede olmayan hesapları filtreleyebilirsin. Kayıt zamanları farklı olabilir. **CSV dışa aktar**, seçtiğin tamamlanmış kaydı dışa aktarır; CSV şifresizdir ve hesap oturumu içermez. Elektronik tablo formülü olarak yorumlanabilecek hücrelere başta tek tırnak eklenir.
7. **Zaman çizelgesi:** profil farkları, liste kayıtları, başarılı kontroller ve hatalar; eski/yeni değerler ve kayıt zamanı. Kayıt zamanı değişikliğin Instagram'da gerçekleştiği kesin zaman değildir.
8. **Ayarlar:** kompakt görünüm, Windows bildirimleri ve isteğe bağlı otomatik profil kontrolü. 1/5/15/30/60/180/360 dakika seçeneklerinden her seçili aralıkta en eski gözlemli tek profil okunur; kendi hesabın ve izlediğin kişiler sırayla kontrol edilir. X ile pencereyi kapatınca sistem tepsisinde çalışmaya devam eder. Tepsi simgesine tıkla → pencere açılır; sağ tık → otomatik kontrol aç/kapat veya Tamamen çık. Tamamen çıkınca kontrol durur. Bilgisayar kapalı/uykuda veya internet yokken kontrol yapılamaz. Normal profil kontrolü tam liste taramaz; aşağıdaki isteğe bağlı kaybolan takipçi incelemesi bu konuda ek tarama yapabilir. Otomatik yeniden giriş yapılmaz. Sınırlama/doğrulama sonrası otomatik okumalar durur; önce elle başarılı profil kontrolü gerekir.

Windows bildirimini açmak, yalnız bu uygulamaya ait Başlat menüsü kısayolunu AppUserModelID ve ToastActivatorCLSID ile oluşturur/günceller. Farklı sahibin kısayolu üzerine yazılmaz; değişen eski kendi kısayolunun önceki kopyası veri klasöründe korunur. İşlem, [Electron'un Windows bildirim gereksinimine](https://www.electronjs.org/docs/latest/tutorial/notifications#platform-considerations) dayanır. Windows/odaklanma ayarları teslimi etkileyebilir; başarısız bildirim veri okuma hizmetini kapatmaz, değişiklik zaman çizelgesinde kalır.

## Gizlilik ve yerel kayıt

Veriler `%LOCALAPPDATA%\InstagramTracker-Desktop\tracker.vault` ve son önceki şifreli `tracker.previous.vault` içinde Windows DPAPI ile korunur. Atomik geçici dosyaya da yalnız şifreli içerik yazılır. Ayrı Electron kullanıcı dizini `%APPDATA%\InstagramTracker-Desktop` altındadır. Giriş parolası ve doğrulama kodu kaydedilmez; Instagram oturum ayarları ana arayüze gönderilmez. Oturumu kaldırmak önceki oturumlu depo kopyasını da kaldırır; bu uygulamanın gözlemleri korunur. **Yerel verileri sil** gözlemleri ve oturumu kaldırır, istek molasını korur. Eski InstagramCounter deposuna ve dışa aktarılan dosyalara dokunulmaz.

Şifreli `.trackerbackup` yedekler gözlem/not/tercih içerir; oturum içermez. Aynı Windows kullanıcısında açılmak içindir. Geri yüklemede önceki depo şifreli önceki kopyada korunur, otomatik kontrol kapatılır, oturum yeniden bağlanır ve daha uzun mevcut istek molası korunur. Bozuk depo sessizce sıfırlanmaz.

Hesap istekleri sabit HTTPS `i.instagram.com` hedefini kullanır. Profil fotoğrafı, oturum/çerez taşımayan ayrı bağlantıyla yalnız `cdninstagram.com` / `fbcdn.net` üzerinden yüklenebilir; küçük JPEG olarak yerel şifreli kayda alınır. Görsel yüklenmezse harfli avatar gösterilir. Aracı sunucu, telemetri ve bulut veritabanı yoktur. Arayüzün doğrudan ağ bağlantısı CSP ve Electron oturum politikasıyla kapalıdır; yalnız sınırlı, ana çerçevesi doğrulanan yerel IPC köprüsü vardır. Worker özel stdin/stdout borularıyla konuşur; localhost sunucusu açmaz.

API isteklerinde HTTP/2, TLS doğrulaması, 25 saniyelik taşıma zaman aşımı, kapalı yönlendirme ve kapalı otomatik tekrar kullanılır. Parola girişleri arasında en az 15 dakika beklenir. 429 sonrası yerel mola 1/2/4/8/16/24 saat ve varsa daha uzun Retry-After'dır. Bu süreler güvenli Instagram kotası veya erişim garantisi değildir.

Yerel kapasite: 200 kişi, profil başına son 366 gözlem, 2.000 olay, her hesap/liste yönü için son 6 tamamlanmış tarama ve son kısmi tarama; tarama başına 20.000 üye / 400 sayfa, şifreli depo için 64 MiB bütçe. Bunlar uygulama kaynak sınırlarıdır. Uzun listeler 15 saniyelik sayfa aralığı nedeniyle uzun sürebilir. Bir depoda tek bağlı Instagram hesabı tutulur; başka hesaba geçmeden yedek alıp yerel verileri kaldır.

## Geliştirme ve doğrulama

Electron 45.0.0 ve Playwright Core 1.64.1 `package-lock.json` ile sabittir. Worker Python 3.12.14 / instagrapi 3.2.0 ile bu makinede derlendi; runtime bağımlılıkları `desktop/backend/requirements.lock.txt`, paketleme bağımlılıkları `build-requirements.lock.txt` ile sabittir. Proje sanal ortamında kilitli dosyalarla kur; sistem Python veya eski prototip ortamını güncelleme.

Interface klasöründe `npm ci`, gerekirse `node node_modules/electron/install.js`; özel worker derleme ortamında:

```powershell
$env:TRACKER_BUILD_PYTHON = 'C:\path\to\build-venv\Scripts\python.exe'
& $env:TRACKER_BUILD_PYTHON -m pip install -r desktop/backend/requirements.lock.txt
& $env:TRACKER_BUILD_PYTHON -m pip install --target .cache/build-tools -r desktop/backend/build-requirements.lock.txt
./desktop/backend/build-worker.ps1
npm run check
npm run package:functional
npm run check:functional
```

Mevcut kendi paketi yenilenirken `npm run package:functional -- --refresh` önce eski paket varlıklarını artifacts altına yedekler. Yeni Electron runtime için ayrı sürüm klasörü gerekir. Paket Python bağımlılık bildirimlerini de içerir. `npm run desktop` kaynaktan işlevli uygulamayı, `npm run desktop:preview` yalnız örnek verili eski tasarımı açar.

Backend testi: `desktop/backend` içinde derleme Python'u ile `-m unittest discover -p test_*.py -v`. Gerçek Windows DPAPI ve üretim sınırları kullanılır; ağ taşıyıcısı sahtedir. `npm run check:functional` paketlenmiş EXE'yi geçici `TrackerQA-*` verisiyle açar; bu kip worker'da ağ/oturum aktarımını kapatır. Gerçek kullanıcı deposuna, eski prototip oturumuna ve gerçek Başlat menüsüne yazmaz. Rapor/ekran görüntüleri `artifacts/desktop-0.2.2-qa` altındadır.

Önceki Windows 0.2.1 doğrulaması: 39 çevrimdışı backend testi, 4 masaüstü zamanlayıcı/tepsi testi ve 23 paketlenmiş Windows kontrolü geçti. Canlı test, kayıtlı Windows oturumunun kabulünü, kendi profil ve takipçi/takip edilen listelerinin HTTP 200 ile okunmasını doğruladı. Listeler bu hesapta birer sayfaydı. Çok sayfalı akış, kısmi kapsam, kesinti, 429, kod devamı ve iptal çevrimdışı sınandı. Yeni parola/2FA girişi, başka hesaplar, uzun süreli oturum, gerçek Windows bildirim teslimi ve uzun süreli otomatik çalışma bu sürümde canlı doğrulanmadı. 1 dakikalık aralıkla pencere gizliyken iki gerçek profil kontrolü HTTP 200 ile geldi; istek başlangıçları arasında 60,11 saniye ölçüldü. Tek elle profil kontrolü bu bağlantıda 1,28 saniye, istekten fotoğraf aşamasına kadar yaklaşık 0,74 saniyeydi. Bu tek örnek ağ/hesap ölçümüdür; hız garantisi değildir. Bu sonuçlar Android girişinin veya tarayıcı sürümünün çalıştığı anlamına gelmez.

## Kontrol hızı ve sürüm geçişi

Kontrol aralığı, tek profil isteğinin yanıt süresinden ayrıdır. Her turda bir profil okunur: N profil ve T dakika aralık için bütün profil sırasının dolaşımı yaklaşık N×T dakika + okuma/işlem beklemeleridir. Ana süreç sırayı 5 saniyede bir değerlendirir; pencerenin gizlenmesi renderer zamanlayıcısına bağlı değildir. Süresi geçmiş kontroller uyku/işlem beklemesi sonrası topluca gönderilmez. Yeni 1/5 dakika seçenekleri API istekleri arasındaki 15 saniyelik yerel aralığı, 429 molasını, oturum/doğrulama engelini veya manuel liste taramasını değiştirmez.

Bir liste taraması, başlangıç profil okuması ve her sayfa için ayrı API isteği yapar. Sunucu sayfa başına 50 üye döndürürse 1.000 üyelik liste yaklaşık 20 sayfadır; 15 saniyelik aralık nedeniyle ilk profil isteğinden son sayfaya en az yaklaşık 5 dakika + yanıt süresi gerekir. Bu koddan hesaplanan örnektir, 1.000 üyeli canlı ölçüm değildir.

Önceki otomatik kontrol ve aralık tercihin korunur; yeni aralık seçimi için Ayarlar → Kontrol aralığı kullan. 1/5 dakikalık ayarlar eski Windows 0.2.0 doğrulayıcısında yoktur; bu ayarları seçtikten sonra güncel 0.2.2 EXE kullan. Depo biçimi ve hesap verileri korunur. Windows başlangıcında otomatik başlatma eklenmedi.

## Engellenme şüphesi / Erişim kontrolleri

**Ayarlar → Kaybolan kayıtlı takipçileri incele** ile açılır. Otomatik çalışma için **Otomatik profil kontrolü**, Windows uyarısı için **Windows bildirimleri** de açık olmalı. Yeni seçenek kapalı gelir; önceki tercihler ve hesap kayıtları korunur.

Önce kendi takipçi listenin bir tamamlanmış başlangıç kaydını oluştur. Sonraki profil gözleminde takipçi sayısı düşerse, ayar açıkken yeni bir tam takipçi taraması sıraya girer. İki tamamlanmış kendi takipçi kaydında eksilen **Kişiler'de kayıtlı hesaplar** erişim inceleme kuyruğuna alınır. Tek başına sayı düşüşü veya kısmi liste kimseyi engellemiş saymaz. Başlangıç listesi yoksa Listeler'de eksik önkoşul görünür. Liste taraması yavaşsa diğer profil kontrolleri de bekler; her API isteği arasında mevcut 15 saniyelik aralık korunur.

İncelemede önce kendi profilin aynı bağlı oturumdan doğrulanır, sonra hedef **kayıtlı hesap kimliğiyle** okunur. İki isteğin arasında en az 15 saniye vardır. Kullanıcı adı değişmişse yeni adıyla erişilebilir görünür; eski kullanıcı adına arama yapılıp yanlış kişiyle eşleştirilmez. Profil yanıtı görülebiliyorsa sayaçların eksik olması hedefi bulunamadı yapmaz. Bağlantı/timeout/401/403/5xx/doğrulama/429 ve kimlik belirsizliği engellenme şüphesi olarak bildirilmez.

Önceki tam listede olup sonraki tam listede olmayan kişinin profili, sağlıklı oturumdan HTTP 404 ile bulunamazsa **Engellenme şüphesi** kaydı ve bildirimler açıksa aynı başlıklı Windows bildirimi oluşur. Hesabı kapatma/silme, sizin uyguladığınız bir engel veya sunucunun erişim davranışı kesin ayrıştırılamaz; ürün kesin “seni engelledi” demez. API'deki `blocking` alanı sizin karşı tarafa engelinizdir; size engel olarak ters çevrilmez. Meta da [engellenen kişinin profili arayamayacağını ve engellemede bildirim gönderilmediğini](https://mydigitalworld.fb.com/mena/lesson/options-for-controlling-your-experience-on-instagram-facebook-messenger-and-whatsapp/) açıklar; bu belirtileri tek başına kesin teşhis kabul etmiyoruz.

**Listeler → kendi hesabın → Takipçiler** altında inceleme vakaları ve kanıtları; kişi ayrıntısında **Erişimi kontrol et**; zaman çizelgesinde **Erişim kontrolleri** filtresi vardır. Elle kontrol bir kayıtlı kişi veya liste üyesi üzerinde çalışır; tam liste yokluk kanıtı yoksa bulunamayan profil yalnız “Profile erişilemiyor; nedeni belirlenemedi” sonucudur. Şüphe bildirimi tıklanınca tek hedef varsa kanıt paneli açılır.

Aynı kaybolma için tek uyarı kaydı tutulur. Profil listede yeniden görünüp tekrar kaybolursa yeni bir inceleme olayı başlayabilir. Kayıt zamanı kesin engelleme saati değildir. Başarısız veya iptal edilen incelemeler otomatik tekrar edilmez; elle tekrar kontrol edilebilir. Tek hedef incelemesi profil sırasıyla dönüşümlü ilerler; ek liste/inceleme işleri toplam kontrol turunu uzatabilir. En çok 200 vaka ve vaka başına son 10 değerlendirme tutulur; geçmiş şüphe olayları o anki liste kanıtını ayrı olarak taşır. Eski şifreli yedekler migration ile açılır; eski EXE'ler yeni erişim değerlendirmelerini yönetmez, güncel sürümü kullan.

0.2.2 doğrulaması: 62 offline backend testi, 6 ana süreç/bildirim testi, 25 paketlenmiş EXE kontrolü geçti. Canlı bir kayıtlı profil erişimi ve kendi profil sağlığı HTTP 200 ile doğrulandı; toplam 15,66 saniye. Gerçek engellenmiş hesap ve Windows bildiriminin gerçek teslimi bu sürümde canlı doğrulanmadı. Engelleme/takip ilişkisi oluşturmak veya değiştirmek için işlem yapılmadı.

## Hesaba ve türe göre bildirimler · 0.2.3

Kişi ayrıntısındaki **Bildirim tercihleri** düğmesinden veya **Ayarlar → Hesaba göre bildirim türleri → hesabı seç → Türleri seç** yolundan açılır. Kendi hesabın için de ayrı seçim yapılabilir. Hesabı tamamen sessize alabilir veya yalnız istediğin alanları seçebilirsin: kullanıcı adı, görünen ad, biyografi, profil fotoğrafı, takipçi/takip edilen/gönderi sayısı, bağlantı, gizlilik, doğrulanma, liste taraması, engellenme şüphesi. Önceden özel tercih yoksa türler açık kabul edilir; genel **Windows bildirimleri** anahtarının önceki durumu korunur. Alanları değiştirmek ağ isteği göndermez.

Örnek: Arda için kullanıcı adı açık, takipçi sayısı kapalı olsun. Bir kontrolde kullanıcı adı, biyografi ve takipçi sayısı değişirse seçili alanlar için **tek Windows bildirimi** oluşturulur. Tıklanınca aynı kontrolün bütün değişiklikleri eski/yeni değerleriyle uygulama içinde açılır. Kapalı takipçi değişikliği de **Yalnız kayıtta**, seçili değişiklikler **Bildirim için seçili** etiketiyle görünür. Bildirimi kapatmak gözlem, zaman çizelgesi veya log kaydını silmez.

Sol menüde **Bildirimler** veya üstteki zil, kontrol başına tek satır olan kayıtları açar. Kayda tıklamak okundu olarak işaretler; **Okunmamış** filtresi ve zil göstergesi vardır. Okundu ve hesap tercihleri şifreli saklanıp yeniden açılışta/yedekten yüklemede korunur. Bildirim kaydındaki alan seçimi ve değerler gözlem anına aittir; sonraki tercih/kullanıcı adı değişiklikleri geçmişi yeniden yazmaz. Son **500 grup**, mevcut 64 MiB ortak şifreli depo bütçesinde tutulur.

Bu sürümden önceki zaman çizelgesi korunur; eski olaylardan yapay, geriye dönük Windows bildirimi/log üretilmez. İlk gözlem veya değişimsiz kontrol yeni bildirim oluşturmaz. Windows bildirimleri genel kapalıyken de yeni değişiklik log'u oluşur. “Windows bildirimi için seçildi” teslim edildi anlamına gelmez; Windows/odaklanma ayarları teslimi etkileyebilir. Listeler ve engellenme şüphesi önceki kapsam/mola kurallarını korur; şüphe kesin engelleme kanıtı değildir.

28 Ekim doğrulaması: **78** offline backend testi, **9** ana süreç/bildirim testi ve **27** gerçek paketlenmiş EXE kontrolü geçti. Gerçek mevcut hesapla açılışta kayıtlar ve genel tercihler korundu; 12 türün penceresi açıldı, API istek sayısı artmadı. Birden çok gerçek Instagram alanını bu sürümde değiştirmedik; gruplama/filtreleme, click IPC ve kalıcı log üretim sınırında sentetik verilerle doğrulandı. Gerçek Windows toast teslimi/tıklaması ayrıca canlı doğrulanmadı.
