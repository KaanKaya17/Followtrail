# InstagramTracker Windows 0.2.21

`artifacts/InstagramTracker-Desktop-0.2.21/InstagramTracker.exe` açılır. ZIP dağıtımını önce bütünüyle çıkart; yanındaki dosyaları ayırma. Eski uygulamayı tepsiden tamamen kapatıp yeni EXE'yi aç. Mevcut şifreli hesaplar yerinde kullanılır. Bu sürüm taşınabilir, imzasız Windows x64 uygulamasıdır; Python kurulumu istemez. Web tasarım önizlemesi ve Android APK ayrıca korunur.

## Dört yerel uygulama dili

**Ayarlar → Uygulama dili** üzerinden Türkçe, English, Español veya Português (Brasil) seçilir. Tercih cihaz genelindeki şifreli ayarda tutulur; hesap kasaları, oturumlar, istek bütçesi, 429 molası, otomasyon ve bildirim tercihleri değişmez. Ekranlar, erişilebilirlik etiketleri, tepsi menüsü, yeni Windows bildirimleri, yerel hata açıklamaları ve sayı/tarih biçimleri seçilen dile uyar. Kayıtlı kullanıcı adı, biyografi, not, eski/yeni değer ve ham kanıt metinleri çevrilmez.

Çeviri katalogları paketle birlikte yereldir; uygulama dilini değiştirmek veya açmak için çeviri hizmetine, hesaba ya da Instagram’a ağ isteği gönderilmez. Eski depolar Türkçe seçili olarak açılır. Sistem dosya seçicisinin Windows tarafından sağlanan düğmeleri Windows’un kendi dilinde kalabilir.

## Otomatik başlangıç hazırlığı

Yeni hesap bağlantısından sonra ilk takipçi ve takip edilen listeleri kendiliğinden sıraya girer. Özet'teki Liste hazırlığı kartı sayfa/hesap sayısını, hazır/bekleyen yönleri ve duraklatma seçeneğini gösterir. İlk kayıt geçmiş takipten çıkışları açıklamaz. Sonraki tamamlanan taramalarla farklar oluşur. Sayı değişimlerinde ve günlük hedefte yeni tarama planlanır; aynı sayı içinde üye değişimi günlük taramayla gözlenebilir. Kısıtlama, büyük listeler ve istek bütçesi geciktirebilir.

Otomatik liste işi tek sayfa işler; devam bilgisi yalnız şifreli kasadadır. Tepside sürer, tamamen kapatılınca durur; açılınca uygun sayfadan devam edebilir. 24 saatten eski, yeni oturuma ait olmayan veya sunucunun reddettiği devam bilgisiyle kayıt birleştirilmez. Mevcut hesapta otomasyon kapalıysa tercihi korunur ve kartta Hazırlığa devam et görünür. 429 molasını bu düğme kaldırmaz; önceki manuel doğrulama kuralı devam eder. [Testler ve ayrıntılar](../docs/AUTOMATIC-PREPARATION-TEST.md).

## Görsel incelikler

Sayaçlar eşit genişlikli rakamlar kullanır. Düğme, ikon ve hesap seçicilerde kısa basma hissi; koyu/OLED kartlarda hafif üst ışık; temaya uygun kaydırma çubuğu vardır. Sistem koyu moduyla aynı ortak palet kullanılır; azaltılmış hareket ve yüksek kontrast tercihleri korunur. Blur, sürekli çalışan yeni animasyon veya ağ servisi eklenmedi. Android 0.1.36 aynı paleti native kart, sayı ve dokunma geri bildirimine uygular. [Doğrulama ve ekran kapsamı](../docs/VISUAL-POLISH-TEST.md).

## Hesap güvenliği sınırları

Okuyucu, yöntem ve kesin yol sözleşmesi dışındaki işlemleri gönderimden önce reddeder. Giriş/cihaz kaydı istisnaları yalnız giriş süresince açıktır; başarısız girişte cihaz bağlamı DPAPI ile korunur. Ortam proxy'leri kullanılmaz. Hesap başına 120/saat, 1200/24 saat; bütün hesaplarda 240/saat, 2400/24 saat kayan yerel bütçe uygulanır. Meta'nın güvenli kotası değildir. Yoğun kontrolde veya büyük listelerde hedef süre uzayabilir; İstek bilgileri sonraki bütçe zamanını gösterir. Yerel hesap silme, yeniden açılış ve hesap geçişi bütçeyi sıfırlamaz. Bağlantıyı kaldırmak sunucu oturumunu kapatmaz. [Testler ve sınırlar](../docs/ACCOUNT-SAFETY-TEST.md).

## Rastgele istek aralığı

Windows 0.2.14, sayaçlı Instagram API ve oturumsuz profil isteklerinde sabit 15 saniye yerine **12–18 saniye arasında eşit olasılıklı süre** seçer. Süre her giden istek için bir kez seçilir; mutlak sonraki istek zamanı şifreli ortak kapıya kaydedilir. Hesap değişimi, yeniden açılış, yerel ayar değişikliği ve ekrandaki geri sayım yeni süre seçmez. Önceden ayrılmış daha uzun beklemeler kısaltılmaz.

