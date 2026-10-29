import {
  access,
  cp,
  mkdir,
  readFile,
  rename,
  writeFile,
} from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const version = JSON.parse(
  await readFile(path.join(root, "package.json"), "utf8"),
).version;
const output = path.resolve(
  root,
  `../artifacts/InstagramTracker-Desktop-${version}`,
);
const resources = path.join(output, "resources/app");
const worker = path.join(root, ".cache/worker-dist/tracker-worker");
await access(path.join(worker, "tracker-worker.exe"));
const refresh = process.argv.includes("--refresh");
if (refresh) {
  const identity = JSON.parse(
    await readFile(path.join(resources, "package.json"), "utf8"),
  );
  if (identity.name !== "instagramtracker-desktop")
    throw new Error("Not an owned functional desktop output");
  const backup = path.resolve(
    root,
    `../artifacts/desktop-${version}-source-${new Date().toISOString().replace(/[:.]/g, "-")}`,
  );
  await cp(resources, backup, { recursive: true });
} else {
  try {
    await access(output);
    throw new Error(
      "Output exists; use --refresh after ownership verification.",
    );
  } catch (e) {
    if (e.code !== "ENOENT") throw e;
  }
  await mkdir(output, { recursive: true });
  await cp(path.join(root, "node_modules/electron/dist"), output, {
    recursive: true,
  });
  await rename(
    path.join(output, "electron.exe"),
    path.join(output, "InstagramTracker.exe"),
  );
}
await mkdir(path.join(resources, "desktop"), { recursive: true });
await mkdir(path.join(resources, "web"), { recursive: true });
for (const name of [
  "main.cjs",
  "theme.cjs",
  "preload.cjs",
  "service.cjs",
  "notifications.cjs",
  "background.cjs",
  "renderer-updates.cjs",
])
  await cp(
    path.join(root, "desktop", name),
    path.join(resources, "desktop", name),
  );
for (const name of [
  "desktop.html",
  "appearance.js",
  "desktop.js",
  "command-palette.js",
  "operation-progress.js",
  "desktop.css",
  "wayback.js",
  "wayback.css",
  "theme.css",
  "style.css",
  "icons.js",
  "i18n.js",
  "favicon.svg",
])
  await cp(path.join(root, "web", name), path.join(resources, "web", name));
await cp(worker, path.join(resources, "worker"), { recursive: true });
await cp(
  path.join(root, ".cache/tray-icon.png"),
  path.join(resources, "desktop/tray.png"),
);
await cp(
  path.join(root, ".cache/worker-licenses"),
  path.join(output, "Python-dependency-notices"),
  { recursive: true },
);
await writeFile(
  path.join(resources, "package.json"),
  JSON.stringify(
    {
      name: "instagramtracker-desktop",
      version,
      main: "desktop/main.cjs",
    },
    null,
    2,
  ),
);
await writeFile(
  path.join(output, "ONCE-OKU.txt"),
  `InstagramTracker Windows ${version}\r\n\r\nInstagramTracker.exe dosyasını aç. Klasördeki diğer dosyaları aynı yerde tut. Python kurman gerekmez.\r\nHesap bağlantısından giriş yap veya önceki Windows prototipindeki oturumu doğrulayarak aktar.\r\nSol alttaki hesap menüsünden Hesap ekle ile ayrı şifreli hesaplar oluşturup oturum kapatmadan geçiş yapabilirsin. Kontrol/bildirim tercihleri hesap başına ayrıdır; istek sırası ve 429 molası ortaktır. Yerel verileri sil yalnız seçili hesabı siler. Güncellemeden sonra eski sürümü aynı veri klasöründe kullanma.\r\nProfil sayıları, listeler ve notlar yerel şifreli depoda tutulur. Instagram erişimi sunucunun izin verdiği ölçüde çalışır.\r\nTakipçi ve takip edilen listelerini ayrı ayrı tara. Kısmi listeler tam kayıt sayılmaz.\r\nOtomatik profil kontrolü Ayarlar’dan açılır. 1/5/15/30/60/180/360 dakika seçenekleri her profil için ayrı uygulanır; profil sayısıyla çarpılmaz. İstekler arasında 12–18 saniyelik rastgele aralık ayrılır; gerçek bekleme daha uzun olabilir. Pencereyi kapatınca sistem tepsisinde devam eder; tamamen çıkmak için tepsi simgesine sağ tıkla ve Tamamen çık seç.\r\nKaybolan kayıtlı takipçi incelemesi Ayarlar’dan isteğe bağlı açılır. Engellenme şüphesi kesin engelleme tespiti değildir; hesap kapanmış da olabilir.\r\nHesaba göre bildirim türleri kişi ayrıntısındaki Bildirim tercihleri veya Ayarlar üzerinden seçilir. Aynı kontrol tek bildirime toplanır; eski/yeni değerler Bildirimler ekranında açılır.\r\nŞifreli yedekler oturum içermez; aynı Windows kullanıcısında açılır. CSV dosyaları şifresizdir.\r\nBu uygulama Instagram’dan bağımsızdır.\r\n`,
);
await writeFile(
  path.join(output, "START-HERE.txt"),
  `InstagramTracker for Windows ${version}\r\n\r\nRun InstagramTracker.exe and keep all files in this folder together. No Python installation is required.\r\nChoose English or Turkish in Settings > App language. Your choice is saved locally.\r\nUse the bottom-left account selector to add and switch accounts without signing out. Each account has its own encrypted vault and monitoring preferences; request pacing and 429 pauses are shared. Delete local data affects only the selected account. Do not use an older app version with the same data folder after upgrading.\r\nSessions and observations are encrypted for your Windows user. Passwords and verification codes are not saved.\r\nAccount requests go directly to Instagram; no intermediary or translation service receives your data.\r\nClosing the window keeps checks running in the system tray. Right-click the tray icon > Quit completely to stop the app.\r\nInstagram may limit access or require verification. A suspected block is not definitive evidence of a block.\r\nThe browser preview uses sample data; real account features are in this Windows app.\r\nInstagramTracker is independent of Instagram.\r\n`,
);
console.log(output);
