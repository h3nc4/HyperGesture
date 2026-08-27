#!/bin/sh
#
# Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
#
# This file is part of HyperGesture.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# Bumps versionCode in app/build.gradle.kts and prints the new value for CI to capture.

set -e

cd "$(dirname "$0")/../" || exit 1
gradle_file="app/build.gradle.kts"

current="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]\{1,\}\).*/\1/p' "${gradle_file}")"
if [ -z "${current}" ]; then
  echo "No 'versionCode = <int>' line found in ${gradle_file}" >&2
  exit 1
fi

next=$((current + 1))

# Rewrite only the versionCode line.
tmp="$(mktemp)"
trap 'rm -f "${tmp}"' INT TERM EXIT
sed 's/^\([[:space:]]*versionCode[[:space:]]*=[[:space:]]*\)[0-9]\{1,\}/\1'"${next}"'/' \
  "${gradle_file}" >"${tmp}"
mv "${tmp}" "${gradle_file}"
trap - INT TERM EXIT

echo "Bumped ${gradle_file} versionCode: ${current} -> ${next}"
