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
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticGateTest {

    // Feeds a real tracker at density 3 through the gate and lists the actions that buzzed.
    private class Run(
        edge: ScreenEdge,
        configuration: GestureConfiguration = GestureConfiguration(),
    ) {
        private val tracker = GestureTracker(edge, configuration, density = 3f)
        private val gate = HapticGate()
        val ticks = mutableListOf<TrackerAction>()
        private var time = 0L

        private fun feed(action: TrackerAction): TrackerAction {
            if (gate.shouldTick(action)) ticks += action
            return action
        }

        fun down(x: Float, y: Float) {
            gate.reset()
            feed(tracker.onDown(TouchSample(x, y, time)))
        }

        fun move(x: Float, y: Float): TrackerAction {
            time += 20L
            return feed(tracker.onMove(TouchSample(x, y, time)))
        }

        fun hold(): TrackerAction = feed(tracker.onHoldElapsed())

        fun up(x: Float, y: Float): TrackerAction {
            time += 20L
            return feed(tracker.onUp(TouchSample(x, y, time)))
        }
    }

    @Test
    fun `a back swipe buzzes once, on arming`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
        assertTrue(run.ticks.single() is TrackerAction.Armed)
    }

    @Test
    fun `a back swipe pulled in and pushed out again buzzes on the re-arm too`() {
        val run = Run(ScreenEdge.RIGHT)
        run.down(1080f, 1200f)
        run.move(900f, 1200f)
        assertEquals(TrackerAction.Disarmed, run.move(1070f, 1200f))
        run.move(900f, 1200f)
        assertTrue(run.up(900f, 1200f) is TrackerAction.Fire)
        assertEquals(2, run.ticks.size)
        assertTrue(run.ticks.all { it is TrackerAction.Armed })
    }

    @Test
    fun `a disarmed swipe released past the trigger in one event buzzes on fire`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        assertEquals(TrackerAction.Disarmed, run.move(30f, 1200f))
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(2, run.ticks.size)
        assertTrue(run.ticks.last() is TrackerAction.Fire)
    }

    @Test
    fun `a reverted back swipe buzzes only for its arm`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        run.move(180f, 1200f)
        run.move(3f, 1200f)
        assertEquals(TrackerAction.Reverted, run.up(3f, 1200f))
        assertEquals(1, run.ticks.size)
    }

    @Test
    fun `home buzzes on fire, not on the way up`() {
        val run = Run(ScreenEdge.BOTTOM)
        run.down(540f, 2300f)
        assertTrue(run.move(540f, 2200f) is TrackerAction.Armed)
        assertTrue(run.ticks.isEmpty())
        assertTrue(run.up(540f, 2100f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
    }

    @Test
    fun `recents buzzes once, when the hold fires`() {
        val run = Run(ScreenEdge.BOTTOM)
        run.down(540f, 2300f)
        run.move(540f, 2200f)
        assertTrue(run.hold() is TrackerAction.Fire)
        run.up(540f, 2200f)
        assertEquals(1, run.ticks.size)
    }

    @Test
    fun `an app switch buzzes once, on arming`() {
        val run = Run(ScreenEdge.BOTTOM, GestureConfiguration(appSwitchEnabled = true))
        run.down(540f, 2300f)
        run.move(700f, 2300f)
        assertTrue(run.up(700f, 2300f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
        assertTrue(run.ticks.single() is TrackerAction.Armed)
    }

    @Test
    fun `a swipe batched into a down and an up still buzzes`() {
        val run = Run(ScreenEdge.LEFT)
        run.down(0f, 1200f)
        assertTrue(run.up(180f, 1200f) is TrackerAction.Fire)
        assertEquals(1, run.ticks.size)
    }

    @Test
    fun `each gesture buzzes on its own`() {
        val run = Run(ScreenEdge.LEFT)
        repeat(2) {
            run.down(0f, 1200f)
            run.move(180f, 1200f)
            run.up(180f, 1200f)
        }
        assertEquals(2, run.ticks.size)
    }
}
