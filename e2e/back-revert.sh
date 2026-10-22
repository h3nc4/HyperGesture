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

# A back swipe brought back to the edge before lifting must not go back, one pushed out must.
# Needs a device from e2e/device.sh, and motionevents because `input swipe` travels one way.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry
warm_input

# swipe_path <x>... - one finger through each x at mid-height, lifted at the last.
swipe_path() {
  "${ADB_BIN}" shell input motionevent DOWN "$1" "${MID_Y}" >/dev/null
  last="$1"
  shift
  for x in "$@"; do
    "${ADB_BIN}" shell input motionevent MOVE "${x}" "${MID_Y}" >/dev/null
    last="${x}"
  done
  "${ADB_BIN}" shell input motionevent UP "${last}" "${MID_Y}" >/dev/null
}

# assert_stayed <description> - the test app is still the resumed activity.
assert_stayed() {
  resumed="$(resumed_activity)"
  case "${resumed}" in
    *"${PKG}"*) pass "$1" ;;
    *) fail "$1 (resumed activity is now ${resumed})" ;;
  esac
}

# assert_left <description> - Back took the test app off the screen.
assert_left() {
  resumed="$(resumed_activity)"
  case "${resumed}" in
    *"${PKG}"*) fail "$1 (still on ${resumed})" ;;
    *) pass "$1" ;;
  esac
}

echo >&2
echo "== a left swipe brought back to the edge does not go back ==" >&2
open_home
reset_log
swipe_path 2 300 150 4
sleep 2
assert_log "the reverted left swipe is reported as reverted" "GESTURE_REVERTED LEFT"
assert_no_log "the reverted left swipe does not fire Back" "GESTURE_FIRED LEFT Back"
assert_no_log "the reverted swipe is not replayed to the app" "REPLAY_DISPATCHED"
assert_stayed "the test app is still on screen after the revert"

echo >&2
echo "== a right swipe brought back to the edge does not go back ==" >&2
open_home
reset_log
swipe_path "${RIGHT_X}" $((RIGHT_X - 300)) $((RIGHT_X - 150)) $((RIGHT_X - 2))
sleep 2
assert_log "the reverted right swipe is reported as reverted" "GESTURE_REVERTED RIGHT"
assert_no_log "the reverted right swipe does not fire Back" "GESTURE_FIRED RIGHT Back"
assert_stayed "the test app is still on screen after the right revert"

echo >&2
echo "== a swipe out with a small wobble still goes back ==" >&2
open_home
reset_log
swipe_path 2 300 270
sleep 2
assert_log "the forward left swipe fires Back" "GESTURE_FIRED LEFT Back"
assert_no_log "the forward left swipe is not reverted" "GESTURE_REVERTED"
# shellcheck disable=SC2310 # a false predicate is a real outcome here, not an error
if log_has "GESTURE_FIRED LEFT Back"; then
  assert_left "Back actually left the test app"
fi

echo >&2
echo "== a revert leaves the strips live for the next swipe ==" >&2
open_home
reset_log
swipe_path 2 300 4
sleep 2
reset_log
swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
sleep 2
assert_log "a swipe after a revert still fires Back" "GESTURE_FIRED LEFT Back"

report "BACK REVERT"
