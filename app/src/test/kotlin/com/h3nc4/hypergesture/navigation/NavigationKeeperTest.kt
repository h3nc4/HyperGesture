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

import com.h3nc4.hypergesture.MemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationKeeperTest {

    private class FakeSettings(
        override var granted: Boolean = true,
        val values: MutableMap<String, String?> = mutableMapOf(),
    ) : SettingsAccess {
        var writes = 0
        var refuseWrites = false
        var ignoreWrites = false

        override fun read(setting: KeptSetting) = values[setting.key]

        override fun write(setting: KeptSetting) {
            if (refuseWrites) throw SecurityException("revoked")
            writes++
            if (!ignoreWrites) values[setting.key] = setting.value
        }
    }

    private val hidden = mutableMapOf<String, String?>("force_fsg_nav_bar" to "1", "navigation_mode" to "2")

    @Test
    fun `settings already hiding the bar are not written`() {
        val settings = FakeSettings(values = hidden)
        assertEquals(KeepOutcome.AlreadyKept, NavigationKeeper(settings).keep())
        assertEquals(0, settings.writes)
    }

    @Test
    fun `a reset is put back and only the drifted key is written`() {
        val settings = FakeSettings(values = hidden.apply { this["navigation_mode"] = "0" })
        assertEquals(KeepOutcome.Restored(listOf("navigation_mode")), NavigationKeeper(settings).keep())
        assertEquals(1, settings.writes)
        assertEquals("2", settings.values["navigation_mode"])
    }

    @Test
    fun `an unset key counts as drifted`() {
        val settings = FakeSettings()
        assertEquals(
            KeepOutcome.Restored(listOf("force_fsg_nav_bar", "navigation_mode")),
            NavigationKeeper(settings).keep(),
        )
    }

    @Test
    fun `without the grant nothing is written`() {
        val settings = FakeSettings(granted = false)
        assertEquals(KeepOutcome.NotGranted, NavigationKeeper(settings).keep())
        assertEquals(0, settings.writes)
    }

    @Test
    fun `a grant revoked mid-write reports a failure`() {
        val settings = FakeSettings().apply { refuseWrites = true }
        assertTrue(NavigationKeeper(settings).keep() is KeepOutcome.Failed)
    }

    @Test
    fun `a write the system ignores is a failure, not a restore`() {
        val settings = FakeSettings().apply { ignoreWrites = true }
        val outcome = NavigationKeeper(settings).keep()
        assertTrue(outcome is KeepOutcome.Failed)
        assertTrue((outcome as KeepOutcome.Failed).reason.contains("navigation_mode"))
    }

    @Test
    fun `release writes both buttons back`() {
        val settings = FakeSettings(values = hidden)
        assertEquals(
            KeepOutcome.Restored(listOf("force_fsg_nav_bar", "navigation_mode")),
            NavigationKeeper(settings).release(),
        )
        assertEquals("0", settings.values["force_fsg_nav_bar"])
        assertEquals("0", settings.values["navigation_mode"])
    }

    @Test
    fun `buttons come back only once the keeper hid the bar and the service is gone`() {
        assertTrue(ButtonRestore.shouldRestore(hidByKeeper = true, serviceRunning = false))
        assertFalse(ButtonRestore.shouldRestore(hidByKeeper = true, serviceRunning = true))
        assertFalse(ButtonRestore.shouldRestore(hidByKeeper = false, serviceRunning = false))
        assertFalse(ButtonRestore.shouldRestore(hidByKeeper = false, serviceRunning = true))
    }

    @Test
    fun `gestures stopping after the keeper hid the bar restores the buttons and clears the flag`() = runBlocking {
        val settings = FakeSettings(values = hidden)
        val store = NavigationKeeperSettings(MemoryDataStore())
        val restore = ButtonRestore(NavigationKeeper(settings), store)
        restore.hiding()
        assertTrue(store.barHidden.first())
        assertTrue(restore.gesturesStopped(serviceRunning = false) is KeepOutcome.Restored)
        assertEquals("0", settings.values["force_fsg_nav_bar"])
        assertEquals("0", settings.values["navigation_mode"])
        assertFalse(store.barHidden.first())
        assertEquals(null, restore.gesturesStopped(serviceRunning = false))
    }

    @Test
    fun `a running service keeps the bar hidden`() = runBlocking {
        val settings = FakeSettings(values = hidden)
        val store = NavigationKeeperSettings(MemoryDataStore())
        val restore = ButtonRestore(NavigationKeeper(settings), store)
        restore.hiding()
        assertEquals(null, restore.gesturesStopped(serviceRunning = true))
        assertEquals(0, settings.writes)
        assertTrue(store.barHidden.first())
    }

    @Test
    fun `a bar the keeper never hid is left to the system`() = runBlocking {
        val settings = FakeSettings(values = hidden)
        val restore = ButtonRestore(NavigationKeeper(settings), NavigationKeeperSettings(MemoryDataStore()))
        assertEquals(null, restore.gesturesStopped(serviceRunning = false))
        assertEquals(0, settings.writes)
    }

    @Test
    fun `buttons already back still clear the flag`() = runBlocking {
        val settings = FakeSettings(values = mutableMapOf("force_fsg_nav_bar" to "0", "navigation_mode" to "0"))
        val store = NavigationKeeperSettings(MemoryDataStore())
        val restore = ButtonRestore(NavigationKeeper(settings), store)
        restore.hiding()
        assertEquals(KeepOutcome.AlreadyKept, restore.gesturesStopped(serviceRunning = false))
        assertFalse(store.barHidden.first())
    }

    @Test
    fun `a restore that cannot write keeps the flag for the next chance`() = runBlocking {
        val store = NavigationKeeperSettings(MemoryDataStore())
        val withoutGrant = ButtonRestore(NavigationKeeper(FakeSettings(granted = false, values = hidden)), store)
        withoutGrant.hiding()
        assertEquals(KeepOutcome.NotGranted, withoutGrant.gesturesStopped(serviceRunning = false))
        assertTrue(store.barHidden.first())
        val revoked = FakeSettings(values = hidden).apply { refuseWrites = true }
        val failed = ButtonRestore(NavigationKeeper(revoked), store).gesturesStopped(serviceRunning = false)
        assertTrue(failed is KeepOutcome.Failed)
        assertTrue(store.barHidden.first())
    }

    @Test
    fun `each restore outcome has a log line`() {
        assertTrue(ButtonRestore.logLine(KeepOutcome.AlreadyKept).startsWith("NAVIGATION_BUTTONS_BACK"))
        assertTrue(ButtonRestore.logLine(KeepOutcome.NotGranted).startsWith("NAVIGATION_BUTTONS_NOT_BACK"))
        assertEquals(
            "NAVIGATION_BUTTONS_BACK force_fsg_nav_bar, navigation_mode",
            ButtonRestore.logLine(KeepOutcome.Restored(listOf("force_fsg_nav_bar", "navigation_mode"))),
        )
        assertEquals("NAVIGATION_BUTTONS_NOT_BACK revoked", ButtonRestore.logLine(KeepOutcome.Failed("revoked")))
    }

    @Test
    fun `the switch starts off and remembers being turned on`() = runBlocking {
        val store = NavigationKeeperSettings(MemoryDataStore())
        assertFalse(store.enabled.first())
        store.setEnabled(true)
        assertTrue(store.enabled.first())
    }
}
