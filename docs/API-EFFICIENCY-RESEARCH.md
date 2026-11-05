# API verimliliği ve alternatif veri yolları

5 Kasım 2026 · Kaynak tabanı: `67dcae1` · Windows 0.2.53 / Android 0.1.83.

Bu belge ilk araştırmanın kaydıdır. Sonraki Windows 0.2.54 / Android 0.1.84 uygulaması ve ölçülen paylaşım sonuçları [doğrulama notundadır](API-EFFICIENCY-TEST.md).

Bu incelemede gerçek hesaba yeni istek gönderilmedi, bekleme kayıtları değiştirilmedi ve uygulama davranışı değiştirilmedi. Bulgular kaynak incelemesi, masaüstünün üretim sınırında sentetik taşıyıcıyla yapılan ölçüm ve birincil kaynak araştırmasına dayanıyor. Meta'nın özel uç noktalarındaki gerçek kotası, güvenli istek sıklığı ve Android'deki 429'un kesin nedeni bu çalışmayla belirlenmiş değildir.

## En büyük bulgu: sıklık, günlük bütçeyle birlikte planlanmalı

[Ortak politika](../design/request-policy.json) hesap başına 120/saat ve 1.200/24 saat; cihaz başına 240/saat ve 2.400/24 saat sınırı koyuyor. Bunlar uygulamanın yerel bütçeleri; Meta tarafından verilmiş kota değildir. 6–10 saniyelik aralık yalnız isteklerin birbirine yakınlığını düzenler, günlük iş miktarını azaltmaz.

24 saat açık bilgisayarda, kendi hesabı dahil toplam `P` profil ve `m` dakikalık aralık için yalnız profil okuması talebi yaklaşık `P × 1440 / m` olur. Gecikmeler, kapalı uygulama ve bütçe durmaları gerçekleşen sayıyı düşürebilir:

- 1 profil, 1 dakika: 1.440/gün; listeler olmadan bile yerel günlük bütçenin üstünde.
- 3 profil, 5 dakika: 864/gün; listeler, arama, doğrulama ve diğer işler için 336 kalır.
- 10 profil, 5 dakika: 2.880/gün; ayrıca 120/saat ile saatlik bütçenin tamamını tüketir.

Öneri: kalan hareketli saatlik/günlük bütçeyi, vadesi gelen işleri ve tahmini liste sayfalarını birlikte değerlendiren planlayıcı. Kullanıcıya seçtiği aralığın mevcut iş yüküyle sürdürülebilir olup olmadığını gösterelim. Öncelik seçimi yapılabilsin; kontrol sıklığını sessizce değiştirmeyelim. Örneğin günlük bütçenin %20'si diğer işlere ayrılırsa yalnız profil okumaları için teorik alt aralık `1,5 × P` dakikadır: 10 profil için 15 dakika. Bu bir Meta güvenlik garantisi veya kesin bildirim süresi değildir; büyük listeler daha fazla pay gerektirir.

## Halihazırda yapılan tasarruflar

- [Windows profil paneli](../interface/web/profile-details.js) 20 profillik, gözlem zamanından itibaren **5 dakikalık** önbellek kullanıyor. Aynı profilin devam eden okumasını paylaşabiliyor; açık yenileme önbelleği atlıyor. Yalnız backend `profile_detail` fonksiyonuna bakmak, kullanıcı her panel açtığında istek gidiyormuş gibi yanlış sonuca götürürdü.
- Android [ViewedProfiles](../app/src/main/java/dev/kaankaya/instagramtracker/ViewedProfiles.kt) 20 kayıtla sınırlı, süreç içi ve oturuma bağlı. [PeoplePolicy](../app/src/main/java/dev/kaankaya/instagramtracker/Models.kt) üzerinden panel tazeliği **15 dakika**. Bu, otomatik kontrol aralığıyla aynı ayar değil. Platformların panel tazelik politikası şu anda farklı; ortaklaştırılabilir.
- Aramada 650 ms yazma beklemesi, aynı sorgu için 60 saniyelik sınırlı önbellek ve son sorgunun öncelenmesi mevcut: [arayüz](../interface/web/profile-search.js), [ortak sınırlar](../design/search-cache.json), [backend](../interface/desktop/backend/search_cache.py).
- [Liste planı](../interface/desktop/backend/list_preparation.py) sayılar değişince erken, diğer durumda günlük tam kontrol yapıyor; sayfalama kaldığı yerden sürebiliyor. Sayı aynıyken biri çıkıp biri girebileceğinden günlük tam kontrolü kaldırmak doğru olmaz. Eksik yanıt tekrarları sınırlı; eski tam liste korunuyor.
- [Hikâye akışı](../interface/desktop/backend/engine.py) kaydedilmiş hikâye kimliklerinde medya indirmeyi atlıyor. Takvimi açmak yerel işlem; açık hikâye kontrolü yeni akış sorgusu yapıyor. Medya/CDN indirmeleri de uygulamanın ortak bütçesine katılıyor: [medya sahibi](../interface/desktop/backend/stories.py). Muhasebede bunları ayrı göstermek faydalı; sayacı ayırmak gerçek ağ isteğini azaltmaz.
- Profilde değişmeyen görsel yeniden kullanılıyor. Liste satırları için mevcut fotoğraf bilgisi kullanılabiliyor; her satıra ayrı profil API sorgusu gerekmiyor.

