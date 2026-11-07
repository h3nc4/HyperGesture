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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.h3nc4.hypergesture.R

@Composable
internal fun SetupScreen(model: AppModel) {
    val checklist = model.checklist
    SectionHeader(stringResource(R.string.setup_group_required))
    SetupRows(checklist.required, model)
    SectionHeader(stringResource(R.string.setup_group_recommended))
    SetupRows(checklist.recommended, model)
    KeepNavigationRow(model)
    FootNote(
        stringResource(
            if (model.diagnostics.navigationIntegrationAvailable) {
                R.string.setup_footer_hyperos
            } else {
                R.string.setup_footer
            },
        ),
    )
}

/** Outside the checklist, since it is an opt-in that needs a computer once. */
@Composable
private fun KeepNavigationRow(model: AppModel) {
    val granted = model.keeperGranted ?: return
    if (granted) {
        ToggleRow(
            label = stringResource(R.string.label_keep_navigation),
            supporting = stringResource(R.string.keep_navigation_body),
            checked = model.keeperEnabled,
            tag = TestTags.SWITCH_KEEP_NAVIGATION,
            onChange = model::switchKeeper,
        )
        return
    }
    InfoRow(
        title = stringResource(R.string.label_keep_navigation),
        supporting = stringResource(R.string.keep_navigation_grant_body),
        supportingTag = TestTags.KEEP_NAVIGATION_GRANT,
    )
    SelectionContainer {
        Text(
            text = stringResource(R.string.keep_navigation_grant_command, LocalContext.current.packageName),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
