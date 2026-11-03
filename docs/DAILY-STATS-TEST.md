# Windows 0.2.36 / Android 0.1.75 — kalıcı günlük sayaçlar

## Veri ve sahiplik

Günlük özetler ayrıntılı 366/90 gözlem penceresinden bağımsızdır. Hesap ve hedef sayısal kimliği, UTC günü, ilk/son gözlem zamanı, örnek sayısı ve bilinen sayaçların ilk/son/minimum/maksimum değerleri saklanır. Sayaçların kendi ilk/son gözlem zamanı da tutulur; örneğin son yanıtta gönderi sayısı yoksa eski değer yeni saatte okunmuş gibi sunulmaz.

Ortak semantik vektör `test-fixtures/daily-stats.json` Python ve Kotlin tarafından okunur: UTC gece sınırı, 29 Şubat, yıl geçişi, yinelenen zaman, eksik gönderi sayısı ve boş gün. Başlangıç aktarımı yalnız eldeki gözlemleri kullanır. Ayrıntılar budanmadan önce özetlenir. Önceden silinen veya hiç gözlenmeyen geçmiş üretilmez.

Windows DPAPI ve Android Keystore/AES-GCM ile profil/yıl başına ayrı atomik dosyalar kullanılır. Bir dosya 512 KiB ile sınırlıdır; bir yılda en fazla 366 günlük kayıt bulunabilir. Sayısal arşiv biyografi, fotoğraf, parola, kod veya oturum içermez. Dosya adlarında kullanıcı adı kullanılmaz. Süreye bağlı otomatik silme yoktur; fiziksel disk ve dosya hataları gizlenmez.

Windows'ta tüm arşiv örnekleri tek arşiv kilidini; Android'de mağaza örnekleri ortak kilidi kullanır. Yeni bir yıl veya hesap geçişi, farklı mağaza nesnelerinin aynı dosyayı eşzamanlı eski veriyle yazmasına izin vermez. Windows'ta dosya içeriği örnekler arasında önbelleğe alınmaz. Arayüz bildirim sürümü hedef profili izler; başka profilin kontrolü kendi grafiğini gereksiz yenilemez.

Normal sayısal profil kaydı hatasında günlük arşiv hatası ayrılır; ana profil/son gözlem kullanımı devam eder ve günlük durum görünürdür. Hatalı dosya boş geçmişle değiştirilmez. Hesap silme Windows arşiv nesnesini iptal eder; önceden başlamış bir okuma/aktarım silinen yılları yeniden oluşturamaz. Android arşiv sorgusu, hesabın hâlâ aynı oturuma ait olduğunu hesap kilidi altında tekrar doğrular; okuma ve hesap silme bu sınırı paylaşır. Android `EncryptedRecord.exists()` yalnız `.bak` kaldığında da atomik okumaya izin verir; [Android kaynak uygulaması](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/util/AtomicFile.java) `openRead()` içinde yedeği geri yükler. Bu iyileştirmenin cihaz regresyon testi eklendi fakat çalıştırılmadı.

## Görünüm ve temizleme

Grafik yalnız açık hedef/aralık için yerel bir kontrol yanıtı alır; ana durum mesajına bütün yıllar eklenmez. Kısa aralıklar dışarıda kalan eski yıl dosyalarını okumaz. En çok 720 grafik noktası döner; uzun aralıklar gözlenen uçlar/extremalar ve kapsam sayıları korunarak gruplandırılır. Boş günler sıfır noktasına çevrilmez; günlük boşluklarda çizgi kesilir.

Temizleme, eski gözlemlerin yeniden aktarılmasını önleyen hedefe özel şifreli bir zaman sınırı bırakır ve yıl dosyalarını siler. Geleceğe tarihlenmiş mevcut gözlemler de bu sınıra dahil edilir. Cihaz saati sonradan geriye alınmışsa yeni günlük kaydın, kaydedilmiş temizleme sınırından sonraki gözlem zamanı olması gerekir. Ana geçmiş, listeler ve oturum korunur. Hesap silme kendi arşivini temizler. Windows kişi kaldırma mevcut geçmiş-koruma davranışını sürdürür; Android kişi kaldırma yeni arşivini de temizler.

