(() => {
  "use strict";
  const FILE_PAGE = 50;

  function mount(root, {modal, api, t, num, language, existing}) {
    let archive, matches = [], shown = 0, active, record = 0, offset = 0, nextOffset, mediaUrl;
    let pending = false, closed = false, token, view = "records";
    const categoryName = name => name.split("/").filter(Boolean)[0] || "";
    const nameOf = name => name.split("/").at(-1);
    const categories = {
      connections: t("Bağlantılar"),
      your_instagram_activity: t("Instagram etkinliğin"),
      personal_information: t("Kişisel bilgiler"),
      logged_information: t("Kayıtlı istatistikler"),
      ads_information: t("Reklam bilgileri"),
      security_and_login_information: t("Güvenlik ve giriş bilgileri"),
      preferences: t("Tercihler"),
      apps_and_websites_off_of_instagram: t("Bağlı uygulamalar ve siteler"),
      media: t("Fotoğraf, video ve ses"),
    };
    const date = new Intl.DateTimeFormat(language, {dateStyle:"medium", timeStyle:"short"});
    const pretty = name => name.replaceAll("_", " ");
    function element(tag, value, className) {
      const node = document.createElement(tag);
      if (value != null) node.textContent = value;
      if (className) node.className = className;
      return node;
    }
    function button(title, action, primary = false) {
      const control = element("button", title, "button " + (primary ? "primary" : "secondary"));
      control.type = "button";
      control.addEventListener("click", action);
      return control;
    }
    function field(title, control) {
      const label = element("label", title);
      label.append(control);
      return label;
    }
    function releaseMedia() {
      if (mediaUrl) URL.revokeObjectURL(mediaUrl);
      mediaUrl = null;
    }
    function size(bytes) {
      const options = {style: "unit", unit: bytes < 1048576 ? "kilobyte" : "megabyte", maximumFractionDigits: 1};
      return new Intl.NumberFormat(language, options).format(bytes / (bytes < 1048576 ? 1024 : 1048576));
    }
    const status = element("p", "", "field-hint");
    status.setAttribute("role", "status");
    const choose = button(t("Instagram ZIP yükle"), open, true);
    const head = element("div", null, "export-browser-header");
    const summary = element("p", t("Arşiv cihazında açılır. Kategorilerden bir dosya seçerek kayıtları inceleyebilirsin."));
    head.append(summary);
    if (!existing) head.append(choose);
    const layout = element("div", null, "export-browser-layout");
    layout.hidden = true;
    const directory = element("section", null, "export-browser-directory");
    const selector = element("select");
    const search = element("input");
    search.type = "search";
    search.maxLength = 200;
    const fileCount = element("p", "", "field-hint");
    fileCount.setAttribute("role", "status");
    const files = element("ul", null, "export-browser-files");
    const more = button(t("Daha fazla göster"), appendFiles);
    directory.append(field(t("Kategori"), selector), field(t("Dosya adı veya klasör ara"), search), fileCount, files, more);
    const detail = element("section", null, "export-browser-detail");
    detail.tabIndex = -1;
    detail.append(element("p", t("Ayrıntıları görmek için bir dosya seç.")));
    layout.append(directory, detail);
    root.append(head, status, layout);

    async function request(action, operation, onSuccess) {
      if (closed || pending) return;
      pending = true;
      choose.disabled = true;
      layout.inert = true;
      status.textContent = action;
      try {
        const response = await operation();
        if (response?.archiveBrowser?.token) token = response.archiveBrowser.token;
        if (closed) return;
        if (response?.ok) {
          status.textContent = "";
          if (!response.cancelled) onSuccess(response.archiveBrowser);
        } else {
          status.textContent = response?.error?.code === "busy" ?
            t("Biraz sonra tekrar dene.") : t("Arşiv veya seçilen dosya açılamadı.");
        }
      } catch {
        if (!closed) status.textContent = t("Arşiv veya seçilen dosya açılamadı.");
      } finally {
        pending = false;
        if (closed && token && !existing) {
          api.command("export_browser_close", {token}).catch(() => {});
        } else {
          choose.disabled = false;
          layout.inert = false;
        }
      }
    }
    function open() {
      request(t("Dosya hazırlanıyor…"), () => existing ?
        Promise.resolve({ok:true,archiveBrowser:existing}) : api.file("export_browser_open"), value => {
        archive = value;
        active = null;
        releaseMedia();
        detail.replaceChildren(element("p", t("Ayrıntıları görmek için bir dosya seç.")));
        summary.textContent = archive.name + " · " + size(archive.size) + " · " + t("{0} dosya", num(archive.files.length));
        const counts = new Map();
        for (const file of archive.files) {
          const key = categoryName(file.name);
          counts.set(key, (counts.get(key) || 0) + 1);
        }
        const all = element("option", t("Tüm kategoriler"));
        all.value = "";
        selector.replaceChildren(all, ...Array.from(counts, ([key, count]) => {
          const option = element("option", (categories[key] || pretty(key)) + " · " + num(count));
          option.value = key;
          return option;
        }));
        search.value = "";
        layout.hidden = false;
        filterFiles();
      });
    }
    function filterFiles() {
      const query = search.value.trim().toLowerCase();
      matches = archive.files.filter(file => (!selector.value || categoryName(file.name) === selector.value) &&
        (file.name.toLowerCase().includes(query) || pretty(file.name).toLowerCase().includes(query)));
      shown = 0;
      files.replaceChildren();
      appendFiles();
    }
    function appendFiles() {
      const batch = matches.slice(shown, shown + FILE_PAGE);
      files.append(...batch.map(file => {
        const item = element("li");
        const control = button(pretty(nameOf(file.name)), () => selectFile(file));
        control.title = file.name;
        control.dataset.fileId = file.id;
        control.setAttribute("aria-pressed", String(active?.id === file.id));
        item.append(control);
        return item;
      }));
      shown += batch.length;
      more.hidden = shown >= matches.length;
      fileCount.textContent = t("{0} / {1} dosya gösteriliyor", num(shown), num(matches.length));
    }
    function selectFile(file) {
      if (pending) return;
      active = file;
      record = offset = 0;
      view = "records";
      releaseMedia();
      for (const control of files.querySelectorAll("button")) control.setAttribute("aria-pressed", String(control.dataset.fileId === String(file.id)));
      detail.replaceChildren(element("h3", pretty(nameOf(file.name))), element("p", file.name, "field-hint"),
        element("p", size(file.size), "field-hint"));
      if (file.copy) detail.append(button(t("Dosyayı kaydet"), () => {
        request(t("Dosya hazırlanıyor…"), () => api.file("export_browser_save", {token, index: file.id, name: nameOf(file.name)}),
          () => { status.textContent = t("Dosya kaydedildi."); });
      }));
      const panel = element("div", null, "export-browser-preview");
      detail.append(panel);
      if ((file.kind === "text" || file.kind === "json") && file.size <= 24 * 1024 * 1024) showText(panel);
      else if (["image", "video", "audio"].includes(file.kind) && file.size <= 32 * 1024 * 1024) {
        panel.append(button(t("Yerel önizlemeyi aç"), () => showMedia(panel, file)));
      } else panel.append(element("p", t("Bu dosya için panelde önizleme yok."), "field-hint"));
    }
    function showText(panel, requestedRecord = record, requestedOffset = offset, requestedView = view) {
      request(t("Kayıt okunuyor…"), () => api.command("export_browser_text", {
        token, index: active.id, record: requestedRecord, offset: requestedOffset, view: requestedView,
      }), value => {
        record = requestedRecord;
        offset = requestedOffset;
        view = requestedView;
        nextOffset = value.next;
        const header = element("div", null, "export-browser-records");
        if (value.hasMetadata) header.append(view === "records" ?
          button(t("Konuşma bilgileri"), () => showText(panel, 0, 0, "metadata")) :
          button(t("Mesajlara dön"), () => showText(panel, 0, 0, "records")));
        if (value.section) header.append(element("p", pretty(value.section), "field-hint"));
        if (value.total > 1) {
          const input = element("input");
          input.type = "number";
          input.min = "1";
          input.max = value.total;
          input.value = record + 1;
          input.addEventListener("change", () => {
            const index = Number(input.value) - 1;
            if (Number.isInteger(index) && index >= 0 && index < value.total) showText(panel, index, 0);
            else input.value = record + 1;
          });
          const previous = button(t("Önceki kayıt"), () => showText(panel, record - 1, 0));
          const next = button(t("Sonraki kayıt"), () => showText(panel, record + 1, 0));
          previous.disabled = record === 0;
          next.disabled = record + 1 >= value.total;
          header.append(previous, field(t("Kayıt {0} / {1}", num(record + 1), num(value.total)), input), next);
        }
        panel.replaceChildren(header);
        if (!value.total) panel.append(element("p", t("Bu dosyada kayıt yok.")));
        else if (value.json && offset === 0 && nextOffset == null) showRecord(panel, value.text);
        else panel.append(element("pre", value.text));
        if (offset > 0) panel.append(button(t("Önceki metin bölümü"), () => showText(panel, record, Math.max(0, offset - 24000))));
        if (nextOffset != null) panel.append(button(t("Sonraki metin bölümü"), () => showText(panel, record, nextOffset)));
      });
    }
    function showRecord(panel, source) {
      let record;
      try { record = JSON.parse(source); } catch { panel.append(element("pre", source)); return; }
      const fields = element("dl", null, "export-browser-fields");
      let budget = 75;
      function visit(value, key = "", depth = 0) {
        if (budget <= 0 || depth > 12) return;
        if (value && typeof value === "object") {
          for (const [name, item] of Object.entries(value)) {
            if (budget <= 0) break;
            visit(item, key ? key + " › " + name : name, depth + 1);
          }
          return;
        }
        if (value == null || value === "") return;
        const suffix = key.split(" › ").at(-1);
        const isTime = /(?:^|_)timestamp(?:_value|_ms)?$/i.test(suffix);
        const time = isTime && Number.isFinite(value) ? (value >= 1e12 ? value : value * 1000) : null;
        const shown = time != null && time >= Date.UTC(2000,0,1) && time < Date.UTC(2100,0,1)
          ? date.format(time) : String(value);
        fields.append(element("dt", pretty(key)), element("dd", shown));
        budget--;
      }
      visit(record);
      if (fields.childNodes.length) panel.append(fields);
      const raw = element("details");
      raw.open = !fields.childNodes.length;
      raw.append(element("summary", t("Ham JSON")), element("pre", source));
      panel.append(raw);
    }
    function showMedia(panel, file) {
      request(t("Dosya hazırlanıyor…"), () => api.command("export_browser_media", {token, index: file.id}), value => {
        releaseMedia();
        const bytes = Uint8Array.fromBase64 ? Uint8Array.fromBase64(value.base64) :
          Uint8Array.from(atob(value.base64), char => char.charCodeAt(0));
        mediaUrl = URL.createObjectURL(new Blob([bytes], {type: value.mime}));
        const player = element(file.kind === "image" ? "img" : file.kind);
        if (file.kind === "image") player.alt = nameOf(file.name);
        else player.controls = true;
        player.src = mediaUrl;
        panel.replaceChildren(player);
      });
    }
    selector.addEventListener("change", filterFiles);
    search.addEventListener("input", filterFiles);
    modal.addEventListener("close", () => {
      closed = true;
      releaseMedia();
      archive = active = null;
      matches = [];
      root.replaceChildren();
      if (!pending && token && !existing) api.command("export_browser_close", {token}).catch(() => {});
    }, {once:true});
    if (existing) open();
  }
  window.TrackerExportBrowser = Object.freeze({mount});
})();
