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

package com.h3nc4.hypergesture.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeConfirmTest {

    // A real bottom tracker at density 3 on a 2400 by 1080 landscape screen, fed through the
    // model the way the overlay does, recording what reached the system.
    private class Screen(
        configuration: GestureConfiguration = GestureConfiguration(),
        landscape: Boolean = true,
    ) {
        private val tracker = GestureTracker(ScreenEdge.BOTTOM, configuration, density = 3f)
        val confirm = LandscapeConfirm()
        val performed = mutableListOf<GestureAction>()
        val pills = mutableListOf<GestureAction>()
        var time = 0L

        init {
            confirm.configure(configuration.landscapeConfirmEnabled, landscape)
        }

        private fun feed(action: TrackerAction) {
            if (action !is TrackerAction.Fire) return
            when (confirm.onFire(action.action, action.edge, time)) {
                is ConfirmVerdict.Perform -> performed += action.action
                is ConfirmVerdict.ShowPill -> pills += action.action
            }
        }

        private fun down(x: Float) {
            confirm.onDown(ScreenEdge.BOTTOM, time)
            feed(tracker.onDown(TouchSample(x, BOTTOM_Y, time)))
        }

        private fun move(x: Float, y: Float) {
            time += STEP_MS
            feed(tracker.onMove(TouchSample(x, y, time)))
        }

        private fun up(x: Float, y: Float) {
            time += STEP_MS
            feed(tracker.onUp(TouchSample(x, y, time)))
            confirm.onStreamEnd(ScreenEdge.BOTTOM, time)
        }

        fun swipeUp() {
            down(CENTRE_X)
            move(CENTRE_X, BOTTOM_Y - 150f)
            up(CENTRE_X, BOTTOM_Y - 150f)
        }

        fun swipeUpAndHold() {
            down(CENTRE_X)
            move(CENTRE_X, BOTTOM_Y - 150f)
            time += 100L
            feed(tracker.onHoldElapsed())
            up(CENTRE_X, BOTTOM_Y - 150f)
        }

        fun swipeRight() {
            down(CENTRE_X)
            move(CENTRE_X + 300f, BOTTOM_Y)
            up(CENTRE_X + 300f, BOTTOM_Y)
        }

        fun pause(ms: Long) {
            time += ms
            confirm.expire(time)
        }
    }

    @Test
    fun `a first swipe up in landscape shows the pill and goes nowhere`() {
        val screen = Screen()
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.pills)
        assertTrue(screen.performed.isEmpty())
        assertTrue(screen.confirm.pillShown)
    }

    @Test
    fun `a second swipe inside the window goes Home and drops the pill`() {
        val screen = Screen()
        screen.swipeUp()
        screen.pause(1_000L)
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.performed)
        assertFalse(screen.confirm.pillShown)
    }

    @Test
    fun `a second swipe after the window shows the pill again`() {
        val screen = Screen()
        screen.swipeUp()
        screen.pause(ConfirmPill.WINDOW_MS)
        assertFalse(screen.confirm.pillShown)
        screen.swipeUp()
        assertTrue(screen.performed.isEmpty())
        assertEquals(2, screen.pills.size)
    }

    @Test
    fun `a swipe that starts inside the window counts even if it ends after it`() {
        val screen = Screen()
        screen.swipeUp()
        screen.time += ConfirmPill.WINDOW_MS - 10L
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.performed)
    }

    @Test
    fun `a swipe and hold after the pill goes to Recents`() {
        val screen = Screen()
        screen.swipeUp()
        screen.pause(500L)
        screen.swipeUpAndHold()
        assertEquals(listOf(GestureAction.Recents), screen.performed)
    }

    @Test
    fun `a first swipe and hold is held back like a swipe`() {
        val screen = Screen()
        screen.swipeUpAndHold()
        assertEquals(listOf(GestureAction.Recents), screen.pills)
        assertTrue(screen.performed.isEmpty())
    }

    @Test
    fun `portrait goes Home on the first swipe`() {
        val screen = Screen(landscape = false)
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.performed)
        assertTrue(screen.pills.isEmpty())
    }

    @Test
    fun `with the setting off landscape goes Home on the first swipe`() {
        val screen = Screen(GestureConfiguration(landscapeConfirmEnabled = false))
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.performed)
        assertTrue(screen.pills.isEmpty())
    }

    @Test
    fun `turning the setting off drops a pill already up`() {
        val screen = Screen()
        screen.swipeUp()
        screen.confirm.configure(enabled = false, landscape = true)
        assertFalse(screen.confirm.pillShown)
    }

    @Test
    fun `rotating to portrait between swipes drops the pill and the next swipe goes Home`() {
        val screen = Screen()
        screen.swipeUp()
        screen.confirm.configure(enabled = true, landscape = false)
        assertFalse(screen.confirm.pillShown)
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.performed)
    }

    @Test
    fun `rotating into landscape between swipes asks again`() {
        val screen = Screen(landscape = false)
        screen.swipeUp()
        screen.confirm.configure(enabled = true, landscape = true)
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.performed)
        assertEquals(listOf(GestureAction.Home), screen.pills)
    }

    @Test
    fun `a rebuild in the same orientation keeps the pill`() {
        val screen = Screen()
        screen.swipeUp()
        screen.confirm.configure(enabled = true, landscape = true)
        assertTrue(screen.confirm.pillShown)
        screen.swipeUp()
        assertEquals(listOf(GestureAction.Home), screen.performed)
    }

    @Test
    fun `a sideways app switch asks for the same confirmation`() {
        val screen = Screen(GestureConfiguration(appSwitchEnabled = true))
        screen.swipeRight()
        assertEquals(listOf(GestureAction.PreviousApp), screen.pills)
        screen.swipeRight()
        assertEquals(listOf(GestureAction.PreviousApp), screen.performed)
    }

    @Test
    fun `a tap handed to the app drops the pill`() {
        val screen = Screen()
        screen.swipeUp()
        screen.confirm.onReplayed(ScreenEdge.BOTTOM)
        assertFalse(screen.confirm.pillShown)
    }

    @Test
    fun `a touch handed to the app from a side edge keeps the pill`() {
        val screen = Screen()
        screen.swipeUp()
        screen.confirm.onReplayed(ScreenEdge.LEFT)
        assertTrue(screen.confirm.pillShown)
    }

    @Test
    fun `a confirming touch still down keeps the pill past its expiry`() {
        val confirm = LandscapeConfirm().apply { configure(enabled = true, landscape = true) }
        confirm.onFire(GestureAction.Home, ScreenEdge.BOTTOM, 0L)
        confirm.onDown(ScreenEdge.BOTTOM, 100L)
        confirm.expire(ConfirmPill.WINDOW_MS + 500L)
        assertTrue(confirm.pillShown)
        assertEquals(
            ConfirmVerdict.Perform,
            confirm.onFire(GestureAction.Home, ScreenEdge.BOTTOM, ConfirmPill.WINDOW_MS + 600L),
        )
    }

    @Test
    fun `Back and Circle to Search are never held back`() {
        val confirm = LandscapeConfirm().apply { configure(enabled = true, landscape = true) }
        assertEquals(ConfirmVerdict.Perform, confirm.onFire(GestureAction.Back, ScreenEdge.LEFT, 0L))
        assertEquals(ConfirmVerdict.Perform, confirm.onFire(GestureAction.Back, ScreenEdge.RIGHT, 0L))
        assertEquals(
            ConfirmVerdict.Perform,
            confirm.onFire(GestureAction.CircleToSearch, ScreenEdge.BOTTOM, 0L),
        )
        assertFalse(confirm.pillShown)
    }

    @Test
    fun `a touch on a side edge does not confirm a bottom swipe`() {
        val confirm = LandscapeConfirm().apply { configure(enabled = true, landscape = true) }
        confirm.onFire(GestureAction.Home, ScreenEdge.BOTTOM, 0L)
        confirm.onDown(ScreenEdge.LEFT, 100L)
        confirm.onStreamEnd(ScreenEdge.LEFT, 200L)
        assertTrue(confirm.pillShown)
        confirm.onDown(ScreenEdge.BOTTOM, 300L)
        assertEquals(ConfirmVerdict.Perform, confirm.onFire(GestureAction.Home, ScreenEdge.BOTTOM, 400L))
    }

    @Test
    fun `the pill expires two seconds after it showed`() {
        val confirm = LandscapeConfirm().apply { configure(enabled = true, landscape = true) }
        assertEquals(
            ConfirmVerdict.ShowPill(1_000L + ConfirmPill.WINDOW_MS),
            confirm.onFire(GestureAction.Home, ScreenEdge.BOTTOM, 1_000L),
        )
        confirm.expire(1_000L + ConfirmPill.WINDOW_MS - 1L)
        assertTrue(confirm.pillShown)
        confirm.expire(1_000L + ConfirmPill.WINDOW_MS)
        assertFalse(confirm.pillShown)
    }

    private companion object {
        const val CENTRE_X = 1200f
        const val BOTTOM_Y = 1075f
        const val STEP_MS = 20L
    }
}
