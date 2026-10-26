# 0.1.21 hızlı izleme testi

Test için kontrolündeki bir hesap kullan. Önceki uygulamayı silmeden aynı imzalı APK'yı güncelle; bağlı hesap, favoriler, notlar ve kayıtlı listeler kalsın. 429/oturum doğrulaması varsa bekleme kaldırılmadan teste devam etme.

1. Kişilerden test hesabının analizini aç, Hızlı izle seç ve gerekiyorsa bildirim izni ver. Kalan süre ve sıradaki kontrol kartı görünmeli. Seçilen hesabın 15 dakikalık normal profil/liste otomasyonu da açılır.
2. İlk okuma/varsa başlangıç listelerinin bitmesini bekle. Test hesabının biyografisini değiştir, APK'yı görünür tut. Profil kontrolü 60 saniye tazelik hedeflidir; liste işi, ağ ve cihaz geciktirebilir. Bildirim ve Zaman çizelgesi kaydı gelmeli. Aynı sayılarda tamamlanmış listeler yeniden okunmamalı; İstek bilgileri sayacı liste sayfalarıyla artmamalı.
3. Kayda dokun: işlem başlangıcı, veri alımı, karşılaştırma kaydı ve önceki karşılaştırılabilir gözlem ayrı olsun. Saatler cihazın saatidir; kesin değişim saati iddiası olmasın. Eski kayıtların tutulmayan saatleri Kaydedilmedi olarak görünür.
4. Kontrolündeki hesapta takipçi veya takip edilen sayısını değiştir. Sonraki uygun profil kontrolünden sonra ilgili yön sıraya girmeli; istekler en az 15 saniye aralıklı olsun. Diğer yönün tamamlanmış taze kaydı korunmalı. İlk okuma baseline; fark için iki uygun okuma gerekir. Sayı aynı ama kişiler farklıysa günlük tarama gerekir.
5. Liste karşılaştırmasının ayrıntılarını aç: yeni görülen/görünmeyen kullanıcı adları, toplam sayılar ve kapsam görünsün. Tarihsel kayıtta her taraftan en fazla 10 kişi örneği vardır; son okumanın bütün kayıtları liste ekranındadır.
6. ⋯ → İşlem geçmişi bütün hesapları; kişi Zaman çizelgesi o kişiyi göstermeli. Filtreyi değiştir, geri dön, ekranı döndür: yeni istek olmamalı. Dil / Language → English ile metinleri kontrol et.
7. Ana ekrana/başka uygulamaya geç: hızlı okuma iptal olur. Geri dönmek ilk başlatılan 15 dakikayı uzatmaz. Normal Android arka plan işi en erken 15 dakika hedefiyle çalışabilir; Doze/üretici kısıtları geciktirebilir. Hızlı izlemeyi bitir sadece hızlı oturumu kapatır; tam durdurma için ayrı profil ve liste otomasyonunu kapat.
8. Hata çıkarsa hızlı oturum durmalı, önceki başarılı veri kalmalı ve işlem geçmişi hata türünü göstermeli. 429'u bilerek üretme veya aynı denemeyi tekrarlama. Liste geri oku eski çalışan davranışını korumalı. Kişiyi silince sadece onun geçmişi silinmeli.

Yerel doğrulama sentetik taşımalı ayrı debug paketindedir. Canlı Instagram'da dakika sıklığının kabulü, fiziksel cihaz arka plan gecikmesi ve pil tüketimi henüz ölçülmedi. Yeni sürüm önizlemedir.
