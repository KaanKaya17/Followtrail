# Yanıt paylaşımı envanteri — 5 Kasım 2026

Kaynak: `a33802a`, Windows 0.2.55 / Android 0.1.85. Bu çalışma uygulama davranışını değiştirmeden, yanıt üreticilerini ve tüketicilerini izler. Dört Windows senaryosu üretim sınırında sentetik yanıtlarla çalıştırıldı; Android için kaynak incelemesi yapıldı. Gerçek kasaya tanı yazılmadı, tanı amacıyla ek Instagram isteği gönderilmedi. Açık uygulamanın normal otomasyonu bu incelemeden bağımsızdır.

## Sonuç

Paylaşım yalnız son eklenen liste başlangıcıyla sınırlı değil. Tek profil okumasının özet, karşılaştırma geçmişi, günlük istatistik, bildirim kaydı ve biyografi arşivini beslediği çevrimdışı olarak doğrulandı. Buna rağmen **erişim teşhisi → profil bilgisi**, **geçici profil ayrıntısı → kişiyi kaydetme** ve **aynı anda aynı fotoğrafı indirme** sınırlarında tamamlanmamış paylaşım var. Bu envanter, uygulamanın bütün ağ trafiğinin bir merkezde birleştirildiği veya toplam isteklerin belirli bir yüzdesinin gereksiz olduğu anlamına gelmez.

## Paylaşımın mevcut olduğu akışlar

1. **Profil yanıtı → birden çok kayıt/görünüm.** Windows'ta `read_profile` tek yanıtı ayrıştırır; `record_profile` aynı profil üzerinden alan farkları, olay/bildirim kaydı, günlük istatistikler, kısa geçmiş, görsel/biyografi geçmişi ve mevcut hesap/kişi bilgisini üretir. Profil bağlantıları da aynı profil gövdesinden ayrıştırılır. Bu tüketiciler ayrı profil sorguları göndermez. Android'de `Observations.own/person` aynı `Profile` ile karşılaştırma ve kontrol günlüğü üretir; `SessionVault.write` aynı gözlemden günlük ve görsel geçmişi günceller. Kaynaklar: [Engine](../interface/desktop/backend/engine.py), [Profil ayrıştırıcı](../interface/desktop/backend/network.py), [Android gözlemleri](../app/src/main/java/dev/kaankaya/instagramtracker/Monitoring.kt), [SessionVault](../app/src/main/java/dev/kaankaya/instagramtracker/SessionVault.kt).
2. **Profil kontrolü → otomatik liste başlangıcı.** Windows'un bağlantı kapsamlı, 20 küçük kayıtla sınırlı 60 saniyelik `ProfileBasis` paylaşımı var. Yeni gözlem zamanı uydurmaz. Android otomatik sayfa işlerine mevcut `ListBasis` değerini taşır. Bu, liste üyelerinin profil yanıtından çıkabildiği anlamına gelmez. Kaynaklar: [ProfileBasis](../interface/desktop/backend/profile_basis.py), [AutomaticEngine](../app/src/main/java/dev/kaankaya/instagramtracker/AutomaticEngine.kt). Önceki ölçüm: [6 → 4 örneği ve büyük liste sınırı](API-EFFICIENCY-TEST.md).
3. **Profil panelini yeniden açma.** Windows paneli 5 dakikalık/20 kayıtlık geçici önbellek kullanır ve aynı kimliğin devam eden okumasının sonucunu paylaşabilir. Android `ViewedProfiles` mevcut hesap/izlenen profil gözlemi ile oturuma bağlı geçici sonucu birleştirir; panel tazeliği 15 dakikadır. Bunlar aynı otomatik kontrol aralığı değildir. Windows panel önbelleği arayüzde, backend `profile_detail` ise ayrı çağrıldığında yeni okuma yapar; bu nedenle yalnız backend'e bakıp her panel açılışının istek gönderdiği söylenemez. Kaynaklar: [Windows paneli](../interface/web/profile-details.js), [Android önbelleği](../app/src/main/java/dev/kaankaya/instagramtracker/ViewedProfiles.kt), [Android paneli](../app/src/main/java/dev/kaankaya/instagramtracker/MemberProfileActivity.kt).
4. **Arama yanıtı → sonuçlar, fotoğraf ve ID ile ekleme.** Sorgu bekletme, aynı sorgu için 60 saniyelik sınırlı önbellek ve eski sorgu sonucunun görünümü ezmesini engelleyen sahiplik mevcut. Sonuçtaki sayısal ID kullanılarak ayrıca kullanıcı adı → ID çözümlemesi yapılmadan profil okunabilir. Arama yanıtı normalde takipçi/takip edilen sayılarını sağlamadığından profil okumasını tümüyle kaldırmak doğru olmaz. Kaynaklar: [Windows arama](../interface/web/profile-search.js), [backend](../interface/desktop/backend/search_cache.py), [Android](../app/src/main/java/dev/kaankaya/instagramtracker/SearchResultsCache.kt), [ID ile ekleme](../interface/desktop/backend/engine.py).
5. **Liste sayfası → yerel arama, filtreler, farklar ve fotoğraf adresleri.** Aynı normalize üye kayıtları tam/kısmi liste görünümüne, karşılıklı takip filtrelerine, eklenen/eksilen kişilerin karşılaştırmasına ve tarihsel olaylara gider. Yerel kaydırma/arama yeni Instagram sayfası değildir; kayıtlı liste üzerinde çalışır. Görsel URL'si listeden gelir; her fotoğraf için profil API sorgusu gerekmiyor. Kaynaklar: [Liste sayfaları](../interface/desktop/backend/list_pages.py), [Windows liste ayrıştırıcısı](../interface/desktop/backend/network.py), [Android listeleri](../app/src/main/java/dev/kaankaya/instagramtracker/FollowLists.kt).
6. **Hikâye akışı → takvim, içerik bilgileri ve yerel medya.** Akış yanıtından hikâye kimliği, zaman, tür ve desteklenen etiket/paylaşım bilgileri ayrıştırılır. Takvim/yerel medya açılışı akışı tekrar okumaz. Başarıyla kaydedilmiş dosyası mevcut hikâye tekrar indirilmez; yalnız eksik metadata kaydı, dosya indirilmiş gibi kabul edilmez. Açık "hikâyeleri kontrol et" işlemi yeni akış isteğidir. Kaynaklar: [Windows akışı](../interface/desktop/backend/engine.py), [contains/record](../interface/desktop/backend/stories.py), [Android kaynak ve arşiv](../app/src/main/java/dev/kaankaya/instagramtracker/StorySource.kt), [Android arşiv](../app/src/main/java/dev/kaankaya/instagramtracker/StoryArchiveStore.kt).
7. **Profil linkleri ve Threads bağlantısı.** Desteklenen bağlantılar profil yanıtından alınır; ayrı Threads varlık sorgusu eklenmez. Site logosu için yerel ikonlar kullanılır. Bağlantının kullanıcı tarafından tarayıcıda açılması ayrı bir kullanıcı eylemidir. Kaynak: [Profil linkleri](../interface/desktop/backend/profile_links.py), [arayüz](../interface/web/profile-links.js).
8. **Tamamlanmış fotoğraf indirmelerini kullanma.** Windows küçük görselleri hesap/kimlik/URL kapsamıyla 15 dakika, en fazla 64 kayıt tutar. Android bellek ve yerel görsel önbelleği kullanır. Bu CDN tasarrufudur; profil API sorgusuyla aynı şey değildir. Tamamlanmış önbellek, henüz tamamlanmamış aynı indirmeyi mutlaka birleştirmez. Kaynaklar: [Windows](../interface/desktop/backend/thumbnails.py), [Android](../app/src/main/java/dev/kaankaya/instagramtracker/AvatarImages.kt).

