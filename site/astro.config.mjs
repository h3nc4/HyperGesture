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

import { defineConfig } from "astro/config";

export default defineConfig({
  site: "https://hypergesture.h3nc4.com",
  output: "static",
  trailingSlash: "always",
  // Inline CSS saves a round trip on slow mobile data, and the whole sheet is a few kilobytes.
  build: { format: "directory", inlineStylesheets: "always" },
  devToolbar: { enabled: false },
});
