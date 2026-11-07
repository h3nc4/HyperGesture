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

package com.h3nc4.hypergesture.update

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class InstallSessionTest {

    private class FakeSessions : InstallSessions {
        val threads = mutableListOf<String>()
        val abandoned = mutableListOf<Int>()
        var createFailure: Exception? = null
        var writeFailure: Exception? = null
        var abandonFailure: Exception? = null

        override fun create(size: Long): Int {
            threads += Thread.currentThread().name
            createFailure?.let { throw it }
            return 7
        }

        override fun writeAndCommit(sessionId: Int, file: File) {
            threads += Thread.currentThread().name
            writeFailure?.let { throw it }
        }

        override fun abandon(sessionId: Int) {
            abandoned += sessionId
            abandonFailure?.let { throw it }
        }
    }

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "install-io") }
    private val io = executor.asCoroutineDispatcher()
    private val sessions = FakeSessions()
    private val apk = File("hypergesture.apk")

    @After
    fun stop() = executor.shutdown()

    private fun install() = runBlocking { installInSession(sessions, apk, io) }

    @Test
    fun `the session is created and written off the calling thread`() {
        assertNull(install())
        assertEquals(2, sessions.threads.size)
        assertTrue(sessions.threads.toString(), sessions.threads.all { it.startsWith("install-io") })
        assertTrue(sessions.abandoned.isEmpty())
    }

    @Test
    fun `a failed write abandons the session and reports why`() {
        sessions.writeFailure = IOException("no space")
        assertEquals("no space", install())
        assertEquals(listOf(7), sessions.abandoned)
    }

    @Test
    fun `an abandon that fails too still reports the first error`() {
        sessions.writeFailure = SecurityException("denied")
        sessions.abandonFailure = IllegalStateException("already gone")
        assertEquals("denied", install())
        assertEquals(listOf(7), sessions.abandoned)
    }

    @Test
    fun `a session that was never created has nothing to abandon`() {
        sessions.createFailure = IOException("too many sessions")
        assertEquals("too many sessions", install())
        assertTrue(sessions.abandoned.isEmpty())
    }
}
