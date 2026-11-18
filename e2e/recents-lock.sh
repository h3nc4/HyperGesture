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

# The Lock in Recents step shows how to lock the app, and the mark the user gives it is kept across a restart.
# Needs a debug build from e2e/device.sh, since the step shows only on HyperOS.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "   release build, which ignores the integration override, so this suite has nothing to reach" >&2
  exit 0
fi

STEP='step_recents_lock'

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

# Home lists the required steps until they are all done, then only the Setup row leads to them.
show_step() {
  open_home
  # shellcheck disable=SC2310 # a missing node sends it to the Setup screen
  find_id "${STEP}" >/dev/null || open_screen row_setup "${STEP}"
}

# expect_step_text <description> <text> - polls, since a tap writes the datastore asynchronously.
expect_step_text() {
  shown=''
  text_try=1
  while [ "${text_try}" -le 6 ]; do
    # shellcheck disable=SC2310 # a missing node is the retry signal, reported below
    shown="$(text_of_id "${STEP}" || true)"
    if [ "${shown}" = "$2" ]; then
      pass "$1"
      return 0
    fi
    sleep 1
    text_try=$((text_try + 1))
  done
  fail "$1 (expected '$2', found '${shown:-nothing}')"
}

restore_device() {
  "${ADB_BIN}" shell settings delete global hypergesture_integration >/dev/null 2>&1 || true
  turn_on_service
}
trap restore_device EXIT

"${ADB_BIN}" shell settings put global hypergesture_integration hyperos >/dev/null
turn_on_service

body="$(string_res step_recents_lock_body)"
confirmed="$(string_res step_confirmed)"

echo >&2
echo "== with the service on, the step explains how to lock the app ==" >&2
show_step
# A mark left by an earlier run is undone, so the case starts from an unmarked step.
# shellcheck disable=SC2310 # no Undo button means the step starts unmarked
if find_id "${STEP}_undo" >/dev/null; then
  tap_id "${STEP}_undo"
  sleep 2
  show_step
fi
expect_step_text "the step shows its explanation" "${body}"

echo >&2
echo "== I did it marks the step done ==" >&2
# shellcheck disable=SC2310 # a missing button is reported, and the cases below then fail too
tap_id "${STEP}_confirm" || fail "the step has no I did it button"
sleep 2
show_step
expect_step_text "the step reads marked done" "${confirmed}"

echo >&2
echo "== the mark survives a restart of the app ==" >&2
# A force stop kills the process. The reopened app then reads the mark back from the datastore.
"${ADB_BIN}" shell am force-stop "${PKG}"
sleep 2
turn_on_service
show_step
expect_step_text "the step still reads marked done after the restart" "${confirmed}"

echo >&2
echo "== Undo clears the mark ==" >&2
# shellcheck disable=SC2310 # a missing button is reported below
tap_id "${STEP}_undo" || fail "the marked step has no Undo button"
sleep 2
show_step
expect_step_text "the step asks again once undone" "${body}"

report "RECENTS LOCK"
