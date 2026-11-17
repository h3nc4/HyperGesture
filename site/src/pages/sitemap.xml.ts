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

import type { APIRoute } from "astro";
import { links } from "../facts";
import { en } from "../i18n/en";
import { pt } from "../i18n/pt";

const languages = [en, pt];
const url = (path: string) => new URL(path, links.site).href;
const alternates = languages.map((t) => `<xhtml:link rel="alternate" hreflang="${t.lang}" href="${url(t.home)}"/>`).join("");

export const GET: APIRoute = () => {
  const entries = languages.map((t) => `<url><loc>${url(t.home)}</loc>${alternates}</url>`).join("\n");
  const body = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${entries}
</urlset>
`;
  return new Response(body, { headers: { "Content-Type": "application/xml" } });
};
