# Liste sahibi kimliği — Windows 0.2.44

Takip Listeleri ekranında seçili hesap, 56 px yerel profil fotoğrafı, belirgin @kullanıcı adı ve ikincil profil adıyla gösterilir. Mevcut yerel seçim kutusu ve klavye erişimi korunur; seçim değişince görsel ve ad aynı sayısal hesap kimliğinden alınır. Fotoğraf yoksa mevcut baş harf gösterimi kullanılır. Uzun ad tek satırda kısalır; tam adı fareyle üzerine gelince görünür. Liste tarama ve karşılaştırma davranışı değişmedi.

Doğrulama ayrı sentetik depolarla yapıldı; gerçek Instagram isteği gönderilmedi:

- `check-profile-search.mjs`: 12 kontrol grubu geçti. Seçili hesap/fotoğraf/ad eşleşmesi, fotoğrafsız hesap, güvenilmeyen resim adresi, HTML kaçışı, uzun ad, yatay fotoğraf/metin hizası, 960 px pencere ve açık/koyu/OLED temalar dahil. JavaScript hatası ve harici renderer isteği yoktu.
- 62 mevcut Node testi ve `npm run check` geçti; dört dildeki 1136 metin eşleşiyor. Yeni metin anahtarı eklenmedi.
- `check-functional-desktop.mjs`: yeni 0.2.44 EXE'sinde 36 kontrol geçti; liste seçimi, karşılaştırma, ilişki filtreleri, yerel arama ve altı ekranda taşma kontrolü dahil.
- `check-navigation-desktop.mjs`: yeni EXE'de 11 klavye/ilk kullanım kontrolü geçti; seçili liste sahibine göre yenileme kısayolu dahil. İki paketli kontrolde de harici istek yoktu.

Yerel ekran görüntüleri `artifacts/profile-search-ui/list-owner-*.png` altında tutulur; Git'e eklenmez. Android bu odaklı Windows düzenlemesinde değiştirilmedi. Backend'de yalnız sürüm bilgisi değişti; Python test kümesi yeniden çalıştırılmadı.