## Doğrulanan üç boşluk

### 1. Başarılı erişim teşhisi hedefin diğer profil alanlarını aktarmıyor

Windows `check_access`, önce kendi hesabına ait taze profil okumasıyla oturum sağlığını sınar. Sonra `access_checks.probe` hedefe `users/{id}/info/` gönderir. Başarılı yanıtın görünürlük ve kullanıcı adı bilgileri alınır; takipçi/takip edilen, biyografi ve diğer profil alanları normal profil deposuna aktarılmaz.

Sentetik deneyde iki API çağrısı vardı: kendi profilinin sağlık okuması ve hedef profili. Hedef yanıtta **77 takipçi / yeni biyografi** gelmesine rağmen kayıtlı kişinin **2 takipçi / eski biyografi** bilgisi kaldı. Erişim sonucu doğru biçimde "accessible" oldu. Bu, normal profil gözleminin güncellendiğini göstermez. Sonraki gerçek profil okuması ayrı sorgudur; panelin kendi önbelleği nedeniyle her açılışın mutlaka yeni istek yaptığı iddia edilmiyor.

Öneri: Başarılı hedef yanıtı, kimliği ve alanları normal profil ayrıştırıcısıyla ayrıca doğrulanabiliyorsa aynı okuma sonucunun uygun tüketicilere aktarılması. Daha az alan içeren geçerli görünürlük yanıtı tüm profil ayrıştırılamadı diye bozulmamalı. **Kendi profilinin sağlık okumasını körlemesine kaldırmak ayrı ve yanlış bir değişiklik olur:** giriş/erişim sorunu ile hedefin bulunamamasını ayıran kanıttır. Android'de eşdeğer teşhis akışı bu incelemede doğrulanmadı; burada iki platformda aynı akış varmış gibi bir sonuç çıkarılmıyor.

### 2. Windows'ta ayrıntısı okunmuş liste kişisini kaydederken ayrıntılar aktarılmıyor

