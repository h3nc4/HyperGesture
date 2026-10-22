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

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.diagnostics.ServiceStatus
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.ui.theme.success
import com.h3nc4.hypergesture.ui.theme.warning

/** The answer to why the app was opened, with the one action the current state needs. */
@Composable
internal fun StatusBlock(status: ServiceStatus, configuration: GestureConfiguration) {
    val context = LocalContext.current
    val title = when (status) {
        ServiceStatus.OFF -> R.string.status_off
        ServiceStatus.STOPPED -> R.string.status_stopped
        ServiceStatus.WORKING -> R.string.status_working
    }
    val body = when (status) {
        ServiceStatus.OFF -> R.string.status_off_body
        ServiceStatus.STOPPED -> R.string.status_stopped_body
        ServiceStatus.WORKING -> workingBody(configuration)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(if (status == ServiceStatus.WORKING) success else warning, CircleShape),
                )
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .testTag(TestTags.SERVICE_STATUS),
                )
            }
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (status) {
                ServiceStatus.OFF -> {
                    Button(onClick = { openAccessibility(context) }) {
                        Text(stringResource(R.string.action_turn_on))
                    }
                    // Restricted settings arrived in Android 13, for apps installed outside a store.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text(
                            text = stringResource(R.string.status_restricted_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { openAppInfo(context) }) {
                            Text(stringResource(R.string.action_open_app_info))
                        }
                    }
                }
                ServiceStatus.STOPPED -> Button(onClick = { openAccessibility(context) }) {
                    Text(stringResource(R.string.action_fix))
                }
                ServiceStatus.WORKING -> Unit
            }
        }
    }
}

private fun workingBody(configuration: GestureConfiguration): Int = when {
    configuration.leftEdgeBackEnabled && configuration.rightEdgeBackEnabled ->
        R.string.status_working_body
    configuration.leftEdgeBackEnabled -> R.string.status_working_body_left
    configuration.rightEdgeBackEnabled -> R.string.status_working_body_right
    else -> R.string.status_working_body_bottom
}
