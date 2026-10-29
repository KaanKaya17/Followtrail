# Windows 0.2.12 — Çoklu hesap önizlemesi

## Kullanım ve kapsam

Sol alttaki hesap düğmesi → **Hesap ekle**. İlk bağlantı doğrulandıktan sonra hesap ayrı şifreli kasaya eklenir. Önceden bağlanmış hesaplara aynı menüden geçilir; geçiş yerel veriyi açar, Instagram isteği veya yeni parola girişi başlatmaz. Oturum süresi dolarsa ilgili hesabın yeniden bağlanması gerekir. Yeni hesabın ilk bağlantısı mevcut Instagram giriş/doğrulama sınırlamalarına tabidir.

Her hesabın kişileri, notları, listeleri, geçmişi, profil zaman tüneli, oturumu, bildirim tercihleri ve kontrol aralığı ayrıdır. Dil, tema ve kompakt görünüm cihaz genelindedir. **Yerel verileri sil** yalnız seçili hesabı kaldırır; onay metni bunu belirtir. **Bağlantıyı kaldır** yalnız seçili oturumu kaldırır, o hesabın gözlemlerini tutar. Şifreli yedek/CSV seçili hesaba aittir; farklı hesap kimliğine ait yedek mevcut hesabın üzerine yüklenmez.

Arka plan kontrolü her hesabın Ayarlar ekranından ayrı açılır. Tek worker ve ortak işlem kilidi kullanılır; hesap başına yeni süreç veya zamanlayıcı yoktur. Arka planda başka hesap incelendiğinde ekranda seçili hesap korunur. İşlem sürerken hesap değiştirmek bekletilir; iptal/tamamlanma sonrasında geçilebilir. Bildirim başlığında bağlı hesabın kullanıcı adı yer alır. Bildirime dokunmak önce sahibi olan hesaba geçer, sonra o hesabın bildirim kaydını açar. Başka işlem sürerken yanlış hesabın kaydı açılmaz; kullanıcıya bilgi verilir.

15 saniyelik istek aralığı, 15 dakikalık parola giriş aralığı ve HTTP 429 molası ortaktır. Hesap geçişi, silme veya yeniden açılış molayı kısaltmaz. 429 sonrası bütün hesapların otomatik kontrolü durur; mola bitince başarılı elle profil/bağlantı kontrolü gerekir. Oturum doğrulama hatası ise ilgili hesabı duraklatır. Çok sayıda hesap/profil veya uzun taramalar ortak sırayı meşgul edebilir; seçilen kontrol aralığı kesin teslim süresi değildir.

## Depo ve geçiş

`%LOCALAPPDATA%\InstagramTracker-Desktop\accounts.vault` hesap dizini, seçili sayısal kimlik, ortak istek kapısı, cihaz ayarları ve küçük zamanlama özetlerini DPAPI ile şifreler. Yeni hesaplar `accounts/<rastgele yerel anahtar>/tracker.vault` kullanır; dosya adında kullanıcı adı veya Instagram kimliği yoktur. Her kasanın ayrı şifreli önceki kopyası vardır. DPAPI koruması aynı Windows kullanıcısının yetkisindedir; ayrı işletim sistemi kullanıcısı veya ayrı parola koruması sağlandığı iddia edilmez.

Mevcut tek hesap `tracker.vault` ve `tracker.previous.vault` yerinde korunarak şifreli dizine ilk hesap olarak kaydedilir; oturumu yeniden oluşturulmaz. Yalnız yarım kalmış işlerin olağan açılış onarımı kayıt değiştirebilir. Eksik/bozuk kasa otomatik boşaltılmaz. Yeni kasa yazılamazsa önceki hesap korunur. Silme önce ilgili kasayı ve oturumlu önceki kopyasını temizler; dizin güncellemesi yarıda kesilirse şifreli silme işaretinden açılışta tamamlanır. Dışa aktarılmış yedeklere dokunulmaz.

Yalnız etkin hesabın büyük kaydı bellekte tutulur; geçiş sırasında eski/yeni kayıt kısa süre birlikte bulunabilir. Hesap listesi diğer kasaları açmak yerine şifreli dizindeki küçük özetleri kullanır. Ortak mola, hesap dosyasından önce kalıcılaştırılır; büyük kasa yazma hatası yeni istek rezervasyonunu kaybettirmez. Oturum/parola/çerez ana arayüze aktarılmaz. Üçüncü taraf servis veya telemetri eklenmedi.

Eski Windows sürümü yeni hesap dizinini tanımaz. Güncellemeden sonra eski sürüme geri dönerek aynı veri klasöründe işlem yapılmamalıdır; eski sürüm yalnız ilk kasayı görür ve ortak istek kapısını kullanmaz.

## Doğrulama

Testler ayrı `TrackerQA-*` dizinlerinde sentetik verilerle çalışır. DPAPI, dosya yazımı, paketlenmiş worker ve Electron IPC gerçektir; Instagram taşıyıcısı testlerde sahte veya `--offline` ile kapalıdır. Gerçek hesap deposu açılmadı/değiştirilmedi; yeni giriş denemesi yapılmadı.

