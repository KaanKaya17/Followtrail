# Windows 0.2.35 — yerel liste sayfalama

## Davranış ve sınırlar

Takip Listeleri ekranı ilk 100 üyeyi alır. Ekranın altına yaklaşınca sonraki bölüm otomatik eklenir; arama, liste sahibi, yön, kayıt ve karşılaştırma değişince önceki sorgunun geç gelen yanıtları kullanılmaz. Yeni bölümler mevcut satırları yeniden oluşturmadan eklenir. Kullanıcı bütün listeyi kaydırırsa satırlar DOM'da birikir; bu çalışma sanal liste/pencereleme uygulamaz.

`Engine.snapshot()` artık liste üyelerini ve bütün fark kimliklerini ana durum mesajına koymaz. `ListPages`, yayınlanan kayıt görünümünü sahiplenir; sayfalama sorguları bu tutarlı yerel görünümü kullanır. Tam ve kısmi listeler, eski/güncel kayıt ve fotoğraf kaynakları birbirine karıştırılmaz. Arama ve filtreler yalnız görünen 100 satıra değil, ilgili yerel kaydın tamamına uygulanır. Henüz Instagram'dan okunmamış üyeler bu yöntemle bulunmaz.

`list_page`, istemci açmayan ve depoya yazmayan bir pipe kontrolüdür. Yürüyen taramanın kilidini, ilerlemesini veya istek bütçesini değiştirmez. Hesap, görünüm revizyonu ve kayıt seçimi doğrulanır; yanıt en fazla 100 üyedir. CSV, kullanıcı dosyayı seçtikten sonra worker'daki tam kayıttan üretilir; ekranda yüklenen sayfayla sınırlanmaz. Formül benzeri CSV hücreleri korunur. Dosya yolu renderer komutu olarak açılamaz.

Fotoğraflar mevcut görünür satır/CDN taşıyıcısını kullanır. Yerel satır sayfalaması Instagram liste API'sine istek atmaz; görünür yeni fotoğraflar mevcut görsel yüklemelerini gerektirebilir. Tarama, kota, mola, şifreli dosya biçimi ve Android davranışı değişmez.

## Doğrulama

- Python: tüm backend testleri; kapsam tam/kısmi liste, 100/100/50 sayfalama, tüm kayıtta arama, ilişki filtresi, eski/yeni fark, Türkçe ad, eski revizyon, hesap ayrımı, depoya/ağa dokunmama ve 250 üyelik CSV.
- Node: sayfa isteklerinin çakışmaması, geç yanıtların yok sayılması, gizli pencere, hatalı yanıtın otomatik tekrarlanmaması ve liste yanıtının etkin tarama ilerlemesini temizlememesi.
- Gerçek üretim EXE/worker/DPAPI ile yalıtılmış sentetik dizinde genel işlev turu ve 10.000 üyelik büyük liste testi. Kaydırma sırası, son sayfadaki kişiyi arama, hesap geçişi ve küçük pipe yanıtı ayrıca sınanır.
- Aynı arayüzün fotoğraf/profil akışı, gerçek çevrimdışı worker'dan yerel sayfa alarak doğrulanır. Yeni durum metinleri TR/EN/ES/PT-BR kataloglarındadır.

208 Python, 51 Node, 10 fotoğraf/profil renderer kontrolü, 36 genel paketli EXE kontrolü ve 4 büyük liste paketli kontrolü geçti. Ortak kaynak/tema/çeviri denetimi ve 180 kılavuz bağlantısı da geçti. Yeni canlı Instagram isteği veya Android testi yapılmadı.

## Ölçüm yöntemi

`interface/scripts/profile-desktop.mjs VERSION --large`: 3 saniye ısınma ardından 20'şer saniye görünür boşta, gizli boşta ve gizliyken 40 yerel not kaydı. İki 10.000 üyelik liste kaydı, 24 profil, 2.000 olay ve 500 bildirim grubu içeren aynı sentetik veri kullanılır. Gerçek paket, pipe ve şifreli yazım yolu çalışır. Bellek bütün uygulama süreç ailesinde 500 ms arayla örneklenir; working-set toplamında paylaşılan sayfalar birden fazla sayılabilir. Soğuk açılış, saatler süren sızıntı, Android pili ve gerçek API hızı ölçülmez. Tek kısa koşu tüm cihazlar için performans garantisi değildir.

## Sonuçlar: 0.2.34 → 0.2.35

Aynı büyük arşivle tek kısa paketli karşılaştırmada, gizliyken 40 not kaydı 12,472 → 7,797 saniye sürdü; 20 saniyelik örnekte süreç ailesinin CPU zamanı 10,406 → 5,063 saniye oldu. Bu aşamadaki örneklenmiş tepe working set 778,7 → 536,1 MiB, tepe private bytes 501,3 → 395,9 MiB oldu. İki sürüm de 7 süreç kullandı ve gizli güncellemelerde sıfır DOM mutation kaydı görüldü.

Görünür boşta tepe working set 586,8 → 409,6 MiB; gizli boşta 586,8 → 409,9 MiB ölçüldü. Görünür boşta CPU zamanı 0,313 → 0,297 saniye, gizli boşta 0,078 → 0,125 saniye oldu. Kısa boşta CPU örnekleri bir hızlanma veya sürekli sıfır tüketim iddiasını desteklemez.

Ayrı, sade bir sentetik durumda iki yönde 10.000'er üye ve ikişer tam kayıt kullanıldı. Eski kaynak `fddf18a` ve yeni kaynak aynı veriyle, ısınmış 15 tekrarda karşılaştırıldı: ana durum JSON'u 2.825.182 → 2.891 bayt; ilk yerel 100 kişilik sayfa 8.709 bayt. İlk gösterimin durum + sayfa verisi yaklaşık 11,6 KB'dır. Bu mikro ölçümde snapshot + JSON medyanı 110,100 → 0,351 ms; 15 tekrarın toplam duvar süresi 1,801 → 0,006 saniye, CPU zamanı 1,562 → sayaç çözünürlüğünde 0,000 saniye oldu. Bu son değer sıfır CPU maliyeti anlamına gelmez. Gerçek arayüz çizimi, IPC çerçeveleme ve soğuk açılış bu mikro ölçüme dahil değildir.

Ham yerel çıktılar `artifacts/desktop-0.2.34-qa/performance-large/summary.json`, `artifacts/desktop-0.2.35-qa/performance-large/summary.json` ve `artifacts/desktop-0.2.35-qa/state-projection.json` altındadır; kaynak depoya paket/test çıktısı eklenmez. Sonuçlar bu cihazın kısa sentetik örnekleridir; ağdaki liste taramasının hızlandığı veya her cihazda aynı kazanç elde edileceği söylenmez.
