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

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.h3nc4.hypergesture.navigation.AndroidSettingsAccess
import com.h3nc4.hypergesture.navigation.NavigationKeeper
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Why the service stopped and each change to the navigation setting, newest last. External storage, so
// `adb shell cat /sdcard/Android/data/com.h3nc4.hypergesture/files/stop_log.txt` reads a release build.
object StopLog {

    const val SEEN_LIVE = "seen live"
    const val SEEN_AT_START = "seen as the service started"
    const val SEEN_AT_BOOT = "seen at boot"
    const val WRITTEN_BY_APP = "written by HyperGesture"

    private const val TAG = "HyperGesture"
    private const val FILE_NAME = "stop_log.txt"
    private const val PREFS = "stop_log"
    private const val MAX_LINES = 200
    private const val SHOWN_LINES = 20
    private const val RUNNING_SINCE = "running_since_ms"
    private const val RUNNING_PID = "running_pid"
    private const val VERSION = "version"
    private const val NAVIGATION_SEEN = "navigation_seen"
    private const val NAVIGATION_PREFIX = "navigation:"

    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    /** Written with `commit`, since a kill straight after the bind must still find the marker. */
    @Synchronized
    fun serviceStarted(context: Context) = guarded {
        recordPreviousStop(context)
        val prefs = prefs(context)
        val edit = prefs.edit()
            .putLong(RUNNING_SINCE, System.currentTimeMillis())
            .putInt(RUNNING_PID, Process.myPid())
        // Left to the update broadcast after the first start, so it still holds the version an update replaced.
        if (!prefs.contains(VERSION)) edit.putString(VERSION, version(context))
        edit.commit()
    }

    /** The setting is already written when the unbind arrives. A missing entry means a switch-off. */
    @Synchronized
    fun serviceUnbound(context: Context) = guarded {
        if (!DiagnosticsCollector.isAccessibilityServiceSwitchedOn(context)) {
            append(context, StopReason.SWITCHED_OFF_LINE)
        }
        if (prefs(context).getInt(RUNNING_PID, 0) == Process.myPid()) clearMarker(context)
    }

    @Synchronized
    fun connectionLost(context: Context) = guarded { append(context, StopReason.CONNECTION_LOST_LINE) }

    /** Called wherever a new process may start, because the process that ended ran nothing to say why. */
    @Synchronized
    fun recordPreviousStop(context: Context) = guarded {
        val prefs = prefs(context)
        if (!prefs.contains(RUNNING_SINCE)) return@guarded
        val facts = StopFacts(
            runningSinceMs = prefs.getLong(RUNNING_SINCE, 0),
            runningPid = prefs.getInt(RUNNING_PID, 0),
            myPid = Process.myPid(),
            lastUpdateMs = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime,
            bootMs = System.currentTimeMillis() - SystemClock.elapsedRealtime(),
            exits = exits(context),
        )
        if (facts.runningPid == facts.myPid) return@guarded
        StopReason.previousStop(facts)?.let { append(context, it) }
        clearMarker(context)
    }

    /** `ACTION_MY_PACKAGE_REPLACED` lands in the new version, which compares itself with the one that ran. */
    @Synchronized
    fun appUpdated(context: Context) = guarded {
        val prefs = prefs(context)
        if (DiagnosticsCollector.isAccessibilityServiceSwitchedOn(context)) {
            append(context, StopReason.update(prefs.getString(VERSION, null), version(context)))
        }
        // The service may already have bound in this process, and its marker stays.
        if (prefs.getInt(RUNNING_PID, 0) != Process.myPid()) clearMarker(context)
        prefs.edit().putString(VERSION, version(context)).commit()
    }

    /** Compares the navigation setting with the last values seen, which the first call only remembers. */
    @Synchronized
    fun recordNavigation(context: Context, cause: String) = guarded {
        val prefs = prefs(context)
        val access = AndroidSettingsAccess(context)
        val now = NavigationKeeper.KEPT.associate { it.key to access.read(it) }
        if (prefs.getBoolean(NAVIGATION_SEEN, false)) {
            val before = now.keys.associateWith { prefs.getString(NAVIGATION_PREFIX + it, null) }
            StopReason.navigationChanges(before, now, cause).forEach { append(context, it) }
        }
        val edit = prefs.edit().putBoolean(NAVIGATION_SEEN, true)
        now.forEach { (key, value) -> edit.putString(NAVIGATION_PREFIX + key, value) }
        edit.commit()
    }

    fun latest(context: Context): String? = runCatching {
        val file = file(context)
        if (!file.exists()) return null
        file.readLines().filter { it.isNotBlank() }.takeLast(SHOWN_LINES).joinToString("\n")
    }.getOrNull()?.ifBlank { null }

    private fun append(context: Context, entry: String) {
        Log.w(TAG, entry)
        val file = file(context)
        val existing = if (file.exists()) file.readText() else ""
        file.writeText(StopReason.appended(existing, "${format.format(Instant.now())} $entry", MAX_LINES))
    }

    private fun clearMarker(context: Context) {
        prefs(context).edit().remove(RUNNING_SINCE).remove(RUNNING_PID).commit()
    }

    private fun exits(context: Context): List<PriorExit> =
        context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(null, 0, 0)
            .orEmpty()
            .map { PriorExit(it.pid, it.reason, it.timestamp, it.description) }

    private fun version(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${info.versionName} (${info.longVersionCode})"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun file(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

    // A log that throws would take the service down with it.
    private inline fun guarded(block: () -> Unit) {
        runCatching(block).onFailure { Log.e(TAG, "Could not update the stop log", it) }
    }
}
