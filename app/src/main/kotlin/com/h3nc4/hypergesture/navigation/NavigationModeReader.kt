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
import android.os.Build
import android.provider.Settings

/**
 * Reading `Settings.Secure` needs no permission; *writing* it needs `WRITE_SECURE_SETTINGS`
 * (adb or root only) — which is why this app can report the mode but never change it.
 */
internal object NavigationModeReader {

    private const val NAVIGATION_MODE = "navigation_mode"

    /** Platform values: 0 three-button, 1 two-button, 2 gestural. */
    fun read(context: Context): NavigationMode = runCatching {
        when (Settings.Secure.getInt(context.contentResolver, NAVIGATION_MODE)) {
            2 -> NavigationMode.GESTURAL
            0, 1 -> NavigationMode.THREE_BUTTON
            else -> NavigationMode.UNKNOWN
        }
    }.getOrDefault(NavigationMode.UNKNOWN)

    fun isHyperOsDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val vendorKnown = manufacturer == "xiaomi" || manufacturer == "redmi" ||
            manufacturer == "poco"
        return vendorKnown && (hyperOsVersion() != null || miuiVersion() != null)
    }

    fun hyperOsVersion(): String? = systemProperty("ro.mi.os.version.name")

    /** Kept for older builds that predate the HyperOS property. */
    fun miuiVersion(): String? = systemProperty("ro.miui.ui.version.name")

    /** Hidden API: every failure degrades to null, never a crash on a non-Xiaomi device. */
    private fun systemProperty(key: String): String? = runCatching {
        @Suppress("PrivateApi")
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
