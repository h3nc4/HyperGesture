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
    val leftEdgeBackEnabled: Boolean = true,
    val rightEdgeBackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
)

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

    data class Fire(val action: GestureAction, val edge: ScreenEdge) : TrackerAction

    data class Unused(val reason: RejectionReason) : TrackerAction
}
