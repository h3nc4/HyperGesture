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

package com.h3nc4.hypergesture.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackTest {

    @Test
    fun `opening a screen puts it on top`() {
        assertEquals(listOf(Screen.HOME, Screen.SETUP), listOf(Screen.HOME).opening(Screen.SETUP))
    }

    @Test
    fun `opening the screen already on top leaves the stack alone`() {
        val stack = listOf(Screen.HOME, Screen.SETUP)
        assertEquals(stack, stack.opening(Screen.SETUP))
    }

    @Test
    fun `a screen lower in the stack opens again on top`() {
        assertEquals(
            listOf(Screen.HOME, Screen.SETUP, Screen.HOME),
            listOf(Screen.HOME, Screen.SETUP).opening(Screen.HOME),
        )
    }

    @Test
    fun `saved names restore their screens in order`() {
        assertEquals(listOf(Screen.HOME, Screen.ABOUT), backStackOf(listOf("HOME", "ABOUT")))
    }

    @Test
    fun `an unknown saved name is skipped`() {
        assertEquals(listOf(Screen.HOME, Screen.SETUP), backStackOf(listOf("HOME", "RETIRED", "SETUP")))
    }

    @Test
    fun `a stack of unknown names restores to home`() {
        assertEquals(listOf(Screen.HOME), backStackOf(listOf("RETIRED")))
        assertEquals(listOf(Screen.HOME), backStackOf(emptyList()))
    }
}
