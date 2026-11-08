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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchRecordingTest {

    private fun sample(i: Int) = TouchSample(i.toFloat(), 2f * i, 8L * i)

    private fun stream(count: Int) = (0 until count).map(::sample)

    private fun record(count: Int, capacity: Int = CAPACITY) =
        TouchRecording(capacity).apply { stream(count).forEach(::add) }

    private fun assertOrdered(samples: List<TouchSample>) {
        samples.zipWithNext().forEach { (a, b) -> assertTrue(b.timestampMs > a.timestampMs) }
    }

    @Test
    fun `thinning drops every other interior sample`() {
        assertEquals(listOf(0, 2, 4).map(::sample), thinSamples(stream(5)))
        assertEquals(listOf(0, 2, 4, 5).map(::sample), thinSamples(stream(6)))
    }

    @Test
    fun `thinning leaves two samples or fewer alone`() {
        assertEquals(emptyList<TouchSample>(), thinSamples(emptyList()))
        assertEquals(stream(1), thinSamples(stream(1)))
        assertEquals(stream(2), thinSamples(stream(2)))
    }

    @Test
    fun `a stream shorter than capacity is kept whole`() {
        val recording = record(CAPACITY - 1)
        assertEquals(stream(CAPACITY - 1), recording.snapshot())
        assertEquals(CAPACITY - 1, recording.size)
    }

    @Test
    fun `a long stream keeps its first and last samples under capacity`() {
        for (count in listOf(CAPACITY + 1, CAPACITY * 3 + 7, CAPACITY * 25)) {
            val kept = record(count).snapshot()
            assertTrue("$count samples kept ${kept.size}", kept.size <= CAPACITY)
            assertEquals(sample(0), kept.first())
            assertEquals(sample(count - 1), kept.last())
            assertOrdered(kept)
        }
    }

    @Test
    fun `every prefix of a long stream ends on its newest sample`() {
        val recording = TouchRecording(CAPACITY)
        stream(CAPACITY * 10).forEach {
            recording.add(it)
            val kept = recording.snapshot()
            assertEquals(it, kept.last())
            assertEquals(kept.size, recording.size)
            assertTrue(kept.size <= CAPACITY)
        }
    }

    @Test
    fun `thinned samples stay evenly spread`() {
        val kept = record(CAPACITY * 8 + 1).snapshot()
        val gaps = kept.zipWithNext { a, b -> b.timestampMs - a.timestampMs }.dropLast(1)
        assertEquals(1, gaps.distinct().size)
    }

    @Test
    fun `the full-size recording never drops the touch`() {
        val kept = record(5_000, capacity = 400).snapshot()
        assertTrue(kept.size in 200..400)
        assertEquals(sample(4_999), kept.last())
    }

    @Test
    fun `clear starts a fresh recording`() {
        val recording = record(CAPACITY * 4)
        recording.clear()
        assertTrue(recording.isEmpty())
        assertNull(recording.first)
        assertEquals(0, recording.size)
        stream(CAPACITY - 1).forEach(recording::add)
        assertEquals(stream(CAPACITY - 1), recording.snapshot())
        assertEquals(sample(0), recording.first)
    }

    @Test
    fun `a capacity too small to thin is refused`() {
        assertThrows(IllegalArgumentException::class.java) { TouchRecording(3) }
    }

    private companion object {
        const val CAPACITY = 16
    }
}
