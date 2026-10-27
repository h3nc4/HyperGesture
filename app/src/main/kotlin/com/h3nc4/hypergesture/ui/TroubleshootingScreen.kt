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
import com.h3nc4.hypergesture.navigation.GenericAndroidNavigationIntegration
import com.h3nc4.hypergesture.navigation.HyperOSNavigationIntegration
import com.h3nc4.hypergesture.navigation.NavigationMode
import com.h3nc4.hypergesture.navigation.RecoveryState
import com.h3nc4.hypergesture.navigation.RecoveryStatus

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

    SectionHeader(stringResource(R.string.section_this_device))
    DeviceRows(diagnostics, model.recoveryState)

    SectionHeader(stringResource(R.string.section_last_problems))
    val none = stringResource(R.string.diag_none)
    ValueRow(stringResource(R.string.diag_last_failure), diagnostics.lastGlobalActionFailure ?: none)
    ValueRow(stringResource(R.string.label_last_app_switch_failure), diagnostics.lastAppSwitchFailure ?: none)
    ValueRow(
        label = stringResource(R.string.diag_last_circle_to_search_failure),
        value = diagnostics.lastCircleToSearchFailure ?: none,
        valueTag = TestTags.DIAG_CIRCLE_TO_SEARCH_FAILURE,
    )
    diagnostics.lastRecordedFailure?.let { failure ->
        InfoRow(title = stringResource(R.string.diag_recorded_failure))
        Text(
            text = failure.take(MAX_FAILURE_CHARS),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    TextButton(onClick = model::refresh, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(R.string.action_refresh_diagnostics))
    }
    FootNote(stringResource(R.string.diag_local_only))
}

@Composable
private fun DeviceRows(diagnostics: Diagnostics, recoveryState: RecoveryState) {
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
    ValueRow(stringResource(R.string.diag_integration), integrationLabel(diagnostics.navigationIntegrationId))
    if (diagnostics.navigationIntegrationAvailable) {
        val recoveryLabel = stringResource(
            when (recoveryState.status) {
                RecoveryStatus.DISABLED -> R.string.recovery_status_disabled
                RecoveryStatus.SHIZUKU_UNAVAILABLE -> R.string.recovery_status_unavailable
                RecoveryStatus.PERMISSION_DENIED -> R.string.recovery_status_permission
                RecoveryStatus.VERIFIED -> R.string.recovery_status_verified
                RecoveryStatus.WRITE_FAILED -> R.string.recovery_status_failed
            },
        )
        ValueRow(stringResource(R.string.recovery_title), recoveryLabel)
    }
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
