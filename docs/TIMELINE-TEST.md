# Zaman çizelgesi — Windows 0.2.45

Zaman çizelgesi Bugün, Dün ve tarih başlıkları altında gösterilir. Gruplama ve saatler bilgisayarın yerel saat dilimindedir; saklanan zaman damgaları değiştirilmez. Kartlarda sayısal hesap kimliğine bağlı yerel fotoğraf, @kullanıcı adı, saat ve değişiklik özeti bulunur. Güncel profil kaydı yoksa olayda açıkça saklanmış kullanıcı adı veya hesap kimliği kullanılır; başlıktan ad tahmin edilmez.

Profil alanları ve liste farkları, Bildirimler ekranıyla aynı önizleme işlevini kullanır. Sayısal değerlerde eski → yeni ve işaretli fark, metinlerde kısa eski/yeni çifti gösterilir. Biyografi de bu ortak metin gösterimine dahildir. Sıfır korunur; bilinmeyen eski değer sıfıra çevrilmez. Liste önizlemesi her yönde iki kullanıcı adı ve kalan sayıyı gösterir; ilk kayıt fark gibi sunulmaz. Tam değerler/kişi adları mevcut ayrıntı pencerelerindedir. Teknik hata metni ayrıntıda kalır.

Normal profil kontrol kayıtları her günün altında açılır bölümde tutulur. Profil görünürlüğü incelemeleri normal kontrol diye gizlenmez. Kontroller filtresi rutin bölümleri açık gösterir. Değişiklikler ve rutin kontroller ayrı ayrı 100'er kayıtla sayfalanır; rutin kayıt sayısı değişiklikleri ilk sayfadan düşürmez. Açılan günler yerel ekran güncellemelerinde açık kalır.

Mevcut tür filtresine hesap filtresi eklendi. Geçmişi bulunan silinmiş profiller ve hesaba bağlı olmayan genel kayıtlar da seçilebilir. Boş birleşimden iki filtre birlikte temizlenebilir. Bağlı hesap değişiminde kişi/tür/sayfa/açılma durumu sıfırlanır. Arayüz işlemleri geçmişi silmez, bildirimleri okundu yapmaz veya Instagram isteği göndermez.

## Doğrulama

- `check-timeline-desktop.mjs`: gerçek 0.2.45 EXE/worker/IPC ve ayrı DPAPI test kasasıyla 5 senaryo grubu geçti. Yerel Bugün/Dün/eski gün grupları, fotoğraf-kimlik eşleşmesi, HTML kaçışı, sıfır/bilinmeyen değer, çok alanlı kayıt, 120 rutin kaydın ayrı sayfalanması, açılma durumunun korunması, görünürlük kontrollerinin ana akışta kalması, birleşik kişi/tür filtreleri, eski profil/genel kayıt seçimi, liste başlangıcı ve eksiksiz ayrıntılar sınandı. Dört dil, üç tema, 960×680 pencere ve hesaplar arası izolasyon kontrol edildi. Geçmiş, bildirim kaydı ve istek kapısı karşılaştırıldı; değişmedi. JavaScript hatası ve HTTP isteği yoktu.
- `check-functional-desktop.mjs`: 36 mevcut paketli kontrol geçti; önceki zaman çizelgesi filtre/ayrıntı ve görünürlük kanıtı akışları dahil.
- `check-notifications-desktop.mjs`: ortak önizlemeyi kullanan bildirim ekranında 5 kontrol grubu geçti. İlk paralel çalıştırma pencere açılışında zaman aşımına uğradı; aynı paket tek başına tekrarlandığında geçti. Bu tek açılış gecikmesinin nedeni doğrulanmadı.
- 62 mevcut Node testi ve `npm run check` geçti; Türkçe/İngilizce/İspanyolca/Brezilya Portekizcesinde 1146 metin eşleşiyor.

Ekran görüntüleri ve sonuç özeti yerel `artifacts/desktop-0.2.45-qa/timeline/` altında, Git dışında tutulur. Veriler sentetiktir; canlı Instagram veya gerçek hesap kasası kullanılmadı. Backend'de yalnız sürüm bilgisi değişti; Python test kümesi yeniden çalıştırılmadı. Android ekranları bu Windows düzenlemesinde değiştirilmedi. Önceki sürümle CPU/RAM veya süre karşılaştırması yapılmadı.
