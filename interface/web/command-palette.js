(function (root) {
  'use strict';
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const normalize = value => String(value).normalize('NFKD').replace(/\p{M}/gu,'').toLowerCase().replace(/ı/g,'i');
  function create({t, icon, entries, select}) {
    let current = null;
    function open() {
      if (current?.open) { current.querySelector('input').focus(); current.querySelector('input').select(); return; }
      const modal = document.createElement('dialog');
      modal.className = 'command-palette';
      modal.setAttribute('aria-labelledby', 'command-title');
      modal.innerHTML = `<header><div><h2 id="command-title">${escape(t("Profil ve ekran ara"))}</h2><p>${escape(t("Kayıtlı hesaplarda ve uygulama ekranlarında ara. Instagram isteği gönderilmez."))}</p></div><button type="button" class="icon-button" data-palette-close aria-label="${escape(t("Aramayı kapat"))}">${icon('close')}</button></header><label class="palette-input">${icon('search')}<input type="search" role="combobox" aria-label="${escape(t("Profil veya ekran adı"))}" aria-autocomplete="list" aria-expanded="true" aria-controls="command-results" autocomplete="off" spellcheck="false" placeholder="${escape(t("Kullanıcı adı, isim veya ekran yaz…"))}"></label><p class="palette-count" role="status" aria-live="polite"></p><div id="command-results" role="listbox" aria-label="${escape(t("Arama sonuçları"))}"></div><footer><span><kbd>↑ ↓</kbd> ${escape(t("Seç"))}</span><span><kbd>Enter</kbd> ${escape(t("Aç"))}</span><span><kbd>Esc</kbd> ${escape(t("Kapat"))}</span><span><kbd>1–5</kbd> ${escape(t("Ana sekmeler"))}</span><span><kbd>Ctrl R</kbd> ${escape(t("Profili yenile"))}</span></footer><p class="palette-count">${escape(t("Ctrl R açık kişi profilini, Listeler’de seçili hesabı veya Özet’te kendi hesabını yeniler. Mola ve işlem sırası korunur."))}</p>`;
      document.body.append(modal); current = modal;
      const input = modal.querySelector('input'), list = modal.querySelector('[role="listbox"]'), count = modal.querySelector('[role="status"]');
      let rows = [], selected = 0;
      function highlight() {
        const options = list.querySelectorAll('[role="option"]');
        for (const [index, node] of [...options].entries()) node.setAttribute('aria-selected', String(index === selected));
        const active = options[selected];
        if (active?.getAttribute('role') === 'option') {
          input.setAttribute('aria-activedescendant', active.id);
          active.scrollIntoView({block:'nearest'});
        } else input.removeAttribute('aria-activedescendant');
      }
      function render() {
        const profilesOnly = input.value.trim().startsWith('@');
        const query = normalize(input.value.trim().replace(/^@/, ''));
        const matches = entries().filter(entry => (!profilesOnly || entry.kind === 'profile') && (!query || normalize(`${entry.title} ${entry.subtitle || ''} ${entry.terms || ''}`).includes(query)));
        rows = matches.slice(0, 12); selected = 0;
        count.textContent = matches.length > rows.length ? t("{0} sonuç · İlk {1} gösteriliyor", matches.length, rows.length) : t("{0} sonuç", matches.length);
        list.innerHTML = rows.map((entry,index) => `<button type="button" role="option" id="command-option-${index}" data-palette-index="${index}" tabindex="-1" aria-selected="${index===0}"><span class="palette-symbol">${icon(entry.icon || 'people')}</span><span class="palette-copy"><strong>${escape(entry.title)}</strong><small>${escape(entry.subtitle || '')}</small></span>${entry.shortcut ? `<kbd>${escape(entry.shortcut)}</kbd>` : ''}</button>`).join('') || `<p class="palette-empty">${escape(t("Kayıtlı profil veya ekran bulunamadı. Farklı bir ad deneyebilirsin."))}</p>`;
        highlight();
      }
      function cleanup() { if (current === modal) current = null; if (modal.isConnected) modal.remove(); }
      function close() { if (modal.open) modal.close(); cleanup(); }
      function activate(index) { const entry = rows[index]; if (entry) { close(); select(entry); } }
      modal.addEventListener('click', event => {
        if (event.target.closest('[data-palette-close]')) close();
        const row = event.target.closest('[data-palette-index]');
        if (row) activate(Number(row.dataset.paletteIndex));
      });
      input.addEventListener('input', render);
      input.addEventListener('keydown', event => {
        if (event.isComposing) return;
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault(); event.stopPropagation();
          if (rows.length) { selected = (selected + (event.key === 'ArrowDown' ? 1 : -1) + rows.length) % rows.length; highlight(); }
        } else if (event.key === 'Enter') { event.preventDefault(); event.stopPropagation(); activate(selected); }
        else if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); }
      });
      modal.addEventListener('cancel', event => { event.preventDefault(); event.stopPropagation(); close(); });
      modal.addEventListener('close', cleanup);
      render(); modal.showModal(); input.focus();
    }
    return Object.freeze({open, isOpen: () => !!current?.open});
  }
  root.TrackerCommandPalette = Object.freeze({create});
})(window);
