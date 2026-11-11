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
  function comparisonRecords(detail, history = []) {
    const records = new Map(), photos = new Map();
    const put = (at, fields) => {
      if (Number.isFinite(at) && at > 0) records.set(at, {...records.get(at), ...fields, at});
    };
    for (const entry of detail.entries || []) {
      const rawImage = detail.images?.[entry.image];
      const image = typeof rawImage === 'string' && rawImage.startsWith('data:image/jpeg;base64,') ? rawImage : undefined;
      if (image && entry.picture) photos.set(entry.picture, image);
      put(entry.at, {bio:entry.bio, picture:entry.picture, image});
      put(entry.lastSeen, {bio:entry.bio, picture:entry.picture, image});
    }
    for (const entry of history) {
      const sameObservation = records.get(entry.observedAt);
      put(entry.observedAt, {
        username:entry.username, name:entry.name, bio:entry.bio, url:entry.url, followers:entry.followers,
        following:entry.following, posts:entry.posts, picture:entry.picture,
        image:entry.picture ? photos.get(entry.picture) : sameObservation?.image,
      });
    }
    return [...records.values()].sort((a,b) => a.at-b.at);
  }
  function renderComparison(before, after, {t, esc, number = String}) {
    const missing = t("Kayıt yok");
    const empty = t("Boş");
    const change = (value, kind) => value ? '<' + kind + '><span class="diff-sign" aria-hidden="true">' +
      (kind === 'del' ? '−' : '+') + '</span>' + value + '</' + kind + '>' : '';
    const text = (value, format) => esc(value == null ? missing : value === '' ? empty : format(value));
    const item = (label, left, right) => '<section class="profile-compare-field"><h4>' + esc(t(label)) +
      '</h4><div class="profile-compare-pair"><div>' + left + '</div><div>' + right + '</div></div></section>';
    const field = (key, label, format = String) => {
      const a = before[key], b = after[key], changed = a != null && b != null && a !== b;
      const left = text(a, format), right = text(b, format);
      return item(label, changed ? change(left,'del') : left, changed ? change(right,'ins') : right);
    };
    const avatar = record => record.image ? '<img src="' + esc(record.image) + '" alt="' + esc(t("Arşivlenmiş profil fotoğrafı")) +
      '">' : esc(record.picture === 'anonymous' ? t("Anonim profil fotoğrafı") : missing);
    const pictureChanged = before.picture && after.picture ? before.picture !== after.picture :
      before.image && after.image && before.image !== after.image;
    const photoCell = (record, kind) => avatar(record) + (pictureChanged ? '<br>' + change(esc(t("Fotoğraf değişti")),kind) : '');
    const biography = before.bio != null && after.bio != null
      ? diff(before.bio,after.bio) : null;
    const bio = kind => biography.filter(part => part.kind !== (kind === 'del' ? 'add' : 'remove'))
      .map(part => part.kind === 'same' ? esc(part.text) : change(esc(part.text),kind)).join('') || esc(empty);
    return item('Profil fotoğrafı',photoCell(before,'del'),photoCell(after,'ins')) +
      field('username','Kullanıcı adı', value => '@' + value) + field('name','Görünen ad') +
      item('Biyografi',biography ? bio('del') : text(before.bio,String),biography ? bio('ins') : text(after.bio,String)) +
      field('url','Web sitesi') + field('followers','Takipçiler',number) + field('following','Takip edilenler',number) +
      field('posts','Gönderiler',number);
  }
  function mountComparison(container, detail, history, format) {
    if (!container) return;
    const {t,esc,date} = format;
    const records = comparisonRecords(detail,history);
    if (records.length < 2) {
      container.textContent = t("Karşılaştırmak için iki profil kaydı gerekiyor.");
      return;
    }
    const options = records.map((record,index) => '<option value="' + index + '">' + esc(date(record.at)) + '</option>').reverse().join('');
    container.innerHTML = '<div class="profile-compare-pair profile-compare-pickers"><label>' + esc(t("Önceki kayıt")) +
      '<select data-compare-before>' + options + '</select></label><label>' + esc(t("Sonraki kayıt")) +
      '<select data-compare-after>' + options + '</select></label></div><div data-compare-result aria-live="polite"></div>';
    const before = container.querySelector('[data-compare-before]');
    const after = container.querySelector('[data-compare-after]');
    const result = container.querySelector('[data-compare-result]');
    before.value = String(records.length-2);
    after.value = String(records.length-1);
    const update = () => {
      const a = records[Number(before.value)], b = records[Number(after.value)];
      if (!a || !b || a.at >= b.at) result.textContent = t("Önceki kayıt, sonraki kayıttan daha eski olmalı.");
      else result.innerHTML = renderComparison(a,b,format);
    };
    before.addEventListener('change',update);
    after.addEventListener('change',update);
    update();
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
      <section class="wayback-section"><h3>${esc(t("İki profil kaydını karşılaştır"))}</h3><p class="field-hint">${esc(t('+ Eklenen · − Kaldırılan'))}</p><div data-profile-compare></div></section>
      <section class="wayback-section"><h3>${esc(t('Avatar galerisi'))}</h3><p>${esc(t('{0} görsel · {1} KB', photos.size, Math.ceil((detail.bytes || 0)/1024)))}</p>
      <div class="wayback-gallery">${[...photos.values()].map(p => `<figure><img src="${esc(p.url)}" alt="${esc(t('Arşivlenmiş profil fotoğrafı'))}" loading="lazy"><figcaption>${esc(t('İlk gözlem'))}: ${esc(date(p.first))}<br>${esc(t('Son gözlem'))}: ${esc(date(p.last))}</figcaption></figure>`).join('') || `<p>${esc(t('Henüz arşivlenmiş fotoğraf yok. Görsel indirilemezse eski fotoğraf üretilmez.'))}</p>`}</div></section>
      <section class="wayback-section"><h3>${esc(t('Biyografi zaman tüneli'))}</h3><p class="field-hint">${esc(t('+ Eklenen · − Kaldırılan'))}</p>${edits.reverse().map(({entry, previous}) => `<article class="wayback-edit"><time>${esc(date(entry.at))}</time><div class="bio-diff">${previous?.bio != null ? diff(previous.bio,entry.bio).map(part => part.kind === 'same' ? esc(part.text) : `<${part.kind === 'add' ? 'ins' : 'del'}><span class="diff-sign" aria-hidden="true">${part.kind === 'add' ? '+' : '−'}</span>${esc(part.text)}</${part.kind === 'add' ? 'ins' : 'del'}>`).join('') : esc(entry.bio || t('Biyografi boş'))}</div>${previous?.bio == null ? `<small>${esc(t('Başlangıç kaydı'))}</small>` : ''}</article>`).join('') || `<p>${esc(t('Arşiv kapalıyken yeni kayıt tutulmaz. Eski kayıtlar açılıp incelenebilir.'))}</p>`}</section>`;
  }
  const api = {diff, render, comparisonRecords, renderComparison, mountComparison};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TrackerWayback = Object.freeze(api);
})(typeof window === 'undefined' ? globalThis : window);
