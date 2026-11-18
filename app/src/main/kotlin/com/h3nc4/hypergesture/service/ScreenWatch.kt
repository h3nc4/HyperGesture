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

import android.view.Display

// Fed from the display listener, since the SCREEN_ON broadcast can arrive seconds after the panel lights.
internal class ScreenWatch(
    private val onScreenOn: () -> Unit,
    private val onScreenOff: () -> Unit,
) {

    private var on: Boolean? = null

    fun onDisplayState(state: Int) {
        val nowOn = state == Display.STATE_ON
        val wasOn = on
        on = nowOn
        when {
            wasOn == null || wasOn == nowOn -> Unit
            nowOn -> onScreenOn()
            else -> onScreenOff()
        }
    }
}
