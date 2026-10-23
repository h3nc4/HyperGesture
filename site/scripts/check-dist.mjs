// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

// Fails when a built page outweighs its budget, links to an anchor or page that does not exist, or lacks its link preview image.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const dist = process.argv[2] ?? "dist";
const site = "https://hypergesture.h3nc4.com";
const budget = 100 * 1024;

function pages(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return pages(path);
    return entry.name.endsWith(".html") ? [path] : [];
  });
}

function fileFor(urlPath) {
  const clean = decodeURIComponent(urlPath.split(/[?#]/)[0]);
  const candidates = clean.endsWith("/") ? [join(dist, clean, "index.html")] : [join(dist, clean), join(dist, clean, "index.html")];
  return candidates.find((path) => existsSync(path) && statSync(path).isFile());
}

const ids = (html) => new Set([...html.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]));
const failures = [];
const rows = [];

for (const page of pages(dist)) {
  const html = readFileSync(page, "utf8");
  const name = "/" + relative(dist, page);
  const own = ids(html);

  // Everything the browser fetches to render the page, as opposed to pages it links to.
  const assets = new Set();
  for (const m of html.matchAll(/<link\b[^>]*>/g)) {
    const rel = /\brel="([^"]+)"/.exec(m[0])?.[1] ?? "";
    const href = /\bhref="([^"]+)"/.exec(m[0])?.[1];
    if (href?.startsWith("/") && /\b(preload|stylesheet|icon|modulepreload)\b/.test(rel)) assets.add(href);
  }
  for (const m of html.matchAll(/<(?:script|img|source)\b[^>]*\bsrc="(\/[^"]+)"/g)) assets.add(m[1]);
  for (const m of html.matchAll(/url\((\/[^)"']+)\)/g)) assets.add(m[1]);

  let total = Buffer.byteLength(html);
  for (const asset of assets) {
    const file = fileFor(asset);
    if (file) total += statSync(file).size;
    else failures.push(`${name}: asset ${asset} is not in ${dist}`);
  }
  // A link preview image is fetched by the sharing app, never by the page, so it stays out of the weight.
  if (/<link rel="canonical"/.test(html)) {
    for (const key of ['property="og:image"', 'name="twitter:image"']) {
      const url = new RegExp(`<meta ${key} content="([^"]+)"`).exec(html)?.[1];
      if (!url) failures.push(`${name}: no ${key} meta tag`);
      else if (!url.startsWith(`${site}/`) || !fileFor(url.slice(site.length))) failures.push(`${name}: ${key} ${url} is not in ${dist}`);
    }
  }

  rows.push({ page: name, html: Buffer.byteLength(html), assets: assets.size, total });
  if (total > budget) failures.push(`${name}: ${total} bytes with its assets, over the ${budget} byte budget`);

  for (const m of html.matchAll(/\shref="([^"]+)"/g)) {
    let href = m[1].replaceAll("&amp;", "&");
    if (href.startsWith(site)) href = href.slice(site.length) || "/";
    if (href.startsWith("#")) {
      if (href.length > 1 && !own.has(href.slice(1))) failures.push(`${name}: ${href} has no matching id`);
      continue;
    }
    if (!href.startsWith("/") || href.startsWith("//")) continue;
    const target = fileFor(href);
    if (!target) {
      failures.push(`${name}: ${href} is not in ${dist}`);
      continue;
    }
    const fragment = href.split("#")[1];
    if (fragment && target.endsWith(".html") && !ids(readFileSync(target, "utf8")).has(fragment)) {
      failures.push(`${name}: ${href} has no matching id`);
    }
  }
}

console.table(rows);
if (rows.length === 0) failures.push(`${dist} holds no pages`);
for (const failure of failures) console.error(failure);
process.exit(failures.length > 0 ? 1 : 0);
