(() => {
  "use strict";
  const api = window.TrackerDesktop,
    icon = window.TrackerIcon;
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
  const num = (value) =>
    typeof value === "number"
      ? new Intl.NumberFormat("tr-TR").format(value)
      : "—";
  const date = (value) =>
    value
      ? new Date(value * 1000).toLocaleString("tr-TR", {
          dateStyle: "medium",
          timeStyle: "short",
        })
      : "Henüz yok";
  const labels = {
    username: "Kullanıcı adı",
    name: "Görünen ad",
    bio: "Biyografi",
    url: "Bağlantı",
    private: "Gizli hesap",
    verified: "Doğrulanmış hesap",
    followers: "Takipçi",
    following: "Takip edilen",
    posts: "Gönderi",
    picture: "Profil fotoğrafı bilgisi",
  };
  const pages = {
    overview: ["Özet", "home"],
    people: ["Kişiler", "people"],
    lists: ["Listeler", "list"],
    timeline: ["Zaman çizelgesi", "clock"],
    settings: ["Ayarlar", "settings"],
  };
  let data = null,
    busy = false,
    progress = null,
    serviceError = null,
    drawerId = null,
    toastTimer;
  const ui = {
    page: "overview",
    query: "",
    filter: "all",
    owner: "",
    direction: "followers",
    listQuery: "",
    listFilter: "all",
    pageSize: 100,
    comparison: false,
    previous: null,
    current: null,
    eventType: "all",
    eventLimit: 100,
    range: "30",
  };
  function toast(message) {
    const node = document.getElementById("toast");
    node.textContent = message;
    node.classList.add("visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove("visible"), 5000);
  }
  const button = (text, action, symbol, kind = "secondary", extra = "") =>
    `<button type="button" class="button ${kind}" data-action="${action}" ${extra}>${symbol ? icon(symbol) : ""}<span>${esc(text)}</span></button>`;
  const idattr = (id) => `data-id="${esc(id)}"`;
  const netDisabled = () => (busy || !data?.connected ? "disabled" : "");
  function person(id) {
    if (data?.account?.id === id) return data.account;
    const p = data?.people.find((p) => p.id === id);
    if (p) return p;
    for (const record of Object.values(data?.lists || {})) {
      for (const scan of [...record.snapshots]
        .reverse()
        .concat(record.draft || []))
        if (scan.members[id]) return scan.members[id];
    }
    return null;
  }
  function avatar(p, large = false) {
    if (
      typeof p?.avatar === "string" &&
      p.avatar.length <= 100000 &&
      /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(p.avatar)
    ) {
      return `<span class="avatar ${large ? "large" : ""}" aria-hidden="true"><img src="${esc(p.avatar)}" alt=""></span>`;
    }
    const name = p?.name || p?.username || "?";
    return `<span class="avatar lavender ${large ? "large" : ""}" aria-hidden="true">${esc(
      name
        .split(" ")
        .filter(Boolean)
        .map((s) => s[0])
        .slice(0, 2)
        .join(""),
    )}</span>`;
  }
  const title = (kicker, heading, text, action = "") =>
    `<div class="page-heading"><div><span class="eyebrow">${esc(kicker)}</span><h1>${esc(heading)}</h1><p>${esc(text)}</p></div>${action}</div>`;
  const empty = (text, action = "") =>
    `<div class="empty-state">${icon("info")}<h2>Burada henüz bir kayıt yok.</h2><p>${esc(text)}</p>${action}</div>`;
  function navigate(page) {
    if (location.hash === "#" + page) {
      ui.page = page;
      render();
    } else location.hash = page;
  }
  function closeDialog() {
    document.querySelector("dialog")?.close();
    drawerId = null;
  }
  function dialog(content, wide = false, drawer = false) {
    closeDialog();
    document.getElementById("overlay-root").innerHTML =
      `<dialog class="${drawer ? "drawer" : "modal"} ${wide ? "wide-modal" : ""}" aria-labelledby="dialog-title"><button class="dialog-close icon-button" data-action="close" aria-label="Kapat">${icon("close")}</button>${content}</dialog>`;
    document.querySelector("dialog").showModal();
  }
  function errorDialog(message) {
    dialog(
      `<span class="dialog-symbol peach">${icon("info")}</span><h2 id="dialog-title">İşlem tamamlanamadı</h2><p>${esc(message)}</p>${data?.gate.blockedUntil > Date.now() / 1000 ? `<p class="dialog-note">Yerel mola: ${esc(date(data.gate.blockedUntil))}. Bu saat Instagram’ın erişim garantisi değildir.</p>` : ""}<div class="dialog-buttons">${button("İstek bilgileri", "requests", "info")}${button("Tamam", "close", null, "primary")}</div>`,
    );
  }
  async function command(operation, args = {}, success = "") {
    if (busy && !["cancel", "code", "state"].includes(operation)) {
      toast("Önce devam eden işlemi bitir veya iptal et.");
      return null;
    }
    const control = ["cancel", "code", "state"].includes(operation);
    if (!control) {
      busy = true;
      progress = { phase: "preparing" };
      render();
    }
    try {
      const result = await api.command(operation, args);
      if (result.state) data = result.state;
      if (!control) {
        busy = false;
        progress = null;
        render();
      }
      if (!result.ok)
        errorDialog(result.error?.message || "İşlem tamamlanamadı.");
      else if (success) toast(success);
      return result;
    } catch {
      if (!control) {
        busy = false;
        progress = null;
        render();
      }
      errorDialog("Yerel hizmete ulaşılamadı. Uygulamayı yeniden açabilirsin.");
      return null;
    }
  }
  async function file(action, args = {}) {
    try {
      const result = await api.file(action, args);
      if (result.state) {
        data = result.state;
        render();
      }
      if (!result.ok)
        errorDialog(result.error?.message || "Dosya işlemi tamamlanamadı.");
      else if (!result.cancelled && action !== "open_storage")
        toast("Dosya işlemi tamamlandı.");
    } catch {
      errorDialog("Dosya okunamadı veya yazılamadı. Mevcut kayıtlar korundu.");
    }
  }
  function sidebar() {
    return `<aside class="sidebar"><a class="brand" href="#overview"><span class="brand-mark">${icon("chart")}</span><span><strong>Tracker</strong><small>Çevren, tek bakışta.</small></span></a><span class="nav-label">ÇALIŞMA ALANIN</span><nav aria-label="Ana gezinme">${Object.entries(
      pages,
    )
      .map(
        ([key, [label, symbol]]) =>
          `<a class="nav-item ${ui.page === key ? "selected" : ""}" href="#${key}" ${ui.page === key ? 'aria-current="page"' : ""}>${icon(symbol)}<span>${label}</span>${key === "people" ? `<span class="nav-count">${data.people.length}</span>` : ""}</a>`,
      )
      .join(
        "",
      )}</nav><div class="sidebar-bottom"><div class="privacy-note">${icon("shield")}<strong>Çevren sana özel.</strong><p>Oturum ve gözlemler bu Windows kullanıcısına bağlı şifrelenir.</p><button class="text-button" data-action="privacy">Gizliliği incele ${icon("arrow")}</button></div><div class="preview-label"><span class="status-dot"></span> Yerel masaüstü <span>0.2</span></div></div></aside>`;
  }
  function topbar() {
    const own = data.account;
    return `<header class="topbar"><div class="breadcrumbs"><span>Çalışma alanım</span>${icon("chevron")}<strong>${pages[ui.page][0]}</strong></div><button class="global-search" data-action="search">${icon("search")}<span>Kişi ara</span><kbd>Ctrl K</kbd></button><div class="top-actions"><span class="sample-pill">${data.connected ? "Yerel oturum" : "Hesap bağlı değil"}</span><button class="icon-button" data-action="notifications" aria-label="Son gözlemler">${icon("bell")}</button><button class="account-button" data-action="account" aria-label="Hesap bağlantısı">${avatar(own)}<span><strong>${own ? "@" + esc(own.username) : "Hesabını bağla"}</strong><small>${data.connected ? "Şifreli oturum" : "Bağlantı bekleniyor"}</small></span>${icon("down")}</button></div></header>`;
  }
  function operationPanel() {
    const root = document.getElementById("operation-root");
    if (!busy) {
      root.innerHTML = "";
      return;
    }
    const messages = {
      preparing: "İşlem hazırlanıyor…",
      login: "Instagram hesabına bağlanılıyor…",
      profile: "Profil okunuyor…",
      avatar: "Profil fotoğrafı yükleniyor…",
      request: "Instagram yanıtı bekleniyor…",
      waiting: "İstekler arasındaki yerel aralık bekleniyor…",
      list: "Liste sayfaları okunuyor…",
      code: "Doğrulama kodu bekleniyor…",
    };
    root.innerHTML = `<div class="operation-panel"><span class="working-dot"></span><div><strong>${esc(messages[progress?.phase] || "İşlem sürüyor…")}</strong><small>${progress?.phase === "list" ? `${num(progress.count)} hesap · Sayfa ${num(progress.page)}` : progress?.phase === "waiting" ? `Sonraki istek: ${esc(date(progress.until))}` : "İşlem bitene kadar diğer ağ işlemleri bekler."}</small></div>${progress?.phase === "code" ? button("Kodu gir", "enter-code", "lock") : ""}${button("İptal et", "cancel", "close")}</div>`;
  }
  function chart(p) {
    const all = data.history[p?.id] || [];
    const cutoff =
      ui.range === "all" ? 0 : Date.now() / 1000 - Number(ui.range) * 86400;
    const history = all.filter(
      (h) => h.observedAt >= cutoff && typeof h.followers === "number",
    );
    const controls = `<div class="segmented" role="group" aria-label="Grafik aralığı">${[
      ["7", "7 gün"],
      ["30", "30 gün"],
      ["all", "Tümü"],
    ]
      .map(
        ([k, l]) =>
          `<button data-action="range" data-value="${k}" class="${ui.range === k ? "active" : ""}" aria-pressed="${ui.range === k}">${l}</button>`,
      )
      .join("")}</div>`;
    let graph = empty("Profil gözlemleri geldikçe grafik burada oluşur.");
    if (history.length) {
      const values = history.map((h) => h.followers),
        min = Math.max(0, Math.min(...values) - 5),
        max = Math.max(...values) + 5;
      const start = history[0].observedAt,
        end = history.at(-1).observedAt;
      const points = history.map((h) => [
        history.length === 1
          ? 360
          : 50 + (620 * (h.observedAt - start)) / Math.max(1, end - start),
        210 - (170 * (h.followers - min)) / (max - min),
      ]);
      const line = points
        .map(([x, y], i) => `${i ? "L" : "M"}${x.toFixed(2)} ${y.toFixed(2)}`)
        .join(" ");
      graph = `<div class="chart-summary"><strong>${num(values.at(-1))}<span>takipçi</span></strong><span class="muted">${history.length} kayıt</span></div><div class="chart-wrap"><svg class="history-chart" viewBox="0 0 710 252" role="img" aria-label="Kayıtlı takipçi sayıları">${[0, 1, 2, 3].map((i) => `<line x1="50" y1="${40 + (i * 170) / 3}" x2="680" y2="${40 + (i * 170) / 3}" class="grid-line"/><text x="34" y="${44 + (i * 170) / 3}" text-anchor="end" class="axis-label">${num(Math.round(max - (i * (max - min)) / 3))}</text>`).join("")}<path d="${line}" class="chart-line"/>${points.map(([x, y], i) => `<circle cx="${x}" cy="${y}" r="5" class="chart-point" tabindex="0"><title>${esc(date(history[i].observedAt))} · ${num(values[i])}</title></circle>`).join("")}<text x="50" y="242" class="axis-label">${esc(new Date(start * 1000).toLocaleDateString("tr-TR"))}</text><text x="670" y="242" text-anchor="end" class="axis-label">${esc(new Date(end * 1000).toLocaleDateString("tr-TR"))}</text></svg></div>`;
    }
    return `<section class="chart-card card"><div class="section-heading"><div><h2>Takipçi geçmişi</h2><p>Yalnız kaydedilmiş gözlemler.</p></div>${controls}</div>${graph}<div class="chart-caption"><span>Noktalar arasındaki hareketler bilinmiyor.</span>${button("Kayıtları gör", "history", "clock", "secondary", idattr(p?.id || ""))}</div></section>`;
  }
  function eventTitle(e) {
    if (e.type === "profile")
      return `@${person(e.personId)?.username || e.personId} · ${labels[Object.keys(e.new || {})[0]] || "Profil değişikliği"}`;
    if (e.type === "list")
      return `${e.new?.direction === "following" ? "Takip edilen" : "Takipçi"} listesi kaydedildi`;
    return e.title;
  }
  function eventRow(e) {
    return `<button class="event-row" data-action="event" ${idattr(e.id)}><span class="event-icon ${e.type === "error" ? "peach" : "lavender"}">${icon(e.type === "list" ? "list" : e.type === "error" ? "info" : "clock")}</span><span class="event-copy"><strong>${esc(eventTitle(e))}</strong><small>${esc(date(e.at))}</small></span>${icon("chevron")}</button>`;
  }
  function overview() {
    const own = data.account,
      history = data.history[own?.id] || [],
      previous = history.at(-2);
    const stat = (label, key, tone) =>
      `<button class="stat-card ${tone}" data-action="open-list" data-value="${key}"><span class="stat-label">${label} ${icon("people")}</span><strong class="stat-value">${num(own?.[key])}</strong><span class="stat-bottom"><span>${previous && own ? `${own[key] - previous[key] > 0 ? "+" : ""}${num(own[key] - previous[key])} son gözleme göre` : "İlk kayıt bir başlangıçtır."}</span>${icon("arrow")}</span></button>`;
    const changes = data.events.filter((e) => e.type === "profile").length;
    return `${title(new Date().toLocaleDateString("tr-TR", { dateStyle: "full" }), "Çevrende neler oluyor?", "Hesabın, favori kişilerin ve son gözlemlerin bir arada.", button(data.connected ? "Gözlemi yenile" : "Hesabını bağla", data.connected ? "refresh" : "account", "refresh", "primary", data.connected ? netDisabled() : ""))}
      ${!own ? `<section class="connect-banner card"><span class="dialog-symbol lavender">${icon("link")}</span><div><h2>Önce hesabınla bağlantı kuralım.</h2><p>Şifren kaydedilmez. Oturum ve gözlemler şifreli olarak bilgisayarında kalır.</p></div>${button("Hesabını bağla", "account", "arrow", "primary")}</section>` : ""}
      <section class="stats-grid" aria-label="Hesap özeti">${stat("Takipçiler", "followers", "violet")}${stat("Takip edilen", "following", "peach")}<button class="stat-card white" data-action="profile-events"><span class="stat-label">Profil değişiklikleri ${icon("photo")}</span><strong class="stat-value">${num(changes)}<span class="stat-unit">kayıtlı fark</span></strong><span class="stat-bottom"><span class="muted">Gözlemler arasındaki değişimler.</span>${icon("arrow")}</span></button></section>
      <div class="overview-columns"><div class="main-column">${chart(own)}<section class="activity-section"><div class="section-heading"><h2>Son gözlemler</h2><a class="text-button" href="#timeline">Tümünü gör ${icon("arrow")}</a></div><div class="activity-list">${data.events.slice(0, 5).map(eventRow).join("") || empty("İlk kontrolünden sonra kayıtlar burada görünür.")}</div></section></div><aside class="insights-column"><section class="snapshot-card"><div class="snapshot-top"><span class="tiny-icon">${icon("clock")}</span><span>SON PROFİL GÖZLEMİ</span></div><h2>${own ? "@" + esc(own.username) : "Henüz bağlantı yok"}</h2><p>${esc(date(own?.observedAt))}</p><div class="snapshot-rule"></div><p>Profil sayıları ve listeler ayrı ayrı kontrol edilir. Bir profil gözlemi, tam liste taraması değildir.</p>${button("İstek bilgileri", "requests", "info")}</section><section class="favorites-section"><div class="section-heading"><h2>Favorilerin</h2><a href="#people" class="text-button">Kişiler ${icon("arrow")}</a></div>${
        data.people
          .filter((p) => p.favorite)
          .slice(0, 5)
          .map(
            (p) =>
              `<button class="person-row" data-action="person" ${idattr(p.id)}>${avatar(p)}<span class="person-ident"><strong>${esc(p.name || p.username)}</strong><small>@${esc(p.username)}</small></span><span class="row-number">${num(p.followers)}</span>${icon("chevron")}</button>`,
          )
          .join("") ||
        "<p>Kişiler bölümünde yıldızladığın hesaplar burada görünür.</p>"
      }</section></aside></div>`;
  }
  function filteredPeople() {
    const q = ui.query.toLocaleLowerCase("tr-TR");
    return data.people
      .filter(
        (p) =>
          (ui.filter !== "favorites" || p.favorite) &&
          (ui.filter !== "watching" || p.watching) &&
          `${p.name || ""} ${p.username} ${p.group || ""}`
            .toLocaleLowerCase("tr-TR")
            .includes(q),
      )
      .sort((a, b) => Number(!!b.favorite) - Number(!!a.favorite));
  }
  function personCard(p) {
    return `<article class="person-card card"><div class="person-card-top"><button class="avatar-button" data-action="person" ${idattr(p.id)} aria-label="${esc(p.name || p.username)} kaydını aç">${avatar(p, true)}</button><button class="icon-button star-button ${p.favorite ? "is-favorite" : ""}" data-action="star" ${idattr(p.id)} aria-label="${esc(p.username)} favori durumunu değiştir" aria-pressed="${!!p.favorite}" ${busy ? "disabled" : ""}>${icon("star")}</button></div><button class="person-title" data-action="person" ${idattr(p.id)}><h2>${esc(p.name || p.username)}</h2><p>@${esc(p.username)}</p></button><p class="person-bio">${esc(p.bio || "Henüz biyografi gözlemi yok.")}</p><div class="person-stats"><span><strong>${num(p.followers)}</strong><small>takipçi</small></span><span><strong>${num(p.following)}</strong><small>takip edilen</small></span></div><div class="person-card-bottom"><span class="group-label">${esc(p.group || "Grupsuz")}</span><span class="watch-label ${p.watching ? "on" : ""}">${icon(p.watching ? "bell" : "clock")}${p.watching ? "İzlenen" : "Kayıtlı"}</span></div></article>`;
  }
  function people() {
    return `${title("KİŞİLERİN", "Çevreni biraz daha yakından tanı.", "Profilleri kaydet, favorilerini ve özel notlarını düzenle.", button("Kişi ekle", "add-person", "plus", "primary", netDisabled()))}<div class="toolbar"><div class="filter-tabs" role="group" aria-label="Kişi filtresi">${[
      ["all", "Tümü"],
      ["favorites", "Favoriler"],
      ["watching", "İzlenenler"],
    ]
      .map(
        ([k, l]) =>
          `<button data-action="people-filter" data-value="${k}" class="${ui.filter === k ? "active" : ""}" aria-pressed="${ui.filter === k}">${l}</button>`,
      )
      .join(
        "",
      )}</div><label class="search-input">${icon("search")}<input id="people-search" type="search" placeholder="İsim, kullanıcı adı veya grup ara" aria-label="Kişilerde ara" value="${esc(ui.query)}"></label></div><div class="results-line"><span id="people-result-count">${filteredPeople().length} kişi</span><span>Favoriler önce gösterilir</span></div><div class="people-grid" id="people-grid">${filteredPeople().map(personCard).join("") || empty("Kişi ekle veya taradığın listeden bir hesabı kaydet.")}</div>`;
  }
  function selectedRecord() {
    return (
      data.lists[`${ui.owner || data.account?.id}:${ui.direction}`] || {
        snapshots: [],
      }
    );
  }
  function selectedScans() {
    const scans = selectedRecord().snapshots;
    const current = scans.find((s) => s.id === ui.current) || scans.at(-1);
    const previous = scans.find((s) => s.id === ui.previous) || scans.at(-2);
    return {
      scans,
      current,
      previous,
      comparable:
        !!current &&
        !!previous &&
        scans.indexOf(previous) < scans.indexOf(current),
    };
  }
  function listEntries() {
    const { current, previous, comparable } = selectedScans(),
      record = selectedRecord();
    let entries = Object.values(
      current?.members || record.draft?.members || {},
    ).map((p) => ({
      ...p,
      change: previous && !previous.members[p.id] ? "Yeni görüldü" : "",
    }));
    if (ui.comparison && comparable) {
      entries = [
        ...Object.values(current.members)
          .filter((p) => !previous.members[p.id])
          .map((p) => ({ ...p, change: "Yeni görüldü" })),
        ...Object.values(previous.members)
          .filter((p) => !current.members[p.id])
          .map((p) => ({ ...p, change: "Son kayıtta yok" })),
      ];
    }
    const other =
      data.lists[
        `${ui.owner || data.account?.id}:${ui.direction === "followers" ? "following" : "followers"}`
      ]?.snapshots.at(-1);
    if (ui.listFilter !== "all")
      entries = entries.filter((p) =>
        other?.members[p.id]
          ? ui.listFilter === "mutual"
          : ui.listFilter === "oneway",
      );
    const q = ui.listQuery.toLocaleLowerCase("tr-TR");
    return entries.filter((p) =>
      `${p.name || ""} ${p.username}`.toLocaleLowerCase("tr-TR").includes(q),
    );
  }
  function listRows() {
    const entries = listEntries();
    return entries.length
      ? entries
          .slice(0, ui.pageSize)
          .map((p) => {
            const saved = data.people.find((x) => x.id === p.id);
            return `<div class="list-row"><button class="list-person" data-action="person" ${idattr(p.id)}>${avatar(p)}<span><strong>${esc(p.name || p.username)}</strong><small>@${esc(p.username)}</small></span></button><span class="list-followers">${num(saved?.followers)}<small> takipçi</small></span><span class="list-state">${p.change ? `<span class="badge ${p.change === "Son kayıtta yok" ? "peach" : "mint"}">${p.change}</span>` : '<span class="subtle-status">Kayıtlı</span>'}</span><button class="icon-button star-button ${saved?.favorite ? "is-favorite" : ""}" data-action="star" ${idattr(p.id)} aria-label="${esc(p.username)} favori durumunu değiştir" aria-pressed="${!!saved?.favorite}" ${busy ? "disabled" : ""}>${icon("star")}</button></div>`;
          })
          .join("")
      : empty("Bir liste taraması başlat veya arama ve filtrelerini değiştir.");
  }
  function listPagination() {
    return listEntries().length > ui.pageSize
      ? button("Daha fazla göster", "more-list", "down")
      : "";
  }
  function lists() {
    const owner = ui.owner || data.account?.id || "",
      { scans, current, previous } = selectedScans(),
      record = selectedRecord();
    const ready =
      !!current &&
      !!previous &&
      scans.indexOf(previous) < scans.indexOf(current);
    const added = ready
      ? Object.keys(current.members).filter((id) => !previous.members[id])
          .length
      : 0;
    const removed = ready
      ? Object.keys(previous.members).filter((id) => !current.members[id])
          .length
      : 0;
    const other =
      data.lists[
        `${owner}:${ui.direction === "followers" ? "following" : "followers"}`
      ]?.snapshots.at(-1);
    if (
      ui.listFilter !== "all" &&
      (!current || !other || current !== scans.at(-1))
    )
      ui.listFilter = "all";
    const scanOptions = (selected) =>
      scans
        .map(
          (s) =>
            `<option value="${esc(s.id)}" ${s.id === selected?.id ? "selected" : ""}>${esc(date(s.finishedAt))} · ${num(Object.keys(s.members).length)} hesap</option>`,
        )
        .join("");
    return `${title("HESAP LİSTELERİ", "Kimler çevrende?", "Liste taramaları gözlemdir; kayıtlar arasındaki farklar kesin olay zamanını göstermez.", button("Listeyi tara", "scan", "refresh", "primary", netDisabled()))}
      <section class="list-account card"><label class="form-label" for="list-owner">Liste sahibi</label><select id="list-owner" aria-label="Liste sahibi">${[
        data.account,
        ...data.people,
      ]
        .filter(Boolean)
        .map(
          (p) =>
            `<option value="${esc(p.id)}" ${p.id === owner ? "selected" : ""}>@${esc(p.username)}</option>`,
        )
        .join(
          "",
        )}</select>${button("CSV dışa aktar", "export-list", "note", "secondary", !current ? "disabled" : "")}</section>
      <div class="toolbar"><div class="filter-tabs" role="group" aria-label="Liste yönü">${[
        ["followers", "Takipçiler"],
        ["following", "Takip edilen"],
      ]
        .map(
          ([k, l]) =>
            `<button data-action="list-tab" data-value="${k}" class="${ui.direction === k ? "active" : ""}" aria-pressed="${ui.direction === k}">${l}</button>`,
        )
        .join(
          "",
        )}</div><label class="search-input">${icon("search")}<input id="list-search" type="search" placeholder="Listede ara" aria-label="Listede ara" value="${esc(ui.listQuery)}"></label></div>
      <section class="list-coverage card"><strong>${current ? "Tamamlanan kayıt: " + num(Object.keys(current.members).length) + " hesap" : "Henüz tamamlanmış liste yok"}</strong><p>${current ? "Son tarama: " + esc(date(current.startedAt)) + " – " + esc(date(current.finishedAt)) + ". Sayfalama bitti ve kayıt sayısı profil sayısıyla uyuştu. Liste tarama sırasında değişebilir." : "Eksik ve yarım listeler karşılaştırmada kullanılmaz."}</p>${record.draft ? `<div class="notice warning"><strong>Kısmi kayıt · ${num(Object.keys(record.draft.members).length)} hesap</strong><p>${esc(date(record.draft.finishedAt || record.draft.startedAt))}. ${record.draft.status === "running" ? "Tarama sürüyor." : "Önceki tarama tamamlanmadı. Yeni tarama baştan başlar."} ${current ? "Üstteki tamamlanmış kayıt korundu." : "Görünen üyeler yalnız kısmi kapsamdadır."}</p></div>` : ""}</section>
      <div class="comparison-controls card"><label>Önceki gözlem<select id="previous-scan" ${scans.length < 2 ? "disabled" : ""}>${scanOptions(previous)}</select></label><label>Sonraki gözlem<select id="current-scan" ${!scans.length ? "disabled" : ""}>${scanOptions(current)}</select></label>${button(ui.comparison ? "Listeye dön" : "Farkları göster", "compare", "compare", "secondary", !ready ? "disabled" : "")}</div>
      ${ui.comparison && ready ? `<div class="compare-summary"><div class="compare-stat mint"><strong>${num(added)}</strong><span>Yeni görülen</span></div><div class="compare-stat peach"><strong>${num(removed)}</strong><span>Son kayıtta olmayan</span></div><p>“Son kayıtta yok” tek başına takipten çıkış veya engelleme kanıtı değildir.</p></div>` : ""}
      <div class="toolbar"><label class="inline-label">İlişki filtresi<select id="relation-filter" ${!current || !other || ui.comparison || current !== scans.at(-1) ? "disabled" : ""}><option value="all" ${ui.listFilter === "all" ? "selected" : ""}>Tümü</option><option value="mutual" ${ui.listFilter === "mutual" ? "selected" : ""}>İki listede de var</option><option value="oneway" ${ui.listFilter === "oneway" ? "selected" : ""}>Diğer listede yok</option></select></label><span class="toolbar-note">İlişki filtresi iki yönün de tamamlanmış son kaydını gerektirir; kayıt zamanları farklı olabilir.</span></div><div class="results-line"><span id="list-result-count">${listEntries().length} hesap · En fazla ${ui.pageSize} satır gösteriliyor</span></div><div class="list-table card" id="list-rows">${listRows()}</div><div id="list-pagination">${listPagination()}</div>`;
  }
  function timeline() {
    const events = data.events.filter(
      (e) => ui.eventType === "all" || e.type === ui.eventType,
    );
    return `${title("GÖZLEM GEÇMİŞİ", "Neyin ne zaman kontrol edildiğini gör.", "Bunlar kayıt zamanlarıdır; değişikliğin Instagram’da gerçekleştiği kesin zaman bilinmez.")}<div class="toolbar"><div class="filter-tabs" role="group" aria-label="Olay filtresi">${[
      ["all", "Tümü"],
      ["profile", "Profil farkları"],
      ["list", "Listeler"],
      ["check", "Kontroller"],
      ["error", "Hatalar"],
    ]
      .map(
        ([k, l]) =>
          `<button data-action="event-filter" data-value="${k}" class="${ui.eventType === k ? "active" : ""}" aria-pressed="${ui.eventType === k}">${l}</button>`,
      )
      .join(
        "",
      )}</div><span>${num(events.length)} kayıt</span></div><div class="timeline-layout"><section class="timeline-main"><div class="timeline-events card">${events.slice(0, ui.eventLimit).map(eventRow).join("") || empty("Seçilen türde henüz kayıt yok.")}</div>${events.length > ui.eventLimit ? button("Daha fazla göster", "more-events", "down") : ""}</section><aside class="timeline-explainer"><span class="outline-orbit">${icon("clock")}</span><h2>Her gözlemin bir kapsamı var.</h2><p>Profil kontrolü, liste taraması ve hata kayıtları ayrı tutulur. Başarısız kontrol eski gözlemi silmez.</p><p>Son 2.000 olay, profil başına son 366 gözlem ve liste yönü başına son 6 tamamlanmış tarama korunur.</p></aside></div>`;
  }
  function toggle(label, description, key) {
    return `<div class="setting-row"><div><strong>${esc(label)}</strong><p>${esc(description)}</p></div><button class="switch ${data.settings[key] ? "on" : ""}" role="switch" aria-checked="${!!data.settings[key]}" aria-label="${esc(label)}" data-action="setting" data-value="${key}" ${busy ? "disabled" : ""}><span></span></button></div>`;
  }
  function settings() {
    return `${title("SANA GÖRE", "Küçük ayarlar, daha rahat bir deneyim.", "Görünümü, bildirimleri ve yerel kayıtlarını yönet.")}<div class="settings-layout"><div><section class="settings-section card"><h2>Görünüm ve gözlem</h2>${toggle("Kompakt görünüm", "Kart ve satır aralıklarını daralt.", "compact")}${toggle("Windows bildirimleri", "Yeni gözlem farkları için Windows bildirimi göster.", "notifications")}${toggle("Otomatik profil kontrolü", "Yalnız uygulama açıkken; kendi hesabın ve izlediğin profiller sırayla kontrol edilir. Liste taraması başlatılmaz.", "automatic")}<div class="setting-row"><div><strong>Kontrol aralığı</strong><p>Her aralıkta en eski gözlemli bir profil okunur. Sınırlama ve doğrulama sonrası otomatik kontrol durur.</p></div><select id="auto-interval" aria-label="Kontrol aralığı" ${busy ? "disabled" : ""}>${[15, 30, 60, 180, 360].map((v) => `<option value="${v}" ${data.settings.interval === v ? "selected" : ""}>${v} dakika</option>`).join("")}</select></div></section><section class="settings-section card"><h2>Hesap ve istekler</h2><div class="setting-row"><div><strong>${data.account ? "@" + esc(data.account.username) : "Hesap bağlı değil"}</strong><p>${data.connected ? "Yerel oturum kayıtlı." : "Giriş için hesap bağlantısını aç."}</p></div>${button("Hesap bağlantısı", "account", "link")}</div><div class="setting-row"><div><strong>İstek bilgileri</strong><p>Son HTTP durumu, giriş aralığı ve yerel mola.</p></div>${button("İncele", "requests", "info")}</div>${data.connected ? button("Bağlantıyı kaldır", "disconnect", "logout", "secondary", busy ? "disabled" : "") : ""}</section><section class="settings-section card"><h2>Yerel veriler</h2><p>Yedekler oturum içermez ve aynı Windows kullanıcısında açılır. CSV dışa aktarımları şifresizdir; dosyayı paylaşmak senin kontrolündedir.</p><div class="settings-buttons">${button("Şifreli yedek al", "export-backup", "lock")}${button("Yedekten yükle", "import-backup", "note")}${button("Kayıt klasörünü aç", "open-storage", "laptop")}${button("Yerel verileri sil", "delete-all", "close")}</div></section></div><aside class="privacy-panel"><span class="privacy-large">${icon("shield")}</span><span class="eyebrow">YEREL GİZLİLİK</span><h2>Verilerin bilgisayarında.</h2><p>Oturum, profil gözlemleri, listeler ve notlar Windows DPAPI ile şifrelenir. Şifre ve doğrulama kodu kaydedilmez.</p><p>Hesap işlemleri doğrudan Instagram’a gider. Aracı sunucu, reklam veya analiz servisi kullanılmaz.</p><p>InstagramTracker, Instagram’dan bağımsızdır. Instagram erişimi sınırlayabilir veya ek doğrulama isteyebilir.</p><div class="privacy-panel-foot">${icon("lock")} Windows masaüstü · 0.2.0</div></aside></div>`;
  }
  function render() {
    if (!data) {
      document.getElementById("app").innerHTML =
        `<div class="startup card"><span class="dialog-symbol lavender">${icon("lock")}</span><h1>InstagramTracker</h1><p>${esc(serviceError || "Şifreli yerel kayıtlar açılıyor…")}</p>${serviceError ? "<p>Uygulamayı yeniden açabilirsin. Mevcut dosyalar otomatik silinmez.</p>" : ""}</div>`;
      operationPanel();
      return;
    }
    document.body.dataset.density = data.settings.compact
      ? "compact"
      : "comfortable";
    document.title = `${pages[ui.page][0]} — Tracker`;
    document.getElementById("app").innerHTML =
      `${sidebar()}<div class="workspace">${topbar()}<main id="main" tabindex="-1">${serviceError ? `<div class="notice warning">${esc(serviceError)}</div>` : ""}${data.lastError ? `<div class="notice warning"><strong>Son işlem:</strong> ${esc(data.lastError.message)} <button class="text-button" data-action="requests">İstek bilgileri</button></div>` : ""}${{ overview, people, lists, timeline, settings }[ui.page]()}<footer class="page-footer"><span>${icon("lock")} Şifreli yerel kayıtlar · Son gözlem ${esc(date(data.account?.observedAt))}</span><button class="text-button" data-action="privacy">Gizlilik ${icon("info")}</button></footer></main></div>`;
    operationPanel();
  }
  function accountDialog() {
    dialog(
      `<span class="dialog-symbol lavender">${icon("link")}</span><h2 id="dialog-title">${data.connected ? "Hesap bağlantın" : "Instagram hesabını bağla"}</h2><p>${data.account ? "Kayıtlı hesap: @" + esc(data.account.username) + ". " : ""}Şifre ve kod yalnız giriş işlemi sırasında kullanılır; dosyaya kaydedilmez.</p>${data.connected ? `<p class="dialog-note">Kayıtlı oturumla profil ve listeleri okuyabilirsin. Yeniden giriş yalnız oturum reddedilirse gerekir.</p>${button("Profili yenile", "refresh", "refresh", "primary", netDisabled())}` : ""}<form id="login-form"><label class="form-label" for="login-username">Kullanıcı adı</label><input id="login-username" name="username" required maxlength="30" pattern="[A-Za-z0-9._]{1,30}" value="${esc(data.account?.username || "")}" autocomplete="username"><label class="form-label" for="login-password">Şifre</label><input id="login-password" name="password" type="password" required maxlength="1024" autocomplete="off"><button class="button primary full-width" type="submit" ${busy ? "disabled" : ""}>${icon("link")} ${data.connected ? "Yeniden bağla" : "Hesabı bağla"}</button></form>${data.counterAvailable ? `<div class="import-connection"><p>Önceki Windows prototipinde kayıtlı bir oturum bulundu. Bir kopyası bu uygulamaya alınır; eski dosya değiştirilmez. Hesap kimliği canlı profil yanıtıyla doğrulanır.</p>${button("Önceki Windows oturumunu kullan", "import-counter", "laptop", "secondary", busy ? "disabled" : "")}</div>` : ""}<p class="field-hint">Instagram ek güvenlik kontrolü isterse resmî uygulamada tamamla. Otomatik parola tekrarı yapılmaz.</p>`,
    );
  }
  function requestsDialog() {
    const g = data.gate;
    dialog(
      `<span class="dialog-symbol lavender">${icon("info")}</span><h2 id="dialog-title">İstek bilgileri</h2><div class="detail-pair"><span>Son HTTP</span><strong>${g.status || "Bilinmiyor"}</strong></div><div class="detail-pair"><span>İstek sayısı</span><strong>${num(g.attempts || 0)}</strong></div><div class="detail-pair"><span>Son istek</span><strong>${esc(date(g.lastAt))}</strong></div><div class="detail-pair"><span>Yerel mola bitişi</span><strong>${esc(date(g.blockedUntil))}</strong></div><div class="detail-pair"><span>Sunucunun Retry-After zamanı</span><strong>${g.serverRetryAt ? esc(date(g.serverRetryAt)) : "Belirtilmedi"}</strong></div><div class="detail-pair"><span>Sonraki parola girişi</span><strong>${esc(date(g.loginAfter))}</strong></div><p class="dialog-note">İstekler arasında en az 15 saniye, parola girişleri arasında en az 15 dakika beklenir. HTTP 429 sonrası yerel mola 1–24 saattir; sunucunun daha uzun bekleme süresi korunur. Mola bitişi erişim garantisi değildir.</p><p>Otomatik kontrol: ${g.manualOnly ? "Duraklatıldı; önce elle başarılı bir profil kontrolü gerekli." : data.settings.automatic ? "Açık." : "Kapalı."}</p>`,
    );
  }
  function personDialog(id) {
    const p = person(id);
    if (!p) return;
    const own = p.id === data.account?.id,
      saved = data.people.find((x) => x.id === id) || p;
    dialog(
      `<div class="drawer-heading">${avatar(p, true)}<span class="badge neutral">${p.observedAt ? "Profil gözlemi" : "Liste üyesi · profil okunmadı"}</span></div><h2 id="dialog-title" class="profile-title">${esc(p.name || p.username)}</h2><p class="profile-handle">@${esc(p.username)}</p><p class="drawer-bio">${esc(p.bio ?? "Biyografi henüz okunmadı.")}</p><div class="drawer-counts"><div><strong>${num(p.followers)}</strong><span>takipçi</span></div><div><strong>${num(p.following)}</strong><span>takip edilen</span></div></div><div class="drawer-actions">${button("Profili kontrol et", "refresh-person", "refresh", "primary", `${idattr(id)} ${netDisabled()}`)}${button("Geçmiş", "history", "clock", "secondary", idattr(id))}</div><section class="drawer-section"><h3>Son kontrol</h3><div class="detail-pair"><span>Gözlem zamanı</span><strong>${esc(date(p.observedAt))}</strong></div><div class="detail-pair"><span>Gizlilik</span><strong>${p.private === true ? "Gizli" : p.private === false ? "Açık" : "Bilinmiyor"}</strong></div><div class="detail-pair"><span>Gönderi</span><strong>${num(p.posts)}</strong></div><div class="detail-pair"><span>Profil bağlantısı</span><span>${esc(p.url || "Gözlem yok")}</span></div></section>${!own ? `<section class="drawer-section"><div class="drawer-actions">${button(saved.favorite ? "Favoriden çıkar" : "Favorilere ekle", "drawer-star", "star", "secondary", `${idattr(id)} ${busy ? "disabled" : ""}`)}${button(saved.watching ? "İzlemeyi kapat" : "İzlemeye al", "watch", "bell", "secondary", `${idattr(id)} ${busy ? "disabled" : ""}`)}</div><p class="field-hint">İzleme, Ayarlar’da otomatik kontrol açıksa uygulama açıkken çalışır.</p><label class="form-label" for="person-group">Grup</label><input id="person-group" maxlength="80" value="${esc(saved.group || "")}" placeholder="Örn. Arkadaşlar"><label class="form-label" for="person-note">Sana özel not</label><textarea id="person-note" maxlength="2000" rows="4" placeholder="Bu kişiyle ilgili bir not…">${esc(saved.note || "")}</textarea><p class="field-hint">Not ve grup şifreli yerel depoya kaydedilir.</p>${button("Notu ve grubu kaydet", "save-note", "check", "secondary", `${idattr(id)} ${busy ? "disabled" : ""}`)}${data.people.some((x) => x.id === id) ? button("Kişilerden kaldır", "remove-person", "close", "secondary", `${idattr(id)} ${busy ? "disabled" : ""}`) : ""}</section>` : ""}`,
      false,
      true,
    );
    drawerId = id;
  }
  function historyDialog(id) {
    const p = person(id),
      history = data.history[id] || [];
    dialog(
      `<span class="dialog-symbol lavender">${icon("chart")}</span><h2 id="dialog-title">@${esc(p?.username || "hesap")} · Profil geçmişi</h2><p>Son ${history.length} kayıt. Sayaçlar yalnız gözlem zamanındaki değerlerdir.</p><div class="history-records">${
        [...history]
          .reverse()
          .map(
            (h) =>
              `<div><span>${esc(date(h.observedAt))}</span><strong>${num(h.followers)} takipçi · ${num(h.following)} takip edilen</strong></div>`,
          )
          .join("") || "<p>Henüz profil gözlemi yok.</p>"
      }</div>`,
      true,
    );
  }
  function valueText(value) {
    if (value === null || value === undefined) return "Bilinmiyor";
    if (typeof value === "boolean") return value ? "Evet" : "Hayır";
    if (typeof value === "object" && value.picture != null)
      return value.picture === "anonymous"
        ? "Anonim profil fotoğrafı"
        : "Kayıtlı profil fotoğrafı bilgisi (iki kayıtta farklı)";
    if (typeof value === "object")
      return Object.entries(value)
        .map(
          ([k, v]) =>
            `${labels[k] || { direction: "Liste yönü", count: "Hesap sayısı", added: "Yeni görülen", removed: "Son kayıtta yok" }[k] || k}: ${valueText(v)}`,
        )
        .join("\n");
    return String(value);
  }
  function eventDialog(id) {
    const e = data.events.find((e) => e.id === id);
    if (!e) return;
    dialog(
      `<span class="dialog-symbol lavender">${icon("clock")}</span><span class="eyebrow">${esc(date(e.at))}</span><h2 id="dialog-title">${esc(eventTitle(e))}</h2><div class="change-values"><div><span>Önceki gözlem</span><strong>${esc(valueText(e.old))}</strong></div><div><span>Sonraki gözlem</span><strong>${esc(valueText(e.new))}</strong></div></div><p class="dialog-note">Bu zaman kontrolün kaydedildiği andır. Liste farkı tek başına takipten çıkış veya engelleme kanıtı değildir.</p>`,
    );
  }
  function codeDialog() {
    dialog(
      `<span class="dialog-symbol lavender">${icon("lock")}</span><h2 id="dialog-title">Instagram doğrulama kodu</h2><p>Kodu aynı bekleyen giriş işlemine gönder. Kod kaydedilmez. Bekleme süresi 5 dakikadır.</p><form id="code-form"><label class="form-label" for="verification-code">Doğrulama kodu</label><input id="verification-code" name="code" inputmode="numeric" type="password" required pattern="[0-9]{6,10}" minlength="6" maxlength="10" autocomplete="one-time-code"><button class="button primary full-width" type="submit">Kodu gönder</button></form>${button("İşlemi iptal et", "cancel", "close")}`,
    );
  }
  document.addEventListener("click", async (event) => {
    const target = event.target.closest("[data-action]");
    if (!target || target.disabled || !data) return;
    const action = target.dataset.action,
      id = target.dataset.id,
      value = target.dataset.value;
    switch (action) {
      case "close":
        closeDialog();
        break;
      case "account":
        accountDialog();
        break;
      case "requests":
        requestsDialog();
        break;
      case "privacy":
        dialog(
          `<span class="dialog-symbol lavender">${icon("shield")}</span><h2 id="dialog-title">Verilerin bilgisayarında kalır.</h2><p>Oturum, gözlemler, listeler ve notlar Windows DPAPI ile şifrelenir. Parola ve doğrulama kodu saklanmaz. Hesap işlemleri doğrudan Instagram’a gider; aracı sunucuya gönderilmez.</p><p>Şifreli yedek oturum içermez. CSV dosyası şifresizdir ve yalnız sen dışa aktarmayı seçtiğinde yazılır.</p><p>InstagramTracker, Instagram’dan bağımsızdır.</p>`,
        );
        break;
      case "refresh":
        closeDialog();
        await command("refresh", {}, "Profil gözlemi kaydedildi.");
        break;
      case "import-counter":
        closeDialog();
        await command(
          "import_counter",
          {},
          "Önceki oturum doğrulandı ve bu uygulamaya kaydedildi.",
        );
        break;
      case "cancel":
        closeDialog();
        await command("cancel");
        toast(
          "İptal istendi. Devam eden ağ isteği en fazla zaman aşımı süresi kadar bekleyebilir.",
        );
        break;
      case "enter-code":
        codeDialog();
        break;
      case "search":
        closeDialog();
        ui.filter = "all";
        navigate("people");
        setTimeout(() => document.getElementById("people-search")?.focus(), 30);
        break;
      case "open-list":
        ui.direction = value;
        ui.owner = data.account?.id || "";
        ui.comparison = false;
        ui.current = null;
        ui.previous = null;
        ui.listFilter = "all";
        navigate("lists");
        break;
      case "range":
        ui.range = value;
        render();
        break;
      case "people-filter":
        ui.filter = value;
        render();
        break;
      case "profile-events":
        ui.eventType = "profile";
        navigate("timeline");
        break;
      case "event-filter":
        ui.eventType = value;
        ui.eventLimit = 100;
        render();
        break;
      case "more-events":
        ui.eventLimit += 100;
        render();
        break;
      case "list-tab":
        ui.direction = value;
        ui.listFilter = "all";
        ui.comparison = false;
        ui.current = null;
        ui.previous = null;
        ui.pageSize = 100;
        render();
        break;
      case "compare":
        ui.comparison = !ui.comparison;
        ui.listFilter = "all";
        ui.pageSize = 100;
        render();
        break;
      case "more-list":
        ui.pageSize += 100;
        render();
        break;
      case "scan":
        await command(
          "scan",
          { id: ui.owner || data.account?.id, direction: ui.direction },
          "Liste taraması kaydedildi.",
        );
        break;
      case "export-list":
        await file("export_list", {
          id: ui.owner || data.account?.id,
          direction: ui.direction,
          snapshotId: selectedScans().current?.id,
        });
        break;
      case "person":
        personDialog(id);
        break;
      case "star":
      case "drawer-star": {
        const p = data.people.find((p) => p.id === id);
        const reopen = action === "drawer-star";
        if (reopen) closeDialog();
        const r = await command(
          "save_person",
          { id, favorite: !p?.favorite },
          "Favori tercihi kaydedildi.",
        );
        if (reopen && r?.ok) personDialog(id);
        break;
      }
      case "watch": {
        const p = data.people.find((p) => p.id === id);
        closeDialog();
        const r = await command("save_person", { id, watching: !p?.watching });
        if (r?.ok) personDialog(id);
        break;
      }
      case "save-note": {
        const note = document.getElementById("person-note").value,
          group = document.getElementById("person-group").value;
        await command(
          "save_person",
          { id, note, group },
          "Not ve grup şifreli olarak kaydedildi.",
        );
        break;
      }
      case "refresh-person": {
        const p = person(id);
        closeDialog();
        const saved =
          id === data.account?.id || data.people.some((p) => p.id === id);
        const r = await command(
          saved ? "refresh" : "add_person",
          saved ? { id } : { username: p.username },
          "Profil gözlemi kaydedildi.",
        );
        if (r?.ok) personDialog(id);
        break;
      }
      case "remove-person":
        dialog(
          `<h2 id="dialog-title">Bu kişi kaldırılsın mı?</h2><p>Favori, not ve izleme tercihi silinir. Eski gözlem geçmişi ve liste kayıtları korunur.</p><div class="dialog-buttons">${button("Vazgeç", "close")}${button("Kişiyi kaldır", "remove-confirm", "close", "primary", idattr(id))}</div>`,
        );
        break;
      case "remove-confirm":
        closeDialog();
        await command("remove_person", { id }, "Kişi kaldırıldı.");
        break;
      case "history":
        historyDialog(id || data.account?.id);
        break;
      case "event":
        eventDialog(id);
        break;
      case "notifications":
        dialog(
          `<h2 id="dialog-title">Son gözlemler</h2><p>Windows bildirimleri Ayarlar bölümünden açılır.</p>${
            data.events
              .filter((e) => ["profile", "list"].includes(e.type))
              .slice(0, 20)
              .map(eventRow)
              .join("") || "<p>Henüz değişiklik gözlenmedi.</p>"
          }`,
          true,
        );
        break;
      case "add-person":
        dialog(
          `<span class="dialog-symbol lavender">${icon("plus")}</span><h2 id="dialog-title">Bir profil ekle.</h2><p>Kullanıcı adıyla Instagram’dan profil bilgisi okunur. Bu işlem takip etme isteği göndermez.</p><form id="add-person-form"><label class="form-label" for="new-username">Instagram kullanıcı adı</label><input id="new-username" name="username" required maxlength="30" pattern="[A-Za-z0-9._]{1,30}" placeholder="kullanici.adi" autocomplete="off"><button class="button primary full-width" type="submit">Profili oku ve kaydet</button></form>`,
        );
        break;
      case "setting":
        await command(
          "settings",
          { [value]: !data.settings[value] },
          "Ayar kaydedildi.",
        );
        break;
      case "export-backup":
        await file("export_backup");
        break;
      case "import-backup":
        await file("import_backup");
        break;
      case "open-storage":
        await file("open_storage");
        break;
      case "disconnect":
        dialog(
          `<h2 id="dialog-title">Yerel oturum kaldırılsın mı?</h2><p>Bu uygulamadaki oturum silinir. Profil gözlemleri ve notlar korunur. Instagram’daki diğer oturumların kapanmaz.</p><div class="dialog-buttons">${button("Vazgeç", "close")}${button("Bağlantıyı kaldır", "disconnect-confirm", "logout", "primary")}</div>`,
        );
        break;
      case "disconnect-confirm":
        closeDialog();
        await command("disconnect", {}, "Yerel oturum kaldırıldı.");
        break;
      case "delete-all":
        dialog(
          `<h2 id="dialog-title">Bütün yerel veriler silinsin mi?</h2><p>Bu uygulamadaki oturum, kişiler, notlar, liste ve geçmiş kayıtları silinir. Dışa aktardığın dosyalar ve önceki Windows prototipi etkilenmez. İstek molası korunur.</p><div class="dialog-buttons">${button("Vazgeç", "close")}${button("Yerel verileri sil", "delete-confirm", "close", "primary")}</div>`,
        );
        break;
      case "delete-confirm":
        closeDialog();
        await command("delete_all", {}, "Yerel kayıtlar silindi.");
        break;
    }
  });
  document.addEventListener("submit", async (event) => {
    const form = event.target;
    if (!["login-form", "code-form", "add-person-form"].includes(form.id))
      return;
    event.preventDefault();
    const values = new FormData(form);
    if (form.id === "login-form") {
      const args = {
        username: String(values.get("username")),
        password: String(values.get("password")),
      };
      form.reset();
      closeDialog();
      await command(
        "login",
        args,
        "Hesap bağlandı ve ilk profil gözlemi kaydedildi.",
      );
      args.password = "";
    } else if (form.id === "code-form") {
      const code = String(values.get("code"));
      form.reset();
      closeDialog();
      await command("code", { code });
    } else {
      const username = String(values.get("username"));
      closeDialog();
      await command("add_person", { username }, "Profil kaydedildi.");
    }
  });
  document.addEventListener("input", (event) => {
    if (event.target.id === "people-search") {
      ui.query = event.target.value;
      document.getElementById("people-grid").innerHTML =
        filteredPeople().map(personCard).join("") ||
        empty("Aramana uygun kişi yok.");
      document.getElementById("people-result-count").textContent =
        `${filteredPeople().length} kişi`;
    }
    if (event.target.id === "list-search") {
      ui.listQuery = event.target.value;
      ui.pageSize = 100;
      document.getElementById("list-rows").innerHTML = listRows();
      document.getElementById("list-result-count").textContent =
        `${listEntries().length} hesap · En fazla ${ui.pageSize} satır gösteriliyor`;
      document.getElementById("list-pagination").innerHTML = listPagination();
    }
  });
  document.addEventListener("change", async (event) => {
    switch (event.target.id) {
      case "list-owner":
        ui.owner = event.target.value;
        ui.current = null;
        ui.previous = null;
        ui.comparison = false;
        ui.listFilter = "all";
        render();
        break;
      case "current-scan":
        ui.current = event.target.value;
        render();
        break;
      case "previous-scan":
        ui.previous = event.target.value;
        render();
        break;
      case "relation-filter":
        ui.listFilter = event.target.value;
        ui.pageSize = 100;
        render();
        break;
      case "auto-interval":
        await command(
          "settings",
          { interval: Number(event.target.value) },
          "Kontrol aralığı kaydedildi.",
        );
        break;
    }
  });
  window.addEventListener("hashchange", () => {
    ui.page = Object.hasOwn(pages, location.hash.slice(1))
      ? location.hash.slice(1)
      : "overview";
    closeDialog();
    render();
    window.scrollTo(0, 0);
  });
  document.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      closeDialog();
      ui.filter = "all";
      navigate("people");
      setTimeout(() => document.getElementById("people-search")?.focus(), 30);
    }
  });
  if (!api) {
    serviceError =
      "Bu sayfa InstagramTracker Windows uygulaması içinde açılmalı.";
    render();
    return;
  }
  api.subscribe((event) => {
    if (event.type === "notification_warning" || event.notificationWarning)
      toast(event.message || event.notificationWarning);
    if (event.state) data = event.state;
    if (event.type === "fatal") {
      serviceError = event.message;
      busy = false;
      progress = null;
      render();
    } else if (event.type === "progress") {
      busy = true;
      progress = event;
      operationPanel();
      if (event.phase === "code") codeDialog();
    } else if (event.type === "ready") {
      serviceError = null;
      render();
    } else if (event.type === "result") {
      busy = false;
      progress = null;
      render();
      if (document.querySelector("#code-form")) closeDialog();
    }
  });
  ui.page = Object.hasOwn(pages, location.hash.slice(1))
    ? location.hash.slice(1)
    : "overview";
  api
    .command("state")
    .then((result) => {
      data = result.state;
      busy = result.busy;
      progress = result.progress;
      serviceError = result.error?.message;
      render();
    })
    .catch(() => {
      serviceError = "Yerel hizmete ulaşılamadı.";
      render();
    });
})();
