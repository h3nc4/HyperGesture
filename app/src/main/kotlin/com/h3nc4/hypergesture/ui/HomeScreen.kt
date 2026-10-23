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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.update.UpdateViewModel

@Composable
internal fun HomeScreen(model: AppModel, updates: UpdateViewModel, onOpen: (Screen) -> Unit) {
    val updateState by updates.state.collectAsState()
    StatusBlock(model.status, model.configuration)
    SetupGroup(model) { onOpen(Screen.SETUP) }

    SectionHeader(stringResource(R.string.section_features))
    SplitSwitchRow(
        title = stringResource(R.string.section_app_switch),
        supporting = appSwitchingSummary(model),
        checked = model.configuration.appSwitchEnabled,
        rowTag = TestTags.ROW_APP_SWITCHING,
        switchTag = TestTags.SWITCH_APP_SWITCH,
        onOpen = { onOpen(Screen.APP_SWITCHING) },
    ) { on -> model.update { it.copy(appSwitchEnabled = on) } }
    SplitSwitchRow(
        title = stringResource(R.string.section_circle_to_search),
        supporting = circleToSearchSummary(model),
        checked = model.configuration.circleToSearchEnabled,
        rowTag = TestTags.ROW_CIRCLE_TO_SEARCH,
        switchTag = TestTags.SWITCH_CIRCLE_TO_SEARCH,
        onOpen = { onOpen(Screen.CIRCLE_TO_SEARCH) },
    ) { on -> model.update { it.copy(circleToSearchEnabled = on) } }
    SplitSwitchRow(
        title = stringResource(R.string.section_updates),
        supporting = updatesSummary(updateState),
        checked = updateState.enabled,
        rowTag = TestTags.ROW_UPDATES,
        switchTag = TestTags.SWITCH_UPDATE_CHECK,
        onOpen = { onOpen(Screen.UPDATES) },
        onChange = updates::setEnabled,
    )

    SectionHeader(stringResource(R.string.section_more))
    NavigationRow(
        title = stringResource(R.string.section_gestures),
        supporting = gestureSettingsSummary(model.configuration),
        tag = TestTags.ROW_GESTURE_SETTINGS,
    ) { onOpen(Screen.GESTURE_SETTINGS) }
    NavigationRow(
        title = stringResource(R.string.section_troubleshooting),
        tag = TestTags.ROW_TROUBLESHOOTING,
    ) { onOpen(Screen.TROUBLESHOOTING) }
    NavigationRow(
        title = stringResource(R.string.section_about),
        supporting = model.diagnostics.appVersionName?.let {
            stringResource(R.string.about_version_summary, it)
        },
        tag = TestTags.ROW_ABOUT,
    ) { onOpen(Screen.ABOUT) }
}

/** The checklist stays on home until gestures can run unattended, then folds into one row. */
@Composable
private fun SetupGroup(model: AppModel, onOpenSetup: () -> Unit) {
    val checklist = model.checklist
    if (checklist.essentialsDone) {
        val left = checklist.recommendedLeft
        NavigationRow(
            title = stringResource(R.string.section_setup),
            supporting = if (left == 0) {
                stringResource(R.string.chip_done)
            } else {
                pluralStringResource(R.plurals.setup_recommended_left, left, left)
            },
            tag = TestTags.ROW_SETUP,
            onClick = onOpenSetup,
        )
        return
    }
    SectionHeader(
        pluralStringResource(
            R.plurals.setup_progress,
            checklist.required.size,
            checklist.requiredDone,
            checklist.required.size,
        ),
    )
    SetupRows(checklist.required, model)
}
