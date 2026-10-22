// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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

class CircleToSearchGestureTest {

    /** Same phone density as GestureTrackerTest: 8dp slop = 22px, 108dp handle = 297px. */
    private val density = 2.75f

    private fun sample(x: Float, y: Float, t: Long) = TouchSample(x, y, t)

    private fun tracker(
        edge: ScreenEdge = ScreenEdge.BOTTOM,
        enabled: Boolean = true,
        configuration: GestureConfiguration = GestureConfiguration(circleToSearchEnabled = enabled),
        handleCenterXPx: Float? = null,
    ) = GestureTracker(edge, configuration, density, handleCenterXPx)

    private val fired = TrackerAction.Fire(GestureAction.CircleToSearch, ScreenEdge.BOTTOM)

    @Test
    fun `a bottom DOWN waits the platform long-press timeout by default`() {
        assertEquals(
            TrackerAction.ScheduleLongPress(400L),
            tracker().onDown(sample(540f, 2395f, 0L)),
        )
    }

    @Test
    fun `nothing is scheduled when the feature is off`() {
        val t = tracker(enabled = false)
        assertEquals(TrackerAction.None, t.onDown(sample(540f, 2395f, 0L)))
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `side edges never schedule a long press`() {
        val t = tracker(edge = ScreenEdge.LEFT)
        assertEquals(TrackerAction.None, t.onDown(sample(5f, 1200f, 0L)))
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `a still finger fires Circle to Search when the timer elapses`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        // Jitter well inside the 22px touch slop.
        assertEquals(TrackerAction.None, t.onMove(sample(545f, 2390f, 200L)))
        assertEquals(fired, t.onLongPressElapsed())
    }

    @Test
    fun `the release after a long press neither fires Home nor asks for a replay`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        t.onLongPressElapsed()
        assertEquals(TrackerAction.None, t.onUp(sample(540f, 2395f, 900L)))
    }

