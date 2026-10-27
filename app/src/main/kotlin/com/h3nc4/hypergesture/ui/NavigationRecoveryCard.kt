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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.navigation.RecoveryStatus

@Composable
internal fun NavigationRecoveryCard(model: AppModel, modifier: Modifier = Modifier) {
    val state = model.recoveryState
    val statusText = stringResource(
        when (state.status) {
            RecoveryStatus.DISABLED -> R.string.recovery_status_disabled
            RecoveryStatus.SHIZUKU_UNAVAILABLE -> R.string.recovery_status_unavailable
            RecoveryStatus.PERMISSION_DENIED -> R.string.recovery_status_permission
            RecoveryStatus.VERIFIED -> R.string.recovery_status_verified
            RecoveryStatus.WRITE_FAILED -> R.string.recovery_status_failed
        },
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.recovery_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { model.setRecoveryEnabled(it) },
                )
            }
            Text(
                stringResource(R.string.recovery_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.enabled && state.status == RecoveryStatus.PERMISSION_DENIED) {
                TextButton(onClick = { model.requestShizukuPermission() }) {
                    Text(stringResource(R.string.recovery_request_permission))
                }
            }
        }
    }
}
