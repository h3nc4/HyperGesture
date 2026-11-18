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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionScopeTest {

    private val subject = ConnectionScope { SupervisorJob() + Dispatchers.Unconfined }

    @Test
    fun `a reconnect after teardown launches like a first connect`() {
        val first = subject.open()
        subject.close()
        assertFalse(first.isActive)

        val second = subject.open()
        var ran = false
        second.launch { ran = true }
        assertTrue(second.isActive)
        assertTrue(ran)
    }

    @Test
    fun `teardown cancels the jobs its connection launched`() {
        val job = subject.open().launch { awaitCancellation() }
        subject.close()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a connect without teardown cancels the previous connection`() {
        val first = subject.open()
        val job = first.launch { awaitCancellation() }
        val second = subject.open()
        assertNotSame(first, second)
        assertTrue(job.isCancelled)
        assertTrue(second.isActive)
    }

    @Test
    fun `a second teardown is harmless`() {
        subject.open()
        subject.close()
        subject.close()
        assertTrue(subject.open().isActive)
    }
}
