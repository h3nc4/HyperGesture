# HyperGesture

> Edge gestures for Xiaomi HyperOS, so you can keep the launcher you actually like.

HyperOS ties its gesture navigation to the stock launcher. Switch to Niagara, Nova or Lawnchair and full-screen gestures stop working. The phone drops you back to the three-button bar with no way to turn them on again.

HyperGesture gives you the gestures back. It's one app, and that's all it needs: no Shizuku, root, ADB, PC, companion app or internet connection.

**[Download the latest release](https://github.com/h3nc4/HyperGesture/releases/latest)**

## Your gestures

| Gesture | Does |
| --- | --- |
| Swipe in from the **left or right edge** | Back |
| Swipe up from the **bottom edge** | Home |
| Swipe up from the bottom **and hold** | Recent apps |

Every one of them is adjustable in Gesture settings:

- how far in from the edge it listens
- the distance you have to swipe
- what "hold" means
- whether the left and right edges are active

Tune those if a gesture fires when you did not mean it, or fails to fire when you did.

## Requirements

- Android 11 or newer
- Tested on a Redmi 14 running Android 16 / HyperOS OS3.0

## Installing

HyperGesture isn't on any app store, so you install the APK yourself.

**1. Download and install it.** On your phone, open [the latest release](https://github.com/h3nc4/HyperGesture/releases/latest), download `hypergesture.apk`, and install it.

**2. Allow restricted settings.** This step is not optional, and skipping it is the most common reason the app appears to do nothing:

> Settings -> Apps -> HyperGesture -> **Allow restricted settings**

Android blocks sideloaded apps from using accessibility features until you opt in. Without this, the switch in step 3 will look like it turned on but the app will never actually start.

**3. Turn it on.** Settings -> Accessibility -> **HyperGesture** -> on.

Then open HyperGesture. The first card should say **Active**. Try a swipe.

## Using it

Open the app and you'll see:

- **Gesture service**: whether your gestures are running, and a shortcut to the Accessibility screen if they aren't.
- **Your gestures**: a reminder of what each swipe does.
- **Navigation bar**: an optional shortcut to hide the three-button bar and reclaim that screen space. See [What it can't do](#what-it-cant-do).
- **Gesture settings**: tap to expand for the thresholds, a haptic-feedback switch and a reset button. It stays collapsed by default so a stray tap cannot move a slider.
- **Diagnostics**: tap to expand. Device details and anything that recently went wrong. Useful if you're reporting a problem. It never leaves your phone.

The app follows your system light/dark setting.

## Privacy

HyperGesture asks for one permission, `RECEIVE_BOOT_COMPLETED`, and nothing else. It has no internet, storage or location access.

Gesture navigation needs an accessibility service, which is a powerful thing to grant. To be concrete about this one: it watches for touches in thin strips along the screen edges and performs Back, Home and Recents. It does not read what's on your screen, your text, or your keystrokes, and it sends nothing anywhere. Everything in Diagnostics stays on the device.

## What it can't do

**It can't turn on HyperOS's own gesture navigation.** That's locked behind permissions only the system itself holds, and no ordinary app can get them (including via Shizuku, which still needs ADB or root to start).

The **Navigation bar** button opens the navigation-mode screen, where you can hide the three-button bar and reclaim that screen space. Choosing gesture navigation there still leaves a third-party launcher without working native gestures, so HyperGesture's own gestures remain the ones that function, either way.

If HyperOS resets your navigation mode after a reboot, the app will show that in Diagnostics, but it can't change it back for you.

The details are in [docs/hyperos-navigation.md](docs/hyperos-navigation.md).

## If something goes wrong

**Gestures stopped working, or Accessibility says "This service is malfunctioning."** Turn HyperGesture off and on again under Settings -> Accessibility. Android remembers that a service once failed and won't restart it on its own, even after an app update.

**The Accessibility switch does nothing.** You missed step 2 above. Allow restricted settings, then try again.

**Taps near the bottom of the screen feel slightly slow.** Known. The app has to briefly take each touch near an edge to see whether it becomes a swipe, then hand it back if it doesn't. Reducing the edge activation width in Gesture settings shrinks the area this affects.

**A gesture fires when you didn't mean it, or won't fire when you do.** Adjust the swipe distance for that edge in Gesture settings. The bottom edge is deliberately more sensitive than the sides, because a swipe starting on the navigation bar has already travelled some distance before the app sees it.

## Documentation

| Where | What it answers |
| --- | --- |
| `README.md` | What HyperGesture is, installing it, using it |
| [`docs/hyperos-navigation.md`](docs/hyperos-navigation.md) | What HyperOS does and doesn't permit about the navigation bar |
| [`docs/development.md`](docs/development.md) | Building, testing, releasing, debugging on a device |

## License

<!-- vale off -->

HyperGesture is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

HyperGesture is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with HyperGesture. If not, see https://www.gnu.org/licenses/.

Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
