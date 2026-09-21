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

# Sideways swipes along the bottom edge, which walk the apps the user came from.
#
# Only the recognizer is asserted: this emulator reports no usage events, so the switch
# itself cannot land here. That half was proven by hand on hardware.
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
echo "== a rightward bottom swipe goes back an app ==" >&2
open_test_app
reset_log
swipe 300 "${BOTTOM_Y}" 800 "${BOTTOM_Y}" 120
sleep 2
assert_log "a rightward bottom swipe fires PreviousApp" "GESTURE_FIRED BOTTOM PreviousApp"
assert_no_log "it is not mistaken for Home" "GESTURE_FIRED BOTTOM Home"

echo >&2
echo "== a leftward bottom swipe goes forward again ==" >&2
open_test_app
reset_log
swipe 800 "${BOTTOM_Y}" 300 "${BOTTOM_Y}" 120
sleep 2
assert_log "a leftward bottom swipe fires NextApp" "GESTURE_FIRED BOTTOM NextApp"

echo >&2
echo "== a sideways swipe that drifts upward first is still a switch ==" >&2
# Discrete motionevents, because `input swipe` interpolates and never drifts: the upward
# drift has to arm Home first, so that carrying on sideways must re-arm.
open_test_app
reset_log
"${ADB_BIN}" shell input motionevent DOWN 300 "${BOTTOM_Y}" >/dev/null
"${ADB_BIN}" shell input motionevent MOVE 310 $((BOTTOM_Y - 40)) >/dev/null
"${ADB_BIN}" shell input motionevent MOVE 800 $((BOTTOM_Y - 30)) >/dev/null
"${ADB_BIN}" shell input motionevent UP 800 $((BOTTOM_Y - 30)) >/dev/null
sleep 2
assert_log "an upward drift then sideways fires PreviousApp" "GESTURE_FIRED BOTTOM PreviousApp"
assert_no_log "the pending Recents hold did not fire under it" "GESTURE_FIRED BOTTOM Recents"

echo >&2
echo "== an upward swipe is never stolen by app switching ==" >&2
open_test_app
reset_log
swipe_and_hold $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 0.6
sleep 2
assert_log "swipe-and-hold still fires Recents" "GESTURE_FIRED BOTTOM Recents"
assert_no_log "it is not read as a switch" "GESTURE_FIRED BOTTOM PreviousApp"
assert_no_log "nor as a switch the other way" "GESTURE_FIRED BOTTOM NextApp"

report "APP SWITCHING"
