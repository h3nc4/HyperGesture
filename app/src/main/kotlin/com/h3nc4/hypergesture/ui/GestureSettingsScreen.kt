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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import kotlin.math.roundToInt

@Composable
internal fun GestureSettingsScreen(model: AppModel) {
    val configuration = model.configuration
    SectionHeader(stringResource(R.string.gestures_title))
    CheatSheet(configuration)

    SectionHeader(stringResource(R.string.section_edges))
    ThresholdSlider(
        label = stringResource(R.string.label_edge_width),
        value = configuration.edgeWidthDp,
        range = 8f..64f,
        formatter = { stringResource(R.string.value_dp, it) },
    ) { updated -> model.update { it.copy(edgeWidthDp = updated) } }
    ToggleRow(
        label = stringResource(R.string.label_left_edge_back),
        checked = configuration.leftEdgeBackEnabled,
    ) { updated -> model.update { it.copy(leftEdgeBackEnabled = updated) } }
    ToggleRow(
        label = stringResource(R.string.label_right_edge_back),
        checked = configuration.rightEdgeBackEnabled,
    ) { updated -> model.update { it.copy(rightEdgeBackEnabled = updated) } }
    ToggleRow(
        label = stringResource(R.string.label_landscape_confirm),
        checked = configuration.landscapeConfirmEnabled,
        supporting = stringResource(R.string.label_landscape_confirm_body),
        tag = TestTags.SWITCH_LANDSCAPE_CONFIRM,
    ) { updated -> model.update { it.copy(landscapeConfirmEnabled = updated) } }

    SectionHeader(stringResource(R.string.section_distance_and_timing))
    ThresholdSlider(
        label = stringResource(R.string.label_side_distance),
        value = configuration.sideMinimumSwipeDistanceDp,
        range = 8f..80f,
        formatter = { stringResource(R.string.value_dp, it) },
    ) { updated -> model.update { it.copy(sideMinimumSwipeDistanceDp = updated) } }
    ThresholdSlider(
        label = stringResource(R.string.label_bottom_distance),
        value = configuration.bottomMinimumSwipeDistanceDp,
        range = 4f..60f,
        formatter = { stringResource(R.string.value_dp, it) },
    ) { updated -> model.update { it.copy(bottomMinimumSwipeDistanceDp = updated) } }
    ThresholdSlider(
        label = stringResource(R.string.label_recents_hold),
        value = configuration.recentsHoldMs.toFloat(),
        range = 50f..500f,
        formatter = { stringResource(R.string.value_ms, it) },
    ) { updated -> model.update { it.copy(recentsHoldMs = updated.toLong()) } }
    ThresholdSlider(
        label = stringResource(R.string.label_maximum_duration),
        value = configuration.maximumArmDurationMs.toFloat(),
        range = 300f..3000f,
        formatter = { stringResource(R.string.value_ms, it) },
    ) { updated -> model.update { it.copy(maximumArmDurationMs = updated.toLong()) } }

    SectionHeader(stringResource(R.string.section_feedback))
    ToggleRow(
        label = stringResource(R.string.label_haptics),
        checked = configuration.hapticFeedbackEnabled,
    ) { updated -> model.update { it.copy(hapticFeedbackEnabled = updated) } }

    val resetMessage = stringResource(R.string.snackbar_reset)
    val undo = stringResource(R.string.action_undo)
    TextButton(
        onClick = { model.resetGestureSettings(resetMessage, undo) },
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.action_reset_defaults))
    }
}

/** Only the gestures this configuration has, so a turned-off one is not advertised. */
@Composable
private fun CheatSheet(configuration: GestureConfiguration) {
    val back = when {
        configuration.leftEdgeBackEnabled && configuration.rightEdgeBackEnabled -> R.string.gesture_back_how
        configuration.leftEdgeBackEnabled -> R.string.gesture_back_left_how
        configuration.rightEdgeBackEnabled -> R.string.gesture_back_right_how
        else -> null
    }
    buildList {
        back?.let { add(R.string.gesture_back_name to it) }
        add(R.string.gesture_home_recents_name to R.string.gesture_home_recents_how)
        if (configuration.appSwitchEnabled) {
            add(R.string.gesture_switch_apps_name to R.string.gesture_switch_apps_how)
        }
        if (configuration.circleToSearchEnabled) {
            add(R.string.gesture_circle_to_search_name to R.string.gesture_circle_to_search_how)
        }
    }.forEach { (name, how) -> InfoRow(title = stringResource(name), supporting = stringResource(how)) }
}

@Composable
internal fun gestureSettingsSummary(configuration: GestureConfiguration): String = stringResource(
    R.string.gesture_settings_summary,
    configuration.edgeWidthDp.roundToInt(),
    configuration.sideMinimumSwipeDistanceDp.roundToInt(),
    configuration.bottomMinimumSwipeDistanceDp.roundToInt(),
)
