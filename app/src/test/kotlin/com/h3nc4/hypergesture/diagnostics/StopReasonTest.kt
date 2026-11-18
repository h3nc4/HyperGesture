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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StopReasonTest {

    private val since = 1_000_000L

    private fun facts(
        runningSinceMs: Long? = since,
        runningPid: Int? = 41,
        lastUpdateMs: Long = since - 1,
        bootMs: Long = since - 10_000,
        exits: List<PriorExit> = emptyList(),
    ) = StopFacts(runningSinceMs, runningPid, myPid = 42, lastUpdateMs, bootMs, exits)

    @Test
    fun `a force-stop is named apart from any other kill`() {
        val exit = PriorExit(41, ApplicationExitInfo.REASON_USER_REQUESTED, since + 5, "stop com.h3nc4.hypergesture")
        assertEquals(
            "STOPPED_BY_FORCE_STOP (stop com.h3nc4.hypergesture)",
            StopReason.previousStop(facts(exits = listOf(exit))),
        )
    }

    @Test
    fun `any other kill carries its reason`() {
        val exit = PriorExit(41, ApplicationExitInfo.REASON_LOW_MEMORY, since + 5, null)
        assertEquals("STOPPED_BY_KILL low memory", StopReason.previousStop(facts(exits = listOf(exit))))
    }

    @Test
    fun `only the exit of the process that ran the service counts`() {
        val other = PriorExit(7, ApplicationExitInfo.REASON_USER_REQUESTED, since + 5, null)
        val older = PriorExit(41, ApplicationExitInfo.REASON_USER_REQUESTED, since - 5, null)
        val own = PriorExit(41, ApplicationExitInfo.REASON_CRASH, since + 9, "")
        assertEquals("STOPPED_BY_KILL crash", StopReason.previousStop(facts(exits = listOf(other, older, own))))
    }

    @Test
    fun `an update is left to the package-replaced broadcast`() {
        val exit = PriorExit(41, ApplicationExitInfo.REASON_OTHER, since + 5, "replace package")
        assertNull(StopReason.previousStop(facts(lastUpdateMs = since + 4, exits = listOf(exit))))
    }

    @Test
    fun `a service that ran before the boot with no exit stopped for the reboot`() {
        assertEquals("STOPPED_BY_REBOOT while the service ran", StopReason.previousStop(facts(bootMs = since + 1)))
    }

    @Test
    fun `a death with no exit record still leaves an entry`() {
        assertEquals("STOPPED_BY_KILL with no exit reason recorded", StopReason.previousStop(facts()))
    }

    @Test
    fun `a clean unbind and the live process leave nothing`() {
        assertNull(StopReason.previousStop(facts(runningSinceMs = null)))
        assertNull(StopReason.previousStop(facts(runningPid = 42)))
    }

    @Test
    fun `an update names both versions`() {
        assertEquals("STOPPED_BY_UPDATE 1.0.0 (9) -> 1.0.1 (10)", StopReason.update("1.0.0 (9)", "1.0.1 (10)"))
        assertEquals("STOPPED_BY_UPDATE unknown -> 1.0.1 (10)", StopReason.update(null, "1.0.1 (10)"))
    }

    @Test
    fun `each navigation key that moved gets its own entry with both values`() {
        val before = mapOf("force_fsg_nav_bar" to "1", "navigation_mode" to "2")
        val after = mapOf("force_fsg_nav_bar" to null, "navigation_mode" to "0")
        assertEquals(
            listOf(
                "NAVIGATION_CHANGED force_fsg_nav_bar 1 -> unset, seen at boot",
                "NAVIGATION_CHANGED navigation_mode 2 -> 0, seen at boot",
            ),
            StopReason.navigationChanges(before, after, "seen at boot"),
        )
    }

    @Test
    fun `unchanged or never seen keys leave nothing`() {
        val same = mapOf("navigation_mode" to "2")
        assertEquals(emptyList<String>(), StopReason.navigationChanges(same, same, "seen live"))
        assertEquals(emptyList<String>(), StopReason.navigationChanges(emptyMap(), same, "seen live"))
    }

    @Test
    fun `the log keeps the newest lines`() {
        assertEquals("b\nc\n", StopReason.appended("a\nb\n", "c", maxLines = 2))
        assertEquals("a\n", StopReason.appended("", "a", maxLines = 2))
    }
}
