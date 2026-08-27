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

# Emulator on a hardware-accelerated headless display, streamed to the browser via
# noVNC, then installs the debug APK - for swiping by hand, which unit tests cannot do.
# A private TigerVNC server is used because the forwarded X display has no usable GL.
#
# Usage: scripts/emulator-vnc.sh [-a avd] [-g WxH] [-p novnc_port] [-n display]
# Then open: http://localhost:6080/vnc.html?autoconnect=true&resize=scale

set -e

avd="hypergesture"
geometry="900x1600"
novnc_port="6080"
display_num="2"
usage="usage: ${0} [-a avd] [-g WxH] [-p novnc_port] [-n display]"

while getopts "a:g:p:n:h" opt; do
  case "${opt}" in
    a) avd="${OPTARG}" ;;
    g) geometry="${OPTARG}" ;;
    p) novnc_port="${OPTARG}" ;;
    n) display_num="${OPTARG}" ;;
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

cd "$(dirname "$0")/../"

cleanup() {
  adb emu kill >/dev/null 2>&1 || true
  pkill adb >/dev/null 2>&1 || true
  pkill -f "Xtigervnc :${display_num}" 2>/dev/null || true
  pkill -f "websockify --web=/usr/share/novnc ${novnc_port}" 2>/dev/null || true
  rm -f "/tmp/.X${display_num}-lock" "/tmp/.X11-unix/X${display_num}" 2>/dev/null || true
}

vnc_port=$((5900 + display_num))
log_dir="${TMPDIR:-/tmp}/hypergesture-emulator-vnc"
mkdir -p "${log_dir}"

cleanup
sleep 1

echo "Starting TigerVNC X server on :${display_num} (${geometry})" >&2
setsid Xtigervnc ":${display_num}" -geometry "${geometry}" -depth 24 \
  -SecurityTypes None -localhost yes -rfbport "${vnc_port}" -desktop "${avd}" \
  >"${log_dir}/xvnc.log" 2>&1 </dev/null &

# Wait for the X socket before starting GL clients.
i=0
while [ ! -S "/tmp/.X11-unix/X${display_num}" ] && [ "${i}" -lt 50 ]; do
  i=$((i + 1))
  sleep 1
done

echo "Starting window manager (openbox)" >&2
DISPLAY=":${display_num}" setsid openbox \
  >"${log_dir}/openbox.log" 2>&1 </dev/null &

echo "Starting emulator '${avd}' (-gpu host)" >&2
DISPLAY=":${display_num}" setsid emulator -avd "${avd}" -gpu host \
  -no-snapshot-save -no-audio -no-boot-anim \
  >"${log_dir}/emulator.log" 2>&1 </dev/null &

echo "Starting noVNC bridge on :${novnc_port}" >&2
setsid websockify --web=/usr/share/novnc "${novnc_port}" "localhost:${vnc_port}" \
  >"${log_dir}/novnc.log" 2>&1 </dev/null &

echo "Waiting for Android to boot" >&2
adb start-server >/dev/null 2>&1 || true
timeout 120 adb wait-for-device || true
# Let the device-side shell do the polling so there is no host pipe to mask.
# shellcheck disable=SC2016 # getprop must run on the device, not expand here.
timeout 180 adb shell \
  'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done' \
  >/dev/null 2>&1 || true

trap cleanup INT TERM EXIT

echo "Building and installing the debug APK" >&2
gradle --no-daemon installDebug

echo >&2
echo "  Emulator '${avd}' is up. Open in your browser:" >&2
echo "    http://localhost:${novnc_port}/vnc.html?autoconnect=true&resize=scale" >&2
echo >&2
echo "  Enable the accessibility service, then swipe from the edges:" >&2
echo "    adb shell settings put secure enabled_accessibility_services \\" >&2
echo "      com.h3nc4.hypergesture/com.h3nc4.hypergesture.service.HyperGestureAccessibilityService" >&2
echo "    adb shell settings put secure accessibility_enabled 1" >&2
echo >&2
echo "  Logs: ${log_dir}/" >&2
echo >&2

# Keep the script in the foreground so the trap tears everything down on Ctrl-C.
wait
