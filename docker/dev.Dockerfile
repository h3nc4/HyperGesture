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
# Development container for HyperGesture. The only place the toolchain is installed, so
# the JDK, the Android SDK and Android Studio all stay off the host.

########################################
# Runtime user configuration
# dev, because dev-base bakes the user it creates and every repository
# shares that image.
ARG USER="dev"
ARG UID="1000"
ARG GID="1000"

########################################
# Android
ARG ANDROID_CMDLINE_TOOLS="15859902"
ARG ANDROID_COMPILE_SDK="37.0"
ARG ANDROID_BUILD_TOOLS="37.0.0"
ARG ANDROID_EMULATOR_API="36"

########################################
# Gradle
ARG GRADLE_VERSION="9.8.0"
ARG GRADLE_SHA256="bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c"

# The package mirror to build through. It answers on one network only, so
# resolving the name is the test for reaching it.
ARG APT_MIRROR="http://debian.lan.h3nc4.com"

################################################################################
# Android SDK stage
FROM debian:trixie-slim@sha256:a99cfc517144bc59b1978475ec53b46ecabec7e43635402ee5b77cc54cd1b20a AS android-sdk
ARG ANDROID_CMDLINE_TOOLS
ARG ANDROID_COMPILE_SDK
ARG ANDROID_BUILD_TOOLS
ARG ANDROID_EMULATOR_API

ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="${ANDROID_HOME}"
ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"

ARG APT_MIRROR
RUN host="${APT_MIRROR#http://}"; \
  if [ -n "${host}" ] && getent hosts "${host}" >/dev/null 2>&1; then \
    sed -i "s|^URIs: http://deb.debian.org/\(.*\)$|URIs: ${APT_MIRROR}/\1 http://deb.debian.org/\1|" \
      /etc/apt/sources.list.d/debian.sources; \
  fi

RUN apt-get update && apt-get install -y --no-install-recommends \
  ca-certificates \
  openjdk-21-jdk-headless \
  unzip \
  wget

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
  && wget -qO /tmp/android-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" \
  && unzip -q /tmp/android-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
  && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
  && rm /tmp/android-tools.zip \
  && yes | sdkmanager --licenses >/dev/null \
  && sdkmanager --install \
  "platform-tools" \
  "platforms;android-${ANDROID_COMPILE_SDK}" \
  "build-tools;${ANDROID_BUILD_TOOLS}" \
  "emulator" \
  "system-images;android-${ANDROID_EMULATOR_API};google_apis;x86_64" >/dev/null

################################################################################
# Android AVD stage
FROM android-sdk AS android-avd
ARG ANDROID_EMULATOR_API
ENV ANDROID_AVD_HOME="/opt/android-avd"

RUN mkdir -p "${ANDROID_AVD_HOME}" \
  && echo "no" | avdmanager create avd --force \
  -n hypergesture \
  -k "system-images;android-${ANDROID_EMULATOR_API};google_apis;x86_64" \
  -d pixel_7

################################################################################
# Debian main stage
FROM h3nc4/dev-base:debian-13@sha256:1d854408035d42667be8b3b46e166f30b0ca41dff1583c1f04234f9d39e2ebaa AS main

# dev-base ends as the dev user, and the steps below need root.
USER root

# Not inherited: dev-base sets it while building, and its squashed image does not
# carry it into the runtime environment.
ENV DEBIAN_FRONTEND=noninteractive

# The Android copies below chown onto the user dev-base created.
ARG UID="1000"
ARG GID="1000"

# Headless emulator viewing. mesa gives the emulator a GL surface in a container.
# dev-base clears the apt lists, so this fetches them again.
ARG APT_MIRROR
RUN host="${APT_MIRROR#http://}"; \
  if [ -n "${host}" ] && getent hosts "${host}" >/dev/null 2>&1; then \
    sed -i "s|^URIs: http://deb.debian.org/\(.*\)$|URIs: ${APT_MIRROR}/\1 http://deb.debian.org/\1|" \
      /etc/apt/sources.list.d/debian.sources; \
  fi

RUN apt-get update -qq && apt-get install --no-install-recommends -y -qq \
  libgl1-mesa-dri \
  libglx-mesa0 \
  mesa-utils \
  novnc \
  openbox \
  openjdk-21-jdk-headless \
  psmisc \
  python3-websockify \
  tigervnc-common \
  tigervnc-standalone-server \
  unzip

########################################
# Gradle. There is no committed wrapper, so the build tool comes from here.
ARG GRADLE_VERSION
ARG GRADLE_SHA256
ADD "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" /tmp/gradle.zip
RUN echo "${GRADLE_SHA256}  /tmp/gradle.zip" | sha256sum -c - \
  && unzip -q /tmp/gradle.zip -d /opt \
  && mv "/opt/gradle-${GRADLE_VERSION}" /opt/gradle \
  && ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle \
  && rm /tmp/gradle.zip \
  && gradle --version

########################################
# Android SDK + AVD
ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="${ANDROID_HOME}"
ENV ANDROID_AVD_HOME="/opt/android-avd"
ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/emulator:${ANDROID_HOME}/platform-tools:${PATH}"

COPY --from=android-sdk --chown=${UID}:${GID} --chmod=0777 /opt/android-sdk /opt/android-sdk
COPY --from=android-avd --chown=${UID}:${GID} --chmod=0777 /opt/android-avd /opt/android-avd

# Only the top dirs need 0777 set. COPY already set the files inside them.
RUN chmod 0777 "${ANDROID_HOME}" "${ANDROID_AVD_HOME}"

########################################
# Clean cache
RUN apt-get clean && rm -rf /var/lib/apt/lists/* && \
  if [ -n "${APT_MIRROR}" ]; then \
    sed -i "s|${APT_MIRROR}/[^ ]* ||" \
      /etc/apt/sources.list.d/debian.sources; \
  fi
RUN rm -rf /var/cache/* /var/log/* /tmp/*

################################################################################
# Final squash image.
FROM scratch AS final
ARG USER
ENV USER="${USER}" \
  LANG="en_US.UTF-8" \
  LC_ALL="en_US.UTF-8" \
  ANDROID_HOME="/opt/android-sdk" \
  ANDROID_SDK_ROOT="/opt/android-sdk" \
  ANDROID_AVD_HOME="/opt/android-avd" \
  JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64" \
  GRADLE_HOME="/opt/gradle" \
  PATH="/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/emulator:/opt/android-sdk/platform-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

COPY --from=main / /

USER "${USER}"

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/entrypoint.sh"]
CMD ["/usr/bin/sleep", "infinity"]
