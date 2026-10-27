import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const theme = JSON.parse(
  await readFile(path.join(root, "design/theme.json"), "utf8"),
);
const colorKeys = [
  "background",
  "surface",
  "ink",
  "muted",
  "violet",
  "violetSoft",
  "coral",
  "peach",
  "teal",
  "mint",
  "line",
];
if (JSON.stringify(Object.keys(theme.colors)) !== JSON.stringify(colorKeys))
  throw new Error("Unexpected color contract");
for (const value of Object.values(theme.colors))
  if (!/^#[a-f0-9]{6}$/.test(value)) throw new Error("Invalid color");
for (const [key, value] of Object.entries(theme.radii))
  if (
    !["card", "button", "control"].includes(key) ||
    !Number.isInteger(value) ||
    value < 0 ||
    value > 48
  )
    throw new Error("Invalid radius");
const kebab = (name) =>
  name.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
const css = `/* Generated from design/theme.json. Run npm run theme from interface/. */\n:root {\n${Object.entries(
  theme.colors,
)
  .map(([key, value]) => `  --${kebab(key)}: ${value};`)
  .join("\n")}\n${Object.entries(theme.radii)
  .map(([key, value]) => `  --radius-${key}: ${value}px;`)
  .join("\n")}\n}\n`;
const kotlin = `package dev.kaankaya.instagramtracker\n\n// Generated from design/theme.json by interface/scripts/generate-theme.mjs.\ninternal object UiPalette {\n${Object.entries(
  theme.colors,
)
  .map(
    ([key, value]) =>
      `    val ${key}: Int = 0xFF${value.slice(1).toUpperCase()}.toInt()`,
  )
  .join("\n")}\n${Object.entries(theme.radii)
  .map(([key, value]) => `    const val ${key}Radius = ${value}`)
  .join("\n")}\n}\n`;
const files = [
  [path.join(root, "interface/web/theme.css"), css],
  [
    path.join(
      root,
      "app/src/main/java/dev/kaankaya/instagramtracker/UiPalette.kt",
    ),
    kotlin,
  ],
];
if (process.argv.includes("--check")) {
  for (const [file, expected] of files)
    if ((await readFile(file, "utf8")).replace(/\r\n/g, "\n") !== expected)
      throw new Error(`Generated theme is stale: ${file}`);
  console.log("Android / web / desktop theme matches its shared source.");
} else {
  for (const [file, contents] of files) await writeFile(file, contents);
  console.log("Generated Android and web theme.");
}
