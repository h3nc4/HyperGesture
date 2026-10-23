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

import type { Strings } from "./i18n/en";

// The card shows the lede's last sentence, and the alt repeats the card's text, so neither needs a translation of its own.
export function ogImage(t: Strings) {
  const line = t.hero.lede.split(/(?<=\.) /).pop() ?? t.hero.lede;
  return {
    line,
    path: `/og/${t.lang.split("-")[0]}.png`,
    width: 1200,
    height: 630,
    alt: `HyperGesture. ${t.hero.title}. ${line}`,
  };
}

export const ogLocale = (t: Strings) => t.locale.replace("-", "_");
