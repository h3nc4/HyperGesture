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
#
# Analyses against the shared SonarQube, one project per developer because
# Community Edition tracks a single branch per project.
set -e

cd "$(dirname "$0")/../"

delete_after=""
while [ $# -gt 0 ]; do
  case "$1" in
    -d) delete_after="1" ;;
    *)
      echo "usage: $0 [-d]" >&2
      exit 2
      ;;
  esac
  shift
done

# Defaulted so a workstation needs nothing but a token.
SONAR_HOST_URL="${SONAR_HOST_URL:-https://sonar.h3nc4.com}"
export SONAR_HOST_URL

if [ -z "${SONAR_TOKEN:-}" ]; then
  echo "SONAR_TOKEN is not set." >&2
  echo "Create one under My Account, Security at ${SONAR_HOST_URL} and export" >&2
  echo "it from your shell profile. The scanner reads it from the environment." >&2
  exit 1
fi

sonar_scan_image="sonarsource/sonar-scanner-cli:12.1"

# Swap is left alone on purpose: pinning it to the cap shuts the container out
# of swap, which makes the scanner thrash the page cache instead.
scanner_java_opts="${SONAR_SCANNER_JAVA_OPTS:--Xmx1g}"
scanner_memory="${SONAR_SCANNER_MEMORY:-4g}"

# curl from inside the scanner image, keeping the token out of argv.
sonar_api() {
  docker run --rm \
    -e SONAR_HOST_URL -e SONAR_TOKEN \
    --entrypoint sh "${sonar_scan_image}" -c "$1"
}

project_key="${SONAR_PROJECT_KEY:-}"

if [ -z "${project_key}" ] && [ -z "${CI:-}" ]; then
  # Single quoted so the container's shell expands it, and api/users/current is internal.
  # shellcheck disable=SC2016
  sonar_login="$(sonar_api \
    'curl -s -u "${SONAR_TOKEN}:" "${SONAR_HOST_URL}/api/users/current"' 2>/dev/null |
    sed -n 's/.*"login" *: *"\([^"]*\)".*/\1/p' | head -n 1)"
  if [ -z "${sonar_login}" ]; then
    sonar_login="$(id -un)"
    echo "Could not read your SonarQube login, falling back to ${sonar_login}" >&2
  fi
  project_key="hypergesture-dev-${sonar_login}"
fi

echo "Analysing against ${SONAR_HOST_URL}${project_key:+ as ${project_key}}"

# This repository is held to h3nc4-no-coverage, not the server default. Two dead
# defensive branches in GestureTracker put 100 percent out of reach.
sonar_gate="${SONAR_GATE:-h3nc4-no-coverage}"

# The gate is chosen per project, so the project has to exist first. Left to the
# scan it would be created under the default gate instead.
gate_key="${project_key:-$(sed -n 's/^sonar\.projectKey=//p' sonar-project.properties)}"
if [ -n "${gate_key}" ]; then
  sonar_api "curl -s -o /dev/null -u \"\${SONAR_TOKEN}:\" \
     -X POST \"\${SONAR_HOST_URL}/api/projects/create\" \
     --data-urlencode project=${gate_key} --data-urlencode name=${gate_key}"
  code="$(sonar_api "curl -s -o /dev/null -w '%{http_code}' -u \"\${SONAR_TOKEN}:\" \
     -X POST \"\${SONAR_HOST_URL}/api/qualitygates/select\" \
     --data-urlencode projectKey=${gate_key} --data-urlencode gateName=${sonar_gate}")"
  case "${code}" in
    204) echo "Gate ${sonar_gate} selected for ${gate_key}" ;;
    *) echo "Warning: selecting ${sonar_gate} returned ${code}" >&2 ;;
  esac
fi

set -- -Dsonar.qualitygate.wait=true
if [ -n "${project_key}" ]; then
  set -- "$@" -Dsonar.projectKey="${project_key}"
fi

scan_status=0
docker run --rm \
  --memory="${scanner_memory}" \
  -e SONAR_HOST_URL -e SONAR_TOKEN \
  -e SONAR_SCANNER_JAVA_OPTS="${scanner_java_opts}" \
  -v "${HYPERGESTURE_HOST_ROOT:-${PWD}}:${PWD}" \
  -w "${PWD}" \
  "${sonar_scan_image}" "$@" || scan_status=$?

# Only a clean scan is cleaned up. A failure keeps its project, so the dashboard
# the scanner just named is still there to read.
if [ -n "${delete_after}" ] && [ -n "${project_key}" ] && [ "${scan_status}" -eq 0 ]; then
  code="$(sonar_api \
    "curl -s -o /dev/null -w '%{http_code}' -u \"\${SONAR_TOKEN}:\" \
       -X POST \"\${SONAR_HOST_URL}/api/projects/delete\" \
       --data-urlencode project=${project_key}")"
  case "${code}" in
    204) echo "Deleted project ${project_key}" ;;
    *) echo "Warning: deleting ${project_key} returned ${code}" >&2 ;;
  esac
fi

exit "${scan_status}"
