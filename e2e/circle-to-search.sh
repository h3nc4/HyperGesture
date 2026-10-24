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

# A long press on the middle of the bottom edge under a 1500 ms system delay. Without Google
# as the assistant it is still recognised and its launch fails gracefully. Needs e2e/device.sh.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry
warm_input

previous_delay="$("${ADB_BIN}" shell settings get secure long_press_timeout 2>/dev/null | tr -d '\r')"

restore() {
  open_home >/dev/null 2>&1
  # shellcheck disable=SC2310 # a cleanup that fails must not hide the suite's own verdict
  set_toggle_id switch_circle_to_search false >/dev/null 2>&1 || true
  if [ -z "${previous_delay}" ] || [ "${previous_delay}" = 'null' ]; then
    "${ADB_BIN}" shell settings delete secure long_press_timeout >/dev/null 2>&1 || true
  else
    "${ADB_BIN}" shell settings put secure long_press_timeout "${previous_delay}" >/dev/null 2>&1 || true
  fi
}
trap restore INT TERM EXIT

# Longer than the 1000 ms arm window, which a still finger used to time out against.
"${ADB_BIN}" shell settings put secure long_press_timeout 1500 >/dev/null
# The service reads the delay when it installs the strips, which turning the feature on does.
open_home
# shellcheck disable=SC2310 # the failure branch reports and exits, which is the point
if ! set_toggle_id switch_circle_to_search true; then
  echo "The Circle to Search cases cannot run while the feature is off." >&2
  exit 1
fi

# hold_still <x> <seconds per step> - a resting finger that jitters by a pixel, so MOVE events
# keep arriving past the arm window as they do from a real finger.
hold_still() {
  "${ADB_BIN}" shell input motionevent DOWN "$1" "${BOTTOM_Y}" >/dev/null
  for dx in 1 0 1 0; do
    sleep "$2"
    "${ADB_BIN}" shell input motionevent MOVE $(($1 + dx)) "${BOTTOM_Y}" >/dev/null
  done
  "${ADB_BIN}" shell input motionevent UP "$1" "${BOTTOM_Y}" >/dev/null
}

# dumpsys lists a bound service by its label, so the app name is what to look for.
service_healthy() {
  accessibility="$("${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r')"
  printf '%s\n' "${accessibility}" | grep -q "Bound services:.*HyperGesture" &&
    ! printf '%s\n' "${accessibility}" | grep -q "Crashed services:.*HyperGesture"
}

echo >&2
echo "== a long press on the handle fires past the arm window ==" >&2
open_test_app
reset_log
hold_still $((WIDTH / 2)) 0.5
sleep 2
assert_log "a 2 s still hold fires CircleToSearch" "GESTURE_FIRED BOTTOM CircleToSearch"
assert_no_log "the still finger was not timed out by the arm window" "unused (TOO_SLOW_TO_ARM)"
assert_no_log "the long press is not replayed to the app" "REPLAY_DISPATCHED"
assert_no_log "nor read as Home" "GESTURE_FIRED BOTTOM Home"
launch_failed=''
# shellcheck disable=SC2310 # a false predicate is a real outcome here, not an error
if log_has "CIRCLE_TO_SEARCH_FAILED"; then
  launch_failed='1'
  pass "without Google as the assistant the launch reports why it failed"
else
  echo "   the launch reported no failure, so this device has an assistant that took it" >&2
fi
# shellcheck disable=SC2310 # a false predicate is the failure being reported
if service_healthy; then
  pass "the service is still bound and not marked crashed"
else
  fail "the service is no longer bound, or is marked crashed"
  printf '%s\n' "${accessibility}" | grep -E "Bound services|Crashed services" | sed 's/^/        /' >&2
fi

echo >&2
echo "== a release before the system delay goes to the app ==" >&2
open_test_app
reset_log
# One device-side shell, since each adb round trip alone can outlast the delay.
"${ADB_BIN}" shell "input motionevent DOWN $((WIDTH / 2)) ${BOTTOM_Y}; input motionevent UP $((WIDTH / 2)) ${BOTTOM_Y}" >/dev/null
sleep 2
assert_no_log "a short hold under a 1500 ms delay does not fire" "GESTURE_FIRED BOTTOM CircleToSearch"
assert_log "it is replayed to the app underneath" "REPLAY_DISPATCHED"

echo >&2
echo "== a long press beside the handle goes to the app ==" >&2
open_test_app
reset_log
hold_still $((WIDTH / 5)) 0.5
sleep 2
assert_no_log "a hold beside the handle does not fire" "GESTURE_FIRED BOTTOM CircleToSearch"
assert_log "it is replayed to the app underneath" "REPLAY_DISPATCHED"

echo >&2
echo "== an upward swipe from the handle is still Home ==" >&2
open_test_app
reset_log
swipe $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 120
sleep 2
assert_log "a bottom swipe fires Home with the feature on" "GESTURE_FIRED BOTTOM Home"
assert_no_log "and not CircleToSearch" "GESTURE_FIRED BOTTOM CircleToSearch"

if [ -n "${launch_failed}" ]; then
  echo >&2
  echo "== Troubleshooting names the failure ==" >&2
  open_screen row_troubleshooting diag_circle_to_search_failure
  # shellcheck disable=SC2310 # a missing row is the failure reported below
  shown="$(text_of_id diag_circle_to_search_failure || true)"
  case "${shown}" in
    *'digital assistant'*) pass "Troubleshooting shows the reason for the last failure" ;;
    *) fail "Troubleshooting did not show the reason for the last failure (got: ${shown})" ;;
  esac
fi

report "CIRCLE TO SEARCH"