Bu değerlerin beklenen ortalaması 15 saniyedir; kısa örnekte veya gerçek ağda tam 15 saniye garanti edilmez. Beş dakikalık profil kontrol ayarı değişmez. Uzun yanıtlar, diğer hesapların işleri ve arka plan zamanlayıcısının 5 saniyelik değerlendirmesi gerçek aralığı uzatabilir. 429/Retry-After, yerel mola, 15 dakikalık parola giriş aralığı ve otomatik tekrar yapmama kuralları korunur. Bu, bot tespitinden veya 429’dan korunma garantisi değildir. Android’in zamanlayıcısı bu sürümde değiştirilmedi. [Doğrulama](../docs/REQUEST-JITTER-TEST.md).

## Çoklu hesap

Sol alttaki hesap düğmesi → **Hesap ekle** ile yeni hesap bağlanır; kayıtlı hesaplara oturumu kapatmadan geçilir. Geçiş Instagram isteği göndermez. Her hesabın ayrı şifreli kasası, kişileri, notları, liste/geçmişi, görsel arşivi, bildirim ve kontrol ayarları vardır. Dil, tema ve kompakt görünüm cihaz genelindedir. Oturum süresi dolarsa yalnız ilgili hesabı yeniden bağlamak gerekir.

Arka plan takibini her hesabın Ayarlar ekranından ayrı aç. Tek işlem sırası ve 12–18 saniyelik rastgele rezervasyon aralığı kullanılır; HTTP 429 molası bütün hesaplar için korunur. Tepsi durumu herhangi bir hesabın takibini gösterir; tepsideki aç/kapat seçeneği **seçili hesabı** etkiler. Bildirim başlığı ilgili hesabı belirtir; tıklamak o hesaba geçip kendi kaydını açar. İşlem sürerken geçiş yapılamaz. **Yerel verileri sil** yalnız seçili hesabı ve oturumlu önceki kopyasını kaldırır; diğer hesaplar korunur.

Mevcut hesap yeniden giriş olmadan ilk kasa olarak korunur. Güncellemeden sonra eski sürümü aynı veri klasöründe kullanma: eski sürüm yeni hesap dizinini/ortak mola kaydını tanımaz. İlk bağlantılar Instagram'ın erişim ve doğrulama koşullarına tabidir; çoklu hesap desteği giriş sınırlamasını kaldırmaz. [Doğrulama ve sınırlar](../docs/MULTI-ACCOUNT-TEST.md).

## Tema

Ayarlar → Tema: **Sistem / Açık tema / Koyu / OLED siyahı**. İlk açılışta Sistem seçilidir; sistem açık/koyu tercihi değişince arayüz de değişir. Açık/Koyu/OLED seçimleri sistemden bağımsızdır ve şifreli yerel ayarlarda kalır. OLED ana zemini siyah, kartları hafif ayrışan koyu yüzeylerdir. Tema tercihi ilk ekran için yalnız tema adını içeren yerel bir önbelleğe de yazılır; hesap bilgisi yazılmaz. Sistem değişimi CSS ile izlenir; yeni zamanlayıcı veya ağ isteği yoktur.

Menüler, formlar, grafikler, ilerleme çubuğu, bildirim günlüğü ve profil zaman tüneli ortak paleti kullanır. Profil fotoğrafları ters çevrilmez. Türkçe, English, Español ve Português (Brasil) desteklenir. Android 0.1.32'de ⋯ → Tema aynı seçenekleri sunar; web tasarım önizlemesi de aynı paletle yerel tercih kaydeder. Üç platform arasında ayar senkronizasyonu veya yeni web hesap bağlantısı eklenmedi. [Doğrulama kapsamı](../docs/APPEARANCE-TEST.md).

## Tarama ilerlemesi ve bekleme

Liste tararken alt durum çubuğu ilgili hesabı, takipçi/takip edilen yönünü, sıradaki sayfayı, alınan sayfa ve kaydedilen benzersiz hesap sayısını gösterir. Liste öncesindeki profil sayısı okuması ayrı belirtilir. Son profil sayısı bir referanstır; tarama sırasında değişebilir. Instagram toplam sayfayı güvenilir biçimde bildirmediği için `4/12` veya tamamlanma yüzdesi üretilmez.

İstekler arasındaki ayrılan 12–18 saniyelik rastgele aralıkta kalan saniye ve süre çubuğu görünür. Bu çubuk yalnız bekleme süresine aittir; bütün taramanın tamamlanma oranı değildir. Süre bitince sunucudan yanıt gelene kadar yanıt bekleniyor denir. Geri sayım ağ isteği göndermez; tepsiye gizlenme/küçültülmede durur ve açılışta güncel zamandan devam eder. İptalde geri sayım hemen kapanır; devam eden HTTP yanıtının mevcut zaman aşımı süresi beklenebilir. Alınmış kısmi kayıtlar korunur.

HTTP 429, normal saniyelik aralıktan ayrı **İstekler duraklatıldı** durumudur. Mola kaydındaki en erken saat ve İstek bilgileri bağlantısı gösterilir; otomatik yeniden deneme başlatılmaz. Saat erişim garantisi değildir. Dört uygulama dili desteklenir. [Doğrulama kapsamı](../docs/PROGRESS-FEEDBACK-TEST.md).

## Klavye ve ilk kullanım

