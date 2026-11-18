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

import android.view.Gravity
import com.h3nc4.hypergesture.gesture.ScreenEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StripFrameTest {

    private val portrait = ScreenGeometry(1080, 2400, 2.625f, 0, 0, 126)

    private val layout = OverlayLayout()

    private fun frame(edge: ScreenEdge, keyboardTopPx: Int?, geometry: ScreenGeometry = portrait) =
        stripFrame(edge, 52, geometry, layout, keyboardTopPx)

    @Test
    fun `without a keyboard the strips keep their full length`() {
        assertEquals(StripFrame(52, 1920, Gravity.START or Gravity.CENTER_VERTICAL), frame(ScreenEdge.LEFT, null))
        assertEquals(StripFrame(52, 1920, Gravity.END or Gravity.CENTER_VERTICAL), frame(ScreenEdge.RIGHT, null))
        assertEquals(StripFrame(972, 178, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL), frame(ScreenEdge.BOTTOM, null))
    }

    @Test
    fun `a keyboard cuts the side strips off at its top`() {
        assertEquals(StripFrame(52, 1260, Gravity.START or Gravity.TOP, 240), frame(ScreenEdge.LEFT, 1500))
        assertEquals(StripFrame(52, 1260, Gravity.END or Gravity.TOP, 240), frame(ScreenEdge.RIGHT, 1500))
    }

    @Test
    fun `a keyboard below the side strips leaves them whole`() {
        assertEquals(frame(ScreenEdge.LEFT, null), frame(ScreenEdge.LEFT, 2160))
    }

    @Test
    fun `a keyboard over the whole side strip leaves one row`() {
        assertEquals(StripFrame(52, 1, Gravity.END or Gravity.TOP, 240), frame(ScreenEdge.RIGHT, 100))
    }

    @Test
    fun `a keyboard leaves the bottom strip only the navigation bar`() {
        assertEquals(StripFrame(972, 126, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL), frame(ScreenEdge.BOTTOM, 1500))
        assertEquals(1, frame(ScreenEdge.BOTTOM, 1500, portrait.copy(navBottomPx = 0)).heightPx)
    }

    @Test
    fun `the side strip on the navigation bar side keeps the bar's width`() {
        val landscape = ScreenGeometry(2400, 1080, 2.625f, 0, 126, 0)
        assertEquals(StripFrame(178, 324, Gravity.END or Gravity.TOP, 108), frame(ScreenEdge.RIGHT, 432, landscape))
    }

    @Test
    fun `only a shown keyboard with a height has a top`() {
        assertEquals(1500, keyboardTop(visible = true, bottomInsetPx = 900, heightPx = 2400))
        assertNull(keyboardTop(visible = false, bottomInsetPx = 900, heightPx = 2400))
        assertNull(keyboardTop(visible = true, bottomInsetPx = 0, heightPx = 2400))
    }
}
