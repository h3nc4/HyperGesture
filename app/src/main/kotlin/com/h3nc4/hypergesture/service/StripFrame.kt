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

package com.h3nc4.hypergesture.service

import android.view.Gravity
import com.h3nc4.hypergesture.gesture.ScreenEdge

/** A strip window's size and placement, with [y] measured down from the display top. */
internal data class StripFrame(val widthPx: Int, val heightPx: Int, val gravity: Int, val y: Int = 0)

// Each strip spans its edge's navigation-bar inset, as the bar passes a swipe on only once it leaves.
// None covers a shown keyboard, where a held key would wait for its release and replay.
internal fun stripFrame(
    edge: ScreenEdge,
    thicknessPx: Int,
    geometry: ScreenGeometry,
    layout: OverlayLayout,
    keyboardTopPx: Int?,
): StripFrame {
    val sideLengthPx = (geometry.heightPx * layout.sideLengthPercent / 100).coerceAtLeast(1)
    return when (edge) {
        ScreenEdge.LEFT, ScreenEdge.RIGHT -> {
            val widthPx = thicknessPx + if (edge == ScreenEdge.LEFT) geometry.navLeftPx else geometry.navRightPx
            val horizontal = if (edge == ScreenEdge.LEFT) Gravity.START else Gravity.END
            val topPx = (geometry.heightPx - sideLengthPx) / 2
            if (keyboardTopPx == null || keyboardTopPx >= topPx + sideLengthPx) {
                StripFrame(widthPx, sideLengthPx, horizontal or Gravity.CENTER_VERTICAL)
            } else {
                StripFrame(widthPx, (keyboardTopPx - topPx).coerceAtLeast(1), horizontal or Gravity.TOP, topPx)
            }
        }
        ScreenEdge.BOTTOM -> StripFrame(
            (geometry.widthPx * layout.bottomLengthPercent / 100).coerceAtLeast(1),
            if (keyboardTopPx == null) thicknessPx + geometry.navBottomPx else geometry.navBottomPx.coerceAtLeast(1),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
    }
}

/** Where the keyboard starts, in display rows, or null while none is shown. */
internal fun keyboardTop(visible: Boolean, bottomInsetPx: Int, heightPx: Int): Int? =
    if (visible && bottomInsetPx > 0) (heightPx - bottomInsetPx).coerceAtLeast(0) else null
