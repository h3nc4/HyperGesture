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

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The latest strip events in memory, so a stream that never ended is reported with its history. */
internal class StreamJournal(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clockMs: () -> Long = System::currentTimeMillis,
    zone: ZoneId = ZoneId.systemDefault(),
) {

    private val format = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(zone)

    private val entries = ArrayDeque<String>(capacity)

    fun add(event: String) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast("${now()} $event")
    }

    fun now(): String = format.format(Instant.ofEpochMilli(clockMs()))

    fun dump(): String = entries.joinToString("\n")

    companion object {
        const val DEFAULT_CAPACITY = 80
    }
}
