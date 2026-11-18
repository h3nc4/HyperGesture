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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLossTest {

    private val nullToken = IllegalStateException(
        "Unable to add window -- token null is not valid; is your activity running?",
    )

    private val otherToken = IllegalStateException(
        "Unable to add window -- token android.os.BinderProxy@1 is not valid; is your activity running?",
    )

    private fun ConnectionLoss.refusedInstall(vararg failures: Throwable): Boolean {
        failures.forEach(::refused)
        return installed(0)
    }

    @Test
    fun `installs refused for a null token read as lost once the streak reaches the threshold`() {
        val subject = ConnectionLoss(threshold = 3)
        assertFalse(subject.refusedInstall(nullToken, nullToken))
        assertFalse(subject.refusedInstall(nullToken))
        assertTrue(subject.refusedInstall(nullToken))
        assertTrue(subject.lost)
    }

    @Test
    fun `an attached strip ends the streak and the loss`() {
        val subject = ConnectionLoss(threshold = 1)
        assertTrue(subject.refusedInstall(nullToken))
        subject.refused(nullToken)
        assertFalse(subject.installed(1))
        assertFalse(subject.lost)
    }

    @Test
    fun `a refusal that names another token or no message breaks the streak`() {
        val subject = ConnectionLoss(threshold = 2)
        assertFalse(subject.refusedInstall(nullToken))
        assertFalse(subject.refusedInstall(nullToken, otherToken))
        assertFalse(subject.refusedInstall(nullToken))
        assertFalse(subject.refusedInstall(RuntimeException()))
        assertFalse(subject.refusedInstall(nullToken))
        assertTrue(subject.refusedInstall(nullToken))
    }

    @Test
    fun `an install with no refusal at all is no evidence of a loss`() {
        val subject = ConnectionLoss(threshold = 1)
        assertFalse(subject.installed(0))
    }

    @Test
    fun `reset forgets the streak and the loss`() {
        val subject = ConnectionLoss(threshold = 2)
        subject.refusedInstall(nullToken)
        subject.refusedInstall(nullToken)
        subject.refused(nullToken)
        subject.reset()
        assertFalse(subject.lost)
        assertFalse(subject.installed(0))
        assertFalse(subject.refusedInstall(nullToken))
        assertTrue(subject.refusedInstall(nullToken))
    }

    @Test
    fun `the default threshold takes three refused installs`() {
        val subject = ConnectionLoss()
        repeat(ConnectionLoss.REFUSED_INSTALLS - 1) { assertFalse(subject.refusedInstall(nullToken)) }
        assertTrue(subject.refusedInstall(nullToken))
    }
}
