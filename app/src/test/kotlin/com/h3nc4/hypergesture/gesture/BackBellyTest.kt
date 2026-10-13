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

package com.h3nc4.hypergesture.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackBellyTest {

    @Test
    fun `scale is empty at the edge and full at the end of the drag`() {
        assertEquals(0f, BackBelly.scale(0f), 0f)
        assertEquals(1f, BackBelly.scale(BackBelly.FULL_DRAG_DP), 1e-6f)
        assertEquals(1f, BackBelly.scale(BackBelly.FULL_DRAG_DP * 2), 1e-6f)
        assertEquals(0f, BackBelly.scale(-10f), 0f)
    }

    @Test
    fun `scale is half way at half the drag, like Xiaomi's 180 px ready point`() {
        assertEquals(0.5f, BackBelly.scale(BackBelly.FULL_DRAG_DP / 2), 1e-6f)
    }

    @Test
    fun `scale only grows with the drag`() {
        var previous = -1f
        for (dp in 0..BackBelly.FULL_DRAG_DP.toInt()) {
            val scale = BackBelly.scale(dp.toFloat())
            assertTrue("shrank at ${dp}dp", scale >= previous)
            previous = scale
        }
    }

    @Test
    fun `profile closes at both ends and peaks in the middle`() {
        assertEquals(0f, BackBelly.profile(0f), 1e-6f)
        assertEquals(0f, BackBelly.profile(1f), 1e-6f)
        assertEquals(1f, BackBelly.profile(0.5f), 1e-6f)
    }

    @Test
    fun `profile is symmetric`() {
        for (step in 0..50) {
            val t = step / 100f
            assertEquals(BackBelly.profile(t), BackBelly.profile(1f - t), 1e-5f)
        }
    }

    /** Widths in px read off Xiaomi's 84 by 858 px asset, every 66 rows from the top. */
    @Test
    fun `profile matches the HyperOS asset to within 2 px`() {
        val measured = listOf(
            0.2f, 2.0f, 8.0f, 20.2f, 40.5f, 65.7f, 81.2f,
            81.1f, 65.1f, 39.8f, 19.7f, 7.7f, 1.8f,
        )
        measured.forEachIndexed { index, widthPx ->
            val t = (index * 66 + 0.5f) / 858f
            assertEquals("row ${index * 66}", widthPx, BackBelly.profile(t) * 84f, 2f)
        }
    }
}
