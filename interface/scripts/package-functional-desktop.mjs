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
const output = path.resolve(
  root,
  "../artifacts/InstagramTracker-Desktop-0.2.0",
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
    `../artifacts/desktop-0.2-source-${new Date().toISOString().replace(/[:.]/g, "-")}`,
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
  "preload.cjs",
  "service.cjs",
  "notifications.cjs",
])
  await cp(
    path.join(root, "desktop", name),
    path.join(resources, "desktop", name),
  );
for (const name of [
  "desktop.html",
  "desktop.js",
  "desktop.css",
  "theme.css",
  "style.css",
  "icons.js",
  "favicon.svg",
])
  await cp(path.join(root, "web", name), path.join(resources, "web", name));
await cp(worker, path.join(resources, "worker"), { recursive: true });
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
      version: "0.2.0",
      main: "desktop/main.cjs",
    },
    null,
    2,
  ),
);
await writeFile(
  path.join(output, "ONCE-OKU.txt"),
  "InstagramTracker Windows 0.2.0\r\n\r\nInstagramTracker.exe dosyasını aç. Klasördeki diğer dosyaları aynı yerde tut. Python kurman gerekmez.\r\nHesap bağlantısından giriş yap veya önceki Windows prototipindeki oturumu doğrulayarak aktar.\r\nProfil sayıları, listeler ve notlar yerel şifreli depoda tutulur. Instagram erişimi sunucunun izin verdiği ölçüde çalışır.\r\nTakipçi ve takip edilen listelerini ayrı ayrı tara. Kısmi listeler tam kayıt sayılmaz.\r\nOtomatik profil kontrolü yalnız uygulama açıkken ve Ayarlar’dan açıldığında çalışır.\r\nŞifreli yedekler oturum içermez; aynı Windows kullanıcısında açılır. CSV dosyaları şifresizdir.\r\nBu uygulama Instagram’dan bağımsızdır.\r\n",
);
console.log(output);
