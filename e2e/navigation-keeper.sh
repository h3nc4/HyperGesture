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

# The switch to keep the bar hidden shows once the permission is granted, stays locked until the
# Control Center tile is added, and a reset is undone while it is on.
# Gestures stopping, by an unbind or a kill, writes the buttons back.
# Needs a debug build from e2e/device.sh, since it borrows the HyperOS integration.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "   release build, which ignores the integration override, so this suite has nothing to reach" >&2
  exit 0
fi

PERMISSION='android.permission.WRITE_SECURE_SETTINGS'
TILE="${PKG}/.setup.GestureTileService"

setting() {
  "${ADB_BIN}" shell settings get "$1" "$2" 2>/dev/null | tr -d '\r'
}

put() {
  "${ADB_BIN}" shell settings put "$1" "$2" "$3" >/dev/null
}

# Pressing Home pauses the activity, so opening it again is a resume that rereads the grant.
# The launcher takes focus once the pause is done, so that is what it waits for.
leave_app() {
  "${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
  away_try=1
  while [ "${away_try}" -le 10 ]; do
    if ! "${ADB_BIN}" shell dumpsys window 2>/dev/null | tr -d '\r' |
      grep -qE "mCurrentFocus.*${PKG}"; then
      return 0
    fi
    sleep 0.5
    away_try=$((away_try + 1))
  done
}

# The HyperOS integration adds required steps the user confirms by hand, and home folds the
# checklist into its Setup row only once they are done. It returns on home, with ROW_SETUP set.
confirm_required_steps() {
  did_it="$(string_res action_i_did_it)"
  ROW_SETUP=''
  open_home
  nodes="${HOME_NODES}"
  confirm_try=1
  while [ "${confirm_try}" -le 4 ]; do
    ROW_SETUP="$(printf '%s\n' "${nodes}" | grep -m1 'resource-id="row_setup"' || true)"
    if [ -n "${ROW_SETUP}" ]; then
      return 0
    fi
    did_it_node="$(printf '%s\n' "${nodes}" | grep -m1 "text=\"${did_it}\"" || true)"
    if [ -n "${did_it_node}" ]; then
      tap_node "${did_it_node}"
      sleep 2
      nodes="$(ui_nodes)"
    else
      open_home
      nodes="${HOME_NODES}"
    fi
    confirm_try=$((confirm_try + 1))
  done
}