Windows v1 gözlem yedeğine isteğe bağlı `daily` bölümü eklendi. Eski yedekler kabul edilir; günlük restore eklemeli yapılır ve çakışmada son gözlemi yeni olan gün korunur. Örnek sayıları toplanmaz. Bütün gelen belgeler yazımdan önce doğrulanır; yıllar ayrı atomik yazıldığı için tüm arşiv için tek bir dosya sistemi işlemiyle geri alma vaat edilmez. Birleştirme yeni geçerli günleri ekleyebilir; başarısızlık eski günleri silmez. Eski etkin temizleme sınırları korunur.

Yedek üretimi arşiv yıllarını sırayla kodlayarak mevcut yaklaşık 64 MiB sınırında durur; sınırı aşan bir yedek oluşturulmaz ve var olan hedef dosya değiştirilmez. Birikmiş bütün yılların tek büyük JSON ağacı belleğe alınmaz. Büyük arşivler için parçalı yedek ayrı bir geliştirmedir; yerel saklama bu yedek sınırına bağlı değildir.

## Çalıştırılan kapsam

- Python: gerçek DPAPI dosyaları, eski gözlemlerden aktarım, 401 gün/366 ayrıntı, örnek sayısı deduplikasyonu, zaman/alan eksikliği, boş gün, yıl geçişi, aralık dosya seçimi, kimlik ayrımı, temizleme, bozuk dosya, yazma hatası, yedek birleştirme/kapasite ve hesap silme.
- Node: geç yanıt, hatalı kapsam, tekrar deneme, boşluklu grafik yolu; ortak tema/çeviri/JavaScript denetimleri.
- Paketli EXE: 799 gün ve yalnız 3 yakın gözlemle 5 yeni kontrol; ay/yıl/tümü, iki sayaç, kişi geçmişi, temizleme ve hesap ayrımı. Önceki genel işlev, liste ve hesap turları da çalıştırılır. Instagram'a bağlanılmaz.
- Android: ortak vektörler dahil 138 JVM testi, release/lint ve Android test APK'sı derlemesi. Gerçek Keystore ve ekran için altı cihaz testi kaynakta hazırdır; kullanıcı cihaz testini sonraya bıraktığı için çalıştırılmamıştır. Canlı Android hesap bağlantısı bu çalışmayla doğrulanmış değildir.

Testlerdeki asenkron backend koşulları Node tarafında gerçekten beklenir. Eski tarayıcı-tarafı Promise koşulları hesap geçişi/ayar bitmeden ilerleyebildiği için ortak `wait-for-account.mjs` altında düzeltildi; üretim beklemeleri gevşetilmedi.

## Performans ölçümü

Geliştirme sırasında eski `cce8de8` kayıt yolu ile yeni yol, aynı 366 yakın gözlem ve iki 10.000 üyelik liste kaydı üzerinden, gerçek DPAPI yazımıyla 12'şer tamamlanmış profil güncellemesinde karşılaştırıldı. Ağ ve gerçek hesap yoktur. Yaklaşık bir yıllık günlük arşiv 2 dosyada 98.300 bayt; 10 yıllık arşiv 11 dosyada 978.450 bayt tuttu. Her iki durumda yalnız bir yıl dosyası yeniden yazıldı.

Bir yıllık örnekte kayıt+ana kasa yazımı medyanı 52,204 → 85,317 ms, 12 güncellemenin toplam duvar süresi 0,874 → 1,382 s, CPU süresi 0,453 → 0,781 s oldu. On yıllık örnekte medyan 58,211 → 88,881 ms; toplam duvar 0,901 → 1,292 s; CPU 0,500 → 0,750 s oldu. Ek arşivlemenin maliyeti vardır; bu ölçüm hızlanma iddiası değildir. İki durumda da eski bütün yıllar yeniden yazılmadığı için yazma süresi toplam yıl sayısıyla aynı oranda büyümedi.

Tek Python sürecinde iki sürüm/iki örnek birlikte görülen tepe working set 78,93 MiB'dır; sürüm başına bellek karşılaştırması değildir. Soğuk açılış, paketli uygulama ailesi CPU/RAM'i, Android pil/Keystore süresi ve saatler süren çalışma ölçülmedi. Sonraki aralık okuma/görünüm bildirim düzenlemeleri için toplam performans iddiası verilmez. Ham ölçüm yerel `artifacts/desktop-0.2.36-qa/daily/performance.json` dosyasındadır; paketler ve yerel test çıktıları Git'e eklenmez.
