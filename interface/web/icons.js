(() => {
  const paths = {
    chart: '<path d="m3 17 6-6 4 3 8-9M15 5h6v6"/>',
    home: '<path d="m3 10 9-7 9 7M5 9v12h5v-6h4v6h5V9"/>',
    people:
      '<circle cx="9" cy="8" r="3"/><path d="M3 21v-2a6 6 0 0 1 12 0v2m1-16a3 3 0 0 1 0 6m2 3a5 5 0 0 1 3 5v2"/>',
    list: '<path d="M9 6h12M9 12h12M9 18h12M3 6h1M3 12h1M3 18h1"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l4 2"/>',
    settings:
      '<path d="m9 3-1 3-3 1-2 3 2 2-1 3 2 3 3-1 3 2 3-1 1-3 3-1 2-3-2-2 1-3-2-3-3 1-3-2Z"/><circle cx="12" cy="11" r="3"/>',
    search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
    chevron: '<path d="m9 5 7 7-7 7"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
    arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
    up: '<path d="m6 14 6-6 6 6"/>',
    refresh: '<path d="M20 9a8 8 0 1 0 0 7M20 3v6h-6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    lock: '<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v3"/>',
    star: '<path d="m12 3 2.8 5.7 6.3.9-4.5 4.4 1 6.4-5.6-3-5.6 3 1-6.4L3 9.6l6.2-.9Z"/>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-8 13h4"/>',
    compare: '<path d="M4 7h16l-4-4M20 17H4l4 4"/>',
    check: '<path d="m5 12 5 5L20 6"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
    link: '<path d="m9 15 6-6m-7 8-1 1a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0m0 10a4 4 0 0 0 6 0l5-5a4 4 0 0 0-6-6l-1 1"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    calendar:
      '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 11h18"/>',
    note: '<path d="M14 3H5v18h14V8l-5-5Zm0 0v5h5M8 12h8m-8 4h6"/>',
    filter: '<path d="M4 7h16M7 12h10m-7 5h4"/>',
    shield:
      '<path d="m12 3 8 3v6c0 4-4 7-8 9-4-2-8-5-8-9V6l8-3Z"/><path d="m8 12 3 3 5-6"/>',
    laptop:
      '<rect x="4" y="3" width="16" height="13" rx="2"/><path d="m4 16-2 5h20l-2-5"/>',
    photo:
      '<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 5-5 4 4 4-6 5 7"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    logout: '<path d="M9 4H4v16h5m5-14 6 6-6 6m-7-6h13"/>',
  };
  window.TrackerIcon = (name, extra = "") =>
    `<svg class="icon ${extra}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.chart}</svg>`;
})();
