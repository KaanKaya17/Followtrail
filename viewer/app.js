import {openZip, MAX_ARCHIVE} from './zip.js';
import {readRelationships, compareLists} from './relationships.js';
import {exportInfo, listViews, filterRows, toCsv, utcDay} from './views.js';
import {MESSAGES, format} from './i18n.js';

const PAGE = 100;
const $ = id => document.getElementById(id);
let language = pickLanguage();
let current = null; // {file, result, info, views}
let second = null; // {file, result, info}
let swapped = false;
let shown = PAGE;

function pickLanguage() {
  try {
    const saved = localStorage.getItem('followtrail-viewer-language');
    if (saved === 'tr' || saved === 'en') return saved;
  } catch { /* storage may be blocked */ }
  return (navigator.language || '').toLowerCase().startsWith('tr') ? 'tr' : 'en';
}
const t = (key, ...values) => {
  const value = MESSAGES[language][key] ?? MESSAGES.en[key] ?? key;
  return typeof value === 'string' ? format(value, ...values) : value;
};
const make = (tag, text, className) => {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = text;
  if (className) node.className = className;
  return node;
};
const number = value => new Intl.NumberFormat(language).format(value);
const day = seconds => new Intl.DateTimeFormat(language, {dateStyle: 'medium', timeZone: 'UTC'}).format(new Date(seconds * 1000));

function profileLink(username) {
  const link = make('a', '@' + username);
  link.href = 'https://www.instagram.com/' + encodeURIComponent(username) + '/';
  link.rel = 'noopener noreferrer';
  link.target = '_blank';
  return link;
}

function applyLanguage() {
  document.documentElement.lang = language;
  for (const node of document.querySelectorAll('[data-t]')) node.textContent = t(node.dataset.t);
  for (const button of document.querySelectorAll('[data-language]'))
    button.setAttribute('aria-pressed', String(button.dataset.language === language));
  $('search').placeholder = t('search');
  $('search').setAttribute('aria-label', t('search'));
  const order = $('order').value || 'name';
  $('order').replaceChildren(...[['name', t('byName')], ['newest', t('newest')], ['oldest', t('oldest')]].map(([value, label]) => {
    const option = make('option', label);
    option.value = value;
    return option;
  }));
  $('order').value = order;
  if (current) renderResult();
}

