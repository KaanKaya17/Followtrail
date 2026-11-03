# Bildirim kartları — Windows 0.2.43

3 Kasım 2026. Bildirim gönderme/tercih/kayıt mantığı değiştirilmeden uygulama içindeki Bildirimler ekranı yenilendi.

## Davranış

- Tek bir hesap içeren kart, sayısal hesap kimliğiyle cihazdaki mevcut profil görselini kullanır; geçmişte dondurulmuş kullanıcı adı değiştirilmez. Birden fazla hesabın değişikliklerini içeren kayıtta grup simgesi ve her önizlemede kaynak kullanıcı adı gösterilir. Görsel indirme isteği yapılmaz.
- Sayısal değişiklikler önceki → yeni ve işaretli farkla gösterilir. Sıfır korunur; bilinmeyen önceki değer sıfıra çevrilmez. Kullanıcı adı/ad/site değişiklikleri kaçış uygulanmış kısa metin çiftleriyle; diğer metin değişiklikleri kısa durumla sunulur. Tam içerik mevcut ayrıntı penceresindedir.
- Kart başına en fazla üç değişiklik önizlenir; fazlasının sayısı belirtilir. Sayısal alanlar öne alınır. Seçili tür karma kayıtta varsa ilgili önizleme öne gelir; kaydın bütün değişiklikleri ayrıntıda korunur. Kaynak dizisi veya şifreli kayıt sırası değiştirilmez.
- Liste önizlemesi, kaydedilmiş eski/yeni kişi adlarından her yönde en fazla iki kullanıcı adı ve kalan sayıyı gösterir. Eski sayı özetinde ad yoksa ad üretilmez. “Son kayıtta yok” kesin takipten çıkış veya engelleme olarak adlandırılmaz.
- Tümü/Okunmamış filtresine ek olarak Tüm türler, Profil, Liste değişiklikleri ve Profil görünürlüğü seçenekleri vardır. Tür sayıları, mevcut okunma filtresinde en az bir ilgili değişiklik içeren kayıtları sayar; karma bir kayıt birkaç türde bulunabilir. Filtreleme kayıtları okundu yapmaz, Instagram isteği göndermez. Boş eşleşme ekranı iki filtreyi birlikte sıfırlayabilir.
- Okunmamış kayıtlar kenar, nokta ve metinle ayrılır. Kart açılınca mevcut yerel okundu işlemi çalışır; Windows bildirimi için seçilmiş olma, teslim edilmiş olma diye sunulmaz. Hesap değişiminde iki filtre de sıfırlanır.
- Bildirimler ekranındaki işlem/mola paneli içerik akışındadır; kartları örtmez. Tek panel elemanı Özet/Bildirimler veya diğer ekranlar arasında taşınır. Kısıtlama metni aynı ekranda ikinci banner olarak tekrarlanmaz. Saklama/teslim açıklamaları Kullanım notları'na taşındı; HTTP ve özgün tanı metni İstek bilgileri'nde korunur.

## Doğrulama

Gerçek hesap deposu yerine ayrı `TrackerQA-*` dizinleri, sentetik olaylar, mevcut JPEG örneği, gerçek paketli worker/IPC ve DPAPI kullanıldı. Ekran görüntüleri yerel `artifacts/desktop-0.2.43-qa/notifications/` altında tutulur ve Git'e eklenmez.

- `check-notifications-desktop.mjs`: 5 senaryo grubu geçti. Dondurulmuş ad/güncel görsel ayrımı, eski → yeni/fark, sıfır/bilinmeyen, HTML kaçışı, karma grup, sınırlandırılmış kişi önizlemesi, eski adsız liste kaydı, eksiksiz ayrıntı, yerel okundu kalıcılığı, birleşik filtre/boş sonuç, tek satır içi mola paneli, dört dil/tema, 960×680 pencere ve hesap geçişi sınandı. Renderer HTTP isteği ve JavaScript hatası yoktu; filtreler istek kapısını/tercihleri değiştirmedi.
- `check-functional-desktop.mjs`: 36 mevcut paketli kontrol geçti; sessize alınmış değişikliklerin ayrıntıda kalması ve yerel okundu işlemi dahil.
- `check-accounts-desktop.mjs`: 10 çoklu hesap kontrolü geçti.
- `check-navigation-desktop.mjs`: 11 klavye/ilk kullanım kontrolü geçti; okunmamış boş durumu ve sıfırlama dahil.
- 62 Node testi ve `npm run check` geçti; dört dilde 1136 metin eşleşir.
- Worker ve Windows 0.2.43 paketi üretildi. Backend'de yalnız sürüm bilgisi değişti; Python test kümesi bu UI çalışmasında yeniden çalıştırılmadı. Son backend/liste kurtarma doğrulaması 0.2.42'deki 242 testtir.

Yeni Instagram uç noktası veya sosyal işlem eklenmedi. Gerçek Windows toast teslimi ve canlı Instagram yanıtları bu arayüz testinin konusu değildi. Android APK bu aşamada derlenmedi; CPU/RAM veya önceki sürümle performans farkı ölçülmedi.
