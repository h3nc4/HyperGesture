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

# The service switched off posts a notice in the shade, and the service returning takes it down.
# Needs a device from e2e/device.sh, and binds the service again.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

notice_title="$(string_res notice_stopped_title)"

notice_posted() {
  "${ADB_BIN}" shell dumpsys notification --noredact 2>/dev/null | tr -d '\r' |
    grep 'NotificationRecord' | grep "pkg=${PKG} " | grep -q 'channel=gestures_stopped'
}

service_bound() {
  "${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r' |
    grep -q "Enabled services:.*${PKG}"
}

# wait_notice <posted|gone> - up to ten seconds, since the notice follows the unbind asynchronously.
wait_notice() {
  notice_try=1
  while [ "${notice_try}" -le 10 ]; do
    # shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
    if notice_posted; then
      [ "$1" = posted ] && return 0
    else
      [ "$1" = gone ] && return 0
    fi
    sleep 1
    notice_try=$((notice_try + 1))
  done
  return 1
}

turn_on() {
  "${ADB_BIN}" shell settings put secure accessibility_enabled 1 >/dev/null
  "${ADB_BIN}" shell settings put secure enabled_accessibility_services "${SERVICE}" >/dev/null
  bound=1
  # shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
  while [ "${bound}" -le 10 ] && ! service_bound; do
    sleep 1
    bound=$((bound + 1))
  done
}

"${ADB_BIN}" shell pm grant "${PKG}" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

echo >&2
echo "== no notice while the service runs ==" >&2
# shellcheck disable=SC2310 # the predicate's answer is the result
if service_bound && wait_notice gone; then
  pass "the shade holds no notice"
else
  fail "a notice is up while the service runs, or the service is not bound"
fi

echo >&2
echo "== the service switched off posts the notice ==" >&2
"${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null
# shellcheck disable=SC2310 # the predicate's answer is the result
if wait_notice posted; then
  pass "dumpsys notification lists the notice"
else
  fail "no notice in dumpsys notification after the switch went off"
fi

echo >&2
echo "== tapping the notice opens Accessibility ==" >&2
# A loaded emulator can leave an ANR dialog up, which ui_nodes answers before the shade opens.
ui_nodes >/dev/null
"${ADB_BIN}" shell cmd statusbar expand-notifications >/dev/null 2>&1 || true
sleep 3
"${ADB_BIN}" exec-out screencap -p >"${LOG_DIR}/stop-notice-shade.png" 2>/dev/null || true
# shellcheck disable=SC2310 # a missing node is reported by the assertion below
notice_node="$(ui_nodes | grep -m1 "text=\"${notice_title}\"" || true)"
tap_node "${notice_node}"
sleep 3
top="$(resumed_activity)"
case "${top}" in
  *com.android.settings*) pass "a tap opens Settings" ;;
  *) fail "a tap did not open Settings (top: '${top}', node: '${notice_node:-none}')" ;;
esac
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null

echo >&2
echo "== the service back on takes the notice down ==" >&2
turn_on
# shellcheck disable=SC2310 # the predicate's answer is the result
if service_bound && wait_notice gone; then
  pass "the notice left the shade by itself"
else
  fail "the notice stayed after the service bound again"
fi

report "STOP NOTICE"
