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

package com.h3nc4.hypergesture.diagnostics

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamJournalTest {

    private var nowMs = 0L

    private fun journal(capacity: Int = 3) = StreamJournal(capacity, { nowMs }, ZoneOffset.UTC)

    @Test
    fun `entries are timestamped in arrival order`() {
        val journal = journal()
        nowMs = 1_000L
        journal.add("LEFT DOWN at 5,1200")
        nowMs = 1_250L
        journal.add("stream released")

        assertEquals(
            "01-01 00:00:01.000 LEFT DOWN at 5,1200\n01-01 00:00:01.250 stream released",
            journal.dump(),
        )
    }

    @Test
    fun `the oldest entry is dropped once capacity is reached`() {
        val journal = journal(capacity = 2)
        journal.add("first")
        journal.add("second")
        journal.add("third")

        val events = journal.dump().lines().map { it.substringAfter(".000 ") }
        assertEquals(listOf("second", "third"), events)
    }

    @Test
    fun `now carries the timestamp entries use`() {
        nowMs = 61_500L
        assertEquals("01-01 00:01:01.500", journal().now())
    }

    @Test
    fun `an empty journal dumps nothing`() {
        assertEquals("", journal().dump())
    }
}
