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
# sdkmanager only installs the newest emulator, platform-tools and system image, so those come
# as pinned zips. Each sha256 is of a zip whose sha1 matches Google's repository2-3.xml.
ARG ANDROID_CMDLINE_TOOLS="15859902"
ARG ANDROID_CMDLINE_TOOLS_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
ARG ANDROID_PLATFORM_TOOLS="37.0.1"
ARG ANDROID_PLATFORM_TOOLS_SHA256="d230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1"
ARG ANDROID_EMULATOR="37.2.13"
ARG ANDROID_EMULATOR_BUILD="16740729"
ARG ANDROID_EMULATOR_SHA256="cbc7c3cfb83220b8a0f9c513fdab557712e74d339ec19ddffa43f23350ae9bdd"
ARG ANDROID_EMULATOR_API="36"
ARG ANDROID_SYSTEM_IMAGE="07"
ARG ANDROID_SYSTEM_IMAGE_SHA256="b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160"
ARG ANDROID_COMPILE_SDK="37.0"
ARG ANDROID_BUILD_TOOLS="37.0.0"

# The package mirror to build through. It answers on one network only, so
# resolving the name is the test for reaching it.
ARG APT_MIRROR="http://debian.lan.h3nc4.com"

################################################################################
# Android SDK stage
FROM debian:trixie-slim@sha256:a72b4433de1cf7d6bae0c4cee74d0547f58601a643b3375f070c51600d63b6b2 AS android-sdk
ARG ANDROID_CMDLINE_TOOLS
ARG ANDROID_CMDLINE_TOOLS_SHA256
ARG ANDROID_PLATFORM_TOOLS
ARG ANDROID_PLATFORM_TOOLS_SHA256
ARG ANDROID_EMULATOR
ARG ANDROID_EMULATOR_BUILD
ARG ANDROID_EMULATOR_SHA256
ARG ANDROID_EMULATOR_API
ARG ANDROID_SYSTEM_IMAGE
ARG ANDROID_SYSTEM_IMAGE_SHA256
ARG ANDROID_COMPILE_SDK
ARG ANDROID_BUILD_TOOLS

ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="${ANDROID_HOME}"
ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"

ARG APT_MIRROR
RUN host="${APT_MIRROR#http://}"; \
  if [ -n "${host}" ] && getent hosts "${host}" >/dev/null 2>&1; then \
    sed -i "s|^URIs: http://deb.debian.org/|URIs: ${APT_MIRROR}/|" \
      /etc/apt/sources.list.d/debian.sources; \
  fi

RUN apt-get update && apt-get install -y --no-install-recommends \
  ca-certificates \
  openjdk-21-jdk-headless \
  unzip \
  wget

RUN fetch() { \
    wget -qO /tmp/package.zip "https://dl.google.com/android/repository/$1" \
    && echo "$2  /tmp/package.zip" | sha256sum -c --quiet - \
    && mkdir -p "$3" && unzip -q /tmp/package.zip -d "$3" && rm /tmp/package.zip; \
  } \
  && fetch "commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" \
  "${ANDROID_CMDLINE_TOOLS_SHA256}" "${ANDROID_HOME}/cmdline-tools" \
  && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
  && fetch "platform-tools_r${ANDROID_PLATFORM_TOOLS}-linux.zip" \
  "${ANDROID_PLATFORM_TOOLS_SHA256}" "${ANDROID_HOME}" \
  && fetch "emulator-linux_x64-${ANDROID_EMULATOR_BUILD}.zip" \
  "${ANDROID_EMULATOR_SHA256}" "${ANDROID_HOME}" \
  && fetch "sys-img/google_apis/x86_64-${ANDROID_EMULATOR_API}_r${ANDROID_SYSTEM_IMAGE}.zip" \
  "${ANDROID_SYSTEM_IMAGE_SHA256}" "${ANDROID_HOME}/system-images/android-${ANDROID_EMULATOR_API}/google_apis"

# avdmanager refuses to create an AVD without an emulator package and ignores the emulator zip's
# source.properties. This writes the package.xml that sdkmanager would have written.
RUN major="${ANDROID_EMULATOR%%.*}" rest="${ANDROID_EMULATOR#*.}" \
  && printf '%s\n' \
  '<?xml version="1.0" encoding="UTF-8"?>' \
  '<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02"' \
  '  xmlns:ns5="http://schemas.android.com/repository/android/generic/02"' \
  '  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">' \
  '  <localPackage path="emulator">' \
  '    <type-details xsi:type="ns5:genericDetailsType"/>' \
  "    <revision><major>${major}</major><minor>${rest%%.*}</minor><micro>${rest#*.}</micro></revision>" \
  '    <display-name>Android Emulator</display-name>' \
  '  </localPackage>' \
  '</ns2:repository>' >"${ANDROID_HOME}/emulator/package.xml"

RUN yes | sdkmanager --licenses >/dev/null \
  && sdkmanager --install \
  "platforms;android-${ANDROID_COMPILE_SDK}" \
  "build-tools;${ANDROID_BUILD_TOOLS}" >/dev/null \
  && sdkmanager --list_installed

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
FROM h3nc4/dev-base:debian-13@sha256:80a26b3967537848ad1331b5b61b4a60ea2f918968a65918f1d86e7ed021900a AS main

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
    sed -i "s|^URIs: http://deb.debian.org/|URIs: ${APT_MIRROR}/|" \
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
# Copied from the official image, so Renovate moves its version and digest as one pin.
COPY --from=gradle:9.9.2-jdk21@sha256:f8c4f7de6ec4f367cbf7fc46db08129ea6a6e9487995bb705c61fbc20f3bc545 /opt/gradle /opt/gradle
RUN ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle \
  && gradle --version

########################################
# Android SDK + AVD
ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="${ANDROID_HOME}"
ENV ANDROID_AVD_HOME="/opt/android-avd"
ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/emulator:${ANDROID_HOME}/platform-tools:${PATH}"

# The SDK is root-owned and read-only. The AVD is world-writable because the emulator writes its
# disks there, and the entrypoint remaps dev to the host's UID, which doesn't own files under /opt.
COPY --from=android-sdk /opt/android-sdk /opt/android-sdk
COPY --from=android-avd --chown=${UID}:${GID} --chmod=0777 /opt/android-avd /opt/android-avd
RUN chmod 0777 "${ANDROID_AVD_HOME}"

########################################
# Clean cache
RUN apt-get clean && rm -rf /var/lib/apt/lists/* && \
  if [ -n "${APT_MIRROR}" ]; then \
    sed -i "s|^URIs: ${APT_MIRROR}/|URIs: http://deb.debian.org/|" \
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
