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

import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenWatchTest {

    private val events = mutableListOf<String>()
    private val watch = ScreenWatch(onScreenOn = { events += "on" }, onScreenOff = { events += "off" })

    @Test
    fun `the first state read is a baseline, not a turn`() {
        watch.onDisplayState(Display.STATE_ON)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `off then on reports each turn once`() {
        watch.onDisplayState(Display.STATE_ON)
        watch.onDisplayState(Display.STATE_OFF)
        watch.onDisplayState(Display.STATE_OFF)
        watch.onDisplayState(Display.STATE_ON)
        watch.onDisplayState(Display.STATE_ON)
        assertEquals(listOf("off", "on"), events)
    }

    @Test
    fun `an always-on doze counts as off`() {
        watch.onDisplayState(Display.STATE_ON)
        watch.onDisplayState(Display.STATE_DOZE)
        watch.onDisplayState(Display.STATE_DOZE_SUSPEND)
        watch.onDisplayState(Display.STATE_ON)
        assertEquals(listOf("off", "on"), events)
    }
}