    @Test
    fun `the long press fires only once`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        assertEquals(fired, t.onLongPressElapsed())
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `a quick tap is still released as unused so it reaches the app`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.NO_MOVEMENT),
            t.onUp(sample(540f, 2395f, 80L)),
        )
        // A timer that was not cancelled in time must not fire on the finished touch.
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `an upward swipe still fires Home and blocks the long press`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        assertTrue(t.onMove(sample(540f, 2295f, 50L)) is TrackerAction.Armed)
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 2295f, 70L)),
        )
    }

    @Test
    fun `a swipe that arms inside the slop still wins over the long press`() {
        val t = tracker(
            configuration = GestureConfiguration(
                circleToSearchEnabled = true,
                bottomMinimumSwipeDistanceDp = 4f,
            ),
        )
        t.onDown(sample(540f, 2395f, 0L))
        assertTrue(t.onMove(sample(540f, 2380f, 50L)) is TrackerAction.Armed)
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
        assertEquals(
            TrackerAction.Fire(GestureAction.Home, ScreenEdge.BOTTOM),
            t.onUp(sample(540f, 2380f, 70L)),
        )
    }

    @Test
    fun `swipe and hold still means Recents, not Circle to Search`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(540f, 2295f, 50L))
        assertEquals(
            TrackerAction.Fire(GestureAction.Recents, ScreenEdge.BOTTOM),
            t.onHoldElapsed(),
        )
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `a finger that strayed and came back does not fire`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        // 25px sideways is past the 22px slop but short of any swipe.
        assertEquals(TrackerAction.None, t.onMove(sample(565f, 2395f, 100L)))
        assertEquals(TrackerAction.None, t.onMove(sample(540f, 2395f, 200L)))
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `a cancelled stream does not fire`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        t.onCancel()
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `the timer follows a changed system delay`() {
        val configuration = GestureConfiguration(
            circleToSearchEnabled = true,
            platform = PlatformDefaults(longPressTimeoutMs = 1000L),
        )
        assertEquals(
            TrackerAction.ScheduleLongPress(1000L),
            tracker(configuration = configuration).onDown(sample(540f, 2395f, 0L)),
        )
    }

    @Test
    fun `a manual hold replaces the system delay only when system timing is off`() {
        val configuration = GestureConfiguration(
            circleToSearchEnabled = true,
            circleToSearchFollowsSystemTiming = false,
            circleToSearchHoldMs = 650L,
        )
        assertEquals(
            TrackerAction.ScheduleLongPress(650L),
            tracker(configuration = configuration).onDown(sample(540f, 2395f, 0L)),
        )
    }

    @Test
    fun `a long press over the handle fires`() {
        val t = tracker(handleCenterXPx = 540f)
        // 140px from the centre is inside the 148.5px half handle.
        assertTrue(t.onDown(sample(680f, 2395f, 0L)) is TrackerAction.ScheduleLongPress)
        assertEquals(fired, t.onLongPressElapsed())
    }

    @Test
    fun `a long press beside the handle is left to the app underneath`() {
        val t = tracker(handleCenterXPx = 540f)
        assertEquals(TrackerAction.None, t.onDown(sample(900f, 2395f, 0L)))
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
        assertEquals(
            TrackerAction.Unused(RejectionReason.NO_MOVEMENT),
            t.onUp(sample(900f, 2395f, 700L)),
        )
    }

    @Test
    fun `the handle never shrinks below the 48dp touch target`() {
        val configuration = GestureConfiguration(
            circleToSearchEnabled = true,
            platform = PlatformDefaults(handleWidthDp = 20f),
        )
        // 60px from the centre: outside a 20dp handle, inside the 66px half of 48dp.
        val t = tracker(configuration = configuration, handleCenterXPx = 540f)
        assertTrue(t.onDown(sample(600f, 2395f, 0L)) is TrackerAction.ScheduleLongPress)
    }

    @Test
    fun `slop is measured as a distance, not per axis`() {
        val t = tracker()
        t.onDown(sample(540f, 2395f, 0L))
        // 18px on each axis is under 22px either way, but 25.5px as a distance.
        assertEquals(TrackerAction.None, t.onMove(sample(558f, 2413f, 100L)))
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    private fun delayedTracker(longPressTimeoutMs: Long) = tracker(
        configuration = GestureConfiguration(
            circleToSearchEnabled = true,
            platform = PlatformDefaults(longPressTimeoutMs = longPressTimeoutMs),
        ),
    )

    @Test
    fun `a 1000 ms system delay still fires past the 1000 ms arm window`() {
        val t = delayedTracker(1000L)
        t.onDown(sample(540f, 2395f, 0L))
        assertEquals(TrackerAction.None, t.onMove(sample(545f, 2390f, 600L)))
        // A jitter after the window would end tracking if the long press did not hold it open.
        assertEquals(TrackerAction.None, t.onMove(sample(542f, 2393f, 1050L)))
        assertEquals(fired, t.onLongPressElapsed())
        assertEquals(TrackerAction.None, t.onUp(sample(542f, 2393f, 1300L)))
    }

    @Test
    fun `a 1500 ms system delay still fires`() {
        val t = delayedTracker(1500L)
        t.onDown(sample(540f, 2395f, 0L))
        assertEquals(TrackerAction.None, t.onMove(sample(545f, 2390f, 1100L)))
        assertEquals(TrackerAction.None, t.onMove(sample(540f, 2398f, 1450L)))
        assertEquals(fired, t.onLongPressElapsed())
    }

    @Test
    fun `a manual hold past the arm window still fires`() {
        val configuration = GestureConfiguration(
            circleToSearchEnabled = true,
            circleToSearchFollowsSystemTiming = false,
            circleToSearchHoldMs = 1500L,
        )
        val t = tracker(configuration = configuration)
        assertEquals(TrackerAction.ScheduleLongPress(1500L), t.onDown(sample(540f, 2395f, 0L)))
        assertEquals(TrackerAction.None, t.onMove(sample(545f, 2390f, 1200L)))
        assertEquals(fired, t.onLongPressElapsed())
    }

    @Test
    fun `a release before a long system delay is still handed to the app`() {
        val t = delayedTracker(1500L)
        t.onDown(sample(540f, 2395f, 0L))
        t.onMove(sample(545f, 2390f, 1100L))
        assertEquals(
            TrackerAction.Unused(RejectionReason.NO_MOVEMENT),
            t.onUp(sample(540f, 2395f, 1200L)),
        )
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `the arm window applies again once the finger strays`() {
        val t = delayedTracker(1500L)
        t.onDown(sample(540f, 2395f, 0L))
        // An upward swipe begun after the window is too late to arm, as without the feature.
        assertEquals(
            TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM),
            t.onMove(sample(540f, 2295f, 1200L)),
        )
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `a swipe begun inside the window still arms under a long system delay`() {
        val t = delayedTracker(1500L)
        t.onDown(sample(540f, 2395f, 0L))
        assertTrue(t.onMove(sample(540f, 2295f, 800L)) is TrackerAction.Armed)
        assertEquals(TrackerAction.None, t.onLongPressElapsed())
    }

    @Test
    fun `beside the handle the arm window still releases a still finger`() {
        val configuration = GestureConfiguration(
            circleToSearchEnabled = true,
            platform = PlatformDefaults(longPressTimeoutMs = 1500L),
        )
        val t = tracker(configuration = configuration, handleCenterXPx = 540f)
        assertEquals(TrackerAction.None, t.onDown(sample(900f, 2395f, 0L)))
        assertEquals(
            TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM),
            t.onMove(sample(902f, 2393f, 1100L)),
        )
    }
}
