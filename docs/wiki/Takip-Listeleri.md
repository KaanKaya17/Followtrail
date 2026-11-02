# Takip Listeleri

[Kılavuz ana sayfası](Home.md) · [Otomatik kontrol](Otomatik-Kontrol-ve-Bildirimler.md)

## Listeyi seçmek

**Takip Listeleri** ekranında önce liste sahibini, ardından **Takipçiler** veya **Takip edilen** yönünü seçin. Kendi hesabınızın ve erişebildiğiniz kaydedilmiş profillerin kayıtlarını ayrı ayrı değerlendirin.

Liste erişimi Instagram'ın verdiği yetkiye ve yanıta bağlıdır. Bir profilin sayaçlarını görebilmek, o profilin bütün takipçi listesini okuyabileceğiniz anlamına gelmez.

<a name="ilk-kayit-ve-sonraki-karsilastirma"></a>

## İlk kayıt ve sonraki karşılaştırma

İlk tamamlanan tarama başlangıç kaydıdır. Daha sonra tamamlanan uygun bir tarama, önceki kayıtla karşılaştırılır.

Örneğin ilk kayıtta A, B ve C; sonraki tam kayıtta A, C ve D varsa uygulama B'nin artık görünmediğini ve D'nin yeni göründüğünü belirleyebilir. Bu, iki kontrol arasındaki net farktır. Olayın kesin saati veya aradaki bütün hareketler bilinmez.

Uygulamayı kullanmaya başlamadan önceki takipten çıkışlar bu yöntemle geriye dönük bulunamaz.

```mermaid
flowchart LR
    A[İlk tam liste] --> B[Başlangıç kaydı]
    B --> C[Sonraki tam liste]
    C --> D[Yeni ve artık görünmeyen üyeler]
    E[Eksik liste] --> F[Önceki tam kaydı koru ve uygun zamanda devam et]
```

## Büyük listeler nasıl okunur?

Listeler sayfa sayfa alınır. Bir sayfanın bitmesi bütün listenin bitmesi değildir. Ekranda okunan üye/sayfa sayısı, devam durumu ve varsa bekleme açıklaması gösterilir.

Küçük bir listede tek sayfa yeterli olabilir. Büyük bir listede bağlantı, kuyruk, istek aralıkları ve bütçe toplam süreyi uzatır. Profil sayısı değişirken yapılan bir taramada beklenen ve okunan sayılar da uyuşmayabilir.

<a name="eksik-listeyi-anlamak"></a>

## Eksik listeyi anlamak

**“Liste henüz tamamlanmadı”** veya **“Okunan liste ile profilindeki sayı uyuşmadı”** açıklaması, eksik verinin tam liste gibi kabul edilmediğini gösterir.

- Önceki tamamlanmış liste korunur.
- Geçerli devam bilgisi varsa tarama uygun sayfadan sürdürülebilir.
- Devam bilgisi kullanılamıyorsa yeni tarama baştan başlayabilir; eski ve yeni kısmi üyeler tek bir tam liste gibi birleştirilmez.
- Eksik listeden kesin takipten çıkan hesabı sonucu üretilmez.

Ekrandaki devam veya tekrar zamanını izleyin. Listeyi sonsuza kadar durmadan sorgulamak yerine eksik durum görünür bırakılır. [Eksik liste için sorun giderme](Sorun-Giderme.md#liste-tamamlanmiyor).

## Kimin geldiğini veya gittiğini görmek

Windows'ta iki uygun tam liste oluştuğunda karşılaştırma alanını kullanın. Liste değişikliği kaydından **Değişen hesapları gör** ile ilgili üyeler açılabilir. Kayıt kapsamı yeterliyse geri takip etmeyenler gibi iki yönlü liste karşılaştırmaları da yapılabilir.

Yalnız “takipçi sayısı 1 azaldı” bilgisi, giden kişinin kimliğini vermez. Üye adları için ilgili listelerin okunması gerekir.

## Otomatik yeni taramalar

Windows'ta otomatik profil kontrolü ve otomatik liste karşılaştırması açıkken kendi hesabınız ve izlenen profiller değerlendirilir. Sayaç değişimi ilgili listeyi öne alabilir. Sayı aynı kalsa da günlük tam kontrol hedefi korunur; bir kişi çıkıp başka biri eklendiğinde toplam sayı değişmeyebilir.

Bu hedefler mevcut istek sırası ve beklemelerle uygulanır. Bir dakikalık profil kontrolü seçmek, bütün listeyi her dakika baştan okuma sözü değildir.

## Arama ve fotoğraflar

Liste içindeki arama, yüklenmiş üyeleri filtreler; henüz okunmamış üyeleri kendiliğinden bulmaz. Fotoğraflar ayrı yüklenir. Görselin gelmemesi üyenin listede bulunmadığı anlamına gelmez.

Liste üyesini özel takibe almak için kaydetme/izleme eylemini ayrıca kullanın. Panelin açılması bu tercihi otomatik değiştirmez.
