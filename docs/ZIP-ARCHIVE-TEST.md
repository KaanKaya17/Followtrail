# Hesap bağlamadan ZIP inceleme — Windows 0.2.92

Windows 0.2.92, Instagram ZIP’ini okurken bir hatayı bütün dosyaya yaymaz. Ek takip listelerini, takip tarihlerini ve muhtemel ad değişikliklerini gösterir. Bu değişiklik 18 Kasım 2026 ZIP denetiminde bulunan 7 veri ve 8 arayüz eksiğini kapatır. Yeni Instagram isteği yoktur.

## Veri

| Önceki durum | 0.2.92 |
| --- | --- |
| **Takip ilişkileri** 512 MB üstü ZIP’i “takip listesi yok” diye reddediyordu | Sınır arşiv tarayıcısıyla aynı: 8 GiB, 50.000 dosya. Fotoğraf/video içeren tam dışa aktarımlar okunur |
| Tek bozuk satır iki listeyi birden “Okunamadı” yapıyordu | Satır atlanır ve sayılır. Okunamayan dosya parçası yalnız kendi listesini **Kısmi** yapar. Diğer liste etkilenmez. JSON varken artık kalmış HTML dosyası yok sayılır. İki dışa aktarım içeren ZIP’te dosyası fazla olan klasör kullanılır |
| Ad değiştiren kişi “çıkan” ve “gelen” görünüyordu | Aynı takip tarihini taşıyan tek çıkan/gelen çifti **Kullanıcı adını değiştirmiş olabilir** olarak ayrı gösterilir. Aynı tarihte birden fazla aday varsa eşleştirme yapılmaz |
| Karşılaştırma yalnız isim veriyordu | Her satırda takip tarihi vardır; sonuçlar ada, en yeni veya en eski tarihe göre sıralanır ve aranır |
| Son takipten çıktıkların, bekleyen istekler, yakın arkadaşlar, engellenenler, kısıtlananlar okunmuyordu | Bu listeler ve gelen istekler, hikâye gizlenenler, kaldırılan öneriler **Arşiv kapsamı → Takip ilişkileri** kartlarında ve **Takip ilişkileri** penceresinde tarihleriyle listelenir. Ayrıca **Seni takip etmeyen yakın arkadaşların** görünümü vardır |
| Dosya adındaki hesap kullanılmıyordu | ZIP’in kendisi hesap adı vermiyorsa `instagram-<hesap>-<tarih>-….zip` adındaki hesap “(dosya adından)” etiketiyle kullanılır |
| Büyük beğeni dosyası ve eski klasör düzeni özetten sessizce düşüyordu | 24 MiB üstü kayıt **Çok büyük** olarak görünür. `likes/`, `comments/`, `saved/` gibi eski düzenler okunur. Yorumlar ve mesaj sohbet sayısı özete eklendi |

## Arayüz

- **Takip ilişkileri** (eski adı “ZIP takip karşılaştırması”) açık ZIP’i kullanır, dosyayı yeniden sormaz. Okunamayan satır veya parça varsa pencerede uyarı görünür.
- **İki ZIP karşılaştır** kaynak kartları iki ZIP’teki takipçi/takip edilen sayısını, kısmi veya okunamayan listeyi gösterir. Ortak okunabilir liste yoksa başlatma düğmesi kapalıdır. Hesap bilgisi eksikse uyarı verir.
- İkinci ZIP sürükle-bırakla da seçilebilir.
- Sonuç başlıkları yöne göredir: **Yeni takipçiler / Takipten çıkanlar**, **Takip etmeye başladıkların / Takibi bıraktıkların**. Eksik liste için hangi ZIP’te olmadığı yazar.
- Sonuçlar **CSV olarak kaydet** ile dışa aktarılır: liste, değişim, kullanıcı adı, önceki ad, UTC takip tarihi. Biçim enjeksiyonuna karşı hücreler korunur.
- Tamamlanan karşılaştırma, aynı ZIP açıkken başka görünüme geçip dönünce yeniden hesaplanmadan gösterilir.
- Gizlenmesi gereken **Daha fazla göster** düğmelerinin görünmesi giderildi (`.button[hidden]`). Arama düğmesi ve filtre satırı dar ve geniş pencerede düzgün dizilir.

## Doğrulama

- `python -m unittest discover -p "test_*.py"` (Python 3.14.3): **335 test geçti**. `test_instagram_archive.py` 14 test: satır/parça toleransı, tek listenin okunamaması, JSON–HTML önceliği, iki dışa aktarımlı ZIP, ek listeler, 520 MiB’lık ZIP, dosya adı hesabı, kısmi kapsam, sohbet/yorum sayısı, “çok büyük” durumu, karşılaştırma tarihleri, ad değişikliği ve belirsiz çiftler, arama, CSV.
- `node --test desktop/*.test.cjs`: 72 test geçti. `npm run check`: dört dilde 1525 ileti eşleşti.
- `npm run check:functional`: paketli 0.2.92 EXE’de **46 kontrol geçti**.
- `node scripts/check-zip-archive-desktop.mjs`: paketli EXE’de **9 kontrol geçti**. Kapsam, takip ilişkileri penceresi (dosya penceresi açılmadan), kaynak kartları, sonuç başlıkları/tarihler/ad değişikliği, gizli “daha fazla” düğmesi, arama, CSV içeriği, hatırlanan sonuç, İngilizce etiketler, 800 px’te taşma olmaması ve sıfır ağ isteği sınandı. Ekran görüntüleri `artifacts/desktop-0.2.92-qa/zip-archive` altında.
- Kılavuz ekran görüntüleri dört dilde `capture-archive-docs.mjs` ile yenilendi.
- Gerçek bir Instagram ZIP’iyle deneme bu sürümde yapılmadı; testler güncel JSON biçimine benzeyen sentetik dosyalarla yapıldı. Sürükle-bırak otomatik testte sınanmadı. Instagram’ın büyük dışa aktarımları böldüğü çok parçalı ZIP’ler birleştirilmez. HTML biçimindeki listelerde takip tarihi okunmaz.
- Dil değiştirildiğinde açık ZIP kapanır ve yeniden açılması gerekir; bu davranış değişmedi.
