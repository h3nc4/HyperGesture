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

declare const process: { env: Record<string, string | undefined> };

// The release workflow passes the real values, and a local build falls back to v1.0.0.
function read(name: string, fallback: string): string {
  const value = process.env[name]?.trim();
  return value ? value : fallback;
}

function check(name: string, value: string, pattern: RegExp): string {
  if (!pattern.test(value)) throw new Error(`${name} is not valid: ${value}`);
  return value;
}

const version = check("VERSION", read("VERSION", "1.0.0").replace(/^v/, ""), /^\d+\.\d+\.\d+$/);
const apkBytes = Number(check("APK_SIZE", read("APK_SIZE", "2465170"), /^\d+$/));
const minAndroid = check("MIN_ANDROID", read("MIN_ANDROID", "11"), /^\d+$/);
const certSha256 = check(
  "CERT_SHA256",
  read("CERT_SHA256", "56561121026eaa6a9428aca85004460dba3adb8837313dd822011f8bbb741b22")
    .replaceAll(":", "")
    .toLowerCase(),
  /^[0-9a-f]{64}$/,
);

export const facts = {
  version,
  apkBytes,
  minAndroid,
  // Pairs as keytool and apksigner users read them, joined by colons.
  certPairs: certSha256.toUpperCase().match(/../g) ?? [],
};

export const links = {
  apk: "https://github.com/h3nc4/HyperGesture/releases/latest/download/hypergesture.apk",
  repo: "https://github.com/h3nc4/HyperGesture",
  releases: "https://github.com/h3nc4/HyperGesture/releases",
  issues: "https://github.com/h3nc4/HyperGesture/issues",
  contributors: "https://github.com/h3nc4/HyperGesture/graphs/contributors",
  obtainium: "obtainium://add/https://github.com/h3nc4/HyperGesture",
  site: "https://hypergesture.h3nc4.com",
};

// Megabytes in powers of ten, the unit Android's own file and storage screens use.
export function formatSize(bytes: number, locale: string): string {
  const mb = new Intl.NumberFormat(locale, { maximumFractionDigits: 1, minimumFractionDigits: 1 });
  return `${mb.format(bytes / 1_000_000)} MB`;
}
