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
import org.junit.Assert.assertSame
import org.junit.Test

class GeometryReinstallTest {

    private val portrait = ScreenGeometry(1080, 2400, 2.75f, 0, 0, 66)
    private val landscape = portrait.copy(widthPx = 2400, heightPx = 1080)

    @Test
    fun `a throw from the geometry read does not escape`() {
        val failure = IllegalStateException("window metrics unavailable")
        val failures = mutableListOf<Throwable>()
        var reinstalled = false
        reinstallOnGeometryChange(portrait, { throw failure }, { reinstalled = true }, failures::add)
        assertFalse(reinstalled)
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
    }

    @Test
    fun `a throw from the reinstall does not escape`() {
        val failures = mutableListOf<Throwable>()
        reinstallOnGeometryChange(portrait, { landscape }, { error("addView refused") }, failures::add)
        assertEquals(1, failures.size)
    }

    @Test
    fun `only a changed geometry reinstalls`() {
        var reinstalls = 0
        reinstallOnGeometryChange(portrait, { portrait }, { reinstalls++ }, { throw it })
        assertEquals(0, reinstalls)
        reinstallOnGeometryChange(portrait, { landscape }, { reinstalls++ }, { throw it })
        assertEquals(1, reinstalls)
    }
}
