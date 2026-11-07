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
import org.junit.Test

class GestureSettingsResetTest {

    private val defaults = GestureConfiguration()

    private val customised = GestureConfiguration(
        edgeWidthDp = 40f,
        sideMinimumSwipeDistanceDp = 50f,
        bottomMinimumSwipeDistanceDp = 30f,
        maximumArmDurationMs = 2000L,
        recentsHoldMs = 300L,
        holdStillnessDp = 20f,
        offAxisToleranceRatio = 0.5f,
        appSwitchEnabled = true,
        leftEdgeBackEnabled = false,
        rightEdgeBackEnabled = false,
        hapticFeedbackEnabled = false,
        landscapeConfirmEnabled = false,
        circleToSearchEnabled = true,
        circleToSearchFollowsSystemTiming = false,
        circleToSearchHoldMs = 900L,
    )

    @Test
    fun `a reset restores the gesture screen's values and keeps app switching and Circle to Search`() {
        val expected = defaults.copy(
            holdStillnessDp = 20f,
            offAxisToleranceRatio = 0.5f,
            appSwitchEnabled = true,
            circleToSearchEnabled = true,
            circleToSearchFollowsSystemTiming = false,
            circleToSearchHoldMs = 900L,
        )

        assertEquals(expected, customised.withGestureSettingsOf(defaults))
    }

    @Test
    fun `undo after a reset gives back every previous value`() {
        val reset = customised.withGestureSettingsOf(defaults)

        assertEquals(customised, reset.withGestureSettingsOf(customised))
    }
}
