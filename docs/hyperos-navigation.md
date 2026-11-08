# HyperOS navigation

The gesture service does not depend on any of this. Everything here concerns the optional second capability: getting HyperOS to hide its three-button navigation bar.

## Current behaviour

`navigation/HyperOSNavigationIntegration.kt` fires the vendor settings intent:

```kotlin
startActivity(
    Intent("com.android.settings.NAVIGATION_MODE_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)
```

Confirmed working on a Redmi 14 running Android 16 and HyperOS OS3.0. It opens the navigation-mode screen and the user taps the option. `adb shell am start` reaches that screen by the same `ActivityManager.startActivity` path, so no privileged permission is involved.

Choosing gesture navigation there hides the bar and gains screen space. It does not give a third-party launcher working native gestures, so HyperGesture's own recognizer remains the thing that performs navigation.

## Keeping the bar hidden

HyperOS shows the bar again after a reboot or a launcher change. The opt-in **Keep the bar hidden** switch on the setup screen puts it back, once the user has granted the app `WRITE_SECURE_SETTINGS` from a computer:

```bash
adb shell pm grant com.h3nc4.hypergesture android.permission.WRITE_SECURE_SETTINGS
```

On a Xiaomi phone that grant also needs the Mi-account "USB debugging (Security settings)" toggle. Shizuku's server runs as the same `adb shell` user, so a Shizuku route would need that toggle too, plus a helper app at runtime. The grant gives the same privilege with neither.

`navigation/NavigationKeeper.kt` writes two settings and reads them back:

| Table | Key | Value |
| --- | --- | --- |
| global | `force_fsg_nav_bar` | `1`, the MIUI full-screen gestures switch |
| secure | `navigation_mode` | `2`, gestural in AOSP |

It runs inside the accessibility service. It writes once when the service binds, which also covers boot, and again whenever a `ContentObserver` sees either setting change. It never polls. Turning the switch off unregisters the observer. Without the grant the screen shows the command in place of the switch.

The buttons come back whenever gestures stop, because a phone with neither gestures nor buttons has no way home. The service writes `0` to both keys as it unbinds, and a kill that skips the unbind is caught at the next boot or app resume, through a flag the keeper sets before it hides the bar.

The emulator suite `e2e/navigation-keeper.sh` covers the grant, the switch, a reset of each key, the buttons coming back on an unbind and on a kill, and a rebind. The emulator is not HyperOS. The suite sets the debug-only `hypergesture_integration` global setting to `hyperos` to reach the HyperOS integration. Whether these two keys alone hide the bar on HyperOS, or the three-button overlay also has to go, is still to be confirmed on the Redmi 14.

## TODO: apply the overlay directly

Opening a settings window is a poor substitute for doing the thing. The state to reach is this one:

```bash
cmd overlay enable com.android.internal.systemui.navbar.gestural
```

`cmd overlay list` shows the current state.

This is blocked because the call needs `android.permission.CHANGE_OVERLAY_PACKAGES`, which is signature-level, so only the platform signer can hold it. If a route ever appears that does not require a signature permission, replacing the settings intent with a direct overlay call is the change to make.

## Routes already closed

| Route | Why it fails |
| --- | --- |
| `cmd overlay enable ...` from the app | `CHANGE_OVERLAY_PACKAGES` is signature-level |
| Writing `Settings.Secure` `navigation_mode` without a grant | `WRITE_SECURE_SETTINGS` is signature, privileged or development, and only adb grants the last |
| Shizuku | Apache-2.0 and legally forkable, yet its privileged server still has to be started by ADB or root, and it runs as the same user the adb grant comes from |

Community consensus through 2025 and 2026 is that no ADB-free way exists to force native gestures for a third-party launcher. Lawnchair closed its own issue on this as not planned.

## Also unfinished

Auto-clicking the toggle once the settings screen opens, using the app's own accessibility service (`findAccessibilityNodeInfosByText` with `ACTION_CLICK`). That needs only public APIs and no extra permission, but it is fragile:

- labels are localised
- view ids change between builds
- HyperOS versions drift

Do it only as best-effort, with a manual fallback.

HyperOS also resets the navigation mode across a reboot. `boot/BootCompletedReceiver.kt` observes that and logs it. Only the keeper above restores it, and only after the grant.
