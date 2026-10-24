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

# Landscape asks for a second bottom swipe before Home, portrait still takes one. Rotates the
# emulator and puts it back on exit. Expects a device prepared by e2e/device.sh.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry

# Locks the display through `wm user-rotation` and puts the previous state back on exit.
rotation_before="$("${ADB_BIN}" shell wm user-rotation | tr -d '\r')"

restore_rotation() {
  case "${rotation_before}" in
    'lock '*) "${ADB_BIN}" shell wm user-rotation "${rotation_before}" >/dev/null 2>&1 || true ;;
    *) "${ADB_BIN}" shell wm user-rotation free >/dev/null 2>&1 || true ;;
  esac
}
trap restore_rotation INT TERM EXIT

# rotate <0|1> - turns the display and waits for the strips rebuilt for it, then aims the next
# swipe inside the strip the window manager placed rather than one measured before the turn.
rotate() {
  "${ADB_BIN}" shell wm user-rotation lock "$1" >/dev/null
  # shellcheck disable=SC2310 # a timeout is the caller's failed case, not a script error
  wait_strip "$1" || return 1
  if [ "$1" = '1' ]; then
    L_BOTTOM_Y=$((STRIP_TOP + 8))
  else
    BOTTOM_Y=$((STRIP_TOP + 8))
  fi
}

# monkey thaws the rotation lock as it exits, so landscape cases open the app with am instead.
# Each case waits for landscape and its strip again, since a confirmed Home leaves the app.
open_in_landscape() {
  "${ADB_BIN}" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -n "${PKG}/.ui.MainActivity" >/dev/null 2>&1 || true
  # shellcheck disable=SC2310 # a timeout shows as the failed swipe that follows
  rotate 1 || echo "   landscape never settled, the swipe below reports what the device did" >&2
}

# stalled <attempt> - true when the emulator stretched a swipe past the arm window, as it does
# now and then after a rotation. The case runs again once the strips take touches again.
stalled() {
  # shellcheck disable=SC2310 # a false predicate is the common outcome
  log_has "unused (TOO_SLOW_TO_ARM)" || return 1
  [ "$1" -lt 3 ] || return 1
  echo "   a swipe arrived too slow to arm, running the case again (attempt $1 of 3)" >&2
  release_try=1
  # shellcheck disable=SC2310 # a missing line is the retry signal
  while [ "${release_try}" -le 10 ] && ! log_has "strips interactive again"; do
    sleep 1
    release_try=$((release_try + 1))
  done
}

# A previous suite may have left the display turned, and warm_input swipes in portrait.
# shellcheck disable=SC2310 # a timeout shows as the failed cases that follow
rotate 0 || echo "   portrait never settled before the cases" >&2
warm_input
open_test_app

echo >&2
echo "== the strips follow a rotation to landscape ==" >&2
L_WIDTH="${HEIGHT}"
L_HEIGHT="${WIDTH}"
L_BOTTOM_Y=$((L_HEIGHT - 44))
L_TOP_Y=$((L_HEIGHT / 2))
# shellcheck disable=SC2310 # a timeout is reported as a failed case, not a script error
if rotate 1; then
  pass "the bottom strip was rebuilt for the landscape width"
else
  fail "no landscape strip within 30 s, the cases below report what the device did"
fi

echo >&2
echo "== a first swipe shows the pill and a second goes Home ==" >&2
case_try=1
while [ "${case_try}" -le 3 ]; do
  open_in_landscape
  reset_log
  # One device shell keeps the focus check between the swipes inside the 2 s window.
  between="$("${ADB_BIN}" shell "input swipe $((L_WIDTH / 2)) ${L_BOTTOM_Y} $((L_WIDTH / 2)) ${L_TOP_Y} 120 && sleep 0.3 && dumpsys window | grep mCurrentFocus && input swipe $((L_WIDTH / 2)) ${L_BOTTOM_Y} $((L_WIDTH / 2)) ${L_TOP_Y} 120" | tr -d '\r')"
  sleep 2
  # shellcheck disable=SC2310 # a false predicate means the swipes arrived in time
  stalled "${case_try}" || break
  case_try=$((case_try + 1))
done
assert_log "the first landscape swipe shows the pill" "GESTURE_CONFIRM_SHOWN BOTTOM Home"
case "${between}" in
  *"${PKG}"*) pass "the app stayed in front after the first swipe" ;;
  *) fail "the app lost focus after the first swipe: ${between}" ;;
esac
assert_log "the second swipe inside the window goes Home" "GESTURE_FIRED BOTTOM Home"
# shellcheck disable=SC2310 # a false predicate is a real outcome here, not an error
if log_has "GESTURE_FIRED BOTTOM Home"; then
  resumed="$(resumed_activity)"
  case "${resumed}" in
    *"${PKG}"*) fail "Home fired but the resumed activity did not change: ${resumed}" ;;
    *) pass "the confirmed Home left the test app" ;;
  esac
fi

echo >&2
echo "== the pill fades and the next swipe starts over ==" >&2
case_try=1
while [ "${case_try}" -le 3 ]; do
  open_in_landscape
  reset_log
  swipe $((L_WIDTH / 2)) "${L_BOTTOM_Y}" $((L_WIDTH / 2)) "${L_TOP_Y}" 120
  sleep 3
  swipe $((L_WIDTH / 2)) "${L_BOTTOM_Y}" $((L_WIDTH / 2)) "${L_TOP_Y}" 120
  sleep 2
  # shellcheck disable=SC2310 # a false predicate means the swipes arrived in time
  stalled "${case_try}" || break
  case_try=$((case_try + 1))
done
assert_log "the pill expired after its window" "GESTURE_CONFIRM_EXPIRED"
assert_no_log "a swipe after the window does not go Home" "GESTURE_FIRED BOTTOM Home"
# shellcheck disable=SC2310 # an empty log is a legitimate result, not a script error
captured="$(gesture_log || true)"
shown="$(printf '%s\n' "${captured}" | grep -c 'GESTURE_CONFIRM_SHOWN BOTTOM Home' || true)"
if [ "${shown}" -eq 2 ]; then
  pass "the late swipe showed the pill again"
else
  fail "expected two pills, saw ${shown}"
fi
case "$(resumed_activity)" in
  *"${PKG}"*) pass "the app is still in front" ;;
  *) fail "the app lost focus with neither swipe confirmed" ;;
esac

echo >&2
echo "== back in portrait one swipe goes Home ==" >&2
# shellcheck disable=SC2310 # a timeout is reported as a failed case, not a script error
if rotate 0; then
  pass "the bottom strip was rebuilt for the portrait width"
else
  fail "no portrait strip within 30 s"
fi
open_test_app
reset_log
swipe $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 120
sleep 2
assert_log "a portrait swipe goes Home at once" "GESTURE_FIRED BOTTOM Home"
assert_no_log "portrait shows no pill" "GESTURE_CONFIRM_SHOWN"

report "LANDSCAPE CONFIRM"
