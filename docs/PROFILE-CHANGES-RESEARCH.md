# Profil değişikliği takibi — araştırma, 25 Ekim 2026

Durum: araştırma ve uygulama önerisi. Çalışan APK 0.1.11; uygulama kodu ve ağ akışları değiştirilmedi. Bu araştırmada Instagram hesabına profil/fotoğraf isteği yapılmadı. Canlı alan varlığı veya değişiklik bildirimi doğrulanmış değildir.

## Sonuç

Kullanıcı adı ve biyografi için mevcut periyodik profil okumasından yararlanmak uygun. Fotoğraf için aynı yanıttaki adresi izlemek mümkün, ancak adres farkı görsel farkıyla eşdeğer sayılmamalı. Kullanıcı adı en düşük belirsizlikli adım; biyografi alanı yanıt verdiğinde karşılaştırılabilir. Fotoğraf kimliği mevcut info yanıtında kontrol edilmeden kesin fotoğraf değişimi sözü verilmemeli.

## Yerel kaynak gözlemleri

İncelenen uygulama kaynak commit'i: 29ec8e76d6391bfecbad0a58c8c8acc0ada7d1d1.

- Models.kt: Profile zaten userId, username, followers, following, observedAt ve isteğe bağlı posts taşır. Biyografi veya fotoğraf metaverisi yok. PeoplePolicy.record kişiyi userId üzerinden eşleştirir ve son 90 gözlemi tutar; favori ve notlar copy ile korunur.
- MobileProfileReader.kt: ilk kullanıcı araması users/{username}/usernameinfo/; sonraki kayıtlı kişi okumaları sabit kimlikle users/{id}/info/. Adın değişmesi tek başına farklı kişiye geçmez. Hesap erişilemezse veya oturum hatası olursa mevcut hata/mola kuralları geçerli kalır.
- Models.kt / ProfileParser.user: yanıttaki ilave biyografi/fotoğraf alanlarını şu an almıyor. Önceki yanıtın saklanmayan alanları sonradan geri getirilemez.
- AccountCodec.kt: her gözlemde username kalıcı olarak yazılıyor. Bu nedenle saklanan eski gözlemlerde gerçekten farklı kullanıcı adları varsa karşılaştırılabilir; uygulama kurulmadan önceki ad geçmişi çıkarılamaz.
- Monitoring.kt / MonitorPolicy.change ve RecentActivity.kt: yalnız takipçi/takip edilen sayı farkını olay sayıyor. Sadece ad, biyografi veya fotoğraf değişmesinde şu an bildirim/akış girdisi oluşmaz.
- SessionVault.kt / EncryptedRecord: hesap kaydı en fazla 512 KiB. Yirmi kişi ve kişi başına 90 gözleme uzun metin/adres/fotoğraf eklemek aynı bütçeyi paylaşır. Örneğin her gözleme yalnız 300 UTF-8 bayt ilave edilmesi 20×90×300=540.000 bayt eder; mevcut JSON ve kayıt verileri hariç bile sınırı aşar. Bu hesap tahmindir, çalışma zamanı ölçümü değildir. Fotoğraf binary verisi bu JSON'a konmamalı.

## Kütüphane ve güncel kaynak kanıtı

Bilgisayarda kurulu instagrapi 3.2.0 kaynakları incelendi; kütüphane çalıştırılıp giriş yapılmadı.

- types.py satır 60–80: User modelinde username, profile_pic_url, profile_pic_url_hd ve biography var. Biography varsayılanı boş metin; bu varsayılan eksik alanı gerçekten silinmiş biyografiden ayırmak için yeterli değil.
- mixins/user.py satır 416–463: user_info_v1 sabit kimliğin info yanıtındaki user nesnesini extract_user_v1'e verir. Uygulamanın mevcut mobil yolu aynı uç ailesidir; her hesabın yanıtında her alanın varlığını kanıtlamaz.
- extractors.py satır 309–318: HD adresi hd_profile_pic_versions veya hd_profile_pic_url_info.url üzerinden türetilir.
- types.py satır 164–188: profile_pic_id, UserShort'ta isteğe bağlı bulunur. User modelinde ilan edilmiş olması söz konusu değil. Bu yüzden kullanılan tam profil info yanıtında sabit fotoğraf kimliğinin geldiği varsayılmaz; ham nesnede kontrollü doğrulanır. Takipçi listesindeki kısa kullanıcıyı sırf fotoğraf kimliği için ek istekle aramak önerilmiyor.
- Belgelenen fotoğraf adresi örnekleri sunucu/ölçek ve sorgu alanları içeriyor. Adres bir görsel kimliği sözleşmesi değildir. URL karşılaştırması, query silme veya dosya adından ID tahmini kesin fotoğraf değişimi olarak sunulmaz.

Birincil kaynaklar:

