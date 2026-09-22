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

# Back, Home, Recents and the tap the app must get back. The unit tests cover the tracker,
# so what is asserted here is the Android integration around it.
#
# Expects a device prepared by e2e/device.sh. Run e2e/run.sh for everything.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry
warm_input

echo >&2
echo "== Back from the left edge ==" >&2
open_test_app
reset_log
swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
sleep 2
assert_log "left-edge swipe fires Back" "GESTURE_FIRED LEFT Back"

echo >&2
echo "== Back again (regression: strips must stay live after a gesture) ==" >&2
open_test_app
reset_log
swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
sleep 2
assert_log "a second left-edge swipe still fires Back" "GESTURE_FIRED LEFT Back"

echo >&2
echo "== Back from the right edge ==" >&2
open_test_app
reset_log
swipe "${RIGHT_X}" "${MID_Y}" $((RIGHT_X - 300)) "${MID_Y}" 120
sleep 2
assert_log "right-edge swipe fires Back" "GESTURE_FIRED RIGHT Back"

echo >&2
echo "== Home from the bottom edge ==" >&2
open_test_app
reset_log
swipe $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 120
sleep 2
assert_log "bottom swipe fires Home" "GESTURE_FIRED BOTTOM Home"
assert_no_log "a continuous bottom swipe is not mistaken for Recents" "GESTURE_FIRED BOTTOM Recents"
# Only meaningful once the gesture above is confirmed: did the system actually move.
# shellcheck disable=SC2310 # a false predicate is a real outcome here, not an error
if log_has "GESTURE_FIRED BOTTOM Home"; then
  resumed="$(resumed_activity)"
  case "${resumed}" in
    *"${PKG}"*) fail "Home fired but the resumed activity did not change: ${resumed}" ;;
    *) pass "Home actually left the test app (performGlobalAction took effect)" ;;
  esac
fi

echo >&2
echo "== Recents from a bottom swipe-and-hold ==" >&2
open_test_app
reset_log
swipe_and_hold $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 0.6
sleep 2
assert_log "bottom swipe-and-hold fires Recents" "GESTURE_FIRED BOTTOM Recents"
assert_no_log "a held bottom swipe does not also fire Home" "GESTURE_FIRED BOTTOM Home"

echo >&2
echo "== a tap near an edge is given back to the app ==" >&2
open_test_app
reset_log
"${ADB_BIN}" shell input tap 2 "${MID_Y}" >/dev/null
sleep 2
assert_no_log "an edge tap is not misread as a gesture" "GESTURE_FIRED"
assert_log "an edge tap is replayed to the app underneath" "REPLAY_DISPATCHED"
assert_no_log "the replay was not refused" "REPLAY_REFUSED"

report "NAVIGATION"
