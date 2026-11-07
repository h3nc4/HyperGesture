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
    fun `the switch starts off and remembers being turned on`() = runBlocking {
        val store = NavigationKeeperSettings(MemoryDataStore())
        assertFalse(store.enabled.first())
        store.setEnabled(true)
        assertTrue(store.enabled.first())
    }
}
