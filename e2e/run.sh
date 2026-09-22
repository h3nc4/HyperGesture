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

# Every suite, in one command, against one emulator. This is what CI runs.
#
# One suite alone costs seconds instead of a boot:
#   e2e/emulator.sh && e2e/device.sh && e2e/app-switch.sh && e2e/emulator.sh -s
#
# Usage: e2e/run.sh [-a avd] [-p apk] [-k]
#   -a  AVD name (default: hypergesture)
#   -p  APK to test (default: debug). Point at hypergesture.apk to catch R8 breakage.
#   -k  keep the emulator running afterwards
#
# From the host, add: docker run --rm --device /dev/kvm ... -c './e2e/run.sh'

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

keep=''
apk_arg=''
usage="usage: $0 [-a avd] [-p apk] [-k]"

while getopts 'a:p:kh' opt; do
  case "${opt}" in
    a) AVD="${OPTARG}" ;;
    p) apk_arg="${OPTARG}" ;;
    k) keep='1' ;;
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

export AVD

cleanup() {
  if [ -z "${keep}" ]; then
    e2e/emulator.sh -s >/dev/null 2>&1 || true
  else
    echo "Emulator left running (-k). Stop it with: e2e/emulator.sh -s" >&2
  fi
}

e2e/emulator.sh
trap cleanup INT TERM EXIT

# shellcheck disable=SC2086 # an empty apk argument must vanish, not become an empty string
e2e/device.sh ${apk_arg:+-p "${apk_arg}"}

suites='navigation app-switch'
worst=0
for suite in ${suites}; do
  echo >&2
  echo "################ ${suite} ################" >&2
  if ! "e2e/${suite}.sh"; then
    worst=1
  fi
done

echo >&2
echo "================ E2E RESULT ================" >&2
if [ "${worst}" -ne 0 ]; then
  echo "a suite failed, see its report above" >&2
  exit 1
fi
echo "every end-to-end suite passed" >&2
