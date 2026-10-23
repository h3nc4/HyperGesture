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

# Renders public/og/<lang>.png, the link preview cards, from og/template.html and each language's strings.
set -eu

cd "$(dirname "$0")/../"

chrome="${CHROME:-chrome-headless-shell}"
node_image="$(sed -n 's/^ *container: *//p' ../.github/workflows/site.yaml)"

uid="$(id -u)"
gid="$(id -g)"
mkdir -p og/build public/og
docker run --rm -u "${uid}:${gid}" -e HOME=/tmp -v "${PWD}:/site" -w /site "${node_image}" node scripts/og-html.mjs >og/build/names
while read -r name; do
  "${chrome}" --no-sandbox --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=1 --window-size=1200,630 --virtual-time-budget=5000 \
    --screenshot="${PWD}/public/og/${name}.png" "file://${PWD}/og/build/${name}.html"
done <og/build/names
