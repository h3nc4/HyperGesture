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

import android.view.ViewConfiguration
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.h3nc4.hypergesture.R

@Composable
internal fun CircleToSearchScreen(model: AppModel) {
    val context = LocalContext.current
    val configuration = model.configuration
    ToggleRow(
        label = stringResource(R.string.label_circle_to_search),
        checked = configuration.circleToSearchEnabled,
        tag = TestTags.SWITCH_CIRCLE_TO_SEARCH,
    ) { updated -> model.update { it.copy(circleToSearchEnabled = updated) } }
    FootNote(stringResource(R.string.section_circle_to_search_body))
    if (!configuration.circleToSearchEnabled) return

    SectionHeader(stringResource(R.string.section_timing))
    ToggleRow(
        label = stringResource(R.string.label_circle_to_search_system_timing),
        supporting = stringResource(R.string.value_ms, ViewConfiguration.getLongPressTimeout()),
        checked = configuration.circleToSearchFollowsSystemTiming,
    ) { updated -> model.update { it.copy(circleToSearchFollowsSystemTiming = updated) } }
    if (!configuration.circleToSearchFollowsSystemTiming) {
        ThresholdSlider(
            label = stringResource(R.string.label_circle_to_search_hold),
            value = configuration.circleToSearchHoldMs.toFloat(),
            range = 250f..1500f,
            formatter = { stringResource(R.string.value_ms, it) },
        ) { updated -> model.update { it.copy(circleToSearchHoldMs = updated.toLong()) } }
    }
    SectionHeader(stringResource(R.string.section_needs))
    InfoRow(
        title = stringResource(R.string.label_assistant),
        supporting = stringResource(
            when (model.assistant) {
                Assistant.GOOGLE -> R.string.assistant_google
                Assistant.OTHER -> R.string.assistant_other
                Assistant.UNKNOWN -> R.string.label_circle_to_search_assistant
            },
        ),
        actions = if (model.assistant == Assistant.GOOGLE) {
            null
        } else {
            {
                OutlinedButton(onClick = { openAssistantSettings(context) }) {
                    Text(stringResource(R.string.action_open_assistant_settings))
                }
            }
        },
    )
    LastProblem(model.diagnostics.lastCircleToSearchFailure)
}

@Composable
internal fun circleToSearchSummary(model: AppModel): String = stringResource(
    when {
        !model.configuration.circleToSearchEnabled -> R.string.feature_off
        model.assistant == Assistant.OTHER -> R.string.feature_needs_assistant
        else -> R.string.feature_on
    },
)