# Taps the Setup row read off home when it is clear of the bottom strip, and falls back to
# open_screen, which reopens home, when that tap does not reach the screen. The dump that showed
# the node is left in SETUP_NODES and the node in SETUP_NODE, both empty when it was not found.
open_setup() {
  leave_app
  confirm_required_steps
  row_centre="$(printf '%s\n' "${ROW_SETUP}" |
    sed -n 's/.*bounds="\[[0-9]*,\([0-9]*\)\]\[[0-9]*,\([0-9]*\)\]".*/\1 \2/p' |
    awk '{print int(($1 + $2) / 2)}')"
  size="$(screen_size)"
  if [ -n "${row_centre}" ] && [ "${row_centre}" -lt $((${size#*x} - 260)) ]; then
    tap_node "${ROW_SETUP}"
    sleep 1
    # shellcheck disable=SC2310 # a missing node sends it to the slower path below
    if find_id "$1" >/dev/null; then
      setup_found "$1"
      return 0
    fi
  fi
  open_screen row_setup "$1"
  setup_found "$1"
}

setup_found() {
  SETUP_NODES="$(cat "${LOG_DIR}/found_nodes" 2>/dev/null || true)"
  SETUP_NODE="$(printf '%s\n' "${SETUP_NODES}" | grep -m1 "resource-id=\"$1\"" || true)"
}

# reopen_setup <tag> - the resume open_setup makes, without the walk through home, as the app
# reopens on the screen it left. Home has no back arrow, so landing there takes open_setup.
reopen_setup() {
  leave_app
  open_test_app
  SETUP_NODE=''
  # shellcheck disable=SC2310 # a node out of reach sends it to open_setup
  if ui_nodes | grep -q 'resource-id="nav_back"' && find_id "$1" >/dev/null; then
    setup_found "$1"
  fi
  [ -n "${SETUP_NODE}" ] || open_setup "$1"
}

# Setup is one scrolled column, and a dump leaves out what is scrolled away. So these prove a dump
# reaches the list's first or last node before an absent node counts as absent.
setup_has_first() {
  first="$(string_res setup_group_required)"
  printf '%s\n' "$1" | grep -qF "text=\"${first}\""
}

setup_has_last() {
  last_hyperos="$(string_res setup_footer_hyperos)"
  last_plain="$(string_res setup_footer)"
  printf '%s\n' "$1" | grep -qF -e "text=\"${last_hyperos}\"" -e "text=\"${last_plain}\""
}

# first_list_text <nodes> - the first text inside the scrolled column, which is its top visible row.
first_list_text() {
  printf '%s\n' "$1" | sed -n '/scrollable="true"/,$ s/.* text="\([^"]\{1,\}\)".*/\1/p' | sed -n 1p
}

# setup_shows_switch <nodes> - looks for the switch in a dump at the end of Setup and in one from its
# top. Together they cover the list only when the top dump also shows the end dump's top row.
setup_shows_switch() {
  end_nodes="$1"
  # shellcheck disable=SC2310 # a false predicate is the answer, not an error
  if setup_has_first "${end_nodes}" && setup_has_last "${end_nodes}"; then
    echo "   one dump covers the whole Setup list" >&2
    printf '%s\n' "${end_nodes}" | grep -q 'resource-id="switch_keep_navigation"'
    return
  fi
  # The grant row can show before the list ends, and then the dump of the end is taken here.
  # shellcheck disable=SC2310 # a false predicate is the answer, not an error
  if ! setup_has_last "${end_nodes}"; then
    scroll_app down
    scroll_app down
    sleep 1
    end_nodes="$(ui_nodes)"
  fi
  scroll_app up
  scroll_app up
  sleep 1
  top_nodes="$(ui_nodes)"
  seam="$(first_list_text "${end_nodes}")"
  seam_count="$(printf '%s\n' "${end_nodes}" | grep -cF "text=\"${seam}\"" || true)"
  # shellcheck disable=SC2310 # a false predicate sends it to the scrolling scan
  if [ -n "${seam}" ] && [ "${seam_count}" = 1 ] && setup_has_last "${end_nodes}" &&
    setup_has_first "${top_nodes}" && printf '%s\n' "${top_nodes}" | grep -qF "text=\"${seam}\""; then
    echo "   two dumps meeting at '${seam}' cover the whole Setup list" >&2
    printf '%s\n%s\n' "${end_nodes}" "${top_nodes}" | grep -q 'resource-id="switch_keep_navigation"'
    return
  fi
  find_id switch_keep_navigation >/dev/null
}

# expect_settings <description> <fsg> <mode> - polls, since the keeper answers asynchronously.
expect_settings() {
  tries=1
  while [ "${tries}" -le 8 ]; do
    fsg="$(setting global force_fsg_nav_bar)"
    mode="$(setting secure navigation_mode)"
    if [ "${fsg}" = "$2" ] && [ "${mode}" = "$3" ]; then
      pass "$1"
      return 0
    fi
    sleep 1
    tries=$((tries + 1))
  done
  fail "$1 (expected fsg=$2 mode=$3, found fsg=${fsg} mode=${mode})"
}

# Every case resets the bar by hand, then waits as long as a restore would take.
reset_bar() {
  put global force_fsg_nav_bar 0
  put secure navigation_mode 0
}

service_enabled() {
  "${ADB_BIN}" shell dumpsys accessibility 2>/dev/null | tr -d '\r' |
    grep -q "Enabled services:.*${PKG}"
}

turn_on_service() {
  "${ADB_BIN}" shell settings put secure accessibility_enabled 1 >/dev/null
  "${ADB_BIN}" shell settings put secure enabled_accessibility_services "${SERVICE}" >/dev/null
  bound=1
  # shellcheck disable=SC2310 # a false predicate is the retry signal, not an error
  while [ "${bound}" -le 10 ] && ! service_enabled; do
    sleep 1
    bound=$((bound + 1))
  done
}

original_fsg="$(setting global force_fsg_nav_bar)"
original_mode="$(setting secure navigation_mode)"

# restore_setting <table> <key> <value read before the suite>, where null means it was unset.
restore_setting() {
  if [ "$3" = 'null' ]; then
    "${ADB_BIN}" shell settings delete "$1" "$2" >/dev/null 2>&1 || true
  else
    put "$1" "$2" "$3"
  fi
}

# The tile is added and removed through SystemUI, which calls the app back to record either.
remove_tile() {
  "${ADB_BIN}" shell cmd statusbar remove-tile "${TILE}" >/dev/null 2>&1 || true
}

restore_device() {
  remove_tile
  "${ADB_BIN}" shell settings delete global hypergesture_integration >/dev/null 2>&1 || true
  "${ADB_BIN}" shell pm revoke "${PKG}" "${PERMISSION}" >/dev/null 2>&1 || true
  restore_setting global force_fsg_nav_bar "${original_fsg}"
  restore_setting secure navigation_mode "${original_mode}"
}
trap restore_device EXIT

put global hypergesture_integration hyperos
"${ADB_BIN}" shell pm revoke "${PKG}" "${PERMISSION}" >/dev/null 2>&1 || true
remove_tile

echo >&2
echo "== without the grant the row shows the adb command ==" >&2
open_setup keep_navigation_grant
setup_nodes="${SETUP_NODES:-$(ui_nodes)}"
# shellcheck disable=SC2310 # a missing node is a result, reported below
if printf '%s\n' "${setup_nodes}" | grep -q 'resource-id="keep_navigation_grant"' ||
  find_id keep_navigation_grant >/dev/null; then
  pass "the grant instructions show"
else
  fail "the grant instructions are missing"
fi
# shellcheck disable=SC2310 # a missing node is a result, reported below
if setup_shows_switch "${setup_nodes}"; then
  fail "the switch shows before the grant"
else
  pass "the switch waits for the grant"
fi

echo >&2
echo "== granted without the tile, the switch shows off and locked, as the bar step is ==" >&2
"${ADB_BIN}" shell pm grant "${PKG}" "${PERMISSION}"
reopen_setup switch_keep_navigation
# shellcheck disable=SC2310 # a missing node is a result, reported below
node="${SETUP_NODE:-$(find_id switch_keep_navigation || true)}"
case "${node}" in
  *'checked="false"'*'enabled="false"'* | *'enabled="false"'*'checked="false"'*)
    pass "the switch shows, turned off and locked"
    ;;
  *) fail "the switch is missing, on or unlocked before the tile: ${node:-nothing}" ;;
