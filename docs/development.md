# Development

Everything a contributor needs. For what the app is and how to use it, see the [README](../README.md).

## Stack

- **Language.** Kotlin, JDK 21
- **Build.** Gradle with the Android Gradle Plugin, both provided by the Dev Container and pinned there to one version
- **Gestures.** `AccessibilityService` plus `TYPE_ACCESSIBILITY_OVERLAY` edge strips, which don't need the "draw over other apps" permission
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
site/             The website, an Astro build served as Worker static assets
.github/          CI workflows
```

## Dev container

The host doesn't need a JDK or an Android SDK. The container has the whole toolchain, and Android Studio is never required.

Build the image once by hand with `docker build -f docker/dev.Dockerfile -t "$(scripts/devcontainer-image.sh)" .`. After that the merge is the release.

The image is versioned by a build id rather than semver. The tag in the image key of `.devcontainer.json` is a whole number that only CI writes. A pull request that changes the Dockerfile gets that pin moved to the next id in a commit of its own, and its checks run in a candidate image built from the branch. Once it reaches main, CI publishes the pinned id and moves `:latest`. Docker Hub is the only record of a release.

There is nothing to decide, by design. Semver would promise a kind of compatibility that no box of build tools can honour, and nobody stays on an older one. The number only has to be ordered and unique. Renovate can bump a base image digest or a pinned tool with no human in the loop, and the release follows on merge. The pin and the change behind it merge together, so main only pins an image built from its own tree.

Note `bash -c`, not `-lc`, when running commands in the container: a login shell sources `/etc/profile`, which replaces `PATH` and drops the SDK directories.

## Commands

Gradle is the only task runner for the app. Node appears only in `site/`, the website, and runs in a container.

```sh
gradle assembleDebug   # build
gradle test            # unit tests (GestureTracker decision logic)
gradle lint            # Android lint
gradle jacocoTestReport
shellcheck -o all scripts/*.sh scripts/hooks/*
./e2e/run.sh              # gesture tests against a headless emulator
./scripts/emulator-vnc.sh # emulator on a browser-viewable display, installs the APK
./scripts/build-apk.sh    # signed release APK via docker/apk.Dockerfile
```

## Testing

Two layers, because they catch different things.

**Unit tests** (`gradle test`) cover `GestureTracker`, the pure decision logic. They do not need a device or Robolectric, because `GestureTracker` has no `android.*` imports at all. The hold timer is driven by calling `onHoldElapsed()` directly, so no clock is involved.

**End-to-end** (`./e2e/run.sh`) drives a headless emulator through the whole path:

- boots the emulator and installs the APK
- enables the accessibility service
- injects real swipes with `adb shell input`

It then asserts on two independent signals: logcat (did *our* service decide correctly) and `dumpsys` (did the system state actually change). It needs `/dev/kvm` and the debug APK from `gradle assembleDebug`:

```sh
docker run --rm --device /dev/kvm -u "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "${HOST_ROOT:-$PWD}:/workspaces/hypergesture" -w /workspaces/hypergesture \
  --entrypoint /bin/bash "$(scripts/devcontainer-image.sh)" \
  -c './e2e/run.sh'
```

`-k` leaves the emulator running for interactive poking. `-p hypergesture.apk` runs the suite against a release build. Run it before every release, because R8 obfuscation and shrinking can break things no JVM test sees. It has already caught one such bug: log and diagnostics strings built from `::class.simpleName` came out as `a`/`b`/`c` once minified, so `GestureAction` has an explicit `id`. R8 also strips every `Log.d` and `Log.v` call from a release build, so a line the suite greps for logs at info or above.

A release build ignores the debug overrides that `e2e/device.sh` writes, so for a release APK the script turns app switching on through the app's own toggle, the way a user would, and the app-switch suite runs in full. That keeps an R8 break in the toggle, its DataStore write or the recognizer visible as a failing case, where a skip would hide all three. The cost is a dependency on the screen. The script reads the `label_app_switch` string, finds that label with `uiautomator` and taps the first switch after it, so a layout that moves the switch away from its label fails the run before any gesture is tried. The Recents hold also keeps its 100 ms default, since its override is debug-only as well. Installing over a build signed with another key uninstalls it first, so the release and debug runs can share one emulator, and on a phone that wipes the installed copy and its settings.

Every bug found on a real device so far was in the Android integration, not the recognizer:

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

The build receives the password as a Docker build arg. The final image stage is `FROM scratch` with only the APK in it, and no publishable layer holds the key or the password. With either piece missing, the release build degrades to unsigned rather than failing, so CI can still verify that `assembleRelease` compiles.

**Back up the keystore somewhere off this machine.** Android identifies an app by its signing certificate, so an update signed with a different key is refused by every device that already has HyperGesture on it, and the only recovery is a new package name. `android.keystore` and `.env` are gitignored and must never be committed, which also means git is not a backup: keep the key and its password in a password manager, `base64` the keystore if the manager only takes text.

Pushing a `v*.*.*` tag runs the same build in CI and attaches the APK to a GitHub release. That needs two repository secrets, `ANDROID_KEYSTORE_B64` (`base64 -w0 android.keystore`) and `KEYSTORE_PASSWORD`. Unlike a local build, CI stops with an error when either is missing rather than publishing an unsigned APK, and it runs `apksigner verify --print-certs` on the result so the certificate is visible in the job log. After a release, CI bumps `versionCode` on `main`, because Android refuses an install whose `versionCode` went backwards.

## Website

`site/` builds hypergesture.h3nc4.com with Astro into static HTML and CSS, with no client JavaScript. The English strings are in `site/src/i18n/en.ts` and the Brazilian Portuguese ones in `site/src/i18n/pt.ts`, which render `/` and `/pt/` from the same components. Add a string to both files, since `pt.ts` is declared with the type of the English object.

The host has no Node, so run npm in the pinned image the site workflow uses:

```sh
node_image=node:24.22.1-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205
docker run --rm -it -u "$(id -u):$(id -g)" -e HOME=/tmp -v "${PWD}/site:/site" -w /site "${node_image}" npm ci
docker run --rm -it -u "$(id -u):$(id -g)" -e HOME=/tmp -v "${PWD}/site:/site" -w /site "${node_image}" npm run build
docker run --rm -it -u "$(id -u):$(id -g)" -e HOME=/tmp -v "${PWD}/site:/site" -w /site "${node_image}" npm run check
docker run --rm -it -u "$(id -u):$(id -g)" -e HOME=/tmp -v "${PWD}/site:/site" -w /site -p 4321:4321 "${node_image}" npx astro dev --host 0.0.0.0
```

`npm run check` fails when a built page weighs more than 100 KB with the font and every file it loads, or when a link points at an id or a page missing from `dist/`.

The version, the APK size and the signing certificate come from the `VERSION`, `APK_SIZE` and `CERT_SHA256` environment variables, read by `site/src/facts.ts` at build time. Unset, they fall back to v1.0.0. `MIN_ANDROID` sets the Android version and defaults to 11. The QR code for the APK link is generated during the build as inline SVG.

The site deploys as an assets-only Cloudflare Worker named `hypergesture-site`, with no script, on the custom domain in `site/wrangler.toml`. The `HyperGesture Site` workflow builds and checks it on every pull request touching `site/`, and deploys on a push to main touching `site/`, reading the version and size of the latest release from the GitHub API. The release workflow calls the same workflow after publishing, with the version, size and certificate of the APK it built. Both deploys need the repository secret `CLOUDFLARE_API_TOKEN`. To deploy by hand, build first, then run `npx wrangler@4.151.2 deploy` in `site/` with that token in the environment.
