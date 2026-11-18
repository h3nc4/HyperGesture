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

# A finger resting on the left strip leaves the right edge working after half a second.
# Needs a device from e2e/device.sh, and sendevent because `input` has only one finger.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry
warm_input

# Sets TOUCH_DEV, TOUCH_MAX_X and TOUCH_MAX_Y from the first multi-touch screen.
read_touchscreen() {
  devices="$("${ADB_BIN}" shell getevent -lp 2>/dev/null || true)"
  axes="$(printf '%s\n' "${devices}" | tr -d '\r' | awk '
    function max_of(line) { sub(/.*max /, "", line); sub(/,.*/, "", line); return line }
    /^add device/ { dev = $4 }
    /ABS_MT_POSITION_X/ && found == "" { found = dev; x = max_of($0) }
    /ABS_MT_POSITION_Y/ && dev == found && y == "" { y = max_of($0) }
    END { print found, x, y }')"
  # shellcheck disable=SC2086 # the three fields are meant to split
  set -- ${axes}
  TOUCH_DEV="${1:-}"
  TOUCH_MAX_X="${2:-}"
  TOUCH_MAX_Y="${3:-}"
  if [ -z "${TOUCH_MAX_Y}" ]; then
    echo "No multi-touch screen in getevent -lp, so two fingers cannot be sent." >&2
    exit 1
  fi
  echo "   touchscreen ${TOUCH_DEV}, axes 0-${TOUCH_MAX_X} x 0-${TOUCH_MAX_Y}" >&2
}

# ev <type> <code> <value> - one sendevent, printed for a batch that runs in one adb shell.
ev() {
  printf 'sendevent %s %s %s %s;' "${TOUCH_DEV}" "$1" "$2" "$3"
}

# finger <slot> <id> <x> <y> - puts that slot's finger down, in display pixels. Without a
# pressure the reader takes the contact for a hover and nothing reaches a window.
finger() {
  ev 3 47 "$1"
  ev 3 57 "$2"
  ev 3 53 $(($3 * TOUCH_MAX_X / (WIDTH - 1)))
  ev 3 54 $(($4 * TOUCH_MAX_Y / (HEIGHT - 1)))
  ev 3 58 50
  ev 3 48 5
  ev 1 330 1
  ev 0 0 0
}

# slide <x> - moves the finger of the last slot named sideways, in display pixels.
slide() {
  ev 3 53 $(($1 * TOUCH_MAX_X / (WIDTH - 1)))
  ev 0 0 0
}

# lift <slot> [last] - lifts that slot's finger, and with "last" the whole touch too.
lift() {
  ev 3 47 "$1"
  ev 3 57 -1
  if [ "${2:-}" = 'last' ]; then
    ev 1 330 0
  fi
  ev 0 0 0
}

send() {
  "${ADB_BIN}" shell "$1" >/dev/null
}

# The input devices take writes from root alone, so adbd restarts as root first.
"${ADB_BIN}" root >/dev/null 2>&1 || true
root_try=1
while [ "${root_try}" -le 20 ]; do
  "${ADB_BIN}" wait-for-device
  if [ "$("${ADB_BIN}" shell id -u 2>/dev/null | tr -d '\r' || true)" = '0' ]; then
    break
  fi
  sleep 1
  root_try=$((root_try + 1))
done
read_touchscreen

echo >&2
echo "== a right swipe goes back while a finger rests on the left strip ==" >&2
open_home
reset_log
rest="$(finger 0 100 2 "${MID_Y}")"
send "${rest}"
sleep 0.8
# Every sendevent is a process, so a few long steps keep the swipe quick enough to arm.
swipe_batch="$(finger 1 101 "${RIGHT_X}" "${MID_Y}")"
for step in 1 2 3 4 5; do
  swipe_batch="${swipe_batch}$(slide $((RIGHT_X - step * 60)))"
done
swipe_batch="${swipe_batch}$(lift 1)"
send "${swipe_batch}"
sleep 2
assert_log "the other edges came back while the left strip held the touch" "OTHER_EDGES_LIVE while LEFT"
assert_log "the right swipe fires Back" "GESTURE_FIRED RIGHT Back"
rest="$(lift 0 last)"
send "${rest}"
sleep 1
assert_no_log "the resting finger fires nothing" "GESTURE_FIRED LEFT"

echo >&2
echo "== every strip takes touches again once both fingers lift ==" >&2
open_home
reset_log
swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
sleep 2
assert_log "a left swipe afterwards still fires Back" "GESTURE_FIRED LEFT Back"

report "OTHER EDGES"
