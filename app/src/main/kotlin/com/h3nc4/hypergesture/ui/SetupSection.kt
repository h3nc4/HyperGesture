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

package com.h3nc4.hypergesture.ui

import android.os.Build
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.diagnostics.ServiceStatus
import com.h3nc4.hypergesture.navigation.EnableRequestOutcome
import com.h3nc4.hypergesture.setup.SetupFlag
import com.h3nc4.hypergesture.setup.SetupItem
import com.h3nc4.hypergesture.setup.SetupStep
import com.h3nc4.hypergesture.setup.StepState
import com.h3nc4.hypergesture.setup.TileAddOutcome

@Composable
internal fun SetupRows(items: List<SetupItem>, model: AppModel) {
    // Outcomes are held rather than their messages, so a locale change re-resolves them.
    var tileOutcome by remember {
        mutableStateOf<TileAddOutcome?>(
            TileAddOutcome.Unsupported.takeUnless { TileAddOutcome.promptAvailable(Build.VERSION.SDK_INT) },
        )
    }
    var navigationOutcome by remember { mutableStateOf<EnableRequestOutcome?>(null) }
    val context = LocalContext.current
    items.forEach { item ->
        when (item.step) {
            SetupStep.RESTRICTED_SETTINGS -> StepRow(
                item = item,
                title = R.string.step_restricted,
                pending = R.string.step_restricted_body,
                confirmed = model.record.restrictedSettingsConfirmed,
                onConfirm = { model.setFlag(SetupFlag.RESTRICTED_SETTINGS_CONFIRMED, it) },
                open = R.string.action_open_app_info to { openAppInfo(context) },
            )
            SetupStep.ACCESSIBILITY -> StepRow(
                item = item,
                title = R.string.step_accessibility,
                pending = if (model.status == ServiceStatus.STOPPED) {
                    R.string.step_accessibility_stopped
                } else {
                    R.string.step_accessibility_body
                },
                done = R.string.step_accessibility_done,
                open = (if (model.status == ServiceStatus.STOPPED) R.string.action_fix else R.string.action_turn_on) to
                    { openAccessibility(context) },
            )
            SetupStep.BATTERY -> StepRow(
                item = item,
                title = R.string.step_battery,
                pending = R.string.step_battery_body,
                confirmed = model.record.batteryConfirmed,
                onConfirm = { model.setFlag(SetupFlag.BATTERY_CONFIRMED, it) },
                open = R.string.action_open_app_info to { openAppInfo(context) },
            )
            SetupStep.AUTOSTART -> StepRow(
                item = item,
                title = R.string.step_autostart,
                pending = R.string.step_autostart_body,
                confirmed = model.record.autostartConfirmed,
                onConfirm = { model.setFlag(SetupFlag.AUTOSTART_CONFIRMED, it) },
                open = R.string.action_open_autostart to { openAutostart(context) },
            )
            SetupStep.TILE -> StepRow(
                item = item,
                title = R.string.step_tile,
                pending = R.string.step_tile_body,
                done = R.string.step_tile_done,
                outcome = tileOutcome?.let { tileOutcomeMessage(it) },
                open = (R.string.action_add_tile to {
                    requestTile(context) { tileOutcome = it }
                }).takeIf { TileAddOutcome.promptAvailable(Build.VERSION.SDK_INT) },
            )
            SetupStep.NAVIGATION_BAR -> StepRow(
                item = item,
                title = R.string.step_navigation_bar,
                pending = R.string.step_navigation_bar_body,
                done = R.string.step_navigation_bar_done,
                outcome = navigationOutcome?.let { enableOutcomeMessage(it) },
                open = R.string.action_open_navigation_settings to {
                    navigationOutcome = model.integration.requestEnable()
                    model.refresh()
                },
            )
        }
    }
}

@Composable
private fun StepRow(
    item: SetupItem,
    title: Int,
    pending: Int,
    done: Int? = null,
    confirmed: Boolean = false,
    onConfirm: ((Boolean) -> Unit)? = null,
    outcome: String? = null,
    open: Pair<Int, () -> Unit>? = null,
) {
    val supporting = when (item.state) {
        StepState.DONE -> when {
            done != null -> stringResource(done)
            confirmed -> stringResource(R.string.step_confirmed)
            else -> null
        }
        StepState.LOCKED -> stringResource(
            if (item.waitsOn == SetupStep.TILE) R.string.step_waits_on_tile else R.string.step_waits_on_accessibility,
        )
        StepState.UNAVAILABLE -> stringResource(R.string.status_navigation_unavailable_body)
        StepState.TO_DO, StepState.UNVERIFIED -> stringResource(pending)
    }
    val (chip, tone) = when (item.state) {
        StepState.DONE -> R.string.chip_done to Tone.GOOD
        StepState.TO_DO -> R.string.chip_to_do to Tone.ATTENTION
        StepState.UNVERIFIED -> R.string.chip_check to Tone.ATTENTION
        StepState.LOCKED -> R.string.chip_locked to Tone.NEUTRAL
        StepState.UNAVAILABLE -> R.string.chip_unavailable to Tone.NEUTRAL
    }
    val actionable = item.state == StepState.TO_DO || item.state == StepState.UNVERIFIED
    val undoable = item.state == StepState.DONE && confirmed && onConfirm != null
    InfoRow(
        title = stringResource(title),
        supporting = listOfNotNull(supporting, outcome).joinToString("\n").ifEmpty { null },
        trailing = { StatusChip(stringResource(chip), tone) },
        actions = when {
            actionable && (open != null || onConfirm != null) -> {
                {
                    open?.let { (label, action) ->
                        OutlinedButton(onClick = action) { Text(stringResource(label)) }
                    }
                    if (item.state == StepState.UNVERIFIED && onConfirm != null) {
                        TextButton(onClick = { onConfirm(true) }) {
                            Text(stringResource(R.string.action_i_did_it))
                        }
                    }
                }
            }
            undoable -> {
                {
                    TextButton(onClick = { onConfirm(false) }) {
                        Text(stringResource(R.string.action_undo))
                    }
                }
            }
            else -> null
        },
    )
}

@Composable
private fun tileOutcomeMessage(outcome: TileAddOutcome): String = when (outcome) {
    TileAddOutcome.Added -> stringResource(R.string.tile_add_added)
    TileAddOutcome.AlreadyAdded -> stringResource(R.string.tile_add_already)
    TileAddOutcome.Dismissed -> stringResource(R.string.tile_add_dismissed)
    is TileAddOutcome.Refused -> stringResource(R.string.tile_add_refused, outcome.code)
    is TileAddOutcome.Failed -> stringResource(R.string.tile_add_failed, outcome.reason)
    TileAddOutcome.Unsupported -> stringResource(R.string.tile_add_unsupported)
}

@Composable
private fun enableOutcomeMessage(outcome: EnableRequestOutcome): String = when (outcome) {
    is EnableRequestOutcome.LaunchedSettings -> stringResource(R.string.outcome_launched)
    EnableRequestOutcome.Unsupported -> stringResource(R.string.outcome_unsupported)
    is EnableRequestOutcome.Failed -> stringResource(R.string.outcome_failed, outcome.reason)
}
