(() => {
  "use strict";
  const i18n = window.TrackerI18n;
  let language = 'tr';
  const t = (message, ...values) => i18n.translate(message, values, language);
  const stored = message => i18n.stored(message, language);
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
      ? i18n.formats(language).number.format(value)
      : "—";
  const requestSpacing = () => [num(data.requestPacing.minSeconds), num(data.requestPacing.maxSeconds)];
  const date = (value) =>
    value
      ? i18n.formats(language).date.format(new Date(value * 1000))
      : t("Henüz yok");
  const labels = {
    username: "Kullanıcı adı",
    name: "Görünen ad",
    bio: "Biyografi",
    url: "Web sitesi",
    private: "Gizli hesap",
    verified: "Doğrulanmış hesap",
    followers: "Takipçi",
    following: "Takip edilen",
    posts: "Gönderi",
    picture: "Profil fotoğrafı bilgisi",
  };
  const pages = {
    overview: ["Özet", "home"],
    people: ["Kaydedilen Profiller", "people"],
    lists: ["Takip Listeleri", "list"],
    notifications: ["Bildirimler", "bell"],
    settings: ["Ayarlar", "settings"],
    timeline: ["Zaman çizelgesi", "clock"],
  };
  const keyboardPages = ['overview', 'people', 'lists', 'notifications', 'settings'];
  let data = null,
    busy = false,
    progress = null,
    serviceError = null,
    windowVisible = true,
    drawerId = null,
    personView = null,
    profileSearch = null,
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
    notificationFilter: "all",
    notificationLimit: 100,
  };
  function acceptState(next) {
    if (data && next.activeAccountId !== data.activeAccountId) {
      listPhotos.clear(true);
      profileDetails.reset();
      closeDialog();
      document.querySelector('.command-palette')?.close();
      Object.assign(ui, {page:'overview', query:'', filter:'all', owner:'', listQuery:'',
        listFilter:'all', comparison:false, previous:null, current:null, eventLimit:100,
        notificationFilter:'all', notificationLimit:100});
      location.hash = '#overview';
    }
    data = next;
  }
  function toast(message) {
    const node = document.getElementById("toast");
    node.textContent = stored(message);
    node.classList.add("visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove("visible"), 5000);
  }
  const button = (text, action, symbol, kind = "secondary", extra = "") =>
    `<button type="button" class="button ${kind}" data-action="${action}" ${extra}>${symbol ? icon(symbol) : ""}<span>${esc(text)}</span></button>`;
  const operationProgress = window.TrackerOperationProgress.create({
    element: document.getElementById("operation-root"), t, date, number: num, button, icon,
  });
  const idattr = (id) => `data-id="${esc(id)}"`;
  const netDisabled = () => (busy || !data?.connected ? "disabled" : "");
  const listPhotos=window.TrackerListPhotos.create({
    request:args=>api.command('list_avatar',args),
    cancel:()=>api.command('cancel_list_images'),
    onImage:(source,image)=>receivePersonPhoto(source,image),
  });
  const profileDetails=window.TrackerProfileDetails.create({
    availability:()=>({busy,wait:Math.max(0,(data?.gate.nextAt || 0)*1000-Date.now()),
      stop:!data?.connected ? {code:'connection'} : (data.gate.blockedUntil || 0)*1000>Date.now() ? {code:'cooldown'} :
        (data.requestBudget.nextAt || 0)*1000>Date.now() ? {code:'budget'} : null}),
    load:async ({manual,...args})=>{
      const refresh=manual && (args.id===data.account?.id || data.people.some(p=>p.id===args.id));
      const response=await command(refresh?'refresh':'profile_detail',refresh?{id:args.id}:args,'',false);
      const detail=refresh ? {accountId:response?.state?.activeAccountId,profile:response?.state?.account?.id===args.id ? response.state.account : response?.state?.people.find(p=>p.id===args.id)} : response?.state?.profileDetail;
      return {...response,accountId:detail?.accountId,profile:detail?.profile};
    },
  });
  function refreshListPhotos() {
    listPhotos.mount(windowVisible && ui.page==='lists' ? document.getElementById('list-rows') : null,
      {accountId:data?.activeAccountId,ownerId:ui.owner || data?.account?.id,direction:ui.direction});
  }
  function person(id) {
    if (data?.account?.id === id) return data.account;
    const p = data?.people.find((p) => p.id === id);
    if (p) return p;
    if(personView?.id===id && personView.accountId===data?.activeAccountId)return personView.profile;
    for (const record of Object.values(data?.lists || {})) {
      for (const scan of [...record.snapshots]
        .reverse()
        .concat(record.draft || [],record.retainedPartial || []))
        if (scan.members[id]) return scan.members[id];
    }
    return data?.access?.find((c) => c.id === id) || null;
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
    `<div class="page-heading"><div><span class="eyebrow">${esc(kicker)}</span><h1>${esc(heading)}</h1>${text ? `<p>${esc(text)}</p>` : ""}</div>${action}</div>`;
  const empty = (text, action = "") =>
    `<div class="empty-state">${icon("info")}<h2>${t("Burada henüz bir kayıt yok.")}</h2>${text ? `<p>${esc(text)}</p>` : ""}${action}</div>`;
  function navigate(page) {
    ui.page = page;
    closeDialog();
    if (location.hash !== '#' + page) location.hash = page;
    render();
    window.scrollTo(0, 0);
  }
  function closeDialog() {
    profileDetails.close();personView=null;
    profileSearch?.close();profileSearch=null;
    document.querySelector("#overlay-root dialog")?.close();
    drawerId = null;
  }
  function dialog(content, wide = false, drawer = false) {
    closeDialog();
    document.getElementById("overlay-root").innerHTML =
      `<dialog class="${drawer ? "drawer" : "modal"} ${wide ? "wide-modal" : ""}" aria-labelledby="dialog-title"><button class="dialog-close icon-button" data-action="close" aria-label="${t("Kapat")}">${icon("close")}</button>${content}</dialog>`;
    const modal = document.querySelector("#overlay-root dialog");
    modal.addEventListener('cancel', event => { event.preventDefault(); closeDialog(); });
    modal.showModal();
  }
  const palette = window.TrackerCommandPalette.create({
    t, icon,
    entries: () => [
      ...Object.entries(pages).map(([id, [name, symbol]]) => ({kind:'page', id, title:t(name), subtitle:t("Uygulama ekranı"), icon:symbol, shortcut:keyboardPages.includes(id) ? String(keyboardPages.indexOf(id)+1) : ''})),
      ...[...(data?.account ? [data.account] : []), ...(data?.people || []).slice().sort((a,b)=>Number(!!b.favorite)-Number(!!a.favorite))]
        .map(p=>({kind:'profile',id:p.id,title:'@'+p.username,subtitle:p.name || t("Kayıtlı profil"),terms:p.group || '',icon:'people'})),
    ],
    select: entry => {
      if (entry.kind === 'profile') { if (person(entry.id)) personDialog(entry.id); }
      else { closeDialog(); navigate(entry.id); }
    },
  });
  function editing() {
    const element = document.activeElement;
    return element?.isContentEditable || !!element?.closest('input, textarea, select');
  }
  async function refreshSelected() {
    if (!data || serviceError) return;
    if (busy) { toast(t("Önce devam eden işlemi bitir veya iptal et.")); return; }
    if (editing() || palette.isOpen()) { toast(t("Yazı alanındayken yenileme kısayolu çalışmaz.")); return; }
    const modal = document.querySelector('#overlay-root dialog[open]');
    if (modal && (!modal.classList.contains('drawer') || !drawerId)) { toast(t("Profili yenilemek için önce açık pencereyi kapat.")); return; }
    const id = modal && drawerId ? drawerId : ui.page === 'lists' ? ui.owner || data.account?.id : ui.page === 'overview' ? data.account?.id : null;
    if(modal?.classList.contains('drawer') && drawerId) {profileDetails.retry();return;}
    if (!id) { toast(t("Yenilemek için bir profil aç veya Özet ekranına geç.")); return; }
    if (!data.connected) { toast(t("Profil yenilemek için önce hesabını bağla.")); return; }
    if (data.gate.blockedUntil > Date.now()/1000) { toast(t("Kontroller için mola sürüyor. Yeniden deneme saatini İstek bilgileri bölümünden görebilirsin.")); return; }
    if (id !== data.account?.id && !data.people.some(p=>p.id===id)) { toast(t("Bu profili yenilemeden önce Kaydedilen Profiller bölümüne ekle.")); return; }
    const target=person(id), reopen=!!modal;
    if (reopen) closeDialog();
    const result=await command('refresh',{id},t("Profil kontrol edildi."));
    if (result?.ok && reopen) personDialog(id);
    else if (result && !result.ok) toast(t("@{0} için yenileme tamamlanamadı.",target?.username || id));
  }
  function guide(heading, copy, steps, actions, symbol='people') {
    return `<section class="empty-guide card"><span class="dialog-symbol lavender">${icon(symbol)}</span><div><h2>${esc(heading)}</h2><p>${esc(copy)}</p>${steps.length ? `<ol>${steps.map(step=>`<li>${esc(step)}</li>`).join('')}</ol>` : ''}<div class="guide-actions">${actions}</div></div></section>`;
  }
  function peopleEmpty() {
    if (!data.people.length) return guide(t("Henüz profil kaydetmedin."),t("Değişikliklerini izlemek veya not eklemek istediğin bir profili buraya ekleyebilirsin. Takipçilerin bu bölüme otomatik eklenmez."),
      [t("Instagram kullanıcı adını yazarak bir profil kaydet."),t("Profili aç; izleme ve bildirim tercihlerini seç. Profil kaydetmek Instagram’da takip isteği göndermez.")],
      (data.connected ? button(t("Profil ekle"),'add-person','plus','primary',netDisabled()) : button(t("Hesabını bağla"),'account','link','primary')) +
      button(t("Takipçi listemi aç"),'guide-own-lists','list','secondary',data.account ? '' : 'disabled'));
    const filtered = ui.query.trim() ? t("Aramana uygun kişi yok.") : ui.filter === 'favorites' ? t("Henüz favori profil yok.") : t("Henüz izlenen profil yok.");
    return guide(filtered,t("Farklı bir arama dene veya tüm kayıtlı kişilere dön. Favori ve izleme tercihlerini kişi ayrıntısından değiştirebilirsin."),[],button(t("Arama ve filtreleri temizle"),'reset-people','search'));
  }
  function notificationsEmpty() {
    if ((data.notificationLog || []).length) return guide(t("Okunmamış bildirim yok."),t("Diğer kayıtlar için Tümü filtresine dönebilirsin."),[],button(t("Tüm bildirimleri göster"),'notification-filter','bell','primary','data-value="all"'),'bell');
    return guide(t("Değişiklik kayıtların burada birikecek."),t("İlk kontrol başlangıç kaydını oluşturur. Daha sonra görülen değişiklikler, her kontrol için tek kayıtta toplanır."),
      [t("Önce bir profil kaydet ve kontrol et."),t("Profil ayrıntısında bildirim almak istediğin değişiklik türlerini seç."),t("Windows bildirimi isteğe bağlıdır; uygulama içindeki kayıtlar bildirim kapalıyken de tutulur.")],
      button(t("Kaydedilen profilleri aç"),'guide-people','people','primary')+button(t("Bildirim ayarları"),'guide-settings','settings'),'bell');
  }
  function userError(value) {
    const messages = {
      limited: t("Instagram istekleri sınırladı. Kontroller geçici olarak duraklatıldı."),
      cooldown: t("Kontroller için mola sürüyor. Yeniden deneme saatini İstek bilgileri bölümünden görebilirsin."),
      budget: t("Kontroller için mola sürüyor. Yeniden deneme saatini İstek bilgileri bölümünden görebilirsin."),
      connection: t("Önce hesabınla bağlantı kuralım."),
      list_coverage: t("Instagram listenin tamamını göstermedi. Okunan hesaplar saklandı; bu listeden değişiklik sonucu çıkarılmadı."),
      list_count: t("Okunan liste ile profilindeki sayı uyuşmadı. Kayıtlar korundu; bu listeden değişiklik sonucu çıkarılmadı."),
      access_unknown: t("Hesap bağlantın doğrulanamadığı için diğer profil kontrol edilmedi.")
    };
    return value && typeof value === 'object' ? messages[value.code] || stored(value.message || t("İşlem tamamlanamadı.")) : stored(value);
  }
  function errorDialog(message) {
    dialog(
      `<span class="dialog-symbol peach">${icon("info")}</span><h2 id="dialog-title">${t("İşlem tamamlanamadı")}</h2><p>${esc(userError(message))}</p>${message && typeof message === 'object' ? `<details><summary>${t("Teknik ayrıntılar")}</summary><p>${esc(message.code)}</p><p>${esc(message.message)}</p></details>` : ''}${data?.gate.blockedUntil > Date.now() / 1000 ? `<p class="dialog-note">${t("En erken {0} yeniden deneyebilirsin. Instagram daha uzun beklemeni isteyebilir.", esc(date(data.gate.blockedUntil)))}</p>` : ""}<div class="dialog-buttons">${button(t("İstek bilgileri"), "requests", "info")}${button(t("Tamam"), "close", null, "primary")}</div>`,
    );
  }
  async function command(operation, args = {}, success = "", showErrors = true) {
    if (busy && !["cancel", "code", "state"].includes(operation)) {
      toast(t("Önce devam eden işlemi bitir veya iptal et."));
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
      if (result.state) acceptState(result.state);
      if (!control) {
        busy = false;
        progress = null;
        render();
      }
      if (!result.ok && showErrors)
        errorDialog(result.error || t("İşlem tamamlanamadı."));
      else if (success) toast(success);
      return result;
    } catch {
      if (!control) {
        busy = false;
        progress = null;
        render();
      }
      const error={message:t("Yerel hizmete ulaşılamadı. Uygulamayı yeniden açabilirsin.")};
      if(showErrors)errorDialog(error.message);
      return {ok:false,error};
    }
  }
  async function file(action, args = {}) {
    try {
      const result = await api.file(action, args);
      if (result.state) {
        acceptState(result.state);
        render();
      }
      if (!result.ok)
        errorDialog(result.error || t("Dosya işlemi tamamlanamadı."));
      else if (!result.cancelled && action !== "open_storage")
        toast(t("Dosya işlemi tamamlandı."));
    } catch {
      errorDialog(t("Dosya okunamadı veya yazılamadı. Mevcut kayıtlar korundu."));
    }
  }
  function sidebar() {
    return `<aside class="sidebar"><a class="brand" href="#overview"><span class="brand-mark">${icon("chart")}</span><span><strong>${t("Tracker")}</strong><small>${t("Çevren, tek bakışta.")}</small></span></a><span class="nav-label">${t("ÇALIŞMA ALANIN")}</span><nav aria-label="${t("Ana gezinme")}">${Object.entries(
      pages,
    )
      .map(
        ([key, [label, symbol]]) =>
          `<a class="nav-item ${ui.page === key ? "selected" : ""}" href="#${key}" ${ui.page === key ? 'aria-current="page"' : ""}>${icon(symbol)}<span>${t(label)}</span>${key === "people" ? `<span class="nav-count">${data.people.length}</span>` : ""}</a>`,
      )
      .join(
        "",
      )}</nav><div class="sidebar-bottom"><button class="account-switcher" data-action="accounts" aria-label="${t("Hesaplar arasında geçiş")}" ${busy ? "disabled" : ""}>${avatar(data.account)}<span><strong>${data.account ? "@" + esc(data.account.username) : t("Hesabını bağla")}</strong><small>${t("{0} kayıtlı hesap", (data.accounts || []).length)}</small></span>${icon("down")}</button><div class="preview-label"><span class="status-dot"></span> ${t("Yerel masaüstü")} <span>0.2</span></div></div></aside>`;
  }
  function topbar() {
    const own = data.account;
    return `<header class="topbar"><div class="breadcrumbs"><span>${t("Çalışma alanım")}</span>${icon("chevron")}<strong>${t(pages[ui.page][0])}</strong></div><button class="global-search" data-action="search">${icon("search")}<span>${t("Kişi ara")}</span><kbd>${t("Ctrl K")}</kbd></button><div class="top-actions"><span class="sample-pill">${data.connected ? t("Yerel oturum") : t("Hesap bağlı değil")}</span><button class="icon-button" data-action="notifications" aria-label="${t("Bildirim kayıtları")}">${icon("bell")}${(data.notificationLog || []).some((entry) => entry.readAt == null) ? `<span class="unread-dot"></span>` : ""}</button><button class="account-button" data-action="account" aria-label="${t("Hesap bağlantısı")}">${avatar(own)}<span><strong>${own ? "@" + esc(own.username) : t("Hesabını bağla")}</strong><small>${data.connected ? t("Şifreli oturum") : t("Bağlantı bekleniyor")}</small></span>${icon("down")}</button></div></header>`;
  }
  function operationPanel() {
    operationProgress.update({busy, progress, gate: data?.gate || {}, visible: windowVisible});
  }
  function chart(p) {
    const all = data.history[p?.id] || [];
    const cutoff =
      ui.range === "all" ? 0 : Date.now() / 1000 - Number(ui.range) * 86400;
    const history = all.filter(
      (h) => h.observedAt >= cutoff && typeof h.followers === "number",
    );
    const controls = `<div class="segmented" role="group" aria-label="${t("Grafik aralığı")}">${[
      ["7", t("7 gün")],
      ["30", t("30 gün")],
      ["all", t("Tümü")],
    ]
      .map(
        ([k, l]) =>
          `<button data-action="range" data-value="${k}" class="${ui.range === k ? "active" : ""}" aria-pressed="${ui.range === k}">${l}</button>`,
      )
      .join("")}</div>`;
    let graph = empty(t("Profilin kontrol edildikçe takipçi geçmişin burada görünür."));
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
      graph = `<div class="chart-summary"><strong>${num(values.at(-1))}<span>${t("takipçi")}</span></strong><span class="muted">${t("{0} kayıt", history.length)}</span></div><div class="chart-wrap"><svg class="history-chart" viewBox="0 0 710 252" role="img" aria-label="${t("Kayıtlı takipçi sayıları")}">${[0, 1, 2, 3].map((i) => `<line x1="50" y1="${40 + (i * 170) / 3}" x2="680" y2="${40 + (i * 170) / 3}" class="grid-line"/><text x="34" y="${44 + (i * 170) / 3}" text-anchor="end" class="axis-label">${num(Math.round(max - (i * (max - min)) / 3))}</text>`).join("")}<path d="${line}" class="chart-line"/>${points.map(([x, y], i) => `<circle cx="${x}" cy="${y}" r="5" class="chart-point" tabindex="0"><title>${esc(date(history[i].observedAt))} · ${num(values[i])}</title></circle>`).join("")}<text x="50" y="242" class="axis-label">${esc(new Date(start * 1000).toLocaleDateString(i18n.locale(language)))}</text><text x="670" y="242" text-anchor="end" class="axis-label">${esc(new Date(end * 1000).toLocaleDateString(i18n.locale(language)))}</text></svg></div>`;
    }
    return `<section class="chart-card card"><div class="section-heading"><div><h2>${t("Takipçi geçmişi")}</h2><p>${t("Önceki kontrollerdeki takipçi sayın.")}</p></div>${controls}</div>${graph}<div class="chart-caption">${button(t("Kayıtları gör"), "history", "clock", "secondary", idattr(p?.id || ""))}</div></section>`;
  }
  function eventTitle(e) {
    if (e.type === "error") return t("Kontrol tamamlanamadı");
    if (e.topic === "access") return stored(e.title);
    if (e.type === "profile")
      return `@${person(e.personId)?.username || e.personId} · ${t(labels[Object.keys(e.new || {})[0]]) || t("Profil değişikliği")}`;
    if (e.type === "list")
      return t("{0} listesi kaydedildi", e.new?.direction === "following" ? t("Takip edilen") : t("Takipçi"));
    return stored(e.title);
  }
  function eventRow(e) {
    return `<button class="event-row" data-action="event" ${idattr(e.id)}><span class="event-icon ${e.type === "error" ? "peach" : "lavender"}">${icon(e.type === "list" ? "list" : e.type === "error" ? "info" : "clock")}</span><span class="event-copy"><strong>${esc(eventTitle(e))}</strong><small>${esc(date(e.at))}</small></span>${icon("chevron")}</button>`;
  }
  function notificationRule(id) {
    const saved = data.notificationRules?.[id];
    return {
      enabled: saved?.enabled ?? true,
      fields: Object.fromEntries(
        Object.keys(data.notificationTypes || {}).map((key) => [
          key,
          saved?.fields?.[key] ?? true,
        ]),
      ),
    };
  }
  function notificationPreferences(id) {
    const p = person(id);
    if (!p) return;
    const selected = notificationRule(id);
    dialog(
      `<span class="dialog-symbol lavender">${icon("bell")}</span><h2 id="dialog-title">${t("@{0} · Bildirim tercihleri", esc(p.username))}</h2><p>${t("Windows bildiriminde görmek istediğin değişiklikleri seç. Bütün gözlenen değişiklikler uygulama içindeki kayıtta saklanır.")}</p><form id="notification-rule-form" data-id="${esc(id)}"><label class="notification-check notification-account-toggle"><input name="account-enabled" type="checkbox" ${selected.enabled ? "checked" : ""}><span>${t("Bu hesabın Windows bildirimleri açık")}</span></label><fieldset class="notification-options"><legend>${t("Değişiklik türleri")}</legend>${Object.entries(
        data.notificationTypes,
      )
        .map(
          ([key, label]) =>
            `<label class="notification-check"><input type="checkbox" name="${esc(key)}" ${selected.fields[key] ? "checked" : ""}><span>${esc(t(label))}</span></label>`,
        )
        .join(
          "",
        )}</fieldset><p class="dialog-note">${t("Genel Windows bildirimleri {0}. Bir kontrolde seçili birkaç değişiklik olsa da tek bildirim gelir. Kayıt zamanı, değişikliğin Instagram’da gerçekleştiği kesin zaman değildir.", data.settings.notifications ? t("açık") : t("kapalı"))}</p><button class="button primary full-width" type="submit" ${busy ? "disabled" : ""}>${t("{0}Tercihleri kaydet", icon("check"))}</button></form>`,
      true,
    );
  }
  function logHeading(entry) {
    const names = [...new Set(entry.changes.map((change) => change.username))];
    return t("{0} · {1} değişiklik", names.length === 1 ? "@" + names[0] : names.length + t(" hesap"), entry.changes.length);
  }
  function notificationRows() {
    return (data.notificationLog || []).filter(
      (entry) => ui.notificationFilter !== "unread" || entry.readAt == null,
    );
  }
  function notifications() {
    const rows = notificationRows(),
      unread = (data.notificationLog || []).filter(
        (entry) => entry.readAt == null,
      ).length;
    return `${title(t("BİLDİRİM KAYITLARI"), t("Bir kontrolde neler değişti?"), t("Aynı kontroldeki değişiklikler tek kayıtta; bildirim tercihin kapalı olsa da geçmiş burada kalır."))}<div class="toolbar"><div class="filter-tabs" role="group" aria-label="${t("Bildirim filtresi")}">${[
      ["all", t("Tümü")],
      ["unread", t("Okunmamış")],
    ]
      .map(
        ([key, label]) =>
          `<button data-action="notification-filter" data-value="${key}" class="${ui.notificationFilter === key ? "active" : ""}" aria-pressed="${ui.notificationFilter === key}">${t(label)}${key === "unread" ? ` (${unread})` : ""}</button>`,
      )
      .join(
        "",
      )}</div><a class="text-button" href="#settings">${t("Bildirim ayarları {0}", icon("settings"))}</a></div><div class="notification-log">${rows
        .slice(0, ui.notificationLimit)
        .map(
          (entry) =>
            `<button class="notification-log-row card ${entry.readAt == null ? "unread" : ""}" data-action="notification-log" ${idattr(entry.id)}><span class="event-icon lavender">${icon("bell")}</span><span class="notification-log-copy"><strong>${esc(logHeading(entry))}</strong><span>${esc([...new Set(entry.changes.map((change) => t(change.label)))].join(" · "))}</span><small>${esc(date(entry.at))} · ${entry.readAt == null ? t("Okunmamış") : t("Okundu")} · ${entry.desktopEligible ? t("Windows bildirimi için seçildi") : t("Yalnız uygulama kaydı")}</small></span>${icon("chevron")}</button>`,
        )
        .join("") ||
      notificationsEmpty()}</div>${rows.length > ui.notificationLimit ? button(t("Daha fazla kayıt göster"), "more-notifications", "down") : ""}<p class="field-hint">${t("Son 500 değişiklik grubu şifreli saklanır. “Windows bildirimi için seçildi” ifadesi Windows’un bildirimi teslim ettiğini doğrulamaz.")}</p>`;
  }
  async function notificationDialog(id) {
    const entry = data.notificationLog?.find((entry) => entry.id === id);
    if (!entry) {
      toast(t("Bu kayıt artık saklanan geçmişte yok."));
      return;
    }
    const selected = new Set(entry.selectedChangeIds);
    dialog(
      `<span class="dialog-symbol lavender">${icon("bell")}</span><span class="eyebrow">${esc(date(entry.at))}</span><h2 id="dialog-title">${esc(logHeading(entry))}</h2><p>${t("Aynı kontrolün bütün değişiklikleri aşağıda. Kapalı bildirim türleri de kayıtta gösterilir.")}</p><ol class="change-log-details">${entry.changes.map((change) => `<li><div class="section-heading"><div><strong>@${esc(change.username)} · ${esc(t(change.label))}</strong><p>${esc(date(change.at))}</p></div><span class="badge ${selected.has(change.eventId) ? "mint" : "neutral"}">${selected.has(change.eventId) ? t("Bildirim için seçili") : t("Yalnız kayıtta")}</span></div>${change.category === "access" ? `<p>${esc(stored(change.title))}</p>${accessPanel(change.personId, change.after)}` : change.category === "lists" ? listChangePreview(change.after,change.eventId) : `<div class="change-values"><div><span>${t("Önceki kontrol")}</span><strong>${esc(valueText(change.before))}</strong></div><div><span>${t("Yeni kontrol")}</span><strong>${esc(valueText(change.after))}</strong></div></div>`}</li>`).join("")}</ol><p class="dialog-note">${t("{0} Kayıt zamanı kesin olay saati değildir.", entry.desktopEnabledAtCheck ? t("Bu kontrol sırasında genel Windows bildirimleri açıktı.") : t("Bu kontrol sırasında genel Windows bildirimleri kapalıydı."))}</p>`,
      true,
    );
    if (entry.readAt == null && !busy)
      await command("read_notification", { id });
  }
  function preparationCard() {
    const state = data.listPreparation;
    if (!data.account || !state) return "";
    const paused = !data.connected || !state.enabled || !data.settings.automatic;
    const blocked = data.gate.manualOnly || data.gate.blockedUntil > Date.now()/1000;
    const working = state.directions.some(d => d.scanning);
    const heading = paused ? t("Liste hazırlığı duraklatıldı") : blocked ? t("Liste hazırlığı bekliyor") :
      state.ready ? t("Başlangıç listelerin hazır") : t("Hesabın bağlandı, listelerin hazırlanıyor");
    const rows = state.directions.map(d => `<li><strong>${d.direction === 'followers' ? t("Takipçiler") : t("Takip edilen")}</strong><span>${
      d.error ? t("Tarama tamamlanmadı; kayıtlar korundu.") : d.ready && !d.scanning ? t("Başlangıç kaydı hazır") :
      d.pages ? t("{0} sayfa · {1} hesap kaydedildi", num(d.pages), num(d.count)) : t("Okuma sırada")}</span></li>`).join('');
    return `<section class="preparation-card card" aria-label="${t("Başlangıç listeleri")}"><div><span class="eyebrow">${t("LİSTE HAZIRLIĞI")}</span><h2>${heading}</h2></div><ul>${rows}</ul>${
      blocked ? `<p>${t("Instagram molası veya doğrulama sürüyor. Otomatik tekrar yapılmaz; İstek bilgilerini incele.")}</p>` :
      data.requestBudget.nextAt > Date.now()/1000 ? `<p>${t("Bütçe için sonraki uygun zaman: {0}", esc(date(data.requestBudget.nextAt)))}</p>` :
      !paused && (!state.ready || working) && data.gate.nextAt > Date.now()/1000 ? `<p>${t("Sonraki istek için en erken saat: {0}", esc(new Date(data.gate.nextAt*1000).toLocaleTimeString(i18n.locale(language))))}</p>` :
      ''}<div class="drawer-actions">${
      button(paused ? t("Hazırlığa devam et") : t("Liste taramalarını duraklat"), paused ? 'preparation-resume' : 'preparation-pause', paused ? 'refresh' : 'clock', 'secondary', busy ? 'disabled' : '')}${
      button(t("Takip listelerini aç"), 'preparation-lists', 'list')}${blocked ? button(t("İstek bilgileri"),'requests','info') : ''}</div></section>`;
  }
  function listChangePreview(value, id) {
    if (value?.added == null) return `<p>${t("İlk kayıt bir başlangıçtır.")}</p>`;
    if (!Array.isArray(value?.membersAdded))
      return `<p>${t("Yeni görülen hesaplar")}: ${num(value?.added)} · ${t("Son kontrolde görünmeyenler")}: ${num(value?.removed)}</p><p class="field-hint">${t("Bu eski kayıtta kişi adları saklanmamış.")}</p>`;
    const preview = (members, label) => members.length ? `<p><strong>${esc(label)}</strong> ${members.slice(0, 3).map(p => '@' + esc(p.username)).join(', ')}${members.length > 3 ? ' · ' + t("ve {0} hesap daha", num(members.length - 3)) : ''}</p>` : '';
    return `<div class="list-change-preview">${preview(value.membersAdded,t("Yeni görülen hesaplar"))}${preview(value.membersRemoved,t("Son kontrolde görünmeyenler"))}${button(t("Değişen hesapları gör"), "list-changes", "people", "secondary", idattr(id))}</div>`;
  }
  function listChangesDialog(id, offset = 0, kind = 'all') {
    const event = data.events.find(e => e.id === id && e.type === 'list');
    const change = event ? {after:event.new, personId:event.personId, at:event.at} : data.notificationLog.flatMap(entry => entry.changes).find(c => c.eventId === id && c.category === 'lists');
    if (!change) { toast(t("Bu kayıt artık saklanan geçmişte yok.")); return; }
    const value=change.after, added=value?.membersAdded, removed=value?.membersRemoved;
    if (!Array.isArray(added) || !Array.isArray(removed)) {
      dialog(`<h2 id="dialog-title">${t("Liste değişiklikleri")}</h2>${listChangePreview(value,id)}`);return;
    }
    const visibleAdded=kind === 'removed' ? [] : added, visibleRemoved=kind === 'added' ? [] : removed;
    const size=100, total=visibleAdded.length+visibleRemoved.length;
    offset=Math.min(Math.max(0, offset), Math.max(0, Math.ceil(total/size)-1)*size);
    const rows=visibleAdded.slice(offset,offset+size).map(person => [person,true]);
    rows.push(...visibleRemoved.slice(Math.max(0,offset-visibleAdded.length),Math.max(0,offset+size-visibleAdded.length)).map(person => [person,false]));
    const ownerUsername=value.ownerUsername || person(change.personId)?.username;
    dialog(`<span class="dialog-symbol lavender">${icon("people")}</span><h2 id="dialog-title">${t("Liste değişiklikleri")}</h2><p>${ownerUsername ? '@'+esc(ownerUsername)+' · ' : ''}${value.direction === 'followers' ? t("Takipçiler") : t("Takip edilen")}</p><p>${esc(date(value.previousAt))} → ${esc(date(change.at))}</p><div class="compare-summary list-change-filters"><button class="compare-stat mint" data-action="list-change-filter" ${idattr(id)} data-value="added" aria-pressed="${kind === 'added'}"><strong>${num(added.length)}</strong><span>${t("Yeni görülen hesaplar")}</span></button><button class="compare-stat peach" data-action="list-change-filter" ${idattr(id)} data-value="removed" aria-pressed="${kind === 'removed'}"><strong>${num(removed.length)}</strong><span>${t("Son kontrolde görünmeyenler")}</span></button></div><button class="text-button" data-action="list-change-filter" ${idattr(id)} data-value="all" aria-pressed="${kind === 'all'}">${t("Tümü")}</button><div class="list-change-pagination"><span>${t("{0}–{1} / {2} hesap",num(total ? offset+1 : 0),num(Math.min(offset+size,total)),num(total))}</span>${button(t("Önceki sayfa"),'list-change-page',null,'secondary',`${idattr(id)} data-value="${offset-size}" data-kind="${kind}" ${offset ? '' : 'disabled'}`)}${button(t("Sonraki sayfa"),'list-change-page',null,'secondary',`${idattr(id)} data-value="${offset+size}" data-kind="${kind}" ${offset+size < total ? '' : 'disabled'}`)}</div><ol class="member-change-list">${rows.map(([p,isAdded])=>`<li><span><strong>@${esc(p.username)}</strong><small>${esc(p.name || '')}</small></span><span class="badge ${isAdded ? 'mint' : 'peach'}">${isAdded ? t("Yeni görülen hesaplar") : t("Son kontrolde görünmeyenler")}</span></li>`).join('')}</ol>`,true);
  }
  function overview() {
    const own = data.account,
      history = data.history[own?.id] || [],
      previous = history.at(-2);
    const stat = (label, key, tone) =>
      `<button class="stat-card ${tone}" data-action="open-list" data-value="${key}"><span class="stat-label">${t(label)} ${icon("people")}</span><strong class="stat-value">${num(own?.[key])}</strong><span class="stat-bottom"><span>${previous && own ? t("{0}{1} son kontrole göre", own[key] - previous[key] > 0 ? "+" : "", num(own[key] - previous[key])) : t("İlk kayıt bir başlangıçtır.")}</span>${icon("arrow")}</span></button>`;
    const changes = data.events.filter((e) => e.type === "profile").length;
    return `${title(new Date().toLocaleDateString(i18n.locale(language), { dateStyle: "full" }), t("Çevrende neler oluyor?"), "", button(data.connected ? t("Şimdi kontrol et") : t("Hesabını bağla"), data.connected ? "refresh" : "account", "refresh", "primary", data.connected ? netDisabled() : ""))}
      ${!own ? `<section class="connect-banner card"><span class="dialog-symbol lavender">${icon("link")}</span><div><h2>${t("Önce hesabınla bağlantı kuralım.")}</h2><p>${t("Şifren kaydedilmez. Oturum ve gözlemler şifreli olarak bilgisayarında kalır.")}</p></div>${button(t("Hesabını bağla"), "account", "arrow", "primary")}</section>` : ""}
      <section class="stats-grid" aria-label="${t("Hesap özeti")}">${stat(t("Takipçiler"), "followers", "violet")}${stat(t("Takip edilen"), "following", "peach")}<button class="stat-card white" data-action="profile-events"><span class="stat-label">${t("Profil değişiklikleri {0}", icon("photo"))}</span><strong class="stat-value">${num(changes)}<span class="stat-unit">${t("kayıtlı fark")}</span></strong><span class="stat-bottom"><span class="muted">${t("Kontroller arasında görülen değişiklikler.")}</span>${icon("arrow")}</span></button></section>
      ${preparationCard()}<div class="overview-columns"><div class="main-column">${chart(own)}<section class="activity-section"><div class="section-heading"><h2>${t("Son değişiklikler ve kontroller")}</h2><a class="text-button" href="#timeline">${t("Tümünü gör {0}", icon("arrow"))}</a></div><div class="activity-list">${data.events.slice(0, 5).map(eventRow).join("") || empty(t("İlk kontrolünden sonra kayıtlar burada görünür."))}</div></section></div><aside class="insights-column"><section class="snapshot-card"><div class="snapshot-top"><span class="tiny-icon">${icon("clock")}</span><span>${t("SON PROFİL KONTROLÜ")}</span></div><h2>${own ? "@" + esc(own.username) : t("Henüz bağlantı yok")}</h2><p>${esc(date(own?.observedAt))}</p><div class="snapshot-rule"></div>${button(t("İstek bilgileri"), "requests", "info")}</section><section class="favorites-section"><div class="section-heading"><h2>${t("Favorilerin")}</h2><a href="#people" class="text-button">${t("Kaydedilen Profiller {0}", icon("arrow"))}</a></div>${data.people
          .filter((p) => p.favorite)
          .slice(0, 5)
          .map(
            (p) =>
              `<button class="person-row" data-action="person" ${idattr(p.id)}>${avatar(p)}<span class="person-ident"><strong>${esc(p.name || p.username)}</strong><small>@${esc(p.username)}</small></span><span class="row-number">${num(p.followers)}</span>${icon("chevron")}</button>`,
          )
          .join("") ||
        `<p>${t("Kaydedilen Profiller bölümünde yıldızladığın hesaplar burada görünür.")}</p>`}</section></aside></div>`;
  }
  function profileChoices(users,label) {
    return `<h3>${esc(label)}</h3>${users.length ? users.slice(0,20).map(p=>`<button type="button" class="person-row" data-action="choose-profile" ${idattr(p.id)} data-username="${esc(p.username)}">${avatar(p)}<span class="person-ident"><strong>${esc(p.name || p.username)}</strong><small>@${esc(p.username)}</small></span>${icon('plus')}</button>`).join('') : `<p class="field-hint">${t("Eşleşen profil bulunamadı.")}</p>`}`;
  }
  function localProfileChoices(query) {
    return data.people.filter(p=>window.TrackerProfileSearch.matches((p.name || '')+' '+p.username,query));
  }
  function addPersonDialog(query='',users=null,error='') {
    dialog(`<span class="dialog-symbol lavender">${icon('search')}</span><h2 id="dialog-title">${t("Bir profil ekle.")}</h2><form id="add-person-form"><label class="form-label" for="new-username">${t("Ad, kullanıcı adı veya profil bağlantısı")}</label><input id="new-username" name="username" required maxlength="256" value="${esc(query)}" autocomplete="off"><div class="drawer-actions"><button class="button primary" type="submit">${t("Instagram’da ara")}</button>${button(t("Kullanıcı adıyla ekle"),'direct-add-person','plus')}</div></form>${error ? `<p role="status" class="notice warning">${esc(error)}</p>` : ''}<div id="profile-search-results">${profileChoices(users || localProfileChoices(query),users ? t("Instagram sonuçları") : t("Kayıtlı eşleşmeler"))}</div>`);
    const field=document.getElementById('new-username'), container=document.getElementById('profile-search-results');
    const note=document.createElement('p');note.className='field-hint';note.setAttribute('role','status');
    field.after(note);
    const owner=data.activeAccountId, modal=field.closest('dialog');
    let imageRevision=0,photosPending=false;
    const cancelImages=()=>{
      imageRevision++;
      if(photosPending)api.command('cancel_search_images').catch(()=>{});
      photosPending=false;
    };
    profileSearch=window.TrackerProfileSearch.live({
      cancelImages,
      availability:()=>({busy,wait:Math.max(0,(data.gate.nextAt || 0)*1000-Date.now()),
        stop:!data.connected ? {code:'connection'} : (data.gate.blockedUntil || 0)*1000>Date.now() ? {code:'cooldown'} :
          (data.requestBudget.nextAt || 0)*1000>Date.now() ? {code:'budget'} : null}),
      local:raw=>{container.innerHTML=profileChoices(localProfileChoices(raw),t("Kayıtlı eşleşmeler"));note.textContent='';},
      status:(phase,value)=>{note.textContent=phase==='loading' ? t("Profiller aranıyor…") : phase==='waiting' ? t("Son arama {0} saniye sonra yapılacak.",value) :
        phase==='busy' ? t("Diğer işlem tamamlanınca aranacak.") : phase==='error' ? userError(value) : '';note.classList.toggle('warning',phase==='error');note.classList.toggle('notice',phase==='error');},
      search:async q=>{
        const response=await command('search_people',{query:q},'',false);
        return {...response,users:response?.state?.searchResults?.users || [],token:response?.state?.searchResults?.token};
      },
      results:response=>{
        if(!modal.open || data.activeAccountId!==owner)return;
        container.innerHTML=profileChoices(response.users,t("Instagram sonuçları"));
        if(!response.token)return;
        const ticket=imageRevision;photosPending=true;
        Promise.all(response.users.map(async p=>{
          if(p.avatar)return;
          const image=await api.command('search_avatar',{token:response.token,id:p.id}).catch(()=>null);
          if(ticket!==imageRevision || !modal.open || data.activeAccountId!==owner || !image?.avatar)return;
          p.avatar=image.avatar;
          const row=[...container.querySelectorAll('[data-action="choose-profile"]')].find(row=>row.dataset.id===p.id);
          if(row)row.querySelector('.avatar').outerHTML=avatar(p);
        })).finally(()=>{if(ticket===imageRevision)photosPending=false;});
      },
    });
    field.focus();profileSearch.input(query);
  }
  async function addByInput() {
    const field=document.getElementById('new-username');
    const username=window.TrackerProfileSearch.username(field.value);
    if(!username) {field.setCustomValidity(t("Geçerli bir Instagram kullanıcı adı gir."));field.reportValidity();return;}
    closeDialog();await command('add_person',{username},t("Profil kaydedildi."));
  }
  function filteredPeople() {
    return data.people.filter(p =>
      (ui.filter !== 'favorites' || p.favorite) &&
      (ui.filter !== 'watching' || p.watching) &&
      window.TrackerProfileSearch.matches([p.name,p.username,p.group].filter(Boolean).join(' '),ui.query)
    ).sort((a,b)=>Number(!!b.favorite)-Number(!!a.favorite));
  }

  function personCard(p) {
    return `<article class="person-card card"><div class="person-card-top"><button class="avatar-button" data-action="person" ${idattr(p.id)} aria-label="${t("{0} kaydını aç", esc(p.name || p.username))}">${avatar(p, true)}</button><button class="icon-button star-button ${p.favorite ? "is-favorite" : ""}" data-action="star" ${idattr(p.id)} aria-label="${t("{0} favori durumunu değiştir", esc(p.username))}" aria-pressed="${!!p.favorite}" ${busy ? "disabled" : ""}>${icon("star")}</button></div><button class="person-title" data-action="person" ${idattr(p.id)}><h2>${esc(p.name || p.username)}</h2><p>@${esc(p.username)}</p></button><p class="person-bio">${esc(p.bio || t("Henüz kayıtlı biyografi yok."))}</p><div class="person-stats"><span><strong>${num(p.followers)}</strong><small>${t("takipçi")}</small></span><span><strong>${num(p.following)}</strong><small>${t("takip edilen")}</small></span></div><div class="person-card-bottom"><span class="group-label">${esc(p.group || t("Grupsuz"))}</span><span class="watch-label ${p.watching ? "on" : ""}">${icon(p.watching ? "bell" : "clock")}${p.watching ? t("İzlenen") : t("Kayıtlı")}</span></div></article>`;
  }
  function people() {
    return `${title(t("KAYDEDİLEN PROFİLLER"), t("Kaydettiğin profiller bir arada."), "", button(t("Profil ekle"), "add-person", "plus", "primary", netDisabled()))}<div class="toolbar"><div class="filter-tabs" role="group" aria-label="${t("Profil filtresi")}">${[
      ["all", t("Tümü")],
      ["favorites", t("Favoriler")],
      ["watching", t("İzlenenler")],
    ]
      .map(
        ([k, l]) =>
          `<button data-action="people-filter" data-value="${k}" class="${ui.filter === k ? "active" : ""}" aria-pressed="${ui.filter === k}">${l}</button>`,
      )
      .join(
        "",
      )}</div><label class="search-input">${icon("search")}<input id="people-search" type="search" placeholder="${t("İsim, kullanıcı adı veya grup ara")}" aria-label="${t("Kaydedilen profillerde ara")}" value="${esc(ui.query)}"></label></div><div class="results-line"><span id="people-result-count">${t("{0} kayıtlı profil", filteredPeople().length)}</span><span>${t("Favoriler önce gösterilir")}</span></div><div class="people-grid" id="people-grid">${filteredPeople().map(personCard).join("") || peopleEmpty()}</div>`;
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
    const shown=current || record.retainedPartial || record.draft;
    let entries = Object.values(
      shown?.members || {},
    ).map((p) => ({
      ...p,
      photoScan:shown.id,
      change: previous && !previous.members[p.id] ? t("Yeni görüldü") : "",
    }));
    if (ui.comparison && comparable) {
      entries = [
        ...Object.values(current.members)
          .filter((p) => !previous.members[p.id])
          .map((p) => ({ ...p, photoScan:current.id, change: t("Yeni görüldü") })),
        ...Object.values(previous.members)
          .filter((p) => !current.members[p.id])
          .map((p) => ({ ...p, photoScan:previous.id, change: t("Son kayıtta yok") })),
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
    const q = ui.listQuery.toLocaleLowerCase(i18n.locale(language));
    return entries.filter((p) =>
      `${p.name || ""} ${p.username}`.toLocaleLowerCase(i18n.locale(language)).includes(q),
    );
  }
  function listRows() {
    const entries = listEntries();
    return entries.length
      ? entries
          .slice(0, ui.pageSize)
          .map((p) => {
            const saved = data.people.find((x) => x.id === p.id);
            return `<div class="list-row"><button class="list-person" data-action="person" ${idattr(p.id)} data-photo-scan="${esc(p.photoScan)}">${avatar(saved?.avatar ? {...p,avatar:saved.avatar} : p)}<span><strong>${esc(p.name || p.username)}</strong><small>@${esc(p.username)}</small></span></button><span class="list-followers">${num(saved?.followers)}<small> ${t("takipçi")}</small></span><span class="list-state">${p.change ? `<span class="badge ${p.change === t("Son kayıtta yok") ? "peach" : "mint"}">${p.change}</span>` : `<span class="subtle-status">${t("Kayıtlı")}</span>`}</span><button class="icon-button star-button ${saved?.favorite ? "is-favorite" : ""}" data-action="star" ${idattr(p.id)} aria-label="${t("{0} favori durumunu değiştir", esc(p.username))}" aria-pressed="${!!saved?.favorite}" ${busy ? "disabled" : ""}>${icon("star")}</button></div>`;
          })
          .join("")
      : empty(t("Bir liste taraması başlat veya arama ve filtrelerini değiştir."));
  }
  function listPagination() {
    return listEntries().length > ui.pageSize
      ? button(t("Daha fazla göster"), "more-list", "down")
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
            `<option value="${esc(s.id)}" ${s.id === selected?.id ? "selected" : ""}>${t("{0} · {1} hesap", esc(date(s.finishedAt)), num(Object.keys(s.members).length))}</option>`,
        )
        .join("");
    return `${title(t("TAKİP LİSTELERİ"), t("Takipçiler ve takip edilenler"), "", button(record.canResume ? t("Taramaya devam et") : current || record.draft ? t("Listeyi yeniden kontrol et") : t("Listeyi kontrol et"), "scan", "refresh", "primary", netDisabled()))}

      <section class="list-account card"><label class="form-label" for="list-owner">${t("Liste sahibi")}</label><select id="list-owner" aria-label="${t("Liste sahibi")}">${[
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
        )}</select>${button(t("CSV dışa aktar"), "export-list", "note", "secondary", !current ? "disabled" : "")}</section>
      <div class="toolbar"><div class="filter-tabs" role="group" aria-label="${t("Liste yönü")}">${[
        ["followers", t("Takipçiler")],
        ["following", t("Takip edilen")],
      ]
        .map(
          ([k, l]) =>
            `<button data-action="list-tab" data-value="${k}" class="${ui.direction === k ? "active" : ""}" aria-pressed="${ui.direction === k}">${l}</button>`,
        )
        .join(
          "",
        )}</div><label class="search-input">${icon("search")}<input id="list-search" type="search" placeholder="${t("Listede ara")}" aria-label="${t("Listede ara")}" value="${esc(ui.listQuery)}"></label></div>
      <section class="list-coverage card"><strong>${current ? t("Tamamlanan kayıt: ") + num(Object.keys(current.members).length) + t(" hesap") : t("Listenin tamamı henüz okunmadı")}</strong><p>${current ? t("Son tarama: ") + esc(date(current.startedAt)) + " – " + esc(date(current.finishedAt)) : t("Farkları göstermek için iki tamamlanmış liste gerekiyor.")}</p>${record.draft ? `<div class="notice warning"><strong>${t("Liste henüz tamamlanmadı · {0} hesap kaydedildi", num(Object.keys(record.draft.members).length))}</strong><p>${esc(date(record.draft.finishedAt || record.draft.startedAt))}. ${record.draft.status === "running" ? t("Tarama sürüyor.") : record.canResume ? t("Taramaya devam et") : t("Önceki tarama tamamlanmadı. Yeni tarama baştan başlar.")} ${current ? t("Önceki tamamlanmış listen duruyor.") : t("Şimdilik yalnız kaydedilen hesaplar gösteriliyor.")}</p></div>` : ""}</section>
      ${!current && record.retainedPartial ? `<div class="notice"><strong>${t("Önceki yarım listeden {0} hesap gösteriliyor; yeni tarama tamamlanınca güncellenecek.",num(Object.keys(record.retainedPartial.members).length))}</strong><p>${esc(date(record.retainedPartial.finishedAt || record.retainedPartial.startedAt))}</p></div>` : ""}<div class="comparison-controls card"><label>${t("Önceki kontrol")}<select id="previous-scan" ${scans.length < 2 ? "disabled" : ""}>${scanOptions(previous)}</select></label><label>${t("Yeni kontrol")}<select id="current-scan" ${!scans.length ? "disabled" : ""}>${scanOptions(current)}</select></label>${button(ui.comparison ? t("Listeye dön") : t("Farkları göster"), "compare", "compare", "secondary", !ready ? "disabled" : "")}</div>
      ${ui.comparison && ready ? `<div class="compare-summary"><div class="compare-stat mint"><strong>${num(added)}</strong><span>${ui.direction === "followers" ? t("Yeni görülen takipçiler") : t("Yeni takip edilen hesaplar")}</span></div><div class="compare-stat peach"><strong>${num(removed)}</strong><span>${t("Son kontrolde görünmeyenler")}</span></div></div>` : ""}
      <div class="toolbar"><label class="inline-label">${t("İlişki filtresi")}<select id="relation-filter" ${!current || !other || ui.comparison || current !== scans.at(-1) ? "disabled" : ""}><option value="all" ${ui.listFilter === "all" ? "selected" : ""}>${t("Tümü")}</option><option value="mutual" ${ui.listFilter === "mutual" ? "selected" : ""}>${t("İki listede de var")}</option><option value="oneway" ${ui.listFilter === "oneway" ? "selected" : ""}>${ui.direction === "following" ? t("Seni geri takip etmeyenler") : t("Senin geri takip etmediklerin")}</option></select></label></div><div class="results-line"><span id="list-result-count">${t("{0} hesap · En fazla {1} satır gösteriliyor", listEntries().length, ui.pageSize)}</span></div><div class="list-table card" id="list-rows">${listRows()}</div><div id="list-pagination">${listPagination()}</div>${accessOverview()}`;
  }
  function accessLabel(c) {
    const last = c.checks.at(-1);
    return (
      {
        pending: t("İnceleme sırasında"),
        checking: t("Kontrol ediliyor"),
        suspected: last?.reason === "anonymous_visible" ? t("Güçlü engellenme şüphesi") : t("Engellenme şüphesi"),
        accessible: last?.usernameChanged
          ? t("Kullanıcı adı değişmiş; profil açılıyor")
          : t("Profil açılıyor"),
        unavailable: last?.reason === "both_unavailable" ? t("İki kontrolde de bulunamadı; nedeni belirsiz") : last?.reason === "anonymous_visible" ? t("Oturumsuz görünür; bağlı oturumdan erişilemiyor") : t("Profil açılmıyor; nedeni bilinmiyor"),
        you_blocked: t("Bu hesaba sizin engel uyguladığınız görünüyor"),
        unknown: last?.reason === "anonymous_inconclusive" ? t("Anonim kontrol tamamlanamadı") : t("Sonuç belirsiz"),
        present: t("Son takipçi kaydında yeniden görüldü"),
      }[c.status] || t("Henüz kontrol edilmedi")
    );
  }
  function anonymousPanel(evidence) {
    if (!evidence) return `<p class="dialog-note">${t("Bu kayıtta anonim kontrol sonucu yok; değerlendirme bağlı oturum kontrolüyle sınırlı.")}</p>`;
    const reasons = {
      identity_match: t("Aynı sayısal hesap kimliği oturumsuz sayfada doğrulandı."),
      identity_mismatch: t("Bu kullanıcı adı altında farklı bir hesap kimliği görüldü; aynı kişi olduğu doğrulanamadı."),
      identity_unverified: t("Sayfa yanıt verdi fakat aynı hesap kimliği doğrulanamadı. HTTP 200 tek başına profil bulundu anlamına gelmez."),
      http_not_found: t("Profil adresi oturumsuz kontrolde de bulunamadı. Hesap dondurulmuş, silinmiş veya kullanıcı adı değiştirilmiş olabilir; ad değişikliğiyle birlikte engelleme de ayırt edilemez."),
      login_required: t("Instagram giriş ekranı istedi; profil yok sayılmadı."),
      verification: t("Instagram doğrulama istedi; profil yok sayılmadı."),
      limited: t("Instagram anonim isteği sınırladı. Mevcut mola korunur; otomatik tekrar yapılmaz."),
      cooldown: t("Yerel mola nedeniyle anonim istek gönderilmedi."),
      connection: t("Bağlantı veya zaman aşımı nedeniyle anonim kontrol tamamlanamadı."),
      server_error: t("Sunucu hatası nedeniyle anonim kontrol tamamlanamadı."),
      access_denied: t("Oturumsuz erişim reddedildi; profil yok sayılmadı."),
      redirect: t("Sayfa yönlendirme istedi; yönlendirme otomatik izlenmedi."),
      body_limit: t("Yanıt boyutu veya inceleme süresi sınırına ulaşıldı; sonuç belirsiz."),
      content_type: t("Beklenen profil sayfası alınamadı; sonuç belirsiz."),
      unrecognized: t("Yanıt profil kanıtı olarak doğrulanamadı."),
    };
    return `<section class="anonymous-evidence"><h4>${t("Oturumsuz karşılaştırma")}</h4><p>${t("Son bilinen kullanıcı adına ayrı, hesap çerezi ve Authorization taşımayan tek profil sayfası isteği yapılır. Üçüncü taraf servis kullanılmaz; Instagram IP adresini görebilir.")}</p><div class="detail-pair"><span>${t("Kontrol edilen kullanıcı adı")}</span><strong>@${esc(evidence.requestedUsername)}</strong></div><div class="detail-pair"><span>${t("Anonim kontrol HTTP")}</span><strong>${evidence.http || t("İstek gönderilmedi veya yanıt alınamadı")}</strong></div><div class="detail-pair"><span>${t("Anonim kontrol zamanı")}</span><strong>${esc(date(evidence.checkedAt))}</strong></div><p>${esc(reasons[evidence.reason] || t("Yanıt profil kanıtı olarak doğrulanamadı."))}</p>${evidence.observedUsername ? `<p>${t("Oturumsuz görülen kullanıcı adı: {0}", "@" + esc(evidence.observedUsername))}</p>` : ""}<p class="field-hint">${t("Kullanıcı adı değişikliği ile engelleme aynı anda olduysa yeni adres bulunamayabilir. Bu özellik kesin engelleme, dondurma veya silinme tespiti değildir.")}</p></section>`;
  }
  function accessPanel(id, assessment = null) {
    const latest = data.access?.find((c) => c.id === id);
    const c = assessment
      ? {
          ...latest,
          id,
          checks: [assessment],
          status: assessment.status,
          absence: assessment.absence || null,
        }
      : latest;
    if (!c)
      return `<p class="field-hint">${t("Henüz erişim kontrolü kaydı yok. Kontrol, kendi oturumunun sağlığını ve kayıtlı hesap kimliğini aynı oturumda inceler.")}</p>`;
    const last = c.checks.at(-1),
      absence = c.absence;
    const reasons = {
      connection: t("Bağlantı veya zaman aşımı sorunu"),
      access_denied: t("Erişim izni alınamadı"),
      server_error: t("Sunucu hatası"),
      invalid_response: t("Yanıt doğrulanamadı"),
      identity: t("Hedef hesap kimliği uyuşmadı"),
      limited: t("Instagram molası"),
      verification: t("Instagram doğrulaması gerekli"),
      session: t("Oturum kabul edilmedi"),
      cancelled: t("Kontrol iptal edildi"),
      interrupted: t("Önceki kontrol yarıda kaldı"),
      access_unknown: t("Oturum sağlığı doğrulanamadı"),
    };
    return `<section class="access-evidence"><h3>${esc(accessLabel(c))}</h3><p>${t("Son değerlendirme: {0}", esc(date(last?.checkedAt)))}</p><div class="detail-pair"><span>${t("Önceki takipçi kaydında vardı")}</span><strong>${absence ? esc(date(absence.previousAt)) : t("Karşılaştırılabilir kanıt yok")}</strong></div><div class="detail-pair"><span>${t("Son tam takipçi kaydında yok")}</span><strong>${absence ? esc(date(absence.currentAt)) : t("Doğrulanmadı")}</strong></div><details><summary>${t("Teknik ayrıntılar")}</summary><div class="detail-pair"><span>${t("Oturum sağlığı HTTP")}</span><strong>${last?.ownHTTP || t("Bilinmiyor")}</strong></div><div class="detail-pair"><span>${t("Hedef profil HTTP")}</span><strong>${last?.targetHTTP || t("Bilinmiyor")}</strong></div></details>${last?.username ? `<p>${t("Kontrolde görülen kullanıcı adı:")} <strong>@${esc(last.username)}</strong></p>` : ""}${reasons[last?.reason] ? `<p>${t("{0}. Bu sonuç engellenme sayılmaz.", esc(reasons[last.reason]))}</p>` : ""}${anonymousPanel(last?.anonymous)}<p class="dialog-note">${t("Bu kayıt kesin engelleme kanıtı değildir. Hesabın kapanması/silinmesi veya sizin uyguladığınız bir engel de erişimi etkileyebilir. Kontrol zamanı, olası engellemenin gerçekleştiği zaman değildir.")}</p></section>`;
  }
  function accessOverview() {
    if (
      (ui.owner || data.account?.id) !== data.account?.id ||
      ui.direction !== "followers"
    )
      return "";
    const cases = data.access || [],
      plan = data.accessPlan || {};
    const message = {
      pending: t("Takipçi sayısındaki düşüş sonrası tam liste taraması sırada."),
      baseline_needed:
        t("Kişi belirlemek için önce tamamlanmış bir başlangıç takipçi listesi gerekiyor."),
      needs_manual_scan:
        t("Önceki liste incelemesi tamamlanmadı. Listeyi yeniden kontrol et ile baştan başlatabilirsin."),
    }[plan.status];
    return `<section class="access-review card"><div class="section-heading"><div><h2>${t("Engellenme şüphesi ve profil görünürlüğü")}</h2><p>${t("Yalnız kayıtlara dayalı olasılık; kesin engelleme listesi değildir.")}</p></div>${button(t("Profil kontrol geçmişi"), "access-history", "shield")}</div>${message ? `<div class="notice warning">${esc(stored(message))}</div>` : ""}<p>${t("Otomatik inceleme: {0}. Tam liste karşılaştırmasında eksilen kayıtlı kişiler, kendi oturumunun sağlığı doğrulandıktan sonra hesap kimliğiyle incelenir.", data.settings.investigateMissing ? t("Açık") : t("Kapalı · Ayarlar’dan açabilirsin"))}</p><div class="access-case-list">${cases.map((c) => `<div class="access-case"><div><strong>@${esc(c.username)}</strong><p>${esc(accessLabel(c))}</p><small>${esc(date(c.checks.at(-1)?.checkedAt || c.detectedAt))}</small></div>${button(t("Kanıtları gör"), "person", "info", "secondary", idattr(c.id))}${button(t("Profil açılabiliyor mu?"), "check-access", "shield", "secondary", `${idattr(c.id)} ${netDisabled()}`)}</div>`).join("") || `<p class="field-hint">${t("Henüz inceleme kaydı yok. İlk liste kaydı başlangıçtır; tek başına takipçi sayısının azalması engellenme sayılmaz.")}</p>`}</div><p class="field-hint">${t("İnceleme önce bağlı oturumu, sonra hedefi kontrol eder. Hedef bulunamazsa ek bir oturumsuz sayfa isteği yapılır. İstekler arasında {0}–{1} saniyelik rastgele aralık ayrılır; gerçek bekleme daha uzun olabilir. Otomatik inceleme için Otomatik profil kontrolü de açık olmalı.", ...requestSpacing())}</p></section>`;
  }
  function timeline() {
    const events = data.events.filter(
      (e) =>
        ui.eventType === "all" ||
        (ui.eventType === "access"
          ? e.topic === "access"
          : e.type === ui.eventType &&
            (ui.eventType !== "profile" || e.topic !== "access")),
    );
    return `${title(t("KONTROL GEÇMİŞİ"), t("Neyin ne zaman kontrol edildiğini gör."), "")}<div class="toolbar"><div class="filter-tabs" role="group" aria-label="${t("Olay filtresi")}">${[
      ["all", t("Tümü")],
      ["profile", t("Profil farkları")],
      ["access", t("Profil görünürlüğü")],
      ["list", "Takip Listeleri"],
      ["check", t("Kontroller")],
      ["error", t("Hatalar")],
    ]
      .map(
        ([k, l]) =>
          `<button data-action="event-filter" data-value="${k}" class="${ui.eventType === k ? "active" : ""}" aria-pressed="${ui.eventType === k}">${l}</button>`,
      )
      .join(
        "",
      )}</div><span>${t("{0} kayıt", num(events.length))}</span></div><div class="timeline-layout timeline-focused"><section class="timeline-main"><div class="timeline-events card">${events.slice(0, ui.eventLimit).map(eventRow).join("") || empty(t("Seçilen türde henüz kayıt yok."))}</div>${events.length > ui.eventLimit ? button(t("Daha fazla göster"), "more-events", "down") : ""}</section></div>`;
  }
  function toggle(label, description, key) {
    return `<div class="setting-row"><div><strong>${esc(t(label))}</strong><p>${esc(description)}</p></div><button class="switch ${data.settings[key] ? "on" : ""}" role="switch" aria-checked="${!!data.settings[key]}" aria-label="${esc(t(label))}" data-action="setting" data-value="${key}" ${busy ? "disabled" : ""}><span></span></button></div>`;
  }
  function monitorSummary() {
    const monitor = data.monitor;
    if (!monitor) return "";
    const status = !monitor.enabled
      ? t("Otomatik kontrol kapalı.")
      : monitor.reason === "session"
        ? t("Hesap bağlantısı bekleniyor.")
        : monitor.reason === "restricted"
          ? t("Otomatik kontrol duraklatıldı; önce elle başarılı profil kontrolü gerekli.")
          : t("Sonraki kontrol için en erken: {0}.", date(monitor.nextAt));
    return `<div class="notice monitor-summary"><strong>${esc(status)}</strong><p>${t("Her profil kendi son kontrolünden yaklaşık {0} dakika sonra yeniden kontrol edilir. {1} profil izleniyor; profil sayısı seçilen aralıkla çarpılmaz. İstekler arasında {2}–{3} saniyelik rastgele aralık ayrılır. Çok sayıda profil, uzun liste taraması, internet ve mola beklemesi süreyi uzatabilir.", num(monitor.intervalSeconds / 60), num(monitor.profileCount), ...requestSpacing())}</p><p>${t("Normal profil sırasına ek olarak {0} erişim incelemesi bekliyor. {1}Kaybolan takipçi incelemesi açıksa liste/erişim işleri sıraya girer; diğer tam liste taramaları elle başlatılır.", num(monitor.accessPending || 0), monitor.followerScanPending ? t("Takipçi listesi incelemesi de sırada. ") : "")}</p></div>`;
  }
  function settings() {
    return `${title(t("SANA GÖRE"), t("Küçük ayarlar, daha rahat bir deneyim."), t("Görünümü, bildirimleri ve yerel kayıtlarını yönet."))}<div class="settings-layout"><div><section class="settings-section card"><h2>${t("Görünüm ve kontroller")}</h2>${window.TrackerAppearance.control({t,disabled:busy})}<p>${t("Pencereyi X ile kapatınca Tracker sistem tepsisinde çalışmaya devam eder. Tamamen kapatmak için tepsi simgesine sağ tıkla → Tamamen çık.")}</p>${toggle(t("Kompakt görünüm"), t("Kart ve satır aralıklarını daralt."), "compact")}${toggle(t("Windows bildirimleri"), t("Yeni değişiklikler için Windows bildirimi göster."), "notifications")}${toggle(t("Kaybolan kayıtlı takipçileri incele"), t("Takipçi sayısı düşünce tamamlanmış başlangıç kaydı üzerinden liste taraması ve eksilen kayıtlı kişiler için erişim kontrolü sıraya alınır. Ek istekler ve bekleme gerektirir; sonuç yalnız engellenme şüphesidir. Hedef bulunamazsa son bilinen kullanıcı adına ek bir oturumsuz sayfa isteği yapılır."), "investigateMissing")}<div class="setting-row"><div><strong>${t("Uygulama dili")}</strong><p>${t("Bu ayar yalnız arayüz dilini değiştirir. Hesap ve kontrol ayarların korunur.")}</p></div><select id="app-language" aria-label="${t("Dil seçimi")}" ${busy ? "disabled" : ""}><option value="tr" ${language === "tr" ? "selected" : ""}>Türkçe</option><option value="en" ${language === "en" ? "selected" : ""}>English</option><option value="es" ${language === "es" ? "selected" : ""}>Español</option><option value="pt-BR" ${language === "pt-BR" ? "selected" : ""}>Português (Brasil)</option></select></div>${toggle(t("Otomatik profil kontrolü"), t("Sistem tepsisinde de çalışır; kendi hesabın ve izlediğin her profil seçilen aralıkla kontrol edilir. Kaybolan takipçi incelemesi ayrıca açılır."), "automatic")}<div class="setting-row"><div><strong>${t("Kontrol aralığı")}</strong><p>${t("Aralık her profil için ayrıdır. Örneğin 3 profil ve 5 dakika seçildiğinde her profil yaklaşık 5 dakikada bir kontrol edilir. Sınırlama ve doğrulama sonrası otomatik kontrol durur.")}</p></div><select id="auto-interval" aria-label="${t("Kontrol aralığı")}" ${busy ? "disabled" : ""}>${[1, 5, 15, 30, 60, 180, 360].map((v) => `<option value="${v}" ${data.settings.interval === v ? "selected" : ""}>${t("{0} dakika", v)}</option>`).join("")}</select></div>${toggle(t("Otomatik liste karşılaştırması"), t("Kendi hesabının ve izlenen profillerin listelerini karşılaştır. Sayı değişince tarama sıraya alınır; iki tam tarama arasında en az 1 dakika vardır. Sayı aynıysa günlük taranır."), "automaticLists")}${monitorSummary()}</section><section class="settings-section card"><h2>${t("Hesap ve istekler")}</h2>${data.account
        ? `<div class="setting-row"><div><strong>${t("Hesaba göre bildirim türleri")}</strong><p>${t("Kendi hesabın ve her kayıtlı kişi için ayrı tercihler. Kişi ayrıntısından da açabilirsin.")}</p></div><select id="notification-account" aria-label="${t("Bildirim hesabı")}">${[
            data.account,
            ...data.people,
          ]
            .filter(Boolean)
            .map(
              (p) =>
                `<option value="${esc(p.id)}">@${esc(p.username)}</option>`,
            )
            .join(
              "",
            )}</select>${button(t("Türleri seç"), "notification-account", "bell")}</div>`
        : ""}<div class="setting-row"><div><strong>${data.account ? "@" + esc(data.account.username) : t("Hesap bağlı değil")}</strong><p>${data.connected ? t("Yerel oturum kayıtlı.") : t("Giriş için hesap bağlantısını aç.")}</p></div>${button(t("Hesap bağlantısı"), "account", "link")}</div><div class="setting-row"><div><strong>${t("İstek bilgileri")}</strong><p>${t("Son HTTP durumu, giriş aralığı ve yerel mola.")}</p></div>${button(t("İncele"), "requests", "info")}</div>${data.connected ? button(t("Bağlantıyı kaldır"), "disconnect", "logout", "secondary", busy ? "disabled" : "") : ""}</section><section class="settings-section card"><h2>${t("Yerel veriler")}</h2><p>${t("Yedekler oturum içermez ve aynı Windows kullanıcısında açılır. CSV dışa aktarımları şifresizdir; dosyayı paylaşmak senin kontrolündedir.")}</p><div class="settings-buttons">${button(t("Şifreli yedek al"), "export-backup", "lock")}${button(t("Yedekten yükle"), "import-backup", "note")}${button(t("Kayıt klasörünü aç"), "open-storage", "laptop")}${button(t("Yerel verileri sil"), "delete-all", "close")}</div></section></div><aside class="privacy-panel"><span class="privacy-large">${icon("shield")}</span><span class="eyebrow">${t("YEREL GİZLİLİK")}</span><h2>${t("Verilerin bilgisayarında.")}</h2><p>${t("Oturum, profil gözlemleri, listeler ve notlar Windows DPAPI ile şifrelenir. Şifre ve doğrulama kodu kaydedilmez.")}</p><p>${t("Hesap işlemleri doğrudan Instagram’a gider. Aracı sunucu, reklam veya analiz servisi kullanılmaz.")}</p><p>${t("InstagramTracker, Instagram’dan bağımsızdır. Instagram erişimi sınırlayabilir veya ek doğrulama isteyebilir.")}</p><div class="privacy-panel-foot">${t("{0} Windows masaüstü · {1}", icon("lock"), esc(data.version))}</div></aside></div>`;
  }
  function render() {
    language = i18n.valid(data?.settings.language) ? data.settings.language : 'tr';
    document.documentElement.lang = language;
    if (data) window.TrackerAppearance.apply(data.settings.theme);
    document.querySelector('.skip-link').textContent = t("İçeriğe geç");
    if (!data) {
      document.getElementById("app").innerHTML =
        `<div class="startup card"><span class="dialog-symbol lavender">${icon("lock")}</span><h1>${t("InstagramTracker")}</h1><p>${esc(stored(serviceError) || t("Şifreli yerel kayıtlar açılıyor…"))}</p>${serviceError ? `<p>${t("Uygulamayı yeniden açabilirsin. Mevcut dosyalar otomatik silinmez.")}</p>` : ""}</div>`;
      operationPanel();
      return;
    }
    document.body.dataset.density = data.settings.compact
      ? "compact"
      : "comfortable";
    document.title = `${t(pages[ui.page][0])} — Tracker`;
    document.getElementById("app").innerHTML =
      `${sidebar()}<div class="workspace">${topbar()}<main id="main" tabindex="-1">${serviceError ? `<div class="notice warning">${esc(stored(serviceError))}</div>` : ""}${data.lastError ? `<div class="notice warning"><strong>${t("Son işlem:")}</strong> ${esc(userError(data.lastError))} <button class="text-button" data-action="requests">${t("İstek bilgileri")}</button></div>` : ""}${{ overview, people, lists, timeline, settings, notifications }[ui.page]()}<footer class="page-footer"><span>${t("{0} Cihazında şifreli · Son kontrol {1}", icon("lock"), esc(date(data.account?.observedAt)))}</span><div class="footer-links"><button class="text-button" data-action="usage-notes">${t("Kullanım notları")}</button><button class="text-button" data-action="privacy">${t("Gizlilik {0}", icon("info"))}</button></div></footer></main></div>`;
    refreshListPhotos();
    operationPanel();
  }
  function accountsDialog() {
    dialog(`<span class="dialog-symbol lavender">${icon("people")}</span><h2 id="dialog-title">${t("Hesapların")}</h2><p>${t("Her hesabın oturumu ve gözlemleri ayrı şifreli kasada tutulur. Hesap değiştirmek Instagram isteği göndermez.")}</p><div class="saved-accounts">${(data.accounts || []).map(a => `<button class="saved-account ${a.id === data.activeAccountId ? 'selected' : ''}" data-action="switch-account" data-id="${esc(a.id)}" aria-pressed="${a.id === data.activeAccountId}" ${busy ? 'disabled' : ''}>${avatar(a)}<span><strong>@${esc(a.username)}</strong><small>${a.connected && !['session','verification'].includes(a.lastError) ? a.monitor?.enabled ? t("Arka plan takibi açık") : t("Oturum kayıtlı · Takip kapalı") : t("Yeniden bağlantı gerekli")}${a.monitor?.reason === 'restricted' ? ' · ' + t("Kontrol duraklatıldı") : ''}</small></span>${a.id === data.activeAccountId ? icon("check") : icon("arrow")}</button>`).join('')}</div>${button(t("Hesap ekle"), "add-account", "plus", "primary", busy ? 'disabled' : '')}<p class="dialog-note">${t("Arka plan takibini her hesabın Ayarlar ekranından ayrı açabilirsin. İstek sırası ve molalar bütün hesaplar için ortaktır.")}</p>`);
  }
  function accountDialog(adding = false) {
    const own = adding ? null : data.account;
    const connected = !adding && data.connected;
    dialog(
      `<span class="dialog-symbol lavender">${icon("link")}</span><h2 id="dialog-title">${connected ? t("Hesap bağlantın") : t("Instagram hesabını bağla")}</h2><p>${t("{0}Şifre ve kod yalnız giriş işlemi sırasında kullanılır; dosyaya kaydedilmez.", own ? t("Kayıtlı hesap: @") + esc(own.username) + ". " : "")}</p>${connected ? `<p class="dialog-note">${t("Kayıtlı oturumla profil ve listeleri okuyabilirsin. Yeniden giriş yalnız oturum reddedilirse gerekir.")}</p>${button(t("Profili yenile"), "refresh", "refresh", "primary", netDisabled())}` : ""}<p class="dialog-note">${t("İlk bağlantıdan sonra başlangıç listelerin otomatik hazırlanır. Tarama ve sonraki kontrolleri ana ekrandan duraklatabilirsin.")}</p><form id="login-form"><label class="form-label" for="login-username">${t("Kullanıcı adı")}</label><input id="login-username" name="username" required maxlength="30" pattern="[A-Za-z0-9._]{1,30}" value="${esc(own?.username || "")}" autocomplete="username"><label class="form-label" for="login-password">${t("Şifre")}</label><input id="login-password" name="password" type="password" required maxlength="1024" autocomplete="off"><button class="button primary full-width" type="submit" ${busy ? "disabled" : ""}>${icon("link")} ${connected ? t("Yeniden bağla") : t("Hesabı bağla")}</button></form>${data.counterAvailable ? `<div class="import-connection"><p>${t("Önceki Windows prototipinde kayıtlı bir oturum bulundu. Bir kopyası bu uygulamaya alınır; eski dosya değiştirilmez. Hesap kimliği canlı profil yanıtıyla doğrulanır.")}</p>${button(t("Önceki Windows oturumunu kullan"), "import-counter", "laptop", "secondary", busy ? "disabled" : "")}</div>` : ""}<p class="field-hint">${t("Instagram ek güvenlik kontrolü isterse resmî uygulamada tamamla. Otomatik parola tekrarı yapılmaz.")}</p>`,
    );
  }
  function requestsDialog() {
    const g = data.gate;
    const budget = data.requestBudget;
    dialog(
      `<span class="dialog-symbol lavender">${icon("info")}</span><h2 id="dialog-title">${t("İstek bilgileri")}</h2><p class="dialog-note">${t("Yerel istek bütçesi: bu hesap saatte {0}, 24 saatte {1}; bütün hesaplar saatte {2}, 24 saatte {3}. Bunlar Meta kotası değildir.", num(budget.accountHourly), num(budget.accountDaily), num(budget.sharedHourly), num(budget.sharedDaily))}</p>${budget.nextAt > Date.now()/1000 ? `<p>${t("Bütçe için sonraki uygun zaman: {0}", esc(date(budget.nextAt)))}</p>` : ""}<div class="detail-pair"><span>${t("Son HTTP")}</span><strong>${g.status || t("Bilinmiyor")}</strong></div>${data.lastError ? `<details><summary>${t("Son hatanın teknik ayrıntıları")}</summary><p>${esc(data.lastError.code)}</p><p>${esc(data.lastError.message)}</p></details>` : ""}<div class="detail-pair"><span>${t("İstek sayısı")}</span><strong>${num(g.attempts || 0)}</strong></div><div class="detail-pair"><span>${t("Son istek")}</span><strong>${esc(date(g.lastAt))}</strong></div><div class="detail-pair"><span>${t("Yerel mola bitişi")}</span><strong>${esc(date(g.blockedUntil))}</strong></div><div class="detail-pair"><span>${t("Sunucunun Retry-After zamanı")}</span><strong>${g.serverRetryAt ? esc(date(g.serverRetryAt)) : t("Belirtilmedi")}</strong></div><div class="detail-pair"><span>${t("Sonraki parola girişi")}</span><strong>${esc(date(g.loginAfter))}</strong></div><p class="dialog-note">${t("İstekler arasında {0}–{1} saniyelik rastgele aralık ayrılır; gerçek bekleme daha uzun olabilir. Parola girişleri arasında en az 15 dakika beklenir. HTTP 429 sonrası yerel mola 1–24 saattir; sunucunun daha uzun bekleme süresi korunur. Mola bitişi erişim garantisi değildir.", ...requestSpacing())}</p><p>${t("Otomatik kontrol: {0}", g.manualOnly ? t("Duraklatıldı; önce elle başarılı bir profil kontrolü gerekli.") : data.settings.automatic ? t("Açık.") : t("Kapalı."))}</p>`,
    );
  }
  function personInformation(view) {
    const p=view.profile,counts=[['followers','takipçi'],['following','takip edilen']].filter(([key])=>Number.isFinite(p[key]) && p[key]>=0);
    const facts=[];
    if(Number.isFinite(p.observedAt))facts.push([t("Son kontrol"),date(p.observedAt)]);
    if(typeof p.private==='boolean')facts.push([t("Gizlilik"),p.private?t("Gizli"):t("Açık")]);
    if(Number.isFinite(p.posts) && p.posts>=0)facts.push([t("Gönderi"),num(p.posts)]);
    return `<div class="drawer-heading">${avatar(view.avatar ? {...p,avatar:view.avatar} : p,true)}</div><h2 id="dialog-title" class="profile-title">${esc(p.name || p.username)}</h2><p class="profile-handle">@${esc(p.username)}</p>${typeof p.bio==='string' && p.bio.trim() ? `<p class="drawer-bio">${esc(p.bio)}</p>` : ''}${personLinks(view)}${counts.length ? `<div class="drawer-counts">${counts.map(([key,label])=>`<div><strong>${num(p[key])}</strong><span>${t(label)}</span></div>`).join('')}</div>` : ''}${facts.length ? `<section class="drawer-section">${facts.map(([label,value])=>`<div class="detail-pair"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`).join('')}</section>` : ''}`;
  }
  function personLink(url,label,brand) {
    return `<button type="button" class="profile-site-link" data-action="open-profile-link" data-url="${esc(url)}" title="${esc(url)}" aria-label="${esc(t("{0} bağlantısını aç",label))}">${window.TrackerLinkIcon(brand)}<span>${esc(label)}</span>${icon('arrow')}</button>`;
  }
  function personLinks(view) {
    const links=window.TrackerProfileLinks.links(view.profile);
    const sites=links.websites.map(link=>personLink(link.url,link.name,link.icon)).join('');
    const social=(links.instagram ? personLink(links.instagram,t("Instagram’da aç"),'instagram') : '')+
      (links.threads ? personLink(links.threads,t("Threads’te aç"),'threads') : '');
    return `<div class="profile-links">${sites}${social}</div>`;
  }
  async function openProfileLink(url) {
    if(!window.TrackerProfileLinks.webUrl(url))return;
    try {await api.openLink(url);}catch{toast(t("Bağlantı açılamadı."));}
  }
  function receivePersonPhoto(source,image) {
    const view=personView;
    if(!view?.source || !['accountId','ownerId','direction','scanId','id'].every(key=>view.source[key]===source[key]))return;
    view.avatar=image;
    const node=document.querySelector('#person-profile-info .avatar');
    if(node)node.outerHTML=avatar({...view.profile,avatar:image},true);
  }
  function personDialog(id,carry={}) {
    const p=person(id);if(!p)return;
    const previous=personView?.id===id && personView.accountId===data.activeAccountId ? personView : null;
    const source=carry.source || previous?.source || null;
    const view={id,accountId:data.activeAccountId,profile:p,source,avatar:carry.avatar || previous?.avatar || (source ? listPhotos.peek(source) : null)};
    const own=id===data.account?.id,saved=data.people.find(person=>person.id===id) || p;
    dialog(`<div id="person-profile-info">${personInformation(view)}</div><p id="person-detail-status" class="field-hint" role="status"></p><div class="drawer-actions">${button(t("Profili kontrol et"),"refresh-person","refresh","primary",`${idattr(id)} ${netDisabled()}`)}${button(t("Geçmiş"),"history","clock","secondary",idattr(id))}${button(t("Bildirim tercihleri"),"notification-rules","bell","secondary",idattr(id))}${button(t("Takip listelerini aç"),"person-lists","list","secondary",idattr(id))}${!own ? button(t("Profil açılabiliyor mu?"),"check-access","shield","secondary",`${idattr(id)} ${netDisabled()}`) : ''}</div>${!own ? accessPanel(id) : ''}${!own ? `<section class="drawer-section"><div class="drawer-actions">${button(saved.favorite?t("Favoriden çıkar"):t("Favorilere ekle"),"drawer-star","star","secondary",`${idattr(id)} ${busy?'disabled':''}`)}${button(saved.watching?t("İzlemeyi kapat"):t("İzlemeye al"),"watch","bell","secondary",`${idattr(id)} ${busy?'disabled':''}`)}</div><p class="field-hint">${t("İzleme, Ayarlar’da otomatik kontrol açıksa pencere kapalıyken sistem tepsisinde de çalışır.")}</p><label class="form-label" for="person-group">${t("Grup")}</label><input id="person-group" maxlength="80" value="${esc(saved.group || '')}" placeholder="${t("Örn. Arkadaşlar")}"><label class="form-label" for="person-note">${t("Sana özel not")}</label><textarea id="person-note" maxlength="2000" rows="4" placeholder="${t("Bu kişiyle ilgili bir not…")}">${esc(saved.note || '')}</textarea><p class="field-hint">${t("Not ve grup şifreli yerel depoya kaydedilir.")}</p>${button(t("Notu ve grubu kaydet"),"save-note","check","secondary",`${idattr(id)} ${busy?'disabled':''}`)}${data.people.some(p=>p.id===id)?button(t("Kayıtlı profillerden kaldır"),"remove-person","close","secondary",`${idattr(id)} ${busy?'disabled':''}`):''}</section>`:''}`,false,true);
    drawerId=id;personView=view;
    if(own || data.people.some(p=>p.id===id))document.querySelector('dialog .drawer-actions')?.insertAdjacentHTML('beforeend',button(t("Profil zaman tüneli"),"wayback","photo","secondary",idattr(id)));
    if(source && !view.avatar)listPhotos.get(source).then(result=>{if(personView===view && result?.avatar)receivePersonPhoto(source,result.avatar);});
    profileDetails.open({id,accountId:view.accountId,known:p},state=>{
      if(personView!==view)return;
      if(state.profile) {
        view.profile=state.profile;
        document.getElementById('person-profile-info').innerHTML=personInformation(view);
      }
      const note=document.getElementById('person-detail-status');
      note.textContent=state.phase==='loading' ? t("Profil bilgileri getiriliyor…") : state.phase==='waiting' ? t("Profil bilgileri {0} saniye sonra okunacak.",state.seconds) : state.phase==='busy' ? t("Diğer işlem tamamlanınca profil okunacak.") : state.phase==='error' ? userError(state.error) || t("İşlem tamamlanamadı.") : '';
      note.classList.toggle('warning',state.phase==='error');
    });
  }
  function historyDialog(id) {
    const p = person(id),
      history = data.history[id] || [];
    dialog(
      `<span class="dialog-symbol lavender">${icon("chart")}</span><h2 id="dialog-title">${t("@{0} · Profil geçmişi", esc(p?.username || t("hesap")))}</h2><p>${t("Son {0} kontrol. Sayılar bu kontroller sırasında görülen değerlerdir.", history.length)}</p><div class="history-records">${[...history]
          .reverse()
          .map(
            (h) =>
              `<div><span>${esc(date(h.observedAt))}</span><strong>${t("{0} takipçi · {1} takip edilen", num(h.followers), num(h.following))}</strong></div>`,
          )
          .join("") || `<p>${t("Profil henüz kontrol edilmedi.")}</p>`}</div>`,
      true,
    );
  }
  async function waybackDialog(id, changes = {}) {
    const result = await command("wayback", {id, ...changes});
    if (!result?.ok || !data.waybackDetail) return;
    const archive = data.waybackDetail;
    dialog(`<span class="dialog-symbol lavender">${icon("clock")}</span><h2 id="dialog-title">${t("Profil zaman tüneli")}</h2><p>@${esc(person(id)?.username || "")}</p><div class="drawer-actions">${button(archive.enabled ? t("Arşivlemeyi durdur") : t("Arşivlemeyi aç"), "wayback-toggle", "clock", "primary", `${idattr(id)} data-value="${!archive.enabled}"`)}${button(t("Arşivi temizle"), "wayback-clear", "trash", "secondary", idattr(id))}</div>${window.TrackerWayback.render(archive, {t,date,esc})}<p class="dialog-note">${t("En fazla {0} değişiklik ve {1} küçük görsel. Oturum ve arşiv yerel olarak şifrelenir.", archive.maxEntries, archive.maxImages)}</p>`, true);
  }
  function valueText(value) {
    if (value === null || value === undefined) return t("Bilinmiyor");
    if (typeof value === "boolean") return value ? t("Evet") : t("Hayır");
    if (typeof value === "object" && value.picture != null)
      return value.picture === "anonymous"
        ? t("Anonim profil fotoğrafı")
        : t("Kayıtlı profil fotoğrafı bilgisi (iki kayıtta farklı)");
    if (typeof value === "object")
      return Object.entries(value)
        .map(
          ([k, v]) =>
            `${t(labels[k]) || { direction: t("Liste yönü"), count: t("Hesap sayısı"), added: t("Yeni görülen hesaplar"), removed: t("Son kayıtta yok") }[k] || k}: ${valueText(v)}`,
        )
        .join("\n");
    return String(value);
  }
  function eventDialog(id) {
    const e = data.events.find((e) => e.id === id);
    if (!e) return;
    if (e.type === 'list') { listChangesDialog(id); return; }
    if (e.type === "error") {
      dialog(`<span class="dialog-symbol peach">${icon("info")}</span><span class="eyebrow">${esc(date(e.at))}</span><h2 id="dialog-title">${t("Kontrol tamamlanamadı")}</h2><p>${t("Bu kontrolün teknik kaydını aşağıdan açabilirsin.")}</p><details><summary>${t("Teknik ayrıntılar")}</summary><p>${esc(stored(e.title))}</p></details>`);
      return;
    }
    if (e.topic === "access") {
      dialog(
        `<span class="dialog-symbol peach">${icon("shield")}</span><span class="eyebrow">${esc(date(e.at))}</span><h2 id="dialog-title">${esc(eventTitle(e))}</h2><p>${t("Bu geçmiş olayının değerlendirmesi: {0}.", esc(e.new?.status === "suspected" ? t("Engellenme şüphesi") : t("Profil görünürlüğü kontrolü")))}</p>${accessPanel(e.personId, e.new)}`,
        true,
      );
      return;
    }
    dialog(
      `<span class="dialog-symbol lavender">${icon("clock")}</span><span class="eyebrow">${esc(date(e.at))}</span><h2 id="dialog-title">${esc(eventTitle(e))}</h2><div class="change-values"><div><span>${t("Önceki kontrol")}</span><strong>${esc(valueText(e.old))}</strong></div><div><span>${t("Yeni kontrol")}</span><strong>${esc(valueText(e.new))}</strong></div></div><p class="dialog-note">${t("Bu zaman kontrolün kaydedildiği andır. Liste farkı tek başına takipten çıkış veya engelleme kanıtı değildir.")}</p>`,
    );
  }
  function codeDialog() {
    dialog(
      `<span class="dialog-symbol lavender">${icon("lock")}</span><h2 id="dialog-title">${t("Instagram doğrulama kodu")}</h2><p>${t("Kodu aynı bekleyen giriş işlemine gönder. Kod kaydedilmez. Bekleme süresi 5 dakikadır.")}</p><form id="code-form"><label class="form-label" for="verification-code">${t("Doğrulama kodu")}</label><input id="verification-code" name="code" inputmode="numeric" type="password" required pattern="[0-9]{6,10}" minlength="6" maxlength="10" autocomplete="one-time-code"><button class="button primary full-width" type="submit">${t("Kodu gönder")}</button></form>${button(t("İşlemi iptal et"), "cancel", "close")}`,
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
      case "accounts":
        accountsDialog();
        break;
      case "add-account":
        accountDialog(true);
        break;
      case "switch-account":
        closeDialog();
        await command('switch_account', {id});
        break;
      case "requests":
        requestsDialog();
        break;
      case "usage-notes":
        dialog(window.TrackerUsageNotes.render(t, t("Son 2.000 olay, profil başına son 366 gözlem ve liste yönü başına son 6 tamamlanmış tarama korunur.")));
        break;
      case "list-changes":
        listChangesDialog(id);
        break;
      case "person-lists":
        ui.owner=id;ui.comparison=false;ui.previous=null;ui.current=null;
        ui.direction='followers';ui.listQuery='';ui.listFilter='all';ui.pageSize=100;
        navigate('lists');
        break;
      case "list-change-page":
        listChangesDialog(id, Number(value), target.dataset.kind);
        break;
      case "list-change-filter":
        listChangesDialog(id, 0, value);
        break;
      case "privacy":
        dialog(
          `<span class="dialog-symbol lavender">${icon("shield")}</span><h2 id="dialog-title">${t("Verilerin bilgisayarında kalır.")}</h2><p>${t("Oturum, gözlemler, listeler ve notlar Windows DPAPI ile şifrelenir. Parola ve doğrulama kodu saklanmaz. Hesap işlemleri doğrudan Instagram’a gider; aracı sunucuya gönderilmez.")}</p><p>${t("Şifreli yedek oturum içermez. CSV dosyası şifresizdir ve yalnız sen dışa aktarmayı seçtiğinde yazılır.")}</p><p>${t("InstagramTracker, Instagram’dan bağımsızdır.")}</p>`,
        );
        break;
      case "refresh":
        closeDialog();
        await command("refresh", {}, t("Profil kontrol edildi."));
        break;
      case "import-counter":
        closeDialog();
        await command(
          "import_counter",
          {},
          t("Önceki oturum doğrulandı ve bu uygulamaya kaydedildi."),
        );
        break;
      case "cancel":
        closeDialog();
        await command("cancel");
        toast(
          t("İptal istendi. Devam eden ağ isteği en fazla zaman aşımı süresi kadar bekleyebilir."),
        );
        break;
      case "enter-code":
        codeDialog();
        break;
      case "search":
        palette.open();
        break;
      case "reset-people":
        ui.query = ''; ui.filter = 'all'; render();
        document.getElementById('people-search')?.focus();
        break;
      case "guide-people":
        navigate('people');
        break;
      case "guide-settings":
        navigate('settings');
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
          t("Liste taraması kaydedildi."),
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
        personDialog(id,{avatar:target.querySelector('.avatar img')?.getAttribute('src'),source:target.dataset.photoScan ?
          {accountId:data.activeAccountId,ownerId:ui.owner || data.account?.id,direction:ui.direction,scanId:target.dataset.photoScan,id} : null});
        break;
      case "wayback":
        await waybackDialog(id);
        break;
      case "wayback-toggle":
        await waybackDialog(id, {enabled: value === "true"});
        break;
      case "wayback-clear":
        dialog(`<h2 id="dialog-title">${t("Arşivi temizle")}</h2><p>${t("Bu profilin biyografi ve avatar arşivi silinir, arşivleme kapanır. Diğer profil gözlemleri korunur.")}</p>${button(t("Vazgeç"), "wayback", null, "secondary", idattr(id))}${button(t("Arşivi sil ve durdur"), "wayback-confirm-clear", "trash", "primary", idattr(id))}`);
        break;
      case "wayback-confirm-clear":
        await waybackDialog(id, {clear:true});
        break;
      case "access-history":
        ui.eventType = "access";
        navigate("timeline");
        break;
      case "check-access": {
        closeDialog();
        const result = await command(
          "check_access",
          { id },
          t("Profilin açılıp açılamadığı kontrol edildi."),
        );
        if (result?.ok) personDialog(id);
        break;
      }
      case "star":
      case "drawer-star": {
        const p = data.people.find((p) => p.id === id);
        const reopen = action === "drawer-star";
        if (reopen) closeDialog();
        const r = await command(
          "save_person",
          { id, favorite: !p?.favorite },
          t("Favori tercihi kaydedildi."),
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
          t("Not ve grup şifreli olarak kaydedildi."),
        );
        break;
      }
      case "refresh-person": {
        profileDetails.retry();
        break;
      }
      case 'open-profile-link': await openProfileLink(target.dataset.url);break;
      case "remove-person":
        dialog(
          `<h2 id="dialog-title">${t("Bu kişi kaldırılsın mı?")}</h2><p>${t("Favori, not ve izleme tercihi silinir. Eski gözlem geçmişi ve liste kayıtları korunur.")}</p><div class="dialog-buttons">${button(t("Vazgeç"), "close")}${button(t("Kişiyi kaldır"), "remove-confirm", "close", "primary", idattr(id))}</div>`,
        );
        break;
      case "remove-confirm":
        closeDialog();
        await command("remove_person", { id }, t("Kişi kaldırıldı."));
        break;
      case "history":
        historyDialog(id || data.account?.id);
        break;
      case "event":
        eventDialog(id);
        break;
      case "notifications":
        closeDialog();
        navigate("notifications");
        break;
      case "notification-rules":
        notificationPreferences(id);
        break;
      case "notification-account":
        notificationPreferences(
          document.getElementById("notification-account").value,
        );
        break;
      case "notification-log":
        await notificationDialog(id);
        break;
      case "notification-filter":
        ui.notificationFilter = value;
        ui.notificationLimit = 100;
        render();
        break;
      case "more-notifications":
        ui.notificationLimit += 100;
        render();
        break;
      case "add-person":
        addPersonDialog();
        break;
      case "direct-add-person":
        await addByInput();
        break;
      case "choose-profile":
        closeDialog();
        if(data.account?.id===id || data.people.some(p=>p.id===id))personDialog(id);
        else await command('add_person',{id,username:target.dataset.username},t("Profil kaydedildi."));
        break;
      case "setting":
        await command(
          "settings",
          { [value]: !data.settings[value] },
          t("Ayar kaydedildi."),
        );
        break;
      case "export-backup":
        await file("export_backup");
        break;
      case "preparation-resume":
        if (data.connected) await command('settings', {automatic:true, automaticLists:true});
        else accountDialog();
        break;
      case "preparation-pause":
        await command('settings', {automaticLists:false});
        break;
      case "guide-own-lists":
      case "preparation-lists":
        if (!data.account) { accountDialog(); break; }
        Object.assign(ui, {owner:data.account.id, direction:'followers', current:null, previous:null,
          comparison:false, listFilter:'all', listQuery:'', pageSize:100});
        navigate('lists');
        break;
      case "import-backup":
        await file("import_backup");
        break;
      case "open-storage":
        await file("open_storage");
        break;
      case "disconnect":
        dialog(
          `<h2 id="dialog-title">${t("Yerel oturum kaldırılsın mı?")}</h2><p>${t("Bu cihazdaki oturum kopyası silinir; gözlem ve notlar korunur. Bu işlem Instagram sunucusundaki oturumu iptal etmez. Şüpheli bir giriş varsa resmî Instagram üzerinden giriş hareketlerini kontrol et.")}</p><div class="dialog-buttons">${button(t("Vazgeç"), "close")}${button(t("Bağlantıyı kaldır"), "disconnect-confirm", "logout", "primary")}</div>`,
        );
        break;
      case "disconnect-confirm":
        closeDialog();
        await command("disconnect", {}, t("Yerel oturum kaldırıldı."));
        break;
      case "delete-all":
        dialog(
          `<h2 id="dialog-title">${t("Seçili hesabın yerel verileri silinsin mi?")}</h2><p>${t("Yalnız seçili hesabın oturumu, kişiler, notlar, listeler ve geçmişi silinir. Diğer hesaplar ve dışa aktarılan dosyalar korunur. Ortak istek molası devam eder.")}</p><div class="dialog-buttons">${button(t("Vazgeç"), "close")}${button(t("Yerel verileri sil"), "delete-confirm", "close", "primary")}</div>`,
        );
        break;
      case "delete-confirm":
        closeDialog();
        await command("delete_all", {}, t("Yerel kayıtlar silindi."));
        break;
    }
  });
  document.addEventListener("submit", async (event) => {
    const form = event.target;
    if (form.id === "notification-rule-form") {
      event.preventDefault();
      const id = form.dataset.id,
        values = new FormData(form);
      const args = {
        id,
        enabled: values.has("account-enabled"),
        fields: Object.fromEntries(
          Object.keys(data.notificationTypes).map((key) => [
            key,
            values.has(key),
          ]),
        ),
      };
      closeDialog();
      await command(
        "notification_rule",
        args,
        t("Bildirim tercihleri kaydedildi."),
      );
      return;
    }

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
        t("Hesap bağlandı ve ilk kontrol tamamlandı."),
      );
      args.password = "";
    } else if (form.id === "code-form") {
      const code = String(values.get("code"));
      form.reset();
      closeDialog();
      await command("code", { code });
    } else {
      const field=document.getElementById('new-username');
      if(!window.TrackerProfileSearch.query(field.value)) {field.setCustomValidity(t("Arama için 2–100 karakter yaz."));field.reportValidity();return;}
      profileSearch?.submit();
    }
  });
  document.addEventListener("input", (event) => {
    if(event.target.id==='new-username') {
      event.target.setCustomValidity('');
      profileSearch?.input(event.target.value);
    }
    if (event.target.id === "people-search") {
      ui.query = event.target.value;
      document.getElementById("people-grid").innerHTML =
        filteredPeople().map(personCard).join("") ||
        peopleEmpty();
      document.getElementById("people-result-count").textContent =
        t("{0} kayıtlı profil", filteredPeople().length);
    }
    if (event.target.id === "list-search") {
      ui.listQuery = event.target.value;
      ui.pageSize = 100;
      document.getElementById("list-rows").innerHTML = listRows();
      document.getElementById("list-result-count").textContent =
        t("{0} hesap · En fazla {1} satır gösteriliyor", listEntries().length, ui.pageSize);
      document.getElementById("list-pagination").innerHTML = listPagination();
      refreshListPhotos();
    }
  });
  document.addEventListener("change", async (event) => {
    switch (event.target.id) {
      case "app-theme":
        if (window.TrackerAppearance.valid(event.target.value)) await command("settings", {theme:event.target.value});
        break;
      case "app-language":
        await command("settings", { language: event.target.value });
        toast(t("Dil tercihi kaydedildi."));
        break;
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
          t("Kontrol aralığı kaydedildi."),
        );
        break;
    }
  });
  window.addEventListener("hashchange", () => {
    const page = Object.hasOwn(pages, location.hash.slice(1))
      ? location.hash.slice(1)
      : "overview";
    if (page === ui.page) return;
    ui.page = page;
    closeDialog();
    render();
    window.scrollTo(0, 0);
  });
  document.addEventListener("keydown", (event) => {
    if (!data || event.isComposing || event.altKey) return;
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      if (!event.repeat) palette.open();
    } else if ((event.ctrlKey || event.metaKey) && !event.shiftKey && event.key.toLowerCase() === 'r') {
      event.preventDefault(); if (!event.repeat) refreshSelected();
    } else if (event.key === 'Escape' && !palette.isOpen()) {
      if (document.querySelector('#overlay-root dialog[open]')) { event.preventDefault(); closeDialog(); }
    } else if (!event.ctrlKey && !event.metaKey && !editing()) {
      if (event.key === '/') { event.preventDefault(); if (!event.repeat) palette.open(); }
      else if (!document.querySelector('dialog[open]') && /^[1-5]$/.test(event.key)) {
        event.preventDefault(); if (!event.repeat) navigate(keyboardPages[Number(event.key)-1]);
      }
    }
  });
  if (!api) {
    serviceError =
      t("Bu sayfa InstagramTracker Windows uygulaması içinde açılmalı.");
    render();
    return;
  }
  api.subscribe((event) => {
    if (event.type === "visibility") {
      windowVisible = event.visible;
      refreshListPhotos();
      operationPanel();
      return;
    }
    if (event.type === "open_notification") {
      notificationDialog(event.id);
      return;
    }
    if (event.type === "notification_warning" || event.notificationWarning)
      toast(event.message || event.notificationWarning);
    if (event.state) acceptState(event.state);
    if (event.type === "resume") {
      windowVisible = event.visible;
      busy = event.busy;
      progress = event.progress;
      serviceError = event.error ? userError(event.error) : null;
      render();
      if (progress?.phase === "code") codeDialog();
      else if (document.querySelector("#code-form")) closeDialog();
    } else if (event.type === "fatal") {
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
      acceptState(result.state);
      busy = result.busy;
      progress = result.progress;
      serviceError = result.error ? userError(result.error) : null;
      render();
    })
    .catch(() => {
      serviceError = t("Yerel hizmete ulaşılamadı.");
      render();
    });
})();
