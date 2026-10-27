# InstagramTracker Windows 0.2.0

`artifacts/InstagramTracker-Desktop-0.2.0/InstagramTracker.exe` açılır. ZIP dağıtımını önce bütünüyle çıkart; yanındaki dosyaları ayırma. Bu sürüm taşınabilir, imzasız Windows x64 uygulamasıdır; Python kurulumu istemez. Web tasarım önizlemesi ve Android APK ayrıca korunur.

## Kullanım

1. **Hesap bağlantısı:** kullanıcı adı ve şifreyi uygulamadaki alana gir. Desteklenen e-posta/2FA kodu istenirse aynı pencereden gönder. Kod beklemesi 5 dakika ve iptal edilebilir. Resmî Instagram güvenlik kontrolü istenirse orada tamamla. Parola otomatik tekrar gönderilmez.
2. Önceki Windows InstagramCounter prototipinde bir oturum varsa **Önceki Windows oturumunu kullan** görünür. Eski dosya değiştirilmeden kopya okunur; hesap kimliği canlı kendi profil yanıtıyla doğrulanmadan yeni depoya kabul edilmez. Uygulama açılışı yalnız önbelleği gösterir; otomatik giriş veya ağ okuması yapmaz.
3. **Özet:** Gözlemi yenile kendi profilini okur. Takipçi/takip edilen sayıları, varsa gönderi, biyografi, kullanıcı adı, görünür ad, bağlantı, gizlilik ve doğrulanma bilgileri kaydedilir. Profil fotoğrafında karşılaştırılabilir kimlik varsa değişim kaydedilir; yalnız CDN URL değişikliği fotoğraf değişikliği sayılmaz. Grafik ve geçmiş ilk gözlemden başlar.
4. **Kişiler:** kullanıcı adıyla profil ekle, favori/izleme durumunu, grup ve özel notunu düzenle. Liste üyesini yerel kaydetmek profil isteği göndermez. Ayrıntıdaki **Profili kontrol et** canlı gözlem alır; ayrıntıyı açmak ve aramak ağ isteği göndermez. Kişilerden kaldırma not/tercihleri kaldırır; eski gözlem ve liste kayıtları korunur.
5. **Listeler:** hesap ve yön seçip **Listeyi tara**. Takipçi ve takip edilen ayrı ayrı okunur; sayfalarda tek tek ilerleme, ortak 15 saniyelik aralık ve iptal vardır. Tarama başındaki profil sayısı, kayıt sayısı ve sunucunun sayfalama sonu uyuşursa tamamlanmış gözlem kaydedilir. Sunucu kısıtlaması, belirsiz son, sayı uyuşmazlığı, döngü veya iptal kısmi kayıt bırakır; eski tamamlanmış kayıt korunur. Kısmi tarama açılışta sürdürülmez; yeni tarama baştan başlar. Bu ölçütler tarama boyunca değişen Instagram listesinin mutlak eksiksizliğini garanti etmez.
6. İki tamamlanmış kaydı seçip **Farkları göster**. Yeni görülen ve son kayıtta olmayan kimlikler ayrılır; bunlar kesin takipten çıkış/engelleme tespiti değildir. İki yön tamamlandıysa iki listede ortak veya diğer listede olmayan hesapları filtreleyebilirsin. Kayıt zamanları farklı olabilir. **CSV dışa aktar**, seçtiğin tamamlanmış kaydı dışa aktarır; CSV şifresizdir ve hesap oturumu içermez. Elektronik tablo formülü olarak yorumlanabilecek hücrelere başta tek tırnak eklenir.
7. **Zaman çizelgesi:** profil farkları, liste kayıtları, başarılı kontroller ve hatalar; eski/yeni değerler ve kayıt zamanı. Kayıt zamanı değişikliğin Instagram'da gerçekleştiği kesin zaman değildir.
8. **Ayarlar:** kompakt görünüm, Windows bildirimleri ve isteğe bağlı otomatik profil kontrolü. Uygulama açıkken her seçili aralıkta en eski gözlemli bir profil okunur; kendi hesabın ve izlediğin kişiler sırayla kontrol edilir. Tam liste taraması, uygulama kapalıyken çalışma ve otomatik yeniden giriş yapılmaz. Sınırlama/doğrulama sonrası otomatik okumalar durur; önce elle başarılı profil kontrolü gerekir.

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

Backend testi: `desktop/backend` içinde derleme Python'u ile `-m unittest -v test_backend.py`. Gerçek Windows DPAPI ve üretim sınırları kullanılır; ağ taşıyıcısı sahtedir. `npm run check:functional` paketlenmiş EXE'yi geçici `TrackerQA-*` verisiyle açar; bu kip worker'da ağ/oturum aktarımını kapatır. Gerçek kullanıcı deposuna, eski prototip oturumuna ve gerçek Başlat menüsüne yazmaz. Rapor/ekran görüntüleri `artifacts/desktop-0.2.0-qa` altındadır.

27 Ekim: 36 çevrimdışı backend testi ve 21 paketlenmiş Windows kontrolü geçti. Canlı test, kayıtlı Windows oturumunun kabulünü, kendi profil ve takipçi/takip edilen listelerinin HTTP 200 ile okunmasını doğruladı. Listeler bu hesapta birer sayfaydı. Çok sayfalı akış, kısmi kapsam, kesinti, 429, kod devamı ve iptal çevrimdışı sınandı. Yeni parola/2FA girişi, başka hesaplar, uzun süreli oturum, gerçek Windows bildirim teslimi ve uzun süreli otomatik çalışma bu sürümde canlı doğrulanmadı. Bu sonuçlar Android girişinin veya tarayıcı sürümünün çalıştığı anlamına gelmez.
