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

# Status rows changed in Settings must update when the app comes back, untapped.
# Needs a device from e2e/device.sh with app switching on, and binds the service again.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

usage_granted="$(string_res label_usage_access_granted)"
usage_missing="$(string_res label_usage_access_missing)"
service_working="$(string_res status_working)"
service_off="$(string_res status_off)"

# Pressing Home pauses the activity without destroying it, so coming back is a resume.
leave_app() {
  "${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
  sleep 2
}

# assert_shows <description> <tag> <expected> - the text the node with that tag shows.
assert_shows() {
  # shellcheck disable=SC2310 # a missing node is a result, reported below
  shown="$(text_of_id "$2" || true)"
  if [ "${shown}" = "$3" ]; then
    pass "$1"
  else
    fail "$1 (expected '$3', the screen shows '${shown:-nothing}')"
  fi
}

service_enabled() {
  "${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r' |
    grep -q "Enabled services:.*${PKG}"
}

echo >&2
echo "== usage access granted in the background shows on return ==" >&2
"${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS default >/dev/null
open_screen row_app_switching usage_access
assert_shows "the row starts out missing" usage_access "${usage_missing}"
leave_app
"${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS allow >/dev/null
open_screen row_app_switching usage_access
assert_shows "the row reads granted without a tap" usage_access "${usage_granted}"

echo >&2
echo "== usage access revoked in the background shows on return ==" >&2
leave_app
"${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS default >/dev/null
open_screen row_app_switching usage_access
assert_shows "the row reads missing without a tap" usage_access "${usage_missing}"

echo >&2
echo "== the service turned off in the background shows on return ==" >&2
open_home
assert_shows "the status starts out working" service_status "${service_working}"
leave_app
"${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null
sleep 3
open_home
assert_shows "the status reads off without a tap" service_status "${service_off}"

echo >&2
echo "== the service turned back on shows on return ==" >&2
leave_app
"${ADB_BIN}" shell settings put secure accessibility_enabled 1 >/dev/null
"${ADB_BIN}" shell settings put secure enabled_accessibility_services "${SERVICE}" >/dev/null
bound=1
# shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
while [ "${bound}" -le 10 ] && ! service_enabled; do
  sleep 1
  bound=$((bound + 1))
done
open_home
assert_shows "the status reads working without a tap" service_status "${service_working}"

report "STATUS REFRESH"
