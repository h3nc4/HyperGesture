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

# End-to-end gesture tests against a headless emulator. The unit tests cover the pure
# tracker logic; every bug that reached a device lived in the Android integration around
# it, which no JVM test can reach. Asserts on logcat (our decision) and dumpsys (the
# system actually moved).
#
# Usage: scripts/e2e-gestures.sh [-a avd] [-p apk] [-k]
#   -a  AVD name (default: hypergesture)
#   -p  APK to test (default: debug). Point at hypergesture.apk to catch R8 breakage.
#   -k  keep the emulator running for interactive debugging
#
# From the host, add: docker run --rm --device /dev/kvm ... -c './scripts/e2e-gestures.sh'

set -e

avd="hypergesture"
keep=""
apk_override=""
usage="usage: ${0} [-a avd] [-p apk] [-k]"

while getopts "a:p:kh" opt; do
  case "${opt}" in
    a) avd="${OPTARG}" ;;
    p) apk_override="${OPTARG}" ;;
    k) keep="1" ;;
    h)
      echo "${usage}"
      exit 0
      ;;
    *)
      echo "${usage}" >&2
      exit 2
      ;;
  esac
done

cd "$(dirname "$0")/../"

# Resolve from ANDROID_HOME, not PATH: a login shell sources /etc/profile, which drops
# the SDK directories.
android_home="${ANDROID_HOME:-/opt/android-sdk}"
ADB_BIN="${android_home}/platform-tools/adb"
EMULATOR_BIN="${android_home}/emulator/emulator"
for tool in "${ADB_BIN}" "${EMULATOR_BIN}"; do
  if [ ! -x "${tool}" ]; then
    echo "Missing ${tool}. Is ANDROID_HOME correct? (got '${android_home}')" >&2
    exit 1
  fi
done

pkg="com.h3nc4.hypergesture"
service="${pkg}/${pkg}.service.HyperGestureAccessibilityService"
apk="${apk_override:-app/build/outputs/apk/debug/app-debug.apk}"
log_dir="build/e2e-logs"
mkdir -p "${log_dir}"

passed=0
failed=0
failures=""

cleanup() {
  if [ -z "${keep}" ]; then
    "${ADB_BIN}" emu kill >/dev/null 2>&1 || true
    pkill -f "emulator.*${avd}" >/dev/null 2>&1 || true
  else
    echo "Emulator left running (-k). Kill it with: adb emu kill" >&2
  fi
}

fail() {
  failed=$((failed + 1))
  failures="${failures}\n  - $1"
  echo "  FAIL: $1" >&2
}

pass() {
  passed=$((passed + 1))
  echo "  ok: $1" >&2
}

################################################################################
# Emulator lifecycle

if [ ! -f "${apk}" ]; then
  echo "Debug APK missing. Build it first: gradle assembleDebug" >&2
  exit 1
fi

echo "== starting emulator '${avd}' (headless) ==" >&2
# CPU rendering is enough; this suite injects input and reads state, never pixels.
setsid "${EMULATOR_BIN}" -avd "${avd}" \
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect \
  >"${log_dir}/emulator.log" 2>&1 </dev/null &

trap cleanup INT TERM EXIT

"${ADB_BIN}" start-server >/dev/null 2>&1 || true
if ! timeout 180 "${ADB_BIN}" wait-for-device; then
  echo "Emulator never appeared. See ${log_dir}/emulator.log" >&2
  exit 1
fi

echo "== waiting for boot ==" >&2
# shellcheck disable=SC2016 # getprop must run on the device, not expand here.
if ! timeout 300 "${ADB_BIN}" shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'; then
  echo "Boot never completed. See ${log_dir}/emulator.log" >&2
  exit 1
fi
"${ADB_BIN}" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
"${ADB_BIN}" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true

# Three-button nav matches the target environment and stops the platform's own gesture
# navigation from satisfying a test our service did nothing for. The mode is derived from
# an enabled overlay, so writing the secure setting alone does nothing: SystemUI recomputes
# it from the RRO. Left in gestural mode the bottom strip sits under the gesture bar and
# SystemUI claims every touch that starts there, so this must not fail quietly.
"${ADB_BIN}" shell cmd overlay enable \
  com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 || true
