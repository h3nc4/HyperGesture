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
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
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
    val rotation: Int = Surface.ROTATION_0,
) {

    /** From the window bounds, which swap on every rotation the display goes through. */
    val landscape: Boolean get() = widthPx > heightPx

    /**
     * Window metrics can still hold the bounds from before a turn the display already made.
     * Those are swapped and their insets dropped, so the next signal reinstalls with fresh ones.
     */
    fun alignedTo(displayRotation: Int, naturalLandscape: Boolean): ScreenGeometry {
        val wantLandscape = (displayRotation % 2 == 1) != naturalLandscape
        if (wantLandscape == landscape || widthPx == heightPx) return copy(rotation = displayRotation)
        return ScreenGeometry(heightPx, widthPx, density, 0, 0, 0, displayRotation)
    }

    companion object {
        fun current(context: Context, windowManager: WindowManager): ScreenGeometry {
            val metrics = windowManager.currentWindowMetrics
            val nav = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
            val read = ScreenGeometry(
                widthPx = metrics.bounds.width(),
                heightPx = metrics.bounds.height(),
                density = context.resources.displayMetrics.density,
                navLeftPx = nav.left,
                navRightPx = nav.right,
                navBottomPx = nav.bottom,
            )
            val display = context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return read
            val mode = display.mode
            return read.alignedTo(display.rotation, mode.physicalWidth > mode.physicalHeight)
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
