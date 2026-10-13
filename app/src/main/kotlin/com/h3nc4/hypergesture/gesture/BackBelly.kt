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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

// The bulge HyperOS grows out of the edge during a back swipe. Xiaomi draws it from an 84 by
// 858 px xxhdpi bitmap, so every size here is that asset measured at 3 px per dp.
object BackBelly {
    const val WIDTH_DP = 28f
    const val HEIGHT_DP = 286f

    /** Xiaomi centres the belly 20 px above the finger. */
    const val ABOVE_FINGER_DP = 6.7f

    /** Xiaomi grows the belly over 360 px of drag, half a cosine from empty to full. */
    const val FULL_DRAG_DP = 120f

    /** Xiaomi skips the arrow while the belly is thinner than this. */
    const val ARROW_MINIMUM_SCALE = 0.1f

    const val ARROW_WIDTH_DP = 6.7f
    const val ARROW_HEIGHT_DP = 13f
    const val ARROW_STROKE_DP = 2.1f

    /** Fitted to every row of the asset, within 1 px of its 84. */
    private const val SIGMA = 0.16f

    private val endValue = bell(HALF)

    /** Fraction of [WIDTH_DP] the belly is drawn at after [dragDp] of inward travel. */
    fun scale(dragDp: Float): Float {
        val progress = dragDp.coerceIn(0f, FULL_DRAG_DP) / FULL_DRAG_DP
        return ((1 - cos(PI * progress)) / 2).toFloat()
    }

    /** Width as a fraction of the full width at [t], from 0 at the top to 1 at the bottom. */
    fun profile(t: Float): Float =
        ((bell(t.coerceIn(0f, 1f) - HALF) - endValue) / (1 - endValue)).coerceIn(0f, 1f)

    private fun bell(offset: Float): Float {
        val z = offset / SIGMA
        return exp(-z * z / 2)
    }

    private const val HALF = 0.5f
}
