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
import org.junit.Test

class KeyboardFollowTest {

    private val delays = mutableListOf<Long>()
    private val pending = mutableListOf<() -> Unit>()
    private val failures = mutableListOf<Throwable>()
    private val tops = mutableListOf<Int?>()

    private fun follow(read: () -> Int?) = KeyboardFollow(
        read = read,
        schedule = { delay, recheck ->
            delays += delay
            pending += recheck
        },
        cancelRecheck = { pending.clear() },
        onFailure = failures::add,
        onKeyboardTop = tops::add,
    )

    private fun runPending() {
        val due = pending.toList()
        pending.clear()
        due.forEach { it() }
    }

    @Test
    fun `no keyboard schedules no reread`() {
        follow { null }.check()
        assertEquals(listOf<Int?>(null), tops)
        assertEquals(0, pending.size)
    }

    @Test
    fun `a shown keyboard is read again until an app hides it`() {
        var topPx: Int? = 1500
        val subject = follow { topPx }
        subject.check()
        runPending()
        topPx = null
        runPending()
        assertEquals(listOf(1500, 1500, null), tops)
        assertEquals(listOf(KeyboardFollow.RECHECK_MS, KeyboardFollow.RECHECK_MS), delays)
        assertEquals(0, pending.size)
    }

    @Test
    fun `a touch read keeps one reread pending`() {
        val subject = follow { 1500 }
        subject.check()
        subject.check()
        runPending()
        assertEquals(3, tops.size)
        assertEquals(3, delays.size)
        assertEquals(1, pending.size)
    }

    @Test
    fun `stop cancels the pending reread`() {
        val subject = follow { 1500 }
        subject.check()
        subject.stop()
        assertEquals(0, pending.size)
    }

    @Test
    fun `a failed read reports once and stops rereading`() {
        follow { error("window metrics gone") }.check()
        assertEquals(1, failures.size)
        assertEquals(emptyList<Int?>(), tops)
        assertEquals(0, pending.size)
    }
}
