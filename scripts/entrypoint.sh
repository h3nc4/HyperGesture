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

# This devcontainer must be run with at least the following flags:
#  -e "HOST_UID_GID=$(id -u):$(id -g)"
#  -v "${HOME}/:/home/hypergesture/"
#  -v "${PWD}:/workspaces/hypergesture"
#  -v /var/run/docker.sock:/var/run/docker.sock

set -e

if [ -d "/workspaces/hypergesture" ]; then
  cd "/workspaces/hypergesture"
else
  echo "Error: /workspaces/hypergesture directory does not exist." >&2
  echo "Ensure the following flag is set in your run command:" >&2
  echo "  \`-v \${PWD}:/workspaces/hypergesture\`" >&2
  exit 1
fi

if [ ! -S /var/run/docker.sock ]; then
  echo "Error: Docker socket /var/run/docker.sock not found." >&2
  echo "Ensure the following flag is set in your run command:" >&2
  echo "  \`-v /var/run/docker.sock:/var/run/docker.sock\`" >&2
  exit 1
fi

# HOST_UID_GID is expected as UID:GID.
if [ -n "${HOST_UID_GID}" ]; then
  host_uid=$(echo "${HOST_UID_GID}" | cut -d: -f1)
  host_gid=$(echo "${HOST_UID_GID}" | cut -d: -f2)
elif [ -z "${DEVCONTAINER}" ]; then
  echo "HOST_UID_GID environment variable not set." >&2
  echo "Ensure the following flag is set in your run command:" >&2
  echo "  \`-e HOST_UID_GID=\$(id -u):\$(id -g)\`" >&2
  exit 1
fi

# Update the user and group IDs if they differ from the host's
current_uid=$(id -u hypergesture)
current_gid=$(id -g hypergesture)
if [ -z "${DEVCONTAINER}" ]; then
  if [ "${host_gid}" != "${current_gid}" ] || [ "${host_uid}" != "${current_uid}" ]; then
    echo "Current UID:GID (${current_uid}:${current_gid}) differs from host (${host_uid}:${host_gid})"
    echo "Updating hypergesture user to match host..."
    exec doas /usr/local/bin/switch-user.sh hypergesture "${host_uid}" "${host_gid}" "$0" "$@"
  fi
fi

host_gid=$(stat -c '%g' /var/run/docker.sock)
current_gid=$(getent group docker | cut -d: -f3)
if [ "${host_gid}" != "${current_gid}" ]; then
  echo "Updating docker group GID to ${host_gid}..."
  doas groupmod -o -g "${host_gid}" docker
fi

# Activate the repository's git hooks.
git config core.hooksPath scripts/hooks || :

# No Gradle build here: it would add minutes to every container start.

./scripts/sonar.sh -i

doas mandb >/dev/null 2>&1

echo "Container initialized successfully."
echo "Run \`docker exec -it <container_name> bash\` to start developing."
exec "$@"
