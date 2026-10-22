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

package com.h3nc4.hypergesture.setup

import com.h3nc4.hypergesture.diagnostics.ServiceStatus

enum class SetupStep { RESTRICTED_SETTINGS, ACCESSIBILITY, BATTERY, AUTOSTART, TILE, NAVIGATION_BAR }

enum class SetupGroup { REQUIRED, RECOMMENDED }

enum class StepState {
    DONE,
    TO_DO,

    /** The app cannot read this setting, so the user confirms it by hand. */
    UNVERIFIED,

    /** Waits on an earlier step, named by [SetupItem.waitsOn]. */
    LOCKED,

    /** A fact about this device rather than something left to do. */
    UNAVAILABLE,
}

/** What the app can read, plus what the user confirmed for the settings it cannot. */
data class SetupFacts(
    val restrictedSettingsApply: Boolean,
    val restrictedSettingsConfirmed: Boolean,
    val serviceEverEnabled: Boolean,
    val service: ServiceStatus,
    val batteryUnrestricted: Boolean,
    val batteryConfirmed: Boolean,
    val autostartApplies: Boolean,
    val autostartConfirmed: Boolean,
    val tileAdded: Boolean,
    val navigationBarChangeable: Boolean,
    val navigationBarHidden: Boolean,
)

data class SetupItem(
    val step: SetupStep,
    val group: SetupGroup,
    val state: StepState,
    val waitsOn: SetupStep? = null,
)

data class SetupChecklist(val items: List<SetupItem>) {

    val required: List<SetupItem> get() = items.filter { it.group == SetupGroup.REQUIRED }

    val recommended: List<SetupItem> get() = items.filter { it.group == SetupGroup.RECOMMENDED }

    val requiredDone: Int get() = required.count { it.state == StepState.DONE }

    /** Gestures keep working once these are done, so home stops leading with setup. */
    val essentialsDone: Boolean get() = requiredDone == required.size

    val recommendedLeft: Int
        get() = recommended.count { it.state != StepState.DONE && it.state != StepState.UNAVAILABLE }

    operator fun get(step: SetupStep): SetupItem? = items.firstOrNull { it.step == step }

    companion object {

        fun evaluate(facts: SetupFacts): SetupChecklist {
            val accessibility = when (facts.service) {
                ServiceStatus.WORKING -> StepState.DONE
                ServiceStatus.STOPPED, ServiceStatus.OFF -> StepState.TO_DO
            }
            val accessibilityDone = accessibility == StepState.DONE
            val items = buildList {
                if (facts.restrictedSettingsApply) {
                    // The switch only turns on once restricted settings are allowed.
                    val done = facts.restrictedSettingsConfirmed || facts.serviceEverEnabled ||
                        facts.service != ServiceStatus.OFF
                    add(required(SetupStep.RESTRICTED_SETTINGS, if (done) StepState.DONE else StepState.UNVERIFIED))
                }
                add(required(SetupStep.ACCESSIBILITY, accessibility))
                add(
                    afterAccessibility(
                        SetupStep.BATTERY,
                        facts.batteryUnrestricted || facts.batteryConfirmed,
                        accessibilityDone,
                    ),
                )
                if (facts.autostartApplies) {
                    add(afterAccessibility(SetupStep.AUTOSTART, facts.autostartConfirmed, accessibilityDone))
                }
                add(
                    SetupItem(
                        SetupStep.TILE,
                        SetupGroup.RECOMMENDED,
                        if (facts.tileAdded) StepState.DONE else StepState.TO_DO,
                    ),
                )
                add(navigationBar(facts, accessibilityDone))
            }
            return SetupChecklist(items)
        }

        private fun required(step: SetupStep, state: StepState) =
            SetupItem(step, SetupGroup.REQUIRED, state)

        private fun afterAccessibility(step: SetupStep, done: Boolean, accessibilityDone: Boolean) =
            when {
                done -> required(step, StepState.DONE)
                accessibilityDone -> required(step, StepState.UNVERIFIED)
                else -> SetupItem(step, SetupGroup.REQUIRED, StepState.LOCKED, SetupStep.ACCESSIBILITY)
            }

        // The bar waits for a running service and the tile, which are the way back if gestures stop.
        private fun navigationBar(facts: SetupFacts, accessibilityDone: Boolean): SetupItem {
            fun item(state: StepState, waitsOn: SetupStep? = null) =
                SetupItem(SetupStep.NAVIGATION_BAR, SetupGroup.RECOMMENDED, state, waitsOn)
            return when {
                !facts.navigationBarChangeable -> item(StepState.UNAVAILABLE)
                facts.navigationBarHidden -> item(StepState.DONE)
                !accessibilityDone -> item(StepState.LOCKED, SetupStep.ACCESSIBILITY)
                !facts.tileAdded -> item(StepState.LOCKED, SetupStep.TILE)
                else -> item(StepState.TO_DO)
            }
        }
    }
}
