// Generated output embeds the local catalog here; there are no remote loads.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TrackerI18n = factory();
})(globalThis, function () {
  'use strict';
  const en = /* TRANSLATIONS */ {};
  const locales = { tr: 'tr-TR', en: 'en-US' };
  const formatterCache = new Map();
  function valid(language) { return language === 'tr' || language === 'en'; }
  function locale(language) { return locales[language] || locales.tr; }
  function formats(language) {
    const key = valid(language) ? language : 'tr';
    if (!formatterCache.has(key)) formatterCache.set(key, {
      number: new Intl.NumberFormat(locale(key)),
      date: new Intl.DateTimeFormat(locale(key), { dateStyle: 'medium', timeStyle: 'short' }),
      day: new Intl.DateTimeFormat(locale(key), { dateStyle: 'full' }),
    });
    return formatterCache.get(key);
  }
  function translate(message, values = [], language = 'tr') {
    if (typeof message !== 'string') return message;
    let text = language === 'en' && Object.hasOwn(en, message) ? en[message] : message;
    if (typeof text !== 'string') {
      const count = values[text.count || 0];
      text = count === 1 || count === '1' ? text.one : text.other;
    }
    return values.length ? text.replace(/\{(\d+)\}/g, (slot, index) => index < values.length ? String(values[index]) : slot) : text;
  }
  // Parse only app-owned historical titles, never user bios, notes or names.
  const storedKeys = [
    '@{0} profili kontrol edildi',
    '@{0} · Erişim kontrolü kaydedildi',
    'Engellenme şüphesi · @{0}: Tam takipçi kaydında yok ve profiline erişilemiyor. Hesap kapanmış da olabilir.',
    'Güçlü engellenme şüphesi · @{0}: Aynı hesap oturumsuz görünür, bağlı oturumdan erişilemiyor. Kesin engelleme kanıtı değildir.',
  ];
  const storedPatterns = storedKeys.map(key => [key, new RegExp('^' + key.split(/\{\d+\}/).map(part => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('([A-Za-z0-9._]+)') + '$')]);
  function stored(message, language = 'tr') {
    if (language !== 'en' || typeof message !== 'string' || Object.hasOwn(en, message)) return translate(message, [], language);
    for (const [key, pattern] of storedPatterns) {
      const match = message.match(pattern);
      if (match) return translate(key, match.slice(1), language);
    }
    return message;
  }
  return Object.freeze({ translate, stored, valid, locale, formats, keys: Object.freeze(Object.keys(en)) });
});
