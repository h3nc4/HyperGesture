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

# With the grant and the switch on, the service taken out of enabled_accessibility_services is put back.
# Without the grant, or with the switch off, nothing is written. A second removal within a minute stays.
# Needs a debug build from e2e/device.sh, since the switch shows only under the HyperOS integration.

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

services() {
  "${ADB_BIN}" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r'
}

listed() {
  case "$(services)" in
    *"${SERVICE}"*) return 0 ;;
    *) return 1 ;;
  esac
}

service_bound() {
  "${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r' |
    grep -q "Enabled services:.*${PKG}"
}

remove_service() {
  "${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null
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

# expect_bound <description> - up to ten seconds for the app to write the setting and the framework to bind.
expect_bound() {
  tries=1
  while [ "${tries}" -le 10 ]; do
    # shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
    if listed && service_bound; then
      pass "$1 (after about ${tries}s)"
      return 0
    fi
    sleep 1
    tries=$((tries + 1))
  done
  # shellcheck disable=SC2310 # an unreadable setting prints as empty, which the message shows
  now="$(services || true)"
  fail "$1 (enabled_accessibility_services reads '${now}')"
}

# expect_off <description> - waits as long as a restore would take, then checks nothing came back.
expect_off() {
  sleep 6
  # shellcheck disable=SC2310 # the predicate's answer is the result
  if listed; then
    now="$(services || true)"
    fail "$1 (enabled_accessibility_services reads '${now}')"
  else
    pass "$1"
  fi
}

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

restore_device() {
  turn_on
  "${ADB_BIN}" shell settings delete global hypergesture_integration >/dev/null 2>&1 || true
  "${ADB_BIN}" shell pm revoke "${PKG}" "${PERMISSION}" >/dev/null 2>&1 || true
}
trap restore_device EXIT

"${ADB_BIN}" shell settings put global hypergesture_integration hyperos >/dev/null
"${ADB_BIN}" shell pm grant "${PKG}" "${PERMISSION}"
turn_on

echo >&2
echo "== granted, the switch shows and starts off ==" >&2
open_setup switch_restore_service
# shellcheck disable=SC2310 # a missing node is a result, reported below
node="$(find_id switch_restore_service || true)"
case "${node}" in
  *'checked="false"'*) pass "the switch shows, turned off" ;;
  *) fail "the switch is missing or on after the grant: ${node:-nothing}" ;;
esac

echo >&2
echo "== switched off, a removal stays ==" >&2
remove_service
expect_off "nothing is written while the switch is off"
turn_on

echo >&2
echo "== switched on, a removal is put back ==" >&2
open_setup switch_restore_service
# shellcheck disable=SC2310 # the failure is reported, and the later cases then fail too
set_toggle_id switch_restore_service true || fail "could not turn the switch on"
leave_app
reset_log
remove_service
expect_bound "the service is back in the setting and bound"
assert_log "the restore is logged" 'SERVICE_RESTORED'
recorded="$("${ADB_BIN}" shell run-as "${PKG}" cat files/last_failure.txt 2>/dev/null | tr -d '\r' || true)"
case "${recorded}" in
  *'label: service restore'*'SERVICE_RESTORED'*) pass "the restore is in the failure record" ;;
  *) fail "the failure record holds no restore: ${recorded:-nothing}" ;;
esac

echo >&2
echo "== removed again within a minute, it stays off ==" >&2
reset_log
remove_service
expect_off "a second removal is left alone"
assert_log "the hold is logged" 'SERVICE_KEPT_OFF'
turn_on

echo >&2
echo "== without the grant, a removal writes nothing ==" >&2
"${ADB_BIN}" shell pm revoke "${PKG}" "${PERMISSION}" >/dev/null 2>&1 || true
turn_on
reset_log
remove_service
expect_off "nothing is written without the grant"
assert_no_log "no restore is logged" 'SERVICE_RESTORED\|SERVICE_NOT_RESTORED'
turn_on

echo >&2
echo "== the switch goes back off for the suites after this one ==" >&2
"${ADB_BIN}" shell pm grant "${PKG}" "${PERMISSION}"
open_setup switch_restore_service
# shellcheck disable=SC2310 # the failure is reported below
if set_toggle_id switch_restore_service false; then
  pass "the switch is off again"
else
  fail "could not turn the switch off"
fi

report "SERVICE RESTORE"
