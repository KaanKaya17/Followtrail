# Android 0.1.34 — WebView 429 kaynağının ayrılması

29 Ekim 2026. Bu sürüm girişin başarılı olduğuna dair bir beyan değildir. Mevcut 429 molasını, hesap kaydını veya giriş taşımasını değiştirmeden tanının güvenilirliğini düzeltir. Telefonun özel tanı ayrıntıları yerelde kalır; Git'e veya sürüm eklerine alınmaz.

## Kök neden ve düzeltme

0.1.33'te `MainActivity.onReceivedHttpError`, Instagram kaynaklarından birinde 429 gördüğünde genel sınırlama politikasını uyguluyordu. A adımı sürüyorsa hatanın URL'sini etkin profil isteğiyle eşleştirmeden W01'e LIMITED / 429 yazıyordu. Böylece sayfa kaynağı hatası profil yanıtı gibi görünebiliyor, son sayaçlı HTTP satırında yanıt bulunmamasıyla da çelişkili bir izlenim oluşuyordu. Bu kaynak incelemesi ve sentetik UI geri çağrısıyla yeniden üretilen bir sınıflandırma hatasıdır; gerçek sunucu 429'unun nedenini açıklamaz.

Etkin profil isteğinin sahibi `BrowserProfileProbe`, gözlenen URL'nin sabit HTTPS origin, hesap yolunun tam eşleşmesi, query/fragment/userinfo yokluğu ve etkin işlemle ilişkisini artık kendisi denetler. Eşleşen 429, fetch sonucuyla aynı tamamlanma yoluna girer: sayaçlı HTTP kaydı, W01 ve mola bir kez güncellenir; lease/JS slotu temizlenir. Başka kaynak hatası bu sahipte profil yanıtına çevrilmez.

Başka bir WebView kaynağının 429'u W00 PAGE olarak tutulur; etkin profil okuması iptal edilir. Profil için tamamlanmış HTTP sonucu uydurulmaz ve W02 başlatılmaz. Genel 429 durdurma politikası korunur; alternatif uç, otomatik yeniden deneme veya mola kaldırma eklenmedi. Sinyal artık sayfa kaynağı olarak açıklanır. İstek bilgileri'ndeki genel satırın adı **Son sayaçlı işlemin HTTP yanıtı** olarak netleştirildi; sayaç dışındaki web sayfası trafiğiyle karıştırılmaz.

## Kayıt biçimi ve uyumluluk

- W00: Sayfa kaynağının 429'u. Bu kayıt tek başına profil endpoint'inin reddedildiğini kanıtlamaz.
- W01: Etkin tarayıcı profil isteğinin doğrulanan sonucu veya açık iptali.
- W02: Uygulama okuyucusunun sonucu. Olmaması, tamamlanmış ikinci sonuç bulunmadığı anlamına gelir.

Yeni turlar sabit format 2 işaretçisi kullanır. 0.1.33 kayıtları korunur; eski 429 kaydının kaynak ayrımının belirsiz olduğu açıklanır. Geçmiş bir W01/429, yeni formatın kesin profil kanıtıymış gibi yeniden etiketlenmez. Yeni karşılaştırma turu ancak kullanıcı açtığında ve mevcut mola/aralık izin verdiğinde başlar. Kayda yalnız sabit aşama/sonuç, saat, HTTP ve sabit format numarası girer; URL, hesap kimliği, kullanıcı adı, parola, kod, çerez veya gövde saklanmaz.

## Doğrulama

- 84 JVM birim testi, release/debug/test derlemeleri ve release lint geçti. Lint: 0 hata, 14 mevcut uyarı. Türkçe/İngilizce kaynak ve yer tutucu kontrolü geçti.
- 21 yeni browser-profile probe ve önceki 13 browser probe JavaScript durumu geçti; HTTP açılmaz, sahte fetch/stream kullanılır.
- Ayrı debug paketinde API 37 emülatörü: WebComparisonBoundaryTest 5, WebComparisonScreenTest 6, WebConnectionTest 7, WebLoginScreenTest 4; toplam 22 sınır/UI testi. Gerçek profil fetch'inin 429'u için W01 ve sayaçlı HTTP'nin birlikte 429 olması ve mola sayacının bir kez artması sınanır. Farklı sayfa URL'sinin 429'u üretim WebViewClient geri çağrısına enjekte edilir; W00 LIMITED/429, W01 CANCELLED/HTTP yok, W02 yok, ana hesap yok ve sayaçlı HTTP yok doğrulanır. Bu ikinci test gerçek Instagram alt kaynak yanıtının yakalandığı iddiası değildir.
- Eski metadata kaydı işaretçisiz yeniden okunur: HTTP ve saat değişmez; kaynak belirsizliği görünür olur. Yeni tur açıkça başlatılana kadar eski kanıt yeniden yazılmaz. Önceki bağlantı/şifreli staging/iptal/429 testleri de korunur.

Canlı hesaba yeni giriş/profil isteği başlatılmadı. Cihazdan yalnız açık uygulama tanısı okundu; root veya çerez/veri çıkarma kullanılmadı. Otomatik testler kullanıcının telefonu yerine sahip olunan emülatörde sentetik oturumlarla çalışır. İmzalı üretim APK'sı paket/izin/debug-fixture denetiminden geçirilir. Windows 0.2.11 ve web tasarım önizlemesi değiştirilmedi.

## Sonraki telefon testi

Mevcut mola sürerken yeni test yok. Süre dolduğunda erişim garanti değildir. Kullanıcı aynı **⋯ → Gelişmiş → Web bağlantısını karşılaştır** yolunu kullanır; A ve yalnız başarılıysa B düğmesini birer kez başlatır. İlk hatada durur. İstek bilgileri'ndeki W00/W01/W02, sonuç ve HTTP satırları yeterlidir; parola/kod/çerez veya kişisel profil içeriği paylaşılmaz. Bu tanı, sonraki düzeltmenin hangi üreticiye ait olduğunu belirlemeye yardımcı olur; giriş sorununun çözüldüğü kabulü değildir.
