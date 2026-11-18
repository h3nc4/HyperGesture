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

import android.app.ApplicationExitInfo

/** One process exit as `ApplicationExitInfo` reports it. */
internal data class PriorExit(val pid: Int, val reason: Int, val timestampMs: Long, val description: String?)

/** The marker the service left while it ran, read in a later process. */
internal data class StopFacts(
    /** Null once the service unbound cleanly, or when it never ran. */
    val runningSinceMs: Long?,
    val runningPid: Int?,
    val myPid: Int,
    val lastUpdateMs: Long,
    val bootMs: Long,
    val exits: List<PriorExit>,
)

/** The lines of the stop log, built without a device so each cause is tested on its own. */
internal object StopReason {

    const val UPDATE = "STOPPED_BY_UPDATE"
    const val FORCE_STOP = "STOPPED_BY_FORCE_STOP"
    const val KILL = "STOPPED_BY_KILL"
    const val REBOOT = "STOPPED_BY_REBOOT"
    const val SWITCHED_OFF = "STOPPED_SWITCHED_OFF"
    const val NAVIGATION = "NAVIGATION_CHANGED"

    const val SWITCHED_OFF_LINE = "$SWITCHED_OFF removed from the enabled services by the user or the system"
    const val CONNECTION_LOST = "STOPPED_CONNECTION_LOST"
    const val CONNECTION_LOST_LINE = "$CONNECTION_LOST the system reset the window token without an unbind"

    // Why the service's process ended without an unbind. Null while it still runs, and for an update,
    // which its own broadcast records.
    fun previousStop(facts: StopFacts): String? {
        val since = facts.runningSinceMs ?: return null
        if (facts.runningPid == facts.myPid) return null
        if (facts.lastUpdateMs > since) return null
        val exit = facts.exits
            .filter { it.pid == facts.runningPid && it.timestampMs >= since }
            .maxByOrNull { it.timestampMs }
        return when {
            exit == null && since < facts.bootMs -> "$REBOOT while the service ran"
            exit == null -> "$KILL with no exit reason recorded"
            exit.reason == ApplicationExitInfo.REASON_USER_REQUESTED -> FORCE_STOP + detail(exit)
            else -> "$KILL ${reasonName(exit.reason)}${detail(exit)}"
        }
    }

    fun update(versionThen: String?, versionNow: String) = "$UPDATE ${versionThen ?: "unknown"} -> $versionNow"

    /** A key missing from [before] has no earlier value to compare, so it is only remembered. */
    fun navigationChanges(before: Map<String, String?>, after: Map<String, String?>, cause: String): List<String> =
        after.filter { (key, value) -> key in before && before[key] != value }
            .map { (key, value) -> "$NAVIGATION $key ${before[key] ?: "unset"} -> ${value ?: "unset"}, $cause" }

    /** The newest [maxLines] lines, with [line] last. */
    fun appended(existing: String, line: String, maxLines: Int): String =
        (existing.lines().filter { it.isNotBlank() } + line).takeLast(maxLines).joinToString("\n", postfix = "\n")

    private fun detail(exit: PriorExit) = exit.description?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_SIGNALED -> "signal"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission change"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited itself"
        else -> "reason $reason"
    }
}
