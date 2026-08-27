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
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.diagnostics.Diagnostics
import com.h3nc4.hypergesture.diagnostics.DiagnosticsCollector
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.navigation.EnableRequestOutcome
import com.h3nc4.hypergesture.navigation.NavigationIntegrationFactory
import com.h3nc4.hypergesture.settings.GestureSettingsRepository
import com.h3nc4.hypergesture.ui.theme.HyperGestureTheme
import com.h3nc4.hypergesture.ui.theme.onSuccessContainer
import com.h3nc4.hypergesture.ui.theme.onWarningContainer
import com.h3nc4.hypergesture.ui.theme.successContainer
import com.h3nc4.hypergesture.ui.theme.warningContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = GestureSettingsRepository(applicationContext)
        setContent {
            HyperGestureTheme {
                Scaffold { padding ->
                    HyperGestureScreen(settings, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun HyperGestureScreen(
    settings: GestureSettingsRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration by settings.configuration.collectAsState(initial = GestureConfiguration())
    val integration = remember { NavigationIntegrationFactory.create(context) }

    var diagnostics by remember { mutableStateOf(DiagnosticsCollector.collect(context)) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()

        AccessibilityStatusCard(enabled = diagnostics.accessibilityServiceEnabled) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        GestureCheatSheet()

        NavigationStatusCard(
            available = diagnostics.navigationIntegrationAvailable,
            onRequestEnable = integration::requestEnable,
            onStateChanged = { diagnostics = DiagnosticsCollector.collect(context) },
        )

        // Both sections stay collapsed by default: an accidental tap while scrolling
        // must not silently change navigation behaviour.
        ExpandableCard(
            title = stringResource(R.string.section_gestures),
            summary = stringResource(R.string.section_gestures_summary),
            expanded = settingsExpanded,
            onToggle = { settingsExpanded = !settingsExpanded },
        ) {
            GestureSettings(configuration) { update ->
                scope.launch { settings.update(update) }
            }
        }

        ExpandableCard(
            title = stringResource(R.string.section_diagnostics),
            summary = stringResource(R.string.section_diagnostics_summary),
            expanded = diagnosticsExpanded,
            onToggle = {
                diagnosticsExpanded = !diagnosticsExpanded
                if (diagnosticsExpanded) diagnostics = DiagnosticsCollector.collect(context)
            },
        ) {
            DiagnosticsContent(diagnostics) {
                diagnostics = DiagnosticsCollector.collect(context)
            }
        }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccessibilityStatusCard(enabled: Boolean, onOpenSettings: () -> Unit) {
    StatusCard(
        title = stringResource(R.string.status_accessibility_title),
        status = StatusBadgeState(
            healthy = enabled,
            healthyLabel = stringResource(R.string.status_active),
            unhealthyLabel = stringResource(R.string.status_inactive),
        ),
        body = stringResource(
            if (enabled) {
                R.string.status_accessibility_enabled
            } else {
                R.string.status_accessibility_disabled
            },
        ),
        hint = stringResource(R.string.status_accessibility_restricted_hint)
            .takeUnless { enabled },
        actionLabel = stringResource(R.string.action_open_accessibility_settings),
        onAction = onOpenSettings,
    )
}

@Composable
private fun NavigationStatusCard(
    available: Boolean,
    onRequestEnable: () -> EnableRequestOutcome,
    onStateChanged: () -> Unit,
) {
    // The outcome is held, not its message: resolving the string during composition keeps
    // it correct across a locale or configuration change.
    val lastOutcome = remember { mutableStateOf<EnableRequestOutcome?>(null) }
    StatusCard(
        title = stringResource(R.string.status_navigation_title),
        status = StatusBadgeState(
            healthy = available,
            healthyLabel = stringResource(R.string.status_navigation_available),
            unhealthyLabel = stringResource(R.string.status_navigation_unavailable),
        ),
        body = stringResource(
            if (available) {
                R.string.status_navigation_available_body
            } else {
                R.string.status_navigation_unavailable_body
            },
        ),
        hint = when (val shown = lastOutcome.value) {
            null -> null
            else -> enableOutcomeMessage(shown)
        },
        actionLabel = stringResource(R.string.action_open_navigation_settings)
            .takeIf { available },
        onAction = {
            lastOutcome.value = onRequestEnable()
            onStateChanged()
        },
    )
}

@Composable
private fun enableOutcomeMessage(outcome: EnableRequestOutcome): String = when (outcome) {
    is EnableRequestOutcome.LaunchedSettings -> stringResource(R.string.outcome_launched)
    EnableRequestOutcome.Unsupported -> stringResource(R.string.outcome_unsupported)
    is EnableRequestOutcome.Failed ->
        stringResource(R.string.outcome_failed, outcome.reason)
}

/** Healthy plus both labels travel together; splitting them bloated every call site. */
private data class StatusBadgeState(
    val healthy: Boolean,
    val healthyLabel: String,
    val unhealthyLabel: String,
) {
    val label: String get() = if (healthy) healthyLabel else unhealthyLabel
}

@Composable
private fun StatusCard(
    title: String,
    status: StatusBadgeState,
    body: String,
    hint: String?,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusBadge(status.healthy, status.label)
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actionLabel?.let {
                FilledTonalButton(onClick = onAction) { Text(it) }
            }
        }
    }
}

@Composable
private fun StatusBadge(healthy: Boolean, label: String) {
    val background = if (healthy) successContainer else warningContainer
    val foreground: Color = if (healthy) onSuccessContainer else onWarningContainer
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun GestureCheatSheet() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.gestures_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            listOf(
                R.string.gesture_back_name to R.string.gesture_back_how,
                R.string.gesture_home_name to R.string.gesture_home_how,
                R.string.gesture_recents_name to R.string.gesture_recents_how,
            ).forEach { (name, how) ->
                Column {
                    Text(
                        text = stringResource(name),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(how),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expandArrow",
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.padding(end = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand,
                    ),
                    modifier = Modifier.rotate(arrowRotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider()
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureSettings(
    configuration: GestureConfiguration,
    onUpdate: ((GestureConfiguration) -> GestureConfiguration) -> Unit,
) {
    ThresholdSlider(
        label = stringResource(R.string.label_edge_width),
        value = configuration.edgeWidthDp,
        range = 8f..64f,
        formatter = { stringResource(R.string.value_dp, it) },
    ) { updated -> onUpdate { it.copy(edgeWidthDp = updated) } }

    ThresholdSlider(
        label = stringResource(R.string.label_side_distance),
        value = configuration.sideMinimumSwipeDistanceDp,
        range = 8f..80f,
        formatter = { stringResource(R.string.value_dp, it) },
    ) { updated -> onUpdate { it.copy(sideMinimumSwipeDistanceDp = updated) } }

    ThresholdSlider(
        label = stringResource(R.string.label_bottom_distance),
        value = configuration.bottomMinimumSwipeDistanceDp,
        range = 4f..60f,
        formatter = { stringResource(R.string.value_dp, it) },
    ) { updated -> onUpdate { it.copy(bottomMinimumSwipeDistanceDp = updated) } }

    ThresholdSlider(
        label = stringResource(R.string.label_recents_hold),
        value = configuration.recentsHoldMs.toFloat(),
        range = 50f..500f,
        formatter = { stringResource(R.string.value_ms, it) },
    ) { updated -> onUpdate { it.copy(recentsHoldMs = updated.toLong()) } }

    ThresholdSlider(
        label = stringResource(R.string.label_maximum_duration),
        value = configuration.maximumArmDurationMs.toFloat(),
        range = 300f..3000f,
        formatter = { stringResource(R.string.value_ms, it) },
    ) { updated -> onUpdate { it.copy(maximumArmDurationMs = updated.toLong()) } }

    ToggleRow(
        label = stringResource(R.string.label_left_edge_back),
        checked = configuration.leftEdgeBackEnabled,
    ) { updated -> onUpdate { it.copy(leftEdgeBackEnabled = updated) } }

    ToggleRow(
        label = stringResource(R.string.label_right_edge_back),
        checked = configuration.rightEdgeBackEnabled,
    ) { updated -> onUpdate { it.copy(rightEdgeBackEnabled = updated) } }

    ToggleRow(
        label = stringResource(R.string.label_haptics),
        checked = configuration.hapticFeedbackEnabled,
    ) { updated -> onUpdate { it.copy(hapticFeedbackEnabled = updated) } }

    TextButton(onClick = { onUpdate { GestureConfiguration() } }) {
        Text(stringResource(R.string.action_reset_defaults))
    }
}

// Commits on release only: a write per drag frame is a DataStore read-modify-write per
// frame, and each write re-emits the config flow, rebuilding all three overlay windows -
// visibly stutters on HyperOS.
@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatter: @Composable (Int) -> String,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (!dragging) local = value }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatter(local.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = local,
            valueRange = range,
            onValueChange = {
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(local)
            },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DiagnosticsContent(diagnostics: Diagnostics, onRefresh: () -> Unit) {
    val none = stringResource(R.string.diag_none)
    val rows = listOf(
        stringResource(R.string.diag_android_version) to
            "${diagnostics.androidRelease} (API ${diagnostics.sdkInt})",
        stringResource(R.string.diag_device) to
            "${diagnostics.manufacturer} ${diagnostics.model}",
        stringResource(R.string.diag_hyperos) to
            (diagnostics.hyperOsVersion ?: diagnostics.miuiVersion ?: none),
        stringResource(R.string.diag_navigation_mode) to diagnostics.navigationMode.name,
        stringResource(R.string.diag_service_enabled) to
            diagnostics.accessibilityServiceEnabled.toString(),
        stringResource(R.string.diag_service_running) to
            diagnostics.accessibilityServiceRunning.toString(),
        stringResource(R.string.diag_integration) to diagnostics.navigationIntegrationId,
        stringResource(R.string.diag_last_failure) to
            (diagnostics.lastGlobalActionFailure ?: none),
        stringResource(R.string.diag_app_version) to diagnostics.appVersionName,
        stringResource(R.string.diag_license) to stringResource(R.string.value_license),
    )
    rows.forEach { (label, value) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(text = value, style = MaterialTheme.typography.bodySmall)
        }
    }
    diagnostics.lastRecordedFailure?.let { failure ->
        Text(
            text = stringResource(R.string.diag_recorded_failure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = failure.take(MAX_FAILURE_CHARS),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
    Text(
        text = stringResource(R.string.diag_local_only),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.url_source)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onRefresh) {
            Text(stringResource(R.string.action_refresh_diagnostics))
        }
        TextButton(
            onClick = {
                // A device with no browser throws ActivityNotFoundException.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, sourceUrl.toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        ) {
            Text(stringResource(R.string.action_view_source))
        }
    }
}

private const val MAX_FAILURE_CHARS = 1200
