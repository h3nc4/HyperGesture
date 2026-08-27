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

# Called by CI to update version numbers in various files based on the Git tag.
set -e

cd "$(dirname "$0")/../"

version=${1#v}
if [ -z "${version}" ]; then
  echo "usage: ${0} <version>" >&2
  exit 2
fi

sed -i 's/versionName = ".*"/versionName = "'"${version}"'"/' app/build.gradle.kts
sed -i 's/sonar.projectVersion=.*/sonar.projectVersion='"${version}"'/' sonar-project.properties
