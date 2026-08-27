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
import androidx.datastore.preferences.preferencesDataStore
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gestureDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "gesture_configuration")

/** Defaults are never duplicated here: a missing key falls back to [GestureConfiguration]. */
class GestureSettingsRepository(private val context: Context) {

    val configuration: Flow<GestureConfiguration> =
        context.gestureDataStore.data.map(::toConfiguration)

    suspend fun read(): GestureConfiguration = configuration.first()

    suspend fun update(transform: (GestureConfiguration) -> GestureConfiguration) {
        val updated = transform(read())
        context.gestureDataStore.edit { preferences ->
            preferences[EDGE_WIDTH_DP] = updated.edgeWidthDp
            preferences[SIDE_MIN_DISTANCE_DP] = updated.sideMinimumSwipeDistanceDp
            preferences[BOTTOM_MIN_DISTANCE_DP] = updated.bottomMinimumSwipeDistanceDp
            preferences[MAX_ARM_DURATION_MS] = updated.maximumArmDurationMs
            preferences[RECENTS_HOLD_MS] = updated.recentsHoldMs
            preferences[HOLD_STILLNESS_DP] = updated.holdStillnessDp
            preferences[OFF_AXIS_TOLERANCE] = updated.offAxisToleranceRatio
            preferences[LEFT_EDGE_BACK] = updated.leftEdgeBackEnabled
            preferences[RIGHT_EDGE_BACK] = updated.rightEdgeBackEnabled
            preferences[HAPTIC_FEEDBACK] = updated.hapticFeedbackEnabled
        }
    }

    private fun toConfiguration(preferences: Preferences): GestureConfiguration {
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
            recentsHoldMs = debugRecentsHoldMs() ?: storedHoldMs,
            holdStillnessDp = preferences[HOLD_STILLNESS_DP] ?: defaults.holdStillnessDp,
            offAxisToleranceRatio =
                preferences[OFF_AXIS_TOLERANCE] ?: defaults.offAxisToleranceRatio,
            leftEdgeBackEnabled = preferences[LEFT_EDGE_BACK] ?: defaults.leftEdgeBackEnabled,
            rightEdgeBackEnabled = preferences[RIGHT_EDGE_BACK] ?: defaults.rightEdgeBackEnabled,
            hapticFeedbackEnabled = preferences[HAPTIC_FEEDBACK] ?: defaults.hapticFeedbackEnabled,
        )
    }

    /**
     * Test seam for the emulator suite, which cannot deliver a gap-free touch stream: on a
     * loaded machine input stalls past the hold window and a continuous swipe is read as a
     * hold. Only consulted in a debuggable build, so a release APK never reaches it.
     */
    private fun debugRecentsHoldMs(): Long? {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return runCatching {
            Settings.Global.getString(context.contentResolver, RECENTS_HOLD_OVERRIDE)?.toLong()
        }.getOrNull()
    }

    private companion object {
        const val RECENTS_HOLD_OVERRIDE = "hypergesture_recents_hold_ms"

        val EDGE_WIDTH_DP = floatPreferencesKey("edge_width_dp")
        val SIDE_MIN_DISTANCE_DP = floatPreferencesKey("side_minimum_swipe_distance_dp")
        val BOTTOM_MIN_DISTANCE_DP = floatPreferencesKey("bottom_minimum_swipe_distance_dp")
        val MAX_ARM_DURATION_MS = longPreferencesKey("maximum_arm_duration_ms")
        val RECENTS_HOLD_MS = longPreferencesKey("recents_hold_ms")
        val HOLD_STILLNESS_DP = floatPreferencesKey("hold_stillness_dp")
        val OFF_AXIS_TOLERANCE = floatPreferencesKey("off_axis_tolerance_ratio")
        val LEFT_EDGE_BACK = booleanPreferencesKey("left_edge_back_enabled")
        val RIGHT_EDGE_BACK = booleanPreferencesKey("right_edge_back_enabled")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
    }
}
