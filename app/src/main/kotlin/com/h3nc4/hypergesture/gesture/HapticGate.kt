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

/** Buzzes once per activation. A fire after an arm that ticked is quiet, and a re-arm ticks. */
class HapticGate {

    private var armTicked = false

    fun reset() {
        armTicked = false
    }

    fun shouldTick(action: TrackerAction): Boolean = when (action) {
        // An arm that schedules a hold stays quiet, so a plain upward swipe buzzes on fire.
        is TrackerAction.Armed -> (action.scheduleHoldMs == null).also { if (it) armTicked = true }
        is TrackerAction.Fire -> {
            val ticked = armTicked
            armTicked = false
            !ticked
        }
        is TrackerAction.Disarmed -> {
            armTicked = false
            false
        }
        else -> false
    }
}
