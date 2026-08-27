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

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Read from `currentWindowMetrics`, never `resources.displayMetrics`: the latter can
 * report only the app-usable area, while the `rawX/rawY` the recognizer compares against
 * are full-display coordinates, and mixing the two breaks bottom-edge detection.
 */
internal data class ScreenGeometry(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val navLeftPx: Int,
    val navRightPx: Int,
    val navBottomPx: Int,
) {

    companion object {
        fun current(context: Context, windowManager: WindowManager): ScreenGeometry {
            val metrics = windowManager.currentWindowMetrics
            val nav = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
            return ScreenGeometry(
                widthPx = metrics.bounds.width(),
                heightPx = metrics.bounds.height(),
                density = context.resources.displayMetrics.density,
                navLeftPx = nav.left,
                navRightPx = nav.right,
                navBottomPx = nav.bottom,
            )
        }
    }
}

/**
 * Strips stop short of each edge's full length so the status bar, app bar controls and
 * the bottom corners stay natively touchable.
 */
internal data class OverlayLayout(
    val sideLengthPercent: Int = 80,
    val bottomLengthPercent: Int = 90,
)
