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

package com.h3nc4.hypergesture.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWalkTest {

    private val chainWindowMs = 5_000L

    private val order = listOf("org.example.mail", "org.example.maps", "org.example.notes")

    private val walk = AppWalk<String>(chainWindowMs).apply { begin(order) }

    @Test
    fun `a new walk starts at the newest app and steps back to the one before`() {
        assertEquals("org.example.maps", walk.target(toPrevious = true))
    }

    @Test
    fun `steps back walk further down the order`() {
        walk.landed(toPrevious = true, nowMs = 1_000L)
        assertEquals("org.example.notes", walk.target(toPrevious = true))
    }

    @Test
    fun `a step forward after a step back returns to the newer app`() {
        walk.landed(toPrevious = true, nowMs = 1_000L)
        walk.landed(toPrevious = true, nowMs = 2_000L)
        assertEquals("org.example.maps", walk.target(toPrevious = false))
    }

    @Test
    fun `nothing is newer than the app the walk started on`() {
        assertNull(walk.target(toPrevious = false))
    }

    @Test
    fun `nothing is older than the end of the order`() {
        walk.landed(toPrevious = true, nowMs = 1_000L)
        walk.landed(toPrevious = true, nowMs = 2_000L)
        assertNull(walk.target(toPrevious = true))
    }

    @Test
    fun `a step inside the chain window keeps the walk`() {
        walk.landed(toPrevious = true, nowMs = 1_000L)
        assertFalse(walk.restartsAt(1_000L + chainWindowMs))
    }

    @Test
    fun `a pause past the chain window restarts the walk`() {
        walk.landed(toPrevious = true, nowMs = 1_000L)
        assertTrue(walk.restartsAt(1_001L + chainWindowMs))
    }

    @Test
    fun `an empty walk always restarts`() {
        val empty = AppWalk<String>(chainWindowMs)
        assertTrue(empty.isEmpty)
        assertTrue(empty.restartsAt(0L))
        assertNull(empty.target(toPrevious = true))
    }

    @Test
    fun `a new order puts the walk back at its newest app`() {
        walk.landed(toPrevious = true, nowMs = 1_000L)
        walk.begin(listOf("org.example.chat", "org.example.mail"))
        assertEquals("org.example.mail", walk.target(toPrevious = true))
    }

    @Test
    fun `the order lists each package once, newest first`() {
        val resumed = listOf("org.example.notes", "org.example.mail", "org.example.maps", "org.example.mail")
        val apps = newestFirst(resumed, ownPackage = "com.h3nc4.hypergesture") { it }
        assertEquals(listOf("org.example.mail", "org.example.maps", "org.example.notes"), apps)
    }

    @Test
    fun `the order leaves out this app and anything without a launch target`() {
        val resumed = listOf("org.example.mail", "com.android.launcher", "com.h3nc4.hypergesture")
        val apps = newestFirst(resumed, ownPackage = "com.h3nc4.hypergesture") { name ->
            name.takeUnless { it == "com.android.launcher" }
        }
        assertEquals(listOf("org.example.mail"), apps)
    }

    @Test
    fun `a package is resolved once however often it resumed`() {
        val resolved = mutableListOf<String>()
        newestFirst(listOf("org.example.mail", "org.example.mail"), ownPackage = "") { name ->
            resolved.add(name)
            name
        }
        assertEquals(listOf("org.example.mail"), resolved)
    }
}