nav_mode=""
attempt=1
while [ "${attempt}" -le 15 ]; do
  nav_mode="$("${ADB_BIN}" shell settings get secure navigation_mode 2>/dev/null | tr -d '\r')"
  if [ "${nav_mode}" = "0" ]; then
    break
  fi
  sleep 1
  attempt=$((attempt + 1))
done
if [ "${nav_mode}" != "0" ]; then
  echo "Could not switch to three-button navigation (navigation_mode=${nav_mode})." >&2
  echo "Bottom-edge cases cannot pass in gestural mode: the gesture bar outranks our" >&2
  echo "strip and claims the stream. Overlays available:" >&2
  "${ADB_BIN}" shell cmd overlay list 2>/dev/null | tr -d '\r' | grep -i navbar | sed 's/^/    /' >&2
  exit 1
fi
echo "   navigation_mode=0 (three-button)" >&2

################################################################################
# Install and enable

echo "== installing ${apk} ==" >&2
"${ADB_BIN}" install -r -g "${apk}" >/dev/null

service_bound() {
  "${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r' |
    grep -q "Enabled services:.*${pkg}"
}

# Android 13+ treats this as a restricted setting: the ACCESS_RESTRICTED_SETTINGS app-op
# must allow it, and AccessibilityManagerService caches its verdict - it can cache a
# rejection right after an install and only re-checks when the enabled list changes.
# Hence the retry, clearing the list each attempt so the write is never a no-op.
enable_service() {
  attempt=1
  while [ "${attempt}" -le 4 ]; do
    "${ADB_BIN}" shell appops set "${pkg}" ACCESS_RESTRICTED_SETTINGS allow >/dev/null 2>&1 || true
    "${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
    sleep 1
    "${ADB_BIN}" shell settings put secure accessibility_enabled 1 >/dev/null
    "${ADB_BIN}" shell settings put secure enabled_accessibility_services "${service}" >/dev/null
    sleep 6
    # shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
    if service_bound; then
      echo "   bound on attempt ${attempt}" >&2
      return 0
    fi
    echo "   not bound yet (attempt ${attempt} of 4)" >&2
    attempt=$((attempt + 1))
  done
  return 1
}

# The recognizer reads "no movement for recentsHoldMs" as a deliberate hold, which is
# right on a device but wrong here: this emulator stalls input mid-swipe for longer than
# the 100ms default, so a continuous swipe becomes Recents. Widen the window for the test
# run only. The app consults this in debuggable builds alone, so the release APK - and the
# default every user gets - are untouched. Must be set before the service binds, or the
# first configuration emission will not carry it.
"${ADB_BIN}" shell settings put global hypergesture_recents_hold_ms 400 >/dev/null 2>&1 || true
hold_override="$("${ADB_BIN}" shell settings get global hypergesture_recents_hold_ms 2>/dev/null | tr -d '\r')"
if [ "${hold_override}" != "400" ]; then
  echo "Could not widen the Recents hold window (got '${hold_override}')." >&2
  echo "Bottom-swipe cases would read a stalled swipe as a hold." >&2
  exit 1
fi
echo "   recents hold widened to 400ms for this run" >&2

echo "== enabling the accessibility service ==" >&2
# Every case below is meaningless if the service never bound.
# shellcheck disable=SC2310 # the failure branch reports and exits, which is the point
if ! enable_service; then
  echo "The accessibility service never bound. Reasons from logcat:" >&2
  "${ADB_BIN}" logcat -d 2>/dev/null | grep -iE 'AccessibilitySecurityPolicy|disallowed by AppOps' |
    tail -5 | sed 's/^/    /' >&2
  exit 1
fi

size="$("${ADB_BIN}" shell wm size | tr -d '\r' | awk -F': *' '{print $2}' | tail -1)"
width="${size%x*}"
height="${size#*x}"
if [ -z "${width}" ] || [ -z "${height}" ]; then
  echo "Could not read screen size (got '${size}')" >&2
  exit 1
fi
mid_y=$((height / 2))
right_x=$((width - 3))

# The bottom strip is deliberately extended across the navigation-bar inset, but the bar
# is a system window above us in Z order: a synthetic swipe starting on the bar is claimed
# by SystemUI, which cancels our stream. On a real device that handoff works the other way
# (the bar is "slippery", so a swipe leaving it lands on us mid-gesture), but injected
# events do not reproduce that. So start just inside the top of our own strip instead,
# whose height the service logs when it installs.
read_strip_height() {
  "${ADB_BIN}" logcat -d -s HyperGesture 2>/dev/null | tr -d '\r' |
    sed -n 's/.*strip BOTTOM installed [0-9]*x\([0-9]*\).*/\1/p' | tail -1
}

