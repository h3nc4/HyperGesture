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

import com.h3nc4.hypergesture.gesture.ScreenEdge
import org.junit.Assert.assertEquals
import org.junit.Test

class StripRepairTest {

    private val left = StripWindow(ScreenEdge.LEFT, attached = true, widthPx = 40, heightPx = 1200)
    private val right = StripWindow(ScreenEdge.RIGHT, attached = true, widthPx = 40, heightPx = 1200)
    private val bottom = StripWindow(ScreenEdge.BOTTOM, attached = true, widthPx = 900, heightPx = 60)

    private val reattached = mutableListOf<List<StripWindow>>()
    private var reattachWorks = true
    private var reinstalls = 0

    private fun repair(vararg strips: StripWindow) = repairDeadStrips(
        strips = strips.toList(),
        reattach = { dead ->
            reattached += dead
            reattachWorks
        },
        reinstall = { reinstalls++ },
    )

    @Test
    fun `strips that all kept their windows are left alone`() {
        repair(left, right, bottom)
        assertEquals(emptyList<List<StripWindow>>(), reattached)
        assertEquals(0, reinstalls)
    }

    @Test
    fun `a detached strip is added back alone`() {
        val detached = left.copy(attached = false)
        repair(detached, right, bottom)
        assertEquals(listOf(listOf(detached)), reattached)
        assertEquals(0, reinstalls)
    }

    @Test
    fun `a strip attached at 0 by 0 is added back`() {
        val collapsed = bottom.copy(widthPx = 0, heightPx = 0)
        repair(left, right, collapsed)
        assertEquals(listOf(listOf(collapsed)), reattached)
    }

    @Test
    fun `one zero dimension is enough to count as dead`() {
        val narrow = right.copy(widthPx = 0)
        val flat = bottom.copy(heightPx = 0)
        repair(left, narrow, flat)
        assertEquals(listOf(listOf(narrow, flat)), reattached)
    }

    @Test
    fun `no installed strip leaves the installer to its own retry`() {
        repair()
        assertEquals(emptyList<List<StripWindow>>(), reattached)
        assertEquals(0, reinstalls)
    }

    @Test
    fun `a refused re-add falls back to the full install`() {
        reattachWorks = false
        repair(left.copy(attached = false), right, bottom)
        assertEquals(1, reinstalls)
    }

    @Test
    fun `the fallback goes through the installer's backoff until the strips attach`() {
        val delays = mutableListOf<Long>()
        var pending: (() -> Unit)? = null
        var calls = 0
        val installer = StripInstaller(
            install = {
                calls++
                if (calls == 1) error("BadTokenException") else 3
            },
            schedule = { delay, retry ->
                delays += delay
                pending = retry
            },
            cancelRetry = { pending = null },
            onFailure = {},
            onAttached = {},
        )

        repairDeadStrips(listOf(left.copy(attached = false), right, bottom), { false }, installer::run)
        assertEquals(listOf(StripInstaller.FIRST_RETRY_MS), delays)
        pending!!.invoke()

        assertEquals(2, calls)
        assertEquals(null, pending)
    }
}
