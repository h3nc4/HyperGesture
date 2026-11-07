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

class StreamHoldTest {

    private val limitMs = 5_000L

    private val hold = StreamHold(limitMs)

    @Test
    fun `a stream holds the strips until its own release`() {
        val stream = hold.start(nowMs = 0L)
        assertTrue(hold.held)
        assertTrue(hold.release(stream))
        assertFalse(hold.held)
        assertNull(hold.deadlineMs)
    }

    @Test
    fun `a second release of one stream changes nothing`() {
        val stream = hold.start(nowMs = 0L)
        hold.release(stream)
        assertFalse(hold.release(stream))
    }

    @Test
    fun `the timer releases a stream that went quiet for the limit`() {
        hold.start(nowMs = 1_000L)
        assertFalse(hold.expire(nowMs = 1_000L + limitMs - 1))
        assertTrue(hold.held)
        assertTrue(hold.expire(nowMs = 1_000L + limitMs))
        assertFalse(hold.held)
    }

    @Test
    fun `every touch event pushes the deadline out`() {
        val stream = hold.start(nowMs = 0L)
        hold.activity(stream, nowMs = 4_000L)
        assertEquals(4_000L + limitMs, hold.deadlineMs)
        assertFalse(hold.expire(nowMs = limitMs))
        assertTrue(hold.expire(nowMs = 4_000L + limitMs))
    }

    @Test
    fun `the timer has nothing to release once the stream ended`() {
        val stream = hold.start(nowMs = 0L)
        hold.release(stream)
        assertFalse(hold.expire(nowMs = 60_000L))
    }

    @Test
    fun `a touch event after the timer released a still finger holds the strips again`() {
        val stream = hold.start(nowMs = 0L)
        hold.expire(nowMs = limitMs)
        assertTrue(hold.activity(stream, nowMs = limitMs + 200L))
        assertTrue(hold.held)
        assertTrue(hold.release(stream))
    }

    @Test
    fun `a stream held again after the timer is not replayed`() {
        val stream = hold.start(nowMs = 0L)
        assertTrue(hold.mayReplay(stream))
        hold.expire(nowMs = limitMs)
        hold.activity(stream, nowMs = limitMs + 200L)
        assertFalse(hold.mayReplay(stream))
        assertTrue(hold.mayReplay(hold.start(nowMs = 60_000L)))
    }

    @Test
    fun `a stale stream is never replayed`() {
        val earlier = hold.start(nowMs = 0L)
        hold.reset()
        assertFalse(hold.mayReplay(earlier))
    }

    @Test
    fun `a touch event of a held stream needs no new hold`() {
        val stream = hold.start(nowMs = 0L)
        assertFalse(hold.activity(stream, nowMs = 10L))
    }

    @Test
    fun `a replay finishing after a reinstall leaves the new strips held`() {
        val replayed = hold.start(nowMs = 0L)
        hold.reset()
        val current = hold.start(nowMs = 100L)

        assertFalse(hold.release(replayed))
        assertTrue(hold.held)
        assertTrue(hold.release(current))
    }

    @Test
    fun `a completion from an earlier stream does not free a later one`() {
        val earlier = hold.start(nowMs = 0L)
        val later = hold.start(nowMs = 50L)

        assertFalse(hold.isCurrent(earlier))
        assertFalse(hold.release(earlier))
        assertFalse(hold.activity(earlier, nowMs = 60L))
        assertTrue(hold.held)
        assertTrue(hold.isCurrent(later))
    }

    @Test
    fun `a reinstall drops the hold of a stream whose UP never arrives`() {
        hold.start(nowMs = 0L)
        hold.reset()
        assertFalse(hold.held)
        assertFalse(hold.expire(nowMs = 60_000L))
    }

    @Test
    fun `the timer never cuts the longest replay short`() {
        val overlayHold = StreamHold(StreamHold.STUCK_STREAM_MS)
        val stream = overlayHold.start(nowMs = 0L)
        // The UP is the last touch event, and the safety net of its replay fires this long after.
        val upMs = 2_000L
        overlayHold.activity(stream, upMs)
        val safetyNetMs = upMs + HyperGestureAccessibilityService.LONGEST_REPLAY_HOLD_MS
        assertFalse(overlayHold.expire(safetyNetMs))
        assertTrue(overlayHold.release(stream))
        assertEquals(5_065L, StreamHold.STUCK_STREAM_MS)
    }
}
