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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

enum class SettingsTable { GLOBAL, SECURE }

data class KeptSetting(val table: SettingsTable, val key: String, val value: String)

/** The system settings, behind a seam so the keeper is tested without a device. */
interface SettingsAccess {
    val granted: Boolean
    fun read(setting: KeptSetting): String?

    /** Throws [SecurityException] once the grant is revoked. */
    fun write(setting: KeptSetting)
}

sealed interface KeepOutcome {
    data object AlreadyKept : KeepOutcome
    data object NotGranted : KeepOutcome
    data class Restored(val keys: List<String>) : KeepOutcome
    data class Failed(val reason: String) : KeepOutcome
}

/** Puts back the settings that hide the bar, which HyperOS resets on a launcher change or a reboot. */
class NavigationKeeper(private val access: SettingsAccess) {

    val granted: Boolean get() = access.granted

    fun keep(): KeepOutcome {
        val drifted = KEPT.filter { access.read(it) != it.value }
        if (drifted.isEmpty()) return KeepOutcome.AlreadyKept
        if (!access.granted) return KeepOutcome.NotGranted
        try {
            drifted.forEach(access::write)
        } catch (e: SecurityException) {
            return KeepOutcome.Failed(e.message ?: "permission refused")
        }
        val refused = drifted.filter { access.read(it) != it.value }
        return if (refused.isEmpty()) {
            KeepOutcome.Restored(drifted.map { it.key })
        } else {
            KeepOutcome.Failed("the system kept ${refused.joinToString { it.key }}")
        }
    }

    companion object {
        /** `force_fsg_nav_bar` is the MIUI switch for full-screen gestures, `navigation_mode` the AOSP one. */
        val KEPT = listOf(
            KeptSetting(SettingsTable.GLOBAL, "force_fsg_nav_bar", "1"),
            KeptSetting(SettingsTable.SECURE, "navigation_mode", "2"),
        )

        fun forDevice(context: Context) = NavigationKeeper(AndroidSettingsAccess(context))
    }
}

internal class AndroidSettingsAccess(private val context: Context) : SettingsAccess {

    override val granted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    override fun read(setting: KeptSetting): String? = runCatching {
        when (setting.table) {
            SettingsTable.GLOBAL -> Settings.Global.getString(context.contentResolver, setting.key)
            SettingsTable.SECURE -> Settings.Secure.getString(context.contentResolver, setting.key)
        }
    }.getOrNull()

    override fun write(setting: KeptSetting) {
        when (setting.table) {
            SettingsTable.GLOBAL -> Settings.Global.putString(context.contentResolver, setting.key, setting.value)
            SettingsTable.SECURE -> Settings.Secure.putString(context.contentResolver, setting.key, setting.value)
        }
    }

    companion object {
        fun uriOf(setting: KeptSetting): Uri = when (setting.table) {
            SettingsTable.GLOBAL -> Settings.Global.getUriFor(setting.key)
            SettingsTable.SECURE -> Settings.Secure.getUriFor(setting.key)
        }
    }
}
