# Günlük İstatistikler

[Kılavuz ana sayfası](Home.md) · [Geçmiş ve analiz](Gecmis-ve-Analiz.md)

Günlük geçmiş, kendi hesabınızın ve kaydettiğiniz profillerin sayaçlarını uzun süre incelemek içindir. Ayrıntılı son kontrollerin sınırı dolsa da günlük özetler varsayılan olarak korunur. Veri saklama bölümünde süre seçilirse günlük özetler bu süreye göre temizlenir.

## Ekranı açmak

**Windows:** Özet ekranındaki grafikte **Günlük** görünümünü seçin. **Son kontroller**, eski ayrıntılı görünümü açar. Kaydedilen profillerde **Geçmiş → Günlük geçmiş** yolunu kullanın.

**Android:** Özet ekranında veya kişinin analiz sayfasında **Günlük geçmiş** düğmesini açın. Yeni ekranın cihaz testi henüz tamamlanmamıştır; [sürüm durumu](Surum-ve-Destek.md#android) kapsamı açıklar.

Takipçi veya takip edilen sayısını; **7 gün**, **30 gün**, **1 yıl** veya **Tümü** aralığında inceleyebilirsiniz. Uzun dönemler ekranda gruplandırılabilir; toplam gözlenen gün ve kontrol sayısı ayrıca gösterilir.

## Ne kaydedilir?

Bir gün içindeki kontrollerin ilk ve son bilinen değeri, görülen en düşük/en yüksek sayı, kontrol zamanları ve kontrol sayısı saklanır. Gönderi sayısı yanıtta bulunuyorsa özete eklenir; mevcut grafik takipçi/takip edilen sayılarını gösterir.

Grafikte günün **son gözlenen değeri** kullanılır. Bugünün kaydı yeni kontrol geldikçe güncellenir; henüz gün sonu oluştuğu iddia edilmez. Noktanın ayrıntısı, gözlenen en düşük/en yüksek değeri gösterir.

**Günler UTC saatine göre gruplanır.** Bu sabit sınır, Windows ve Android'in aynı tarih anlamını kullanmasını sağlar. Kontrol yapılmayan günler sıfır sayılmaz; eksik kalır. Kontroller arasındaki hareketler bilinmez.

Örneğin iki kayıt arasında sayı 100'den 103'e çıktıysa net fark +3'tür. Bu, yalnız üç kişinin geldiğini veya arada hiç kimsenin ayrılmadığını kanıtlamaz. Üye adları için [tam liste karşılaştırması](Takip-Listeleri.md) gerekir.

## Eski veriler ne olur?

Güncellemede, hâlâ cihazda bulunan eski gözlemler günlük kayda alınabilir. Yeni bir başarılı kontrol geldiğinde eski ayrıntılar budanmadan önce bu özetleme yapılır. Daha önce silinmiş geçmiş veya uygulamayı kurmadan önceki günler oluşturulamaz.

Bu kayıtlar cihazlar arasında otomatik eşitlenmez. Bir telefonda alınan gözlem, bilgisayardaki günlük arşive kendiliğinden eklenmez.

## Saklama ve temizleme

Özetler hesap, profil ve yıl bazında ayrı şifreli dosyalarda tutulur. Her yıl için küçük bir kayıt kullanılır; yeni kontrolde bütün yıllar yeniden yazılmaz. Günlük sayfalara bakmak yeni Instagram isteği gerektirmez.

**Günlük geçmişi temizle**, seçili profilin günlük özetlerini kaldırır. Son ayrıntılı kontrolleri, listeleri ve oturumu kaldırmaz. Temizlenen eski özetler, yakın geçmişten kendiliğinden yeniden oluşturulmaz. Sonraki yeni kontroller yeni günlük kayıtlar oluşturabilir.

Hesabın yerel verilerini silmek o hesaba ait günlük arşivi de siler. Windows ve Android'de bir kişiyi kayıtlı profillerden çıkarmak o kişinin günlük geçmişini de temizler. Dışa aktarılan dosyalar ayrıca silinmelidir. [Saklama ve silme kapsamı](../DATA-LIFECYCLE.md).

Disk, şifreleme veya bozuk dosya sorunu olursa günlük kayıt durumu ayrıca hata gösterebilir. Mevcut dosyalar sessizce sıfırlanmaz. Sorun bildiriminde parola veya ham arşiv dosyası paylaşmayın.

## Windows yedeği

Şifreli gözlem yedeği günlük özetleri de içerir. Geri yükleme günlük kayıtları birleştirir; aynı gün çakıştığında daha yeni kayıt korunur, kontrol sayıları birbirine eklenmez. Etkin hesaptaki açık temizleme işareti, silinen eski günlerin tekrar eklenmesini engeller.

Yedek aynı Windows kullanıcı bağlamına bağlıdır; oturum içermez. Mevcut tek dosyalık yedek sınırı yaklaşık 64 MiB'dır. Çok büyük bir arşiv bu sınırı aşarsa yedek üretilmez ve önceki hedef dosya korunur; cihazdaki günlük kayıtlar silinmez. [Gizlilik ve veriler](Gizlilik-ve-Veriler.md).
