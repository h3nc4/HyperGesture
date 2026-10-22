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

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.h3nc4.hypergesture.R

@Composable
internal fun AppSwitchingScreen(model: AppModel) {
    val context = LocalContext.current
    val configuration = model.configuration
    ToggleRow(
        label = stringResource(R.string.label_app_switch),
        checked = configuration.appSwitchEnabled,
        tag = TestTags.SWITCH_APP_SWITCH,
    ) { updated -> model.update { it.copy(appSwitchEnabled = updated) } }
    FootNote(stringResource(R.string.section_app_switch_body))
    SectionHeader(stringResource(R.string.section_needs))
    InfoRow(
        title = stringResource(R.string.label_usage_access),
        supporting = stringResource(
            if (model.usageAccess) R.string.label_usage_access_granted else R.string.label_usage_access_missing,
        ),
        supportingTag = TestTags.USAGE_ACCESS,
        trailing = if (model.usageAccess) {
            null
        } else {
            {
                OutlinedButton(onClick = { openUsageAccess(context) }) {
                    Text(stringResource(R.string.action_grant_usage_access))
                }
            }
        },
    )
    SectionHeader(stringResource(R.string.section_gestures_it_adds))
    InfoRow(
        title = stringResource(R.string.gesture_previous_app_name),
        supporting = stringResource(R.string.gesture_previous_app_how),
    )
    InfoRow(
        title = stringResource(R.string.gesture_next_app_name),
        supporting = stringResource(R.string.gesture_next_app_how),
    )
    LastProblem(model.diagnostics.lastAppSwitchFailure)
}

/** The supporting text of a feature row on home, naming what it still needs. */
@Composable
internal fun appSwitchingSummary(model: AppModel): String = stringResource(
    when {
        !model.configuration.appSwitchEnabled -> R.string.feature_off
        !model.usageAccess -> R.string.feature_needs_usage_access
        else -> R.string.feature_on
    },
)
