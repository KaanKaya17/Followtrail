// Local presentation preference only. CSS owns live system theme changes.
(function(root) {
  'use strict';
  const choices=Object.freeze(['system','light','dark','oled']);
  const valid=value=>choices.includes(value);
  let selected='system';
  try {const saved=localStorage.getItem('tracker-ui-theme');if(valid(saved))selected=saved;}catch { /* Optional first-paint cache. */ }
  document.documentElement.dataset.theme=selected;
  root.TrackerAppearance=Object.freeze({choices,valid,selected:()=>selected,
    apply(value) {
      const next=valid(value)?value:'system';
      if(next===selected)return;
      selected=next;document.documentElement.dataset.theme=selected;
      try {localStorage.setItem('tracker-ui-theme',selected);}catch { /* Windows vault remains authoritative. */ }
    },
    control({t,disabled=false}) {
      const names=[t("Sistem"),t("Açık tema"),t("Koyu"),t("OLED siyahı")];
      return `<div class="setting-row"><div><strong>${t("Tema")}</strong><p>${t("Sistem temasını izle veya bu cihaz için bir görünüm seç.")}</p></div><select id="app-theme" aria-label="${t("Tema seçimi")}" ${disabled?'disabled':''}>${choices.map((value,index)=>`<option value="${value}" ${value===selected?'selected':''}>${names[index]}</option>`).join('')}</select></div>`;
    }
  });
})(window);