## Yeni kazanç: aynı güncel profil bilgisini tekrar okumamak

[Engine.scan](../interface/desktop/backend/engine.py) yeni bir takipçi veya takip edilen taramasının başında profil sorguluyor. Hemen önce profil zaten kontrol edilmiş olsa da iki yön bağımsız yeni okumalar yapıyor. Devam sayfaları bunu tekrar etmiyor. Örtüşen sayfaları onarma sonunda yapılan ek profil kontrolü ise ayrı bir doğruluk kontrolü; körlemesine kaldırılmamalı.

Android [AutomaticEngine](../app/src/main/java/dev/kaankaya/instagramtracker/AutomaticEngine.kt) bazı otomatik akışlarda mevcut profili `ListBasis` olarak zaten paylaşıyor. Dolayısıyla iki platformda aynı optimizasyonun eksik olduğunu söyleyemeyiz.

Windows'ta kısa süre önce başarıyla okunmuş, aynı hesap ve bağlantıya ait profil; otomatik taramanın başlangıç sayısı olarak kullanılabilir. Örneğin 60 saniyelik tazelik penceresiyle başlanabilir. Bu süre öneridir, uygulanmış politika değildir. Uzun taramada süre dolduğunda yeniden okuma gerekir. Kullanıcının açık yenileme talebi, sayım uyuşmazlığındaki doğrulama ve oturum geçerliliğini sınayan bağımsız kontrol korunmalı.

Kimlik, bağlantı ve son gözlem zamanının tek sahibi olmalı. Önbellekten okuma yeni gözlem/bildirim üretmemeli; eski `observedAt` korunmalı. Hesap değişimi, yeniden bağlantı ve silme önbelleği geçersizleştirmeli. Bekleyen aynı kimlikli işler, başarıyla alınan güncel sonuçla karşılanabilir; eski başarısızlık/429 başarı gibi önbelleğe alınmamalı.

### Tekrarlanabilir çevrimdışı ölçüm

Çalıştırılan komut: `C:\Users\m3rt1\Documents\InstagramCounter\.venv\Scripts\python.exe scripts/audit-api-efficiency.py`.

[Script](../scripts/audit-api-efficiency.py), gerçek Engine ve sayfalama/kota sınırını sahte yanıtlarla çalıştırır. Her örnekte bir profil yenileme ve ardından iki tam liste taraması vardır; sayfalar tam 50 kayıt döndürür, tekrar/eksik kayıt yoktur. Geçici şifreli kasa kullanılır; gerçek kasa açılmaz.

- **50 takipçi + 50 takip edilen:** ölçülen 3 profil + 2 liste = **5 istek**. Aynı profil temeli iki yön için de kullanılabilirse aritmetik alt sınır **3**.
- **82 + 50:** ölçülen 3 profil + 3 liste = **6 istek**. Aynı koşuldaki alt sınır **4**; bu kısa senaryoda potansiyel %33 azalma.
- **3.000 + 2.000:** ölçülen 3 profil + 100 liste = **103 istek**. İdeal alt sınır **101**; yaklaşık %1,9. Kısa önbellek süresi uzun tarama sırasında dolabileceğinden bu alt sınıra ulaşılacağı iddia edilmiyor.

Sonraki sayfaların ilerlemesi ve iki listenin tam kayıt sayıları script tarafından doğrulandı. Sabit sentetik 8 saniyelik aralıkla sanal bekleme toplamları sırasıyla 32, 40 ve 816 saniye; bunlar gerçek internet süreleri değil. Üç örneğin birlikte gerçek çalışma bölümü 7,0756 saniye duvar süresi, 2,6094 saniye işlemci süresi ve Python süreci için 60.043.264 bayt (57,3 MiB) tepe çalışma kümesi verdi. Zaman ölçümü modül yüklemesinden sonra başlar; bellek süreç genelinin tepesidir. Süreç ailesi, gerçek ağ/TLS, soğuk/sıcak karşılaştırma, Android çalışma zamanı ve ajan token maliyeti ölçülmedi. Üretim optimizasyonu uygulanmadığından bir önce/sonra hızlanma ölçümü yoktur.

