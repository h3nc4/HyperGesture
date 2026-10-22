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

// Prints VERSION, APK_SIZE and CERT_SHA256 lines for $GITHUB_ENV, from the release workflow's
// inputs when it passes them, or else from the latest published release on GitHub.
const given = {
  VERSION: process.env.IN_VERSION ?? "",
  APK_SIZE: process.env.IN_APK_SIZE ?? "",
  CERT_SHA256: process.env.IN_CERT_SHA256 ?? "",
};

if (given.VERSION) {
  for (const [name, value] of Object.entries(given)) if (value) console.log(`${name}=${value}`);
  process.exit(0);
}

const repo = process.env.GITHUB_REPOSITORY ?? "h3nc4/HyperGesture";
const headers = { Accept: "application/vnd.github+json" };
if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
const response = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers });
if (!response.ok) throw new Error(`latest release lookup answered ${response.status}`);
const release = await response.json();
const apk = release.assets.find((asset) => asset.name === "hypergesture.apk");
if (!apk) throw new Error(`release ${release.tag_name} has no hypergesture.apk`);

// Every release shares one signing key, and the default in src/facts.ts is its certificate.
console.log(`VERSION=${release.tag_name}`);
console.log(`APK_SIZE=${apk.size}`);
