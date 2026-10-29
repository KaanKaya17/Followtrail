# Windows 0.2.14 — Rastgele istek aralığı

Sayaçlı Instagram API ve oturumsuz profil isteklerinin mevcut ortak kapısı, sabit 15 saniye yerine **12–18 saniye arasında eşit olasılıklı bir süre** seçer. Dağılımın beklenen ortalaması 15 saniyedir; gerçekleşen kısa örneğin ortalaması veya gerçek istek başlangıçları için 15 saniye garantisi yoktur. Alt sınır 12 saniyeye iner. Değerler `engine.py` içindeki `REQUEST_SPACING` tanımında merkezidir; yeni ayar ekranı eklenmedi. Bu düzenleme 429 veya bot tespitinden korunma garantisi değildir.

Her giden istekten önce tek süre seçilir ve `nextAt = lastAt + seçilen süre` rezervasyonu şifreli kaydedilir. Mevcut rezervasyon dolmadan süre yeniden seçilmez; ekran güncellemesi, profil kontrol ayarı, hesap geçişi, yeniden açılış veya iptal bunu değiştirmez. Eski sürümün daha uzun rezervasyonu da kısaltılmaz. İstekler arasında zaten yeterince boş zaman geçmişse ek bir ilk bekleme eklenmez; o isteğin başlangıcında sonraki rezervasyon ayrılır.

Hesaplar ortak kapıyı ve tek worker sırasını kullanır. 429/Retry-After, 1–24 saatlik yerel mola, 15 dakikalık parola giriş aralığı, 429 sonrası otomatik kontrolü durdurma ve yerel molayı yeni HTTP yanıtı gibi saymama davranışı korunur. Profil başına seçilen 5 dakika gibi aralıklar değişmez. Ağ yanıtı, kuyruk ve arka plan koordinatörünün 5 saniyelik değerlendirmesi gerçek başlangıç aralığını uzatabilir. Seçilen 18 saniye, gerçek beklemenin üst sınırı değildir.

Türkçe/İngilizce istek, erişim ve zamanlama açıklamaları min/max değerlerini backend'in `requestPacing` alanından alır. İlerleme çubuğu mevcut mutlak `until` zamanını kullanır; yeni periyodik zamanlayıcı veya yeniden deneme eklenmedi. Fotoğraf CDN yüklemeleri bu sayaçlı API kapısının kapsamında değildir. Yeni sunucu, izin, telemetri veya kullanıcı verisi aktarımı yoktur.

## Çalıştırılan doğrulama

Tüm kontroller ayrı `TrackerQA-*` dizinlerinde sentetik hesap/zaman/HTTP ile çalıştı. Windows DPAPI, atomik kayıt, paketlenmiş worker ve Electron IPC gerçektir. Gerçek kullanıcı kasası açılmadı; Instagram'a yeni istek veya giriş denemesi gönderilmedi.

- Python discovery: **148 test geçti**. Yedi yeni jitter regresyonu; uniform alt/üst sınırları ve istek başına tek çekiliş, ortak kasada yeniden açılış/geçiş, eski uzun rezervasyon, iptal, boşta ek bekleme olmaması, 429/Retry-After sırasında çekiliş/HTTP yapılmaması ve gerçek sayfalama sınırında farklı sürelerin ilerleme olaylarına taşınmasını doğrular.
- Eski sabit 15 saniyeli tarama/zamanlama testleri, üretim sınırına enjekte edilen sabit test süresini kullanır; bunlar mevcut sayfalama ve kişi başına kontrol semantiğini sınamaya devam eder. Üretim varsayılanının `random.uniform(12, 18)` çağırdığı, dönüşleri kontrol edilen rastgelelik sınırında doğrulanır. İstatistiksel dağılım ölçümü yapılmadı; rastgele örnek ortalamasına dayanan kararsız test eklenmedi.
- `npm run check`: ortak tema üretimi, Türkçe/İngilizce katalog ve JavaScript kaynak kontrolü geçti. Node **28 test geçti**.
- Paketlenmiş EXE: **10 ilerleme kontrolü**, **30 mevcut işlev kontrolü** ve **10 çoklu hesap kontrolü** geçti. İlerleme testinde 12 ve 18 saniyelik çubuklar, TR/EN politika açıklamaları, değişmeyen 15 dakikalık giriş aralığı, tek geri sayım, gizli/küçültülmüş pencerede durması, iptal ve 429 sınandı. Yalnız sentetik ilerleme olayları enjekte edildi; gerçek API rezervasyonları Python üretim sınırında doğrulandı.

Yerel raporlar/ekranlar `artifacts/progress-ui/`, `artifacts/accounts-ui/` ve `artifacts/desktop-0.2.14-qa/` altındadır. Önceki paketler değiştirilmedi; 0.2.14 ayrı önizlemedir. Android 0.1.34 ve örnek web zamanlayıcısı değiştirilmedi. Android/emülatör işleri yeniden çalıştırılmadı.

Canlı Instagram 429 oranı/bot tespiti üzerindeki etki, uzun süreli gerçek istek dağılımı, CPU/bellek kıyaslaması ve native bildirim teslimi bu sürümde ölçülmedi. Önceki 0.2.12 gizli pencere testinde bir kez görülen ve tekrarda yaşanmayan takılmanın kök nedeni de bu değişiklikle çözülmüş sayılmaz; [önceki doğrulama notu](MULTI-ACCOUNT-TEST.md) korunur.
