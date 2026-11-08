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

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.h3nc4.hypergesture.diagnostics.Diagnostics
import com.h3nc4.hypergesture.diagnostics.DiagnosticsCollector
import com.h3nc4.hypergesture.diagnostics.ServiceStatus
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.navigation.ButtonRestore
import com.h3nc4.hypergesture.navigation.NavigationIntegration
import com.h3nc4.hypergesture.navigation.NavigationIntegrationFactory
import com.h3nc4.hypergesture.navigation.NavigationKeeperSettings
import com.h3nc4.hypergesture.navigation.NavigationMode
import com.h3nc4.hypergesture.service.HyperGestureAccessibilityService
import com.h3nc4.hypergesture.settings.GestureSettingsRepository
import com.h3nc4.hypergesture.setup.SetupChecklist
import com.h3nc4.hypergesture.setup.SetupFacts
import com.h3nc4.hypergesture.setup.SetupFlag
import com.h3nc4.hypergesture.setup.SetupRecord
import com.h3nc4.hypergesture.setup.SetupStateRepository
import com.h3nc4.hypergesture.setup.StopNotifier
import com.h3nc4.hypergesture.setup.setupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Everything the screens show, read once per resume and kept current by the two flows. */
@Stable
internal class AppModel(
    private val context: Context,
    private val settings: GestureSettingsRepository,
    private val setupState: SetupStateRepository,
    private val scope: CoroutineScope,
) {
    val integration: NavigationIntegration get() = NavigationIntegrationFactory.create(context)
    private val keeperSettings = NavigationKeeperSettings(context)
    val snackbar = SnackbarHostState()

    /** Null where the integration has no keeper, so the screen leaves the row out. */
    var keeperGranted: Boolean? by mutableStateOf(integration.keeper?.granted)
        private set
    var keeperEnabled by mutableStateOf(false)
        private set

    var configuration by mutableStateOf(GestureConfiguration())
        private set
    var record by mutableStateOf(SetupRecord())
        private set
    var diagnostics: Diagnostics by mutableStateOf(DiagnosticsCollector.collect(context))
        private set
    var usageAccess by mutableStateOf(hasUsageAccess(context))
        private set
    var assistant by mutableStateOf(readAssistant(context))
        private set
    private var batteryUnrestricted by mutableStateOf(batteryUnrestricted(context))
    private var notificationsAllowed by mutableStateOf(notificationsAllowed(context))

    val status: ServiceStatus get() = diagnostics.serviceStatus

    val checklist: SetupChecklist
        get() = SetupChecklist.evaluate(
            SetupFacts(
                restrictedSettingsApply = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                restrictedSettingsConfirmed = record.restrictedSettingsConfirmed,
                serviceEverEnabled = record.serviceEverEnabled,
                service = status,
                batteryUnrestricted = batteryUnrestricted,
                batteryConfirmed = record.batteryConfirmed,
                autostartApplies = diagnostics.navigationIntegrationAvailable,
                autostartConfirmed = record.autostartConfirmed,
                tileAdded = record.tileAdded,
                notificationsApply = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                notificationsAllowed = notificationsAllowed,
                navigationBarChangeable = diagnostics.navigationIntegrationAvailable,
                navigationBarHidden = diagnostics.navigationMode == NavigationMode.GESTURAL,
            ),
        )

    fun refresh() {
        diagnostics = DiagnosticsCollector.collect(context)
        usageAccess = hasUsageAccess(context)
        assistant = readAssistant(context)
        batteryUnrestricted = batteryUnrestricted(context)
        notificationsAllowed = notificationsAllowed(context)
        keeperGranted = integration.keeper?.granted
        restoreButtonsIfStopped()
        if (status != ServiceStatus.OFF && !record.serviceEverEnabled) {
            setFlag(SetupFlag.SERVICE_EVER_ENABLED, true)
        }
        StopNotifier.sync(context)
    }

    // Catches a kill that skipped the service's unbind, which would leave neither gestures nor buttons.
    private fun restoreButtonsIfStopped() {
        val restore = integration.keeper?.let { ButtonRestore(it, keeperSettings) } ?: return
        scope.launch(Dispatchers.IO) {
            restore.gesturesStopped(HyperGestureAccessibilityService.isRunning())
                ?.let { Log.i(TAG, ButtonRestore.logLine(it)) }
        }
    }

    fun update(transform: (GestureConfiguration) -> GestureConfiguration) {
        scope.launch { settings.update(transform) }
    }

    fun setFlag(flag: SetupFlag, value: Boolean) {
        scope.launch { setupState.set(flag, value) }
    }

    fun switchKeeper(enabled: Boolean) {
        scope.launch { keeperSettings.setEnabled(enabled) }
    }

    /** Reset has no confirmation, so the snackbar offers the old values back instead. */
    fun resetGestureSettings(message: String, undoLabel: String) {
        val previous = configuration
        update { it.withGestureSettingsOf(GestureConfiguration()) }
        scope.launch {
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) update { it.withGestureSettingsOf(previous) }
        }
    }

    internal suspend fun observe() {
        scope.launch { settings.configuration.collect { configuration = it } }
        scope.launch { setupState.record.collect { record = it } }
        scope.launch { keeperSettings.enabled.collect { keeperEnabled = it } }
        // UiAutomation, which uiautomator and screen readers use, unbinds the service for a moment.
        // So a stop shows only once it outlasts that, and a return shows at once.
        HyperGestureAccessibilityService.running.collectLatest { running ->
            if (!running) delay(STOP_GRACE_MS)
            refresh()
        }
    }
}

private const val STOP_GRACE_MS = 3_000L
private const val TAG = "HyperGesture"

@Composable
internal fun rememberAppModel(): AppModel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember {
        AppModel(
            context.applicationContext,
            GestureSettingsRepository(context.applicationContext),
            context.applicationContext.setupState,
            scope,
        )
    }
    LaunchedEffect(model) { model.observe() }
    // Settings changes everything here from another activity, so it is read again on resume.
    LifecycleResumeEffect(model) {
        model.refresh()
        onPauseOrDispose {}
    }
    return model
}
