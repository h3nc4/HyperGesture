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

# A strip that loses its window while the screen is off is back within a second of screen-on.
# Expects a device prepared by e2e/device.sh with the debug APK, which reads the detach setting.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "   release build, which ignores the detach setting, so this suite has nothing to reach" >&2
  exit 0
fi

geometry

SEAM='hypergesture_detach_strip_on_screen_off'
REPAIR_BUDGET_MS=1000

# The suites after this one need the detach setting cleared and the screen on and unlocked.
restore_screen() {
  "${ADB_BIN}" shell settings delete global "${SEAM}" >/dev/null 2>&1 || true
  "${ADB_BIN}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "${ADB_BIN}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}
trap restore_screen INT TERM EXIT

# overlay_windows - how many accessibility overlay windows this package has on screen.
overlay_windows() {
  "${ADB_BIN}" shell dumpsys window windows 2>/dev/null | tr -d '\r' | awk -v pkg="u0 ${PKG}}" '
    /^  Window #/ { mine = index($0, pkg) > 0 }
    mine && /ty=ACCESSIBILITY_OVERLAY/ { n++; mine = 0 }
    END { print n + 0 }'
}

# wait_log <seconds> <grep-pattern>
wait_log() {
  wait_try=1
  while [ "${wait_try}" -le "$1" ]; do
    # shellcheck disable=SC2310 # a missing line is the retry signal, not an error
    if log_has "$2"; then
      return 0
    fi
    sleep 1
    wait_try=$((wait_try + 1))
  done
  return 1
}

# first_after <epoch ms> <grep-pattern> - when the first main or events line matching it was
# logged at or after that moment. A plain logcat -c leaves the events buffer in place.
first_after() {
  "${ADB_BIN}" logcat -d -v epoch -b main -b events 2>/dev/null | tr -d '\r' | grep -e "$2" |
    awk -v after="$1" '{ split($1, t, "."); ms = t[1] * 1000 + t[2]; if (ms >= after) { print ms; exit } }'
}

timing_log() {
  "${ADB_BIN}" logcat -d -v epoch -b main -b events 2>/dev/null | tr -d '\r' |
    grep -e 'HyperGesture:' -e power_screen_state -e screen_toggled | grep -v GEOMETRY_SIGNAL |
    tail -30 | sed 's/^/        /' >&2
}

echo >&2
echo "== a strip detached while the screen is off is reinstalled on screen-on ==" >&2
"${ADB_BIN}" shell input keyevent KEYCODE_WAKEUP >/dev/null
"${ADB_BIN}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
sleep 1
windows_before="$(overlay_windows)"
"${ADB_BIN}" shell settings put global "${SEAM}" LEFT >/dev/null
reset_log
"${ADB_BIN}" shell input keyevent KEYCODE_SLEEP >/dev/null
# shellcheck disable=SC2310 # a timeout is reported as this case's failure
if wait_log 5 'STRIP_DETACHED_FOR_TEST LEFT'; then
  pass "the seam drops the left strip at screen-off"
else
  fail "the seam never dropped the left strip at screen-off"
fi
"${ADB_BIN}" shell settings delete global "${SEAM}" >/dev/null
windows_dark="$(overlay_windows)"
if [ "${windows_dark}" -lt "${windows_before}" ]; then
  pass "the window manager lost a strip while the screen was off (${windows_before} to ${windows_dark})"
else
  fail "the window count did not drop while the screen was off (${windows_before} to ${windows_dark})"
fi

# The marker and the key run in one device shell, so both are stamped by the device clock. The
# budget runs from power_screen_state, since the emulator alone can take over a second to light the panel.
"${ADB_BIN}" shell 'log -t HyperGesture SCREEN_WAKE_SENT; input keyevent KEYCODE_WAKEUP' >/dev/null
# shellcheck disable=SC2310 # a timeout is reported as this case's failure
if wait_log 5 'strip LEFT \(installed\|reattached\)'; then
  assert_log "the screen-on check names the detached strip" 'SCREEN_ON_REPAIR.*LEFT.*attached=false'
  woke="$(first_after 0 SCREEN_WAKE_SENT)"
  lit="$(first_after "${woke}" 'power_screen_state: \[1')"
  back="$(first_after "${woke}" 'strip LEFT \(installed\|reattached\)')"
  if [ -z "${lit}" ] || [ -z "${back}" ]; then
    fail "no screen-on (${lit:-none}) or no left strip (${back:-none}) logged after the wake key"
    timing_log
  elif [ $((back - lit)) -le "${REPAIR_BUDGET_MS}" ]; then
    pass "the left strip is back $((back - lit)) ms after screen-on, which took $((lit - woke)) ms"
  else
    fail "the left strip took $((back - lit)) ms after screen-on, over ${REPAIR_BUDGET_MS} ms"
    timing_log
  fi
else
  fail "the left strip never came back after screen-on"
  gesture_log | sed 's/^/        /' >&2
fi
sleep 1
windows_after="$(overlay_windows)"
if [ "${windows_after}" -eq "${windows_before}" ]; then
  pass "every strip window is back (${windows_after})"
else
  fail "the window count after screen-on is ${windows_after}, it was ${windows_before}"
fi

echo >&2
echo "== a screen-on with every strip alive reinstalls nothing ==" >&2
reset_log
"${ADB_BIN}" shell input keyevent KEYCODE_SLEEP >/dev/null
sleep 1
"${ADB_BIN}" shell input keyevent KEYCODE_WAKEUP >/dev/null
sleep 2
assert_no_log "no repair runs when no strip was lost" 'SCREEN_ON_REPAIR'

report "SCREEN REPAIR"
