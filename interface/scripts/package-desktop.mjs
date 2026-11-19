import { cp, mkdir, rename, writeFile, access } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.resolve(root, "../artifacts/Tracker-Desktop-UI");
const resources = path.join(output, "resources/app");
const refresh = process.argv.includes("--refresh");
if (refresh) {
  const { readFile } = await import("node:fs/promises");
  const identity = JSON.parse(
    await readFile(path.join(resources, "package.json"), "utf8"),
  );
  if (identity.name !== "instagramtracker-interface")
    throw new Error("Not an owned Tracker UI preview");
  const backup = path.resolve(
    root,
    `../artifacts/desktop-ui-source-backup-${new Date().toISOString().replace(/[:.]/g, "-")}`,
  );
  await cp(resources, backup, { recursive: true });
} else {
  try {
    await access(output);
    throw new Error(
      "Desktop output exists. Use --refresh to update only the owned app assets.",
    );
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  await mkdir(output, { recursive: true });
  await cp(path.join(root, "node_modules/electron/dist"), output, {
    recursive: true,
  });
}
await mkdir(resources, { recursive: true });
await cp(path.join(root, "web"), path.join(resources, "web"), {
  recursive: true,
});
await mkdir(path.join(resources, "desktop"), { recursive: true });
await cp(
  path.join(root, "desktop/preview.cjs"),
  path.join(resources, "desktop/main.cjs"),
);
await writeFile(
  path.join(resources, "package.json"),
  JSON.stringify(
    {
      name: "instagramtracker-interface",
      version: "0.1.0",
      main: "desktop/main.cjs",
    },
    null,
    2,
  ),
);
if (process.platform === "win32" && !refresh)
  await rename(
    path.join(output, "electron.exe"),
    path.join(output, "Followtrail.exe"),
  );
await writeFile(
  path.join(output, "ONCE-OKU.txt"),
  "Followtrail — Arayüz önizlemesi\r\n\r\nFollowtrail.exe dosyasını çalıştır. Diğer dosyaları aynı klasörde tut.\r\nBütün hesaplar ve sayılar örnektir. Instagram girişi veya veri okuma bağlı değildir.\r\nBu önizleme mevcut Windows InstagramCounter oturumunu okumaz veya değiştirmez.\r\n",
);
process.stdout.write(`Desktop preview: ${output}\n`);
