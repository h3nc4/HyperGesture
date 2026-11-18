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

# screen_log - this package's main lines and the screen events, filtered on the device, since the
# emulator drops its adb connection while both whole buffers stream and the dump arrives cut short.
screen_log() {
  "${ADB_BIN}" shell 'logcat -d -v epoch -b main -b events | grep -F -e HyperGesture: -e power_screen_state -e screen_toggled' 2>/dev/null |
    tr -d '\r' | grep -v GEOMETRY_SIGNAL
}

show_screen_log() {
  printf '%s\n' "${captured}" | tail -30 | sed 's/^/        /' >&2
}

# first_after <log> <epoch ms> <grep-pattern> - when the first line of that log matching it was
# logged at or after that moment. A plain logcat -c leaves the events buffer in place.
first_after() {
  printf '%s\n' "$1" | grep -e "$3" |
    awk -v after="$2" '{ split($1, t, "."); ms = t[1] * 1000 + t[2]; if (ms >= after) { print ms; exit } }'
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
# The strip often returns before power_screen_state is logged, so the wait is for both lines.
woke=''
lit=''
back=''
wait_try=1
while [ "${wait_try}" -le 5 ]; do
  # shellcheck disable=SC2310 # an empty or failed dump means another try
  captured="$(screen_log || true)"
  woke="$(first_after "${captured}" 0 SCREEN_WAKE_SENT)"
  if [ -n "${woke}" ]; then
    lit="$(first_after "${captured}" "${woke}" 'power_screen_state: \[1')"
    back="$(first_after "${captured}" "${woke}" 'strip LEFT \(installed\|reattached\)')"
  fi
  if [ -n "${lit}" ] && [ -n "${back}" ]; then
    break
  fi
  sleep 1
  wait_try=$((wait_try + 1))
done
if [ -z "${back}" ]; then
  fail "the left strip never came back after screen-on"
  show_screen_log
elif [ -z "${lit}" ]; then
  fail "no screen-on logged after the wake key (${woke:-no marker}), though the left strip is back"
  show_screen_log
else
  assert_log "the screen-on check names the detached strip" 'SCREEN_ON_REPAIR.*LEFT.*attached=false'
  if [ $((back - lit)) -le "${REPAIR_BUDGET_MS}" ]; then
    pass "the left strip is back $((back - lit)) ms after screen-on, which took $((lit - woke)) ms"
  else
    fail "the left strip took $((back - lit)) ms after screen-on, over ${REPAIR_BUDGET_MS} ms"
    show_screen_log
  fi
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
