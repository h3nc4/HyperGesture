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

import kotlin.math.abs

/**
 * Tracks one finger on one screen edge. Contains no Android types on purpose: that is the
 * whole reason gesture recognition can be tested on a plain JVM.
 *
 * [GestureConfiguration.maximumArmDurationMs] gates only the pre-arm phase; once armed a
 * gesture may be held indefinitely, or swipe-and-hold for Recents would be discarded.
 *
 * Recents fires mid-gesture, the moment the hold elapses: deciding on release instead
 * makes a merely slow Home swipe register as Recents, with no feedback about which.
 *
 * There is deliberately no minimum-velocity threshold, which would penalise the slow,
 * deliberate swipes a careful user makes.
 */
class GestureTracker(
    private val edge: ScreenEdge,
    private val configuration: GestureConfiguration,
    private val density: Float,
) {

    private var start: TouchSample? = null
    private var anchor: TouchSample? = null
    private var tracking = false
    private var armed = false
    private var holdFired = false

    // Set when the armed gesture was a sideways one, which fires instead of [shortAction]
    // and schedules no hold.
    private var armedSideways: GestureAction? = null

    private val shortAction: GestureAction =
        if (edge == ScreenEdge.BOTTOM) GestureAction.Home else GestureAction.Back

    private val holdAction: GestureAction? =
        if (edge == ScreenEdge.BOTTOM) GestureAction.Recents else null

    private val minimumDistancePx: Float
        get() = density * when (edge) {
            ScreenEdge.BOTTOM -> configuration.bottomMinimumSwipeDistanceDp
            ScreenEdge.LEFT, ScreenEdge.RIGHT -> configuration.sideMinimumSwipeDistanceDp
        }

    private val stillnessPx: Float get() = configuration.holdStillnessDp * density

    fun onDown(sample: TouchSample): TrackerAction {
        start = sample
        anchor = sample
        tracking = true
        armed = false
        holdFired = false
        armedSideways = null
        return TrackerAction.None
    }

    fun onMove(sample: TouchSample): TrackerAction {
        val origin = start ?: return TrackerAction.None
        if (!tracking) return TrackerAction.None

        if (!armed) {
            if (sample.timestampMs - origin.timestampMs > configuration.maximumArmDurationMs) {
                tracking = false
                return TrackerAction.Unused(RejectionReason.TOO_SLOW_TO_ARM)
            }

            val sideways = sidewaysAction(origin, sample)
            if (sideways != null) {
                armed = true
                armedSideways = sideways
                anchor = sample
                return TrackerAction.Armed(scheduleHoldMs = null)
            }
            if (!hasArmed(origin, sample)) return TrackerAction.None

            armed = true
            anchor = sample
            return TrackerAction.Armed(
                scheduleHoldMs = if (holdAction != null) configuration.recentsHoldMs else null,
            )
        }

        // An upward drift can arm Home before the sideways travel accumulates, so a gesture
        // may still become a sideways one until the hold fires.
        if (!holdFired && armedSideways == null) {
            val late = sidewaysAction(origin, sample)
            if (late != null) {
                armedSideways = late
                anchor = sample
                return TrackerAction.Armed(scheduleHoldMs = null)
            }
        }

        if (holdFired || holdAction == null || armedSideways != null) return TrackerAction.None

        // The hold may come anywhere along the swipe, so movement re-anchors, not cancels.
        val reference = anchor ?: return TrackerAction.None
        val moved = abs(sample.xPx - reference.xPx) > stillnessPx ||
            abs(sample.yPx - reference.yPx) > stillnessPx
        if (!moved) return TrackerAction.None

        anchor = sample
        return TrackerAction.RearmHold(configuration.recentsHoldMs)
    }

    fun onHoldElapsed(): TrackerAction {
        val action = holdAction ?: return TrackerAction.None
        if (!tracking || !armed || holdFired || armedSideways != null) return TrackerAction.None
        holdFired = true
        return TrackerAction.Fire(action, edge)
    }

    fun onUp(sample: TouchSample): TrackerAction {
        val origin = start
        val wasTracking = tracking
        val wasArmed = armed
        val alreadyFired = holdFired
        val sideways = armedSideways
        tracking = false

        if (alreadyFired) return TrackerAction.None
        if (wasTracking && wasArmed) return TrackerAction.Fire(sideways ?: shortAction, edge)

        // A stream can arrive as DOWN then UP with no MOVE between - event batching under
        // load does this, and a slow emulator does it reliably. The release is still a
        // swipe if it clears the threshold on its own, so judge it by the same rule.
        if (wasTracking && origin != null &&
            sample.timestampMs - origin.timestampMs <= configuration.maximumArmDurationMs
        ) {
            val late = sidewaysAction(origin, sample)
            if (late != null) return TrackerAction.Fire(late, edge)
            if (hasArmed(origin, sample)) return TrackerAction.Fire(shortAction, edge)
        }
        return TrackerAction.Unused(rejectionFor(origin, sample))
    }

    fun onCancel() {
        tracking = false
        armed = false
        holdFired = false
        armedSideways = null
        start = null
        anchor = null
    }

    // A swipe along the bottom edge rather than up from it. Only the bottom edge offers this:
    // on a side edge the sideways axis is the one that already means Back.
    private fun sidewaysAction(origin: TouchSample, sample: TouchSample): GestureAction? {
        if (edge != ScreenEdge.BOTTOM || !configuration.appSwitchEnabled) return null
        val sideways = sample.xPx - origin.xPx
        if (abs(sideways) < minimumDistancePx) return null
        // Held to the same cone as every other gesture, measured the other way round.
        if (abs(sample.yPx - origin.yPx) > abs(sideways) * configuration.offAxisToleranceRatio) {
            return null
        }
        return if (sideways > 0f) GestureAction.PreviousApp else GestureAction.NextApp
    }

    private fun hasArmed(origin: TouchSample, sample: TouchSample): Boolean {
        val travelPx = travelFrom(origin, sample)
        if (travelPx < minimumDistancePx) return false
        val offAxisPx = offAxisFrom(origin, sample)
        return abs(offAxisPx) <= travelPx * configuration.offAxisToleranceRatio
    }

    /** Positive inward, negative outward. */
    private fun travelFrom(origin: TouchSample, sample: TouchSample): Float = when (edge) {
        ScreenEdge.LEFT -> sample.xPx - origin.xPx
        ScreenEdge.RIGHT -> origin.xPx - sample.xPx
        ScreenEdge.BOTTOM -> origin.yPx - sample.yPx
    }

    private fun offAxisFrom(origin: TouchSample, sample: TouchSample): Float = when (edge) {
        ScreenEdge.LEFT, ScreenEdge.RIGHT -> sample.yPx - origin.yPx
        ScreenEdge.BOTTOM -> sample.xPx - origin.xPx
    }

    private fun rejectionFor(origin: TouchSample?, sample: TouchSample): RejectionReason {
        if (origin == null) return RejectionReason.NO_MOVEMENT
        val travelPx = travelFrom(origin, sample)
        val offAxisPx = abs(offAxisFrom(origin, sample))
        val movedAtAll = abs(travelPx) > 0f || offAxisPx > 0f
        return when {
            !movedAtAll -> RejectionReason.NO_MOVEMENT
            travelPx < 0f -> RejectionReason.WRONG_DIRECTION
            travelPx < minimumDistancePx -> RejectionReason.INSUFFICIENT_MOVEMENT
            offAxisPx > travelPx * configuration.offAxisToleranceRatio -> RejectionReason.OFF_AXIS
            sample.timestampMs - origin.timestampMs > configuration.maximumArmDurationMs ->
                RejectionReason.TOO_SLOW_TO_ARM
            else -> RejectionReason.INSUFFICIENT_MOVEMENT
        }
    }
}
