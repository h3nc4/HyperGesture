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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherEdgesTest {

    private val others = OtherEdges()

    @Test
    fun `every strip is shut the moment a touch starts`() {
        others.hold(ScreenEdge.LEFT, nowMs = 1_000L)
        ScreenEdge.entries.forEach { assertFalse(others.touchable(it, held = true)) }
        assertEquals(1_000L + OtherEdges.AFTER_MS, others.dueMs)
    }

    @Test
    fun `half a second in the other edges come back and the holding one stays shut`() {
        others.hold(ScreenEdge.LEFT, nowMs = 1_000L)
        assertFalse(others.elapse(nowMs = 1_499L))
        assertFalse(others.touchable(ScreenEdge.RIGHT, held = true))

        assertTrue(others.elapse(nowMs = 1_500L))
        assertTrue(others.touchable(ScreenEdge.RIGHT, held = true))
        assertTrue(others.touchable(ScreenEdge.BOTTOM, held = true))
        assertFalse(others.touchable(ScreenEdge.LEFT, held = true))
        assertNull(others.dueMs)
        assertEquals(OtherEdges.AFTER_MS, 500L)
    }

    @Test
    fun `a touch on another edge shuts every strip again for its own half second`() {
        others.hold(ScreenEdge.LEFT, nowMs = 0L)
        others.elapse(nowMs = 500L)
        others.hold(ScreenEdge.RIGHT, nowMs = 2_000L)
        ScreenEdge.entries.forEach { assertFalse(others.touchable(it, held = true)) }
        assertTrue(others.elapse(nowMs = 2_500L))
        assertTrue(others.touchable(ScreenEdge.LEFT, held = true))
        assertFalse(others.touchable(ScreenEdge.RIGHT, held = true))
    }

    @Test
    fun `closing shuts the other edges and cancels the wait`() {
        others.hold(ScreenEdge.BOTTOM, nowMs = 0L)
        others.elapse(nowMs = 500L)
        others.close()
        assertFalse(others.touchable(ScreenEdge.LEFT, held = true))
        assertNull(others.dueMs)
        assertFalse(others.elapse(nowMs = 60_000L))
    }

    @Test
    fun `every strip takes touches once nothing holds them`() {
        others.hold(ScreenEdge.LEFT, nowMs = 0L)
        ScreenEdge.entries.forEach { assertTrue(others.touchable(it, held = false)) }
    }
}
