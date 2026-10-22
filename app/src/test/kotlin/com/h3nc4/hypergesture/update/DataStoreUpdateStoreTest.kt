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

package com.h3nc4.hypergesture.update

import com.h3nc4.hypergesture.MemoryDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreUpdateStoreTest {

    private val store = DataStoreUpdateStore(MemoryDataStore())

    @Test
    fun `a fresh install has the check off and nothing recorded`() = runBlocking {
        assertFalse(store.enabled())
        assertNull(store.lastAttemptMs())
        assertNull(store.cachedBody())
    }

    @Test
    fun `the switch is remembered both ways`() = runBlocking {
        store.setEnabled(true)
        assertTrue(store.enabled())
        store.setEnabled(false)
        assertFalse(store.enabled())
    }

    @Test
    fun `a failed attempt keeps the last good answer`() = runBlocking {
        store.recordAttempt(atMs = 100, body = "first")
        store.recordAttempt(atMs = 200, body = null)
        assertEquals(200L, store.lastAttemptMs())
        assertEquals("first", store.cachedBody())
        store.recordAttempt(atMs = 300, body = "second")
        assertEquals("second", store.cachedBody())
    }
}