- `Ctrl+K` veya `/`: kendi hesabın, kaydettiğin kişiler ve uygulama ekranlarında yerel arama paleti. `@` ile başlarsan yalnız profiller aranır. Grup ve görünen ad da aranabilir; Instagram'da yeni hesap araması yapılmaz. Yeni kişi için Kaydedilen Profiller → Kişi ekle kullanılmalı. Ok tuşlarıyla seç, Enter ile aç, Esc ile kapat.
- `Ctrl+R`: açık kişi ayrıntısındaki kayıtlı profili, Takip Listeleri'de seçili hesabı veya Özet'te kendi hesabını yeniler. Başka ekranda seçili profil yoksa yönlendirici mesaj gösterir. Yazı/form alanındayken, başka modal açıkken, işlem sürerken veya yerel mola varken yenileme göndermez. Kayıtlı olmayan liste üyesini kısayolla kendiliğinden kişilere eklemez. Mevcut istek sırası ve aralığı kullanılır.
- `1 / 2 / 3 / 4 / 5`: Özet / Kaydedilen Profiller / Takip Listeleri / Bildirimler / Ayarlar. Yazı yazarken veya modal açıkken sekme geçişi yapılmaz. Zaman çizelgesi menüde ve palet aramasında bulunur.
- `Esc`: en üst paleti, modalı veya kişi panelini kapatır. Altta açık bir form varken Ctrl+K ile palet açıp Esc ile kapatmak formdaki taslağı korur. Bu kısayol devam eden ağ işlemini iptal etmez; iptal düğmesi ayrıca kullanılır.

Kısayollar yalnız uygulama odaktayken çalışır; sistem genelinde tuş yakalama kaydı yoktur. Palet bir defada en fazla 12 sonuç gösterir. Toplam eşleşme daha fazlaysa sayı belirtilir; aramayı daraltabilirsin.

Kaydedilen Profiller ve Bildirimler'in boş durumları ilk adımı açıklayan rehber kartlarıdır. Bağlantısı olmayan kullanıcı doğru bağlantı formuna, bağlı kullanıcı kişi ekleme formuna yönlendirilir. Arama/filtrede sonuç bulunmaması tüm kayıtların boş olmasıyla karıştırılmaz; temizleme düğmesi sunulur. Okunmamış kayıt kalmadığında tüm bildirimlere dönüş düğmesi gösterilir. Bu kartları görüntülemek veya yerel arama yapmak hesap isteği başlatmaz, bildirim/otomatik kontrol tercihini değiştirmez. [Doğrulama kapsamı](../docs/KEYBOARD-ONBOARDING-TEST.md).

## Oturumsuz erişim karşılaştırması

Kişi ayrıntısındaki **Erişimi kontrol et**, önce bağlı hesabın sağlığını ve hedefin kayıtlı sayısal kimliğini inceler. Hedef için gerçek 404 alınırsa son bilinen kullanıcı adına ayrı bir oturumsuz profil sayfası isteği eklenir. Otomatik yol için Ayarlar → **Kaybolan kayıtlı takipçileri incele** ve **Otomatik profil kontrolü** açık olmalı; önceki ve son tamamlanmış kendi takipçi kayıtlarında kaybolan kayıtlı kişiler sıraya alınır. Normal profil kontrolü başına anonim istek gönderilmez. Sağlıklı oturum, hedef ve gerektiğinde anonim adım toplam üç istek olabilir; her biri ortak 12–18 saniyelik rastgele aralık rezervasyonuna uyar.

- Aynı sayısal ID anonim sayfada doğrulanır, bağlı oturumdan bulunamaz ve listede kaybolma kanıtı varsa **Güçlü engellenme şüphesi** gösterilir. Bildirim tercihi açıksa aynı inceleme olayında bir kez bildirim üretilebilir. Eski zayıf şüphenin güçlü kanıta yükselmesi bir kez ayrıca bildirilebilir.
- İki tarafta da bulunamazsa nedeni belirsizdir: dondurma, silme, ad değişikliği veya ad değişikliğiyle engelleme ayrıştırılmaz. Donduruldu/silindi diye kesin bildirim gönderilmez.
- HTTP 200 tek başına yeterli değildir. Giriş/doğrulama ekranı, kimlik uyuşmazlığı, bağlantı hatası, beklenmeyen sayfa veya 429 sonucu **Anonim kontrol tamamlanamadı** olarak gösterilir. Engellenme bildirimi üretilmez. Eski kayıtlar geriye dönük anonim doğrulanmış sayılmaz.

Kanıtlar kişi ayrıntısı, zaman çizelgesi ve güçlü şüphe bildirim günlüğünde açılır. Kontrol edilen ad, HTTP durumları, zaman ve yöntemin sınırları gösterilir. Kayıtlı hesap çerezi/Authorization gönderilmez; üçüncü taraf servis yoktur. Ham HTML ve dönen çerezler saklanmaz. Yönlendirme ve otomatik tekrar yoktur; anonim 429 da mevcut ortak molayı korur. Sayfanın yalnız JavaScript ile profil göstermesi halinde ham GET kimliği doğrulayamayabilir; bu belirsiz kalır. Güncel adı henüz hiç gözlenmemiş, adını değiştirip engellemiş kişi çözülemeyebilir. [Geliştirme notları](../docs/DEVELOPMENT-BACKLOG.md), [doğrulama kapsamı](../docs/ANONYMOUS-VISIBILITY-TEST.md).

