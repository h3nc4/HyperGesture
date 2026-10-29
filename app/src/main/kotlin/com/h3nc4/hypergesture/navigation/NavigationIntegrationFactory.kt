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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings

object NavigationIntegrationFactory {

    fun create(context: Context): NavigationIntegration = when {
        NavigationModeReader.isHyperOsDevice() -> HyperOSNavigationIntegration(context)
        debugHyperOs(context) -> HyperOSNavigationIntegration(context, supported = true)
        else -> GenericAndroidNavigationIntegration(context)
    }

    /** Test seam that lets the emulator suite reach the HyperOS integration. Debug only. */
    private fun debugHyperOs(context: Context): Boolean {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return false
        return runCatching {
            Settings.Global.getString(context.contentResolver, INTEGRATION_OVERRIDE) ==
                HyperOSNavigationIntegration.ID
        }.getOrDefault(false)
    }

    private const val INTEGRATION_OVERRIDE = "hypergesture_integration"
}
