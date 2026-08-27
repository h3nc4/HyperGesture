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

# Widens /dev/kvm and the DRI render nodes so the emulator can use hardware acceleration
# from inside the container. Both are optional: without them the emulator falls back to
# CPU rendering, so a missing device must not fail container start.

set -e

for device in /dev/kvm /dev/dri/*; do
  [ -e "${device}" ] || continue
  sudo chmod a+rw "${device}" || echo "Could not widen ${device}; emulator may fall back to CPU." >&2
done
