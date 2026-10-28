(function (root) {
  'use strict';
  const tokens = value => String(value).match(/\s+|https?:\/\/[^\s]+|[\p{L}\p{N}_]+|[^\s]/gu) || [];
  function diff(before, after) {
    const a = tokens(before), b = tokens(after), result = [];
    const push = (kind, text) => { if (!text) return; const last = result.at(-1); if (last?.kind === kind) last.text += text; else result.push({kind, text}); };
    if (a.length * b.length > 160000) {
      let start = 0, end = 0;
      while (start < Math.min(a.length, b.length) && a[start] === b[start]) start++;
      while (end < Math.min(a.length, b.length) - start && a[a.length - end - 1] === b[b.length - end - 1]) end++;
      push('same', a.slice(0, start).join('')); push('remove', a.slice(start, a.length - end).join(''));
      push('add', b.slice(start, b.length - end).join('')); push('same', a.slice(a.length - end).join('')); return result;
    }
    const table = Array.from({length: a.length + 1}, () => new Uint16Array(b.length + 1));
    for (let i = a.length - 1; i >= 0; i--) for (let j = b.length - 1; j >= 0; j--)
      table[i][j] = a[i] === b[j] ? table[i+1][j+1] + 1 : Math.max(table[i+1][j], table[i][j+1]);
    let i = 0, j = 0;
    while (i < a.length || j < b.length) {
      if (i < a.length && j < b.length && a[i] === b[j]) { push('same', a[i++]); j++; }
      else if (i < a.length && (j === b.length || table[i+1][j] >= table[i][j+1])) push('remove', a[i++]);
      else push('add', b[j++]);
    }
    return result;
  }
  function render(detail, {t, date, esc}) {
    const entries = detail.entries || [], photos = new Map(), edits = [];
    entries.forEach((entry, index) => {
      if (entry.image && detail.images?.[entry.image]) {
        const p = photos.get(entry.image);
        if (p) p.last = entry.lastSeen; else photos.set(entry.image, {first:entry.at,last:entry.lastSeen,url:detail.images[entry.image]});
      }
      const previous = entries[index-1];
      if (entry.bio != null && (!previous || previous.bio !== entry.bio)) edits.push({entry, previous});
    });
    return `<p class="dialog-note">${esc(t('Arşiv ilk kayıtla başlar. Tarihler gözlem zamanıdır; gerçek değişiklik anı bilinmez.'))}</p>
      ${detail.full ? `<div class="notice warning">${esc(t('Arşiv sınırına ulaşıldı. Eski kayıtlar korunur; yer açmak için arşivi temizleyebilirsin.'))}</div>` : ''}
      <section class="wayback-section"><h3>${esc(t('Avatar galerisi'))}</h3><p>${esc(t('{0} görsel · {1} KB', photos.size, Math.ceil((detail.bytes || 0)/1024)))}</p>
      <div class="wayback-gallery">${[...photos.values()].map(p => `<figure><img src="${esc(p.url)}" alt="${esc(t('Arşivlenmiş profil fotoğrafı'))}" loading="lazy"><figcaption>${esc(t('İlk gözlem'))}: ${esc(date(p.first))}<br>${esc(t('Son gözlem'))}: ${esc(date(p.last))}</figcaption></figure>`).join('') || `<p>${esc(t('Henüz arşivlenmiş fotoğraf yok. Görsel indirilemezse eski fotoğraf üretilmez.'))}</p>`}</div></section>
      <section class="wayback-section"><h3>${esc(t('Biyografi zaman tüneli'))}</h3><p class="field-hint">${esc(t('+ Eklenen · − Kaldırılan'))}</p>${edits.reverse().map(({entry, previous}) => `<article class="wayback-edit"><time>${esc(date(entry.at))}</time><div class="bio-diff">${previous?.bio != null ? diff(previous.bio,entry.bio).map(part => part.kind === 'same' ? esc(part.text) : `<${part.kind === 'add' ? 'ins' : 'del'}><span class="diff-sign" aria-hidden="true">${part.kind === 'add' ? '+' : '−'}</span>${esc(part.text)}</${part.kind === 'add' ? 'ins' : 'del'}>`).join('') : esc(entry.bio || t('Biyografi boş'))}</div>${previous?.bio == null ? `<small>${esc(t('Başlangıç kaydı'))}</small>` : ''}</article>`).join('') || `<p>${esc(t('Arşiv kapalıyken yeni kayıt tutulmaz. Eski kayıtlar açılıp incelenebilir.'))}</p>`}</section>`;
  }
  const api = {diff, render};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TrackerWayback = Object.freeze(api);
})(typeof window === 'undefined' ? globalThis : window);
