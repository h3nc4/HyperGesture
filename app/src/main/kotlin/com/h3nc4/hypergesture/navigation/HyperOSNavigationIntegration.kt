// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.h3nc4.hypergesture.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Opens the HyperOS navigation-mode screen, unreachable through Settings UI under a
 * third-party launcher. `adb shell am start` reaches it via the same
 * `ActivityManager.startActivity` path an app uses, so no privilege is implied.
 *
 * Succeeding only hides the navigation bar; it did **not** restore native HyperOS
 * gestures on the owner's device, so HyperGesture's own recognizer still does the real
 * work. See docs/hyperos-navigation.md.
 */
class HyperOSNavigationIntegration(private val context: Context) : NavigationIntegration {

    override val id: String = ID

    override fun isSupported(): Boolean = NavigationModeReader.isHyperOsDevice()

    override fun status(): NavigationIntegrationStatus {
        val version = NavigationModeReader.hyperOsVersion()
            ?: NavigationModeReader.miuiVersion()
        return NavigationIntegrationStatus(
            integrationId = id,
            available = isSupported(),
            currentMode = NavigationModeReader.read(context),
            detail = if (isSupported()) {
                "HyperOS detected (${version ?: "unknown build"}). The navigation-mode " +
                    "screen can be opened, but enabling gesture navigation is not " +
                    "confirmed to restore native gestures under a third-party launcher."
            } else {
                "Not a HyperOS device."
            },
        )
    }

    override fun requestEnable(): EnableRequestOutcome {
        if (!isSupported()) return EnableRequestOutcome.Unsupported

        val intent = Intent(NAVIGATION_MODE_SETTINGS_ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            EnableRequestOutcome.LaunchedSettings(NAVIGATION_MODE_SETTINGS_ACTION)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Navigation mode settings activity not found", e)
            EnableRequestOutcome.Failed("This HyperOS build has no navigation-mode settings screen.")
        } catch (e: SecurityException) {
            Log.w(TAG, "Navigation mode settings activity refused the launch", e)
            EnableRequestOutcome.Failed("HyperOS refused to open the navigation-mode screen.")
        }
    }

    companion object {
        const val ID = "hyperos"

        /** Vendor-specific action, not a public `Settings.ACTION_*` constant. */
        const val NAVIGATION_MODE_SETTINGS_ACTION = "com.android.settings.NAVIGATION_MODE_SETTINGS"

        private const val TAG = "HyperGesture"
    }
}