- [instagrapi 3.2.0 türleri](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/types.py)
- [instagrapi 3.2.0 profil okuyucusu](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/mixins/user.py)
- [instagrapi 3.2.0 dönüştürücüleri](https://github.com/subzeroid/instagrapi/blob/3.2.0/instagrapi/extractors.py)
- [Projenin kullanıcı rehberi](https://github.com/subzeroid/instagrapi/blob/master/docs/usage-guide/user.md)

Bunlar üçüncü taraf kütüphanenin kendi kaynaklarıdır; Meta'nın resmî özel mobil API garantisi değildir. Meta'nın mesajlaşma profil belgesini web araştırma aracıyla açma girişimi web sitesinde 429 döndü; bu, kullanıcının Instagram hesabındaki veya APK'daki yeni bir 429 değildir. Araştırma o sayfanın içeriğine dayanmaz.

## Önerilen uygulama

1. Önce mevcut profil yanıtındaki alanları ayrıştır: biyografi için eksik, null ve açık boş metin ayrı kalsın. Eski kayıtta alan yoksa bilinmiyor. İlk bilinen gözlem başlangıç değeri olsun, değişiklik bildirimi olmasın. Bilinmeyen aralıklarda sessizce güncel veri uydurulmasın; karşılaştırma iki bilinen gözlemin zamanlarıyla anlatılsın.
2. Aynı userId için kullanıcı adını karşılaştır. Kullanıcıya eski ve yeni ad göster. Aynı adın harf büyüklüğü farkı nasıl sunulacağı açıkça belirlensin; kullanıcı eşleştirmesi yine ID ile yapılsın.
3. Biyografide önceki/yeni metin ve gözlem aralığı saklansın. Açık boş metne geçiş gerçek silme gözlemi olabilir; eksik/null alan tek başına silme sayılmasın. Emoji, satır sonları ve bağlantılar korunmalı; sessiz trim/normalizasyon yapılmamalı.
4. Fotoğrafta sabit profil fotoğrafı kimliği her iki yanıtta da varsa önce kimlik değişimi kaydedilsin. Bu, aynı görselin yeniden yüklenmesini de kapsayabilir; görsel içerik farkıyla karıştırılmasın. Yalnız URL varsa bağlantı güncellemesi olarak tutulabilir; kesin fotoğraf değişimi bildirimi üretilmesin. Kimlik yoksa ilk sürümde fotoğraf değişim takibi doğrulanamadı olarak kalsın.
5. Eski/yeni fotoğrafı yan yana göstermek ayrı aşama: gerçekten dosya alınması, boyut/çözünürlük/zaman sınırları ve şifreli medya saklama gerekir. Byte hash farkı yeniden sıkıştırma/ölçek farkında da oluşabilir; algısal benzerlik ise olasılıksaldır. Medya indirme mevcut tek JSON profil GET'i dışında ek CDN trafiğidir; hiç ek istek yok denemez. Bu aşama ilk metin takibinden ayrılmalı.
6. Tüm değişim yorumunu tek ortak ProfileChanges sınırında üret; bildirim, ana sayfa akışı ve analiz aynı sonucu kullansın. Sayılar sabitken de ad/biyografi olayı oluşabilsin. Bildirimde biyografinin tamamını kilit ekranında gösterme; ayrıntı uygulamada olsun.
7. İlk sürümde kayıtlı kişileri kapsa. Kendi hesabının şu an yalnız son özeti tutuluyor; kendi ad/biyografi geçmişini de istenirse aynı sahiplik kurallarıyla ayrıca ele al. Takipçi listesindeki her üyeyi otomatik izlemeye başlama.
8. Mevcut 15 dakikalık profil önbelleği, seçilmiş 1/6/24 saat izleme aralığı, tek etkin istek ve 429/oturum durdurma kuralları korunsun. Yeni alanlar normal profil yanıtından okunabildiğinde ek profil çağrısı gerekmez. Bu, sunucunun alanları mutlaka vereceği anlamına gelmez.
9. Biyografi/fotoğraf metaverisini her sayı gözlemine çoğaltmadan, sadece değişen değerleri sürümleyerek ve kapasiteyi ölçerek sakla. Eski oturum/geçmiş/favorilerin korunması için additive migration ve kontrollü depolama sınırı uygula; daha büyük alan elde etmek için mevcut güvenli kayıt sınırını körlemesine yükseltme.

## Görünüm önerisi

Mevcut kişi analizinde Profil geçmişi: Kullanıcı adı / Biyografi / Fotoğraf sekmeleri veya filtreleri. Ana sayfada aynı Son değişiklikler akışı: kullanıcı adı değişti, biyografi güncellendi; fotoğrafta kanıt düzeyi açık bir ifade. Ayrıntıda eski/yeni değer, önceki ve yeni gözlem zamanı. Kesin değişim anı veya kaçırılmayan anlık takip iddiası yok.

## Kabul testleri

- Eski v1–v4 hesaplardan oturum, geçmiş, favori ve bildirim ayarlarının korunması.
- Aynı ID/ad değişikliği; ad aynı/ID farklı ayrımı. Tek gözlemde sahte olay üretmeme.
- Biyografi eksik/null/boş metin/dolu; Unicode, emoji, satır sonu ve normal metin değişimi.
- Aynı fotoğraf kimliği/farklı URL, farklı kimlik/aynı görüntü olasılığı, eksik kimlik, görsel indirme başarısızlığı. Yanlış fotoğraf değişikliği bildirimi üretmeme.
- Sayı değişmeden metin değişiminde akış ve bildirim; aynı gözlemi yeniden açınca tekrar bildirim olmaması.
- Başarısız/iptal okuma veya 429'da önceki kayıtları koruma; yeni otomatik sorgu döngüsü oluşturmama.
- Kapasite sınırında büyük biyografi/URL sayılarıyla gerçek veri ölçüsü; eski kayıt, oturum ve favori kaybı olmaması.

Uygulama ve canlı kabul bu araştırmanın parçası değildir. Önerilen sıra: kullanıcı adı + biyografi, ardından profil yanıtında fotoğraf kimliği doğrulaması ve gerekirse ayrı medya geçmişi.
