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
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.diagnostics.Diagnostics
import com.h3nc4.hypergesture.diagnostics.StuckStreamLog
import com.h3nc4.hypergesture.navigation.GenericAndroidNavigationIntegration
import com.h3nc4.hypergesture.navigation.HyperOSNavigationIntegration
import com.h3nc4.hypergesture.navigation.NavigationMode

@Composable
internal fun TroubleshootingScreen(model: AppModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.refresh() }
    val diagnostics = model.diagnostics
    SectionHeader(stringResource(R.string.section_known_fixes))
    InfoRow(
        title = stringResource(R.string.fix_stopped_title),
        supporting = stringResource(R.string.fix_stopped_body),
        actions = {
            OutlinedButton(onClick = { openAccessibility(context) }) {
                Text(stringResource(R.string.action_open))
            }
        },
    )
    InfoRow(
        title = stringResource(R.string.fix_greyed_out_title),
        supporting = stringResource(R.string.fix_greyed_out_body),
        actions = {
            OutlinedButton(onClick = { openAppInfo(context) }) {
                Text(stringResource(R.string.action_open_app_info))
            }
        },
    )

    SectionHeader(stringResource(R.string.section_adb_grants))
    AdbGrants { readAdbGrants(context) }

    SectionHeader(stringResource(R.string.section_this_device))
    DeviceRows(diagnostics)

    SectionHeader(stringResource(R.string.section_last_problems))
    val none = stringResource(R.string.diag_none)
    ValueRow(stringResource(R.string.diag_last_failure), diagnostics.lastGlobalActionFailure ?: none)
    ValueRow(stringResource(R.string.label_last_app_switch_failure), diagnostics.lastAppSwitchFailure ?: none)
    ValueRow(
        label = stringResource(R.string.diag_last_circle_to_search_failure),
        value = diagnostics.lastCircleToSearchFailure ?: none,
        valueTag = TestTags.DIAG_CIRCLE_TO_SEARCH_FAILURE,
    )
    diagnostics.stopHistory?.let { history ->
        InfoRow(title = stringResource(R.string.diag_stop_history))
        MonospaceBlock(history)
    }
    val stuck = diagnostics.lastStuckStream
    ValueRow(
        label = stringResource(R.string.diag_last_stuck_stream),
        value = stuck?.lineSequence()?.first()?.removePrefix(StuckStreamLog.MARKER)?.trim() ?: none,
        valueTag = TestTags.DIAG_STUCK_STREAM,
    )
    // The newest events are at the end, right before the release.
    stuck?.let { report ->
        val tail = report.takeLast(MAX_FAILURE_CHARS)
        MonospaceBlock(if (tail.length < report.length) tail.substringAfter('\n') else tail)
    }
    diagnostics.lastRecordedFailure?.let { failure ->
        val copied = stringResource(R.string.snackbar_failure_copied)
        // Android 13 and later confirm a copy themselves, and a snackbar under that would say it twice.
        val announce = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        RecordedFailure(
            failure = failure,
            onCopied = { if (announce) model.announce(copied) },
            onClear = model::clearRecordedFailure,
        )
    }
    TextButton(onClick = model::refresh, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(R.string.action_refresh_diagnostics))
    }
    FootNote(stringResource(R.string.diag_local_only))
}

/** The block shows only the head of the record, so Copy hands over the whole of it. */
@Composable
internal fun RecordedFailure(failure: String, onCopied: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.clip_label_failure)
    InfoRow(
        title = stringResource(R.string.diag_recorded_failure),
        actions = {
            OutlinedButton(onClick = {
                copyToClipboard(context, clipLabel, failure)
                onCopied()
            }) {
                Text(stringResource(R.string.action_copy_failure))
            }
            OutlinedButton(onClick = onClear) {
                Text(stringResource(R.string.action_clear_failure))
            }
        },
    )
    MonospaceBlock(failure.take(MAX_FAILURE_CHARS))
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun MonospaceBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun DeviceRows(diagnostics: Diagnostics) {
    val none = stringResource(R.string.diag_none)
    val unknown = stringResource(R.string.diag_unknown)
    val yes = stringResource(R.string.value_yes)
    val no = stringResource(R.string.value_no)
    fun yesNo(value: Boolean) = if (value) yes else no
    ValueRow(
        stringResource(R.string.diag_android_version),
        stringResource(R.string.value_android_version, diagnostics.androidRelease ?: unknown, diagnostics.sdkInt),
    )
    ValueRow(
        stringResource(R.string.diag_device),
        listOfNotNull(diagnostics.manufacturer, diagnostics.model).joinToString(" ").ifBlank { unknown },
    )
    ValueRow(stringResource(R.string.diag_hyperos), diagnostics.hyperOsVersion ?: diagnostics.miuiVersion ?: none)
    ValueRow(
        stringResource(R.string.diag_navigation_mode),
        stringResource(navigationModeLabel(diagnostics.navigationMode)),
    )
    ValueRow(stringResource(R.string.diag_service_enabled), yesNo(diagnostics.accessibilityServiceSwitchedOn))
    ValueRow(stringResource(R.string.diag_service_bound), yesNo(diagnostics.accessibilityServiceEnabled))
    ValueRow(stringResource(R.string.diag_service_running), yesNo(diagnostics.accessibilityServiceRunning))
    ValueRow(stringResource(R.string.diag_strips_attached), diagnostics.attachedStrips?.toString() ?: none)
    ValueRow(stringResource(R.string.diag_integration), integrationLabel(diagnostics.navigationIntegrationId))
}

private fun navigationModeLabel(mode: NavigationMode): Int = when (mode) {
    NavigationMode.GESTURAL -> R.string.value_navigation_gestural
    NavigationMode.THREE_BUTTON -> R.string.value_navigation_buttons
    NavigationMode.UNKNOWN -> R.string.diag_unknown
}

@Composable
private fun integrationLabel(id: String): String = when (id) {
    HyperOSNavigationIntegration.ID -> stringResource(R.string.integration_hyperos)
    GenericAndroidNavigationIntegration.ID -> stringResource(R.string.integration_generic)
    else -> id
}

private const val MAX_FAILURE_CHARS = 1200
