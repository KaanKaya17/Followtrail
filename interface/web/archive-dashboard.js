(() => {
  "use strict";
  const groups = [
    ["connections","Takipler","people",/^connections\/followers_and_following\//],
    ["profile","Profil geçmişi","clock",/^personal_information\/personal_information\/|\/signup_details\.json$/],
    ["messages","Mesajlar","note",/\/messages\/.*\/message_\d+\.json$/],
    ["content","Paylaşımların","image",/^(media\/|your_instagram_activity\/media\/)/],
    ["insights","İstatistikler","activity",/\/past_instagram_insights\//],
    ["activity","Etkinliklerin","heart",/^your_instagram_activity\/(likes|saved|comments|story_interactions|threads|instants)\//],
    ["contacts","Rehber","people",/^connections\/contacts\//],
    ["account","Diğer hesap verileri","lock",/^(ads_information|security_and_login_information|preferences|apps_and_websites_off_of_instagram|personal_information|logged_information)\//],
  ];
  const names = {
    followers:"Takipçiler", following:"Takip edilen", close_friends:"Yakın arkadaşlar",
    blocked_profiles:"Engellediğin hesaplar", pending_follow_requests:"Bekleyen takip istekleri",
    recent_follow_requests:"Son takip istekleri", recently_unfollowed_profiles:"Takipten çıktıkların",
    "follow_requests_you've_received":"Gelen takip istekleri", removed_suggestions:"Kaldırılan öneriler",
    profile_changes:"Profil değişiklikleri", personal_information:"Kişisel bilgiler",
    instagram_profile_information:"Profil bilgileri", signup_details:"Hesap oluşturma bilgileri",
    notes:"Notlar", stories:"Hikâyeler", reels:"Reels", profile_photos:"Profil fotoğrafları",
    other_content:"Diğer paylaşımlar", audience_insights:"Kitle özeti",
    profiles_reached:"Erişim özeti", content_interactions:"İçerik etkileşimleri",
    liked_posts:"Beğendiğin gönderiler", liked_comments:"Beğendiğin yorumlar",
    saved_posts:"Kaydettiğin gönderiler", saved_music:"Kaydettiğin müzikler",
    post_comments:"Gönderi yorumların", reels_comments:"Reels yorumların",
    story_likes:"Beğendiğin hikâyeler", stories_viewed:"İzlediğin hikâyeler",
    polls:"Anketler", quizzes:"Testler", questions:"Sorular",
    synced_contacts:"Eşitlenen kişiler",
  };
  const fieldNames = {
    "First Name":"Ad", "Last Name":"Soyad", "Contact Information":"İletişim bilgisi",
    Username:"Kullanıcı adı", Name:"Ad", name:"Ad", Email:"E-posta", Bio:"Biyografi",
    Changed:"Değişen alan", "Previous value":"Önceki değer", "New value":"Yeni değer",
    "Date Range":"Tarih aralığı", Followers:"Takipçiler", "Accounts Reached":"Erişilen hesaplar",
    "Accounts reached":"Erişilen hesaplar", "Content Interactions":"İçerik etkileşimleri",
    "Instagram Plays":"Oynatma", "Instagram Likes":"Beğeniler", "Instagram Comments":"Yorumlar",
    "Instagram Shares":"Paylaşımlar", "Instagram Saves":"Kaydetmeler",
    title:"Başlık", Title:"Başlık", Caption:"Açıklama", Comment:"Yorum", content:"Mesaj",
    sender_name:"Gönderen", Question:"Soru", Answer:"Yanıt", Author:"Yazar", URL:"Bağlantı", uri:"Dosya",
  };
  const basename = path => path.split("/").at(-1);
  const pretty = value => value.replaceAll("_", " ");
  const insightFields = ["Followers", "Accounts Reached", "Accounts reached", "Content Interactions",
    "Instagram Plays", "Instagram Likes", "Instagram Comments", "Instagram Shares", "Instagram Saves"];
  const inlineFields = new Set(["Comment", "Caption", "Contact Information", "Answer"]);
  const changeNames = {"Profile name":"Profil adı", "Username":"Kullanıcı adı", "Phone number":"Telefon numarası", "Email":"E-posta", "Bio":"Biyografi"};
  const activityTypes = [
    ["likes","Beğeniler"], ["saved","Kaydetmeler"], ["comments","Yorumlar"],
    ["story_interactions","Hikâyeler"], ["threads","Threads"], ["instants","Instants"],
  ];
  const folderType = file => file.name.split("/")[1];
  const dateBound = (value, end = false) => {
    if (!value) return null;
    const day = new Date(value + "T00:00:00");
    if (end) day.setDate(day.getDate() + 1);
    const seconds = day.getTime() / 1000;
    return Number.isSafeInteger(seconds) ? seconds : null;
  };

  function mount(root, {api, t, num, language, openRaw, openLists, autoOpen = false}) {
    let archive, closed = false, opening = false, generation = 0, mediaUrl, searchTimer;
    let filesByName = new Map(), filesById = new Map(), groupByFile = new Map();
    const monthsFormat = new Intl.DateTimeFormat(language, {month:"short", year:"numeric", timeZone:"UTC"});
    const date = new Intl.DateTimeFormat(language, {dateStyle:"medium", timeStyle:"short"});
    const dayFormat = new Intl.DateTimeFormat(language, {dateStyle:"long"});
    const clock = new Intl.DateTimeFormat(language, {hour:"2-digit", minute:"2-digit"});
    const make = (tag, text, css) => {
      const node = document.createElement(tag);
      if (text) node.textContent = text;
      if (css) node.className = css;
      return node;
    };
    const action = (text, run, css = "button secondary") => {
      const node = make("button", text, css);
      node.type = "button";
      node.addEventListener("click", run);
      return node;
    };
    function release() {
      if (mediaUrl) URL.revokeObjectURL(mediaUrl);
      mediaUrl = null;
    }
    function link(value, text = value) {
      const url = window.TrackerProfileLinks.webUrl(value);
      if (!url) return null;
      const control = make("a", text, "archive-dashboard-link");
      control.href = url;
      control.addEventListener("click", event => {
        event.preventDefault();
        api.openLink(url).catch(() => {});
      });
      control.addEventListener("auxclick", event => event.preventDefault());
      return control;
    }
    function profile(value) {
      const username = value?.replace(/^@/, "");
      return username && /^[a-z0-9._]{1,30}$/i.test(username) && /[a-z0-9_]/i.test(username)
        ? link("https://www.instagram.com/" + username + "/", "@" + username) : null;
    }
    const toolbar = make("div", "", "archive-dashboard-toolbar");
    const description = make("p", t("Instagram’dan indirdiğin ZIP dosyasını seç. Veriler cihazında açılır."));
    const load = action(t("Instagram ZIP yükle"), open, "button primary");
    toolbar.append(description, load);
    if (openLists) toolbar.append(action(t("ZIP takip karşılaştırması"), openLists));
    const raw = action(t("Dosya tarayıcısı"), () => openRaw(archive));
    raw.hidden = true;
    toolbar.append(raw);
    const status = make("p", "", "field-hint");
    status.setAttribute("role", "status");
    const guide = make("details", "", "card archive-dashboard-guide");
    guide.open = !autoOpen;
    guide.append(make("summary", t("Instagram ZIP dosyası nasıl alınır?")));
    const steps = make("ol");
    for (const step of [
      t("Instagram Hesaplar Merkezi’nde Bilgilerin ve izinlerin → Bilgilerini dışa aktar bölümünü aç."),
      t("Dışa aktarım oluştur, Instagram profilini seç ve Cihaza aktar seçeneğine dokun."),
      t("Biçim olarak JSON, tarih aralığı olarak Tüm zamanlar seç. Yalnız takip listeleri gerekiyorsa veri seçimini Takipçiler ve takip edilenler ile sınırla."),
      t("Mesaj, profil ve etkinlik geçmişini de görmek istiyorsan bunları veri seçimine ekle veya tüm bilgileri seç."),
      t("Dışa aktarımı başlat. Dosya hazır olduğunda Instagram’dan indir ve bu ekrandaki Instagram ZIP yükle düğmesiyle aç."),
    ]) steps.append(make("li", step));
    guide.append(steps);
    const center = link("https://accountscenter.instagram.com/info_and_permissions/", t("Hesaplar Merkezi’ni aç"));
    if (center) guide.append(center);
    const automation = make("p", t("Tracker şu anda Instagram’dan yeni ZIP istemiyor. İndirdiğin dosyayı cihazında açıyor."), "field-hint");
    guide.append(automation);
    const overview = make("section", "", "card archive-dashboard-overview");
    const tools = make("div", "", "archive-dashboard-toolbar archive-dashboard-tools");
    const searchForm = make("form", "", "archive-dashboard-filters archive-dashboard-global-search");
    const searchInput = make("input");
    searchInput.type = "search";
    searchInput.minLength = 2;
    searchInput.maxLength = 160;
    searchInput.required = true;
    searchInput.placeholder = t("Tüm arşivde ara");
    searchInput.setAttribute("aria-label", searchInput.placeholder);
    const submit = make("button", t("Ara"), "button primary");
    submit.type = "submit";
    searchForm.append(searchInput, submit);
    searchForm.addEventListener("submit", event => {
      event.preventDefault();
      const query = searchInput.value.trim();
      if (query.length >= 2) showSearch(query);
    });
    const compareButton = action(t("İki ZIP karşılaştır"), showComparison);
    tools.append(searchForm, compareButton);
    overview.hidden = tools.hidden = true;
    const cards = make("div", "", "archive-dashboard-categories");
    const body = make("section", "", "card archive-dashboard-body");
    body.hidden = true;
    root.replaceChildren(toolbar, status, guide, overview, tools, cards, body);

    async function open() {
      if (opening || closed) return;
      opening = true;
      load.disabled = true;
      status.textContent = t("Dosya hazırlanıyor…");
      try {
        const result = await api.file("export_browser_open");
        const value = result?.archiveBrowser;
        if (closed) {
          if (value) api.command("export_browser_close", {token:value.token}).catch(() => {});
          return;
        }
        status.textContent = "";
        if (result?.cancelled) return;
        if (!result?.ok || !value) throw Error();
        archive = value;
        filesByName = new Map(archive.files.map(file => [file.name, file]));
        filesById = new Map(archive.files.map(file => [file.id, file]));
        generation++;
        release();
        tools.hidden = false;
        searchInput.value = "";
        raw.hidden = false;
        guide.open = false;
        description.textContent = archive.name + " · " + t("{0} dosya", num(archive.files.length));
        showGroups();
        loadSummary(value);
      } catch {
        if (!closed) status.textContent = t("Arşiv veya seçilen dosya açılamadı.");
      } finally {
        opening = false;
        load.disabled = false;
      }
    }

    function showGroups() {
      const claimed = new Set();
      const items = groups.map(([id, label, , pattern]) => [id, label,
        archive.files.filter(file => (file.kind === "json" || id === "content" && ["image","video","audio"].includes(file.kind)) &&
          !claimed.has(file.id) && pattern.test(file.name) && claimed.add(file.id))]);
      const rest = archive.files.filter(file => file.kind === "json" && !claimed.has(file.id));
      items.at(-1)[2].push(...rest);
      const available = items.filter(([, , files]) => files.length);
      groupByFile = new Map(available.flatMap(group => group[2].map(file => [file.id, group])));
      cards.replaceChildren();
      const controls = available.map(([id, label, files]) => {
        const control = action("", () => selectGroup(id, label, files), "archive-dashboard-category");
        control.dataset.archiveGroup = id;
        control.append(make("strong", t(label)), make("small", t("{0} veri kaynağı", num(files.length))));
        cards.append(control);
        return control;
      });
      body.hidden = !available.length;
      if (controls.length) controls[0].click();
    }

    function focusSource(index) {
      const group = groupByFile.get(index);
      if (!group) return;
      selectGroup(...group, index);
      body.querySelector("h2")?.focus();
      body.scrollIntoView({block:"nearest"});
    }

    async function loadSummary(value) {
      overview.hidden = true;
      try {
        const result = await api.command("export_browser_summary", {token:value.token});
        if (closed || archive !== value || !result?.ok) return;
        const {metrics, months} = result.archiveBrowser;
        const labels = [
          ["followers","Takipçiler"], ["following","Takip edilen"], ["profileChanges","Profil değişiklikleri"],
          ["likes","Beğendiğin gönderiler"], ["saved","Kaydettiğin gönderiler"], ["stories","Hikâyeler"], ["contacts","Eşitlenen kişiler"],
        ];
        const tiles = make("div", "", "archive-dashboard-metric-grid");
        for (const [key, label] of labels) {
          const metric = metrics[key];
          if (!metric) continue;
          const tile = groupByFile.has(metric.index)
            ? action("", () => focusSource(metric.index), "archive-dashboard-category archive-dashboard-metric")
            : make("div", "", "archive-dashboard-category archive-dashboard-metric");
          tile.append(make("small", t(label)), make("strong", num(metric.count)));
          tiles.append(tile);
        }
        if (!tiles.childElementCount) return;
        overview.replaceChildren(make("h2", t("Arşiv özeti")), make("p", t("Bu ZIP’teki kayıtların özeti"), "field-hint"), tiles);
        if (months.length) {
          const chart = make("section", "", "archive-dashboard-trend archive-dashboard-months");
          chart.append(make("h3", t("Arşivdeki gönderi beğenileri · Aya göre")));
          const max = Math.max(1, ...months.map(([, count]) => count));
          for (const [month, count] of months) {
            const row = make("div");
            const label = monthsFormat.format(new Date(month + "-01T00:00:00Z"));
            const bar = make("meter");
            bar.max = max;
            bar.value = count;
            bar.setAttribute("aria-label", label);
            row.append(make("span", label), bar, make("strong", num(count)));
            chart.append(row);
          }
          overview.append(chart);
        }
        overview.hidden = false;
      } catch { /* The selected archive is still available for browsing. */ }
    }

    function toolView(title) {
      clearTimeout(searchTimer);
      const current = ++generation;
      release();
      for (const control of cards.children) control.setAttribute("aria-pressed", "false");
      const heading = make("h2", title);
      heading.tabIndex = -1;
      body.replaceChildren(heading);
      body.hidden = false;
      return current;
    }

    function showSearch(query) {
      const current = toolView(t("Arama sonuçları"));
      const token = archive.token;
      const feedback = make("p", "", "field-hint");
      feedback.setAttribute("role", "status");
      const results = make("div", "", "archive-dashboard-search-results");
      const nextButton = action(t("Daha fazla göster"), () => scan());
      body.append(make("p", t("JSON kayıtlarının okunabilir alanları ve arşivdeki dosya adları aranır."), "field-hint"),
        feedback, results, nextButton);
      const sections = new Map();
      let cursor = {}, skipped = 0, count = 0;
      const active = () => !closed && generation === current;
      function appendResult(hit) {
        const file = filesById.get(hit.index);
        if (!file) return;
        const [id, label] = groupByFile.get(file.id) || ["account", "Diğer hesap verileri"];
        if (!sections.has(id)) {
          const section = make("section", "", "archive-dashboard-rows");
          section.append(make("h3", t(label)));
          sections.set(id, section);
          results.append(section);
        }
        const article = make("article", "", "archive-dashboard-record");
        const name = t(names[basename(file.name).replace(/(?:_\d+)?\.json$/i, "")] || pretty(basename(file.name)));
        article.append(make("h4", name), make("p", file.name, "field-hint"));
        if (hit.preview) article.append(make("p", hit.preview, "archive-dashboard-copy"));
        if (hit.record !== null) {
          const detail = make("details");
          detail.append(make("summary", t("Kayıt ayrıntısı")));
          detail.addEventListener("toggle", async () => {
            if (!detail.open || detail.dataset.loaded || !active()) return;
            detail.dataset.loaded = "true";
            const panel = make("div", t("Kayıt okunuyor…"));
            detail.append(panel);
            try {
              const result = await api.command("export_browser_browse", {token,index:file.id,record:hit.record});
              if (!active()) return;
              if (!result?.ok || !result.archiveBrowser.rows[0]) throw Error();
              panel.replaceChildren(record(result.archiveBrowser.rows[0], id, current));
            } catch {
              delete detail.dataset.loaded;
              panel.remove();
              if (active()) detail.open = false;
            }
          });
          article.append(detail);
        } else if (["image","video","audio"].includes(file.kind)) {
          preview(file, article, current);
        }
        if (groupByFile.has(file.id)) article.append(action(t("Veri kaynağını aç"), () => focusSource(file.id)));
        else if (file.kind !== "json" && !["image","video","audio"].includes(file.kind))
          article.append(action(t("Dosya tarayıcısı"), () => openRaw(archive)));
        sections.get(id).append(article);
      }
      async function scan() {
        nextButton.hidden = true;
        feedback.textContent = t("Arşiv aranıyor…");
        try {
          do {
            const result = await api.command("export_browser_search", {token, query, ...cursor});
            if (!active()) return;
            if (!result?.ok) throw Error();
            const page = result.archiveBrowser;
            for (const hit of page.rows) appendResult(hit);
            count += page.rows.length;
            skipped += page.skipped;
            cursor = page.next;
            if (page.rows.length) break;
          } while (cursor);
          feedback.textContent = (count ? t("{0} sonuç gösteriliyor", num(count)) : t("Eşleşen kayıt bulunamadı.")) +
            (cursor ? " · " + t("Aramanın devamı var.") : "") +
            (skipped ? " · " + t("{0} kaynak okunamadı.", num(skipped)) : "");
        } catch {
          if (!active()) return;
          feedback.textContent = t("Arşiv araması tamamlanamadı.");
        }
        if (active()) nextButton.hidden = !cursor;
      }
      scan();
    }

    function showComparison() {
      const current = toolView(t("İki ZIP karşılaştır"));
      const value = archive;
      body.append(make("p", t("İlk ZIP eski, seçeceğin ikinci ZIP yeni olmalı. Aynı hesabın ve aynı tarih kapsamının arşivlerini kullan.")),
        make("p", t("Farklar ZIP’teki kullanıcı adlarına göre hesaplanır. Kullanıcı adı değişmiş veya dışa aktarma kapsamı farklı olabilir."), "field-hint"),
        make("p", t("İlk ZIP: {0}", value.name)));
      const choose = action(t("İkinci ZIP’i seç"), compare);
      const feedback = make("p", "", "field-hint");
      feedback.setAttribute("role", "status");
      const results = make("div", "", "archive-dashboard-rows");
      body.append(choose, feedback, results);
      const active = () => !closed && generation === current;
      async function compare() {
        choose.disabled = true;
        results.inert = true;
        feedback.textContent = t("Dosya hazırlanıyor…");
        try {
          const response = await api.file("export_browser_compare", {token:value.token});
          if (!active()) return;
          feedback.textContent = "";
          if (response?.cancelled) return;
          results.replaceChildren();
          if (!response?.ok) throw Error();
          const {name, directions} = response.archiveBrowser;
          results.append(make("p", t("İkinci ZIP: {0}", name), "field-hint"));
          for (const [direction, label] of [["followers","Takipçiler"],["following","Takip edilen"]]) {
            const data = directions[direction];
            const panel = make("section", "", "archive-dashboard-record");
            panel.append(make("h3", t(label)));
            results.append(panel);
            if (!data) {
              panel.append(make("p", t("Bu liste iki ZIP’te de bulunmadığı için karşılaştırılamadı."), "field-hint"));
              continue;
            }
            panel.append(make("p", num(data.before) + " → " + num(data.after), "archive-dashboard-compare-count"));
            for (const [change, wording] of [["added","Yalnız ikinci ZIP’te"],["removed","Yalnız ilk ZIP’te"]]) {
              if (!data[change]) continue;
              const detail = make("details");
              detail.append(make("summary", t(wording) + " · " + num(data[change])));
              const list = make("ul", "", "archive-dashboard-compare-list");
              let offset = 0, reading = false;
              const more = action(t("Daha fazla göster"), loadNames);
              more.hidden = true;
              detail.append(list, more);
              detail.addEventListener("toggle", () => { if (detail.open && !offset) loadNames(); });
              panel.append(detail);
              async function loadNames() {
                if (reading || !active()) return;
                reading = true;
                try {
                  const response = await api.command("export_browser_comparison_page", {token:value.token,direction,change,offset});
                  if (!active() || !results.contains(detail)) return;
                  if (!response?.ok) throw Error();
                  const {names, total} = response.archiveBrowser;
                  for (const username of names) {
                    const li = make("li");
                    li.append(profile(username) || username);
                    list.append(li);
                  }
                  offset += names.length;
                  more.hidden = offset >= total;
                } catch {
                  if (active()) feedback.textContent = t("Arşiv veya seçilen dosya açılamadı.");
                } finally {
                  reading = false;
                }
              }
            }
            if (!data.added && !data.removed) panel.append(make("p", t("Kullanıcı adı farkı yok."), "field-hint"));
          }
        } catch {
          if (active()) feedback.textContent = t("İki ZIP karşılaştırılamadı. Dosyalarda okunabilir takip listeleri olmalı.");
        } finally {
          if (active()) {
            choose.disabled = false;
            results.inert = false;
          }
        }
      }
    }

    function selectGroup(id, label, files, preferred) {
      clearTimeout(searchTimer);
      generation++;
      release();
      for (const control of cards.children) control.setAttribute("aria-pressed", String(control.dataset.archiveGroup === id));
      const picker = make("select");
      picker.setAttribute("aria-label", t("Veri türü"));
      let shownFiles = files;
      const namesById = new Map();
      for (const file of files) {
        const name = basename(file.name);
        const base = name.replace(/(?:_\d+)?\.json$/i, "");
        const folder = file.name.split("/").at(-2) || "";
        const suffix = name.match(/_(\d+)\.json$/)?.[1];
        const display = id === "messages" ? pretty(folder.replace(/_\d+$/, "")) + (suffix && suffix !== "1" ? " · " + suffix : "") :
          (Object.hasOwn(names,base) ? t(names[base]) : pretty(base)) + (suffix && suffix !== "1" ? " · " + suffix : "");
        namesById.set(file.id, display);
      }
      function populatePicker() {
        picker.replaceChildren(...shownFiles.map(file => {
          const option = make("option", namesById.get(file.id));
          option.value = file.id;
          return option;
        }));
      }
      const selected = () => shownFiles.find(file => file.id === Number(picker.value));
      populatePicker();
      if (id === "profile") {
        const changes = files.find(file => basename(file.name) === "profile_changes.json");
        if (changes) picker.value = changes.id;
      }
      if (id === "connections") {
        const followers = files.find(file => /^followers(?:_\d+)?\.json$/i.test(basename(file.name)));
        if (followers) picker.value = followers.id;
      }
      const query = make("input");
      query.type = "search";
      query.maxLength = 160;
      query.placeholder = t("Kayıtlarda ara");
      query.setAttribute("aria-label", query.placeholder);
      const controls = make("div", "", "archive-dashboard-filters");
      controls.append(picker, query);
      const dateFilter = ["connections","activity"].includes(id);
      const order = make("select");
      const from = make("input"), to = make("input");
      const field = (label, input) => {
        const node = make("label", t(label));
        node.append(input);
        return node;
      };
      if (dateFilter) {
        from.type = to.type = "date";
        for (const [value, text] of [["newest","En yeni takip tarihi"],["oldest","En eski takip tarihi"]]) {
          const option = make("option", t(id === "connections" ? text : value === "newest" ? "En yeni" : "En eski"));
          option.value = value;
          order.append(option);
        }
        controls.append(field("Sıralama", order), field("Başlangıç tarihi", from), field("Bitiş tarihi", to));
        for (const control of [order, from, to]) control.addEventListener("change", () => read());
      }
      const feedback = make("p", "", "field-hint");
      feedback.setAttribute("role", "status");
      const rows = make("div", "", "archive-dashboard-rows");
      const nav = make("div", "", "archive-dashboard-toolbar");
      const heading = make("h2", t(label));
      heading.tabIndex = -1;
      body.hidden = false;
      if (preferred != null) picker.value = preferred;
      const groupTabs = make("div", "", "archive-dashboard-tabs");
      body.replaceChildren(heading, groupTabs, controls, feedback, rows, nav);
      rows.classList.toggle("archive-dashboard-compact", ["connections","activity"].includes(id));
      if (id === "activity") {
        for (const [type, name] of activityTypes) {
          const entries = files.filter(file => folderType(file) === type);
          if (!entries.length) continue;
          const tab = action(t(name), () => {
            for (const child of groupTabs.children) child.setAttribute("aria-pressed", String(child === tab));
            shownFiles = entries;
            populatePicker();
            if (entries.some(file => file.id === preferred)) picker.value = preferred;
            preferred = undefined;
            query.value = "";
            read();
          });
          tab.dataset.type = type;
          tab.setAttribute("aria-pressed", "false");
          groupTabs.append(tab);
        }
      }
      if (id === "messages") {
        const chatList = make("aside", "", "archive-dashboard-chats");
        const chatSearch = make("input");
        chatSearch.type = "search";
        chatSearch.placeholder = t("Sohbetlerde ara");
        chatSearch.setAttribute("aria-label", chatSearch.placeholder);
        const chatButtons = make("div", "", "archive-dashboard-chat-buttons");
        const conversations = Map.groupBy(files, file => file.name.slice(0, file.name.lastIndexOf("/")));
        function updateChats() {
          const filter = chatSearch.value.trim().toLocaleLowerCase(language);
          chatButtons.replaceChildren();
          for (const [folder, parts] of conversations) {
            const name = pretty(basename(folder).replace(/_\d+$/, ""));
            if (!name.toLocaleLowerCase(language).includes(filter)) continue;
            const choice = action(name, () => selectConversation(parts));
            choice.setAttribute("aria-pressed", String(shownFiles === parts));
            chatButtons.append(choice);
          }
        }
        function selectConversation(parts) {
          shownFiles = parts;
          populatePicker();
          if (parts.some(file => file.id === preferred)) picker.value = preferred;
          preferred = undefined;
          picker.hidden = parts.length === 1;
          query.value = "";
          updateChats();
          read();
        }
        chatSearch.addEventListener("input", updateChats);
        chatList.append(chatSearch, chatButtons);
        const messages = make("div", "", "archive-dashboard-chat-thread");
        messages.append(controls, feedback, rows, nav);
        const split = make("div", "", "archive-dashboard-chat-layout");
        split.append(chatList, messages);
        body.replaceChildren(heading, split);
        selectConversation([...conversations.values()].find(parts => parts.some(file => file.id === preferred)) || conversations.values().next().value);
      }
      async function read(offset = 0) {
        const file = selected();
        const current = ++generation;
        release();
        rows.replaceChildren();
        nav.replaceChildren();
        query.hidden = file.kind !== "json";
        if (file.kind !== "json") {
          feedback.textContent = "";
          preview(file, rows, current);
          return;
        }
        feedback.textContent = t("Kayıt okunuyor…");
        try {
          if (dateFilter && from.value && to.value && from.value > to.value) {
            feedback.textContent = t("Bitiş tarihi başlangıç tarihinden önce olamaz.");
            return;
          }
          const sort = dateFilter ? order.value : ["profile","messages"].includes(id) ? "newest" : null;
          const result = await api.command("export_browser_browse", {token:archive.token,index:file.id,query:query.value,offset,
            since:dateBound(from.value), until:dateBound(to.value,true), order:sort});
          if (closed || current !== generation) return;
          if (!result?.ok) throw Error();
          const {rows: page, total, title, trend} = result.archiveBrowser;
          feedback.textContent = (title ? title + " · " : "") + t("{0} kayıt", num(total));
          if (!page.length) rows.append(make("p", t("Bu dosyada kayıt yok.")));
          if (trend && !offset) rows.append(trendChart(trend));
          let lastDay;
          for (const item of page) {
            if (["activity","profile","messages"].includes(id)) {
              const day = item.when ? dayFormat.format(item.when * 1000) : "";
              if (day && day !== lastDay) rows.append(make("h3", day, "archive-dashboard-day"));
              lastDay = day;
            }
            rows.append(record(item, id, current));
          }
          if (offset) nav.append(action(t("Önceki sayfa"), () => read(Math.max(0, offset - 24))));
          if (offset + page.length < total) nav.append(action(t("Sonraki sayfa"), () => read(offset + page.length)));
        } catch {
          if (!closed && current === generation) feedback.textContent = t("Arşiv veya seçilen dosya açılamadı.");
        }
      }
      picker.addEventListener("change", () => { clearTimeout(searchTimer); query.value = ""; read(); });
      query.addEventListener("input", () => { clearTimeout(searchTimer); searchTimer = setTimeout(read, 180); });
      if (id === "activity" && groupTabs.firstElementChild) {
        const preferredFile = files.find(file => file.id === preferred);
        ([...groupTabs.children].find(tab => preferredFile && tab.dataset.type === folderType(preferredFile)) ||
          groupTabs.firstElementChild).click();
      }
      else if (id !== "messages") read();
    }

    function trendChart({metric, points}) {
      const chart = make("section", "", "archive-dashboard-trend");
      chart.append(make("h3", t(fieldNames[metric])));
      const max = Math.max(1, ...points.map(([, value]) => value));
      for (const [stamp, value] of points) {
        const row = make("div");
        const label = dayFormat.format(stamp * 1000);
        const bar = make("meter");
        bar.max = max;
        bar.value = value;
        bar.setAttribute("aria-label", label);
        row.append(make("span", label), bar, make("strong", num(value)));
        chart.append(row);
      }
      return chart;
    }

    function record(item, group, current) {
      const fields = new Map(item.fields);
      const article = make("article", "", "archive-dashboard-record");
      if (group === "messages") article.classList.add("archive-dashboard-message");
      const head = make("header");
      const change = fields.get("Changed");
      const heading = (change && Object.hasOwn(changeNames, change) ? t(changeNames[change]) : change) || item.title || fields.get("Caption") ||
        fields.get("content") || (group === "insights" ? t("İstatistikler") : t("Kayıt ayrıntısı"));
      const headingNode = make("h3");
      const profileLink = group === "connections" ?
        profile(fields.get("Username")) || (heading.startsWith("@") ? profile(heading) : null) : null;
      headingNode.append(profileLink || document.createTextNode(heading.slice(0, 150)));
      head.append(headingNode);
      if (item.when) {
        const time = make("time", (group === "messages" ? clock : date).format(item.when * 1000));
        time.dateTime = new Date(item.when * 1000).toISOString();
        head.append(time);
      }
      article.append(head);
      const label = key => Object.hasOwn(fieldNames, key) ? t(fieldNames[key]) : pretty(key);
      const shown = new Set();
      function valueNode(key, value) {
        return link(value) || (key === "Username" ? profile(value) : null) || document.createTextNode(value);
      }
      function formattedField(key, value, diff) {
        const output = make("dd");
        if (diff) {
          output.className = "bio-diff";
          for (const part of diff) output.append(part.kind === "same" ? document.createTextNode(part.text) :
            make(part.kind === "add" ? "ins" : "del", part.text));
        } else output.append(valueNode(key, value));
        return output;
      }
      function addFields(keys, css, differences = null) {
        const list = make("dl", "", css);
        for (const key of keys) {
          const value = fields.get(key);
          if (value == null) continue;
          shown.add(key);
          const field = make("div");
          const display = group === "insights" && /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) ? num(Number(value)) : value;
          field.append(make("dt", label(key)), formattedField(key, display, differences?.[key]));
          list.append(field);
        }
        if (list.childElementCount) article.append(list);
      }
      if (group === "insights") {
        if (fields.has("Date Range")) {
          article.append(make("p", label("Date Range") + " · " + fields.get("Date Range"), "archive-dashboard-period"));
          shown.add("Date Range");
        }
        addFields(insightFields, "archive-dashboard-metrics");
      }
      if (change) {
        shown.add("Changed");
        const before = fields.get("Previous value"), after = fields.get("New value");
        if (change.toLowerCase() === "username" && before && after) {
          const line = make("p", "", "archive-dashboard-copy");
          // The older handle may now belong to another account.
          line.append(before, " → ", profile(after) || after);
          article.append(line);
          shown.add("Previous value");
          shown.add("New value");
        } else {
          const isBio = ["bio","biography"].includes(change.toLowerCase());
          const parts = isBio && before != null && after != null ? window.TrackerWayback.diff(before, after) : null;
          const differences = parts && {"Previous value":parts.filter(part => part.kind !== "add"),
            "New value":parts.filter(part => part.kind !== "remove")};
          addFields(["Previous value", "New value"], "archive-dashboard-values", differences);
        }
      }
      for (const key of ["content", "Comment", "Caption"]) {
        const value = fields.get(key);
        if (group === "activity") continue;
        if (value && value !== heading) article.append(make("p", value, "archive-dashboard-copy"));
        if (value) shown.add(key);
      }
      addFields([...inlineFields].filter(key => !shown.has(key)), "archive-dashboard-values");
      if (group === "activity" && fields.has("URL")) {
        const url = fields.get("URL");
        const line = make("p", "", "archive-dashboard-period");
        line.append(link(url, t("Bağlantıyı aç")) || url);
        article.append(line);
        shown.add("URL");
      }
      const detail = make("details");
      detail.append(make("summary", t("Kayıt ayrıntısı")));
      const extra = make("dl", "", "archive-dashboard-fields");
      for (const [key, value] of item.fields) {
        if (shown.has(key) || value === heading || key === "Username" && profileLink || value === "0" && /timestamp|time|date/i.test(key)) continue;
        extra.append(make("dt", label(key)), formattedField(key, value));
      }
      if (extra.childElementCount) { detail.append(extra); article.append(detail); }
      for (const path of new Set([item.media, ...(item.attachments || [])])) {
        const media = path && filesByName.get(path);
        if (media && ["image","video","audio"].includes(media.kind)) preview(media, article, current);
      }
      return article;
    }
    function preview(file, parent, current) {
      const button = action(t("Yerel önizlemeyi aç"), async () => {
        button.disabled = true;
        try {
          const result = await api.command("export_browser_media", {token:archive.token,index:file.id});
          if (closed || generation !== current) return;
          if (!result?.ok) throw Error();
          const {mime,base64} = result.archiveBrowser;
          const bytes = Uint8Array.fromBase64 ? Uint8Array.fromBase64(base64) : Uint8Array.from(atob(base64), char => char.charCodeAt(0));
          release();
          root.querySelector(".archive-dashboard-player")?.remove();
          mediaUrl = URL.createObjectURL(new Blob([bytes],{type:mime}));
          const player = make(file.kind === "image" ? "img" : file.kind, "", "archive-dashboard-player");
          if (file.kind === "image") player.alt = basename(file.name);
          else player.controls = true;
          player.src = mediaUrl;
          parent.append(player);
        } catch {
          if (!closed && generation === current) status.textContent = t("Arşiv veya seçilen dosya açılamadı.");
        } finally { button.disabled = false; }
      });
      parent.append(button);
    }
    if (autoOpen) open();
    return {root, language, close() {
      closed = true;
      clearTimeout(searchTimer);
      release();
      if (archive) api.command("export_browser_close", {token:archive.token}).catch(() => {});
      archive = null;
      filesByName.clear();
      filesById.clear();
      groupByFile.clear();
    }};
  }
  window.TrackerArchiveDashboard = Object.freeze({mount});
})();
