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
import org.junit.Assert.assertFalse
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

    // Drives the belly from a real tracker at density 3, so 3 px is 1 dp of drag.
    private fun swipe(vararg xPx: Float): Pair<BackBellyState, MutableList<Float>> {
        val tracker = GestureTracker(ScreenEdge.LEFT, GestureConfiguration(), density = 3f)
        val belly = BackBellyState()
        val scales = mutableListOf<Float>()
        tracker.onDown(TouchSample(0f, 1200f, 0L))
        xPx.forEachIndexed { index, x ->
            belly.follow(tracker.onMove(TouchSample(x, 1200f, 20L * (index + 1))), x / 3f)
            scales += belly.scale
        }
        val last = xPx.last()
        belly.follow(tracker.onUp(TouchSample(last, 1200f, 20L * (xPx.size + 1))), last / 3f)
        return belly to scales
    }

    @Test
    fun `the belly follows a reverted swipe back and shrinks toward the edge`() {
        val (_, scales) = swipe(180f, 120f, 60f, 3f)
        assertTrue("did not shrink on the way back: $scales", scales.zipWithNext().all { (a, b) -> b < a })
        assertTrue(scales.last() < BackBelly.ARROW_MINIMUM_SCALE)
    }

    @Test
    fun `pulling back inside the trigger drops the arrow and pushing out restores it`() {
        val tracker = GestureTracker(ScreenEdge.LEFT, GestureConfiguration(), density = 3f)
        val belly = BackBellyState()
        tracker.onDown(TouchSample(0f, 1200f, 0L))
        belly.follow(tracker.onMove(TouchSample(180f, 1200f, 20L)), 60f)
        assertTrue(belly.arrowShown)
        belly.follow(tracker.onMove(TouchSample(30f, 1200f, 40L)), 10f)
        assertFalse(belly.arrowShown)
        assertTrue("the belly keeps following without its arrow", belly.visible)
        assertEquals(BackBelly.scale(10f), belly.scale, 0f)
        belly.follow(tracker.onMove(TouchSample(180f, 1200f, 60L)), 60f)
        assertTrue(belly.arrowShown)
    }

    @Test
    fun `a reverted swipe leaves no belly on screen`() {
        val tracker = GestureTracker(ScreenEdge.LEFT, GestureConfiguration(), density = 3f)
        val belly = BackBellyState()
        tracker.onDown(TouchSample(0f, 1200f, 0L))
        belly.follow(tracker.onMove(TouchSample(180f, 1200f, 20L)), 60f)
        belly.follow(tracker.onMove(TouchSample(3f, 1200f, 40L)), 1f)
        assertFalse("the arrow outlived the revert", belly.arrowShown)
        val release = tracker.onUp(TouchSample(3f, 1200f, 60L))
        assertEquals(TrackerAction.Reverted, release)
        belly.follow(release, 1f)
        assertFalse(belly.visible)
        assertFalse(belly.arrowShown)
        assertEquals(0f, belly.scale, 0f)
    }

    @Test
    fun `a hidden belly ignores a late move`() {
        val (belly, _) = swipe(180f, 3f)
        belly.follow(TrackerAction.None, 50f)
        assertEquals(0f, belly.scale, 0f)
    }

    @Test
    fun `a fired swipe leaves no belly on screen either`() {
        val (belly, scales) = swipe(180f, 170f)
        assertTrue(scales.all { it > 0f })
        assertFalse(belly.visible)
    }
}
