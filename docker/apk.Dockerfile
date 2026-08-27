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

################################################################################
# A Dockerfile to build the HyperGesture release APK using the dev image toolchain.

ARG DEV_IMAGE_TAG="1.0.0@sha256:7356c794012a3a9c55eecfc4604f5c4098427f3f15b6eea455f5c1c8b962f860"

FROM h3nc4/hypergesture-dev:${DEV_IMAGE_TAG} AS builder

ARG VERSION="1.0.0"
# Signing is optional: without a keystore the build produces an unsigned release.
ARG KEYSTORE_PASSWORD=""
ARG KEYSTORE_ALIAS="hypergesture"

USER root
WORKDIR /workspaces/hypergesture

COPY --chown=1000:1000 . .

ENV KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD}"
ENV KEYSTORE_ALIAS="${KEYSTORE_ALIAS}"
ENV GRADLE_USER_HOME="/tmp/gradle"

RUN ./scripts/version.sh "${VERSION}" \
  && gradle --no-daemon assembleRelease

# The task output name depends on whether signing material was present.
RUN mkdir -p /out \
  && cp "$(find app/build/outputs/apk/release -name '*.apk' | head -n 1)" \
  /out/hypergesture.apk

################################################################################
# Final stage to extract only the APK
FROM scratch AS final

COPY --from=builder /out/hypergesture.apk /hypergesture.apk
