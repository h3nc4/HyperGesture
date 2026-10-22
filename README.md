# HyperGesture

> Edge gestures for Xiaomi HyperOS, so you can keep the launcher you actually like.

HyperOS ties its gesture navigation to the stock launcher. Switch to Niagara, Nova or Lawnchair and full-screen gestures stop working. The phone drops you back to the three-button bar with no way to turn them on again.

HyperGesture gives you the gestures back. It's one app, and that's all it needs: no Shizuku, root, ADB, PC, companion app or internet connection.

The gestures work on any phone running Android 11 or newer. HyperOS adds two shortcuts, listed under [Requirements](#requirements).

**[Download the latest release](https://github.com/h3nc4/HyperGesture/releases/latest)**

## Your gestures

| Gesture | Does |
| --- | --- |
| Swipe in from the **left or right edge** | Back |
| Swipe up from the **bottom edge** | Home |
| Swipe up from the bottom **and hold** | Recent apps |
| Swipe **right along the bottom edge** | Previous app, once app switching is on |
| Swipe **left along the bottom edge** | Next app, once app switching is on |

A side swipe draws a dark bulge with a back arrow beside the finger, the way HyperOS's own Back gesture does. It grows as the finger moves inward and shrinks away on release.

A back swipe can be taken back before lifting the finger. Pulling it back inside the side swipe distance drops the arrow and shrinks the bulge, and releasing there does nothing, neither Back nor a tap on the app underneath. Pushing out past that distance again re-arms it.

Recent apps opens once the finger has rested for the hold time, before it lifts. A swipe has to stay within 45 degrees of its own direction to count. A touch near an edge that never becomes a gesture is replayed to the app underneath as an ordinary tap, long press or drag.

App switching walks the apps used in the last six hours, newest first. Swipes within five seconds of each other keep walking the same order, and after a longer pause the next swipe starts again from the most recent app. It needs usage access, granted in step 5 of [Installing](#installing).

Each threshold is adjustable in Gesture settings:

| Setting | Default | Range |
| --- | --- | --- |
| Edge activation width, how far in from the edge a swipe can start | 20 dp | 8 to 64 dp |
| Side swipe distance | 24 dp | 8 to 80 dp |
| Bottom swipe distance | 10 dp | 4 to 60 dp |
| Hold for Recent apps | 100 ms | 50 to 500 ms |
| Time allowed to start a swipe, after which the touch goes to the app underneath | 1000 ms | 300 to 3000 ms |
| Left edge swipe = Back, Right edge swipe = Back | on | on or off |
| Vibrate when a gesture fires | on | on or off |

Tune those if a gesture fires when you did not mean it, or fails to fire when you did. **Reset to defaults** puts every value back.

## Requirements

- Android 11 or newer
- Tested on a Redmi 14 running Android 16 / HyperOS OS3.0, and by the test suite on a stock Android 16 emulator

On a Xiaomi, Redmi or POCO phone running HyperOS or MIUI, the app adds a **Navigation bar** shortcut and an **Autostart** shortcut. Any other phone gets the same gestures, the Navigation bar card reads **Unavailable**, and the Autostart button opens the app's App info page instead.

Permissions, and how each is granted:

| Permission | Granted | Used for |
| --- | --- | --- |
| Accessibility service | by hand, required | every gesture |
| Usage access | by hand, optional | Previous app and Next app alone |
| `RECEIVE_BOOT_COMPLETED` | at install | checking the navigation mode after a reboot |

## Installing

HyperGesture isn't on any app store, so you install the APK yourself.

**1. Download and install it.** On your phone, open [the latest release](https://github.com/h3nc4/HyperGesture/releases/latest), download `hypergesture.apk`, and install it.

**2. Allow restricted settings.** On Android 13 and newer this step is mandatory, and skipping it is the most common reason the app appears to do nothing:

> Settings -> Apps -> HyperGesture -> ⋮ menu -> **Allow restricted settings**

Android blocks sideloaded apps from using accessibility features until you opt in. Without this, the switch in step 3 will look like it turned on but the app will never actually start.

**3. Turn it on.** Settings -> Accessibility -> **HyperGesture** -> on.

Then open HyperGesture. The first card should say **Active**. Try a swipe.

**4. On HyperOS, keep it running.** HyperOS stops the gestures in the background unless autostart is allowed and battery restriction is off. In the **Keep me running** card, tap **Autostart** and allow it, then tap **App info** and choose **No restrictions** under Power.

**5. Optionally, turn on app switching.** In the **App switching** card, turn on the sideways swipe, tap **Grant** and allow Usage access for HyperGesture. Back in the app, the card reads **Usage access granted**.

## Using it

The app shows these cards, top to bottom:

- **Gesture service**: whether your gestures are running, and a shortcut to the Accessibility screen if they aren't.
- **Control Center tile**: a button that adds the HyperGesture tile to Control Center on Android 13 and newer, and the outcome of that request. Older versions take the tile by editing Control Center by hand. The tile reads Active or Not running, and a tap on it while stopped opens the Accessibility screen.
- **Keep me running**: the Autostart and App info shortcuts from step 4 above.
- **Navigation bar**: on HyperOS, an optional shortcut to hide the three-button bar and reclaim that screen space. See [What it can't do](#what-it-cant-do).
- **App switching**: the switch for Previous app and Next app, and whether usage access is granted. It stays expanded, because this gesture needs a permission before it works.
- **Your gestures**: a reminder of what each swipe does.
- **Gesture settings**: tap to expand for the thresholds, a haptic-feedback switch and a reset button. It stays collapsed by default so a stray tap cannot move a slider.
- **Diagnostics**: tap to expand. Device details, the current navigation mode, whether the service is enabled and running, and the last Back, Home, Recents or app switch failure. Useful when reporting a problem. It never leaves the phone.

The app follows your system light/dark setting.

## Privacy

HyperGesture holds `RECEIVE_BOOT_COMPLETED` and `PACKAGE_USAGE_STATS`, and the second does nothing until Usage access is granted by hand. It also declares that it looks up installed apps with a launcher icon, which Android 11 and newer require before one app can find how to start another. It lacks internet, storage and location access.

Gesture navigation needs an accessibility service, which is a powerful thing to grant. To be concrete about this one: it watches for touches in thin strips along the screen edges and turns a swipe into Back, Home, Recents or a switch to another app. Its configuration withholds both window content and accessibility events from it, so it never reads screen contents, typed text or keystrokes.

With app switching on, the app reads which apps came to the front in the last six hours, to know where Previous app leads. That list stays in memory on the phone, like everything in Diagnostics, because the app lacks network access.

## What it can't do

**It can't turn on HyperOS's own gesture navigation.** That's locked behind permissions only the system itself holds, and no ordinary app can get them (including via Shizuku, which still needs ADB or root to start).

The **Navigation bar** button opens the navigation-mode screen, where you can hide the three-button bar and reclaim that screen space. Choosing gesture navigation there still leaves a third-party launcher without working native gestures, so HyperGesture's own gestures remain the ones that function, either way.

If HyperOS resets your navigation mode after a reboot, Diagnostics shows the mode it came back in, but the app can't change it back for you.

App switching skips the home screen and any app without a launcher icon, since those lack a launch entry to start.

The details are in [docs/hyperos-navigation.md](docs/hyperos-navigation.md).

## If something goes wrong

**Gestures stopped working, or Accessibility says "This service is malfunctioning."** Turn HyperGesture off and on again under Settings -> Accessibility. Android remembers that a service once failed and won't restart it on its own, even after an app update.

**Gestures stop after a while, or after a reboot, on HyperOS.** Autostart or battery restriction is still at its default. Redo step 4 above.

**Gestures stopped and the navigation bar is hidden, so Back and Home are gone.** Swipe down from the top of the screen and tap the HyperGesture tile, which opens the Accessibility screen. Add the tile from the app beforehand.

**The Accessibility switch does nothing.** You missed step 2 above. Allow restricted settings, then try again.

**Previous app and Next app do nothing.** Usage access is missing, or the swipe is still off in the App switching card. Diagnostics names the reason under **Last app switch problem**.

**Taps near the bottom of the screen feel slightly slow.** Known. The app has to briefly take each touch near an edge to see whether it becomes a swipe, then hand it back if it doesn't. Reducing the edge activation width in Gesture settings shrinks the area this affects.

**A gesture fires when you didn't mean it, or won't fire when you do.** Adjust the swipe distance for that edge in Gesture settings. The bottom edge is deliberately more sensitive than the sides, because a swipe starting on the navigation bar has already travelled some distance before the app sees it.

## Building and testing

The Docker image that `.devcontainer.json` names holds the whole toolchain. The host needs Docker alone. From a clone, this builds the debug APK and runs the unit tests:

```sh
git clone https://github.com/h3nc4/HyperGesture.git
cd HyperGesture
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e GRADLE_USER_HOME=/tmp/gradle \
  -v "$PWD:/workspaces/hypergesture" -w /workspaces/hypergesture \
  --entrypoint /bin/bash "$(scripts/devcontainer-image.sh)" \
  -c 'gradle --no-daemon assembleDebug test'
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Pass `-c 'gradle --no-daemon lint'` instead for Android lint.

The end-to-end suite boots a headless Android emulator, installs that debug APK and drives real swipes through it, so it needs `/dev/kvm` and the debug build above. Add `--device /dev/kvm` to the same `docker run` and pass `-c './e2e/run.sh'`. With `-p hypergesture.apk` it runs against a release build from `scripts/build-apk.sh` instead, and `-k` leaves the emulator running afterwards.

Releasing, signing and debugging on a device are in [docs/development.md](docs/development.md).

## Documentation

| Where | What it answers |
| --- | --- |
| `README.md` | What HyperGesture is, installing and using it, building it from source |
| [`docs/hyperos-navigation.md`](docs/hyperos-navigation.md) | What HyperOS does and doesn't permit about the navigation bar |
| [`docs/development.md`](docs/development.md) | Building, testing, releasing, debugging on a device |

## License

<!-- vale off -->

HyperGesture is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

HyperGesture is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with HyperGesture. If not, see https://www.gnu.org/licenses/.

Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
