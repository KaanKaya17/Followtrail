# 0.1.12 — profil değişikliği test adımları

Bu önizleme kayıtlı kişilerde kullanıcı adı, biyografi ve fotoğrafın karşılaştırılabilir kimlik/varsayılan fotoğraf durumunu izler. Yeni fotoğraf dosyası indirmez; URL farkından fotoğraf değişikliği çıkarmaz. Kendi ana hesabının özetinden ayrı olarak Kişiler'e eklenen hedefler içindir. Sabit hesap kimliği kullanılır; yeni adla tekrar kişi eklemek gerekmez.

1. `InstagramTracker-0.1.12.apk` dosyasını mevcut uygulamanın üzerine güncelleme olarak kur. Uygulamayı kaldırma/verilerini temizleme. Önceki bağlantı, kişiler, notlar ve favoriler kalmalı.
2. Kayıtlı bir kişinin analizini aç. Profil bilgileri ve Profil geçmişi görünmeli. Eski sürümde biyografi yoksa bilinmiyor açıklaması normaldir; güncelleme kendi kendine bu alanı internetten almaz.
3. Düğme izin veriyorsa Kişiyi yenile'ye bir kez bas. Profil önbelleği 15 dakikadan yeniyse süreyi bekle. Yanıtta biyografi/fotoğraf kimliği gelirse başlangıç kaydı oluşur; ilk alım değişiklik diye bildirilmez. Alan gelmezse açık mesaj görünür, sayılar yine çalışmalı.
4. Değişiklik denemesi için düzenleyebildiğin ayrı bir test hesabı kullan. Test hesabında biyografiyi değiştir veya açıkça boşalt; istersen kullanıcı adını ya da fotoğrafını da değiştir. Sonraki izinli başarılı okumayı bekle; mola/önbellek kaydını silme.
5. Aynı kayıtlı kişiyi tekrar yenile. Kullanıcı adı/biyografi değiştiyse Son değişiklikler ve Profil geçmişi girdisine dokun: önceki-yeni değer ve iki gözlem zamanı gelmeli. Bu, değişikliğin kesin saatini bildirmez. Eksik/null biyografi silinme değildir.
6. Fotoğraf kimliği iki bilinen okumada farklıysa veya bilinen varsayılan fotoğraf durumu değiştiyse Profil fotoğrafı bilgisi değişti mesajı beklenir. Karşılaştırılabilir kimlik yoksa bu özellik o yanıtta doğrulanamaz. Salt adresin değişmesi bildirim üretmemeli. Eski/yeni fotoğraf görselleri arşivlenmez.
7. Kişide Değişiklik bildirimi açıksa aynı metin/fotoğraf bilgisi değişimleri de mevcut seçilen aralıkta bildirim üretebilir. Sayılar sabitken de tetiklenir. Bildirimde tam biyografi değil değişiklik türü görünür. Android'in zamanlaması/pil yönetimi geciktirebilir. Bildirime dokunmak ilgili kişiyi açar.
8. Uygulamayı kapatıp yeniden aç. Profil geçmişi, favoriler ve eski sayılar korunmalı; kayıtlı ekranda gezinme veya aynı akış girdisini açmak tekrar bildirim/istek üretmemeli.

429, giriş veya doğrulama hatasında test durur. Otomatik tekrar veya başka uçlara yedek sorgu eklenmedi. Son HTTP durumu ve kullanıcı mesajı yeterli; parola, çerez, biyografi içeriği veya ham yanıt paylaşmak gerekmez.

Yalnız 90 gözlemlik saklama penceresi kullanılır. Çok büyük metadata değişimleri 512 KiB kayda sığmazsa eski kayıt korunur; kullanıcıya kapasite mesajı gösterilir. Biyografi/adres için 8192 UTF-8 bayt yerel yakalama bütçesidir, Instagram alan limiti değildir. Bu bütçeyi aşan isteğe bağlı alan alınamadı olarak tutulur; sayı okuması bunun yüzünden başarısız olmaz.

Otomatik kontroller sentetik veriyle yapıldı. Gerçek Instagram'ın bu alanları ilgili oturumda döndürmesi ve fiziksel telefon bildirimi henüz bu sürümün canlı kabulü değildir.
