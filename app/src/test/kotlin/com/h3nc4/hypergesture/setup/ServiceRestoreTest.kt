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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.h3nc4.hypergesture.MemoryDataStore
import com.h3nc4.hypergesture.navigation.KeptSetting
import com.h3nc4.hypergesture.navigation.SettingsAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRestoreTest {

    private class FakeSettings(override var granted: Boolean = true) : SettingsAccess {
        val values = mutableMapOf<String, String?>()
        var writes = 0
        var refuseWrites = false
        var refusal: String? = "revoked"
        var ignoreWrites = false

        override fun read(setting: KeptSetting) = values[setting.key]

        override fun write(setting: KeptSetting) {
            if (refuseWrites) throw SecurityException(refusal)
            writes++
            if (!ignoreWrites) values[setting.key] = setting.value
        }
    }

    private val removed = RestoreFacts(
        optedIn = true,
        granted = true,
        switchedOn = false,
        running = false,
        serviceEverEnabled = true,
        keptOff = false,
        lastRestoreAtMs = null,
        nowMs = 1_000_000L,
    )

    private fun decide(facts: RestoreFacts) = ServiceRestoreRule.decide(facts)

    @Test
    fun `a removal with the switch and the grant is restored`() {
        assertEquals(RestoreDecision.RESTORE, decide(removed))
    }

    @Test
    fun `without the grant nothing is decided`() {
        assertEquals(RestoreDecision.NONE, decide(removed.copy(granted = false)))
    }

    @Test
    fun `with the switch off nothing is decided`() {
        assertEquals(RestoreDecision.NONE, decide(removed.copy(optedIn = false)))
    }

    @Test
    fun `a service still listed or running is left to the framework`() {
        assertEquals(RestoreDecision.NONE, decide(removed.copy(switchedOn = true)))
        assertEquals(RestoreDecision.NONE, decide(removed.copy(running = true)))
    }

    @Test
    fun `a fresh install that never ran the service is not switched on`() {
        assertEquals(RestoreDecision.NONE, decide(removed.copy(serviceEverEnabled = false)))
    }

    @Test
    fun `a second removal within the window is held off`() {
        val soon = removed.copy(lastRestoreAtMs = removed.nowMs - ServiceRestoreRule.REPEAT_WINDOW_MS + 1)
        assertEquals(RestoreDecision.HOLD_OFF, decide(soon))
    }

    @Test
    fun `a removal after the window is restored again`() {
        val later = removed.copy(lastRestoreAtMs = removed.nowMs - ServiceRestoreRule.REPEAT_WINDOW_MS)
        assertEquals(RestoreDecision.RESTORE, decide(later))
    }

    @Test
    fun `a restore stamped in the future by a clock change does not hold off`() {
        assertEquals(RestoreDecision.RESTORE, decide(removed.copy(lastRestoreAtMs = removed.nowMs + 5_000L)))
    }

    @Test
    fun `a removal held off stays off until the service runs again`() {
        assertEquals(RestoreDecision.NONE, decide(removed.copy(keptOff = true)))
    }

    @Test
    fun `the service is appended and every other one kept`() {
        assertEquals("a/b", ServiceRestoreRule.withService(null, "a/b"))
        assertEquals("a/b", ServiceRestoreRule.withService("", "a/b"))
        assertEquals("x/y:a/b", ServiceRestoreRule.withService("x/y", "a/b"))
        assertEquals("x/y:z/w:a/b", ServiceRestoreRule.withService("x/y::z/w:", "a/b"))
    }

    private val pkg = "com.h3nc4.hypergesture"
    private val cls = "com.h3nc4.hypergesture.service.HyperGestureAccessibilityService"
    private val services = "enabled_accessibility_services"

    private fun restore(access: FakeSettings, settings: ServiceRestoreSettings) =
        ServiceRestore(access, settings, pkg, cls)

    private fun optedIn() = ServiceRestoreSettings(MemoryDataStore()).also { runBlocking { it.setEnabled(true) } }

    @Test
    fun `a restore writes the service and accessibility_enabled`() = runBlocking {
        val access = FakeSettings().apply { values[services] = "other/thing" }
        val outcome = restore(access, optedIn()).afterStop(running = false, serviceEverEnabled = true, nowMs = 1L)
        assertEquals(RestoreOutcome.Restored, outcome)
        assertEquals("other/thing:$pkg/$cls", access.values[services])
        assertEquals("1", access.values["accessibility_enabled"])
    }

    @Test
    fun `without the grant nothing is written`() = runBlocking {
        val access = FakeSettings(granted = false)
        val outcome = restore(access, optedIn()).afterStop(running = false, serviceEverEnabled = true, nowMs = 1L)
        assertEquals(RestoreOutcome.Untouched, outcome)
        assertEquals(0, access.writes)
    }

    @Test
    fun `the switch left off writes nothing`() = runBlocking {
        val access = FakeSettings()
        val settings = ServiceRestoreSettings(MemoryDataStore())
        val outcome = restore(access, settings).afterStop(running = false, serviceEverEnabled = true, nowMs = 1L)
        assertEquals(RestoreOutcome.Untouched, outcome)
        assertEquals(0, access.writes)
    }

    @Test
    fun `a service listed under its short name is not written again`() = runBlocking {
        val access = FakeSettings().apply { values[services] = "$pkg/.service.HyperGestureAccessibilityService" }
        val outcome = restore(access, optedIn()).afterStop(running = false, serviceEverEnabled = true, nowMs = 1L)
        assertEquals(RestoreOutcome.Untouched, outcome)
        assertEquals(0, access.writes)
    }

    @Test
    fun `switching it off twice within a minute keeps it off until it runs again`() = runBlocking {
        val access = FakeSettings()
        val settings = optedIn()
        val restore = restore(access, settings)
        assertEquals(RestoreOutcome.Restored, restore.afterStop(false, true, nowMs = 10_000L))
        access.values[services] = ""
        assertEquals(RestoreOutcome.HeldOff, restore.afterStop(false, true, nowMs = 30_000L))
        assertTrue(settings.keptOff())
        // Boot and app resumes later still leave it off.
        assertEquals(RestoreOutcome.Untouched, restore.afterStop(false, true, nowMs = 500_000L))
        assertEquals("", access.values[services])
        restore.serviceRunning()
        assertFalse(settings.keptOff())
        assertEquals(RestoreOutcome.Restored, restore.afterStop(false, true, nowMs = 600_000L))
    }

    @Test
    fun `a revoked grant reports the refusal`() = runBlocking {
        val access = FakeSettings().apply { refuseWrites = true }
        val outcome = restore(access, optedIn()).afterStop(running = false, serviceEverEnabled = true, nowMs = 1L)
        assertEquals(RestoreOutcome.Failed("revoked"), outcome)
    }

    @Test
    fun `a write the system ignores is reported`() = runBlocking {
        val access = FakeSettings().apply { ignoreWrites = true }
        val outcome = restore(access, optedIn()).afterStop(running = false, serviceEverEnabled = true, nowMs = 1L)
        assertTrue(outcome is RestoreOutcome.Failed)
    }

    @Test
    fun `a refusal without a message still reports why`() = runBlocking {
        val access = FakeSettings().apply {
            refuseWrites = true
            refusal = null
        }
        val outcome = restore(access, optedIn()).afterStop(running = false, serviceEverEnabled = true, nowMs = 1L)
        assertEquals(RestoreOutcome.Failed("permission refused"), outcome)
    }

    @Test
    fun `a running service clears nothing it never held off`() = runBlocking {
        val settings = optedIn()
        restore(FakeSettings(), settings).serviceRunning()
        assertFalse(settings.keptOff())
    }

    @Test
    fun `turning the switch off stops the restore`() = runBlocking {
        val access = FakeSettings()
        val settings = optedIn()
        settings.setEnabled(false)
        assertEquals(RestoreOutcome.Untouched, restore(access, settings).afterStop(false, true, nowMs = 1L))
        assertEquals(0, access.writes)
    }

    /** Suspends on every read, as the store on disk does. */
    private class SlowDataStore(private val inner: DataStore<Preferences> = MemoryDataStore()) :
        DataStore<Preferences> by inner {
        override val data: Flow<Preferences> = flow {
            yield()
            emitAll(inner.data)
        }
    }

    @Test
    fun `a store that suspends on each read restores the same way`() = runBlocking {
        val settings = ServiceRestoreSettings(SlowDataStore()).apply { setEnabled(true) }
        val access = FakeSettings()
        assertEquals(RestoreOutcome.Restored, restore(access, settings).afterStop(false, true, nowMs = 1L))
    }

    @Test
    fun `a running service or one never enabled is left alone`() = runBlocking {
        val access = FakeSettings()
        val restore = restore(access, optedIn())
        assertEquals(RestoreOutcome.Untouched, restore.afterStop(running = true, serviceEverEnabled = true, nowMs = 1L))
        assertEquals(RestoreOutcome.Untouched, restore.afterStop(running = false, serviceEverEnabled = false, nowMs = 1L))
        assertEquals(0, access.writes)
    }
}
