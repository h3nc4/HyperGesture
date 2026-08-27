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

package com.h3nc4.hypergesture.diagnostics

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import com.h3nc4.hypergesture.navigation.NavigationIntegrationFactory
import com.h3nc4.hypergesture.navigation.NavigationMode
import com.h3nc4.hypergesture.navigation.NavigationModeReader
import com.h3nc4.hypergesture.service.GestureActionPerformer
import com.h3nc4.hypergesture.service.HyperGestureAccessibilityService

/** Everything here stays on the device: rendered in the UI, never transmitted. */
data class Diagnostics(
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val hyperOsVersion: String?,
    val miuiVersion: String?,
    val navigationMode: NavigationMode,
    val accessibilityServiceEnabled: Boolean,
    val accessibilityServiceRunning: Boolean,
    val navigationIntegrationId: String,
    val navigationIntegrationAvailable: Boolean,
    val lastGlobalActionFailure: String?,
    /** Shown in-app because release builds cannot be read with `adb run-as`. */
    val lastRecordedFailure: String?,
    val appVersionName: String,
)

object DiagnosticsCollector {

    fun collect(context: Context): Diagnostics {
        val integration = NavigationIntegrationFactory.create(context)
        val status = integration.status()
        return Diagnostics(
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            hyperOsVersion = NavigationModeReader.hyperOsVersion(),
            miuiVersion = NavigationModeReader.miuiVersion(),
            navigationMode = status.currentMode,
            accessibilityServiceEnabled = isAccessibilityServiceEnabled(context),
            accessibilityServiceRunning = HyperGestureAccessibilityService.isRunning(),
            navigationIntegrationId = status.integrationId,
            navigationIntegrationAvailable = status.available,
            lastGlobalActionFailure = GestureActionPerformer.lastFailure,
            lastRecordedFailure = FailureLog.read(context),
            appVersionName = appVersionName(context),
        )
    }

    /** Avoids parsing `ENABLED_ACCESSIBILITY_SERVICES`, whose separator format is undocumented. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
            ?: return false
        val target = HyperGestureAccessibilityService::class.java.name
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.name == target }
    }

    private fun appVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")
}
