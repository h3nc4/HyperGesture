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

## TODO: apply the overlay directly

Opening a settings window is a poor substitute for doing the thing. The state to reach is this one:

```bash
cmd overlay enable com.android.internal.systemui.navbar.gestural
```

`cmd overlay list` shows the current state.

This is blocked because the call needs `android.permission.CHANGE_OVERLAY_PACKAGES`, which is signature-level, so only the platform signer can hold it. If a route ever appears that does not require a signature permission, replacing the settings intent with a direct overlay call is the change to make.

## Shizuku recovery

HyperGesture now has an explicit, off-by-default Shizuku option. When enabled, it checks and repairs only `Settings.Global force_fsg_nav_bar=1` and `Settings.Secure navigation_mode=2`, then reads both values back. It runs shell commands directly instead of a Shizuku UserService, which avoids a UserService startup crash on some MediaTek builds. The UI reports whether Shizuku is unavailable, permission is missing, both values were verified, or a write/readback failed. It checks on app resume, Shizuku binder availability, boot, package/preferred-home broadcasts when delivered, real-time ContentObserver settings monitoring, and an inexact 15-minute WorkManager fallback.

Shizuku does not remove the setup requirement: on non-rooted devices it must be started again after each reboot. The check reports failure without claiming support when a build rejects writes. In particular, the upstream investigation reported writes unavailable on a Redmi 14; compatibility must be established per device/build. Disabling recovery cancels scheduled work and does not restore or otherwise change the current values. The manual navigation settings shortcut remains available.

This only maintains the navigation bar settings. It does **not** enable Xiaomi's native gesture engine, make native gestures work under third-party launchers, or touch overlays or accessibility configuration.

## Routes already closed

| Route | Why it fails |
| --- | --- |
| `cmd overlay enable ...` from the app | `CHANGE_OVERLAY_PACKAGES` is signature-level |
| Writing `Settings.Secure` `navigation_mode` | `WRITE_SECURE_SETTINGS` is signature or privileged. Reading it is unrestricted, so the app can report the mode but never change it |
| Shizuku as an ADB-free privilege source | Its privileged server still has to be started by ADB, wireless debugging, or root; the optional recovery above only uses Shizuku when it is already available |
| Granting the permission over adb | On the target device `adb shell` holds neither `WRITE_SECURE_SETTINGS` nor `GRANT_RUNTIME_PERMISSIONS`. Xiaomi keeps both behind the Mi-account "USB debugging (Security settings)" toggle |

Community consensus through 2025 and 2026 is that no ADB-free way exists to force native gestures for a third-party launcher. Lawnchair closed its own issue on this as not planned.

## Also unfinished

Auto-clicking the toggle once the settings screen opens, using the app's own accessibility service (`findAccessibilityNodeInfosByText` with `ACTION_CLICK`). That needs only public APIs and no extra permission, but it is fragile:

- labels are localised
- view ids change between builds
- HyperOS versions drift

Worth doing only as best-effort, with a manual fallback.

HyperOS also resets the navigation mode across a reboot. `boot/BootCompletedReceiver.kt` observes that and reports it. A normal APK cannot restore it.
