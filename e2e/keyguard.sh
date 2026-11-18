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

# No strip reacts over the lock screen, and the strips work again within a second of unlocking.
# Needs a device from e2e/device.sh.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry

# The swipe lock goes on for this suite. A device without a lock screen never shows the keyguard.
lock_before="$("${ADB_BIN}" shell locksettings get-disabled 2>/dev/null | tr -d '\r')"
restore_lock() {
  "${ADB_BIN}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  "${ADB_BIN}" shell locksettings set-disabled "${lock_before:-false}" >/dev/null 2>&1 || true
}
trap restore_lock INT TERM EXIT
"${ADB_BIN}" shell locksettings set-disabled false >/dev/null

keyguard_showing() {
  "${ADB_BIN}" shell dumpsys activity activities 2>/dev/null | tr -d '\r' |
    grep -q 'mKeyguardShowing=true'
}

# The boot's own broadcasts slow system_server's binder calls to hundreds of milliseconds for a
# while after boot, which says nothing about the strips. So each timed step waits them out.
settle() {
  "${ADB_BIN}" shell timeout 60 am wait-for-broadcast-idle >/dev/null 2>&1 || true
}

# wait_log <pattern> - up to ten seconds.
wait_log() {
  log_try=1
  while [ "${log_try}" -le 20 ]; do
    # shellcheck disable=SC2310 # a miss is the retry signal, not an error
    if log_has "$1"; then
      return 0
    fi
    sleep 0.5
    log_try=$((log_try + 1))
  done
  return 1
}

warm_input

echo >&2
echo "== the strips disarm when the screen locks ==" >&2
# Locked over the launcher, as a user would. HyperGesture's own activity shares the service's
# main thread, and its resume on unlock holds that thread for about 600ms.
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
sleep 1
settle
reset_log
"${ADB_BIN}" shell input keyevent KEYCODE_SLEEP >/dev/null
# shellcheck disable=SC2310 # the predicate's answer is the result
if wait_log 'STRIPS_DISARMED'; then
  pass "the screen going off disarms the strips"
else
  fail "no STRIPS_DISARMED after the screen went off"
fi
"${ADB_BIN}" shell input keyevent KEYCODE_WAKEUP >/dev/null
shown=1
# shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
while [ "${shown}" -le 10 ] && ! keyguard_showing; do
  sleep 1
  shown=$((shown + 1))
done
if [ "${shown}" -le 10 ]; then
  pass "the device wakes onto the lock screen"
else
  fail "the lock screen never showed after waking"
fi

echo >&2
echo "== no strip reacts over the lock screen ==" >&2
reset_log
swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
sleep 1
swipe "${RIGHT_X}" "${MID_Y}" $((WIDTH - 300)) "${MID_Y}" 120
sleep 2
assert_no_log "no strip sees a touch over the lock screen" 'DOWN at'
assert_no_log "no gesture fires over the lock screen" 'GESTURE_FIRED'
assert_no_log "waking onto the keyguard keeps the strips disarmed" 'STRIPS_ARMED'
# shellcheck disable=SC2310 # the predicate's answer is the result
if keyguard_showing; then
  pass "the lock screen is still up after the swipes"
else
  fail "the lock screen went away during the swipes"
fi

echo >&2
echo "== the strips work again within a second of unlocking ==" >&2
settle
reset_log
# Device clock on both ends, so the adb round trip does not count against the second.
unlocked_at="$("${ADB_BIN}" shell date +%s.%N | tr -d '\r')"
"${ADB_BIN}" shell wm dismiss-keyguard >/dev/null
# shellcheck disable=SC2310 # the predicate's answer is the result
if wait_log 'STRIPS_ARMED'; then
  armed_at="$("${ADB_BIN}" logcat -d -v epoch -s HyperGesture 2>/dev/null | tr -d '\r' |
    awk '/STRIPS_ARMED/ { print $1; exit }')"
  took_ms="$(awk -v a="${unlocked_at}" -v b="${armed_at}" 'BEGIN { printf "%d", (b - a) * 1000 }')"
  if [ "${took_ms}" -lt 1000 ]; then
    pass "the strips armed ${took_ms}ms after the unlock"
  else
    fail "the strips armed ${took_ms}ms after the unlock, past the second"
  fi
else
  fail "no STRIPS_ARMED within ten seconds of the unlock"
fi
# A strip reinstalled for a settling inset is gone while it is rebuilt, so the swipe gets three tries.
back_try=1
while [ "${back_try}" -le 3 ]; do
  swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
  sleep 2
  # shellcheck disable=SC2310 # a miss is the retry signal, not an error
  if log_has 'GESTURE_FIRED LEFT Back'; then
    break
  fi
  back_try=$((back_try + 1))
done
assert_log "a left-edge swipe fires Back once unlocked" 'GESTURE_FIRED LEFT Back'

report "KEYGUARD"
