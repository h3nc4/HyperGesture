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

package com.h3nc4.hypergesture.navigation

import android.content.Context
import kotlinx.coroutines.flow.first

/** Writes the buttons back once gestures stop, so the phone is never left with neither. */
class ButtonRestore(
    private val keeper: NavigationKeeper,
    private val settings: NavigationKeeperSettings,
) {

    /** Recorded before the keeper writes anything, so a kill straight after still leaves the trace. */
    suspend fun hiding() = settings.setBarHidden(true)

    /** Null when the keeper left nothing hidden, or the service is still there to keep it. */
    suspend fun gesturesStopped(serviceRunning: Boolean): KeepOutcome? {
        if (!shouldRestore(settings.barHidden.first(), serviceRunning)) return null
        val outcome = keeper.release()
        if (outcome == KeepOutcome.AlreadyKept || outcome is KeepOutcome.Restored) settings.setBarHidden(false)
        return outcome
    }

    companion object {
        fun shouldRestore(hidByKeeper: Boolean, serviceRunning: Boolean) = hidByKeeper && !serviceRunning

        fun logLine(outcome: KeepOutcome): String = when (outcome) {
            KeepOutcome.AlreadyKept -> "NAVIGATION_BUTTONS_BACK already shown"
            KeepOutcome.NotGranted -> "NAVIGATION_BUTTONS_NOT_BACK the grant is missing"
            is KeepOutcome.Restored -> "NAVIGATION_BUTTONS_BACK ${outcome.keys.joinToString()}"
            is KeepOutcome.Failed -> "NAVIGATION_BUTTONS_NOT_BACK ${outcome.reason}"
        }

        /** Null where the integration has no keeper, which leaves nothing to restore. */
        fun forDevice(context: Context): ButtonRestore? =
            NavigationIntegrationFactory.create(context).keeper?.let {
                ButtonRestore(it, NavigationKeeperSettings(context))
            }
    }
}
