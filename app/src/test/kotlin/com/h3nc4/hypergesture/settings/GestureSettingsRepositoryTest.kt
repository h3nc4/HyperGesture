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

import com.h3nc4.hypergesture.FilesContext
import com.h3nc4.hypergesture.MemoryDataStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
