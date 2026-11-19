/* Local request quota on the overview: how much of this account's hourly and
   24-hour budget is used. Rendering reads the state only; it sends no request. */
window.TrackerRequestQuota = Object.freeze({
  render(t, num, budget) {
    const counted = ['accountHourUsed', 'accountDayUsed', 'sharedHourUsed', 'sharedDayUsed', 'accountHourly', 'accountDaily']
      .every(key => Number.isInteger(budget?.[key]) && budget[key] >= 0);
    if (!counted) return "";
    const meter = (label, used, limit) => {
      const shown = Math.min(used, limit);
      return `<div class="quota-meter"><div class="quota-head"><span>${label}</span><strong>${t("{0} / {1}", num(used), num(limit))}</strong></div>
        <meter min="0" max="${limit}" low="${Math.round(limit * 0.6)}" high="${Math.round(limit * 0.85)}" optimum="0" value="${shown}" aria-label="${label}: ${t("{0} / {1}", num(used), num(limit))}"></meter></div>`;
    };
    return `<div class="monitor-quota" role="group" aria-label="${t("İstek kotası")}">
      <span class="eyebrow">${t("İstek kotası")}</span>
      <div class="quota-meters">${meter(t("Son 1 saat"), budget.accountHourUsed, budget.accountHourly)}${meter(t("Son 24 saat"), budget.accountDayUsed, budget.accountDaily)}</div>
      <p class="quota-note">${t("Bu hesap için uygulamanın kendi sınırıdır, Instagram’ın kotası değildir. Bütün hesaplar: son 1 saatte {0} / {1}, son 24 saatte {2} / {3}.",
        num(budget.sharedHourUsed), num(budget.sharedHourly), num(budget.sharedDayUsed), num(budget.sharedDaily))}</p>
    </div>`;
  },
});
