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

# Boots the emulator and leaves it running, so every suite shares one boot.
#
# Usage: e2e/emulator.sh [-a avd] [-s]
#   -a  AVD name (default: hypergesture)
#   -s  stop a running emulator instead of starting one

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

stop=''
usage="usage: $0 [-a avd] [-s]"

while getopts 'a:sh' opt; do
  case "${opt}" in
    a) AVD="${OPTARG}" ;;
    s) stop='1' ;;
    h)
      echo "${usage}"
      exit 0
      ;;
    *)
      echo "${usage}" >&2
      exit 2
      ;;
  esac
done

require_emulator

if [ -n "${stop}" ]; then
  "${ADB_BIN}" emu kill >/dev/null 2>&1 || true
  pkill -f "emulator.*${AVD}" >/dev/null 2>&1 || true
  echo "emulator '${AVD}' stopped" >&2
  exit 0
fi

echo "== starting emulator '${AVD}' (headless) ==" >&2
# CPU rendering is enough: the suites inject input and read state, never pixels.
setsid "${EMULATOR_BIN}" -avd "${AVD}" \
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect \
  >"${LOG_DIR}/emulator.log" 2>&1 </dev/null &

"${ADB_BIN}" start-server >/dev/null 2>&1 || true
if ! timeout 180 "${ADB_BIN}" wait-for-device; then
  echo "Emulator never appeared. See ${LOG_DIR}/emulator.log" >&2
  exit 1
fi

echo "== waiting for boot ==" >&2
# shellcheck disable=SC2016 # getprop must run on the device, not expand here.
if ! timeout 300 "${ADB_BIN}" shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'; then
  echo "Boot never completed. See ${LOG_DIR}/emulator.log" >&2
  exit 1
fi
"${ADB_BIN}" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
"${ADB_BIN}" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true

# Gestural mode puts the gesture bar over our bottom strip and satisfies tests our service
# did nothing for. The mode comes from an overlay, so the secure setting alone does nothing.
force_three_button() {
  for mode in gestural twobutton threebutton; do
    "${ADB_BIN}" shell cmd overlay disable \
      "com.android.internal.systemui.navbar.${mode}" >/dev/null 2>&1 || true
  done
  sleep 1
  "${ADB_BIN}" shell cmd overlay enable \
    com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 || true
}

nav_mode=''
attempt=1
while [ "${attempt}" -le 6 ]; do
  force_three_button
  waited=1
  while [ "${waited}" -le 10 ]; do
    nav_mode="$("${ADB_BIN}" shell settings get secure navigation_mode 2>/dev/null | tr -d '\r')"
    if [ "${nav_mode}" = '0' ]; then
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  if [ "${nav_mode}" = '0' ]; then
    break
  fi
  echo "   navigation_mode=${nav_mode}, retrying the overlay switch (attempt ${attempt} of 6)" >&2
  attempt=$((attempt + 1))
done
if [ "${nav_mode}" != '0' ]; then
  echo "Could not switch to three-button navigation (navigation_mode=${nav_mode})." >&2
  echo "Bottom-edge cases cannot pass in gestural mode: the gesture bar outranks our" >&2
  echo "strip and claims the stream. Overlays available:" >&2
  "${ADB_BIN}" shell cmd overlay list 2>/dev/null | tr -d '\r' | grep -i navbar | sed 's/^/    /' >&2
  exit 1
fi
echo "   navigation_mode=0 (three-button)" >&2
echo "emulator '${AVD}' ready. Stop it with: e2e/emulator.sh -s" >&2
