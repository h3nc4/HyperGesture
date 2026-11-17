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

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGeometryTest {

    private val portrait = ScreenGeometry(1080, 2400, 2.625f, 0, 0, 126)

    @Test
    fun `a read that matches the display rotation keeps its insets`() {
        val landscape = ScreenGeometry(2400, 1080, 2.625f, 0, 126, 0)
        assertEquals(
            landscape.copy(rotation = Surface.ROTATION_90),
            landscape.alignedTo(Surface.ROTATION_90, naturalLandscape = false),
        )
        assertEquals(
            portrait.copy(rotation = Surface.ROTATION_180),
            portrait.alignedTo(Surface.ROTATION_180, naturalLandscape = false),
        )
    }

    @Test
    fun `a read from before the turn is swapped without insets`() {
        assertEquals(
            ScreenGeometry(2400, 1080, 2.625f, 0, 0, 0, Surface.ROTATION_270),
            portrait.alignedTo(Surface.ROTATION_270, naturalLandscape = false),
        )
    }

    @Test
    fun `a display that is landscape at rest is portrait at a quarter turn`() {
        val tablet = ScreenGeometry(2560, 1600, 2f, 0, 0, 80)
        assertEquals(
            ScreenGeometry(1600, 2560, 2f, 0, 0, 0, Surface.ROTATION_90),
            tablet.alignedTo(Surface.ROTATION_90, naturalLandscape = true),
        )
    }

    @Test
    fun `a new rotation alone makes the geometry differ`() {
        assertEquals(false, portrait == portrait.alignedTo(Surface.ROTATION_180, naturalLandscape = false))
    }
}
