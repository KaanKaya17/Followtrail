(() => {
  "use strict";
  const icon = window.TrackerIcon;
  const esc = (value) =>
    String(value ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
  const number = (value) => new Intl.NumberFormat("tr-TR").format(value);
  const pages = {
    overview: ["Özet", "home"],
    people: ["Kişiler", "people"],
    lists: ["Listeler", "list"],
    timeline: ["Zaman çizelgesi", "clock"],
    settings: ["Ayarlar", "settings"],
  };
  const original = [
    {
      id: "200",
      name: "Deniz Çelik",
      username: "deniz.demo",
      followers: 1356,
      following: 362,
      tone: "lavender",
      favorite: true,
      watching: true,
      group: "Arkadaşlar",
      bio: "Kahve, kitaplar ve yeni yollar.",
      note: "Birlikte gidilecek yerler listemiz var.",
    },
    {
      id: "201",
      name: "Ada Yılmaz",
      username: "ada.demo",
      followers: 2056,
      following: 365,
      tone: "peach",
      favorite: true,
      watching: true,
      group: "İçerik üreticileri",
      bio: "Yolda olmayı seviyorum.",
      note: "Gezi önerilerine bak.",
    },
    {
      id: "202",
      name: "Ece Arslan",
      username: "ece.demo",
      followers: 948,
      following: 284,
      tone: "mint",
      favorite: false,
      watching: true,
      group: "Arkadaşlar",
      bio: "Şehrin küçük hikâyeleri.",
      note: "",
    },
    {
      id: "203",
      name: "Mert Aydın",
      username: "mert.demo",
      followers: 714,
      following: 502,
      tone: "sand",
      favorite: false,
      watching: false,
      group: "Arkadaşlar",
      bio: "Müzik, fotoğraf, biraz da kahve.",
      note: "",
    },
    {
      id: "204",
      name: "Selin Aksoy",
      username: "selin.demo",
      followers: 1824,
      following: 623,
      tone: "rose",
      favorite: false,
      watching: false,
      group: "İçerik üreticileri",
      bio: "Gündelik şeylerden ilham alıyorum.",
      note: "",
    },
    {
      id: "205",
      name: "Can Demir",
      username: "can.demo",
      followers: 683,
      following: 341,
      tone: "sky",
      favorite: false,
      watching: true,
      group: "Arkadaşlar",
      bio: "İyi bir yürüyüş her şeyi değiştirir.",
      note: "",
    },
    {
      id: "206",
      name: "Duru Yalçın",
      username: "duru.demo",
      followers: 1208,
      following: 438,
      tone: "lavender",
      favorite: false,
      watching: false,
      group: "Diğer",
      bio: "Yeni fikirlerin peşinde.",
      note: "",
    },
    {
      id: "207",
      name: "Bora Eren",
      username: "bora.demo",
      followers: 527,
      following: 293,
      tone: "mint",
      favorite: false,
      watching: false,
      group: "Diğer",
      bio: "Az eşya, çok anı.",
      note: "",
    },
  ];
  const own = {
    id: "100",
    name: "Benim hesabım",
    username: "benim.demo",
    followers: 875,
    following: 412,
    tone: "lavender",
    bio: "Güzel anlar biriktiriyorum.",
  };
  const history = [800, 812, 809, 824, 838, 831, 849, 858, 851, 864, 857, 875];
  const listRecords = {
    followers: {
      current: ["200", "201", "202", "203", "204", "205", "206", "207"],
      previous: ["200", "201", "202", "207", "208"],
    },
    following: {
      current: ["200", "201", "203", "205", "206", "207"],
      previous: ["200", "201", "203", "205", "207", "208"],
    },
  };
  const events = [
    {
      id: "count",
      day: "Bugün",
      time: "16:42",
      type: "profile",
      icon: "people",
      tone: "lavender",
      title: "Takipçi sayında yeni bir gözlem",
      text: "@benim.demo · Önceki kayda göre +18",
      old: "857 takipçi",
      next: "875 takipçi",
      scope:
        "26 Eki 16:40 → 27 Eki 16:42 · İki örnek profil gözleminin net farkı.",
    },
    {
      id: "bio",
      day: "Bugün",
      time: "15:18",
      type: "profile",
      icon: "note",
      tone: "mint",
      title: "Deniz biyografisini değiştirdi",
      text: "@deniz.demo · Biyografi değişikliği",
      old: "Kahve ve kitaplar.",
      next: "Kahve, kitaplar ve yeni yollar.",
      scope:
        "Son iki örnek profil kaydı karşılaştırıldı. Değişikliğin kesin saati bilinmiyor.",
    },
    {
      id: "list",
      day: "Bugün",
      time: "12:06",
      type: "lists",
      icon: "compare",
      tone: "peach",
      title: "Takipçi listesinde farklı hesaplar var",
      text: "@benim.demo · 4 yeni görülen, 1 görünmeyen",
      old: "Önceki örnek listede 5 hesap",
      next: "Son örnek listede 8 hesap",
      scope:
        "Bu bir liste karşılaştırması örneğidir; kesin takipten çıkış bildirimi değildir.",
    },
    {
      id: "name",
      day: "Dün",
      time: "18:24",
      type: "profile",
      icon: "photo",
      tone: "rose",
      title: "Ada kullanıcı adını güncelledi",
      text: "@ada.demo · Kullanıcı adı değişikliği",
      old: "@ada.yol.demo",
      next: "@ada.demo",
      scope: "Aynı örnek hesap kimliğinin iki gözlemindeki kullanıcı adları.",
    },
    {
      id: "check",
      day: "Dün",
      time: "16:40",
      type: "profile",
      icon: "check",
      tone: "lavender",
      title: "Profil gözlemi kaydedildi",
      text: "@benim.demo · Karşılaştırma için bir başlangıç",
      old: "Önceki örnek gözlem",
      next: "857 takipçi · 408 takip edilen",
      scope:
        "Zaman çizelgesi kayıt zamanını gösterir; Instagram olayının kesin zamanını göstermez.",
    },
  ];
  const state = {
    page: "overview",
    people: structuredClone(original),
    peopleFilter: "all",
    peopleSearch: "",
    list: "followers",
    listSearch: "",
    comparing: false,
    range: "7",
    eventType: "all",
    compact: false,
    notifications: true,
  };
  try {
    state.compact = localStorage.getItem("tracker-ui-compact") === "true";
  } catch {
    /* Preferences are optional. */
  }
  let toastTimer;
  function toast(text) {
    const node = document.getElementById("toast");
    node.textContent = text;
    node.classList.add("visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove("visible"), 4200);
  }
  const person = (id) =>
    id === own.id ? own : state.people.find((p) => p.id === id);
  const avatar = (p, large = false) =>
    `<span class="avatar ${esc(p.tone)} ${large ? "large" : ""}" aria-hidden="true">${esc(
      p.name
        .split(" ")
        .map((x) => x[0])
        .slice(0, 2)
        .join(""),
    )}</span>`;
  const button = (text, action, symbol, kind = "secondary", extra = "") =>
    `<button type="button" class="button ${kind}" data-action="${action}" ${extra}>${symbol ? icon(symbol) : ""}<span>${text}</span></button>`;
  const iconButton = (symbol, label, action, extra = "") =>
    `<button type="button" class="icon-button" aria-label="${esc(label)}" title="${esc(label)}" data-action="${action}" ${extra}>${icon(symbol)}</button>`;
  function sidebar() {
    return `<aside class="sidebar"><a class="brand" href="#overview"><span class="brand-mark">${icon("chart")}</span><span><strong>Tracker</strong><small>Çevren, tek bakışta.</small></span></a>
      <span class="nav-label">ÇALIŞMA ALANIN</span><nav aria-label="Ana gezinme">${Object.entries(
        pages,
      )
        .map(
          ([key, [label, symbol]]) =>
            `<a class="nav-item ${state.page === key ? "selected" : ""}" href="#${key}" ${state.page === key ? 'aria-current="page"' : ""}>${icon(symbol)}<span>${label}</span>${key === "people" ? `<span class="nav-count">${state.people.length}</span>` : ""}</a>`,
        )
        .join("")}</nav>
      <div class="sidebar-bottom"><div class="privacy-note">${icon("shield")}<strong>Çevren sana özel.</strong><p>Gözlemler, karşılaştırmalar ve sana ait notlar. Tek bir yerde.</p><button class="text-button" data-action="privacy">Gizliliği incele ${icon("arrow")}</button></div><div class="preview-label"><span class="status-dot"></span> Arayüz önizlemesi <span>01</span></div></div></aside>`;
  }
  function topbar() {
    return `<header class="topbar"><a class="mobile-brand" href="#overview"><span class="brand-mark">${icon("chart")}</span>Tracker</a><div class="breadcrumbs"><span>Çalışma alanım</span>${icon("chevron")}<strong>${pages[state.page][0]}</strong></div><button class="global-search" data-action="search">${icon("search")}<span>Kişi ara</span><kbd>Ctrl K</kbd></button><div class="top-actions"><span class="sample-pill">Örnek veriler</span>${iconButton("bell", "Örnek bildirimler", "notifications")}<button class="account-button" data-action="account" aria-label="Örnek hesap bağlantısını incele">${avatar(own)}<span><strong>@benim.demo</strong><small>Örnek hesap</small></span>${icon("down")}</button></div></header>`;
  }
  function title(kicker, heading, text, action = "") {
    return `<div class="page-heading"><div><span class="eyebrow">${kicker}</span><h1>${heading}</h1><p>${text}</p></div>${action}</div>`;
  }
  function footer() {
    return `<footer class="page-footer"><span>${icon("lock")} Yalnız örnek veriler · Instagram bağlantısı etkin değil</span><button class="text-button" data-action="scope">Bu önizleme hakkında ${icon("info")}</button></footer>`;
  }
  function mobileNavigation() {
    return `<nav class="mobile-nav" aria-label="Mobil gezinme">${Object.entries(
      pages,
    )
      .map(
        ([key, [label, symbol]]) =>
          `<a href="#${key}" class="${state.page === key ? "selected" : ""}" ${state.page === key ? 'aria-current="page"' : ""}>${icon(symbol)}<span>${key === "timeline" ? "Geçmiş" : label}</span></a>`,
      )
      .join("")}</nav>`;
  }
  function chart() {
    const values = state.range === "7" ? history.slice(-7) : history;
    const start = history.length - values.length;
    const low = Math.floor((Math.min(...values) - 12) / 20) * 20;
    const high = Math.ceil((Math.max(...values) + 8) / 20) * 20;
    const points = values.map((v, i) => [
      50 + (i * 620) / (values.length - 1),
      210 - ((v - low) / (high - low)) * 170,
    ]);
    const path = points
      .map(([x, y], i) => `${i ? "L" : "M"} ${x.toFixed(2)} ${y.toFixed(2)}`)
      .join(" ");
    return `<div class="chart-card card"><div class="section-heading"><div><h2>Takipçi geçmişi</h2><p>Sayılardan bir hikâye çıkar.</p></div><div class="segmented" role="group" aria-label="Grafik tarih aralığı">${[
      ["7", "7 gün"],
      ["30", "30 gün"],
      ["all", "Tümü"],
    ]
      .map(
        ([key, label]) =>
          `<button class="${state.range === key ? "active" : ""}" data-action="range" data-value="${key}" aria-pressed="${state.range === key}">${label}</button>`,
      )
      .join("")}</div></div>
      <div class="chart-summary"><strong>${number(875)}<span>takipçi</span></strong><span class="delta positive">${icon("chart")} +${875 - values[0]} <span>seçilen örneklerde</span></span></div>
      <div class="chart-wrap"><svg class="history-chart" viewBox="0 0 710 252" role="img" aria-labelledby="chart-title chart-desc"><title id="chart-title">Örnek takipçi geçmişi</title><desc id="chart-desc">${values.length} örnek kayıt. İlk sayı ${values[0]}, son sayı 875. Noktalar arasındaki bütün hareketler bilinmiyor.</desc>
      ${[0, 1, 2, 3]
        .map((i) => {
          const y = 40 + (i * 170) / 3;
          return `<line x1="50" y1="${y}" x2="680" y2="${y}" class="grid-line"/><text x="34" y="${y + 4}" text-anchor="end" class="axis-label">${Math.round(high - (i * (high - low)) / 3)}</text>`;
        })
        .join("")}
      <path d="${path} L670 210 L50 210 Z" class="chart-area"/><path d="${path}" class="chart-line"/>
      ${points.map(([x, y], i) => `<circle cx="${x}" cy="${y}" r="5" class="chart-point ${i === points.length - 1 ? "last" : ""}" tabindex="0" data-point="${16 + start + i} Eki · ${values[i]} takipçi" aria-label="${16 + start + i} Ekim: ${values[i]} takipçi"><title>${16 + start + i} Eki · ${values[i]} takipçi</title></circle>`).join("")}
      ${[0, Math.floor((values.length - 1) / 3), Math.floor(((values.length - 1) * 2) / 3), values.length - 1].map((i) => `<text x="${points[i][0]}" y="241" text-anchor="middle" class="axis-label">${16 + start + i} Eki</text>`).join("")}</svg></div>
      <div class="chart-caption"><span id="chart-caption">${values.length} örnek gözlem · ${16 + start}–27 Ekim</span><button class="text-button" data-action="history">Kayıtları gör ${icon("arrow")}</button></div></div>`;
  }
  function favoriteRows() {
    const favorites = state.people.filter((p) => p.favorite);
    return favorites.length
      ? favorites
          .slice(0, 3)
          .map(
            (p) =>
              `<button class="person-row" data-action="person" data-id="${p.id}">${avatar(p)}<span class="person-ident"><strong>${esc(p.name)}</strong><small>@${p.username}</small></span><span class="row-number">${number(p.followers)}<small>takipçi</small></span>${icon("chevron")}</button>`,
          )
          .join("")
      : `<div class="empty-small">${icon("star")}<p>Kişilerde yıldızladığın hesaplar burada görünür.</p></div>`;
  }
  function eventRow(e, compact = false) {
    return `<button class="event-row ${compact ? "compact-event" : ""}" data-action="event" data-id="${e.id}"><span class="event-icon ${e.tone}">${icon(e.icon)}</span><span class="event-copy"><strong>${e.title}</strong><small>${e.text}</small></span><time>${e.time}</time>${icon("chevron")}</button>`;
  }
  function overview() {
    return `${title("27 EKİM 2026 · SALI", "Çevrende neler oluyor?", "Hesabın, favori kişilerin ve son gözlemlerin bir arada.", button("Gözlemi yenile", "refresh", "refresh", "primary"))}
      <section class="stats-grid" aria-label="Örnek hesap özeti"><button class="stat-card violet" data-action="open-list" data-value="followers"><span class="stat-label">Takipçiler ${icon("people")}</span><strong class="stat-value">875</strong><span class="stat-bottom"><span class="stat-change">${icon("chart")} +18 <span>son gözlemde</span></span>${icon("arrow")}</span></button>
      <button class="stat-card peach" data-action="open-list" data-value="following"><span class="stat-label">Takip edilen ${icon("people")}</span><strong class="stat-value">412</strong><span class="stat-bottom"><span class="stat-change">${icon("chart")} +4 <span>son gözlemde</span></span>${icon("arrow")}</span></button>
      <button class="stat-card white" data-action="profile-events"><span class="stat-label">Profil değişiklikleri ${icon("photo")}</span><strong class="stat-value">3<span class="stat-unit">yeni gözlem</span></strong><span class="stat-bottom"><span class="muted">Küçük değişiklikleri fark et.</span>${icon("arrow")}</span></button></section>
      <div class="overview-columns"><div class="main-column">${chart()}<section class="activity-section"><div class="section-heading"><div><h2>Son değişiklikler</h2><p>Son kayıtlarda dikkat çekenler.</p></div><a class="text-button" href="#timeline">Tümünü gör ${icon("arrow")}</a></div><div class="activity-list">${events
        .slice(0, 3)
        .map((e) => eventRow(e, true))
        .join("")}</div></section></div>
      <aside class="insights-column"><section class="snapshot-card"><div class="snapshot-top"><span class="tiny-icon">${icon("check")}</span><span>SON ÖRNEK GÖZLEM</span></div><h2>Her şey bir kayıttan başlar.</h2><p>Bugün, 16:42</p><div class="snapshot-rule"></div><div class="snapshot-line"><span>Profil bilgisi</span><span class="mini-check">${icon("check")} Kayıtlı</span></div><div class="snapshot-line"><span>Liste kapsamı</span><button class="text-button" data-action="scope">Örneği incele ${icon("chevron")}</button></div><p class="snapshot-footnote">Gördüklerin kayıtlı gözlemlerdir; anlık Instagram verisi değildir.</p></section>
      <section class="favorites-section"><div class="section-heading"><h2>Favorilerin</h2>${iconButton("plus", "Favori kişileri seç", "favorites")}</div>${favoriteRows()}<a class="all-people" href="#people">Bütün kişileri keşfet ${icon("arrow")}</a></section>
      <div class="little-note">${icon("lock")}<p>Kontrol sende.<br><span>Yalnızca anlamlı değişikliklere odaklan.</span></p></div></aside></div>`;
  }
  function filteredPeople() {
    const query = state.peopleSearch.toLocaleLowerCase("tr-TR");
    return state.people
      .filter(
        (p) =>
          (state.peopleFilter !== "favorites" || p.favorite) &&
          (state.peopleFilter !== "watching" || p.watching) &&
          `${p.name} ${p.username} ${p.group}`
            .toLocaleLowerCase("tr-TR")
            .includes(query),
      )
      .sort((a, b) => Number(b.favorite) - Number(a.favorite));
  }
  function personCard(p) {
    return `<article class="person-card card"><div class="person-card-top"><button class="avatar-button" data-action="person" data-id="${p.id}" aria-label="${esc(p.name)} kaydını aç">${avatar(p, true)}</button><button class="icon-button star-button ${p.favorite ? "is-favorite" : ""}" data-action="star" data-id="${p.id}" aria-label="${esc(p.name)} ${p.favorite ? "favorilerden çıkar" : "favorilere ekle"}" aria-pressed="${p.favorite}">${icon("star")}</button></div><button class="person-title" data-action="person" data-id="${p.id}"><h2>${esc(p.name)}</h2><p>@${esc(p.username)}</p></button><p class="person-bio">${esc(p.bio)}</p><div class="person-stats"><span><strong>${number(p.followers)}</strong><small>takipçi</small></span><span><strong>${number(p.following)}</strong><small>takip edilen</small></span></div><div class="person-card-bottom"><span class="group-label">${esc(p.group)}</span><span class="watch-label ${p.watching ? "on" : ""}">${icon(p.watching ? "bell" : "clock")}${p.watching ? "İzleme örneği" : "Kayıtlı"}</span></div></article>`;
  }
  function empty(message, symbol = "search") {
    return `<div class="empty-state">${icon(symbol)}<h2>Burada henüz bir şey yok.</h2><p>${message}</p></div>`;
  }
  function peopleGrid() {
    const people = filteredPeople();
    return people.length
      ? people.map(personCard).join("")
      : empty("Farklı bir arama veya filtre deneyebilirsin.");
  }
  function people() {
    return `${title("KİŞİLERİN", "Çevreni biraz daha yakından tanı.", "Favorilerini bir arada tut, kayıtlı profillerde nelerin değiştiğini gör.", button("Örnek kişi ekle", "add-person", "plus", "primary"))}
      <div class="toolbar"><div class="filter-tabs" role="group" aria-label="Kişi filtresi">${[
        ["all", "Tümü", state.people.length],
        [
          "favorites",
          "Favoriler",
          state.people.filter((p) => p.favorite).length,
        ],
        [
          "watching",
          "İzlenenler",
          state.people.filter((p) => p.watching).length,
        ],
      ]
        .map(
          ([key, label, count]) =>
            `<button data-action="people-filter" data-value="${key}" class="${state.peopleFilter === key ? "active" : ""}" aria-pressed="${state.peopleFilter === key}">${label}<span>${count}</span></button>`,
        )
        .join(
          "",
        )}</div><label class="search-input">${icon("search")}<input id="people-search" type="search" placeholder="İsim, kullanıcı adı veya grup ara" aria-label="Kişilerde ara" value="${esc(state.peopleSearch)}"></label></div>
      <div class="results-line"><span id="people-result-count">${filteredPeople().length} örnek kişi</span><span>Favoriler önce gösterilir</span></div><div class="people-grid" id="people-grid">${peopleGrid()}</div>`;
  }
  function listPeople() {
    const ids = listRecords[state.list].current;
    return ids
      .map(person)
      .filter(Boolean)
      .filter((p) =>
        `${p.name} ${p.username}`
          .toLocaleLowerCase("tr-TR")
          .includes(state.listSearch.toLocaleLowerCase("tr-TR")),
      );
  }
  function listRows() {
    const rows = listPeople();
    return rows.length
      ? rows
          .map(
            (p) =>
              `<div class="list-row"><button class="list-person" data-action="person" data-id="${p.id}">${avatar(p)}<span><strong>${esc(p.name)}</strong><small>@${p.username}</small></span></button><span class="list-followers">${number(p.followers)}<small> takipçi</small></span><span class="list-state">${!listRecords[state.list].previous.includes(p.id) ? '<span class="badge mint">Yeni görüldü</span>' : '<span class="subtle-status">Kayıtlı</span>'}</span><button class="icon-button star-button ${p.favorite ? "is-favorite" : ""}" data-action="star" data-id="${p.id}" aria-label="${esc(p.name)} favori durumunu değiştir" aria-pressed="${p.favorite}">${icon("star")}</button>${iconButton("chevron", `${p.name} ayrıntıları`, "person", `data-id="${p.id}"`)}</div>`,
          )
          .join("")
      : empty("Bu örnek listede aramana uygun hesap bulunamadı.");
  }
  function lists() {
    const record = listRecords[state.list];
    const added = record.current.filter(
      (id) => !record.previous.includes(id),
    ).length;
    const removed = record.previous.filter(
      (id) => !record.current.includes(id),
    ).length;
    return `${title("HESAP LİSTELERİ", "Kimler çevrende?", "Takipçi ve takip edilen kayıtlarını incele, iki gözlem arasındaki farklara bak.", button(state.comparing ? "Listeye dön" : "Gözlemleri karşılaştır", "compare", "compare", state.comparing ? "primary" : "secondary"))}
      <section class="list-account card">${avatar(own)}<div><strong>@benim.demo</strong><span>Örnek hesap · Son kayıt bugün, 16:42</span></div><button class="text-button" data-action="scope">Kaydın kapsamı ${icon("info")}</button></section>
      <div class="toolbar"><div class="filter-tabs" role="group" aria-label="Liste yönü">${[
        ["followers", "Takipçiler", "875"],
        ["following", "Takip edilen", "412"],
      ]
        .map(
          ([key, label, count]) =>
            `<button data-action="list-tab" data-value="${key}" class="${state.list === key ? "active" : ""}" aria-pressed="${state.list === key}">${label}<span>${count}</span></button>`,
        )
        .join(
          "",
        )}</div><label class="search-input">${icon("search")}<input id="list-search" type="search" placeholder="Kayıtlı listede ara" aria-label="Listede ara" value="${esc(state.listSearch)}"></label></div>
      ${state.comparing ? `<section class="comparison-panel"><div><span class="eyebrow">ÖNCEKİ → SON GÖZLEM</span><h2>Listendeki küçük farklar</h2><p>26 Ekim, 16:40 → 27 Ekim, 16:42</p></div><div class="comparison-stat"><strong>+${added}</strong><span>Yeni görülen</span></div><div class="comparison-stat removed"><strong>${removed}</strong><span>Son kayıtta görünmeyen</span></div><p class="comparison-note">${icon("info")} Seçilen yöndeki örnek listeler karşılaştırılıyor. Görünmemek, kesin takipten çıkış anlamına gelmez.</p></section>` : ""}
      <section class="list-card card"><div class="list-table-head"><span>Hesap</span><span>Profil kaydı</span><span>Son gözlem</span><span></span><span></span></div><div id="list-rows">${listRows()}</div><div class="list-table-footer"><span id="list-result-count">${listPeople().length} örnek hesap gösteriliyor</span><span>Arama yeni istek göndermez</span></div></section>`;
  }
  function timeline() {
    const filtered = events.filter(
      (e) => state.eventType === "all" || e.type === state.eventType,
    );
    return `${title("GÖZLEM GEÇMİŞİ", "Değişimlerin izini sür.", "Neyin ne zaman gözlendiğini, hangi kayıtların karşılaştırıldığını gör.", button("Kayıt kapsamı", "scope", "calendar"))}
      <div class="toolbar"><div class="filter-tabs" role="group" aria-label="Geçmiş filtresi">${[
        ["all", "Tüm gözlemler"],
        ["profile", "Profil değişiklikleri"],
        ["lists", "Liste karşılaştırmaları"],
      ]
        .map(
          ([key, label]) =>
            `<button data-action="event-filter" data-value="${key}" class="${state.eventType === key ? "active" : ""}" aria-pressed="${state.eventType === key}">${label}</button>`,
        )
        .join(
          "",
        )}</div><span class="toolbar-note">${filtered.length} örnek kayıt</span></div><div class="timeline-layout"><section class="timeline-main">${[
        "Bugün",
        "Dün",
      ]
        .map((day) => {
          const rows = filtered.filter((e) => e.day === day);
          return rows.length
            ? `<div class="timeline-day"><span class="day-label"><span></span>${day}<small>${day === "Bugün" ? "27 Ekim" : "26 Ekim"}</small></span><div class="timeline-events card">${rows.map((e) => eventRow(e)).join("")}</div></div>`
            : "";
        })
        .join(
          "",
        )}</section><aside class="timeline-explainer"><span class="outline-orbit">${icon("clock")}</span><h2>Bir anı değil,<br>bir gözlemi kaydediyoruz.</h2><p>Buradaki zamanlar kayıtların alındığı anlardır. Değişikliğin Instagram’da gerçekleştiği kesin zamanı göstermez.</p><button class="text-button" data-action="scope">Daha fazla bilgi ${icon("arrow")}</button></aside></div>`;
  }
  function settings() {
    return `${title("SANA GÖRE", "Küçük ayarlar, daha rahat bir deneyim.", "Görünümü düzenle ve bu önizlemenin kapsamını incele.")}
      <div class="settings-layout"><div><section class="settings-section card"><h2>Görünüm</h2><div class="setting-row"><div><strong>Açık ve ferah</strong><p>Telefon, web ve masaüstünde aynı renk paleti.</p></div><span class="theme-swatch" aria-label="Mor, krem ve şeftali renk paleti"><i></i><i></i><i></i></span></div><div class="setting-row"><div><strong>Kompakt görünüm</strong><p>Kartlar arasında daha az boşluk bırak.</p></div><button class="switch ${state.compact ? "on" : ""}" role="switch" aria-checked="${state.compact}" aria-label="Kompakt görünüm" data-action="compact"><span></span></button></div><div class="setting-row"><div><strong>Arayüz dili</strong><p>Bu tasarım önizlemesinin dili.</p></div><span class="badge neutral">Türkçe</span></div></section>
      <section class="settings-section card"><h2>Önizleme alanı</h2><div class="setting-row"><div><strong>Bildirim göstergesi</strong><p>Yalnız örnek bildirimlerin arayüzünü değiştirir.</p></div><button class="switch ${state.notifications ? "on" : ""}" role="switch" aria-checked="${state.notifications}" aria-label="Örnek bildirim göstergesi" data-action="notification-toggle"><span></span></button></div><div class="setting-row"><div><strong>Örnek hesap</strong><p>@benim.demo · Gerçek hesap bağlantısı kurulmadı.</p></div>${button("Bağlantıyı incele", "account", "link")}</div><div class="setting-row"><div><strong>Örnekleri başa al</strong><p>Bu oturumdaki kişi, favori ve not düzenlemelerini sıfırla.</p></div><button class="text-button danger-text" data-action="reset">Örnekleri sıfırla ${icon("refresh")}</button></div></section></div>
      <aside class="privacy-panel"><span class="privacy-large">${icon("shield")}</span><span class="eyebrow">TASARIMIN MERKEZİNDE GİZLİLİK</span><h2>Verilerinle arana<br>bir sunucu koymuyoruz.</h2><p>Bu önizleme hiçbir Instagram hesabına bağlanmaz; bütün kişiler ve sayılar örnektir.</p><p>Örnek notlar yalnız bu açık oturumda tutulur. Yalnız kompakt görünüm tercihi tarayıcıya kaydedilir.</p><div class="privacy-panel-foot">${icon("lock")} Yerel çalışma için tasarlandı.</div></aside></div>`;
  }
  function render() {
    document.body.dataset.density = state.compact ? "compact" : "comfortable";
    document.title = `${pages[state.page][0]} — Tracker`;
    const contents = { overview, people, lists, timeline, settings }[
      state.page
    ]();
    document.getElementById("app").innerHTML =
      `${sidebar()}<div class="workspace">${topbar()}<main id="main" tabindex="-1">${contents}${footer()}</main></div>${mobileNavigation()}`;
  }
  function closeDialog() {
    const dialog = document.querySelector("dialog");
    if (dialog) dialog.close();
  }
  function dialog(content, wide = false, drawer = false) {
    closeDialog();
    const root = document.getElementById("overlay-root");
    root.innerHTML = `<dialog class="${drawer ? "drawer" : "modal"} ${wide ? "wide-modal" : ""}" aria-labelledby="dialog-title"><button class="dialog-close icon-button" data-action="close" aria-label="Kapat">${icon("close")}</button>${content}</dialog>`;
    const node = root.querySelector("dialog");
    node.addEventListener("click", (e) => {
      if (e.target === node) {
        const r = node.getBoundingClientRect();
        if (
          e.clientX < r.left ||
          e.clientX > r.right ||
          e.clientY < r.top ||
          e.clientY > r.bottom
        )
          node.close();
      }
    });
    node.showModal();
  }
  function personDialog(id) {
    const p = person(id);
    if (!p) return;
    dialog(
      `<div class="drawer-heading">${avatar(p, true)}<span class="badge neutral">Örnek profil kaydı</span></div><h2 id="dialog-title" class="profile-title">${esc(p.name)}</h2><p class="profile-handle">@${esc(p.username)}</p><p class="drawer-bio">${esc(p.bio)}</p><div class="drawer-counts"><div><strong>${number(p.followers)}</strong><span>takipçi</span></div><div><strong>${number(p.following)}</strong><span>takip edilen</span></div></div><div class="drawer-actions">${button(p.favorite ? "Favorilerinde" : "Favorilere ekle", "drawer-star", "star", p.favorite ? "primary" : "secondary", `data-id="${p.id}"`)}${button("Gözlem kapsamı", "scope", "info")}</div><section class="drawer-section"><h3>Son kontrol durumu</h3><div class="detail-pair"><span>Son profil gözlemi</span><strong>Bugün, 16:42</strong></div><div class="detail-pair"><span>Veri kapsamı</span><strong>Örnek profil ve liste</strong></div><div class="detail-pair"><span>Şu anki durum</span><span class="badge mint">Örnek kayıt</span></div></section><section class="drawer-section"><div class="section-heading"><h3>Sana özel not</h3>${icon("note")}</div><label class="visually-hidden" for="person-note">${esc(p.name)} için örnek not</label><textarea id="person-note" maxlength="500" rows="4" placeholder="Bu örnek kişiyle ilgili bir not bırak…">${esc(p.note || "")}</textarea><p class="field-hint">Yalnız bu önizleme oturumunda tutulur.</p>${button("Notu kaydet", "save-note", "check", "secondary", `data-id="${p.id}"`)}</section>`,
      false,
      true,
    );
  }
  function eventDialog(id) {
    const e = events.find((e) => e.id === id);
    if (!e) return;
    dialog(
      `<span class="dialog-symbol ${e.tone}">${icon(e.icon)}</span><span class="eyebrow">${e.day.toLocaleUpperCase("tr-TR")} · ${e.time}</span><h2 id="dialog-title">${e.title}</h2><p>${e.text}</p><div class="change-values"><div><span>Önceki kayıt</span><strong>${esc(e.old)}</strong></div>${icon("arrow")}<div><span>Son kayıt</span><strong>${esc(e.next)}</strong></div></div><p class="dialog-note">${icon("info")}${e.scope}</p>${button("Anladım", "close", null, "primary")}`,
      true,
    );
  }
  function scopeDialog() {
    dialog(
      `<span class="dialog-symbol lavender">${icon("info")}</span><span class="eyebrow">ARAYÜZ ÖNİZLEMESİ</span><h2 id="dialog-title">Gördüğün veriler birer örnek.</h2><p>Bu aşamada ekranları, gezinmeyi ve görsel düzeni inceliyorsun. Hesap girişi, Instagram’dan veri okuma ve gerçek bildirimler henüz bağlı değil.</p><div class="scope-points"><div>${icon("people")}<span>Hesaplar ve sayılar tamamen örnektir.</span></div><div>${icon("compare")}<span>Liste farkları kesin takipten çıkış olarak sunulmaz.</span></div><div>${icon("clock")}<span>Kayıt zamanı, değişimin kesin zamanı değildir.</span></div><div>${icon("lock")}<span>Bu arayüz mevcut telefon veya bilgisayar oturumunu okumaz.</span></div></div>${button("Arayüzü incelemeye devam et", "close", "arrow", "primary")}`,
      true,
    );
  }
  function accountDialog() {
    dialog(
      `<span class="dialog-symbol lavender">${icon("link")}</span><span class="eyebrow">HESAP BAĞLANTISI</span><h2 id="dialog-title">Önce görünüm, sonra bağlantı.</h2><p>Bu örnek hesap, arayüzü güvenle keşfetmen için burada. Gerçek kullanıcı adı veya parola girmen gerekmiyor.</p><div class="account-preview-row">${avatar(own)}<span><strong>@benim.demo</strong><small>Örnek hesap · 875 takipçi · 412 takip edilen</small></span><span class="badge neutral">Önizleme</span></div><p class="dialog-note">${icon("lock")}Instagram bağlantısı ve yerel veri işleme, arayüz aşamasından sonra eklenecek.</p>${button("Tamam", "close", null, "primary")}`,
      true,
    );
  }
  function navigate(page) {
    if (!(page in pages)) return;
    closeDialog();
    if (location.hash !== `#${page}`) location.hash = page;
    else {
      state.page = page;
      render();
    }
  }
  document.addEventListener("click", (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    const action = target.dataset.action,
      id = target.dataset.id,
      value = target.dataset.value;
    switch (action) {
      case "close":
        closeDialog();
        break;
      case "person":
        personDialog(id);
        break;
      case "event":
        eventDialog(id);
        break;
      case "privacy":
        navigate("settings");
        break;
      case "scope":
        scopeDialog();
        break;
      case "account":
        accountDialog();
        break;
      case "refresh":
        dialog(
          `<span class="dialog-symbol lavender">${icon("refresh")}</span><h2 id="dialog-title">Canlı yenileme henüz etkin değil.</h2><p>Bu aşama yalnız arayüz önizlemesi. Instagram’a istek gönderilmedi; bir tarama başlatılmadı.</p>${button("Örnek kayıtları incele", "close", null, "primary")}`,
        );
        break;
      case "open-list":
        state.list = value;
        state.comparing = false;
        navigate("lists");
        break;
      case "profile-events":
        state.eventType = "profile";
        navigate("timeline");
        break;
      case "range":
        state.range = value;
        render();
        break;
      case "people-filter":
        state.peopleFilter = value;
        render();
        break;
      case "favorites":
        state.peopleFilter = "favorites";
        state.peopleSearch = "";
        navigate("people");
        break;
      case "list-tab":
        state.list = value;
        state.listSearch = "";
        render();
        break;
      case "compare":
        state.comparing = !state.comparing;
        render();
        break;
      case "event-filter":
        state.eventType = value;
        render();
        break;
      case "star": {
        const p = person(id);
        if (p && p !== own) {
          p.favorite = !p.favorite;
          render();
          toast(
            p.favorite
              ? "Örnek kişi favorilerine eklendi."
              : "Örnek kişi favorilerinden çıkarıldı.",
          );
        }
        break;
      }
      case "drawer-star": {
        const p = person(id);
        if (p && p !== own) {
          p.favorite = !p.favorite;
          render();
          personDialog(id);
        }
        break;
      }
      case "save-note": {
        const p = person(id);
        if (p) {
          p.note = document.getElementById("person-note").value;
          toast("Örnek not bu oturum için kaydedildi.");
        }
        break;
      }
      case "compact":
        state.compact = !state.compact;
        try {
          localStorage.setItem("tracker-ui-compact", String(state.compact));
        } catch {}
        render();
        break;
      case "notification-toggle":
        state.notifications = !state.notifications;
        render();
        break;
      case "notifications":
        dialog(
          `<span class="dialog-symbol lavender">${icon("bell")}</span><h2 id="dialog-title">Küçük değişiklikler, tek yerde.</h2><p>Bildirim görünümü · Bütün kayıtlar örnektir.</p>${
            state.notifications
              ? `<div class="notification-preview">${events
                  .slice(0, 3)
                  .map((e) => eventRow(e, true))
                  .join("")}</div>`
              : '<p class="dialog-note">Örnek bildirim göstergesi ayarlardan kapatıldı.</p>'
          }`,
          true,
        );
        break;
      case "search":
        state.peopleFilter = "all";
        navigate("people");
        setTimeout(() => document.getElementById("people-search")?.focus(), 20);
        break;
      case "history":
        dialog(
          `<span class="dialog-symbol lavender">${icon("chart")}</span><h2 id="dialog-title">Örnek gözlem kayıtları</h2><p>Gerçek hesap verisi değildir. Grafikteki sayılar bu kayıtlardan oluşur.</p><div class="history-records">${history
            .map(
              (count, index) =>
                `<div><span>${16 + index} Ekim 2026</span><strong>${number(count)} takipçi</strong></div>`,
            )
            .reverse()
            .join("")}</div>`,
          true,
        );
        break;
      case "add-person":
        dialog(
          `<span class="dialog-symbol lavender">${icon("plus")}</span><h2 id="dialog-title">Çevrene bir örnek kişi ekle.</h2><p>Bu işlem Instagram’da arama yapmaz. Yalnız arayüzde bir örnek kart oluşturur.</p><form id="add-person-form"><label class="form-label" for="sample-name">Görünen ad</label><input id="sample-name" name="name" required maxlength="60" placeholder="Örn. İpek Kaya" autocomplete="off"><label class="form-label" for="sample-username">Örnek kullanıcı adı</label><input id="sample-username" name="username" required maxlength="30" pattern="[A-Za-z0-9._]{1,30}" placeholder="ipek.demo" autocomplete="off"><label class="form-label" for="sample-group">Grup</label><select id="sample-group" name="group"><option>Arkadaşlar</option><option>İçerik üreticileri</option><option>Diğer</option></select><button class="button primary full-width" type="submit">${icon("plus")}Örnek kartı ekle</button></form>`,
        );
        break;
      case "reset":
        dialog(
          `<span class="dialog-symbol peach">${icon("refresh")}</span><h2 id="dialog-title">Örnekleri başa alalım mı?</h2><p>Bu oturumdaki örnek kişi, favori ve not düzenlemelerin sıfırlanır. Gerçek hesap veya uygulama verilerine dokunulmaz.</p><div class="dialog-buttons">${button("Vazgeç", "close")}${button("Örnekleri sıfırla", "reset-confirm", "refresh", "primary")}</div>`,
        );
        break;
      case "reset-confirm":
        state.people = structuredClone(original);
        state.peopleSearch = "";
        state.peopleFilter = "all";
        closeDialog();
        render();
        toast("Örnek veriler başlangıç durumuna döndü.");
        break;
    }
  });
  document.addEventListener("input", (event) => {
    if (event.target.id === "people-search") {
      state.peopleSearch = event.target.value;
      document.getElementById("people-grid").innerHTML = peopleGrid();
      document.getElementById("people-result-count").textContent =
        `${filteredPeople().length} örnek kişi`;
    }
    if (event.target.id === "list-search") {
      state.listSearch = event.target.value;
      document.getElementById("list-rows").innerHTML = listRows();
      document.getElementById("list-result-count").textContent =
        `${listPeople().length} örnek hesap gösteriliyor`;
    }
  });
  document.addEventListener("submit", (event) => {
    if (event.target.id !== "add-person-form") return;
    event.preventDefault();
    const data = new FormData(event.target),
      username = String(data.get("username")).trim();
    if (
      state.people.some(
        (p) => p.username.toLowerCase() === username.toLowerCase(),
      )
    ) {
      toast("Bu örnek kullanıcı adı zaten listede.");
      return;
    }
    state.people.push({
      id: String(1000 + state.people.length),
      name: String(data.get("name")).trim(),
      username,
      followers: 1200,
      following: 340,
      tone: "lavender",
      favorite: false,
      watching: false,
      group: String(data.get("group")),
      bio: "Yeni bir örnek profil.",
      note: "",
    });
    state.peopleFilter = "all";
    state.peopleSearch = "";
    closeDialog();
    render();
    toast("Örnek kişi kartı eklendi.");
  });
  document.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      closeDialog();
      state.peopleFilter = "all";
      navigate("people");
      setTimeout(() => document.getElementById("people-search")?.focus(), 20);
    }
  });
  for (const type of ["pointerover", "focusin"])
    document.addEventListener(type, (event) => {
      const point = event.target.closest("[data-point]");
      if (point) {
        const caption = document.getElementById("chart-caption");
        if (caption) caption.textContent = point.dataset.point;
      }
    });
  window.addEventListener("hashchange", () => {
    state.page = Object.hasOwn(pages, location.hash.slice(1))
      ? location.hash.slice(1)
      : "overview";
    closeDialog();
    render();
    window.scrollTo(0, 0);
  });
  state.page = Object.hasOwn(pages, location.hash.slice(1))
    ? location.hash.slice(1)
    : "overview";
  render();
})();