Ayrıca `node --test interface/desktop/profile-details.test.cjs` çalıştırıldı: **6/6 geçti**. Önceden mevcut önbellek, devam eden isteği paylaşma, hesap değişimi, iptal ve 429'da otomatik tekrar etmeme davranışları doğrulandı. Bu testler gerçek Meta kotasını doğrulamaz.

## Alternatif veri yolları

### Resmî dışa aktarma dosyası: çevrimdışı başlangıç için değerli

Meta, Instagram ve Facebook bilgilerinin Accounts Center üzerinden indirilebildiğini açıklıyor. Bu yol, kullanıcı kendi takipçi/takip edilen dosyalarını sağladığında başlangıç verisini canlı liste sorgusu olmadan içeri almak için adaydır. [Meta'nın açıklaması](https://about.fb.com/news/2023/10/manage-your-information-across-apps/).

**Öneri; mevcut özellik değil:** ZIP/JSON'u cihazda ayrıştıran, yalnız seçilmiş bağlantı dosyalarını okuyan içe aktarıcı. Güncel gerçek dışa aktarma örneği bu incelemede açılmadı; format, dosya tarihi ve kapsama doğrulaması uygulama öncesinde gerekli. Arşivde hesap ID'si yoksa kullanıcı adlarını ID varmış gibi kabul etmeyelim; her satıra ID sorgusu eklemek tasarrufu yok eder. Eski dosyayı bugünün tam listesi saymayalım. Bu, başka kişilerin güncel listelerini veya yüklenmeden önceki tüm takipten çıkış geçmişini otomatik sağlamaz; canlı izlemenin yerine geçmez.

### Resmî Instagram API: uygun hesaplar için ayrı bir ürün yolu

Meta'nın yayımladığı koleksiyon Instagram Login akışını profesyonel işletme/üretici hesapları için tanımlıyor; profil, medya ve istatistik özellikleri bu kapsamda değerlendirilebilir. Facebook Login akışında bağlı Facebook Sayfası şartı ve tüketici hesaplarına erişememe sınırı ayrıca belirtiliyor. [Meta Instagram API koleksiyonu](https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api?entity=request-23987686-af579d08-121e-4897-8f45-5fd41ace49df).

Bu erişimi kişisel hesapların veya izlenen diğer kişilerin tüm takipçi listesinin birebir alternatifi olarak doğrulayamadım. Desteklenen olaylarda webhook kullanımı ayrı bir inceleme konusu olabilir; genel takipçi/takipten çıkma bildirimi varmış gibi tasarlanmamalı. Sürekli erişilebilir webhook sunucusu, uygulamanın yalnız yerel çalışma tercihine de yeni mimari gerektirir. Geliştirici dokümanlarının bazı doğrudan sayfaları araştırma aracına 429 verdi; özel alan ve izinlerin eksiksiz güncel sözleşmesi doğrulanmış sayılmıyor.

### Tarayıcı üzerinden yerel yardımcı: mümkün bir deney, kota çözümü değil

Masaüstünde kullanıcı Instagram'ı normal tarayıcısında kullanırken açıkça izin verdiği veriyi yerel olarak uygulamaya aktaran bir eklenti değerlendirilebilir. Hazır görülen veriyi yeniden sorgulamadan kullanmak ek istekleri azaltabilir; aktarılabilir verinin gerçekten dönen alanlar, kimlik ve kapsamıyla doğrulanması gerekir. Tek bir açılmış liste tüm listeyi temsil etmez. Geniş alan izinleri, çerez aktarımı veya ham yanıt arşivi gerektirmeyen bir prototip tercih edilmeli. Chrome, eklentilerde alan izinlerini ve aynı-kaynak sınırlarını belgeliyor. [Chrome ağ erişimi belgesi](https://developer.chrome.com/docs/extensions/develop/concepts/network-requests).

Android'de Custom Tabs tarayıcı oturumuyla paylaşım sağlasa da gömen uygulamanın sayfa ve oturum bilgisine erişimi özellikle sınırlı. Yalnız Custom Tab'a geçmek, Instagram çerezini ve tüm veriyi uygulamaya otomatik aktarmaz. [Chromium güvenlik açıklaması](https://chromium.googlesource.com/chromium/src/+/main/docs/security/custom-tabs-faq.md). Resmî yetkilendirme dönüşü bulunmayan kişisel liste erişiminde bu tek başına giriş çözümü değildir.

Kendi APK'mızı değiştirmek önbellek, iş birleştirme, durum yönetimi ve ölçüm eklememizi sağlar. Başka APK'yı değiştirmek veya farklı taşıyıcı kullanmak sunucunun daha çok erişim vereceğinin kanıtı değildir. Mevcut tarayıcı/native karşılaştırması ileride aynı uç nokta, aynı hesap ve tek kontrollü okuma ile sınanabilir; 429 sonrasında uç nokta değiştirerek tekrar zinciri oluşturulmamalı.

## Az kazandıran veya doğrulanmamış yollar

- Paralel/çoğaltılmış istek gerçek iş miktarını azaltmaz. Toplu GraphQL veya HTTP/2 ile tek bağlantı kullanımı, sunucunun alt işlemleri tek kota saydığı anlamına gelmez. Mevcut özel uç noktalar için genel toplu sorgu sözleşmesi doğrulanmadı.
- `count` büyütmek ancak sunucu gerçekten daha fazla benzersiz kayıt döndürürse kazançtır. [Önceki 50/100/200 denemelerinde](LIST-PAGE-SIZE-PROBE.md) küçük profilde her sayfa 25 kişi, büyük profilde her başlangıç sayfası 50 kişi ve açık kapsam kısıtı döndü. Daha büyük sayı istemek bu örneklerde kazanç sağlamadı; bu incelemede yeniden canlı denenmedi. Sunucunun kısıtlı verdiği listeyi hızlandırma veya önbellekle eksiksiz yapamayız; eksikliği doğru göstermeyi sürdürmeliyiz.
- ETag/304, sıkıştırma ve bağlantı havuzu bayt veya bağlantı kurma maliyetini azaltabilir; istek sayısını azaltmış sayılmaz. Bu uç noktalarda kullanılabilir doğrulayıcılar gözlenmeden varsayılmamalı. Bağlantı havuzu hesap/çerez izolasyonu korunarak ayrıca ölçülebilir.
- Aramada kısa sorgunun ilk 20 sonucunu daha uzun sorgunun eksiksiz sonucu saymak hatalıdır. Kayıtlı kişilerde yerel arama faydalı; uzaktaki farklı sorguların kapsama sınırını korumalı.
- Bir profil sayısının değişmemesi, listenin aynı kaldığına kanıt değildir. İlk sayfa aynı diye kalan sayfalardan vazgeçmek de eksiksiz fark hesabı sağlamaz.

429'un hangi kimlik ve sayaçla uygulandığını HTTP standardı belirlemez; Retry-After da zorunlu değildir. Bu nedenle yerel bütçeyi artırmak veya bekleme bitişini görmek, sunucu erişiminin açıldığını kanıtlamaz. [RFC 6585 bölüm 4](https://www.rfc-editor.org/rfc/rfc6585#section-4).

## Önerilen uygulama sırası

1. **Harcama görünürlüğü ve bütçe tahmini:** profil, liste, arama, giriş ve medya taleplerini değer/kimlik/ham yanıt kaydetmeden sınıflandırmak. Gerekli toplam işin bütçeye sığıp sığmadığını önceden hesaplamak. Başarı ölçütü: aynı kapsamda daha az gerçek istek ve daha az bütçe nedeniyle bekleme.
2. **Tek profil sonucu, birden çok tüketici:** Windows yeni liste başlangıcında güncel bilgiyi paylaşmak; Android'in mevcut ListBasis yaklaşımıyla ortak tazelik/kimlik sözleşmesi oluşturmak. Aynı işlem için devam eden veya yeni tamamlanan okumaları birleştirmek. Kimlik, hesap değişimi, eski yanıt, iptal ve 429 regresyonları zorunlu.
3. **Kullanıcı tercihiyle öncelikli izleme:** önemli profiller ve kullanıcı işlemlerine pay ayırmak; arka plan işlerini adil dağıtmak. Günlük tam liste kontrolünü ve eşit sayılı değişiklikleri saptama gereğini korumak. Daha seyrek kontrolün bildirim gecikmesini açıkça göstermek.
4. **Yerel resmî dosya içe aktarma:** gerçek dışa aktarma örneğiyle format/kapsam/tarih doğrulamasından sonra başlangıç yükünü azaltmak. Windows ve Android aynı kuralları paylaşmalı.
5. **Ayrı araştırma prototipleri:** profesyonel hesap API'si ve isteğe bağlı tarayıcı yardımcısı. Desteklenen veriler kanıtlanmadan mevcut giriş veya liste akışının yerine koymamak.

Bu sıranın ilk iki adımı uygulamanın mevcut işlevlerini koruyarak somut kazanç hedefler. Genel bir yüzdeyle tüm uygulamanın hızlanacağını veya 429'un biteceğini söylemek için canlı, izinli ve eşdeğer kapsamlı önce/sonra ölçümü henüz yok.