function download(name, text) {
  const url = URL.createObjectURL(new Blob([text], {type: 'text/csv;charset=utf-8'}));
  const link = make('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function load(file) {
  if (!file) return null;
  if (!/\.zip$/i.test(file.name) || file.size > MAX_ARCHIVE) throw new Error(t('tooLarge'));
  try {
    const zip = await openZip(file);
    return {file, result: await readRelationships(zip), info: exportInfo(file.name)};
  } catch {
    throw new Error(t('invalid'));
  }
}

async function openFirst(file) {
  const status = $('status');
  status.textContent = t('reading');
  try {
    current = await load(file);
    second = null;
    swapped = false;
    shown = PAGE;
    $('search').value = '';
    status.textContent = '';
    $('result').hidden = false;
    renderResult();
    $('result').scrollIntoView({behavior: 'smooth', block: 'start'});
  } catch (error) {
    status.textContent = error.message;
  }
}

function renderResult() {
  const {file, result, info} = current;
  current.views = listViews(result);
  const line = [file.name];
  if (info.account) line.push(t('account', info.account));
  if (info.date) line.push(t('exportDate', day(Date.parse(info.date + 'T00:00:00Z') / 1000)));
  $('file-line').textContent = line.join(' · ');

  const notices = [];
  for (const direction of ['followers', 'following']) {
    const quality = result.quality[direction];
    if (quality && !quality.complete)
      notices.push(t('partialList', t(direction), number(quality.skipped), number(quality.missingParts.length), number(quality.unreadableParts)));
  }
  if (Object.values(result.quality).some(quality => quality.format === 'html')) notices.push(t('htmlDates'));
  if (result.ignoredSources) notices.push(t('ignored'));
  $('notices').replaceChildren(...notices.map(text => make('p', text, 'notice')));

  const cards = current.views.map(([key, rows]) => {
    const card = make('button', null, 'card');
    card.type = 'button';
    card.append(make('small', t(key)), make('strong', rows ? number(rows.length) : t('missing')));
    card.disabled = !rows;
    card.addEventListener('click', () => {
      $('list').value = key;
      shown = PAGE;
      renderList();
      $('list').scrollIntoView({behavior: 'smooth', block: 'center'});
    });
    return card;
  });
  $('cards').replaceChildren(...cards);

  const selected = $('list').value;
  $('list').replaceChildren(...current.views.filter(([, rows]) => rows).map(([key, rows]) => {
    const option = make('option', `${t(key)} · ${number(rows.length)}`);
    option.value = key;
    return option;
  }));
  if ([...$('list').options].some(option => option.value === selected)) $('list').value = selected;
  renderList();
  renderCompare();
}

function currentRows() {
  const view = current.views.find(([key]) => key === $('list').value);
  return view?.[1] ? filterRows(view[1], $('search').value, $('order').value) : [];
}

function renderList() {
  const rows = currentRows();
  $('count').textContent = rows.length ? t('countOf', number(rows.length)) : t('noRows');
  $('rows').replaceChildren(...rows.slice(0, shown).map(row => {
    const item = make('li');
    item.append(profileLink(row.username), make('small', row.timestamp ? t('since', day(row.timestamp)) : t('noDate')));
    return item;
  }));
  $('more').hidden = rows.length <= shown;
}

async function openSecond(file) {
  const target = $('compare');
  target.replaceChildren(make('p', t('reading'), 'hint'));
  try {
    second = await load(file);
    // Put the older export first when both file names carry a date.
    swapped = Boolean(current.info.date && second.info.date && current.info.date > second.info.date);
    renderCompare();
  } catch (error) {
    second = null;
    target.replaceChildren(make('p', error.message, 'notice'));
  }
}

function comparison() {
  const [before, after] = swapped ? [second, current] : [current, second];
  const directions = {};
  for (const direction of ['followers', 'following']) {
    const a = before.result[direction], b = after.result[direction];
    directions[direction] = a && b ? compareLists(a, b) : null;
  }
  return {before, after, directions};
}

function renderCompare() {
  const target = $('compare');
  if (!second) {
    target.replaceChildren();
    return;
  }
  const {before, after, directions} = comparison();
  const sources = make('div', null, 'sources');
  for (const [label, item] of [[t('older'), before], [t('newer'), after]]) {
    const box = make('div', null, 'source');
    box.append(make('small', label), make('strong', item.file.name));
    sources.append(box);
  }
  const swap = make('button', t('swap'), 'button');
  swap.type = 'button';
  swap.addEventListener('click', () => {
    swapped = !swapped;
    renderCompare();
  });
  const csv = make('button', t('compareCsv'), 'button');
  csv.type = 'button';
  csv.addEventListener('click', () => {
    const rows = [];
    for (const [direction, data] of Object.entries(directions)) {
      if (!data) continue;
      for (const change of ['added', 'removed']) for (const row of data[change]) rows.push([direction, change, row.username, '', utcDay(row.timestamp)]);
      for (const row of data.renamed) rows.push([direction, 'renamed', row.to, row.from, utcDay(row.timestamp)]);
    }
    download('followtrail-comparison.csv', toCsv(['list', 'change', 'username', 'previous_username', 'follow_date_utc'], rows));
  });
  const actions = make('div', null, 'controls');
  actions.append(swap, csv);
  const parts = [sources, actions];
  if (before.info.account && after.info.account && before.info.account !== after.info.account)
    parts.push(make('p', t('differentAccounts'), 'notice'));
  for (const direction of ['followers', 'following']) {
    const data = directions[direction];
    const box = make('section', null, 'change');
    box.append(make('h3', t(direction)));
    if (!data) {
      box.append(make('p', t('missingSide', t(direction)), 'hint'));
      parts.push(box);
      continue;
    }
    box.append(make('p', `${number(data.before)} → ${number(data.after)}`, 'delta'));
    const groups = [[t('added')[direction], data.added], [t('removed')[direction], data.removed], [t('renamed'), data.renamed]];
    if (groups.every(([, rows]) => !rows.length)) box.append(make('p', t('nothingChanged'), 'hint'));
    for (const [label, rows] of groups) {
      if (!rows.length) continue;
      const details = make('details');
      details.open = rows.length <= 30;
      details.append(make('summary', `${label} · ${number(rows.length)}`));
      const list = make('ol', null, 'rows');
      for (const row of rows) {
        const item = make('li');
        if (row.from) {
          const pair = make('span');
          pair.append(profileLink(row.from), document.createTextNode(' → '), profileLink(row.to));
          item.append(pair);
        } else item.append(profileLink(row.username));
        item.append(make('small', row.timestamp ? t('since', day(row.timestamp)) : t('noDate')));
        list.append(item);
      }
      details.append(list);
      if (rows === data.renamed) details.append(make('p', t('renamedNote'), 'hint'));
      box.append(details);
    }
    parts.push(box);
  }
  target.replaceChildren(...parts);
}

document.addEventListener('DOMContentLoaded', () => {
  applyLanguage();
  for (const button of document.querySelectorAll('[data-language]'))
    button.addEventListener('click', () => {
      language = button.dataset.language;
      try { localStorage.setItem('followtrail-viewer-language', language); } catch { /* optional */ }
      applyLanguage();
    });
  $('file').addEventListener('change', event => openFirst(event.target.files[0]));
  $('second').addEventListener('change', event => openSecond(event.target.files[0]));
  const drop = $('drop');
  drop.addEventListener('dragover', event => {
    if (!event.dataTransfer?.types.includes('Files')) return;
    event.preventDefault();
    drop.classList.add('active');
  });
  drop.addEventListener('dragleave', () => drop.classList.remove('active'));
  drop.addEventListener('drop', event => {
    event.preventDefault();
    drop.classList.remove('active');
    openFirst(event.dataTransfer.files[0]);
  });
  for (const id of ['list', 'order']) $(id).addEventListener('change', () => { shown = PAGE; renderList(); });
  $('search').addEventListener('input', () => { shown = PAGE; renderList(); });
  $('more').addEventListener('click', () => { shown += PAGE; renderList(); });
  $('csv').addEventListener('click', () => {
    const key = $('list').value;
    download(`followtrail-${key}.csv`, toCsv(['username', 'follow_date_utc'], currentRows().map(row => [row.username, utcDay(row.timestamp)])));
  });
});
