import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../web",
);
const port = Number(process.env.TRACKER_UI_PORT || 4173);
if (!Number.isInteger(port) || port < 1024 || port > 65535)
  throw new Error("Invalid preview port");
const allowed = new Map([
  ["/", "index.html"],
  ["/index.html", "index.html"],
  ["/theme.css", "theme.css"],
  ["/style.css", "style.css"],
  ["/app.js", "app.js"],
  ["/icons.js", "icons.js"],
  ["/i18n.js", "i18n.js"],
  ["/favicon.svg", "favicon.svg"],
]);
const types = {
  ".html": "text/html",
  ".css": "text/css",
  ".js": "text/javascript",
  ".svg": "image/svg+xml",
};
const server = http.createServer(async (request, response) => {
  const file = allowed.get(new URL(request.url, "http://localhost").pathname);
  if (request.method !== "GET" && request.method !== "HEAD") {
    response.writeHead(405);
    response.end();
    return;
  }
  if (!file) {
    response.writeHead(404);
    response.end("Not found");
    return;
  }
  try {
    const data = await readFile(path.join(root, file));
    response.writeHead(200, {
      "Content-Type": `${types[path.extname(file)]}; charset=utf-8`,
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
      "Content-Security-Policy":
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'none'; font-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
    });
    response.end(request.method === "HEAD" ? undefined : data);
  } catch {
    response.writeHead(500);
    response.end("Preview file unavailable");
  }
});
server.listen(port, "127.0.0.1", () =>
  process.stdout.write(`Local: http://127.0.0.1:${port}\n`),
);
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () => server.close());
