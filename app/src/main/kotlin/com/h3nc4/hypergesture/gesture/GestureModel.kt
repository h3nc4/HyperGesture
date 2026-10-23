// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
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

/**
 * [id] exists because R8 obfuscates class names in release builds: `::class.simpleName`
 * reads as "a" / "b" / "c" once minified.
 */
sealed interface GestureAction {
    val id: String

    data object Back : GestureAction {
        override val id: String = "Back"
    }

    data object Home : GestureAction {
        override val id: String = "Home"
    }

    data object Recents : GestureAction {
        override val id: String = "Recents"
    }

    /** One step back through the apps the user came from. */
    data object PreviousApp : GestureAction {
        override val id: String = "PreviousApp"
    }

    /** One step forward again, undoing a [PreviousApp]. */
    data object NextApp : GestureAction {
        override val id: String = "NextApp"
    }

    /** Google's Circle to Search, as a long press on the system gesture handle starts it. */
    data object CircleToSearch : GestureAction {
        override val id: String = "CircleToSearch"
    }
}

enum class ScreenEdge { LEFT, RIGHT, BOTTOM }

data class TouchSample(val xPx: Float, val yPx: Float, val timestampMs: Long)

data class GestureConfiguration(
    val edgeWidthDp: Float = 20f,
    val sideMinimumSwipeDistanceDp: Float = 24f,
    /**
     * Lower than the sides on purpose: a swipe starting on the navigation bar only reaches
     * our strip once the bar hands it off, so part of the travel is already spent.
     */
    val bottomMinimumSwipeDistanceDp: Float = 10f,
    /**
     * Gates only the phase *before* arming; once armed a gesture may be held indefinitely,
     * or "swipe up and hold" for Recents would time out and be discarded.
     */
    val maximumArmDurationMs: Long = 1000L,
    val recentsHoldMs: Long = 100L,
    val holdStillnessDp: Float = 12f,
    /** Multiple of the on-axis travel; 1.0 is a 45-degree cone. */
    val offAxisToleranceRatio: Float = 1.0f,
    /** Off by default: switching apps needs usage access the user grants by hand. */
    val appSwitchEnabled: Boolean = false,
    val leftEdgeBackEnabled: Boolean = true,
    val rightEdgeBackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    /** On by default, as HyperOS asks for a second bottom swipe in a landscape app. */
    val landscapeConfirmEnabled: Boolean = true,
    /** Off by default: it takes a still finger on the bottom edge away from the app underneath. */
    val circleToSearchEnabled: Boolean = false,
    /** On by default, as the system's own handle waits for the "touch and hold delay". */
    val circleToSearchFollowsSystemTiming: Boolean = true,
    /** Used only when [circleToSearchFollowsSystemTiming] is off. */
    val circleToSearchHoldMs: Long = PlatformDefaults.LONG_PRESS_TIMEOUT_MS,
    /** Read from the device at runtime and never persisted. */
    val platform: PlatformDefaults = PlatformDefaults(),
) {
    val effectiveCircleToSearchHoldMs: Long
        get() = if (circleToSearchFollowsSystemTiming) {
            platform.longPressTimeoutMs
        } else {
            circleToSearchHoldMs
        }
}

/** Launcher3's numbers for a long press on the navigation handle, at AOSP's defaults. */
data class PlatformDefaults(
    /** ViewConfiguration.getLongPressTimeout(), 400 ms on AOSP since Android 12. */
    val longPressTimeoutMs: Long = LONG_PRESS_TIMEOUT_MS,
    /** ViewConfiguration.getScaledTouchSlop() in dp. */
    val touchSlopDp: Float = 8f,
    /** SystemUI navigation_home_handle_width, the part of the edge a long press counts on. */
    val handleWidthDp: Float = 108f,
) {
    companion object {
        const val LONG_PRESS_TIMEOUT_MS = 400L

        /** Launcher3 never lets the handle's long-press target shrink below this. */
        const val MIN_TOUCH_TARGET_DP = 48f
    }
}

enum class RejectionReason {
    NO_MOVEMENT,
    INSUFFICIENT_MOVEMENT,
    WRONG_DIRECTION,
    TOO_SLOW_TO_ARM,
    OFF_AXIS,
}

sealed interface TrackerAction {
    data object None : TrackerAction

    data class Armed(val scheduleHoldMs: Long?) : TrackerAction

    data class RearmHold(val delayMs: Long) : TrackerAction

    /** Starts the long-press timer, whose expiry is reported through `onLongPressElapsed`. */
    data class ScheduleLongPress(val delayMs: Long) : TrackerAction

    data class Fire(val action: GestureAction, val edge: ScreenEdge) : TrackerAction

    /** An armed side swipe pulled back inside its trigger distance, with the finger still down. */
    data object Disarmed : TrackerAction

    /** A side swipe released inside its trigger distance after arming, so nothing fires or replays. */
    data object Reverted : TrackerAction

    data class Unused(val reason: RejectionReason) : TrackerAction
}
