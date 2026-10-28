# Geliştirme notları

## Anonim erişim karşılaştırması — 28 Ekim 2026

Windows 0.2.8'de ilk adım: sağlıklı bağlı oturumda hedef ID bulunamadığında son bilinen kullanıcı adına tek çerezsiz profil sayfası isteği. Pozitif sonuç için aynı sayısal kimlik doğrulanır. İki tarafın da bulunamaması nedeni açıklamaz; giriş ekranı, kimliği doğrulanamayan HTTP 200 ve sınırlama belirsizdir. Arayüz yapılan yöntemi ve sınırlamaları açıklar.

Sonraki geliştirmeler:

- **Anonim ID → güncel kullanıcı adı:** Kurulu kütüphanenin `user_info_v2_gql` yolu, oturum olmadan sorgu hazırlayabildi; sunucunun anonim isteği kabul ettiği doğrulanmadı. Üretime eklenmedi. Kullanıcı adı değişikliği + engelleme sorununu çözebilmesi için aynı ID ile canlı, tekrarlanabilir yanıt gerekir. Giriş/sınırlama durumunda kayıtlı oturum enjekte edilmemeli ve alternatif adreslerle sınırlama aşılmamalı.
- **Çerezsiz GET ve tarayıcı görünümü:** Kullanıcının oturumsuz tarayıcıda profil görmesi, ham HTTP GET'in aynı kimliği döndürdüğünü garanti etmiyor. Mevcut ayrıştırıcı yalnız doğrulanabilen profil JSON'u / metaverisiyle olumlu sonuç verir. Yeni biçimler format temelli kanıt ve testle eklenmeli; önerilen hesap listelerinden eşleşme çıkarılmamalı. Gerekirse ileride yerel, ayrı oturumlu tarayıcı desteği değerlendirilir; mevcut kullanıcı tarayıcısı veya hesap çerezi kullanılmaz.
- **Kalıcı ID / kullanıcı adı geçmişi:** Başarılı mevcut gözlemlerden öğrenilen ad değişikliklerini dönen sayaç geçmişinden ayrı saklama. İki kontrol arasında hem adını değiştirip hem engelleyen kişinin yeni adı hiç gözlenmemiş olabilir; bu sınır açık kalmalı.
- **Bağımsız kontrollü doğrulama:** Yetkili test hesaplarıyla normal görünürlük, yalnız engelleme, yalnız ad değişikliği, ikisi birlikte ve geçici hesap kapatma senaryoları. Kişisel hesabı dondurmak test önkoşulu yapılmamalı. Başarı yüzdesi ancak yeterli ölçümle raporlanmalı.
- **Platform kapsamı:** Bu erişim karşılaştırması Windows'tadır. Android'e işlev taşınırken aynı sınıflandırma, oturum ayrımı, yerel mola ve Türkçe/İngilizce açıklamalar korunmalı. Web tasarım önizlemesi gerçek anonim kontrol yapıyor gibi sunulmamalı.

## Uzun dönem sayaç analizi

Kullanıcıyla daha önce konuşulan kalıcı günlük özetler henüz eklenmedi. Windows'ta 366 gözlem, 5 dakikalık aralıkta yaklaşık 30,5 saat kapsar. Haftalık/aylık analiz için dönen ayrıntılardan bağımsız günlük özetler gerekir. Profil zaman tüneli biyografi/fotoğraf değişikliklerini ayrı saklar; bu sayaç sorununu çözmez.
