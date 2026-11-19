(() => {
  "use strict";
  // Insights computed locally from the open Instagram ZIP: interactions,
  // activity hours, logins, ads and a shareable year summary without usernames.
  const PAGE = 50;
  const YEAR = 365 * 86400;

  function render(body, {api, t, num, language, archive, active, profile}) {
    const make = (tag, text, css) => {
      const node = document.createElement(tag);
      if (text !== undefined && text !== null && text !== "") node.textContent = text;
      if (css) node.className = css;
      return node;
    };
    const button = (text, run, css = "button secondary") => {
      const node = make("button", text, css);
      node.type = "button";
      node.addEventListener("click", run);
      return node;
    };
    const day = new Intl.DateTimeFormat(language, {dateStyle:"medium"});
    const moment = new Intl.DateTimeFormat(language, {dateStyle:"medium", timeStyle:"short"});
    const monthName = new Intl.DateTimeFormat(language, {month:"long", timeZone:"UTC"});
    const weekday = new Intl.DateTimeFormat(language, {weekday:"short", timeZone:"UTC"});
    const weekdayLong = new Intl.DateTimeFormat(language, {weekday:"long", timeZone:"UTC"});
    const dateOf = seconds => day.format(new Date(seconds * 1000));
    const hourLabel = hour => String(hour).padStart(2, "0") + ":00";
    const monthLabel = month => monthName.format(new Date(Date.UTC(2024, month - 1, 1)));
    const tile = (label, value, hint) => {
      const node = make("div", "", "archive-dashboard-category archive-dashboard-metric");
      node.append(make("small", label), make("strong", value === null || value === undefined ? "—" : value));
      if (hint) node.append(make("small", hint));
      return node;
    };
    const grid = (...tiles) => {
      const node = make("div", "", "archive-dashboard-metric-grid");
      node.append(...tiles);
      return node;
    };
    const note = text => make("p", text, "field-hint");
    const empty = text => make("p", text, "archive-insights-empty field-hint");

    if (!archive) {
      body.append(note(t("İçgörüleri görmek için önce Instagram ZIP’ini aç.")));
      return;
    }
    const feedback = note(t("ZIP’teki kayıtlar inceleniyor…"));
    feedback.setAttribute("role", "status");
    const tabs = make("div", "", "archive-dashboard-tabs archive-insights-tabs");
    const panel = make("section", "", "archive-insights-panel");
    body.append(note(t("Bu görünüm yalnız açtığın ZIP’i kullanır. Instagram’a istek gönderilmez ve sonuçlar kaydedilmez.")), feedback, tabs, panel);

    let data;
    const sections = [
      ["people", t("Etkileşim"), showPeople],
      ["activity", t("Etkinlik saatleri"), showActivity],
      ["security", t("Hesap güvenliği"), showSecurity],
      ["ads", t("Reklamlar"), showAds],
      ["year", t("Yıl özeti"), showYear],
    ];
    function select(id) {
      for (const control of tabs.children) control.setAttribute("aria-pressed", String(control.dataset.insight === id));
      panel.replaceChildren();
      sections.find(section => section[0] === id)[2]();
    }
    for (const [id, label] of sections) {
      const control = button(label, () => select(id));
      control.dataset.insight = id;
      control.setAttribute("aria-pressed", "false");
      tabs.append(control);
    }
    tabs.hidden = true;

    (async () => {
      const response = await api.command("export_browser_insights", {token:archive.token, offset:new Date().getTimezoneOffset()}).catch(() => null);
      if (!active()) return;
      if (!response?.ok) {
        feedback.textContent = t("İçgörüler hazırlanamadı. Dosya tarayıcısıyla kayıtları yine inceleyebilirsin.");
        return;
      }
      data = response.archiveBrowser;
      const skipped = data.skipped.map(key => ({likes:t("Beğeniler"), comments:t("Yorumlar"), stories:t("Hikâye etkileşimleri"),
        saved:t("Kaydetmeler"), security:t("Hesap güvenliği"), ads:t("Reklamlar")})[key] || key);
      feedback.textContent = [
        data.dateRange ? t("ZIP’in kapsadığı aralık: {0} – {1}", day.format(new Date(data.dateRange[0])), day.format(new Date(data.dateRange[1]))) : "",
        skipped.length ? t("Çok büyük veya okunamayan kaynaklar atlandı: {0}", skipped.join(", ")) : "",
      ].filter(Boolean).join(" · ");
      tabs.hidden = false;
      select("people");
    })();

    function personRow(person, extra) {
      const card = make("article", "", "archive-dashboard-record archive-insights-person");
      const header = make("header");
      header.append(profile(person.username) || make("strong", "@" + person.username));
      if (person.followsYou === true) header.append(make("span", t("Seni takip ediyor"), "archive-insights-badge positive"));
      else if (person.followsYou === false) header.append(make("span", t("Seni takip etmiyor"), "archive-insights-badge negative"));
      if (person.youFollow === false) header.append(make("span", t("Takip etmiyorsun"), "archive-insights-badge"));
      card.append(header);
      const facts = [];
      if (person.followedAt) facts.push(t("Takip başlangıcı: {0}", dateOf(person.followedAt)));
      facts.push(person.count ? t("{0} etkileşim", num(person.count)) : t("Bu ZIP’te etkileşim yok"));
      if (person.last) facts.push(t("Son etkileşim: {0}", dateOf(person.last)));
      if (extra) facts.push(extra);
      card.append(make("small", facts.join(" · ")));
      return card;
    }

    function pagedList(container, rows, row, size = PAGE) {
      let shown = 0;
      const list = make("div", "", "archive-dashboard-rows archive-dashboard-compact");
      const more = button(t("Daha fazla göster"), draw);
      function draw() {
        list.append(...rows.slice(shown, shown + size).map(row));
        shown = Math.min(rows.length, shown + size);
        more.hidden = shown >= rows.length;
      }
      container.append(list, more);
      draw();
    }

    function showPeople() {
      const people = data.people;
      if (!people.lists.following) {
        panel.append(empty(t("Bu ZIP’te takip ettiklerin listesi yok. Etkileşim analizi için dışa aktarıma Takipçiler ve takip edilenler bölümünü ekle.")));
      }
      const sources = people.sources;
      const counted = [["likes", t("Beğeniler")], ["comments", t("Yorumlar")], ["stories", t("Hikâye etkileşimleri")], ["saved", t("Kaydetmeler")]];
      if (counted.every(([key]) => sources[key] === null))
        panel.append(empty(t("Bu ZIP’te beğeni, yorum, hikâye veya kaydetme kaydı yok. Dışa aktarıma Etkinliklerin bölümünü eklersen etkileşimler sayılır.")));
      panel.append(grid(...counted.map(([key, label]) => tile(label, sources[key] === null ? t("Yok") : num(sources[key])))));
      panel.append(note(t("Etkileşim: senin beğendiğin, yorum yaptığın, hikâyesine tepki verdiğin veya kaydettiğin içerikler. Mesajlar sayılmaz. ZIP’in tarih aralığı dışındakiler görünmez.")));
      const reference = data.activity.last || Date.now() / 1000;
      const views = [
        ["silent", t("Hiç etkileşmediklerin"), () => people.following.filter(row => !row.count)
          .sort((a, b) => (a.followedAt ?? Infinity) - (b.followedAt ?? Infinity))],
        ["stale", t("Son 12 ayda etkileşmediklerin"), () => people.following.filter(row => row.count && (!row.last || row.last < reference - YEAR))
          .sort((a, b) => (a.last ?? 0) - (b.last ?? 0))],
        ["oneway", t("Seni takip etmeyenler"), () => people.lists.followers ? people.following.filter(row => row.followsYou === false)
          .sort((a, b) => (a.followedAt ?? Infinity) - (b.followedAt ?? Infinity)) : null],
        ["top", t("En çok etkileştiklerin"), () => people.top],
      ];
      const chips = make("div", "", "archive-dashboard-tabs");
      const result = make("div");
      panel.append(chips, result);
      function choose(id) {
        for (const chip of chips.children) chip.setAttribute("aria-pressed", String(chip.dataset.view === id));
        const rows = views.find(view => view[0] === id)[2]();
        result.replaceChildren();
        if (rows === null) {
          result.append(empty(t("Bu ZIP’te takipçi listesi olmadığı için geri takip bilgisi yok.")));
          return;
        }
        result.append(make("h3", t("{0} hesap", num(rows.length))));
        if (id === "silent") result.append(note(t("En eski takipten başlayarak sıralanır. Etkileşim olmaması kişiyi tanımadığın anlamına gelmez.")));
        if (id === "stale") result.append(note(t("Son etkileşimi 12 aydan eski olanlar. Karşılaştırma ZIP’teki en yeni etkinliğe göre yapılır.")));
        if (!rows.length) result.append(empty(t("Bu görünümde hesap yok.")));
        else pagedList(result, rows, person => personRow(person, id === "oneway" && person.followedAt
          ? t("{0} gündür takip ediyorsun", num(Math.max(0, Math.floor((reference - person.followedAt) / 86400)))) : ""));
      }
      for (const [id, label] of views) {
        const chip = button(label, () => choose(id));
        chip.dataset.view = id;
        chips.append(chip);
      }
      choose(people.following.length ? "silent" : "top");
    }

    function showActivity() {
      const {grid: cells, total, first, last} = data.activity;
      if (!total) {
        panel.append(empty(t("Bu ZIP’te tarihli beğeni, yorum, hikâye veya kaydetme kaydı yok.")));
        return;
      }
      const max = Math.max(...cells.flat());
      let best = [0, 0];
      cells.forEach((row, weekdayIndex) => row.forEach((count, hour) => {
        if (count > cells[best[0]][best[1]]) best = [weekdayIndex, hour];
      }));
      const totals = cells.map(row => row.reduce((sum, value) => sum + value, 0));
      const hours = Array.from({length:24}, (_, hour) => cells.reduce((sum, row) => sum + row[hour], 0));
      const busiestDay = totals.indexOf(Math.max(...totals));
      const busiestHour = hours.indexOf(Math.max(...hours));
      // 2024-01-01 was a Monday; the backend's weekday 0 is Monday.
      const named = (format, index) => format.format(new Date(Date.UTC(2024, 0, 1 + index)));
      panel.append(grid(
        tile(t("Tarihli etkinlik"), num(total), first && last ? dateOf(first) + " – " + dateOf(last) : ""),
        tile(t("En yoğun gün"), named(weekdayLong, busiestDay)),
        tile(t("En yoğun saat"), hourLabel(busiestHour)),
        tile(t("En yoğun zaman dilimi"), named(weekday, best[0]) + " " + hourLabel(best[1])),
      ));
      const table = make("div", "", "archive-insights-heatmap");
      table.setAttribute("role", "table");
      table.setAttribute("aria-label", t("Haftanın günü ve saate göre etkinlik"));
      const head = make("div", "", "archive-insights-heatmap-row");
      head.setAttribute("role", "row");
      head.append(make("span", "", "archive-insights-heatmap-label"));
      for (let hour = 0; hour < 24; hour++) head.append(make("span", hour % 3 ? "" : String(hour).padStart(2, "0"), "archive-insights-heatmap-hour"));
      table.append(head);
      cells.forEach((row, index) => {
        const line = make("div", "", "archive-insights-heatmap-row");
        line.setAttribute("role", "row");
        line.append(make("span", named(weekday, index), "archive-insights-heatmap-label"));
        row.forEach((count, hour) => {
          const cell = make("span", "", "archive-insights-heatmap-cell");
          cell.setAttribute("role", "cell");
          cell.style.setProperty("--level", count ? String(Math.round(15 + 85 * count / max)) + "%" : "0%");
          cell.title = named(weekdayLong, index) + " " + hourLabel(hour) + " · " + num(count);
          cell.setAttribute("aria-label", cell.title);
          line.append(cell);
        });
        table.append(line);
      });
      panel.append(make("h3", t("Haftanın günü ve saate göre")), table);
      panel.append(note(t("Saatler bu bilgisayarın şu anki saat dilimine göre gösterilir; yaz saati geçişleri hesaba katılmaz.")));
    }

    function showSecurity() {
      const security = data.security;
      if (!security.present) {
        panel.append(empty(t("Bu ZIP’te giriş ve güvenlik kayıtları yok. Dışa aktarıma Güvenlik ve giriş bilgileri bölümünü ekle.")));
        return;
      }
      let reveal = false;
      const mask = ip => !ip ? "—" : reveal ? ip : ip.includes(":") ? ip.split(":").slice(0, 2).join(":") + ":…" : ip.replace(/\.\d+\.\d+$/, ".•.•");
      panel.append(grid(
        tile(t("Giriş kaydı"), num(security.loginCount)),
        tile(t("Farklı IP adresi"), num(security.ipCount)),
        tile(t("Farklı cihaz"), num(security.devices.length)),
        tile(t("Parola değişikliği"), num(security.passwordChanges.length)),
      ));
      const recent = security.devices.filter(item => item.recent);
      if (recent.length) {
        const alert = make("div", "", "archive-insights-alert");
        alert.append(make("strong", t("Son 30 günde ilk kez görülen cihaz: {0}", recent.map(item => item.device).join(", "))),
          make("p", t("Bu sen değilsen Instagram’da parolanı değiştir ve Hesaplar Merkezi → Parola ve güvenlik → Giriş yaptığın yerler bölümünü kontrol et.")));
        panel.append(alert);
      }
      if (security.devices.length) {
        panel.append(make("h3", t("Giriş yapılan cihazlar")));
        const list = make("div", "", "archive-dashboard-rows archive-dashboard-compact");
        for (const item of security.devices) {
          const card = make("article", "", "archive-dashboard-record");
          const header = make("header");
          header.append(make("strong", item.device));
          if (item.recent) header.append(make("span", t("Yeni"), "archive-insights-badge negative"));
          card.append(header, make("small", t("{0} giriş · İlk: {1} · Son: {2}", num(item.count), dateOf(item.first), dateOf(item.last))));
          list.append(card);
        }
        panel.append(list);
      }
      if (security.logins.length) {
        const heading = make("div", "", "archive-insights-heading");
        const toggle = button(t("IP adreslerini göster"), () => {
          reveal = !reveal;
          toggle.textContent = reveal ? t("IP adreslerini gizle") : t("IP adreslerini göster");
          drawLogins();
        });
        heading.append(make("h3", t("Son girişler")), toggle);
        const logins = make("div");
        panel.append(heading, note(t("IP adresleri ekran paylaşımına karşı varsayılan olarak kısaltılır.")), logins);
        function drawLogins() {
          logins.replaceChildren();
          pagedList(logins, security.logins, item => {
            const card = make("article", "", "archive-dashboard-record");
            const header = make("header");
            header.append(make("strong", item.device || t("Bilinmeyen cihaz")), make("time", moment.format(new Date(item.at * 1000))));
            card.append(header, make("small", "IP: " + mask(item.ip)));
            return card;
          }, 10);
        }
        drawLogins();
      }
      if (security.passwordChanges.length) {
        panel.append(make("h3", t("Parola değişiklikleri")));
        const list = make("ul", "", "archive-insights-list");
        for (const at of security.passwordChanges) list.append(make("li", moment.format(new Date(at * 1000))));
        panel.append(list);
      }
      if (security.privacyChanges.length) {
        panel.append(make("h3", t("Hesap gizliliği değişiklikleri")));
        const list = make("ul", "", "archive-insights-list");
        for (const item of security.privacyChanges) list.append(make("li", moment.format(new Date(item.at * 1000)) + (item.value ? " · " + item.value : "")));
        panel.append(list);
      }
    }

    function showAds() {
      const ads = data.ads;
      if (!ads.present) {
        panel.append(empty(t("Bu ZIP’te reklam bilgileri yok. Dışa aktarıma Reklam bilgileri bölümünü ekle.")));
        return;
      }
      panel.append(grid(
        tile(t("Etkinliğini kullanan reklamveren"), num(ads.advertiserCount)),
        tile(t("Gördüğün reklam"), ads.viewed === null ? t("Yok") : num(ads.viewed)),
        tile(t("Tıkladığın reklam"), ads.clicked === null ? t("Yok") : num(ads.clicked)),
        tile(t("İlgi alanı"), num(ads.topicCount)),
      ));
      const flags = [
        ["has_data_file_custom_audience", count => t("{0} reklamveren seninle ilgili bilgiyi içeren bir liste yükledi", count)],
        ["has_remarketing_custom_audience", count => t("{0} reklamveren seni yeniden hedefleme kitlesine ekledi", count)],
        ["has_in_person_store_visit", count => t("{0} reklamveren mağaza ziyaretine dayalı kitle kullandı", count)],
      ].filter(([key]) => ads.flags[key]);
      if (flags.length) {
        const list = make("ul", "", "archive-insights-list");
        for (const [key, label] of flags) list.append(make("li", label(num(ads.flags[key]))));
        panel.append(list);
      }
      if (ads.topics.length) {
        panel.append(make("h3", t("Instagram’ın sana atadığı ilgi alanları")));
        const chips = make("div", "", "archive-insights-chips");
        for (const topic of ads.topics) chips.append(make("span", topic, "archive-insights-chip"));
        panel.append(chips);
      }
      if (ads.topAuthors.length) {
        panel.append(make("h3", t("En çok reklamını gördüğün hesaplar")));
        const list = make("ol", "", "archive-insights-list");
        for (const item of ads.topAuthors) list.append(make("li", item.name + " · " + num(item.count)));
        panel.append(list);
      }
      if (ads.advertisers.length) {
        panel.append(make("h3", t("Etkinliğini veya bilgini kullanan reklamverenler")));
        const filter = make("input");
        filter.type = "search";
        filter.maxLength = 60;
        filter.placeholder = t("Reklamveren ara");
        filter.setAttribute("aria-label", filter.placeholder);
        const chips = make("div", "", "archive-insights-chips");
        const draw = () => {
          const needle = filter.value.trim().toLocaleLowerCase(language);
          chips.replaceChildren(...ads.advertisers.filter(name => !needle || name.toLocaleLowerCase(language).includes(needle))
            .slice(0, 300).map(name => make("span", name, "archive-insights-chip")));
        };
        filter.addEventListener("input", draw);
        panel.append(filter, chips);
        draw();
        if (ads.advertiserCount > ads.advertisers.length) panel.append(note(t("İlk {0} reklamveren gösteriliyor.", num(ads.advertisers.length))));
      }
    }

    function showYear() {
      const years = Object.keys(data.years).sort().reverse();
      if (!years.length) {
        panel.append(empty(t("Bu ZIP’te yıl özeti için tarihli kayıt yok.")));
        return;
      }
      const chips = make("div", "", "archive-dashboard-tabs");
      const preview = make("div", "", "archive-insights-year");
      panel.append(chips, preview);
      const labels = [
        ["likes", t("Beğeni")], ["comments", t("Yorum")], ["stories", t("Hikâye etkileşimi")], ["saved", t("Kaydetme")],
        ["newFollowers", t("Yeni takipçi")], ["newFollowing", t("Takip etmeye başladığın")],
        ["shared", t("Paylaşım")], ["activeDays", t("Aktif gün")],
      ];
      function choose(year) {
        for (const chip of chips.children) chip.setAttribute("aria-pressed", String(chip.dataset.year === year));
        const item = {...data.years[year], shared:data.years[year].posts + data.years[year].reels + data.years[year].storiesShared};
        preview.replaceChildren();
        const highlights = [
          item.busiestMonth ? [t("En yoğun ay"), monthLabel(item.busiestMonth)] : null,
          item.busiestHour !== null ? [t("En yoğun saat"), hourLabel(item.busiestHour)] : null,
        ].filter(Boolean);
        preview.append(grid(...labels.map(([key, label]) => tile(label, num(item[key]))), ...highlights.map(([label, value]) => tile(label, value))));
        const canvas = drawCard(year, item, labels, highlights);
        const image = make("img", "", "archive-insights-card");
        image.src = canvas.toDataURL("image/png");
        image.alt = t("{0} yıl özeti görseli", year);
        const status = note("");
        status.setAttribute("role", "status");
        const save = button(t("Görseli PNG olarak kaydet"), async () => {
          save.disabled = true;
          try {
            const result = await api.file("save_insight_image", {png:image.src.split(",")[1], name:"Followtrail-" + year + ".png"});
            if (result?.ok && !result.cancelled) status.textContent = t("Görsel kaydedildi.");
            else if (!result?.ok) throw Error();
          } catch {
            status.textContent = t("Görsel kaydedilemedi.");
          } finally { save.disabled = false; }
        }, "button primary");
        preview.append(make("h3", t("Paylaşılabilir görsel")),
          note(t("Görselde kullanıcı adı, profil fotoğrafı veya kişi bilgisi yoktur; yalnız sayılar yer alır.")), image, save, status);
      }
      for (const year of years) {
        const chip = button(year, () => choose(year));
        chip.dataset.year = year;
        chips.append(chip);
      }
      choose(years[0]);
    }

    function drawCard(year, item, labels, highlights) {
      const canvas = document.createElement("canvas");
      const width = 1080, height = 1350;
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext("2d");
      const gradient = context.createLinearGradient(0, 0, width, height);
      gradient.addColorStop(0, "#2b1f5c");
      gradient.addColorStop(1, "#674bd8");
      context.fillStyle = gradient;
      context.fillRect(0, 0, width, height);
      const font = (size, weight = 600) => `${weight} ${size}px "Segoe UI", system-ui, sans-serif`;
      context.fillStyle = "#ffffff";
      context.font = font(40, 600);
      context.fillText("Followtrail", 80, 120);
      context.font = font(170, 800);
      context.fillText(year, 80, 320);
      context.font = font(40, 500);
      context.fillStyle = "rgba(255,255,255,0.8)";
      context.fillText(t("Instagram yılım, rakamlarla"), 80, 390);
      const cells = [...labels.map(([key, label]) => [label, num(item[key])]), ...highlights];
      const columns = 2, top = 450, cellWidth = (width - 160 - 40) / columns, cellHeight = 128;
      cells.slice(0, 10).forEach(([label, value], index) => {
        const x = 80 + (index % columns) * (cellWidth + 40);
        const y = top + Math.floor(index / columns) * (cellHeight + 16);
        context.fillStyle = "rgba(255,255,255,0.12)";
        context.beginPath();
        context.roundRect(x, y, cellWidth, cellHeight, 28);
        context.fill();
        context.fillStyle = "rgba(255,255,255,0.78)";
        context.font = font(30, 500);
        context.fillText(label, x + 32, y + 46, cellWidth - 64);
        context.fillStyle = "#ffffff";
        context.font = font(56, 750);
        context.fillText(String(value), x + 32, y + 106, cellWidth - 64);
      });
      context.fillStyle = "rgba(255,255,255,0.7)";
      context.font = font(28, 500);
      context.fillText(t("Instagram ZIP’inden cihazda hesaplandı"), 80, height - 70);
      return canvas;
    }
  }
  window.TrackerArchiveInsights = Object.freeze({render});
})();
