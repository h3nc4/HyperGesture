// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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

package com.h3nc4.hypergesture.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gestureDataStore: DataStore<Preferences> by
    resettingPreferencesDataStore("gesture_configuration")

/** Defaults are never duplicated here: a missing key falls back to [GestureConfiguration]. */
class GestureSettingsRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val debugOverrides: () -> DebugOverrides,
) {

    constructor(
        context: Context,
        store: DataStore<Preferences> = context.gestureDataStore,
    ) : this(store, { DebugOverrides.read(context) })

    val configuration: Flow<GestureConfiguration> =
        store.data.map { toConfiguration(it, debugOverrides()) }

    suspend fun read(): GestureConfiguration = configuration.first()

    suspend fun update(transform: (GestureConfiguration) -> GestureConfiguration) {
        store.edit { preferences ->
            val overrides = debugOverrides()
            val updated = transform(toConfiguration(preferences, overrides))
            preferences[EDGE_WIDTH_DP] = updated.edgeWidthDp
            preferences[SIDE_MIN_DISTANCE_DP] = updated.sideMinimumSwipeDistanceDp
            preferences[BOTTOM_MIN_DISTANCE_DP] = updated.bottomMinimumSwipeDistanceDp
            preferences[MAX_ARM_DURATION_MS] = updated.maximumArmDurationMs
            // An overridden field keeps its stored value, so a debug run never persists the override.
            if (overrides.recentsHoldMs == null) preferences[RECENTS_HOLD_MS] = updated.recentsHoldMs
            preferences[HOLD_STILLNESS_DP] = updated.holdStillnessDp
            preferences[OFF_AXIS_TOLERANCE] = updated.offAxisToleranceRatio
            if (overrides.appSwitchEnabled == null) preferences[APP_SWITCH] = updated.appSwitchEnabled
            preferences[LEFT_EDGE_BACK] = updated.leftEdgeBackEnabled
            preferences[RIGHT_EDGE_BACK] = updated.rightEdgeBackEnabled
            preferences[HAPTIC_FEEDBACK] = updated.hapticFeedbackEnabled
            preferences[LANDSCAPE_CONFIRM] = updated.landscapeConfirmEnabled
            preferences[CIRCLE_TO_SEARCH] = updated.circleToSearchEnabled
            preferences[CIRCLE_TO_SEARCH_SYSTEM_TIMING] = updated.circleToSearchFollowsSystemTiming
            preferences[CIRCLE_TO_SEARCH_HOLD_MS] = updated.circleToSearchHoldMs
        }
    }

    private fun toConfiguration(preferences: Preferences, overrides: DebugOverrides): GestureConfiguration {
        val defaults = GestureConfiguration()
        val storedHoldMs = preferences[RECENTS_HOLD_MS] ?: defaults.recentsHoldMs
        return GestureConfiguration(
            edgeWidthDp = preferences[EDGE_WIDTH_DP] ?: defaults.edgeWidthDp,
            sideMinimumSwipeDistanceDp =
                preferences[SIDE_MIN_DISTANCE_DP] ?: defaults.sideMinimumSwipeDistanceDp,
            bottomMinimumSwipeDistanceDp =
                preferences[BOTTOM_MIN_DISTANCE_DP] ?: defaults.bottomMinimumSwipeDistanceDp,
            maximumArmDurationMs =
                preferences[MAX_ARM_DURATION_MS] ?: defaults.maximumArmDurationMs,
            recentsHoldMs = overrides.recentsHoldMs ?: storedHoldMs,
            holdStillnessDp = preferences[HOLD_STILLNESS_DP] ?: defaults.holdStillnessDp,
            offAxisToleranceRatio =
                preferences[OFF_AXIS_TOLERANCE] ?: defaults.offAxisToleranceRatio,
            appSwitchEnabled = overrides.appSwitchEnabled ?: (preferences[APP_SWITCH] ?: defaults.appSwitchEnabled),
            leftEdgeBackEnabled = preferences[LEFT_EDGE_BACK] ?: defaults.leftEdgeBackEnabled,
            rightEdgeBackEnabled = preferences[RIGHT_EDGE_BACK] ?: defaults.rightEdgeBackEnabled,
            hapticFeedbackEnabled = preferences[HAPTIC_FEEDBACK] ?: defaults.hapticFeedbackEnabled,
            landscapeConfirmEnabled =
                preferences[LANDSCAPE_CONFIRM] ?: defaults.landscapeConfirmEnabled,
            circleToSearchEnabled =
                preferences[CIRCLE_TO_SEARCH] ?: defaults.circleToSearchEnabled,
            circleToSearchFollowsSystemTiming = preferences[CIRCLE_TO_SEARCH_SYSTEM_TIMING]
                ?: defaults.circleToSearchFollowsSystemTiming,
            circleToSearchHoldMs =
                preferences[CIRCLE_TO_SEARCH_HOLD_MS] ?: defaults.circleToSearchHoldMs,
        )
    }

    private companion object {
        val EDGE_WIDTH_DP = floatPreferencesKey("edge_width_dp")
        val SIDE_MIN_DISTANCE_DP = floatPreferencesKey("side_minimum_swipe_distance_dp")
        val BOTTOM_MIN_DISTANCE_DP = floatPreferencesKey("bottom_minimum_swipe_distance_dp")
        val MAX_ARM_DURATION_MS = longPreferencesKey("maximum_arm_duration_ms")
        val RECENTS_HOLD_MS = longPreferencesKey("recents_hold_ms")
        val HOLD_STILLNESS_DP = floatPreferencesKey("hold_stillness_dp")
        val OFF_AXIS_TOLERANCE = floatPreferencesKey("off_axis_tolerance_ratio")
        val APP_SWITCH = booleanPreferencesKey("app_switch_enabled")
        val LEFT_EDGE_BACK = booleanPreferencesKey("left_edge_back_enabled")
        val RIGHT_EDGE_BACK = booleanPreferencesKey("right_edge_back_enabled")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
        val LANDSCAPE_CONFIRM = booleanPreferencesKey("landscape_confirm_enabled")
        val CIRCLE_TO_SEARCH = booleanPreferencesKey("circle_to_search_enabled")
        val CIRCLE_TO_SEARCH_SYSTEM_TIMING =
            booleanPreferencesKey("circle_to_search_follows_system_timing")
        val CIRCLE_TO_SEARCH_HOLD_MS = longPreferencesKey("circle_to_search_hold_ms")
    }
}

/** Values a debuggable build reads from `Settings.Global`, so the emulator suite needs no taps. */
internal data class DebugOverrides(
    val recentsHoldMs: Long? = null,
    val appSwitchEnabled: Boolean? = null,
) {
    companion object {
        private const val RECENTS_HOLD_OVERRIDE = "hypergesture_recents_hold_ms"
        private const val APP_SWITCH_OVERRIDE = "hypergesture_app_switch"

        // A loaded emulator stalls input past the hold window. The suite lengthens that window, or a swipe reads as a hold.
        fun read(context: Context): DebugOverrides {
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return DebugOverrides()
            return DebugOverrides(
                recentsHoldMs = global(context, RECENTS_HOLD_OVERRIDE)?.toLongOrNull(),
                appSwitchEnabled = global(context, APP_SWITCH_OVERRIDE)?.let {
                    it == "1" || it.equals("true", ignoreCase = true)
                },
            )
        }

        private fun global(context: Context, name: String): String? =
            runCatching { Settings.Global.getString(context.contentResolver, name) }.getOrNull()
    }
}
