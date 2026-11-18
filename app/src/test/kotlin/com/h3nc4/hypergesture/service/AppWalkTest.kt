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

    private val walk = AppWalk<String>(chainWindowMs).apply { begin(WalkOrder(order, frontInOrder = true)) }

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
        walk.begin(WalkOrder(listOf("org.example.chat", "org.example.mail"), frontInOrder = true))
        assertEquals("org.example.mail", walk.target(toPrevious = true))
    }

    private val ownPackage = "com.h3nc4.hypergesture"

    private val launcher = "app.niagara.launcher"

    private val systemPackages = setOf("com.android.systemui", "com.android.permissioncontroller", "com.android.settings")

    private val filter = WalkFilter(
        ownPackage = ownPackage,
        homePackages = setOf(launcher),
        isSystem = { it in systemPackages },
        hasLauncherEntry = { it == "com.android.settings" || it == launcher || it.startsWith("org.example.") },
    )

    // Each package stays in front for 10 s, well past the shortest visit.
    private fun visits(vararg packages: String) = packages.mapIndexed { i, name -> Resumed(name, i * 10_000L) }

    private fun walkOrder(resumed: List<Resumed>) = newestFirst(resumed, minVisitMs = 1_000L, filter = filter) { it }

    private fun order(resumed: List<Resumed>) = walkOrder(resumed).apps

    private fun walkFrom(resumed: List<Resumed>) = AppWalk<String>(chainWindowMs).apply { begin(walkOrder(resumed)) }

    @Test
    fun `the order lists each package once, newest first`() {
        val apps = order(visits("org.example.notes", "org.example.mail", "org.example.maps", "org.example.mail"))
        assertEquals(listOf("org.example.mail", "org.example.maps", "org.example.notes"), apps)
    }

    @Test
    fun `the order leaves out this app and anything without a launch target`() {
        val resumed = visits("org.example.mail", "org.example.gone", ownPackage)
        val apps = newestFirst(resumed, minVisitMs = 1_000L, filter = filter) { name ->
            name.takeUnless { it == "org.example.gone" }
        }
        assertEquals(listOf("org.example.mail"), apps.apps)
    }

    @Test
    fun `launcher in front, a step back opens the most recent app`() {
        val walk = walkFrom(visits("org.example.maps", "org.example.mail", launcher))
        assertEquals("org.example.mail", walk.target(toPrevious = true))
        assertNull(walk.target(toPrevious = false))
    }

    @Test
    fun `a filtered system app in front, a step back opens the most recent app`() {
        val walk = walkFrom(visits("org.example.maps", "org.example.mail", "com.android.permissioncontroller"))
        assertEquals("org.example.mail", walk.target(toPrevious = true))
        walk.landed(toPrevious = true, nowMs = 1_000L)
        assertEquals("org.example.maps", walk.target(toPrevious = true))
    }

    @Test
    fun `an app in front starts the walk on itself`() {
        val walk = walkFrom(visits("org.example.maps", "org.example.mail"))
        assertEquals("org.example.maps", walk.target(toPrevious = true))
    }

    @Test
    fun `one app behind the launcher is still something to step back to`() {
        assertTrue(walkOrder(visits("org.example.mail", launcher)).hasPrevious)
        assertFalse(walkOrder(visits("org.example.mail")).hasPrevious)
    }

    @Test
    fun `stepping back from an app skips the launcher, even one with a launcher entry`() {
        val apps = order(visits("org.example.maps", launcher, "org.example.mail"))
        assertEquals(listOf("org.example.mail", "org.example.maps"), apps)
    }

    @Test
    fun `system ui and system packages without a launcher entry are skipped`() {
        val resumed = visits("org.example.maps", "com.android.systemui", "com.android.permissioncontroller", "org.example.mail")
        assertEquals(listOf("org.example.mail", "org.example.maps"), order(resumed))
    }

    @Test
    fun `a system app the user can open from the launcher stays in the order`() {
        val apps = order(visits("com.android.settings", "org.example.mail"))
        assertEquals(listOf("org.example.mail", "com.android.settings"), apps)
    }

    @Test
    fun `a dialog-only task too short to be a visit is dropped`() {
        val resumed = listOf(
            Resumed("org.example.maps", 0L),
            Resumed("org.example.share", 10_000L),
            Resumed("org.example.mail", 10_400L),
        )
        assertEquals(listOf("org.example.mail", "org.example.maps"), order(resumed))
    }

    @Test
    fun `an app in front is kept however recently it resumed`() {
        val resumed = listOf(Resumed("org.example.maps", 0L), Resumed("org.example.mail", 10_000L))
        assertEquals(listOf("org.example.mail", "org.example.maps"), order(resumed))
    }

    @Test
    fun `a package whose newest visit was short keeps its place from an earlier real one`() {
        val resumed = listOf(
            Resumed("org.example.maps", 0L),
            Resumed("org.example.notes", 10_000L),
            Resumed("org.example.maps", 20_000L),
            Resumed("org.example.mail", 20_300L),
        )
        assertEquals(listOf("org.example.mail", "org.example.notes", "org.example.maps"), order(resumed))
    }

    @Test
    fun `activities of one app in a row are one visit`() {
        val resumed = listOf(
            Resumed("org.example.maps", 0L),
            Resumed("org.example.notes", 10_000L),
            Resumed("org.example.notes", 10_200L),
            Resumed("org.example.notes", 10_500L),
            Resumed("org.example.mail", 12_000L),
        )
        assertEquals(listOf("org.example.mail", "org.example.notes", "org.example.maps"), order(resumed))
    }

    @Test
    fun `a package is checked and resolved once however often it resumed`() {
        val checked = mutableListOf<String>()
        val counting = WalkFilter("", emptySet(), isSystem = { checked.add(it); false }, hasLauncherEntry = { true })
        val resolved = mutableListOf<String>()
        newestFirst(visits("org.example.mail", "org.example.maps", "org.example.mail"), 1_000L, counting) { name ->
            resolved.add(name)
            name
        }
        assertEquals(listOf("org.example.mail", "org.example.maps"), resolved)
        assertEquals(listOf("org.example.mail", "org.example.maps"), checked)
    }
}