## Profil zaman tüneli

Özet'te kendi profilini veya Kaydedilen Profiller'de kayıtlı kişiyi aç → **Profil zaman tüneli → Arşivlemeyi aç**. Başlangıçta kapalıdır; açarken elde bulunan son gözlem başlangıç kaydı olur. Sonraki normal profil kontrollerindeki bilinen biyografi ve fotoğraf değişiklikleri ayrı arşive kaydedilir. Bu ekranı açmak hesap API isteği göndermez. Galeri yalnız indirilebilen küçük JPEG'leri içerir; geçmişte indirilmemiş fotoğraflar üretilemez. Tarihler ilk/son gözlem zamanıdır; kesin değişiklik zamanı değildir.

Biyografide eklenen kelimeler yeşil ve `+`, kaldırılanlar kırmızı ve `−` ile gösterilir. Kaldırılan metin ayrıca üstü çizilidir. Biyografi okunamadığında önceki metin korunur; gerçekten boş gelen biyografi ayrı gözlemdir. Bu görünüm ilişki durumu gibi bir anlam çıkarsamaz.

Profil başına en fazla 200 değişiklik ve 24 farklı küçük görsel, toplamda 8 MiB görsel bütçesi vardır. Eski arşivler 366 sayaç gözlemi döndüğünde silinmez. Kapasiteye ulaşıldığında mevcut kayıtlar korunur, yeni kayıt/görsel için uyarı gösterilir. Aynı fotoğraf birden fazla kez görülse de tek kopya tutulur. **Arşivlemeyi durdur** eski kayıtları korur; **Arşivi temizle** onaydan sonra bu profilin arşivini siler ve kaydı durdurur. Normal profil gözlemleri ayrıca korunur. Otomatik önceki depo kopyası da temizlenir; daha önce dışa aktardığın yedekler değişmez.

Arşiv DPAPI ile şifreli yerel depodadır ve oturumsuz `.trackerbackup` yedeklerine dahildir. Android 0.1.31'de aynı özellik kişi ayrıntısında ve profil seçili zaman çizelgesindedir; ayrı Keystore kaydı kullanır, toplam 1,8 MiB bütçesi vardır ve fotoğrafları normal kullanımda yüklendikçe ekler. Web tasarım önizlemesi örnek kayıtlar gösterir.

## Kullanım

1. **Hesap bağlantısı:** kullanıcı adı ve şifreyi uygulamadaki alana gir. Desteklenen e-posta/2FA kodu istenirse aynı pencereden gönder. Kod beklemesi 5 dakika ve iptal edilebilir. Resmî Instagram güvenlik kontrolü istenirse orada tamamla. Parola otomatik tekrar gönderilmez.
2. Önceki Windows InstagramCounter prototipinde bir oturum varsa **Önceki Windows oturumunu kullan** görünür. Eski dosya değiştirilmeden kopya okunur; hesap kimliği canlı kendi profil yanıtıyla doğrulanmadan yeni depoya kabul edilmez. Uygulama açılışı önbelleği gösterir; otomatik parola girişi yapmaz. Önceden açılmış otomatik profil kontrolü varsa kayıtlı oturumla süre ve mola kurallarına göre devam eder.
3. **Özet:** Gözlemi yenile kendi profilini okur. Takipçi/takip edilen sayıları, varsa gönderi, biyografi, kullanıcı adı, görünür ad, bağlantı, gizlilik ve doğrulanma bilgileri kaydedilir. Profil fotoğrafında karşılaştırılabilir kimlik varsa değişim kaydedilir; yalnız CDN URL değişikliği fotoğraf değişikliği sayılmaz. Grafik ve geçmiş ilk gözlemden başlar.
4. **Kaydedilen Profiller:** kullanıcı adıyla profil ekle, favori/izleme durumunu, grup ve özel notunu düzenle. Liste üyesini yerel kaydetmek profil isteği göndermez. Ayrıntıdaki **Profili kontrol et** canlı gözlem alır; ayrıntıyı açmak ve aramak ağ isteği göndermez. Kaydedilen Profillerden kaldırma not/tercihleri kaldırır; eski gözlem ve liste kayıtları korunur.
5. **Takip Listeleri:** hesap ve yön seçip **Listeyi tara**. Takipçi ve takip edilen ayrı ayrı okunur; sayfalarda tek tek ilerleme, ortak 12–18 saniyelik rastgele aralık rezervasyonu ve iptal vardır. Tarama başındaki profil sayısı, kayıt sayısı ve sunucunun sayfalama sonu uyuşursa tamamlanmış gözlem kaydedilir. Sunucu kısıtlaması, belirsiz son, sayı uyuşmazlığı, döngü veya iptal kısmi kayıt bırakır; eski tamamlanmış kayıt korunur. Kısmi tarama açılışta sürdürülmez; yeni tarama baştan başlar. Bu ölçütler tarama boyunca değişen Instagram listesinin mutlak eksiksizliğini garanti etmez.
6. İki tamamlanmış kaydı seçip **Farkları göster**. Yeni görülen ve son kayıtta olmayan kimlikler ayrılır; bunlar kesin takipten çıkış/engelleme tespiti değildir. İki yön tamamlandıysa iki listede ortak veya diğer listede olmayan hesapları filtreleyebilirsin. Kayıt zamanları farklı olabilir. **CSV dışa aktar**, seçtiğin tamamlanmış kaydı dışa aktarır; CSV şifresizdir ve hesap oturumu içermez. Elektronik tablo formülü olarak yorumlanabilecek hücrelere başta tek tırnak eklenir.
7. **Zaman çizelgesi:** profil farkları, liste kayıtları, başarılı kontroller ve hatalar; eski/yeni değerler ve kayıt zamanı. Kayıt zamanı değişikliğin Instagram'da gerçekleştiği kesin zaman değildir.
8. **Ayarlar:** kompakt görünüm, Windows bildirimleri ve isteğe bağlı otomatik profil kontrolü. 1/5/15/30/60/180/360 dakika seçenekleri her profile ayrı uygulanır; kendi hesabın ve izlediğin kişiler, kendi süreleri dolduğunda 12–18 saniyelik rastgele istek aralığı rezervasyonuyla sırayla kontrol edilir. X ile pencereyi kapatınca sistem tepsisinde çalışmaya devam eder. Tepsi simgesine tıkla → pencere açılır; sağ tık → otomatik kontrol aç/kapat veya Tamamen çık. Tamamen çıkınca kontrol durur. Bilgisayar kapalı/uykuda veya internet yokken kontrol yapılamaz. Normal profil kontrolü tam liste taramaz; aşağıdaki isteğe bağlı kaybolan takipçi incelemesi bu konuda ek tarama yapabilir. Otomatik yeniden giriş yapılmaz. Sınırlama/doğrulama sonrası otomatik okumalar durur; önce elle başarılı profil kontrolü gerekir.

