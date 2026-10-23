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

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.update.UpdateViewModel

@Composable
internal fun UpdatesScreen(updates: UpdateViewModel) {
    val context = LocalContext.current
    val state by updates.state.collectAsState()
    Column(modifier = Modifier.testTag(TestTags.SCREEN_UPDATES)) {
        ToggleRow(
            label = stringResource(R.string.update_switch),
            checked = state.enabled,
            tag = TestTags.SWITCH_UPDATE_CHECK,
            onChange = updates::setEnabled,
        )
        FootNote(stringResource(R.string.update_switch_body))
        if (!state.enabled) return@Column
        UpdateStatusRow(
            state = state,
            onCheckNow = updates::checkNow,
            onInstall = updates::downloadAndInstall,
            onAllowInstalls = {
                context.startFirst(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri(),
                    ),
                )
            },
        )
    }
}