# Binding and installing are not the same moment: the strips go up when the settings
# collector first emits, which is a DataStore read later. Reading once races that.
strip_bottom_h=""
attempt=1
while [ "${attempt}" -le 30 ]; do
  # shellcheck disable=SC2310 # an empty result is the retry signal, not an error
  strip_bottom_h="$(read_strip_height)"
  if [ -n "${strip_bottom_h}" ]; then
    break
  fi
  sleep 1
  attempt=$((attempt + 1))
done
if [ -z "${strip_bottom_h}" ]; then
  echo "The strips were never installed (no 'strip BOTTOM installed' in 30s)." >&2
  echo "Last HyperGesture log lines:" >&2
  "${ADB_BIN}" logcat -d -s HyperGesture 2>/dev/null | tr -d '\r' | tail -10 | sed 's/^/    /' >&2
  exit 1
fi

bottom_y=$((height - strip_bottom_h + 8))
echo "screen ${width}x${height}, bottom strip ${strip_bottom_h}px tall, swiping from y=${bottom_y}" >&2

################################################################################
# Helpers

# Clears logcat, so each case asserts only on what it caused.
reset_log() {
  "${ADB_BIN}" logcat -c >/dev/null 2>&1 || true
}

# Prints this case's HyperGesture log lines.
gesture_log() {
  "${ADB_BIN}" logcat -d -s HyperGesture 2>/dev/null | tr -d '\r'
}

# assert_log <description> <grep-pattern>
assert_log() {
  # shellcheck disable=SC2310 # an empty log is a legitimate result, not a script error
  captured="$(gesture_log || true)"
  if printf '%s\n' "${captured}" | grep -q "$2"; then
    pass "$1"
  else
    fail "$1 (no log matching '$2')"
    printf '%s\n' "${captured}" | sed 's/^/        /' >&2
  fi
}

# log_has <grep-pattern> - quiet predicate, for chaining a secondary assertion onto a
# primary one so a system-performed action cannot be mistaken for one of ours.
log_has() {
  # shellcheck disable=SC2310 # an empty log is a legitimate result, not a script error
  captured="$(gesture_log || true)"
  printf '%s\n' "${captured}" | grep -q "$1"
}

# assert_no_log <description> <grep-pattern>
assert_no_log() {
  # shellcheck disable=SC2310 # an empty log is a legitimate result, not a script error
  captured="$(gesture_log || true)"
  if printf '%s\n' "${captured}" | grep -q "$2"; then
    fail "$1 (unexpectedly matched '$2')"
    printf '%s\n' "${captured}" | sed 's/^/        /' >&2
  else
    pass "$1"
  fi
}

resumed_activity() {
  "${ADB_BIN}" shell dumpsys activity activities 2>/dev/null |
    tr -d '\r' | grep -oE 'topResumedActivity.*' | head -1 || true
}

# monkey returns as soon as the intent is sent. On a cold start under load the app is
# still drawing, and an injected stream then arrives stretched over a second, which the
# recognizer rightly discards as too slow to arm. Wait for the window instead of guessing.
open_test_app() {
  "${ADB_BIN}" shell monkey -p "${pkg}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  focus_try=1
  while [ "${focus_try}" -le 20 ]; do
    if "${ADB_BIN}" shell dumpsys window 2>/dev/null | tr -d '\r' |
      grep -qE "mCurrentFocus.*${pkg}"; then
      break
    fi
    sleep 1
    focus_try=$((focus_try + 1))
  done
  sleep 1
}

# A quick continuous swipe: never holds still, so it can only ever mean the
# short action (Back / Home), never the hold action.
swipe() {
  "${ADB_BIN}" shell input swipe "$1" "$2" "$3" "$4" "${5:-120}" >/dev/null
}

# A swipe that arms and then genuinely holds still before releasing. Separate
# `input motionevent` invocations are what make the pause real - `input swipe`
# interpolates continuously and would keep re-anchoring the hold timer.
swipe_and_hold() {
  "${ADB_BIN}" shell input motionevent DOWN "$1" "$2" >/dev/null
  "${ADB_BIN}" shell input motionevent MOVE "$3" "$4" >/dev/null
  sleep "${5:-0.6}"
  "${ADB_BIN}" shell input motionevent UP "$3" "$4" >/dev/null
}

