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

// When the strips other than the one holding a touch take new touches again, so a finger
// resting on one edge never leaves every other edge dead for as long as it stays down.
internal class OtherEdges(private val afterMs: Long = AFTER_MS) {

    private var holder: ScreenEdge? = null

    private var startedMs = 0L

    var live = false
        private set

    /** When [elapse] gives the other edges back, or null while nothing waits for it. */
    val dueMs: Long?
        get() = if (holder != null && !live) startedMs + afterMs else null

    fun hold(edge: ScreenEdge, nowMs: Long) {
        holder = edge
        startedMs = nowMs
        live = false
    }

    /** True when this call gave the other edges back. */
    fun elapse(nowMs: Long): Boolean {
        val due = dueMs ?: return false
        if (nowMs < due) return false
        live = true
        return true
    }

    fun close() {
        holder = null
        live = false
    }

    /** Whether [edge] takes a new touch, [held] being true while a stream keeps the strips. */
    fun touchable(edge: ScreenEdge, held: Boolean): Boolean = !held || (live && edge != holder)

    companion object {
        const val AFTER_MS = 500L
    }
}
