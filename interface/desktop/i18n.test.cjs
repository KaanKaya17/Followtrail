const test = require('node:test');
const assert = require('node:assert/strict');
const i18n = require('../web/i18n.js');
const { changeNotification } = require('./notifications.cjs');

test('four local languages preserve substitution values and format numbers', () => {
  assert.equal(i18n.translate('Özet', [], 'en'), 'Overview');
  assert.equal(i18n.translate('Özet', [], 'tr'), 'Özet');
  assert.equal(i18n.translate('{0} dakika', [1], 'en'), '1 minute');
  assert.equal(i18n.translate('{0} dakika', [5], 'en'), '5 minutes');
  assert.equal(i18n.translate('{0} · {1} değişiklik', ['@qa', 1], 'en'), '@qa · 1 change');
  assert.equal(i18n.translate('{0} · {1} değişiklik', ['@qa', 3], 'en'), '@qa · 3 changes');
  assert.equal(i18n.formats('tr').number.format(1200), '1.200');
  assert.equal(i18n.formats('en').number.format(1200), '1,200');
  assert.equal(i18n.translate('Özet', [], 'es'), 'Resumen');
  assert.equal(i18n.translate('Özet', [], 'pt-BR'), 'Resumo');
  assert.equal(i18n.translate('{0} dakika', [1], 'es'), '1 minuto');
  assert.equal(i18n.translate('{0} dakika', [2], 'pt-BR'), '2 minutos');
  assert.equal(i18n.formats('es').number.format(1200), '1200');
  assert.equal(i18n.formats('pt-BR').number.format(1200), '1.200');
  assert.equal(i18n.formats('en'), i18n.formats('en'));
  for (const key of ['constructor', 'toString', '__proto__', 'PRIVATE-QA-NOTE <script>'])
    assert.equal(i18n.translate(key, [], 'en'), key);
});
test('historical titles translate without changing frozen records or usernames', () => {
  const source = 'Engellenme şüphesi · @qa.person: Tam takipçi kaydında yok ve profiline erişilemiyor. Hesap kapanmış da olabilir.';
  assert.match(i18n.stored(source, 'en'), /^Suspected block · @qa.person:/);
  assert.equal(i18n.stored('@qa.person profili kontrol edildi', 'en'), '@qa.person profile checked');
  assert.equal(i18n.stored('@qa.person · Erişim kontrolü kaydedildi', 'en'), '@qa.person · Access check saved');
  assert.equal(i18n.stored('My private note: Özet', 'en'), 'My private note: Özet');
  assert.equal(i18n.stored(source, 'tr'), source);
  assert.match(i18n.stored(source, 'es'), /^Sospecha de bloqueo · @qa.person:/);
  assert.match(i18n.stored(source, 'pt-BR'), /^Suspeita de bloqueio · @qa.person:/);
});
test('Windows notification uses current language and retained selection', () => {
  const entry = { id: 'log', selectedChangeIds: ['event'], changes: [{ eventId: 'event', username: 'qa.person', category: 'followers', label: 'Takipçi sayısı', title: 'original' }] };
  const before = JSON.stringify(entry);
  const content = changeNotification(entry, 'en');
  assert.equal(content.title, 'InstagramTracker · New observation');
  assert.equal(content.body, '@qa.person · Follower count changed. Open details.');
  assert.equal(content.logId, 'log');
  assert.equal(JSON.stringify(entry), before);
  assert.match(changeNotification(entry, 'tr').body, /Takipçi sayısı değişti/);
});
test('strong suspicion notification translates the method without inventing certainty', () => {
  const title='Güçlü engellenme şüphesi · @qa.person: Aynı hesap oturumsuz görünür, bağlı oturumdan erişilemiyor. Kesin engelleme kanıtı değildir.';
  const entry={id:'strong',selectedChangeIds:['event'],changes:[{eventId:'event',username:'qa.person',category:'access',title,after:{reason:'anonymous_visible'}}]};
  const notification=changeNotification(entry,'en');
  assert.equal(notification.title,'InstagramTracker · Strong suspicion of a block');
  assert.match(notification.body,/same account is visible while logged out/);
  assert.match(notification.body,/not definitive proof/);
});
