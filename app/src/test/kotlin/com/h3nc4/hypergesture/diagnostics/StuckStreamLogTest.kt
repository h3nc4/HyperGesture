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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StuckStreamLogTest {

    private fun report(stamp: String) =
        "STREAM_STUCK $stamp held by LEFT\nLEFT attached=true\n---\n$stamp LEFT DOWN at 5,1200\n\n"

    @Test
    fun `the latest report is the last one appended`() {
        val text = report("11-01 10:00:00.000") + report("11-02 10:00:00.000")
        assertEquals(report("11-02 10:00:00.000").trim(), StuckStreamLog.latestOf(text))
    }

    @Test
    fun `a file with no report has no latest one`() {
        assertNull(StuckStreamLog.latestOf(""))
        assertNull(StuckStreamLog.latestOf("garbage\n"))
    }

    @Test
    fun `trimming keeps the newest reports whole`() {
        val older = report("11-01 10:00:00.000")
        val newer = report("11-02 10:00:00.000")
        val trimmed = StuckStreamLog.trimmed(older + newer, newer.length + 10)
        assertEquals(newer, trimmed)
    }

    @Test
    fun `text within the limit is kept as it is`() {
        val text = report("11-01 10:00:00.000")
        assertEquals(text, StuckStreamLog.trimmed(text, text.length))
    }
}
