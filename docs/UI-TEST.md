# 0.1.15 diyaloglarını inceleme

1. Güncellemeyi mevcut uygulamanın üzerine kur; kaldırma/veri temizleme yapma. Kayıtlar ve otomasyon seçimleri kalmalı.
2. Otomasyonu ayarla penceresi yumuşak mor zeminli, yuvarlatılmış köşeli olmalı. Seçimler ve Başlat/İptal/Nasıl çalışır? okunmalı; sistem yazısını büyütüp kaydırmayı da dene.
3. Otomasyon durumu → Yenile yalnız yerel kayıtları yeniden gösterir. Satıra basmak kayıtlı listeyi açar. Liste kayıtlarını temizle artık Nasıl çalışır? içinde; ayrı onayı İptal edince veri kalmalı.
4. Kişi ekle ve Not ve grup pencerelerini klavye açıkken dene. Alanlar, kaydırma ve Kaydet/Vazgeç düğmeleri erişilebilir olmalı. Kişiyi kaldır onayından vazgeç.
5. Değişiklik bildirimi ve Kaydın kapsamı pencerelerini aç. Uzun açıklamalar kaydırılmalı; sistem Geri düğmesi pencereyi kapatmalı.

Bu görsel inceleme için yeni Instagram verisi çekmek gerekmez. Çok sayfalı otomasyon denemesi [ayrı kılavuzda](AUTOMATION-TEST.md).

## Önceki ana ekran kontrolleri

1. `InstagramTracker-0.1.11.apk` dosyasını mevcut uygulamanın üzerine güncelleme olarak kur. Uygulamayı kaldırma veya verilerini temizleme. Yeni arayüz için hesabını yeniden bağlaman gerekmez.
2. Ana sayfada kendi kullanıcı adını ve önceki sayıları kontrol et. İki renkli sayı kartı, ilgili takipçi/takip edilen ekranını açar; bu geçiş okuma başlatmaz.
3. Alt çubuktan Kişiler ekranını ve kayıtlı bir kişinin analizini aç. Geçmiş, not, grup ve izleme ayarları aynı kayıtlardan gelir.
4. Önceden alınmış bir liste varsa arama alanına birkaç harf yaz. Arama yalnız bu kayıtları filtreler, ağ isteği göndermez. Klavye açıkken üst gezinme/alt işlemler yerine arama sonuçlarına alan açılmalı; Bitti ile kapatınca işlem düğmeleri geri gelmeli.
5. Liste ekranındaki bilgi düğmesinden Kaydın kapsamı açıklamasını aç. Gösterilen sayının alınan kayıtlara ait olduğu ve son sayfa görülmesinin tamlık garantisi vermediği açık olmalı.
6. Aramada eşleşmeyen bir ad dene; Aramana uygun hesap yok mesajı gelmeli. Temizle düğmesi kayıtları geri göstermeli. Bu, alınmış sayfada gerçekten sıfır hesap olmasıyla karışmamalı.
7. İnternet yokken önceden alınmış veriler açılmalı; Çevrimdışı görüntülüyorsun açıklaması gelmeli. Tarih ve sayılar kendiliğinden yenilenmemeli.
8. Daha büyük sistem yazı boyutunda başlıkları, sekmeleri ve kaydırmayı kontrol et. Geri düğmesiyle ana ekrana dön.

Bu inceleme için yeni veri çekmek gerekmez. Profil veya liste okuma düğmesine basarsan mevcut istek ve mola kuralları geçerlidir. Büyük listede devam, uzun süreli oturum ve liste karşılaştırması için önceki canlı test kılavuzu ayrıca geçerlidir.

Önceki 0.1.9 kaynak/APK yedeği de korunur. Çalışan 0.1.8, `checkpoint/0.1.8-live-20261025` etiketi ve ayrı yerel APK/kaynak/Git yedeğinde korunur. Bu sürüm yeni veri toplama özelliği veya veri biçimi geçişi eklemez.

## Son değişiklikler ve favoriler

- Kişiler ekranında bir kişinin yıldızına bas: favori listenin başına gelmeli. Uygulamayı yeniden açınca ve aynı kişiyi başarıyla yenileyince yıldız korunmalı. Yıldızı kapatınca kişi normal sırasına döner.
- Ana sayfada Son değişiklikler, kayıtlı gözlemler arasındaki sayı farklarını tarihle göstermeli. İlk kayıt veya yalnız gönderi değişikliği takipçi değişikliği diye sunulmaz.
- Akış kaydına dokunmak ilgili analizi/listeyi açmalı; Tümünü gör yalnız saklanan en son 20 girdiyi gösterir. Bu işlemler ve yıldızlar okuma sayacını artırmamalı.
- Liste etkinliği son hedefin eldeki sayfa kayıtlarıyla sınırlıdır. Sayfa kaydı, yeni takipçi veya takipten çıkma olayı değildir. Mevcut geçmişin dışında eski hareketler tahmin edilmez.
