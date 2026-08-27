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

class GestureTrackerTest {

    /** 24dp = 66px, 10dp = 27.5px, 12dp = 33px. */
    private val phoneDensity = 2.75f

    private val tabletDensity = 3.5f

    private fun tracker(
        edge: ScreenEdge,
        density: Float = phoneDensity,
        configuration: GestureConfiguration = GestureConfiguration(),
    ) = GestureTracker(edge, configuration, density)

    private fun sample(x: Float, y: Float, t: Long) = TouchSample(x, y, t)

    @Test
    fun `inward swipe from the left edge fires Back on release`() {
        val t = tracker(ScreenEdge.LEFT)
        assertEquals(TrackerAction.None, t.onDown(sample(5f, 1200f, 0L)))
        // 100px is 36dp, past the 24dp side threshold.
        val armed = t.onMove(sample(105f, 1200f, 60L))
        assertTrue("expected arming, got $armed", armed is TrackerAction.Armed)
        assertEquals(null, (armed as TrackerAction.Armed).scheduleHoldMs)
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(105f, 1200f, 80L)),
        )
    }

    @Test
    fun `inward swipe from the right edge fires Back on release`() {
        val t = tracker(ScreenEdge.RIGHT)
        t.onDown(sample(1075f, 1200f, 0L))
        assertTrue(t.onMove(sample(975f, 1200f, 60L)) is TrackerAction.Armed)
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.RIGHT),
            t.onUp(sample(975f, 1200f, 80L)),
        )
    }

    @Test
    fun `a side hold does nothing because side edges have no hold action`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(105f, 1200f, 60L))
        assertEquals(TrackerAction.None, t.onHoldElapsed())
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(105f, 1200f, 80L)),
        )
    }

    @Test
    fun `upward swipe released while armed fires Home`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        val armed = t.onMove(sample(540f, 2295f, 50L))
        assertTrue(armed is TrackerAction.Armed)
        assertEquals(100L, (armed as TrackerAction.Armed).scheduleHoldMs)
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 2295f, 70L)),
        )
    }

    @Test
    fun `holding still after arming fires Recents immediately, mid-gesture`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onHoldElapsed(),
        )
    }

    @Test
    fun `once Recents has fired the release does not also fire Home`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        t.onHoldElapsed()
        assertEquals(TrackerAction.None, t.onUp(sample(540f, 2295f, 900L)))
    }

    @Test
    fun `Recents fires only once even if the hold timer elapses twice`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        assertTrue(t.onHoldElapsed() is TrackerAction.Fire)
        assertEquals(TrackerAction.None, t.onHoldElapsed())
    }

    @Test
    fun `moving after arming restarts the hold timer instead of cancelling it`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        // 40px is past the 33px stillness allowance.
        val rearm = t.onMove(sample(540f, 2255f, 90L))
        assertEquals(TrackerAction.RearmHold(100L), rearm)
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onHoldElapsed(),
        )
    }

    @Test
    fun `small jitter while holding does not restart the hold timer`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        // 5px per axis is well inside the 33px stillness allowance.
        assertEquals(TrackerAction.None, t.onMove(sample(545f, 2290f, 70L)))
    }

    @Test
    fun `a long hold after arming is still a gesture, not a timeout`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        // 5s is far past the 1000ms maximumArmDurationMs.
        assertTrue(t.onMove(sample(540f, 2200f, 5_000L)) is TrackerAction.RearmHold)
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onHoldElapsed(),
        )
    }

    @Test
    fun `a tap is unused so the caller can replay it`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        val result = t.onUp(sample(540f, 2395f, 40L))
        assertEquals(TrackerAction.Unused(RejectionReason.NO_MOVEMENT), result)
    }

    @Test
    fun `too short a swipe is unused`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        t.onMove(sample(25f, 1200f, 40L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.INSUFFICIENT_MOVEMENT),
            t.onUp(sample(25f, 1200f, 60L)),
        )
    }

    @Test
    fun `swiping outward from an edge is unused as the wrong direction`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(50f, 1200f, 0L))
        t.onMove(sample(10f, 1200f, 40L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.WRONG_DIRECTION),
            t.onUp(sample(10f, 1200f, 60L)),
        )
    }

    @Test
    fun `swiping downward from the bottom edge is unused as the wrong direction`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2300f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.WRONG_DIRECTION),
            t.onUp(sample(540f, 2360f, 60L)),
        )
    }

    @Test
    fun `a diagonal swipe outside the tolerance cone does not arm`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        // 100px right, 200px down: outside the 45-degree cone.
        assertEquals(TrackerAction.None, t.onMove(sample(105f, 1400f, 60L)))
        assertEquals(
            TrackerAction.Unused(RejectionReason.OFF_AXIS),
            t.onUp(sample(105f, 1400f, 80L)),
        )
    }

    @Test
    fun `a swipe inside the tolerance cone still arms`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        // 100px right, 80px down: inside the 45-degree cone.
        assertTrue(t.onMove(sample(105f, 1280f, 60L)) is TrackerAction.Armed)
    }

    @Test
    fun `taking too long to arm reports a timeout and stops tracking`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(5f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM),
            t.onMove(sample(15f, 1200f, 1_500L)),
        )
        assertEquals(TrackerAction.None, t.onMove(sample(200f, 1200f, 1_600L)))
        assertTrue(t.onUp(sample(200f, 1200f, 1_700L)) is TrackerAction.Unused)
    }

    @Test
    fun `a cancelled stream produces nothing on a later hold`() {
        val t = tracker(ScreenEdge.BOTTOM)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        t.onCancel()
        assertEquals(TrackerAction.None, t.onHoldElapsed())
    }

    @Test
    fun `arming distance scales with density rather than pixels`() {
        // 80px is 29dp at 2.75x (arms, past 24dp) but only 23dp at 3.5x (does not).
        val onPhone = tracker(ScreenEdge.LEFT, phoneDensity)
        onPhone.onDown(sample(5f, 1200f, 0L))
        assertTrue(onPhone.onMove(sample(85f, 1200f, 60L)) is TrackerAction.Armed)

        val onTablet = tracker(ScreenEdge.LEFT, tabletDensity)
        onTablet.onDown(sample(5f, 1200f, 0L))
        assertEquals(TrackerAction.None, onTablet.onMove(sample(85f, 1200f, 60L)))
    }

    @Test
    fun `the bottom edge arms sooner than the sides by design`() {
        // 40px is 14dp: past the bottom's 10dp threshold, short of the sides' 24dp.
        val bottom = tracker(ScreenEdge.BOTTOM)
        bottom.onDown(sample(540f, 2395f, 0L))
        assertTrue(bottom.onMove(sample(540f, 2355f, 40L)) is TrackerAction.Armed)

        val side = tracker(ScreenEdge.LEFT)
        side.onDown(sample(5f, 1200f, 0L))
        assertEquals(TrackerAction.None, side.onMove(sample(45f, 1200f, 40L)))
    }

    @Test
    fun `a custom hold duration is reported back to the caller`() {
        val configuration = GestureConfiguration(recentsHoldMs = 250L)
        val t = tracker(ScreenEdge.BOTTOM, configuration = configuration)
        t.onDown(sample(540f, 2395f, 0L))
        val armed = t.onMove(sample(540f, 2295f, 50L)) as TrackerAction.Armed
        assertEquals(250L, armed.scheduleHoldMs)
    }

    @Test
    fun `a larger minimum distance delays arming`() {
        val configuration = GestureConfiguration(sideMinimumSwipeDistanceDp = 60f)
        val t = tracker(ScreenEdge.LEFT, configuration = configuration)
        t.onDown(sample(5f, 1200f, 0L))
        // 100px is 36dp: enough by default, not enough at 60dp.
        assertEquals(TrackerAction.None, t.onMove(sample(105f, 1200f, 60L)))
        assertTrue(t.onMove(sample(180f, 1200f, 90L)) is TrackerAction.Armed)
    }

    /**
     * Event batching can deliver a stream as DOWN then UP with no MOVE at all, which the
     * emulator does under load. The release alone still has to count as a swipe.
     */
    @Test
    fun `a swipe delivered as down then up with no move still fires`() {
        val t = tracker(ScreenEdge.LEFT)
        assertEquals(TrackerAction.None, t.onDown(sample(2f, 1200f, 0L)))
        assertEquals(
            TrackerAction.Fire(GestureAction.Back, ScreenEdge.LEFT),
            t.onUp(sample(300f, 1200f, 120L)),
        )
    }

    @Test
    fun `a down then up pair that is too slow to arm is still unused`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(2f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM),
            t.onUp(sample(300f, 1200f, 5_000L)),
        )
    }

    @Test
    fun `a down then up pair that is too short is still unused`() {
        val t = tracker(ScreenEdge.LEFT)
        t.onDown(sample(2f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.INSUFFICIENT_MOVEMENT),
            t.onUp(sample(20f, 1200f, 60L)),
        )
    }

    @Test
    fun `a down then up pair going the wrong way is still unused`() {
        val t = tracker(ScreenEdge.RIGHT, density = phoneDensity)
        t.onDown(sample(1070f, 1200f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.WRONG_DIRECTION),
            t.onUp(sample(1078f, 1200f, 60L)),
        )
    }
}
