# HyperGesture

> Edge gestures for Xiaomi HyperOS, so you can keep the launcher you actually like.

HyperOS ties its gesture navigation to the stock launcher. Switch to Nova, Niagara or Lawnchair and the phone drops you back to three buttons. HyperGesture brings the gestures back with one app, without root, ADB, Shizuku or a computer. It works on any phone with Android 11 or newer.

**[Download the latest release](https://github.com/h3nc4/HyperGesture/releases/latest)** · [hypergesture.h3nc4.com](https://hypergesture.h3nc4.com), the install guide in English and Português

## Gestures

| Gesture | Does |
| --- | --- |
| Swipe in from the left or right edge | Back |
| Swipe up from the bottom edge | Home |
| Swipe up from the bottom and hold | Recent apps |
| Swipe right or left along the bottom edge | Previous or next app, once app switching is on |
| Touch and hold the middle of the bottom edge | Circle to Search, once it is on |

In landscape the first bottom swipe only shows a pill, and a second one within 2 seconds goes Home, as HyperOS does in games. Every distance and delay is adjustable in the app's Gesture settings.

## Install

1. Install `hypergesture.apk` from [the latest release](https://github.com/h3nc4/HyperGesture/releases/latest).
2. Allow restricted settings under Settings › Apps › HyperGesture › ⋮ › **Allow restricted settings**.
3. Turn it on under Settings › Accessibility › **HyperGesture**, then open the app. Its setup checklist covers the rest.

If Play Protect blocks the install, the site's [install steps](https://hypergesture.h3nc4.com/#install) explain it. If the gestures stop, the app's Troubleshooting screen has the fixes.

## Privacy

Gestures need an accessibility service. This one watches thin strips along the screen edges and turns a swipe into Back, Home, Recents or an app switch. Its configuration withholds window content and accessibility events, so it never reads the screen or what you type. The app goes online only for an update check that stays off until you turn it on, and then asks GitHub for the latest release. It has no ads, analytics or account.

## What it can't do

It can't switch on HyperOS's own gesture navigation, since only the system is allowed to do that. It can open the navigation settings so you can hide the three-button bar. If HyperOS resets your navigation mode after a reboot, the optional Shizuku recovery can restore its two settings when enabled and Shizuku is running with permission. [docs/hyperos-navigation.md](docs/hyperos-navigation.md) has the details, and [docs/development.md](docs/development.md) covers working on the code.

## Navigation setting recovery

This is an opt-in recovery for `force_fsg_nav_bar=1` and `navigation_mode=2`. It uses Shizuku's shell/root identity to write those two settings and reads both back. It runs shell commands directly instead of a Shizuku UserService, which avoids a UserService startup crash on some MediaTek builds. It does not change overlays, accessibility settings, or any other values, and it does not turn on HyperOS's native gesture engine. HyperGesture's own gestures remain responsible for Back, Home and Recents.

Install and start [Shizuku](https://shizuku.rikka.app/download/), then grant HyperGesture Shizuku permission and turn on **Navigation setting recovery (Shizuku)**. It checks when HyperGesture resumes, when Shizuku becomes available, after boot, after package or preferred-home changes when Android delivers those broadcasts, through real-time ContentObserver settings monitoring, and an inexact 15-minute WorkManager fallback. The existing **Open navigation settings** action remains available as a manual route.

On a non-rooted device, Shizuku must be started again after every reboot. HyperGesture reports when Shizuku is unavailable, permission is missing, or a write/readback fails. Setting writes are device/build dependent: the upstream navigation investigation reports that Shizuku writes were unavailable on a Redmi 14, so a verified result on one POCO or HyperOS build does not establish compatibility elsewhere. Turning recovery off cancels scheduled checks and leaves the current settings unchanged.

## Contributors

- Henrique Almeida ([@h3nc4](https://github.com/h3nc4)), author and maintainer
- Victor Eduardo ([@victoresidoro](https://github.com/victoresidoro)), Circle to Search, the Control Center tile, edge to edge and wrapping labels

Circle to Search starts the way [MiCTS](https://github.com/parallelcc/MiCTS) and [CTSLauncher](https://github.com/chenxiaolong/CTSLauncher) do. Everyone who has sent code is on [the contributors page](https://github.com/h3nc4/HyperGesture/graphs/contributors).

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). Copyright (C) 2026 Henrique Almeida and the contributors above.
