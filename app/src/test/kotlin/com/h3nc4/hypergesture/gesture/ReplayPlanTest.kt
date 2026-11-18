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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPlanTest {

    private fun plan(samples: List<TouchSample>, density: Float = DENSITY) =
        ReplayPlan.of(samples, density, LONG_PRESS_MS)

    private fun drag(samples: List<TouchSample>) = plan(samples) as ReplayPlan.Drag

    /** A straight drag along x, one sample every [stepMs], at [xAt] for each elapsed time. */
    private fun stroke(durationMs: Long, stepMs: Long = 8L, xAt: (Long) -> Float = { it.toFloat() }) =
        (0L..durationMs step stepMs).map { TouchSample(xAt(it), 500f, 1_000L + it) }

    private fun still(durationMs: Long, xPx: Float = 100f) =
        listOf(TouchSample(xPx, 500f, 1_000L), TouchSample(xPx + 2f, 500f, 1_000L + durationMs))

    private fun speed(segment: ReplaySegment): Float =
        (segment.points.last().xPx - segment.points.first().xPx) / segment.durationMs

    @Test
    fun `no samples plans nothing`() {
        assertNull(plan(emptyList()))
    }

    @Test
    fun `a quick tap is clamped to a short stroke`() {
        assertEquals(ReplayPlan.MIN_TAP_MS, plan(still(10L))!!.durationMs)
        assertEquals(ReplayPlan.TAP_STROKE_CAP_MS, plan(still(200L))!!.durationMs)
    }

    @Test
    fun `a hold past the long press timeout is mirrored up to the cap`() {
        assertEquals(LONG_PRESS_MS, plan(still(LONG_PRESS_MS))!!.durationMs)
        assertEquals(ReplayPlan.MAX_REPLAY_MS, plan(still(10_000L))!!.durationMs)
    }

    @Test
    fun `the tap slop scales with density`() {
        val moved = listOf(TouchSample(0f, 0f, 0L), TouchSample(6f, 0f, 30L))
        assertTrue(plan(moved, density = 1f) is ReplayPlan.Drag)
        assertTrue(plan(moved, density = 3f) is ReplayPlan.Tap)
        val atSlop = listOf(TouchSample(0f, 0f, 0L), TouchSample(ReplayPlan.TAP_SLOP_DP * 3f, 0f, 30L))
        assertTrue(plan(atSlop, density = 3f) is ReplayPlan.Drag)
    }

    @Test
    fun `segments chain from the first sample to the last`() {
        val samples = stroke(300L)
        val segments = drag(samples).segments
        assertEquals(samples.first(), segments.first().points.first())
        assertEquals(samples.last(), segments.last().points.last())
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.points.last(), b.points.first()) }
        assertEquals(samples, listOf(samples.first()) + segments.flatMap { it.points.drop(1) })
    }

    @Test
    fun `segment durations add up to the recorded duration`() {
        listOf(16L, 120L, 300L, 1_000L, 2_996L).forEach { durationMs ->
            val samples = stroke(durationMs, stepMs = 4L)
            val recorded = samples.last().timestampMs - samples.first().timestampMs
            assertEquals(recorded, drag(samples).durationMs)
        }
    }

    @Test
    fun `a drag past the cap is replayed within it`() {
        val plan = drag(stroke(9_000L))
        assertEquals(ReplayPlan.MAX_REPLAY_MS, plan.durationMs)
        assertTrue(plan.segments.all { it.durationMs >= 1L })
    }

    @Test
    fun `segments grow from the release backwards`() {
        val durations = drag(stroke(1_000L, stepMs = 4L)).segments.map { it.durationMs }.reversed()
        assertEquals(ReplayPlan.TAIL_SEGMENT_MS, durations.first())
        durations.dropLast(1).zipWithNext().forEach { (later, earlier) -> assertEquals(later * 2, earlier) }
    }

    @Test
    fun `a long drag needs only a few segments`() {
        assertTrue(drag(stroke(ReplayPlan.MAX_REPLAY_MS)).segments.size <= 9)
        assertEquals(1, drag(listOf(TouchSample(0f, 0f, 0L), TouchSample(50f, 0f, 8L))).segments.size)
    }

    @Test
    fun `a fling keeps its release speed rather than its average`() {
        // Accelerating as x = t^2 / 100. The finger leaves at 6 px per ms, twice its average.
        val samples = stroke(300L, stepMs = 4L) { (it * it) / 100f }
        val average = samples.last().xPx / 300f
        val release = speed(drag(samples).segments.last())
        assertTrue("release $release against average $average", release > 1.8f * average)
        assertEquals(6f, release, 0.2f)
    }

    @Test
    fun `sparse samples leave no empty segment`() {
        val samples = listOf(TouchSample(0f, 0f, 0L), TouchSample(10f, 0f, 10L), TouchSample(100f, 0f, 100L))
        val plan = drag(samples)
        assertEquals(listOf(10L, 90L), plan.segments.map { it.durationMs })
        assertTrue(plan.segments.all { it.points.size == 2 })
    }

    @Test
    fun `samples sharing a timestamp still get a positive duration`() {
        val plan = drag(listOf(TouchSample(0f, 0f, 5L), TouchSample(80f, 0f, 5L)))
        assertEquals(listOf(1L), plan.segments.map { it.durationMs })
    }

    @Test
    fun `a recording past its capacity replays to where the finger lifted`() {
        val recording = TouchRecording(400)
        val samples = stroke(4_000L, stepMs = 4L)
        samples.forEach(recording::add)
        assertEquals(samples.last(), drag(recording.snapshot()).segments.last().points.last())
    }

    private companion object {
        const val DENSITY = 3f
        const val LONG_PRESS_MS = 400L
    }
}
