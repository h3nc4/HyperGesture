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
# A switch-off, a navigation change, a force-stop and an update each leave their own stop log entry.
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

stop_log_path="/sdcard/Android/data/${PKG}/files/stop_log.txt"

stop_log() {
  "${ADB_BIN}" shell cat "${stop_log_path}" 2>/dev/null | tr -d '\r' || true
}

stop_log_lines() {
  stop_log | awk 'NF { n++ } END { print n + 0 }'
}

# expect_entry <case> <lines before> <pattern> - one new entry matching it within ten seconds, and no other stop.
expect_entry() {
  entry_try=1
  while [ "${entry_try}" -le 10 ]; do
    added="$(stop_log | tail -n "+$(($2 + 1))")"
    if printf '%s\n' "${added}" | grep -q -- "$3"; then
      break
    fi
    sleep 1
    entry_try=$((entry_try + 1))
  done
  matching="$(printf '%s\n' "${added}" | grep -c -- "$3" || true)"
  others="$(printf '%s\n' "${added}" | grep -v -- "$3" | grep -c 'STOPPED_' || true)"
  if [ "${matching}" = 1 ] && [ "${others}" = 0 ]; then
    pass "$1"
  else
    fail "$1 (${matching} matching '$3', ${others} other stops)"
    printf '%s\n' "${added}" | sed 's/^/        /' >&2
  fi
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
lines="$(stop_log_lines)"
"${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null
# shellcheck disable=SC2310 # the predicate's answer is the result
if wait_notice posted; then
  pass "dumpsys notification lists the notice"
else
  fail "no notice in dumpsys notification after the switch went off"
fi
expect_entry "the switch-off leaves its own stop log entry" "${lines}" 'STOPPED_SWITCHED_OFF'

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

echo >&2
echo "== a navigation change is logged with both values ==" >&2
# The watch registers once the service has read its settings, a moment after the bind.
sleep 3
mode_before="$("${ADB_BIN}" shell settings get secure navigation_mode | tr -d '\r')"
case "${mode_before}" in
  0) mode_new=2 ;;
  *) mode_new=0 ;;
esac
logged_before="${mode_before}"
[ "${mode_before}" = 'null' ] && logged_before='unset'
lines="$(stop_log_lines)"
"${ADB_BIN}" shell settings put secure navigation_mode "${mode_new}" >/dev/null
expect_entry "the change leaves its own stop log entry" "${lines}" \
  "NAVIGATION_CHANGED navigation_mode ${logged_before} -> ${mode_new}, seen live"
if [ "${mode_before}" = 'null' ]; then
  "${ADB_BIN}" shell settings delete secure navigation_mode >/dev/null
else
  "${ADB_BIN}" shell settings put secure navigation_mode "${mode_before}" >/dev/null
fi
sleep 2

echo >&2
echo "== a force-stop is named on the next start ==" >&2
lines="$(stop_log_lines)"
"${ADB_BIN}" shell am force-stop "${PKG}" >/dev/null
sleep 2
# A force-stop takes the service out of the enabled list, so it binds again only when switched on.
turn_on
expect_entry "the force-stop leaves its own stop log entry" "${lines}" 'STOPPED_BY_FORCE_STOP'

echo >&2
echo "== an update is named with both versions ==" >&2
# The installed APK goes back over itself, which a debug and a release run share.
"${ADB_BIN}" shell "cp \"\$(pm path ${PKG} | head -n 1 | cut -d: -f2)\" /data/local/tmp/hg-update.apk"
lines="$(stop_log_lines)"
if "${ADB_BIN}" shell pm install -r /data/local/tmp/hg-update.apk >/dev/null; then
  expect_entry "the update leaves its own stop log entry" "${lines}" 'STOPPED_BY_UPDATE .* -> '
else
  fail "the installed APK could not be installed over itself"
fi
"${ADB_BIN}" shell rm -f /data/local/tmp/hg-update.apk >/dev/null
turn_on
# shellcheck disable=SC2310 # the predicate's answer is the result
if service_bound; then
  pass "the service is bound again after the update"
else
  fail "the service did not bind after the update"
fi

report "STOP NOTICE"