Windows bildirimini açmak, yalnız bu uygulamaya ait Başlat menüsü kısayolunu AppUserModelID ve ToastActivatorCLSID ile oluşturur/günceller. Farklı sahibin kısayolu üzerine yazılmaz; değişen eski kendi kısayolunun önceki kopyası veri klasöründe korunur. İşlem, [Electron'un Windows bildirim gereksinimine](https://www.electronjs.org/docs/latest/tutorial/notifications#platform-considerations) dayanır. Windows/odaklanma ayarları teslimi etkileyebilir; başarısız bildirim veri okuma hizmetini kapatmaz, değişiklik zaman çizelgesinde kalır.

## Gizlilik ve yerel kayıt

Mevcut ilk hesap `%LOCALAPPDATA%\InstagramTracker-Desktop\tracker.vault` içinde kalır; yeni hesaplar `accounts/<rastgele yerel anahtar>/tracker.vault` kullanır. Her kasanın ayrı `tracker.previous.vault` şifreli önceki kopyası vardır. Hesap dizini, cihaz tercihleri ve ortak mola `accounts.vault` içinde DPAPI ile şifrelenir. Atomik geçici dosyaya da yalnız şifreli içerik yazılır. Ayrı Electron kullanıcı dizini `%APPDATA%\InstagramTracker-Desktop` altındadır. Giriş parolası ve doğrulama kodu kaydedilmez; Instagram oturum ayarları ana arayüze gönderilmez. Oturumu kaldırmak önceki oturumlu depo kopyasını da kaldırır; bu uygulamanın gözlemleri korunur. **Yerel verileri sil** gözlemleri ve oturumu kaldırır, istek molasını korur. Eski InstagramCounter deposuna ve dışa aktarılan dosyalara dokunulmaz.

Şifreli `.trackerbackup` yedekler gözlem/not/tercih içerir; oturum içermez. Aynı Windows kullanıcısında açılmak içindir. Geri yüklemede önceki depo şifreli önceki kopyada korunur, otomatik kontrol kapatılır, oturum yeniden bağlanır ve daha uzun mevcut istek molası korunur. Bozuk depo sessizce sıfırlanmaz.

Hesap istekleri sabit HTTPS `i.instagram.com` hedefini kullanır. Profil fotoğrafı, oturum/çerez taşımayan ayrı bağlantıyla yalnız `cdninstagram.com` / `fbcdn.net` üzerinden yüklenebilir; küçük JPEG olarak yerel şifreli kayda alınır. Görsel yüklenmezse harfli avatar gösterilir. Aracı sunucu, telemetri ve bulut veritabanı yoktur. Arayüzün doğrudan ağ bağlantısı CSP ve Electron oturum politikasıyla kapalıdır; yalnız sınırlı, ana çerçevesi doğrulanan yerel IPC köprüsü vardır. Worker özel stdin/stdout borularıyla konuşur; localhost sunucusu açmaz.

API isteklerinde HTTP/2, TLS doğrulaması, 25 saniyelik taşıma zaman aşımı, kapalı yönlendirme ve kapalı otomatik tekrar kullanılır. Parola girişleri arasında en az 15 dakika beklenir. 429 sonrası yerel mola 1/2/4/8/16/24 saat ve varsa daha uzun Retry-After'dır. Bu süreler güvenli Instagram kotası veya erişim garantisi değildir.

Yerel kapasite: 200 kişi, profil başına son 366 gözlem, 2.000 olay, her hesap/liste yönü için son 6 tamamlanmış tarama ve son kısmi tarama; tarama başına 20.000 üye / 400 sayfa, şifreli depo için 64 MiB bütçe. Bunlar uygulama kaynak sınırlarıdır. Uzun listeler ortak sayfa aralığı ve yanıt süreleri nedeniyle uzun sürebilir. Her bağlı Instagram hesabı ayrı şifreli kasada tutulur; hesaplar arasında sol alttaki menüden geçilir.

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

Backend testi: `desktop/backend` içinde derleme Python'u ile `-m unittest discover -p test_*.py -v`. Gerçek Windows DPAPI ve üretim sınırları kullanılır; ağ taşıyıcısı sahtedir. `npm run check:functional` paketlenmiş EXE'yi geçici `TrackerQA-*` verisiyle açar; bu kip worker'da ağ/oturum aktarımını kapatır. Gerçek kullanıcı deposuna, eski prototip oturumuna ve gerçek Başlat menüsüne yazmaz. Rapor/ekran görüntüleri `artifacts/desktop-0.2.5-qa` altındadır.

Önceki Windows 0.2.1 doğrulaması: 39 çevrimdışı backend testi, 4 masaüstü zamanlayıcı/tepsi testi ve 23 paketlenmiş Windows kontrolü geçti. Canlı test, kayıtlı Windows oturumunun kabulünü, kendi profil ve takipçi/takip edilen listelerinin HTTP 200 ile okunmasını doğruladı. Takip Listeleri bu hesapta birer sayfaydı. Çok sayfalı akış, kısmi kapsam, kesinti, 429, kod devamı ve iptal çevrimdışı sınandı. Yeni parola/2FA girişi, başka hesaplar, uzun süreli oturum, gerçek Windows bildirim teslimi ve uzun süreli otomatik çalışma bu sürümde canlı doğrulanmadı. 1 dakikalık aralıkla pencere gizliyken iki gerçek profil kontrolü HTTP 200 ile geldi; istek başlangıçları arasında 60,11 saniye ölçüldü. Tek elle profil kontrolü bu bağlantıda 1,28 saniye, istekten fotoğraf aşamasına kadar yaklaşık 0,74 saniyeydi. Bu tek örnek ağ/hesap ölçümüdür; hız garantisi değildir. Bu sonuçlar Android girişinin veya tarayıcı sürümünün çalıştığı anlamına gelmez.

## Kontrol hızı ve sürüm geçişi

Windows 0.2.4 itibarıyla kontrol aralığı her profil için ayrıdır. Örneğin kendi hesabı dahil 3 profil ve 5 dakika seçildiğinde her profil yaklaşık 5 dakikada bir okunur; aralık profil sayısıyla çarpılmaz. Her profilin son başarılı gözlemi ve son otomatik denemesi ayrı değerlendirilir. Elle başarılı kontrol de o profilin süresini yeniler. Başarısız/iptal edilmiş otomatik deneme aynı profili hemen tekrar denetmez; deneme zamanı şifreli depoda yeniden başlatma boyunca korunur. Ana süreç süresi dolan en eski profili 5 saniyede bir değerlendirerek sırayla işler; API istek başlangıçları için 12–18 saniyelik rastgele aralık ayrılır. Uyku sonrası geçmiş turlar tekrar oynatılmaz. Çok sayıda profil, yanıt süresi, uzun liste taraması ve ek erişim incelemeleri hedef aralığı uzatabilir; seçilen süre garanti değildir. Ek inceleme işleri kendi aralıklarıyla ve normal profilleri sürekli ertelemeyecek şekilde sıraya girer. 429 molası ve oturum/doğrulama engelleri korunur.

Bir liste taraması, başlangıç profil okuması ve her sayfa için ayrı API isteği yapar. Sunucu sayfa başına 50 üye döndürürse 1.000 üyelik liste yaklaşık 20 sayfadır; 20 bekleme aralığı için ayrılan süre toplamda yaklaşık 4–6 dakika, beklenen ortalama 5 dakikadır; yanıt süresi ve zamanlayıcı gecikmeleri ayrıca eklenir. Bu koddan hesaplanan örnektir, 1.000 üyeli canlı ölçüm değildir.

Önceki otomatik kontrol ve aralık tercihin korunur; yeni aralık seçimi için Ayarlar → Kontrol aralığı kullan. 1/5 dakikalık ayarlar eski Windows 0.2.0 doğrulayıcısında yoktur; bu ayarları seçtikten sonra güncel 0.2.6 EXE kullan. Depo biçimi ve hesap verileri korunur. Windows başlangıcında otomatik başlatma eklenmedi.

## Engellenme şüphesi / Erişim kontrolleri

**Ayarlar → Kaybolan kayıtlı takipçileri incele** ile açılır. Otomatik çalışma için **Otomatik profil kontrolü**, Windows uyarısı için **Windows bildirimleri** de açık olmalı. Yeni seçenek kapalı gelir; önceki tercihler ve hesap kayıtları korunur.

Önce kendi takipçi listenin bir tamamlanmış başlangıç kaydını oluştur. Sonraki profil gözleminde takipçi sayısı düşerse, ayar açıkken yeni bir tam takipçi taraması sıraya girer. İki tamamlanmış kendi takipçi kaydında eksilen **Kaydedilen Profiller'de kayıtlı hesaplar** erişim inceleme kuyruğuna alınır. Tek başına sayı düşüşü veya kısmi liste kimseyi engellemiş saymaz. Başlangıç listesi yoksa Takip Listeleri'de eksik önkoşul görünür. Liste taraması yavaşsa diğer profil kontrolleri de bekler; her API isteği için ortak 12–18 saniyelik rastgele aralık rezervasyonu korunur.

İncelemede önce kendi profilin aynı bağlı oturumdan doğrulanır, sonra hedef **kayıtlı hesap kimliğiyle** okunur. İki istek arasında 12–18 saniyelik rastgele aralık ayrılır; gerçek bekleme daha uzun olabilir. Kullanıcı adı değişmişse yeni adıyla erişilebilir görünür; eski kullanıcı adına arama yapılıp yanlış kişiyle eşleştirilmez. Profil yanıtı görülebiliyorsa sayaçların eksik olması hedefi bulunamadı yapmaz. Bağlantı/timeout/401/403/5xx/doğrulama/429 ve kimlik belirsizliği engellenme şüphesi olarak bildirilmez.

Önceki tam listede olup sonraki tam listede olmayan kişinin profili, sağlıklı oturumdan HTTP 404 ile bulunamazsa **Engellenme şüphesi** kaydı ve bildirimler açıksa aynı başlıklı Windows bildirimi oluşur. Hesabı kapatma/silme, sizin uyguladığınız bir engel veya sunucunun erişim davranışı kesin ayrıştırılamaz; ürün kesin “seni engelledi” demez. API'deki `blocking` alanı sizin karşı tarafa engelinizdir; size engel olarak ters çevrilmez. Meta da [engellenen kişinin profili arayamayacağını ve engellemede bildirim gönderilmediğini](https://mydigitalworld.fb.com/mena/lesson/options-for-controlling-your-experience-on-instagram-facebook-messenger-and-whatsapp/) açıklar; bu belirtileri tek başına kesin teşhis kabul etmiyoruz.

**Takip Listeleri → kendi hesabın → Takipçiler** altında inceleme vakaları ve kanıtları; kişi ayrıntısında **Erişimi kontrol et**; zaman çizelgesinde **Erişim kontrolleri** filtresi vardır. Elle kontrol bir kayıtlı kişi veya liste üyesi üzerinde çalışır; tam liste yokluk kanıtı yoksa bulunamayan profil yalnız “Profile erişilemiyor; nedeni belirlenemedi” sonucudur. Şüphe bildirimi tıklanınca tek hedef varsa kanıt paneli açılır.

Aynı kaybolma için tek uyarı kaydı tutulur. Profil listede yeniden görünüp tekrar kaybolursa yeni bir inceleme olayı başlayabilir. Kayıt zamanı kesin engelleme saati değildir. Başarısız veya iptal edilen incelemeler otomatik tekrar edilmez; elle tekrar kontrol edilebilir. Tek hedef incelemesi profil sırasıyla dönüşümlü ilerler; ek liste/inceleme işleri toplam kontrol turunu uzatabilir. En çok 200 vaka ve vaka başına son 10 değerlendirme tutulur; geçmiş şüphe olayları o anki liste kanıtını ayrı olarak taşır. Eski şifreli yedekler migration ile açılır; eski EXE'ler yeni erişim değerlendirmelerini yönetmez, güncel sürümü kullan.

0.2.2 doğrulaması: 62 offline backend testi, 6 ana süreç/bildirim testi, 25 paketlenmiş EXE kontrolü geçti. Canlı bir kayıtlı profil erişimi ve kendi profil sağlığı HTTP 200 ile doğrulandı; toplam 15,66 saniye. Gerçek engellenmiş hesap ve Windows bildiriminin gerçek teslimi bu sürümde canlı doğrulanmadı. Engelleme/takip ilişkisi oluşturmak veya değiştirmek için işlem yapılmadı.

## Hesaba ve türe göre bildirimler · 0.2.3

Kişi ayrıntısındaki **Bildirim tercihleri** düğmesinden veya **Ayarlar → Hesaba göre bildirim türleri → hesabı seç → Türleri seç** yolundan açılır. Kendi hesabın için de ayrı seçim yapılabilir. Hesabı tamamen sessize alabilir veya yalnız istediğin alanları seçebilirsin: kullanıcı adı, görünen ad, biyografi, profil fotoğrafı, takipçi/takip edilen/gönderi sayısı, bağlantı, gizlilik, doğrulanma, liste taraması, engellenme şüphesi. Önceden özel tercih yoksa türler açık kabul edilir; genel **Windows bildirimleri** anahtarının önceki durumu korunur. Alanları değiştirmek ağ isteği göndermez.

Örnek: Arda için kullanıcı adı açık, takipçi sayısı kapalı olsun. Bir kontrolde kullanıcı adı, biyografi ve takipçi sayısı değişirse seçili alanlar için **tek Windows bildirimi** oluşturulur. Tıklanınca aynı kontrolün bütün değişiklikleri eski/yeni değerleriyle uygulama içinde açılır. Kapalı takipçi değişikliği de **Yalnız kayıtta**, seçili değişiklikler **Bildirim için seçili** etiketiyle görünür. Bildirimi kapatmak gözlem, zaman çizelgesi veya log kaydını silmez.

Sol menüde **Bildirimler** veya üstteki zil, kontrol başına tek satır olan kayıtları açar. Kayda tıklamak okundu olarak işaretler; **Okunmamış** filtresi ve zil göstergesi vardır. Okundu ve hesap tercihleri şifreli saklanıp yeniden açılışta/yedekten yüklemede korunur. Bildirim kaydındaki alan seçimi ve değerler gözlem anına aittir; sonraki tercih/kullanıcı adı değişiklikleri geçmişi yeniden yazmaz. Son **500 grup**, mevcut 64 MiB ortak şifreli depo bütçesinde tutulur.

Bu sürümden önceki zaman çizelgesi korunur; eski olaylardan yapay, geriye dönük Windows bildirimi/log üretilmez. İlk gözlem veya değişimsiz kontrol yeni bildirim oluşturmaz. Windows bildirimleri genel kapalıyken de yeni değişiklik log'u oluşur. “Windows bildirimi için seçildi” teslim edildi anlamına gelmez; Windows/odaklanma ayarları teslimi etkileyebilir. Takip Listeleri ve engellenme şüphesi önceki kapsam/mola kurallarını korur; şüphe kesin engelleme kanıtı değildir.

28 Ekim doğrulaması: **78** offline backend testi, **9** ana süreç/bildirim testi ve **27** gerçek paketlenmiş EXE kontrolü geçti. Gerçek mevcut hesapla açılışta kayıtlar ve genel tercihler korundu; 12 türün penceresi açıldı, API istek sayısı artmadı. Birden çok gerçek Instagram alanını bu sürümde değiştirmedik; gruplama/filtreleme, click IPC ve kalıcı log üretim sınırında sentetik verilerle doğrulandı. Gerçek Windows toast teslimi/tıklaması ayrıca canlı doğrulanmadı.

## Arka plan kaynak kullanımı · 0.2.5

Pencere gizli veya küçültülmüşken ara durum/ilerleme/sonuçlar renderer’a gönderilmez. Gözlemler, şifreli kayıt ve bildirim seçimi ana süreç/worker’da sürer; pencere açılınca yalnız güncel durum bir kez gönderilir. Kod beklemesi, hizmet hatası, son bildirim uyarısı ve tıklanan kayıt korunur. Tepsi menüsü yalnız hazır/meşgul/otomatik/mola durumu değiştiğinde yeniden oluşturulur. Arayüz ve Python hizmeti bellekte tutulur; bu çalışma bir renderer kapatıp yeniden açma değişikliği değildir.

Ölçüm: `node scripts/profile-desktop.mjs 0.2.5` ve `--large`. Gerçek paketlenmiş EXE, yalnız sentetik TrackerQA deposu ve ağsız worker kullanır. Üç saniye ısınma sonrası görünür boşta, gizli boşta ve gizli 40 yerel şifreli not güncellemesi için 20’şer saniye ölçer. PowerShell gözlemcisi uygulama ailesinin CPU süresini, 500 ms örneklemeyle toplam working set ve private bytes tepesini kaydeder; gözlemci uygulama maliyetine dahil değildir. Working set toplamı paylaşılan sayfaları birden fazla sayabilir; private bytes fiziksel RAM değil ayrılmış bellektir. Sonuçlar `artifacts/desktop-0.2.5-qa/performance*/summary.json`; kısa ısınmış örnekler saatler süren sızıntı veya zayıf cihaz garantisi değildir.

## Türkçe / English · 0.2.6

**Ayarlar → Uygulama dili** anında Türkçe veya İngilizceye geçer; tercih şifreli depoda yeniden açılış ve oturumsuz yedek boyunca korunur. Eski depolar Türkçe varsayımıyla açılır. Ekranlar, erişilebilirlik etiketleri, tepsi menüsü, uygulamanın dosya iletişimleri, yeni bildirim metinleri, hata gösterimi ve tarih/sayı biçimleri seçime uyar. Sistem dosya seçicisinin Windows tarafından sağlanan düğmeleri sistem dilindedir. Depo açılamayıp dil tercihi okunamazsa başlangıç hatası varsayılan Türkçedir.

Dil değişimi yeni hesap kontrolü sayılmaz; mevcut son hata bilgisi korunarak çevrilir. Geçmiş başlıkların gösterimi ve bildirim türü etiketleri seçilen dile uyarlanır; kaydedilmiş kullanıcı adı, biyografi, not, eski/yeni değerler ve bildirim seçimi yeniden yazılmaz. Dil değiştirmek ağ isteği, oturum yenileme veya mola sıfırlama başlatmaz. Çeviriler yerel pakettedir. Sayı/tarih biçimlendiriciler dil başına önbelleğe alınır.

Web örnek arayüzünde **Ayarlar → Arayüz dili** aynı iki seçeneği sunar; tarayıcı tercihi ayrı saklanır. Android'de mevcut **⋯ → Dil / Language** seçimi korunmuştur; bu sürümde APK/kaynak değişmedi.
