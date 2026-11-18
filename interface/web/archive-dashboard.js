(() => {
  "use strict";
  const groups = [
    ["connections","Takipler","people",/(^|\/)followers_and_following\//],
    ["profile","Profil geçmişi","clock",/^personal_information\/personal_information\/|\/signup_details\.json$/],
    ["messages","Mesajlar","note",/\/messages\/.*\/message_\d+\.json$/],
    ["content","Paylaşımların","image",/^(media\/|your_instagram_activity\/media\/)/],
    ["insights","İstatistikler","activity",/\/past_instagram_insights\//],
    ["activity","Etkinliklerin","heart",/^(your_instagram_activity\/)?(likes|saved|comments|story_interactions|threads|instants)\//],
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
  const relationLabels = t => ({
    recentlyUnfollowed:t("Son takipten çıktıkların"), pendingRequests:t("Bekleyen takip isteklerin"),
    recentRequests:t("Son takip istekleri"), receivedRequests:t("Gelen takip istekleri"),
    closeFriends:t("Yakın arkadaşlar"), blocked:t("Engellediğin hesaplar"), restricted:t("Kısıtladığın hesaplar"),
    hideStoryFrom:t("Hikâyeni gizlediklerin"), removedSuggestions:t("Kaldırılan öneriler"),
  });
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

  function mount(root, {api, t, num, language, openRaw, openLists, readOwnStories, readOwnStoryMedia, autoOpen = false}) {
    let archive, closed = false, opening = false, generation = 0, mediaUrl, searchTimer;
    let filesByName = new Map(), filesById = new Map(), groupByFile = new Map();
    const monthsFormat = new Intl.DateTimeFormat(language, {month:"short", year:"numeric", timeZone:"UTC"});
    const date = new Intl.DateTimeFormat(language, {dateStyle:"medium", timeStyle:"short"});
    const dayFormat = new Intl.DateTimeFormat(language, {dateStyle:"long"});
    const archiveDate = new Intl.DateTimeFormat(language, {dateStyle:"long", timeZone:"UTC"});
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
    const load = action(t("Instagram ZIP yükle"), () => open(), "button primary");
    toolbar.append(description, load);
    toolbar.append(action(t("İçerik ve etkileşim"), showContent));
    const relations = openLists ? action(t("Takip ilişkileri"), () => openLists(archive?.token)) : null;
    if (relations) toolbar.append(relations);
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
    const dropHint = make("p", t("ZIP dosyasını buraya bırak"), "archive-dashboard-drop field-hint");
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
    root.replaceChildren(toolbar, dropHint, status, overview, guide, tools, cards, body);
    const thumbnails = new Set();
    let lastComparison = null;
    function clearThumbnails() {
      for (const url of thumbnails) URL.revokeObjectURL(url);
      thumbnails.clear();
    }
    function dragOver(event) {
      if (!event.dataTransfer?.types.includes("Files")) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = opening ? "none" : "copy";
      dropHint.classList.toggle("active", !opening);
    }
    function dragLeave(event) {
      if (!event.relatedTarget || !root.contains(event.relatedTarget)) dropHint.classList.remove("active");
    }
    function drop(event) {
      if (!event.dataTransfer?.types.includes("Files")) return;
      event.preventDefault();
      dropHint.classList.remove("active");
      if (opening || closed) return;
      const files = event.dataTransfer.files;
      if (files.length !== 1 || !/\.zip$/i.test(files[0].name)) {
        status.textContent = t("Tek bir ZIP dosyası bırak.");
        return;
      }
      open(files[0]);
    }
    root.addEventListener("dragover", dragOver);
    root.addEventListener("dragleave", dragLeave);
    root.addEventListener("drop", drop);

    async function open(file) {
      if (opening || closed) return;
      opening = true;
      load.disabled = true;
      status.textContent = t("Dosya hazırlanıyor…");
      try {
        const result = await (file ? api.openDroppedArchive(file) : api.file("export_browser_open"));
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
        clearThumbnails();
        release();
        tools.hidden = false;
        searchInput.value = "";
        raw.hidden = false;
        guide.open = false;
        description.textContent = archive.name + " · " + t("{0} dosya", num(archive.files.length));
        status.textContent = t("Listeler, ZIP hazırlanırken dışa aktarılan kayıtları gösterir.");
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
        const {metrics, months, scope, metadata} = result.archiveBrowser;
        const info = [value.name];
        if (metadata.account) info.push("@" + metadata.account + (metadata.accountSource === "filename" ? " " + t("(dosya adından)") : ""));
        if (metadata.exportedAt) info.push(t("Dışa aktarım: {0}", date.format(new Date(metadata.exportedAt))));
        else if (metadata.filenameDate) {
          const archiveDay = new Date(metadata.filenameDate + "T00:00:00Z");
          info.push(t("Dosya adındaki tarih: {0}", archiveDate.format(archiveDay)));
        }
        const coverage = make("ul", "", "archive-dashboard-coverage");
        for (const [key, label] of [["followers", t("Takipçiler")], ["following", t("Takip edilen")], ["messages", t("Mesajlar")]]) {
          const metric = metrics[key];
          const state = metric ? (key === "messages" ? t("{0} sohbet", num(metric.count)) : num(metric.count)) : {
            present:t("Var"), missing:t("Bu ZIP’te yok"), unreadable:t("Okunamadı"), empty:t("ZIP’te kayıt yok"), partial:t("Kısmi"),
          }[scope[key]];
          const item = make("li");
          const control = metric && groupByFile.has(metric.index)
            ? action("", () => focusSource(metric.index), "archive-dashboard-category")
            : make("div", "", "archive-dashboard-category");
          control.append(make("strong", label), make("span", state));
          if (scope[key] === "partial") {
            const missing = [metric.skipped ? t("{0} satır okunamadı", num(metric.skipped)) : "",
              metric.missingParts?.length ? t("{0} parça eksik", num(metric.missingParts.length)) : ""].filter(Boolean);
            control.append(make("small", t("Kısmi") + (missing.length ? " · " + missing.join(" · ") : ""), "archive-dashboard-partial"));
          } else if (scope[key] === "unreadable") {
            control.append(make("small", t("Dosya var ama okunamadı. Dosya tarayıcısında açabilirsin."), "archive-dashboard-partial"));
          }
          item.append(control);
          coverage.append(item);
        }
        const labels = [
          ["profileChanges",t("Profil değişiklikleri")], ["likes",t("Beğendiğin gönderiler")],
          ["saved",t("Kaydettiğin gönderiler")], ["comments",t("Yorumların")], ["stories",t("Hikâyeler")],
          ["contacts",t("Eşitlenen kişiler")],
        ];
        const tile = (label, metric) => {
          const node = groupByFile.has(metric.index)
            ? action("", () => focusSource(metric.index), "archive-dashboard-category archive-dashboard-metric")
            : make("div", "", "archive-dashboard-category archive-dashboard-metric");
          node.append(make("small", label), make("strong", metric.tooLarge ? t("Çok büyük") : num(metric.count)));
          if (metric.tooLarge) node.title = t("Bu kayıt özet için çok büyük. Dosya tarayıcısında açabilirsin.");
          return node;
        };
        const tiles = make("div", "", "archive-dashboard-metric-grid");
        for (const [key, label] of labels) if (metrics[key]) tiles.append(tile(label, metrics[key]));
        const relationTiles = make("div", "", "archive-dashboard-metric-grid");
        const relationNames = relationLabels(t);
        for (const [key, label] of Object.entries(relationNames))
          if (result.archiveBrowser.relations?.[key]) relationTiles.append(tile(label, result.archiveBrowser.relations[key]));
        overview.replaceChildren(make("h2", t("Arşiv kapsamı")), make("p", info.join(" · "), "archive-dashboard-file-info"), coverage);
        if (relationTiles.childElementCount) overview.append(make("h3", t("Takip ilişkileri")), relationTiles);
        if (tiles.childElementCount) overview.append(make("h3", t("Etkinlik")), tiles);
        if (months.length) {
          const chart = make("section", "", "archive-dashboard-trend archive-dashboard-months");
          chart.append(make("h3", t("Arşivdeki gönderi beğenileri · Aya göre")));
          const max = Math.max(1, ...months.map(([, count]) => count ?? 0));
          for (const [month, count] of months) {
            const row = make("div");
            const label = monthsFormat.format(new Date(month + "-01T00:00:00Z"));
            row.append(make("span", label));
            if (count === null) {
              row.append(make("small", t("ZIP’te kayıt yok"), "archive-dashboard-month-empty"));
            } else {
              const bar = make("meter");
              bar.max = max;
              bar.value = count;
              bar.setAttribute("aria-label", label);
              row.append(bar, make("strong", num(count)));
            }
            chart.append(row);
          }
          overview.append(chart);
        }
        overview.hidden = false;
      } catch { /* The selected archive is still available for browsing. */ }
    }

    function toolView(title) {
      clearThumbnails();
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

    async function showContent() {
      const current = toolView(t("İçerik ve etkileşim"));
      const value = archive;
      const active = () => !closed && current === generation;
      const feedback = make("p", t("Kayıt okunuyor…"), "field-hint");
      feedback.setAttribute("role", "status");
      const calendar = make("section", "", "story-calendar content-calendar");
      const details = make("section", "", "archive-dashboard-rows content-calendar-results");
      const layout = make("div", "", "content-calendar-layout");
      layout.append(calendar, details);
      body.append(feedback, layout);
      const sources = await Promise.allSettled([
        value ? api.command("export_browser_content", {token:value.token}) : Promise.resolve(null),
        readOwnStories ? readOwnStories() : Promise.resolve(null),
      ]);
      if (!active()) return;
      const zip = sources[0].value;
      const localSource = sources[1].value;
      const accountMismatch = value?.metadata?.account && localSource?.username &&
        value.metadata.account.toLowerCase() !== localSource.username.toLowerCase();
      const local = accountMismatch ? null : localSource;
      const stories = local?.items || [];
      const localDays = new Map();
      for (const story of stories) {
        const at = story.publishedAt ?? story.capturedAt;
        if (!Number.isFinite(at)) continue;
        const day = window.TrackerStories.day(story);
        if (!localDays.has(day)) localDays.set(day, []);
        localDays.get(day).push(story);
      }
      const latest = [zip?.archiveBrowser?.latest, ...localDays.keys()].filter(Boolean).sort().at(-1);
      let month = (latest || new Date().toISOString()).slice(0,7);
      let selected = latest || month + "-01", revision = 0;
      const monthFormat = new Intl.DateTimeFormat(language,{month:"long",year:"numeric",timeZone:"UTC"});
      const weekday = new Intl.DateTimeFormat(language,{weekday:"short",timeZone:"UTC"});
      const labels = {stories:t("Hikâyeler"),reels:"Reels",posts:t("Gönderiler")};
      const metricLabels = {views:t("Görüntülenmeler"),plays:t("Oynatma"),likes:t("Beğeniler"),comments:t("Yorumlar"),reactions:t("Tepkiler")};
      const dateFor = day => new Date(day + "T00:00:00Z");
      function blob(asset) {
        return URL.createObjectURL(new Blob([Uint8Array.fromBase64(asset.base64)],{type:asset.mime}));
      }
      function cardFor(item, story, ticket, candidates) {
        const card = make("article", "", "archive-dashboard-record content-calendar-card");
        card.append(make("h4", labels[item.kind]), make("small", t(story ? "Yerel hikâye arşivi" : "Instagram ZIP")));
        card.append(make("time", date.format(new Date(item.at * 1000))));
        if (item.caption) card.append(make("p",item.caption));
        const metrics = make("dl", "", "archive-dashboard-fields");
        for (const [key, number] of Object.entries(item.metrics || {})) {
          metrics.append(make("dt", metricLabels[key]), make("dd", num(number)));
        }
        if (metrics.childElementCount) card.append(metrics);
        const assets = (item.assets || []).map(index=>filesById.get(index)).filter(Boolean);
        const thumb = assets.find(file=>file.kind==="image" && file.size<=256*1024);
        if (thumb && candidates.length < 8) candidates.push([thumb, card]);
        for (const file of assets) preview(file, card, current);
        if (story?.status === "saved" && readOwnStoryMedia) {
          const play = action(t("Yerel önizlemeyi aç"), async () => {
            play.disabled = true;
            try {
              const asset = await readOwnStoryMedia(local, story.id);
              if (!active() || ticket !== revision || !card.isConnected) return;
              if (!asset) throw Error();
              release();
              body.querySelector(".archive-dashboard-player")?.remove();
              mediaUrl = blob(asset);
              const element = make(asset.mime.startsWith("video/") ? "video" : "img", "", "archive-dashboard-player");
              if (element.tagName === "VIDEO") element.controls = true;
              else element.alt = "";
              element.src = mediaUrl;
              card.append(element);
            } catch {
              if (active() && ticket === revision) feedback.textContent = t("Hikâye açılamadı.");
            } finally { play.disabled = false; }
          });
          card.append(play);
        }
        return card;
      }
      async function draw(offset=0) {
        const ticket = ++revision;
        clearThumbnails();
        release();
        const response = zip?.ok
          ? await api.command("export_browser_content", {token:value.token,month,day:selected,offset}).catch(()=>null) : null;
        if (!active() || ticket !== revision) return;
        const payload = response?.archiveBrowser;
        feedback.textContent = (value && !response?.ok) || sources[1].status === "rejected" ? t("Bazı içerik kaynakları okunamadı.") :
          accountMismatch ? t("Bu ZIP farklı bir kullanıcı adına ait. Takvimde yalnız ZIP içerikleri gösteriliyor.") :
          payload?.skipped ? t("{0} kaynak okunamadı.",num(payload.skipped)) :
          value ? "" : t("Gönderi ve Reels geçmişini görmek için Instagram ZIP’ini aç.");
        const start = dateFor(month + "-01");
        const header = make("header","","story-calendar-heading");
        const move = (step,label) => {
          const target = window.TrackerStories.shiftMonth(month,step);
          const button = action("", () => {
            month=target; selected=month+"-01"; draw();
          },"icon-button");
          button.innerHTML = window.TrackerIcon(step < 0 ? "previousMonth" : "nextMonth");
          button.setAttribute("aria-label", label);
          button.disabled = !target;
          return button;
        };
        header.append(move(-1,t("Önceki ay")),make("h3",monthFormat.format(start)),move(1,t("Sonraki ay")));
        const grid = make("div","","story-calendar-grid");
        for(let index=0;index<7;index++) grid.append(make("span",weekday.format(new Date(Date.UTC(2024,0,1+index))),"story-weekday"));
        for(let index=0;index<(start.getUTCDay()+6)%7;index++) grid.append(make("span","","story-calendar-blank"));
        const days = new Date(Date.UTC(start.getUTCFullYear(),start.getUTCMonth()+1,0)).getUTCDate();
        for(let index=1;index<=days;index++) {
          const day = month+"-"+String(index).padStart(2,"0");
          const count=(payload?.days[day] || 0)+(localDays.get(day)?.length || 0);
          const button=action(num(index),()=>{selected=day;draw();},"story-calendar-day");
          button.classList.toggle("has-stories",count>0);
          button.classList.toggle("is-selected",day===selected);
          button.setAttribute("aria-pressed",String(day===selected));
          button.setAttribute("aria-label",archiveDate.format(dateFor(day))+(count?" · "+t("{0} kayıt",num(count)):""));
          if(count) button.append(make("small",num(count)));
          grid.append(button);
        }
        calendar.replaceChildren(header,grid);
        const candidates = [];
        details.replaceChildren(make("h3", archiveDate.format(dateFor(selected))));
        const localItems = offset ? [] : localDays.get(selected) || [];
        const rows = (payload?.items || []).map(item=>[item,null]);
        for(const story of localItems) rows.push([{kind:"stories",at:story.publishedAt ?? story.capturedAt},story]);
        rows.sort(([a],[b])=>b.at-a.at);
        if(!rows.length) details.append(make("p",t("Bu günde içerik kaydı yok.")));
        for(const [item,story] of rows) details.append(cardFor(item,story,ticket,candidates));
        const navigation = make("div","","drawer-actions");
        if(offset) navigation.append(action(t("Önceki sayfa"),()=>draw(Math.max(0,offset-24))));
        if(offset+24<(payload?.total || 0)) navigation.append(action(t("Sonraki sayfa"),()=>draw(offset+24)));
        if(navigation.childElementCount) details.append(navigation);
        for(const [file,card] of candidates) {
          const preview = await api.command("export_browser_media",{token:value.token,index:file.id}).catch(()=>null);
          if(!active() || ticket!==revision) return;
          if(preview?.ok) {
            const url=blob(preview.archiveBrowser);
            thumbnails.add(url);
            const image=make("img","","content-calendar-thumbnail");
            image.alt="";
            image.src=url;
            card.prepend(image);
          }
        }
      }
      await draw();
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
      // A finished comparison survives switching views while the same ZIP is open.
      const saved = lastComparison?.token === value.token ? lastComparison : null;
      let pair = saved?.pair || null, reverse = saved?.reverse || false, working = false, outcome = saved?.outcome || null;
      body.append(make("p", t("İlk ZIP eski, seçeceğin ikinci ZIP yeni olmalı. Aynı hesabın ve aynı tarih kapsamının arşivlerini kullan.")),
        make("p", t("ZIP’lerde hesap kimliği yoktur; farklar kullanıcı adına göre hesaplanır. Aynı takip tarihini taşıyan tek bir ad değişikliği ayrıca gösterilir."), "field-hint"));
      const sources = make("div", "", "archive-dashboard-compare-sources");
      const drop = make("p", t("İkinci ZIP’i buraya da bırakabilirsin"), "archive-dashboard-drop field-hint");
      const choose = action(t("İkinci ZIP’i seç"), () => selectSecond());
      const swap = action(t("Yer değiştir"), () => {
        reverse = !reverse;
        outcome = null;
        results.replaceChildren();
        renderSources();
      });
      const run = action(t("Karşılaştırmayı başlat"), compare, "button primary");
      const controls = make("div", "", "drawer-actions");
      controls.append(choose, swap, run);
      const feedback = make("p", "", "field-hint");
      feedback.setAttribute("role", "status");
      const filters = make("div", "", "archive-dashboard-filters archive-dashboard-compare-filters");
      const query = make("input");
      query.type = "search";
      query.maxLength = 30;
      query.placeholder = t("Sonuçlarda kullanıcı adı ara");
      query.setAttribute("aria-label", query.placeholder);
      const order = make("select");
      order.setAttribute("aria-label", t("Sıralama"));
      for (const [key, label] of [["name", t("Kullanıcı adı")], ["newest", t("En yeni takip tarihi")], ["oldest", t("En eski takip tarihi")]]) {
        const option = make("option", label);
        option.value = key;
        order.append(option);
      }
      const exportCsv = action(t("CSV olarak kaydet"), saveCsv);
      filters.append(query, order, exportCsv);
      filters.hidden = true;
      const results = make("div", "", "archive-dashboard-rows");
      // Drop handling lives on this view's own container, so it ends with the view.
      const zone = make("div", "", "archive-dashboard-compare-zone");
      zone.append(sources, drop, controls, feedback, filters, results);
      body.append(zone);
      const active = () => !closed && generation === current;
      const day = stamp => archiveDate.format(new Date(stamp + "T00:00:00Z"));
      const followDay = stamp => archiveDate.format(new Date(stamp * 1000));
      function listState(lists, key) {
        const state = lists?.[key];
        if (!state) return t("Bu ZIP’te yok");
        if (state.unreadable) return t("Okunamadı");
        return num(state.count) + (state.complete ? "" : " · " + t("Kısmi"));
      }
      function sourceCard(label, file) {
        const card = make("section", "", "archive-dashboard-record");
        card.append(make("h3", label));
        if (!file) {
          card.append(make("p", t("Henüz seçilmedi"), "field-hint"));
          return card;
        }
        card.append(make("strong", file.name));
        card.append(make("p", file.account ? "@" + file.account + (file.accountSource === "filename" ? " " + t("(dosya adından)") : "") : t("ZIP’te hesap bilgisi yok")));
        if (file.exportedAt) card.append(make("p", t("Dışa aktarım: {0}", date.format(new Date(file.exportedAt)))));
        else if (file.filenameDate) card.append(make("p", t("Dosya adındaki tarih: {0}", day(file.filenameDate))));
        if (file.dateRange) card.append(make("p", t("Tarih aralığı") + " · " + file.dateRange.map(day).join(" – ")));
        if (file.lists) {
          const lists = make("ul", "", "archive-dashboard-compare-coverage");
          for (const [key, name] of [["followers", t("Takipçiler")], ["following", t("Takip edilen")]]) {
            const item = make("li");
            item.append(make("span", name), make("strong", listState(file.lists, key)));
            lists.append(item);
          }
          card.append(lists);
        }
        return card;
      }
      function renderSources() {
        const first = reverse ? pair.second : pair?.first || {name:value.name, ...value.metadata};
        const second = reverse ? pair.first : pair?.second;
        sources.replaceChildren(sourceCard(t("Eski ZIP"), first), sourceCard(t("Yeni ZIP"), second));
        const reversed = first.exportedAt && second?.exportedAt
          ? Date.parse(first.exportedAt) > Date.parse(second.exportedAt)
          : (first.exportedAt?.slice(0,10) || first.filenameDate || "") >
            (second?.exportedAt?.slice(0,10) || second?.filenameDate || "9999") &&
            !!(second?.exportedAt || second?.filenameDate);
        choose.textContent = pair ? t("İkinci ZIP’i değiştir") : t("İkinci ZIP’i seç");
        choose.disabled = working;
        swap.hidden = run.hidden = !pair;
        swap.disabled = working;
        run.disabled = working || !pair?.comparable;
        run.textContent = pair?.differentAccounts ? t("Yine de karşılaştır") : t("Karşılaştırmayı başlat");
        feedback.textContent = !pair ? "" : !pair.comparable ? t("İki ZIP’te de okunabilen ortak bir takip listesi yok.") :
          pair.differentAccounts ? t("ZIP’ler farklı kullanıcı adlarına ait. Aynı hesabın iki ZIP’ini seç.") :
          reversed ? t("İlk ZIP daha yeni görünüyor. Karşılaştırmadan önce yerlerini değiştirebilirsin.") :
          pair.unknownAccount ? t("ZIP’lerden birinde hesap bilgisi yok. Aynı hesaba ait olduklarını kontrol et.") : "";
      }
      async function selectSecond(file) {
        if (working) return;
        working = true;
        renderSources();
        results.inert = true;
        feedback.textContent = t("Dosya hazırlanıyor…");
        let failed = false;
        try {
          const response = await (file ? api.prepareDroppedComparison(value.token, file)
            : api.file("export_browser_prepare_comparison", {token:value.token}));
          if (!active() || response?.cancelled) return;
          results.replaceChildren();
          filters.hidden = true;
          outcome = null;
          pair = response?.ok ? response.archiveBrowser : null;
          reverse = false;
          if (!pair) throw Error();
        } catch {
          if (active()) {
            pair = null;
            reverse = false;
            results.replaceChildren();
            failed = true;
          }
        } finally {
          working = false;
          results.inert = false;
          if (active()) {
            renderSources();
            if (failed) feedback.textContent = t("Arşiv veya seçilen dosya açılamadı.");
          }
        }
      }
      zone.addEventListener("dragover", event => {
        if (!event.dataTransfer?.types.includes("Files")) return;
        event.preventDefault();
        event.stopPropagation();
        drop.classList.add("active");
      });
      zone.addEventListener("dragleave", () => drop.classList.remove("active"));
      zone.addEventListener("drop", event => {
        if (!event.dataTransfer?.files.length) return;
        event.preventDefault();
        event.stopPropagation();
        drop.classList.remove("active");
        const files = [...event.dataTransfer.files];
        if (files.length !== 1 || !/\.zip$/i.test(files[0].name)) feedback.textContent = t("Tek bir ZIP dosyası bırak");
        else selectSecond(files[0]);
      });
      const wording = {
        followers:{added:t("Yeni takipçiler"), removed:t("Takipten çıkanlar"), renamed:t("Kullanıcı adını değiştirmiş olabilir")},
        following:{added:t("Takip etmeye başladıkların"), removed:t("Takibi bıraktıkların"), renamed:t("Kullanıcı adını değiştirmiş olabilir")},
      };
      const missingText = {
        first:t("Bu liste eski ZIP’te yok; karşılaştırılamadı."),
        second:t("Bu liste yeni ZIP’te yok; karşılaştırılamadı."),
        both:t("Bu liste iki ZIP’te de yok."),
      };
      function renderOutcome() {
        results.replaceChildren();
        if (!outcome) return;
        filters.hidden = !Object.keys(outcome.directions).length;
        for (const [direction, label] of [["followers", t("Takipçiler")], ["following", t("Takip edilen")]]) {
          const data = outcome.directions[direction];
          const panel = make("section", "", "archive-dashboard-record");
          panel.append(make("h3", label));
          results.append(panel);
          if (!data) {
            panel.append(make("p", missingText[outcome.missing?.[direction] || "both"], "field-hint"));
            continue;
          }
          panel.append(make("p", num(data.before) + " → " + num(data.after), "archive-dashboard-compare-count"));
          if (!data.complete) panel.append(make("p", t("Listelerden biri kısmi okundu; sonuç eksik olabilir."), "notice warning"));
          for (const change of ["added", "removed", "renamed"]) {
            if (!data[change]) continue;
            const detail = make("details");
            detail.dataset.change = change;
            detail.append(make("summary", wording[direction][change] + " · " + num(data[change])));
            const list = make("ul", "", "archive-dashboard-compare-list");
            const more = action(t("Daha fazla göster"), () => loadNames(detail, direction, change, list, more));
            more.hidden = true;
            detail.append(list, more);
            if (change === "renamed") detail.append(make("p", t("ZIP’te hesap kimliği yok. Eski ve yeni adın takip tarihi aynı olduğu için eşleştirildi; kesin değildir."), "field-hint"));
            detail.addEventListener("toggle", () => { if (detail.open && !list.dataset.loaded) loadNames(detail, direction, change, list, more); });
            panel.append(detail);
          }
          if (!data.added && !data.removed && !data.renamed) panel.append(make("p", t("Kullanıcı adı farkı yok."), "field-hint"));
        }
      }
      async function loadNames(detail, direction, change, list, more, reset = false) {
        if (detail.dataset.reading || !active()) return;
        detail.dataset.reading = "1";
        const offset = reset ? 0 : Number(list.dataset.loaded || 0);
        try {
          const response = await api.command("export_browser_comparison_page", {token:value.token,direction,change,offset,
            query:query.value.trim(),order:order.value});
          if (!active() || !results.contains(detail)) return;
          if (!response?.ok) throw Error();
          const {rows, total} = response.archiveBrowser;
          if (reset) list.replaceChildren();
          for (const row of rows) {
            const li = make("li");
            if (change === "renamed") {
              li.append(profile(row.from) || "@" + row.from, " → ", profile(row.to) || "@" + row.to);
            } else li.append(profile(row.username) || row.username);
            if (row.timestamp) li.append(make("small", t("Takip tarihi: {0}", followDay(row.timestamp))));
            list.append(li);
          }
          list.dataset.loaded = String(offset + rows.length);
          more.hidden = offset + rows.length >= total;
          if (!total) list.append(make("li", t("Aramana uyan hesap yok."), "field-hint"));
        } catch {
          if (active()) feedback.textContent = t("Arşiv veya seçilen dosya açılamadı.");
        } finally {
          delete detail.dataset.reading;
        }
      }
      let filterTimer;
      const refilter = () => {
        clearTimeout(filterTimer);
        filterTimer = setTimeout(() => {
          for (const detail of results.querySelectorAll("details")) {
            const list = detail.querySelector("ul"), more = detail.querySelector("button");
            const direction = detail.closest("section") === results.children[0] ? "followers" : "following";
            if (detail.open) loadNames(detail, direction, detail.dataset.change, list, more, true);
            else { list.replaceChildren(); delete list.dataset.loaded; }
          }
        }, 250);
      };
      query.addEventListener("input", refilter);
      order.addEventListener("change", refilter);
      async function saveCsv() {
        const response = await api.file("export_browser_comparison_csv", {token:value.token}).catch(() => null);
        if (!active() || response?.cancelled) return;
        feedback.textContent = response?.ok ? t("Karşılaştırma CSV olarak kaydedildi.") : t("CSV kaydedilemedi.");
      }
      async function compare() {
        if (!pair || working) return;
        working = true;
        renderSources();
        results.inert = true;
        feedback.textContent = t("Dosya hazırlanıyor…");
        try {
          const response = await api.command("export_browser_compare", {token:value.token,candidate:pair.candidate,reverse});
          if (!active()) return;
          feedback.textContent = "";
          if (response?.cancelled) return;
          if (!response?.ok) throw Error();
          outcome = response.archiveBrowser;
          lastComparison = {token:value.token, pair, reverse, outcome};
          query.value = "";
          renderOutcome();
        } catch {
          if (active()) feedback.textContent = t("İki ZIP karşılaştırılamadı. Dosyalarda okunabilir takip listeleri olmalı.");
        } finally {
          if (active()) {
            working = false;
            results.inert = false;
            renderSources();
          }
        }
      }
      renderSources();
      renderOutcome();
    }

    function selectGroup(id, label, files, preferred) {
      clearThumbnails();
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
    return {root, language, open, close() {
      closed = true;
      root.removeEventListener("dragover", dragOver);
      root.removeEventListener("dragleave", dragLeave);
      root.removeEventListener("drop", drop);
      clearTimeout(searchTimer);
      clearThumbnails();
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
