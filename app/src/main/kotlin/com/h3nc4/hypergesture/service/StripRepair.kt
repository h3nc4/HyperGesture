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
import android.content.pm.ApplicationInfo
import android.provider.Settings
import com.h3nc4.hypergesture.gesture.ScreenEdge

private const val DETACH_ON_SCREEN_OFF = "hypergesture_detach_strip_on_screen_off"

internal data class StripWindow(
    val edge: ScreenEdge,
    val attached: Boolean,
    val widthPx: Int,
    val heightPx: Int,
) {
    val alive: Boolean get() = attached && widthPx > 0 && heightPx > 0
}

// Re-adds only the dead strips, since removing every live window for a full install blocks for up
// to a second while the screen turns on. A refused re-add falls back to the full install.
internal fun repairDeadStrips(
    strips: List<StripWindow>,
    reattach: (dead: List<StripWindow>) -> Boolean,
    reinstall: () -> Unit,
) {
    val dead = strips.filterNot(StripWindow::alive)
    if (dead.isNotEmpty() && !reattach(dead)) reinstall()
}

/** Test seam that names the strip the emulator suite drops at screen-off. Debug only. */
internal fun stripToDetachForTest(context: Context): ScreenEdge? {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
    return runCatching {
        Settings.Global.getString(context.contentResolver, DETACH_ON_SCREEN_OFF)?.let(ScreenEdge::valueOf)
    }.getOrNull()
}
