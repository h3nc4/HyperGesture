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

# Release gates. `tag` prints a vX.Y.Z tag's version, `version-code` wants a versionCode above
# the previous tag's, and `cert` wants apksigner's one signer to match the pin in facts.ts.
set -eu

cd "$(dirname "$0")/../"

gradle_file="app/build.gradle.kts"
facts_file="site/src/facts.ts"

fail() {
  echo "::error::$*" >&2
  exit 1
}

version_code() {
  sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]\{1,\}\).*/\1/p' | head -n 1
}

# The case also refuses a newline, which grep -x would read as two separate lines.
require_release_tag() {
  case "$1" in
    *[!v0-9.]*) ;;
    *) if printf '%s\n' "$1" | grep -qxE 'v[0-9]+\.[0-9]+\.[0-9]+'; then return; fi ;;
  esac
  fail "tag '$1' is not vMAJOR.MINOR.PATCH"
}

check_tag() {
  require_release_tag "$1"
  printf '%s\n' "${1#v}"
}

check_version_code() {
  tag="$1"
  require_release_tag "${tag}"
  current="$(version_code <"${gradle_file}")"
  [ -n "${current}" ] || fail "no versionCode in ${gradle_file}"

  # The release tag just below this one in version order, whether or not this one exists yet.
  previous="$(
    { git tag --list 'v*' && printf '%s\n' "${tag}"; } |
      grep -xE 'v[0-9]+\.[0-9]+\.[0-9]+' | sort -uV | sed -n "/^${tag}\$/{x;p;q;};h"
  )"
  if [ -z "${previous}" ]; then
    echo "no release before ${tag}, versionCode ${current} stands"
    return
  fi

  before="$(git show "${previous}:${gradle_file}" | version_code)"
  [ -n "${before}" ] || fail "no versionCode in ${gradle_file} at ${previous}"
  [ "${current}" -gt "${before}" ] ||
    fail "versionCode ${current} is not above ${before} from ${previous}, so Android would refuse the update"
  echo "versionCode ${current} is above ${before} from ${previous}"
}

check_cert() {
  pinned="$(sed -n 's/.*read("CERT_SHA256", "\([0-9a-f]\{64\}\)").*/\1/p' "${facts_file}")"
  [ -n "${pinned}" ] || fail "no pinned CERT_SHA256 in ${facts_file}"

  digests="$(grep -oE 'certificate SHA-256 digest: [0-9a-f]{64}' "$1" | cut -d' ' -f4 | sort -u)"
  [ -n "${digests}" ] || fail "apksigner printed no SHA-256 certificate digest"
  count="$(printf '%s\n' "${digests}" | wc -l)"
  [ "${count}" -eq 1 ] || fail "the APK carries ${count} signers"
  [ "${digests}" = "${pinned}" ] ||
    fail "signed with ${digests}, but ${facts_file} pins ${pinned}, so no install could update to it"
  printf '%s\n' "${digests}"
}

case "${1:-}" in
  tag) check_tag "${2:-}" ;;
  version-code) check_version_code "${2:-}" ;;
  cert) check_cert "${2:?apksigner output file}" ;;
  *)
    echo "usage: ${0} tag|version-code|cert <argument>" >&2
    exit 2
    ;;
esac
