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

class StatusSettleTest {

    private val working = StatusSettle(GRACE, ServiceStatus.WORKING)

    @Test
    fun `the first read shows at once whatever it is`() {
        assertEquals(ServiceStatus.DEGRADED, StatusSettle(GRACE, ServiceStatus.DEGRADED).shown)
        assertEquals(ServiceStatus.STOPPED, StatusSettle(GRACE, ServiceStatus.STOPPED).shown)
    }

    @Test
    fun `a strip reinstall that recovers inside the grace never shows degraded`() {
        assertEquals(GRACE, working.offer(ServiceStatus.DEGRADED, nowMs = 1_000))
        assertEquals(ServiceStatus.WORKING, working.shown)
        assertNull(working.offer(ServiceStatus.WORKING, nowMs = 1_250))
        assertEquals(ServiceStatus.WORKING, working.shown)
    }

    @Test
    fun `a rebind that passes through stopped and degraded keeps working`() {
        working.offer(ServiceStatus.STOPPED, nowMs = 0)
        assertEquals(2_500L, working.offer(ServiceStatus.DEGRADED, nowMs = 500))
        working.offer(ServiceStatus.WORKING, nowMs = 900)
        assertEquals(ServiceStatus.WORKING, working.shown)
    }

    @Test
    fun `strips still refused when the grace ends show degraded`() {
        working.offer(ServiceStatus.DEGRADED, nowMs = 0)
        assertEquals(1_000L, working.offer(ServiceStatus.DEGRADED, nowMs = 2_000))
        assertNull(working.offer(ServiceStatus.DEGRADED, nowMs = GRACE))
        assertEquals(ServiceStatus.DEGRADED, working.shown)
    }

    @Test
    fun `a recovery restarts the grace for the next fault`() {
        working.offer(ServiceStatus.DEGRADED, nowMs = 0)
        working.offer(ServiceStatus.WORKING, nowMs = 2_900)
        assertEquals(GRACE, working.offer(ServiceStatus.DEGRADED, nowMs = 3_100))
        assertEquals(ServiceStatus.WORKING, working.shown)
    }

    @Test
    fun `a stop the caller already saw hold for the grace shows at once`() {
        assertNull(working.offer(ServiceStatus.STOPPED, nowMs = 10_000, heldMs = GRACE))
        assertEquals(ServiceStatus.STOPPED, working.shown)
    }

    @Test
    fun `a stop held for part of the grace waits only the rest`() {
        assertEquals(1_000L, working.offer(ServiceStatus.STOPPED, nowMs = 10_000, heldMs = 2_000))
    }

    @Test
    fun `off and working show at once`() {
        working.offer(ServiceStatus.DEGRADED, nowMs = 0)
        assertNull(working.offer(ServiceStatus.OFF, nowMs = 100))
        assertEquals(ServiceStatus.OFF, working.shown)
        assertNull(working.offer(ServiceStatus.WORKING, nowMs = 200))
        assertEquals(ServiceStatus.WORKING, working.shown)
    }

    @Test
    fun `a fall from off into stopped waits too`() {
        val off = StatusSettle(GRACE, ServiceStatus.OFF)
        assertEquals(GRACE, off.offer(ServiceStatus.STOPPED, nowMs = 0))
        assertEquals(ServiceStatus.OFF, off.shown)
    }

    @Test
    fun `a fault already shown moves to another fault at once`() {
        val degraded = StatusSettle(GRACE, ServiceStatus.DEGRADED)
        assertNull(degraded.offer(ServiceStatus.STOPPED, nowMs = 0))
        assertEquals(ServiceStatus.STOPPED, degraded.shown)
    }

    private companion object {
        const val GRACE = 3_000L
    }
}
