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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

package com.h3nc4.hypergesture.navigation

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

private val Context.navigationRecoveryStore by preferencesDataStore("navigation_recovery")

enum class RecoveryStatus {
    DISABLED, SHIZUKU_UNAVAILABLE, PERMISSION_DENIED, VERIFIED, WRITE_FAILED
}

data class RecoveryState(
    val enabled: Boolean = false,
    val status: RecoveryStatus = RecoveryStatus.DISABLED,
)

class NavigationRecoveryRepository(private val context: Context) {
    val state: Flow<RecoveryState> = context.navigationRecoveryStore.data.map { preferences ->
        RecoveryState(
            enabled = preferences[ENABLED] ?: false,
            status = preferences[STATUS]?.let { name ->
                RecoveryStatus.entries.firstOrNull { it.name == name }
            } ?: RecoveryStatus.DISABLED,
        )
    }

    suspend fun isEnabled() = state.first().enabled

    suspend fun setEnabled(enabled: Boolean) {
        context.navigationRecoveryStore.edit { it[ENABLED] = enabled }
        if (enabled) {
            NavigationRecoveryScheduler.schedulePeriodic(context)
            NavigationRecoveryScheduler.runDirectIfEnabled(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(NavigationRecoveryScheduler.PERIODIC_WORK)
            setStatus(RecoveryStatus.DISABLED)
        }
    }

    suspend fun setStatus(status: RecoveryStatus) {
        context.navigationRecoveryStore.edit { it[STATUS] = status.name }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val STATUS = stringPreferencesKey("status")
    }
}

object NavigationRecoveryScheduler {
    const val PERIODIC_WORK = "navigation-recovery-periodic"
    private val executionMutex = Mutex()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<NavigationRecoveryWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Executes Shizuku recovery directly without waiting for asynchronous WorkManager queues.
     * Guarded by a Mutex to prevent overlapping shell executions.
     */
    suspend fun runDirectIfEnabled(context: Context): RecoveryStatus? {
        val repository = NavigationRecoveryRepository(context)
        if (!repository.isEnabled()) return null
        return executionMutex.withLock {
            if (!repository.isEnabled()) return@withLock null
            val status = ShizukuNavigationRecovery.run(context)
            repository.setStatus(status)
            status
        }
    }
}

/**
 * Monitors navigation settings in real time via ContentObserver.
 * Immediately triggers direct recovery when HyperOS or SystemUI resets force_fsg_nav_bar or navigation_mode.
 */
class NavigationSettingsObserver(
    private val context: Context,
    handler: Handler? = null,
) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        val cr = context.contentResolver
        val fsg = Settings.Global.getString(cr, "force_fsg_nav_bar")
        val nav = Settings.Secure.getString(cr, "navigation_mode")
        if (fsg != "1" || nav != "2") {
            CoroutineScope(Dispatchers.IO).launch {
                NavigationRecoveryScheduler.runDirectIfEnabled(context)
            }
        }
    }

    fun register() {
        runCatching {
            val cr = context.contentResolver
            cr.registerContentObserver(
                Settings.Global.getUriFor("force_fsg_nav_bar"),
                false,
                this,
            )
            cr.registerContentObserver(
                Settings.Secure.getUriFor("navigation_mode"),
                false,
                this,
            )
        }
    }

    fun unregister() {
        runCatching {
            context.contentResolver.unregisterContentObserver(this)
        }
    }
}

/** Fixed allow-list: recovery targets the settings required for full-screen gestures. */
internal object NavigationSettingTargets {
    val all = listOf(
        SettingTarget("global", "force_fsg_nav_bar", "1"),
        SettingTarget("secure", "navigation_mode", "2"),
    )

    fun verified(values: Map<String, String>) = all.all { values[it.key] == it.value }
}

internal data class SettingTarget(val namespace: String, val key: String, val value: String)

internal data class ParsedRecoveryOutput(
    val fsg: String?,
    val nav: String?,
) {
    val isVerified: Boolean get() = fsg == "1" && nav == "2"
}

internal object ShizukuShell {
    private var newProcessMethod: Method? = null

    fun exec(command: String, timeoutSeconds: Long = 8): CommandResult {
        if (!Shizuku.pingBinder()) {
            return CommandResult(exitCode = -1, output = "Shizuku binder not responding")
        }
        val method = newProcessMethod ?: synchronized(this) {
            newProcessMethod ?: Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }.also { newProcessMethod = it }
        }

        val cmd = arrayOf("sh", "-c", command)
        val process = method.invoke(null, cmd, null, null) as Process
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return CommandResult(exitCode = -1, output = "Execution timed out")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() } +
            process.errorStream.bufferedReader().use { it.readText() }
        return CommandResult(exitCode = process.exitValue(), output = output.trim())
    }
}

internal data class CommandResult(val exitCode: Int, val output: String)

object ShizukuNavigationRecovery {

    fun buildRecoveryScript(packageName: String): String = """
        settings put global force_fsg_nav_bar 1
        settings put secure navigation_mode 2
        settings --user 0 put secure navigation_mode 2 2>/dev/null || true
        settings --user 0 put global force_fsg_nav_bar 1 2>/dev/null || true
        settings put global use_gesture_version_three 1 2>/dev/null || true
        if cmd overlay list 2>/dev/null | grep -q '\[x\] com.android.internal.systemui.navbar.threebutton'; then
            cmd overlay disable com.android.internal.systemui.navbar.threebutton 2>/dev/null || true
        fi
        appops set $packageName 10008 allow 2>/dev/null || true
        appops set $packageName 10021 allow 2>/dev/null || true
        appops set $packageName android:get_usage_stats allow 2>/dev/null || true
        dumpsys deviceidle whitelist +$packageName 2>/dev/null || true
        am set-standby-bucket $packageName active 2>/dev/null || true
        echo "FSG=$(settings get global force_fsg_nav_bar 2>/dev/null)"
        echo "NAV=$(settings get secure navigation_mode 2>/dev/null)"
    """.trimIndent()

    suspend fun run(context: Context): RecoveryStatus {
        if (!Shizuku.pingBinder()) return RecoveryStatus.SHIZUKU_UNAVAILABLE
        try {
            if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return RecoveryStatus.PERMISSION_DENIED
            }
        } catch (_: Exception) {
            return RecoveryStatus.SHIZUKU_UNAVAILABLE
        }

        return try {
            withContext(Dispatchers.IO) {
                val script = buildRecoveryScript(context.packageName)
                val result = ShizukuShell.exec(script)
                if (result.exitCode != 0) {
                    RecoveryStatus.WRITE_FAILED
                } else {
                    val parsed = parseOutput(result.output)
                    if (parsed.isVerified) RecoveryStatus.VERIFIED else RecoveryStatus.WRITE_FAILED
                }
            }
        } catch (error: CancellationException) {
            if (!currentCoroutineContext().isActive) throw error
            if (!Shizuku.pingBinder()) RecoveryStatus.SHIZUKU_UNAVAILABLE else RecoveryStatus.WRITE_FAILED
        } catch (_: Exception) {
            if (!Shizuku.pingBinder()) RecoveryStatus.SHIZUKU_UNAVAILABLE else RecoveryStatus.WRITE_FAILED
        }
    }

    internal fun parseOutput(output: String): ParsedRecoveryOutput {
        var fsg: String? = null
        var nav: String? = null
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("FSG=")) {
                fsg = trimmed.substringAfter("FSG=").trim()
            } else if (trimmed.startsWith("NAV=")) {
                nav = trimmed.substringAfter("NAV=").trim()
            }
        }
        return ParsedRecoveryOutput(fsg, nav)
    }
}
