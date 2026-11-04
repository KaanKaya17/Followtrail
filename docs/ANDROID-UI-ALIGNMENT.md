# Android 0.1.76: Windows görünümüyle uyum

## Kapsam

Bu sürüm, Windows 0.2.40–0.2.47 arasındaki profil/özet/liste sahibi/bildirim/zaman çizelgesi düzenlemelerinin native Android karşılığıdır. Electron kodu Android'e taşınmaz; mevcut Android View, ListView, Keystore ve görünürlük yaşam döngüsü kullanılır. Windows paketi değişmez.

- Özet: büyük hesap fotoğrafı, kullanıcı adı ve profil adı, kendi profil ayrıntısını açma, Bildirimler ve Zaman Çizelgesi erişimi. Aynı güncel kontrole ait eski/yeni sayaçlar varsa kartta gösterilir.
- Profil: fotoğraf, kullanıcı adı/isim, bilinen gönderi sayısı dahil sayaçlar, biyografi satırları ve mevcut bağlantılar. Yenileme, listeler, favori ve izleme öne alınır. Ayrıntılı geçmiş, not/grup, günlük geçmiş, görsel geçmiş ve bildirim tercihleri korunur; ikincil işlemler Diğer menüsüne taşınır. İzleme ayrıntıları isteyerek açılır.
- Liste sahibi: seçili fotoğraf, @kullanıcı adı ve profil adı; boşta iken başka kayıtlı profilin yerel listesine geçiş. Devam eden istek veya sıra sırasında hedef değişmez. Ekran yeniden oluşturulunca seçilen hedef korunur.
- Bildirimler: profil kontrolündeki değişiklikler tek kayıt, tümü/okunmamış ve profil/liste tür filtreleri, profil filtresi, kalıcı okundu durumu. Rutin başarılı kontroller bildirim sayılmaz. Tercihler sistem bildirimlerini sınırlar; yerel işlem geçmişini silmez.
- Zaman Çizelgesi: güne göre gruplama, ayrı açılan rutin kontroller, profil/tür filtreleri, gerçek eski/yeni değerler, kayıt sırasında kullanılan kullanıcı adı ve aynı sabit ID'nin güncel fotoğrafı. İlk görünüm en çok 50 değişiklik ve 50 rutin kontrolü kapsar; sonraki kayıtlar yerelden açılır. ListView yalnız görünür satırları oluşturur/yeniden kullanır.
- Günlük grafik: seçilen dönemdeki ilk bilinen değer ile son bilinen değer arasındaki net fark da gösterilir. Boş günler ve kalıcı günlük arşiv davranışı değişmez.

## Saklama ve kaynaklar

İşlem günlüğünün mevcut v1 şifreli zarfı eklemeli genişletildi. Önceki/sonraki sayaç ve profil metni değerleri yeni kontrollerden itibaren saklanır. Eski günlüklerde bulunmayan değerler üretilmez; varsa eski net fark yine gösterilir. Profil metni değişiklikleri doğrudan gözlemi üreten karşılaştırmadan alınır.

Günlük en fazla 2000 olay ve 2 MiB zarfla sınırlıdır; büyük metinlerde en eski olaylar sayı sınırına ulaşmadan budanabilir. Bildirim görünümü en fazla son 500 değişikliktir. Okundu anahtarları ve profil tercihleri aynı hesabın şifreli dosyasındadır. Geçmiş liste farklarında her yönden en fazla 10 kimlik örneği tutulması korunur; eksik örnekler tam listeymiş gibi sunulmaz. Günlük istatistik arşivinin yıllık dosyaları bu olay sınırından ayrıdır.

Günlük okuma önbelleği en fazla iki kayıt yuvasını tutar; dosya/yedek boyutu ve değişme zamanı ile yeniden doğrulanır. Yerel kayıt bildirimleri görünür geçmiş ekranını günceller. Ekran durunca dinleyici ve görüntü işleri durur; geç tamamlanan yükleme, yeniden oluşturulmuş ekranı güncellemez. Yeni ağ sorgusu veya arka plan anketi eklenmez.

Avatar önbelleği hesap + kişi + bilinen fotoğraf ID'siyle eşleşir. Aynı fotoğrafın imzalı CDN adresi değiştiğinde kayıt yeniden kullanılabilir. ID yoksa URL anahtarı korunur; farklı hesap, kişi veya fotoğraf ID'si ayrı kalır. Mevcut iki indirme işçisi, sınırlı kuyruk/bellek/disk ve iptal davranışı korunur.

## Platform sınırları

Bu çalışma bütün Windows özelliklerinin Android'e taşındığı anlamına gelmez. Windows hikâye arşivi gibi ayrı özellikler bu sürüme dahil değildir. Android'in sistem arka plan zamanlaması masaüstüyle aynı değildir. Hesap girişi, WebView doğrulaması, istek kuralları, bütçeler ve kayıtlı 429 molaları değiştirilmez. Canlı Instagram bağlantısının çözüldüğü iddia edilmez.

## Doğrulama

4 Kasım 2026:

- `:app:testDebugUnitTest`: 142 test, 0 hata/başarısızlık/atlama.
- `:app:lintRelease`: 0 hata, 48 uyarı. Uyarılar çoğunlukla mevcut metin/çoğul önerileri, kullanılmayan kaynaklar ve bağımlılık sürüm önerileridir; uyarısız olduğu iddia edilmez.
- Release, debug ve instrumentasyon APK derlemeleri başarılı. Dört dilde 716 kaynakta isim, parametre ve çoğul sözleşmesi kontrolü geçti.
- Mevcut imza anahtarıyla APK üretildi; v2/v3 imzası ve `verify-release.py` paket kontrolü geçti. Paket 2.500.027 bayt; SHA-256: `de52c8df70c32ac953abfaa409c56badf2353b4dca7c7c0b6f435800797da58a`.
- API 36 emülatöründeki ilk koşu tamamlanmadı. Bazı yerel geçmiş/profil işlemleri geçti; rutin satır testinde filtre düğmesini olay satırından ayıran beklenti düzeltildi. İzleme/otomasyon diyaloglarında bulunamayan kontroller gözlendi; nedeni ve son APK üzerindeki tam ekran akışı henüz doğrulanmadı. Bu koşu geçmiş sayılmaz. Son fotoğraf URL kurtarma regresyonu ve 2000 kayıt sınırı cihaz testleri derlendi, tamamlanmış cihaz sonucu yoktur.
- Önceki `Medium_Phone` sanal cihazı açılışında EXT4 hatası ve kernel panic görüldü. Verileri silinmedi. Ayrı `TrackerUiParity` test cihazı oluşturuldu; sonraki çalışma bellek daraldığı için durduruldu. Kullanıcı ekran testini sonraya bıraktı; emülatör kapatıldı. Bağlı gerçek telefona kurulum yapılmadı.

Cihaz testleri ayrı debug paketinde sentetik veri kullanır; gerçek Instagram hesabı/taşıyıcısı test edilmez. Genel CPU/batarya kazancı veya yüzdelik hızlanma ölçümü yapılmadı. Tamamlanmış ekran testi ve canlı hesap bağlantısı bu sürümün doğrulanmış kapsamı değildir.
