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

# Installs the app on a booted device and binds it, ready for any suite.
#
# Usage: e2e/device.sh [-p apk]
#   -p  APK to install (default: debug). Point at hypergesture.apk to catch R8 breakage.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

apk="app/build/outputs/apk/debug/app-debug.apk"
usage="usage: $0 [-p apk]"

while getopts 'p:h' opt; do
  case "${opt}" in
    p) apk="${OPTARG}" ;;
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

require_adb

if [ ! -f "${apk}" ]; then
  echo "APK missing at ${apk}. Build it first: gradle assembleDebug" >&2
  exit 1
fi

echo "== installing ${apk} ==" >&2
"${ADB_BIN}" install -r -g "${apk}" >/dev/null

# override <setting> <value> <why it matters>. Read in debuggable builds alone, and only
# before the service binds, or the first configuration emission misses it.
override() {
  "${ADB_BIN}" shell settings put global "$1" "$2" >/dev/null 2>&1 || true
  got="$("${ADB_BIN}" shell settings get global "$1" 2>/dev/null | tr -d '\r')"
  if [ "${got}" != "$2" ]; then
    echo "Could not set $1 (got '${got}')." >&2
    echo "$3" >&2
    exit 1
  fi
  echo "   $1=$2" >&2
}

# This emulator stalls input mid-swipe for longer than the 100ms default, so a continuous
# swipe reads as a deliberate hold and becomes Recents.
override hypergesture_recents_hold_ms 400 \
  "Bottom-swipe cases would read a stalled swipe as a hold."

# App switching ships off, since it needs usage access the user grants by hand, so the
# app-switch cases would never reach the gesture.
override hypergesture_app_switch 1 \
  "The app-switch cases cannot reach the gesture while the feature is off."

service_bound() {
  "${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r' |
    grep -q "Enabled services:.*${PKG}"
}

# A restricted setting since Android 13, and the service caches a rejection until the
# enabled list changes. Hence the retry, clearing the list so no write is a no-op.
enable_service() {
  attempt=1
  while [ "${attempt}" -le 4 ]; do
    "${ADB_BIN}" shell appops set "${PKG}" ACCESS_RESTRICTED_SETTINGS allow >/dev/null 2>&1 || true
    "${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
    sleep 1
    "${ADB_BIN}" shell settings put secure accessibility_enabled 1 >/dev/null
    "${ADB_BIN}" shell settings put secure enabled_accessibility_services "${SERVICE}" >/dev/null
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

echo "== enabling the accessibility service ==" >&2
# Every case in every suite is meaningless if the service never bound.
# shellcheck disable=SC2310 # the failure branch reports and exits, which is the point
if ! enable_service; then
  echo "The accessibility service never bound. Reasons from logcat:" >&2
  "${ADB_BIN}" logcat -d 2>/dev/null | grep -iE 'AccessibilitySecurityPolicy|disallowed by AppOps' |
    tail -5 | sed 's/^/    /' >&2
  exit 1
fi
# Discovered here, while the bind-time log line still exists, and cached for the suites.
rm -f "${LOG_DIR}/geometry"
geometry
echo "device ready for the suites" >&2
