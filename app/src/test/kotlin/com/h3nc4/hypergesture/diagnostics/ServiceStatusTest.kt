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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStatusTest {

    @Test
    fun `switch off and nothing bound reads off`() {
        assertEquals(ServiceStatus.OFF, ServiceStatus.of(switchedOn = false, bound = false, running = false))
    }

    @Test
    fun `switch on without a live service reads stopped`() {
        assertEquals(ServiceStatus.STOPPED, ServiceStatus.of(switchedOn = true, bound = false, running = false))
    }

    @Test
    fun `bound but not yet connected reads stopped`() {
        assertEquals(ServiceStatus.STOPPED, ServiceStatus.of(switchedOn = false, bound = true, running = false))
    }

    @Test
    fun `a live service reads working`() {
        assertEquals(ServiceStatus.WORKING, ServiceStatus.of(switchedOn = true, bound = true, running = true))
    }

    @Test
    fun `finds the service in a full or short entry`() {
        val cls = "$PKG.service.HyperGestureAccessibilityService"
        assertTrue(isServiceListed("other.app/.A:$PKG/$cls", PKG, cls))
        assertTrue(isServiceListed("$PKG/.service.HyperGestureAccessibilityService", PKG, cls))
    }

    @Test
    fun `an empty, missing or foreign list does not list it`() {
        val cls = "$PKG.service.HyperGestureAccessibilityService"
        assertFalse(isServiceListed(null, PKG, cls))
        assertFalse(isServiceListed("", PKG, cls))
        assertFalse(isServiceListed("other.app/$cls", PKG, cls))
        assertFalse(isServiceListed("$PKG/$PKG.Other", PKG, cls))
    }

    private companion object {
        const val PKG = "com.h3nc4.hypergesture"
    }
}
