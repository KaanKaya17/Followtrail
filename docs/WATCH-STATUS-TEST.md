# 0.1.13 — takip durumu ve bildirim testi

1. `artifacts/InstagramTracker-0.1.13.apk` dosyasını mevcut uygulamanın üzerine kur. Kaldırma/veri temizleme yapma. Kişiler, favoriler, notlar ve mevcut 1/6/24 saat ayarları korunmalı.
2. Kişiler → Analizi aç → Takip durumu kartını aç. Son başarılı profil okuması kayıt zamanını göstermeli. Bu, son deneme veya profilin Instagram'daki kesin değişim saati değildir.
3. Değişiklik bildirimi anahtarını aç; bildirim izni sorulursa izin ver. 15 dakika, 30 dakika, 1 saat, 6 saat, 12 saat ve 24 saat seçenekleri görünmeli. Bir aralık seçip kaydet; açıkken Kontrol aralığını değiştir ile düzenle. Kapatıp açınca tercih korunmalı. Aralık değiştirmek hemen istek göndermez.
4. Durumu güncelle'ye bas. Yerel durum yenilenmeli; İstek bilgileri'ndeki deneme sayısı artmamalı. Ekran aynı kalırken zamanın geçmesi kendiliğinden canlı güncelleme yapmaz. Arka plan işi sürerken sayaç bağımsız olarak değişebilir; bu düğme isteği başlatmaz.
5. Tek, kontrolündeki test hesabıyla başla. Bir başarılı okumada biyografi/fotoğraf başlangıç bilgisi oluşsun. Sonra test hesabında yalnız biyografiyi değiştir. İzleme açıkken sonraki başarılı kontrolde, sayılar aynı kalsa da Biyografi değişti bildirimi ve Profil geçmişi kaydı beklenir. İlk bilinen alan başlangıçtır, bildirim değildir.
6. Kullanıcı adı ve fotoğraf için ayrı denemeler yap. Kullanıcı adı sabit hesap kimliğiyle izlenir. Fotoğrafta iki bilinen okumanın karşılaştırılabilir kimliği veya varsayılan fotoğraf durumu farklı olmalı. Yalnız URL farkı bildirim değildir; görseller arşivlenmez. Alan dönmezse bu değişim doğrulanamaz.
7. Uygulamayı arka plana al. 15 dakika yalnız hedef/minimum aralıktır: Android'in işi çalıştırması gecikebilir. Bütün kişiler ve açık ana hesap otomatik yenilemesi ortak 15 dakikalık bütçeyi paylaşır. Her çalışmada tek kişi okunur; 20 kişide hepsinin 15 dakikada bir okunması beklenmez. Favori olmak sırayı öne almaz. Kontrol zamanı gelen kişilerde önceki hedefe göre sıra gösterilir; kesin bitiş saati verilmez.
8. Bildirim iznini sistem ayarlarından kapatıp uygulamaya dön. Takip durumu iznin kapalı olduğunu ve otomatik kontrolün durduğunu göstermeli. İzni açıp uygulamaya dönünce yerel durum yeniden değerlendirilmeli. Çevrimdışıyken internet beklendiği yazmalı.
9. Instagram 429/bekleme yanıtı verirse denemeyi durdur. Mola durumu ve otomatik takibin duraklaması görünmeli. Mola dolduğunda kendiliğinden tekrar deneme olmaz; izinli bir elle okuma başarılı olunca yeniden açılır. Mola veya uygulama verisini sıfırlama.
10. İzlemeyi kapatmak ilgili bildirimi iptal etmeli. Biyografinin tamamı bildirimde gösterilmez; ayrıntılar uygulamadadır. Kilit ekranı görünümü Android ayarlarına bağlıdır.

Eski sürüme dönmek yeni dakika ayarlarını koruma garantisi vermez: 15/30 dakika 1 saat, 12 saat 24 saat uyumluluk değeriyle açılır. Önceki APK ve kaynak ayrı yedektedir; kullanıcı oturumu bu yedeğe alınmamıştır.

Geliştirme kontrolleri sentetik taşıma ile ayrı debug paketinde yapılır. Bu dokümanın yazılması canlı Instagram'da süre veya alan kapsamının doğrulandığı anlamına gelmez. Bu sürümde 1–2 dakikalık hızlı takip yoktur.
