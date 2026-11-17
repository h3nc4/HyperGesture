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
import org.junit.Assert.assertTrue
import org.junit.Test

class StripInstallerTest {

    private val delays = mutableListOf<Long>()
    private var pending: (() -> Unit)? = null
    private val failures = mutableListOf<Throwable>()
    private val attached = mutableListOf<Int>()

    private fun installer(install: () -> Int) = StripInstaller(
        install = install,
        schedule = { delay, retry ->
            delays += delay
            pending = retry
        },
        cancelRetry = { pending = null },
        onFailure = failures::add,
        onAttached = attached::add,
    )

    private fun runPending() {
        val retry = pending
        pending = null
        retry?.invoke()
    }

    @Test
    fun `a throw on the first install is retried until the strips attach`() {
        var calls = 0
        val subject = installer {
            calls++
            if (calls < 3) error("BadTokenException") else 3
        }
        subject.run()
        runPending()
        runPending()
        assertEquals(3, calls)
        assertEquals(2, failures.size)
        assertEquals(listOf(0, 0, 3), attached)
        assertEquals(listOf(250L, 500L), delays)
        assertEquals(null, pending)
    }

    @Test
    fun `an install that attaches no strip is retried without a throw`() {
        var result = 0
        val subject = installer { result }
        subject.run()
        assertTrue(failures.isEmpty())
        assertEquals(listOf(250L), delays)
        result = 2
        runPending()
        assertEquals(listOf(0, 2), attached)
        assertEquals(null, pending)
    }

    @Test
    fun `a fresh install cancels the pending retry and resets the backoff`() {
        var result = 0
        val subject = installer { result }
        subject.run()
        runPending()
        assertEquals(listOf(250L, 500L), delays)
        result = 3
        subject.run()
        assertEquals(null, pending)
        result = 0
        subject.run()
        assertEquals(250L, delays.last())
    }

    @Test
    fun `the backoff doubles up to its ceiling`() {
        assertEquals(250L, StripInstaller.retryDelayMs(0))
        assertEquals(4_000L, StripInstaller.retryDelayMs(4))
        assertEquals(StripInstaller.LONGEST_RETRY_MS, StripInstaller.retryDelayMs(6))
        assertEquals(StripInstaller.LONGEST_RETRY_MS, StripInstaller.retryDelayMs(40))
    }
}
