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

import com.h3nc4.hypergesture.MemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupStateRepositoryTest {

    private val repository = SetupStateRepository(MemoryDataStore())

    private suspend fun record() = repository.record.first()

    @Test
    fun `nothing recorded reads as nothing done`() = runBlocking {
        assertEquals(SetupRecord(), record())
    }

    @Test
    fun `each flag lands in its own field`() = runBlocking {
        val expected = mapOf<SetupFlag, (SetupRecord) -> Boolean>(
            SetupFlag.TILE_ADDED to SetupRecord::tileAdded,
            SetupFlag.SERVICE_EVER_ENABLED to SetupRecord::serviceEverEnabled,
            SetupFlag.RESTRICTED_SETTINGS_CONFIRMED to SetupRecord::restrictedSettingsConfirmed,
            SetupFlag.BATTERY_CONFIRMED to SetupRecord::batteryConfirmed,
            SetupFlag.AUTOSTART_CONFIRMED to SetupRecord::autostartConfirmed,
        )
        assertEquals(SetupFlag.entries.toSet(), expected.keys)
        for ((flag, field) in expected) {
            val alone = SetupStateRepository(MemoryDataStore())
            alone.set(flag, true)
            val read = alone.record.first()
            assertEquals(flag.name, true, field(read))
            assertEquals(flag.name, 1, expected.values.count { it(read) })
        }
    }

    @Test
    fun `an undo clears a confirmation`() = runBlocking {
        repository.set(SetupFlag.BATTERY_CONFIRMED, true)
        repository.set(SetupFlag.BATTERY_CONFIRMED, false)
        assertEquals(SetupRecord(), record())
    }

    @Test
    fun `the tile records its own adding and removal`() = runBlocking {
        repository.setTileAdded(true)
        assertEquals(SetupRecord(tileAdded = true), record())
        repository.setTileAdded(false)
        assertEquals(SetupRecord(), record())
    }
}
