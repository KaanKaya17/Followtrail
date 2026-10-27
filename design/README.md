# Ortak görsel dil

`theme.json`, Android, web ve masaüstünün ana renk ve köşe ölçülerinin tek kaynağıdır. Değişiklikten sonra `interface/` içinden `npm run theme`, doğrulama için `npm run check` çalıştırılır. Üretilen `UiPalette.kt` ve `theme.css` kaynakla birlikte tutulur.

Arka plan sıcak beyaz, ana metin koyu mürekkep, vurgu mor. İkincil sayaç şeftali; olumlu örnek karşılaştırma yeşil tonuyla işaretlenir. Anlam yalnız renge bağlanmaz. Birincil yüzey ve düğmeler düz mor; kartlar beyaz, ince nötr sınır ve 24 birim köşe kullanır. Ana düğmeler 18, küçük kontroller 12 birim köşeye sahiptir. Ölçek web'de CSS px, Android'de dp'dir; birebir piksel düzeni değil tutarlı hiyerarşi hedeflenir.

Masaüstü/web geniş ekranda soldan gezinir, dar web ekranında alt menü kullanır. Android'in mevcut Özet/Kişiler alt menüsü korunur; liste ve geçmişe kendi mevcut akışlarından girilir. Platformlara özgü davranışları ortak görünüm adına yeniden yazmadık.

Web ve masaüstündeki örnek kişi/listeler yalnız arayüz etkileşimleridir. Android'in mevcut üretim işlevleri ve şifreli kullanıcı kayıtları değişmez; görsel QA ayrı `.debug` paketiyle sentetik veride yapılır. Gerçek giriş, şifreli kayıt ve veri işleme yeni web/masaüstüne sonraki aşamada bağlanacaktır.
