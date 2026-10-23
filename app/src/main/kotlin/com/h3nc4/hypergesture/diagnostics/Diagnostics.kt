// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.h3nc4.hypergesture.navigation.NavigationIntegrationFactory
import com.h3nc4.hypergesture.navigation.NavigationMode
import com.h3nc4.hypergesture.navigation.NavigationModeReader
import com.h3nc4.hypergesture.service.AppSwitcher
import com.h3nc4.hypergesture.service.GestureActionPerformer
import com.h3nc4.hypergesture.service.HyperGestureAccessibilityService

/** Everything here stays on the device: rendered in the UI, never transmitted. */
data class Diagnostics(
    val androidRelease: String?,
    val sdkInt: Int,
    val manufacturer: String?,
    val model: String?,
    val hyperOsVersion: String?,
    val miuiVersion: String?,
    val navigationMode: NavigationMode,
    /** Bound by the system, which drops a service HyperOS stopped. */
    val accessibilityServiceEnabled: Boolean,
    /** The switch in Accessibility, which stays on after HyperOS stops the service. */
    val accessibilityServiceSwitchedOn: Boolean,
    val accessibilityServiceRunning: Boolean,
    val navigationIntegrationId: String,
    val navigationIntegrationAvailable: Boolean,
    val lastGlobalActionFailure: String?,
    /** Usually the missing usage access, which no global action failure would explain. */
    val lastAppSwitchFailure: String?,
    /** Usually Google not being the default assistant. */
    val lastCircleToSearchFailure: String?,
    /** Shown in-app because release builds cannot be read with `adb run-as`. */
    val lastRecordedFailure: String?,
    val appVersionName: String?,
) {
    val serviceStatus: ServiceStatus
        get() = ServiceStatus.of(
            switchedOn = accessibilityServiceSwitchedOn,
            bound = accessibilityServiceEnabled,
            running = accessibilityServiceRunning,
        )
}

object DiagnosticsCollector {

    fun collect(context: Context): Diagnostics {
        val integration = NavigationIntegrationFactory.create(context)
        val status = integration.status()
        return Diagnostics(
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            hyperOsVersion = NavigationModeReader.hyperOsVersion(),
            miuiVersion = NavigationModeReader.miuiVersion(),
            navigationMode = status.currentMode,
            accessibilityServiceEnabled = isAccessibilityServiceEnabled(context),
            accessibilityServiceSwitchedOn = isAccessibilityServiceSwitchedOn(context),
            accessibilityServiceRunning = HyperGestureAccessibilityService.isRunning(),
            navigationIntegrationId = status.integrationId,
            navigationIntegrationAvailable = status.available,
            lastGlobalActionFailure = GestureActionPerformer.lastFailure,
            lastAppSwitchFailure = AppSwitcher.lastFailure,
            lastCircleToSearchFailure = GestureActionPerformer.lastCircleToSearchFailure,
            lastRecordedFailure = FailureLog.read(context),
            appVersionName = appVersionName(context),
        )
    }

    /** Bound services only, so a service HyperOS stopped reads false here. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
            ?: return false
        val target = HyperGestureAccessibilityService::class.java.name
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.name == target }
    }

    fun isAccessibilityServiceSwitchedOn(context: Context): Boolean = isServiceListed(
        runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull(),
        context.packageName,
        HyperGestureAccessibilityService::class.java.name,
    )

    private fun appVersionName(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
}
