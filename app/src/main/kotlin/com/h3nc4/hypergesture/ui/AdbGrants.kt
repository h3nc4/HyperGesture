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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.navigation.NavigationIntegrationFactory

internal enum class AdbGrant(@StringRes val title: Int, @StringRes val body: Int, @StringRes val command: Int) {
    SECURE_SETTINGS(
        R.string.grant_secure_settings_title,
        R.string.grant_secure_settings_body,
        R.string.keep_navigation_grant_command,
    ),
    USAGE_ACCESS(R.string.label_usage_access, R.string.grant_usage_access_body, R.string.usage_access_grant_command),
    ;

    val tag: String get() = "${TestTags.GRANT_STATE}_${name.lowercase()}"
}

internal data class GrantState(val grant: AdbGrant, val granted: Boolean)

/** Only the keeper writes secure settings, so that grant is listed only where the integration has one. */
internal fun readAdbGrants(context: Context): List<GrantState> = buildList {
    NavigationIntegrationFactory.create(context).keeper?.let { add(GrantState(AdbGrant.SECURE_SETTINGS, it.granted)) }
    add(GrantState(AdbGrant.USAGE_ACCESS, hasUsageAccess(context)))
}

/** Read again on each resume. A grant made from a computer lands while the app is in the background. */
@Composable
internal fun AdbGrants(read: () -> List<GrantState>) {
    val currentRead by rememberUpdatedState(read)
    var grants by remember { mutableStateOf(currentRead()) }
    LifecycleResumeEffect(Unit) {
        grants = currentRead()
        onPauseOrDispose {}
    }
    FootNote(stringResource(R.string.adb_grants_intro))
    grants.forEach { GrantRow(it) }
}

@Composable
private fun GrantRow(state: GrantState) {
    val context = LocalContext.current
    val command = stringResource(state.grant.command, context.packageName)
    val clipLabel = stringResource(R.string.clip_label_adb_command)
    var copied by remember(command) { mutableStateOf(false) }
    InfoRow(
        title = stringResource(state.grant.title),
        supporting = stringResource(state.grant.body),
        trailing = {
            StatusChip(
                label = stringResource(if (state.granted) R.string.chip_granted else R.string.chip_missing),
                tone = if (state.granted) Tone.GOOD else Tone.ATTENTION,
                modifier = Modifier.testTag(state.grant.tag),
            )
        },
    )
    // Inside the row the chip narrows the command and a long token breaks mid-word.
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        SelectionContainer {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedButton(
            onClick = {
                copyCommand(context, clipLabel, command)
                copied = true
            },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(if (copied) R.string.action_command_copied else R.string.action_copy_command))
        }
    }
}

private fun copyCommand(context: Context, label: String, command: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, command))
}