esac
waits="$(string_res step_waits_on_tile)"
# shellcheck disable=SC2310 # a missing text is a result, reported below
if printf '%s\n' "${SETUP_NODES}" | grep -q "text=\"[^\"]*${waits}" ||
  ui_nodes | grep -q "text=\"[^\"]*${waits}"; then
  pass "the switch says it waits for the tile"
else
  fail "the switch does not say it waits for the tile"
fi
reset_bar
tap_node "${node}"
sleep 4
expect_settings "a tap on the locked switch writes nothing" 0 0
# shellcheck disable=SC2310 # a missing node is a result, reported below
case "$(find_id switch_keep_navigation || true)" in
  *'checked="false"'*) pass "a tap on the locked switch leaves it off" ;;
  *) fail "a tap on the locked switch turned it on" ;;
esac

echo >&2
echo "== the tile added, the switch unlocks ==" >&2
"${ADB_BIN}" shell cmd statusbar add-tile "${TILE}" >/dev/null
# Setup collects the stored tile flag, so the open screen unlocks with no resume. The last
# four tries reopen it, in case the flag reaches the screen only on a resume.
unlocked=''
unlock_try=1
while [ "${unlock_try}" -le 7 ]; do
  # shellcheck disable=SC2310 # a missing node is the retry signal, reported below
  if [ "${unlock_try}" -le 3 ]; then
    node="$(find_id switch_keep_navigation || true)"
  else
    reopen_setup switch_keep_navigation
    node="${SETUP_NODE:-$(find_id switch_keep_navigation || true)}"
  fi
  case "${node}" in
    *'enabled="true"'*)
      unlocked='1'
      break
      ;;
    *) ;;
  esac
  sleep 1
  unlock_try=$((unlock_try + 1))
done
if [ -n "${unlocked}" ]; then
  pass "the switch unlocks once the tile is added"
else
  fail "the switch stays locked with the tile added: ${node:-nothing}"
fi

echo >&2
echo "== switched off, a reset stays ==" >&2
reset_bar
sleep 4
expect_settings "nothing is written while the switch is off" 0 0

echo >&2
echo "== switching on hides the bar at once ==" >&2
reset_log
# shellcheck disable=SC2310 # the failure is reported, and the later cases then fail too
set_toggle_id switch_keep_navigation true || fail "could not turn the switch on"
expect_settings "both settings are restored" 1 2
assert_log "the restore is logged" 'NAVIGATION_RESTORED'

echo >&2
echo "== a reset of either setting is undone ==" >&2
put secure navigation_mode 0
expect_settings "navigation_mode is put back" 1 2
put global force_fsg_nav_bar 0
expect_settings "force_fsg_nav_bar is put back" 1 2

echo >&2
echo "== the service switched off brings the buttons back ==" >&2
expect_settings "the bar is hidden while the service runs" 1 2
reset_log
"${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null
expect_settings "both settings read 0 once the service stops" 0 0
assert_log "the restore of the buttons is logged" 'NAVIGATION_BUTTONS_BACK'

echo >&2
echo "== a reset while the service was away is undone when it binds again ==" >&2
put global force_fsg_nav_bar 1
put secure navigation_mode 0
sleep 3
expect_settings "nothing is written while the service is off" 1 0
turn_on_service
expect_settings "the service restores on bind" 1 2

echo >&2
echo "== a kill with no unbind is caught when the app resumes ==" >&2
# A force stop kills the process with no callback, and the system drops the service from the enabled list.
"${ADB_BIN}" shell am force-stop "${PKG}"
sleep 3
expect_settings "the kill leaves the bar hidden" 1 2
open_test_app
expect_settings "the app resuming brings the buttons back" 0 0
turn_on_service
expect_settings "the service hides the bar again on bind" 1 2

echo >&2
echo "== switching off stops the keeper ==" >&2
open_setup switch_keep_navigation
# shellcheck disable=SC2310 # the failure is reported, and the next case then fails too
set_toggle_id switch_keep_navigation false || fail "could not turn the switch off"
sleep 2
reset_bar
sleep 4
expect_settings "a reset stays once the switch is off" 0 0

report "NAVIGATION KEEPER"
