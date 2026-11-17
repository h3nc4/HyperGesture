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

package com.h3nc4.hypergesture.setup

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.h3nc4.hypergesture.diagnostics.FailureLog
import com.h3nc4.hypergesture.diagnostics.isServiceListed
import com.h3nc4.hypergesture.navigation.AndroidSettingsAccess
import com.h3nc4.hypergesture.navigation.KeptSetting
import com.h3nc4.hypergesture.navigation.SettingsAccess
import com.h3nc4.hypergesture.navigation.SettingsTable
import com.h3nc4.hypergesture.service.HyperGestureAccessibilityService
import com.h3nc4.hypergesture.settings.resettingPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal enum class RestoreDecision { RESTORE, HOLD_OFF, NONE }

internal data class RestoreFacts(
    val optedIn: Boolean,
    val granted: Boolean,
    val switchedOn: Boolean,
    val running: Boolean,
    val serviceEverEnabled: Boolean,
    val keptOff: Boolean,
    val lastRestoreAtMs: Long?,
    val nowMs: Long,
)

/**
 * HyperOS and the user both switch the service off by writing the same secure setting, so
 * the restore is opt-in, and a removal soon after a restore is read as the user's.
 */
internal object ServiceRestoreRule {

    const val REPEAT_WINDOW_MS = 60_000L

    fun decide(facts: RestoreFacts): RestoreDecision = when {
        facts.running || facts.switchedOn -> RestoreDecision.NONE
        !facts.optedIn || !facts.granted || !facts.serviceEverEnabled || facts.keptOff -> RestoreDecision.NONE
        facts.lastRestoreAtMs != null && facts.nowMs - facts.lastRestoreAtMs in 0 until REPEAT_WINDOW_MS ->
            RestoreDecision.HOLD_OFF
        else -> RestoreDecision.RESTORE
    }

    /** Appends to the `:` separated list the way AOSP Settings writes it, and drops empty entries. */
    fun withService(enabledServices: String?, component: String): String =
        enabledServices.orEmpty().split(':').filter { it.isNotBlank() }.plus(component).joinToString(":")
}

private val Context.serviceRestoreDataStore: DataStore<Preferences> by
    resettingPreferencesDataStore("service_restore")

/** Off until the user turns it on, and only offered once the keeper's grant is held. */
internal class ServiceRestoreSettings(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.serviceRestoreDataStore)

    val enabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: false }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    suspend fun keptOff(): Boolean = store.data.first()[KEPT_OFF] ?: false

    /** Set on a removal the app left alone, cleared once the service runs again. */
    suspend fun setKeptOff(keptOff: Boolean) {
        store.edit { it[KEPT_OFF] = keptOff }
    }

    suspend fun lastRestoreAtMs(): Long? = store.data.first()[LAST_RESTORE_AT_MS]

    suspend fun setLastRestoreAtMs(atMs: Long) {
        store.edit { it[LAST_RESTORE_AT_MS] = atMs }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val KEPT_OFF = booleanPreferencesKey("kept_off")
        val LAST_RESTORE_AT_MS = longPreferencesKey("last_restore_at_ms")
    }
}

internal sealed interface RestoreOutcome {
    data object Untouched : RestoreOutcome
    data object HeldOff : RestoreOutcome
    data object Restored : RestoreOutcome
    data class Failed(val reason: String) : RestoreOutcome
}

/** Puts the service back in `enabled_accessibility_services` after something else took it out. */
internal class ServiceRestore(
    private val access: SettingsAccess,
    private val settings: ServiceRestoreSettings,
    private val packageName: String,
    private val className: String,
) {

    suspend fun afterStop(running: Boolean, serviceEverEnabled: Boolean, nowMs: Long): RestoreOutcome {
        val decision = ServiceRestoreRule.decide(
            RestoreFacts(
                optedIn = settings.enabled.first(),
                granted = access.granted,
                switchedOn = listed(),
                running = running,
                serviceEverEnabled = serviceEverEnabled,
                keptOff = settings.keptOff(),
                lastRestoreAtMs = settings.lastRestoreAtMs(),
                nowMs = nowMs,
            ),
        )
        return when (decision) {
            RestoreDecision.NONE -> RestoreOutcome.Untouched
            RestoreDecision.HOLD_OFF -> {
                settings.setKeptOff(true)
                RestoreOutcome.HeldOff
            }
            RestoreDecision.RESTORE -> {
                // Recorded first, so a write that rebinds and is removed again still counts as a repeat.
                settings.setLastRestoreAtMs(nowMs)
                write()
            }
        }
    }

    suspend fun serviceRunning() {
        if (settings.keptOff()) settings.setKeptOff(false)
    }

    private fun listed() = isServiceListed(access.read(services("")), packageName, className)

    private fun write(): RestoreOutcome {
        try {
            access.write(KeptSetting(SettingsTable.SECURE, Settings.Secure.ACCESSIBILITY_ENABLED, "1"))
            val current = access.read(services(""))
            access.write(services(ServiceRestoreRule.withService(current, "$packageName/$className")))
        } catch (e: SecurityException) {
            return RestoreOutcome.Failed(e.message ?: "permission refused")
        }
        return if (listed()) RestoreOutcome.Restored else RestoreOutcome.Failed("the system kept the service off")
    }

    // The value is ignored on a read.
    private fun services(value: String) =
        KeptSetting(SettingsTable.SECURE, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)

    companion object {
        private const val TAG = "HyperGesture"

        fun forDevice(context: Context) = ServiceRestore(
            AndroidSettingsAccess(context),
            ServiceRestoreSettings(context),
            context.packageName,
            HyperGestureAccessibilityService::class.java.name,
        )

        /** Each restore and each removal left alone goes to the failure record, since logcat drops info. */
        fun report(context: Context, outcome: RestoreOutcome) {
            val message = when (outcome) {
                RestoreOutcome.Untouched -> return
                RestoreOutcome.HeldOff ->
                    "SERVICE_KEPT_OFF removed again within a minute of a restore, so it stays off"
                RestoreOutcome.Restored ->
                    "SERVICE_RESTORED put back in enabled_accessibility_services after it was removed"
                is RestoreOutcome.Failed -> "SERVICE_NOT_RESTORED ${outcome.reason}"
            }
            Log.w(TAG, message)
            FailureLog.note(context, "service restore", message)
        }
    }
}
