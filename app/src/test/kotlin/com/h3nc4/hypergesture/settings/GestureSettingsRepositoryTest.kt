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

package com.h3nc4.hypergesture.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.h3nc4.hypergesture.FilesContext
import com.h3nc4.hypergesture.MemoryDataStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GestureSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `two concurrent updates of different fields both land`() = runBlocking {
        val repository = GestureSettingsRepository(FilesContext(folder.root), MemoryDataStore())

        coroutineScope {
            launch { repository.update { it.copy(edgeWidthDp = 40f) } }
            launch { repository.update { it.copy(hapticFeedbackEnabled = false) } }
        }

        val stored = repository.read()
        assertEquals(40f, stored.edgeWidthDp)
        assertFalse(stored.hapticFeedbackEnabled)
    }

    @Test
    fun `concurrent updates of one field each read the value the previous one wrote`() = runBlocking {
        val repository = GestureSettingsRepository(FilesContext(folder.root), MemoryDataStore())

        coroutineScope {
            repeat(20) { launch { repository.update { it.copy(edgeWidthDp = it.edgeWidthDp + 1f) } } }
        }

        assertEquals(40f, repository.read().edgeWidthDp)
    }

    @Test
    fun `an update under debug overrides leaves the overridden fields as stored`() = runBlocking {
        val store = MemoryDataStore(mutablePreferencesOf(RECENTS_HOLD_MS to 250L, APP_SWITCH to false))
        val overrides = DebugOverrides(recentsHoldMs = 400L, appSwitchEnabled = true)
        val repository = GestureSettingsRepository(store) { overrides }

        repository.update { it.copy(hapticFeedbackEnabled = false) }

        val stored = store.data.first()
        assertEquals(250L, stored[RECENTS_HOLD_MS])
        assertEquals(false, stored[APP_SWITCH])
        assertEquals(false, stored[HAPTIC_FEEDBACK])
        val read = repository.read()
        assertEquals(400L, read.recentsHoldMs)
        assertTrue(read.appSwitchEnabled)
    }

    @Test
    fun `without overrides an update stores every field it was given`() = runBlocking {
        val store = MemoryDataStore()
        val repository = GestureSettingsRepository(store) { DebugOverrides() }

        repository.update { it.copy(recentsHoldMs = 300L, appSwitchEnabled = true) }

        val stored = store.data.first()
        assertEquals(300L, stored[RECENTS_HOLD_MS])
        assertEquals(true, stored[APP_SWITCH])
    }

    @Test
    fun `a release build ignores the overrides`() {
        assertEquals(DebugOverrides(), DebugOverrides.read(FilesContext(folder.root)))
    }

    private companion object {
        val RECENTS_HOLD_MS = longPreferencesKey("recents_hold_ms")
        val APP_SWITCH = booleanPreferencesKey("app_switch_enabled")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
    }
}
