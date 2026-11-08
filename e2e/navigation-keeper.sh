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

# The switch to keep the bar hidden shows once the permission is granted, and a reset is undone while it is on.
# Gestures stopping, by an unbind or a kill, writes the buttons back.
# Needs a debug build from e2e/device.sh, since it borrows the HyperOS integration.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "   release build, which ignores the integration override, so this suite has nothing to reach" >&2
  exit 0
fi

PERMISSION='android.permission.WRITE_SECURE_SETTINGS'

setting() {
  "${ADB_BIN}" shell settings get "$1" "$2" 2>/dev/null | tr -d '\r'
}

put() {
  "${ADB_BIN}" shell settings put "$1" "$2" "$3" >/dev/null
}

# Pressing Home pauses the activity, so opening it again is a resume that rereads the grant.
leave_app() {
  "${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
  sleep 2
}

# The HyperOS integration adds required steps the user confirms by hand, and home folds the
# checklist into its Setup row only once they are done.
confirm_required_steps() {
  did_it="$(string_res action_i_did_it)"
  confirm_try=1
  while [ "${confirm_try}" -le 4 ]; do
    open_home
    nodes="$(ui_nodes)"
    if printf '%s\n' "${nodes}" | grep -q 'resource-id="row_setup"'; then
      return 0
    fi
    tap_node "$(printf '%s\n' "${nodes}" | grep -m1 "text=\"${did_it}\"" || true)"
    sleep 2
    confirm_try=$((confirm_try + 1))
  done
}

open_setup() {
  leave_app
  confirm_required_steps
  open_screen row_setup "$1"
}

# expect_settings <description> <fsg> <mode> - polls, since the keeper answers asynchronously.
expect_settings() {
  tries=1
  while [ "${tries}" -le 8 ]; do
    fsg="$(setting global force_fsg_nav_bar)"
    mode="$(setting secure navigation_mode)"
    if [ "${fsg}" = "$2" ] && [ "${mode}" = "$3" ]; then
      pass "$1"
      return 0
    fi
    sleep 1
    tries=$((tries + 1))
  done
  fail "$1 (expected fsg=$2 mode=$3, found fsg=${fsg} mode=${mode})"
}

# Every case resets the bar by hand, then waits as long as a restore would take.
reset_bar() {
  put global force_fsg_nav_bar 0
  put secure navigation_mode 0
}

service_enabled() {
  "${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r' |
    grep -q "Enabled services:.*${PKG}"
}

turn_on_service() {
  "${ADB_BIN}" shell settings put secure accessibility_enabled 1 >/dev/null
  "${ADB_BIN}" shell settings put secure enabled_accessibility_services "${SERVICE}" >/dev/null
  bound=1
  # shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
  while [ "${bound}" -le 10 ] && ! service_enabled; do
    sleep 1
    bound=$((bound + 1))
  done
}

original_fsg="$(setting global force_fsg_nav_bar)"
original_mode="$(setting secure navigation_mode)"

# restore_setting <table> <key> <value read before the suite>, where null means it was unset.
restore_setting() {
  if [ "$3" = 'null' ]; then
    "${ADB_BIN}" shell settings delete "$1" "$2" >/dev/null 2>&1 || true
  else
    put "$1" "$2" "$3"
  fi
}

restore_device() {
  "${ADB_BIN}" shell settings delete global hypergesture_integration >/dev/null 2>&1 || true
  "${ADB_BIN}" shell pm revoke "${PKG}" "${PERMISSION}" >/dev/null 2>&1 || true
  restore_setting global force_fsg_nav_bar "${original_fsg}"
  restore_setting secure navigation_mode "${original_mode}"
}
trap restore_device EXIT

put global hypergesture_integration hyperos
"${ADB_BIN}" shell pm revoke "${PKG}" "${PERMISSION}" >/dev/null 2>&1 || true

echo >&2
echo "== without the grant the row shows the adb command ==" >&2
open_setup keep_navigation_grant
# shellcheck disable=SC2310 # a missing node is a result, reported below
if find_id keep_navigation_grant >/dev/null; then
  pass "the grant instructions show"
else
  fail "the grant instructions are missing"
fi
# shellcheck disable=SC2310 # a missing node is a result, reported below
if find_id switch_keep_navigation >/dev/null; then
  fail "the switch shows before the grant"
else
  pass "the switch waits for the grant"
fi

echo >&2
echo "== granted, the switch appears and starts off ==" >&2
"${ADB_BIN}" shell pm grant "${PKG}" "${PERMISSION}"
open_setup switch_keep_navigation
# shellcheck disable=SC2310 # a missing node is a result, reported below
node="$(find_id switch_keep_navigation || true)"
case "${node}" in
  *'checked="false"'*) pass "the switch shows, turned off" ;;
  *) fail "the switch is missing or on after the grant: ${node:-nothing}" ;;
esac

echo >&2
echo "== switched off, a reset stays ==" >&2
reset_bar
sleep 4
expect_settings "nothing is written while the switch is off" 0 0

echo >&2
echo "== switching on hides the bar at once ==" >&2
reset_log
# shellcheck disable=SC2310 # the failure is reported, and the later cases then fail too
set_toggle_id switch_keep_navigation true || fail "could not turn the switch on"
expect_settings "both settings are restored" 1 2
assert_log "the restore is logged" 'NAVIGATION_RESTORED'

echo >&2
echo "== a reset of either setting is undone ==" >&2
put secure navigation_mode 0
expect_settings "navigation_mode is put back" 1 2
put global force_fsg_nav_bar 0
expect_settings "force_fsg_nav_bar is put back" 1 2

echo >&2
echo "== the service switched off brings the buttons back ==" >&2
expect_settings "the bar is hidden while the service runs" 1 2
reset_log
"${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null
expect_settings "both settings read 0 once the service stops" 0 0
assert_log "the restore of the buttons is logged" 'NAVIGATION_BUTTONS_BACK'

echo >&2
echo "== a reset while the service was away is undone when it binds again ==" >&2
put global force_fsg_nav_bar 1
put secure navigation_mode 0
sleep 3
expect_settings "nothing is written while the service is off" 1 0
turn_on_service
expect_settings "the service restores on bind" 1 2

echo >&2
echo "== a kill with no unbind is caught when the app resumes ==" >&2
# A force stop kills the process with no callback, and the system drops the service from the enabled list.
"${ADB_BIN}" shell am force-stop "${PKG}"
sleep 3
expect_settings "the kill leaves the bar hidden" 1 2
open_test_app
expect_settings "the app resuming brings the buttons back" 0 0
turn_on_service
expect_settings "the service hides the bar again on bind" 1 2

echo >&2
echo "== switching off stops the keeper ==" >&2
open_setup switch_keep_navigation
# shellcheck disable=SC2310 # the failure is reported, and the next case then fails too
set_toggle_id switch_keep_navigation false || fail "could not turn the switch off"
sleep 2
reset_bar
sleep 4
expect_settings "a reset stays once the switch is off" 0 0

report "NAVIGATION KEEPER"
