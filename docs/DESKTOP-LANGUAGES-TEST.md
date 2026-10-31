# Windows 0.2.20 · uygulama dilleri

31 Ekim 2026. Türkçe, English, Español ve Português (Brasil) ortak yerel katalogdan sunulur. Android 0.1.63 bu değişiklikte yeniden paketlenmedi.

## Doğrulama

- `cd interface && npm run check`: üç çeviri kataloğunda aynı 999 kaynak mesajı, çoğul biçimleri ve kaynak metinle aynı yer tutucular; üretilen JavaScript ve ortak tema güncel.
- `node --test desktop/*.test.cjs`: 28 test geçti. Dil biçimleri, geçmiş başlıklarında kullanıcı adının korunması, bildirim üretimi, tepsi ve renderer yaşam döngüsü kapsanır.
- `python -m unittest discover -p 'test_*.py'` (`interface/desktop/backend`): 167 çevrimdışı test geçti. Son dil metni düzenlemelerinden sonra `test_localization test_accounts` grubundaki 26 test ayrıca geçti.
- `node scripts/check-i18n-web.mjs`: 11 tarayıcı kontrolü geçti; seçiciler, yeniden yükleme, sayı biçimleri, kullanıcı biyografisinin korunması ve taşma kontrolü. Dış ağ isteği 0.
- `node scripts/check-functional-desktop.mjs`: son paketli Windows EXE üzerinde 31 kontrol geçti. Sentetik, ayrı şifreli test deposunda dört dil, altı ana ekran, İspanyolca/PT-BR yeniden açılışı, kişi/kendi hesap verileri ve istek kapısının korunması doğrulandı. Dış ağ isteği 0.

Uzun İspanyolca açıklama liste filtresinde yatay taşma oluşturdu. Masaüstü `.toolbar-note` kuralında metin sarmalama ve küçülebilen genişlik tanımlandı; son EXE turunda altı ekran da geçti.

## Kapsam

Tercih cihaz genelindeki şifreli hesap kataloğunda tutulur; hesap geçişinde de aynı dil seçilir. Yerel hizmet hataları ve bildirim türleri aynı JSON kaynaklarını, renderer/tepsi/bildirim üretimi aynı üretilmiş JavaScript kataloğunu kullanır. Kullanıcı içerikleri ve gözlem kanıtları kalıcı olarak yeniden yazılmaz. İngilizce kaynağın yer tutucu kontrolü korunur; yeni diller yalnız kendi metinlerini ekler.

Bu kontroller canlı Instagram girişi veya gerçek Windows bildirim teslimi değildir. Windows'un kendi dosya seçicisi düğmeleri sistem dilinde kalabilir. Kaynak ve yer tutucu kapsamı tamdır; her cümlenin ana dili konuşan bir editör tarafından değerlendirildiği iddia edilmez.
