# Eksik takipçi sayfalaması — Windows 0.2.42

3 Kasım 2026. Kullanıcının test profilinde 75/82 takipçi kaydı kalmıştı; takip edilen kaydı 144/144 tamamlanmıştı. Mevcut şifreli kayıt salt okunur DPAPI açılışıyla incelendi; oturum/çerez/ham yanıt dışarı aktarılmadı. Aktif okuyucu hesabı, hedef profilden farklıydı.

## Canlı bulgu

İlk kontrollü normal tarama HTTP 200 ile 25 + 25 + 25 + 7 satır döndürdü. Sayfalar arası dört tekrar nedeniyle 78 benzersiz kimlik kaldı. Son yanıt `has_more=false` ve devam işareti olmadan bitti. Kısıtlı liste bayrağı false idi; bu, eksiksiz liste kanıtı olmadı. Kota veya 429 engeli gözlenmedi. Eski kısmi kayıtla yeni kaydın birleşimi 82 kimlik içeriyordu; farklı günlerin bu kayıtları güncel tam liste olarak birleştirilmedi.

Yalnız bir ek tur denemesi 72 → 77 benzersiz hesapla eksik kaldı. Kütüphanenin desteklediği açık tarih sıralamasıyla yapılan dar tanı da 74 → 79'da kaldı; bu sıralama üretim davranışına eklenmedi. İki tanı başarısız olarak raporlandı; tamamlandı bayrağı üretilmedi.

Son, güncellenmiş normal okuyucu denemesi aynı tarama içinde **73 → 79 → 82** benzersiz hesaba ulaştı. Üç turda 12 liste sayfası okundu; son sayfalama bitişi görüldü ve ardından profil sayısı yeniden 82 olarak doğrulandı. **82/82 tamamlanmış başlangıç kaydı** şifreli kasaya yazıldı. Bu deneme 12 liste GET'i + ilk/son profil GET'i olmak üzere 14 sayaçlı istek kullandı; son yanıt HTTP 200 idi. İlk kayıt geçmişteki takipten çıkışları üretmez. Bu sonuç yalnız bu hedef/oturum ve denemeyi doğrular; her Instagram listesinin tamamlanacağı garantisi değildir.

## Uygulanan davranış

- Tekrar eden sayfalar gözlenmiş ve sayfalama sonunda profil sayısından az benzersiz hesap kalmışsa aynı yeni taramada en fazla üç ek tamamlama turu yapılabilir.
- Bir tam tur yeni hesap getirmezse durulur. Tamamlama yalnız ilk başlangıçtan itibaren 10 dakikalık pencerede sürdürülebilir; eski tarama/önceki gün kayıtları bu üyelik kümesine eklenmez.
- Sayfalama döngüsü, kısıtlı/belirsiz yanıt, 429, bütçe, iptal ve kapasite sınırları korunur. Her sayfa normal istek aralığından geçer. Başarı için son sayfa ve eşleşen güncel profil sayısı gerekir; toplam/kimlikler eşleşmezse fark bildirimi çıkarılmaz.
- Ek turun ilk sayfası için bekleyen durum, devam belirteci yokluğundan ayrı saklanır. Yeniden başlatma bu durumu sürdürebilir; süre dolmuş kayıt yeni tarama olur.
- Tamamlanmış eski liste önceliğini korur. Henüz tam liste yoksa gösterilecek kısmi kaydın sahibi backend'dir: en geniş kapsam, eşit kapsamda daha yeni zaman seçilir. Yeni tarama başlarken en geniş kısmi kayıt korunur; üyelikler birleştirilmez. Arayüz `visiblePartialId` ile bu seçimi kullanır.
- Tamamlama yöntemi liste ekranında dört dilde belirtilir. Yeni izin, hesap işlemi veya ağ uç noktası yoktur.

## Kontroller

- 242 Python testi geçti; yeniden başlatmada gerçek DPAPI, eski kısmi kayıtların karışmaması, sıfır ilerlemede durma, üç tur sınırı, değişmiş son sayaç, 429, süre aşımı ve eski kayıtta eksik zaman alanı dahil.
- 62 Node testi ve `npm run check` geçti; dört dilde 1129 mesaj eşleşir.
- Worker derlendi; Windows 0.2.42 paketi üretildi.
- 36 genel masaüstü, 10 çoklu hesap, 4 büyük liste, 11 arama/profil renderer ve 3 yeni liste kurtarma paket kontrolü geçti. Sentetik testlerde dış arayüz isteği/JavaScript hatası yoktur. Yeni paket kontrolü 78 kişilik güncel kısmi kaydın 76 kişilik eski kayıt yerine gösterilmesini ve fark hesabının kapalı kalmasını sınar.

Canlı tanı yalnız sayfa/kayıt/tekrar sayısı ve sabit protokol alanlarının türlerini tuttu; gerçek takipçi kimlikleri, isimler, parola, çerez veya gövdeler Git'e eklenmedi. CPU/RAM veya önceki sürümle performans farkı ölçülmedi. Android bu değişiklikte derlenmedi.
