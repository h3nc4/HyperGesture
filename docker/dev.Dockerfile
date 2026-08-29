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
# Development container for HyperGesture. The only place the toolchain is installed:
# the host needs no JDK, no Android SDK and no Android Studio.

########################################
# Runtime user configuration
ARG USER="hypergesture"
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
ARG GRADLE_VERSION="9.7.1"
ARG GRADLE_SHA256="acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"

################################################################################
# Android SDK stage
FROM debian:trixie@sha256:f324c7ff54321e8d9c588493a20244965938ce0aa50bbd1022d38010e9ffc4b1 AS android-sdk
ARG ANDROID_CMDLINE_TOOLS
ARG ANDROID_COMPILE_SDK
ARG ANDROID_BUILD_TOOLS
ARG ANDROID_EMULATOR_API

ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="${ANDROID_HOME}"
ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"

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
FROM debian:trixie@sha256:f324c7ff54321e8d9c588493a20244965938ce0aa50bbd1022d38010e9ffc4b1 AS main
ARG USER
ARG UID
ARG GID

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update -qq

# Gen locale
RUN apt-get install --no-install-recommends -y -qq locales && \
  echo "en_US.UTF-8 UTF-8" >/etc/locale.gen && \
  locale-gen en_US.UTF-8 && \
  update-locale LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8

# shellcheck is the shell linter used by the hooks and CI.
RUN apt-get install --no-install-recommends -y -qq \
  bash-completion \
  ca-certificates \
  curl \
  file \
  git \
  gnupg \
  gosu \
  iproute2 \
  iputils-ping \
  jq \
  less \
  man-db \
  nano \
  openjdk-21-jdk-headless \
  openssh-client \
  opendoas \
  procps \
  psmisc \
  shellcheck \
  tini \
  tree \
  unzip \
  wget

# Docker outside of Docker, used by scripts/sonar.sh and scripts/build-apk.sh
RUN apt-get install --no-install-recommends -y -qq \
  docker-cli \
  docker-buildx

# Headless emulator viewing; mesa gives the emulator a GL surface in a container.
RUN apt-get install --no-install-recommends -y -qq \
  libgl1-mesa-dri \
  libglx-mesa0 \
  mesa-utils \
  novnc \
  openbox \
  python3-websockify \
  tigervnc-common \
  tigervnc-standalone-server

# Gradle. There is no committed wrapper; this is where the build tool comes from.
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
# Create a non-root developing user and configure doas
RUN addgroup --gid "${GID}" "${USER}"
RUN adduser --uid "${UID}" --gid "${GID}" \
  --shell "/bin/bash" --disabled-password "${USER}"

RUN addgroup --gid 110 docker && usermod -aG docker "${USER}"

RUN printf "permit nopass nolog keepenv %s as root\n" "${USER}" >/etc/doas.conf && \
  chmod 400 /etc/doas.conf && \
  printf "%s\nset -e\n%s\n" "#!/bin/sh" "doas \"\$@\"" >/usr/local/bin/sudo && \
  chmod a+rx /usr/local/bin/sudo

COPY scripts/switch-user.sh /usr/local/bin/switch-user.sh
COPY scripts/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/switch-user.sh /usr/local/bin/entrypoint.sh

########################################
# Android SDK + AVD
ENV ANDROID_HOME="/opt/android-sdk"
ENV ANDROID_SDK_ROOT="${ANDROID_HOME}"
ENV ANDROID_AVD_HOME="/opt/android-avd"
ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/emulator:${ANDROID_HOME}/platform-tools:${PATH}"

COPY --from=android-sdk --chown=${UID}:${GID} --chmod=0777 /opt/android-sdk /opt/android-sdk
COPY --from=android-avd --chown=${UID}:${GID} --chmod=0777 /opt/android-avd /opt/android-avd

# Only the top dirs need 0777 set; COPY already set the files inside them.
RUN chmod 0777 "${ANDROID_HOME}" "${ANDROID_AVD_HOME}"

########################################
# Clean cache
RUN apt-get clean && rm -rf /var/lib/apt/lists/*
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