- `test_accounts.py`: mevcut kasayı koruyan geçiş; ayrı not/oturum; aynı numeric ID ile ad değişikliği; cihaz ayarları ve bağımsız izleme tercihleri; ortak 429/istek rezervasyonu; arka plan hesabının doğru kaydı/bildirimi ve seçimin geri yüklenmesi; hesapla sınırlı oturum hatası; başarısız giriş/kasa yazımı; hesap silme ve kesintiden toparlanma; yabancı yedeği reddetme; ilk yedeği ayrı kasaya alma; bozuk/eksik kasa; dizin yol doğrulaması; açılış onarımında ortak molanın korunması.
- Node testleri: arka plan seçilmemiş hesabı işlerken tepsi anahtarının seçili hesaba bağlı kalması; worker bildiriminde hesap sahibinin korunması; bildirimin önce doğru hesaba geçmesi; meşgulken yabancı kayıt açılmaması.
- `check-accounts-desktop.mjs`: gerçek EXE üzerinde hesap menüsü, boş yeni bağlantı formu, geçişte eski palet/filtrelerin kapanması, ayrı not/listeler, cihaz genelinde Türkçe/İngilizce ve OLED, 960×680 pencere, yeniden açılış, hesaba özel silme ve değişmeyen istek sayacı. Yerel dosya seçimi açıkken hesap değişirse başka hesabın yedeğinin dışa aktarılmaması da gerçek IPC/dosya sınırında doğrulandı. Sentetik ekranlar `artifacts/accounts-ui/` altında yereldir.

Çalıştırılan kontroller: Python discovery **140 test** (20 yeni çoklu hesap testi), Node **28 test**, `npm run check`, paketlenmiş EXE için **10 çoklu hesap**, **30 işlev**, **10 kısayol/onboarding**, **9 ilerleme** kontrolü ve Windows/web için **8 tema senaryo grubu** geçti. Zaman tüneli ayrıntısının koordinatörden geçerken korunması ve sonraki durumlara tekrar eklenmemesi ayrıca doğrulandı. Android kaynakları değiştirilmedi; Android derleme/telefon testleri bu değişiklik için yeniden çalıştırılmadı.

## Kaynak kullanımı

29 Ekim yerel ölçümünde önce paketlenmiş 0.2.11 (bir kayıtlı hesap), ardından 0.2.12 (aynı etkin veri, ek bir pasif sentetik hesap) çalıştırıldı. Her birinde 3 saniye ısınma ve görünür boşta/gizli boşta/gizliyken 40 yerel şifreli not kaydı için ayrı yaklaşık 20 saniye örneklem alındı. Ölçümler sıralı yapıldı; aynı anda başka QA uygulaması çalıştırılmadı. `profile-desktop.mjs 0.2.11` ve `profile-desktop.mjs 0.2.12 --accounts` kullanıldı.

- İki sürüm de uygulama ailesinde **7 süreç** kullandı. 0.2.12'de görünür ve gizli boşta örneklemlerinde ölçülen CPU farkı **0 saniye**, görünür boşta tepe working set **365,6 MiB**, gizli boşta **367,6 MiB** oldu. Örnekleme/CPU sayacı çözünürlüğü nedeniyle bu, sürekli sıfır işlem tüketimi iddiası değildir.
- Gizliyken 40 not kaydı: 0.2.11 **0,564 saniye** duvar süresi / **0,281 CPU saniyesi** / **378,3 MiB** aile working-set tepesi; 0.2.12 **0,544 saniye** / **0,250 CPU saniyesi** / **375,3 MiB** tepe. Özel commit bellek tepeleri sırasıyla **238,5 / 234,2 MiB**. Bu küçük farklar tek kısa koşuda hızlanma kanıtı sayılmaz; ek pasif hesabın bu işte belirgin bir artış üretmediğini gösterir.
- Her üç fazda iki sürümde de uygulama ana DOM mutasyon sayısı **0** kaldı. Yeni sürüm yerel not değişirken aynı kalan hesap dizinini yeniden şifreleyip yazmaz; yalnız değişen büyük kasa yazılır.

500 ms örnekleme yapıldı; working-set toplamı paylaşılan sayfaları birden fazla sayabilir. Soğuk açılış, saatler süren bellek sızıntısı, çok sayıda büyük kasa, gerçek ağ/bildirim yükü ve zayıf cihazlar ölçülmedi. Yerel ham sonuçlar `artifacts/desktop-0.2.11-qa/performance/` ve `artifacts/desktop-0.2.12-qa/performance-accounts/` altındadır.

Yeni hesapların gerçek Instagram girişleri, iki canlı oturumla uzun süreli izleme, gerçek Windows bildirim teslimi/tıklaması ve başka Windows kullanıcıları bu sürümde canlı doğrulanmadı. Android 0.1.34 ve örnek web arayüzüne çoklu hesap eklenmedi; Android giriş sorunu bu özellikle çözülmüş sayılmaz. Bu sürüm önizlemedir.
