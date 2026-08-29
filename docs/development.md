# Development

Everything a contributor needs. For what the app is and how to use it, see the [README](../README.md).

## Stack

- **Language.** Kotlin, JDK 21
- **Build.** Gradle with the Android Gradle Plugin, both provided by the Dev Container and pinned there to one version
- **Gestures.** `AccessibilityService` plus `TYPE_ACCESSIBILITY_OVERLAY` edge strips, which need no "draw over other apps" permission
- **UI.** Jetpack Compose, Material 3
- **Persistence.** DataStore Preferences, no database
- **Quality.** JUnit over a device-free recognizer, Android lint, shellcheck, SonarQube, and an emulator-driven gesture suite

Versions are pinned in `gradle/libs.versions.toml`, and Renovate raises the bumps in grouped pull requests. `minSdk` is 30 because the edge overlays need `WindowMetrics` insets and `setSystemGestureExclusionRects`. The emulator runs API 36 rather than matching `compileSdk`, because no API 37 `google_apis` x86_64 system image is published yet.

## Repository layout

```text
app/
  src/main/kotlin/com/h3nc4/hypergesture/
    gesture/      Pure, Android-free recognition (unit tested)
    service/      AccessibilityService, edge overlays, global actions
    navigation/   NavigationIntegration + HyperOS/generic implementations
    settings/     DataStore-backed configuration
    diagnostics/  Local-only device/state snapshot
    boot/         BOOT_COMPLETED observer
    ui/           MainActivity, the single Compose screen, theme
  src/test/kotlin/            Recognizer tests (plain JVM, no device)
docker/           Dev container and APK build images
scripts/          Entrypoint, hooks, sonar, version, emulator and E2E helpers
docs/             This file and the platform investigations
.github/          CI workflows
```

## Dev container

The host doesn't need a JDK or an Android SDK. The container has the whole toolchain, and Android Studio is never required.

Build the image once by hand with `docker build -f docker/dev.Dockerfile -t h3nc4/hypergesture-dev:"$(cat .github/VERSION)" .`. After that the merge is the release.

The image is versioned by a build id rather than semver: `.github/VERSION` holds a whole number that only CI writes. A change to the Dockerfile or the scripts it copies is tested against a candidate image built from the pull request. Once it reaches main, CI publishes the next id and moves `:latest`. It then rewrites the pin in `.devcontainer.json` and `.github/VERSION`, and tags the source commit `dc-v<id>` as the record.

There is nothing to decide, which is the point. Semver would promise a kind of compatibility that no box of build tools can honour, and nobody stays on an older one. The number only has to be ordered and unique. Renovate can bump a base image digest or a pinned tool with no human in the loop, and the release follows on merge. Publishing happens before the pin moves, so the pin never names an image that Docker Hub does not have yet.

Note `bash -c`, not `-lc`, when running commands in the container: a login shell sources `/etc/profile`, which replaces `PATH` and drops the SDK directories.

## Commands

Gradle is the only task runner. There is no Node toolchain.

```sh
gradle assembleDebug   # build
gradle test            # unit tests (GestureTracker decision logic)
gradle lint            # Android lint
gradle jacocoTestReport
shellcheck -o all scripts/*.sh scripts/hooks/*
./scripts/e2e-gestures.sh # gesture tests against a headless emulator
./scripts/emulator-vnc.sh # emulator on a browser-viewable display, installs the APK
./scripts/build-apk.sh    # signed release APK via docker/apk.Dockerfile
```

## Testing

Two layers, because they catch different things.

**Unit tests** (`gradle test`) cover `GestureTracker`, the pure decision logic. They do not need a device or Robolectric, because `GestureTracker` has no `android.*` imports at all. The hold timer is driven by calling `onHoldElapsed()` directly, so no clock is involved.

**End-to-end** (`./scripts/e2e-gestures.sh`) drives a headless emulator through the whole path:

- boots the emulator and installs the APK
- enables the accessibility service
- injects real swipes with `adb shell input`

It then asserts on two independent signals: logcat (did *our* service decide correctly) and `dumpsys` (did the system state actually change). It needs `/dev/kvm`:

```sh
docker run --rm --device /dev/kvm \
  -v "$HYPERGESTURE_HOST_ROOT:/workspaces/hypergesture" -w /workspaces/hypergesture \
  --entrypoint /bin/bash h3nc4/hypergesture-dev:"$(cat .github/VERSION)" \
  -c './scripts/e2e-gestures.sh'
```

`-k` leaves the emulator running for interactive poking. `-p hypergesture.apk` runs the suite against a release build, worth doing before every release, because R8 obfuscation and shrinking can break things no JVM test sees. It has already caught one such bug: log and diagnostics strings built from `::class.simpleName` came out as `a`/`b`/`c` once minified, so `GestureAction` carries an explicit `id`.

Every bug that ever reached a real device was in the Android integration, not the recognizer:

- overlay windows not receiving touches
- strips left permanently inert after a gesture fired
- coordinates measured against the wrong display

None of that is reachable from a JVM test.

## Releasing

Signing material lives outside git:

- `android.keystore` at the repository root
- `KEYSTORE_PASSWORD` in `.env`, gitignored and sourced by `scripts/build-apk.sh`

```sh
./scripts/build-apk.sh 1.0.0   # produces ./hypergesture.apk
```

The password reaches the build as a Docker build arg, and the final image stage is `FROM scratch` carrying only the APK, so a publishable layer contains neither the key nor the password. With either piece missing, the release build degrades to unsigned rather than failing, so CI can still verify that `assembleRelease` compiles.

**Back up the keystore somewhere off this machine.** Android identifies an app by its signing certificate, so an update signed with a different key is refused by every device that already has HyperGesture on it, and the only recovery is a new package name. `android.keystore` and `.env` are gitignored and must never be committed, which also means git is not a backup: keep the key and its password in a password manager, `base64` the keystore if the manager only takes text.

Pushing a `v*.*.*` tag runs the same build in CI and attaches the APK to a GitHub release. That needs two repository secrets, `ANDROID_KEYSTORE_B64` (`base64 -w0 android.keystore`) and `KEYSTORE_PASSWORD`. Unlike a local build, CI stops with an error when either is missing rather than publishing an unsigned APK, and it runs `apksigner verify --print-certs` on the result so the certificate is visible in the job log. After a release, CI bumps `versionCode` on `main`, because Android refuses an install whose `versionCode` went backwards.