`profile_detail` izlenmeyen bir liste üyesinin tam bilgisini arayüze geçici sonuç olarak verir. Sonraki `save_person`, kayıtlı kişi yoksa üyeyi `known_person` üzerinden listeden oluşturur. Geçici profil sonucundaki sayılar/biyografi bu aktarımda kullanılmaz.

Deney: **1 API çağrısıyla** panel için 77 takipçi okundu. Kişiyi kaydetme **0 ek API çağrısı** yaptı; fakat kalıcı kişi kaydında takipçi ve biyografi alanları boş kaldı. Arayüzün geçici önbelleği bu sayıları göstermeye devam edebilir; backend aktarımındaki eksiklik ayrı bir olgudur. Bu araştırmada sonraki her görünümde fazladan bir sorgu olduğu ölçülmedi.

Android `MemberProfileActivity.enableWatch` ise eldeki gösterilmiş `Profile` değerini `PeoplePolicy.record` üzerinden kayda aktarabiliyor. Öneri: Windows'ta da arayüzden keyfî profil gövdesi kabul etmeden, backend'in sahip olduğu kısa süreli ve bağlantı kapsamlı doğrulanmış sonucu kayıt sırasında kullanmak. İlk okumanın gerçek zamanını korumak; izlemeyi açmayı yeni ağ gözlemi gibi kaydetmemek.

### 3. Aynı fotoğrafın eşzamanlı indirmeleri birleştirilmiyor

Windows `Thumbnails` tamamlanan fotoğrafları önbelleğe alıyor; devam eden işleri ise kaynak anahtarıyla paylaşılan tek iş olarak tutmuyor. Sentetik okuyucu bekletilip aynı hesap/ID/URL için iki tüketici birlikte çalıştırıldı: **2 CDN indirmesi**, iki yanıt. Üçüncü tüketici tamamlanmış önbellekten geldi; indirme sayısı 2'de kaldı. Bu deney **0 Instagram API çağrısı** içerir ve gerçek kullanıcıda ne sıklıkta oluştuğunu ölçmez.

Android `AvatarImages.load` da her çağrıda ayrı `Job` oluşturur; bellek/disk önbelleği vardır fakat aynı anda önbelleği kaçıran işlerin tek indirmeyi paylaşması kaynakta görülmedi. Android'de bu yarış gerçek cihazda çalıştırılmadı.

Öneri: Hesap + hedef + resim sürümü/URL anahtarına göre devam eden indirmeyi paylaşmak. Tüketicilerin iptali bağımsız olmalı; bir satır kapanınca diğer görünür tüketicinin indirmesi iptal edilmemeli. Hesap geçişi/silme tüm ilgili tüketicileri geçersizleştirmeli.

## Ayrı isteklerin her zaman gereksiz olmadığı yerler

- Takipçi sayısı, takipçi kimliklerini sağlamaz; değişiklikler için liste sayfaları gerekir. Aynı sayı, aynı üyeler demek değildir.
- Fotoğraf URL'si, fotoğraf dosyası değildir; CDN indirmesi ayrı iştir.
- Arama/liste üyeleri tam profil değildir. Windows liste sınırı ID/ad/kullanıcı adı/fotoğraf adresini tutar; Android ayrıca varsa gizlilik/doğrulanma alanlarını taşıyabilir. Bir alanın gövdede bulunabilmesi, her yanıtta geleceği anlamına gelmez. Bu ek liste alanlarının Windows'ta korunması ayrıca değerlendirilebilir; tek başına profil okumasını kaldırdığı ölçülmedi.
- Elle açık yenileme, süresi dolmuş bilgi, yeni bağlantı ve oturum sağlığı doğrulaması taze okuma gerektirebilir.
- Bütün ham yanıtı saklamak hedef değil. Desteklenen gerekli alanları, kimlik/tarih/kapsam bilgileriyle normalize ederek paylaşmak gerekiyor. Eksik alanı sıfıra çevirmemeli; önbellek kullanımı yeni gözlem veya yeni bildirim üretmemeli.

## Tekrarlama ve öncelik

Windows backend ortamında `python scripts/audit-response-reuse.py` çalıştırıldı. Dört senaryo ve içlerindeki doğrulamalar geçti. Sonuç dosyası yerel `artifacts/response-reuse-audit.json`; gerçek veri veya kimlik içermez ve Git'e eklenmez. Kod [audit script'indedir](../scripts/audit-response-reuse.py). Bu bir performans hızlanma ölçümü değildir; CPU/tepe bellek/soğuk-sıcak koşu ve uygulama genelinde kazanç ölçülmedi.

İlk iki iş: **başarılı hedef profil yanıtını paylaşmak** ve **okunmuş ayrıntıyı kişiyi kaydederken korumak**. Ardından fotoğraf işlerini birleştirmek. Güvenli ortak sahiplik kurulmadan bütün istekleri genel bir URL önbelleğine koymak uygun değil. Bu incelemede üretim kodu, APK, günlük bütçe ve çalışmakta olan Windows sürümü değiştirilmedi.
