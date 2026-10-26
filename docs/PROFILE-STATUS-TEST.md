# 0.1.22 kontrol durumu testi

1. Önceki uygulamayı silmeden APK'yı güncelle. Özet ekranında kendi kullanıcı adının altındaki kısa durum alanına bak: son başarılı profil okuması ve otomatik kontrolün durumu görünsün. Otomasyon kapalıysa kapalı yazmalı; ekrana girmek izlemeyi açmamalı.
2. Kişiler ekranını aç: her kart kendi kontrol zamanını ve bekleme nedenini göstersin. Kayıtlı bir kişinin analizini açınca da aynı durum alanı bulunmalı. Bilgiler açılışta ve görünürken yaklaşık 30 saniyede yerel kayıttan güncellenir. Bu işlem ağ üzerinden yeni profil/liste kontrolü değildir.
3. Durum alanına dokun: başarılı profil saati, en erken yerel kontrol hedefi (uygunsa), takipçi ve takip edilen yönünde alınan hesap sayısı/kapsam/son sayfa saati ayrı olsun. Hiç okunmayan yön Henüz kayıt yok yazsın. Zaman çizelgesi ve Listeleri incele mevcut ekranlara götürsün.
4. Yarım listede Liste devamı bekliyor; sınırlı/belirsiz kayıtta açık kapsam ifadesi; iki liste sonu gözlenmişse bu bilgi görünsün. Sayı değiştiği için veya günlük hedef dolduğu için tamamlanmış liste tekrar uygunsa Liste güncellemesi sırada yazsın. Bu ifadeler o anda gerçek ağ isteğinin başladığı anlamına gelmez.
5. Son karşılaştırılabilir profil kontrolünde fark yoksa ilgili mesajı gör. Hızlı izle açıkken hızlı oturum durumu, bildirim izni kapalıysa izin beklemesi, ağ yoksa bağlantı beklemesi uygun olsun. 429'u bilerek üretme; mevcut mola varsa durum alanı beklemeyi göstersin ve süreyi kısaltmasın.
6. Dil / Language → English ile aynı akışları kontrol et. Büyük yazıda metinler kesilmeden kaydırılabilsin; detay penceresinin kapatma/geçiş düğmeleri erişilebilir olsun. Geri dönüş ve favoriler çalışmaya devam etsin.

Takipçi farkında isim göstermek için iki uygun liste okuması gerekir. Sayı farkı tek başına kimliği vermez. İşlem geçmişi ve liste farkları zaten yeni görülen/görünmeyen hesapları gösterir; bu sürüm bildirim veya fark hesabını değiştirmez.

Yerel testler ayrı debug paketi ve sentetik taşıma kullanır. Canlı Instagram/telefon arka plan gecikmesi ve pil tüketimi bu arayüz değişikliğinde doğrulanmış sayılmaz.
