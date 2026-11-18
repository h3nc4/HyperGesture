# hypergesture-dev

Development container for [HyperGesture](https://github.com/h3nc4/HyperGesture), an Android app that brings edge gestures to Xiaomi HyperOS under a third-party launcher. The image contains the whole toolchain, so the JDK, the Android SDK and the emulator never touch the host.

## Contents

- OpenJDK 21 and Gradle, since the repository commits no Gradle wrapper
- Android command-line tools, platform tools, build tools and one compile SDK
- Android emulator with a Google APIs x86_64 system image and a ready `hypergesture` AVD
- TigerVNC, noVNC and openbox, for viewing the headless emulator from a browser
- Everything in [h3nc4/dev-base](https://hub.docker.com/r/h3nc4/dev-base), which this image builds on

[dev.Dockerfile](https://github.com/h3nc4/HyperGesture/blob/main/docker/dev.Dockerfile) pins every version.

## Tags

Each tag is a whole number that grows by one whenever the image inputs change. The `image` key of [.devcontainer.json](https://github.com/h3nc4/HyperGesture/blob/main/.devcontainer.json) is the tag the repository builds with, and CI moves that pin on the pull request that changes the image.

## Usage

Open the repository in a dev container and the pinned tag is pulled for you. The emulator needs `/dev/kvm`, which the container reaches because it runs privileged. CI runs the same image for lint, unit tests, the APK builds and the emulator suites.

## License

GPL-3.0-or-later, as the [source](https://github.com/h3nc4/HyperGesture) is.
