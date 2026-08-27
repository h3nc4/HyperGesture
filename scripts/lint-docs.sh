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

# Lints the project's Markdown with Vale, including for AI writing tells.
# Styles are fetched on first run into the gitignored .vale/styles.
#

set -e

cd "$(dirname "$0")/../"

if ! command -v vale >/dev/null 2>&1; then
  echo "vale not found. Run this inside the dev container." >&2
  exit 1
fi

if [ ! -d .vale/styles/ai-tells ]; then
  echo "Fetching Vale styles..." >&2
  vale sync
fi

exec vale "$@" README.md docs/
