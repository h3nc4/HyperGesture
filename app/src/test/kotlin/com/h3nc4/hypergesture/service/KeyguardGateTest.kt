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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyguardGateTest {

    private val reported = mutableListOf<Boolean>()
    private val delays = mutableListOf<Long>()
    private var pending: (() -> Unit)? = null
    private var locked = false
    private val gate = KeyguardGate(
        keyguardLocked = { locked },
        schedule = { delay, check ->
            delays += delay
            pending = check
        },
        cancel = { pending = null },
        onChange = reported::add,
    )

    private fun runPending() {
        val check = pending
        pending = null
        check?.invoke()
    }

    // The screen goes off, the keyguard comes up and the device wakes onto it.
    private fun lockAndWake() {
        gate.screenOff()
        locked = true
        gate.screenOn()
    }

    @Test
    fun `connecting over the lock screen disarms the strips and watches for the unlock`() {
        locked = true
        gate.connected(screenOn = true)
        assertFalse(gate.armed)
        assertEquals(listOf(false), reported)
        assertNotNull(pending)
    }

    @Test
    fun `connecting over the lock screen with the screen off waits for the wake`() {
        locked = true
        gate.connected(screenOn = false)
        assertFalse(gate.armed)
        assertNull(pending)
    }

    @Test
    fun `connecting while unlocked reports armed`() {
        gate.connected(screenOn = true)
        assertTrue(gate.armed)
        assertEquals(listOf(true), reported)
        assertNull(pending)
    }

    @Test
    fun `a reconnect reports again even when nothing changed`() {
        locked = true
        gate.connected(screenOn = false)
        gate.connected(screenOn = false)
        assertEquals(listOf(false, false), reported)
    }

    @Test
    fun `screen off disarms and the unlock broadcast arms again`() {
        gate.connected(screenOn = true)
        lockAndWake()
        assertFalse(gate.armed)
        locked = false
        gate.userPresent()
        assertTrue(gate.armed)
        assertNull(pending)
        assertEquals(listOf(true, false, true), reported)
    }

    @Test
    fun `waking onto the keyguard keeps the strips disarmed while it shows`() {
        gate.connected(screenOn = true)
        lockAndWake()
        runPending()
        runPending()
        assertFalse(gate.armed)
        assertEquals(listOf(true, false), reported)
        assertEquals(List(3) { KeyguardGate.UNLOCK_CHECK_MS }, delays)
    }

    @Test
    fun `the strips arm at the first check after the keyguard goes, ahead of the broadcast`() {
        gate.connected(screenOn = true)
        lockAndWake()
        runPending()
        locked = false
        runPending()
        assertTrue(gate.armed)
        assertNull(pending)
        gate.userPresent()
        assertEquals(listOf(true, false, true), reported)
    }

    @Test
    fun `a second wake signal leaves the pending check where it was`() {
        gate.connected(screenOn = true)
        lockAndWake()
        val first = pending
        gate.screenOn()
        gate.screenOn()
        assertSame(first, pending)
        assertEquals(listOf(KeyguardGate.UNLOCK_CHECK_MS), delays)
    }

    @Test
    fun `a late wake broadcast after the unlock changes nothing`() {
        gate.connected(screenOn = true)
        lockAndWake()
        locked = false
        runPending()
        gate.screenOn()
        assertTrue(gate.armed)
        assertNull(pending)
        assertEquals(listOf(true, false, true), reported)
    }

    @Test
    fun `the screen going off stops the checks`() {
        gate.connected(screenOn = true)
        lockAndWake()
        gate.screenOff()
        assertNull(pending)
        assertFalse(gate.armed)
    }

    @Test
    fun `waking inside the lock delay arms without waiting for an unlock`() {
        gate.connected(screenOn = true)
        gate.screenOff()
        gate.screenOn()
        assertTrue(gate.armed)
        assertNull(pending)
        assertEquals(listOf(true, false, true), reported)
    }

    @Test
    fun `repeated signals report each change once`() {
        gate.connected(screenOn = true)
        gate.screenOff()
        gate.screenOff()
        gate.userPresent()
        gate.userPresent()
        gate.screenOn()
        assertEquals(listOf(true, false, true), reported)
    }
}
