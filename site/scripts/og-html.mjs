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

// Fills og/template.html with each language's strings, for scripts/og-images.sh to screenshot.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { registerHooks } from "node:module";

// The strings import each other without an extension, the way Vite resolves them for Astro.
registerHooks({
  resolve(specifier, context, next) {
    if (specifier.startsWith(".") && !/\.\w+$/.test(specifier)) return next(`${specifier}.ts`, context);
    return next(specifier, context);
  },
});

const { en } = await import("../src/i18n/en.ts");
const { pt } = await import("../src/i18n/pt.ts");
const { ogImage } = await import("../src/og.ts");

const escape = (text) => text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
const template = readFileSync(new URL("../og/template.html", import.meta.url), "utf8");
const out = new URL("../og/build/", import.meta.url);
mkdirSync(out, { recursive: true });

for (const t of [en, pt]) {
  const image = ogImage(t);
  const values = { lang: t.lang, title: t.title, headline: t.hero.title, line: image.line };
  const html = template.replaceAll(/\{\{(\w+)\}\}/g, (_, key) => escape(values[key]));
  const name = image.path.split("/").pop().replace(/\.png$/, "");
  writeFileSync(new URL(`${name}.html`, out), html);
  console.log(name);
}