################################################################################
# Cases

echo >&2
echo "== service came up ==" >&2
assert_log "accessibility service installed its edge strips" "strip .* installed"

# The first stream the device ever delivers arrives stretched over roughly a second, far
# past the arm window, however long the swipe was asked to take. Warm the path with
# throwaway gestures until one actually lands inside the window, so no assertion below is
# the first: a single warm-up swipe is not always enough on a loaded machine. A fired Back
# closes the test app, hence reopening each round.
warm=1
while [ "${warm}" -le 6 ]; do
  open_test_app
  reset_log
  swipe 2 "${mid_y}" 300 "${mid_y}" 120
  sleep 2
  # shellcheck disable=SC2310 # a miss is the retry signal, not an error
  if gesture_log | grep -q "GESTURE_FIRED LEFT Back"; then
    echo "   input path warm after ${warm} throwaway swipe(s)" >&2
    break
  fi
  warm=$((warm + 1))
done
if [ "${warm}" -gt 6 ]; then
  echo "   input path never warmed; the cases below report what the device did" >&2
fi

echo >&2
echo "== Back from the left edge ==" >&2
open_test_app
reset_log
swipe 2 "${mid_y}" 300 "${mid_y}" 120
sleep 2
assert_log "left-edge swipe fires Back" "GESTURE_FIRED LEFT Back"

echo >&2
echo "== Back again (regression: strips must stay live after a gesture) ==" >&2
open_test_app
reset_log
swipe 2 "${mid_y}" 300 "${mid_y}" 120
sleep 2
assert_log "a second left-edge swipe still fires Back" "GESTURE_FIRED LEFT Back"

echo >&2
echo "== Back from the right edge ==" >&2
open_test_app
reset_log
swipe "${right_x}" "${mid_y}" $((right_x - 300)) "${mid_y}" 120
sleep 2
assert_log "right-edge swipe fires Back" "GESTURE_FIRED RIGHT Back"

echo >&2
echo "== Home from the bottom edge ==" >&2
open_test_app
reset_log
swipe $((width / 2)) "${bottom_y}" $((width / 2)) $((height - 600)) 120
sleep 2
assert_log "bottom swipe fires Home" "GESTURE_FIRED BOTTOM Home"
assert_no_log "a continuous bottom swipe is not mistaken for Recents" "GESTURE_FIRED BOTTOM Recents"
# Secondary, and only meaningful once our own gesture is confirmed above: prove
# performGlobalAction actually moved the system, not just that we asked it to.
# shellcheck disable=SC2310 # a false predicate is a real outcome here, not an error
if log_has "GESTURE_FIRED BOTTOM Home"; then
  resumed="$(resumed_activity)"
  case "${resumed}" in
    *"${pkg}"*) fail "Home fired but the resumed activity did not change: ${resumed}" ;;
    *) pass "Home actually left the test app (performGlobalAction took effect)" ;;
  esac
fi

echo >&2
echo "== Recents from a bottom swipe-and-hold ==" >&2
open_test_app
reset_log
swipe_and_hold $((width / 2)) "${bottom_y}" $((width / 2)) $((height - 600)) 0.6
sleep 2
assert_log "bottom swipe-and-hold fires Recents" "GESTURE_FIRED BOTTOM Recents"
assert_no_log "a held bottom swipe does not also fire Home" "GESTURE_FIRED BOTTOM Home"

echo >&2
echo "== a tap near an edge is given back to the app ==" >&2
open_test_app
reset_log
"${ADB_BIN}" shell input tap 2 "${mid_y}" >/dev/null
sleep 2
assert_no_log "an edge tap is not misread as a gesture" "GESTURE_FIRED"
assert_log "an edge tap is replayed to the app underneath" "REPLAY_DISPATCHED"
assert_no_log "the replay was not refused" "REPLAY_REFUSED"

################################################################################
# Report

echo >&2
echo "================ E2E RESULT ================" >&2
echo "passed: ${passed}  failed: ${failed}" >&2
if [ "${failed}" -ne 0 ]; then
  printf 'failures:%b\n' "${failures}" >&2
  echo "emulator log: ${log_dir}/emulator.log" >&2
  exit 1
fi
echo "all gesture end-to-end checks passed" >&2
