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

# Builds the release APK in a container, dropping hypergesture.apk in the repo root.
set -e

cd "$(dirname "$0")/../"

if [ -f "${PWD}/.env" ]; then
  # shellcheck disable=SC1091 # "${PWD}/.env" file is optional
  . "${PWD}/.env"
fi

version="${1#v}"
if [ -z "${version}" ]; then
  version="1.0.0"
fi

# CI passes the tag with the digest it resolved, so the build and the verify share one image.
dev_image_tag="${DEV_IMAGE_TAG:-$(./scripts/devcontainer-image.sh -t)}"

set -- -f docker/apk.Dockerfile \
  --build-arg VERSION="${version}" \
  --build-arg DEV_IMAGE_TAG="${dev_image_tag}" \
  --output type=local,dest=.
if [ -s android.keystore ]; then
  set -- "$@" --secret id=keystore,src=android.keystore
fi
if [ -n "${KEYSTORE_PASSWORD:-}" ]; then
  export KEYSTORE_PASSWORD
  set -- "$@" --secret id=keystore_password,env=KEYSTORE_PASSWORD
fi

docker build "$@" .
