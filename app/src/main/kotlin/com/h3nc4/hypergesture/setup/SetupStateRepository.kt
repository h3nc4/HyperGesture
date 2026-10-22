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

package com.h3nc4.hypergesture.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Setup facts the app cannot read back from the system, so it records them as they happen. */
data class SetupRecord(
    val tileAdded: Boolean = false,
    val serviceEverEnabled: Boolean = false,
    val restrictedSettingsConfirmed: Boolean = false,
    val batteryConfirmed: Boolean = false,
    val autostartConfirmed: Boolean = false,
)

enum class SetupFlag(internal val key: Preferences.Key<Boolean>) {
    TILE_ADDED(booleanPreferencesKey("tile_added")),
    SERVICE_EVER_ENABLED(booleanPreferencesKey("service_ever_enabled")),
    RESTRICTED_SETTINGS_CONFIRMED(booleanPreferencesKey("restricted_settings_confirmed")),
    BATTERY_CONFIRMED(booleanPreferencesKey("battery_confirmed")),
    AUTOSTART_CONFIRMED(booleanPreferencesKey("autostart_confirmed")),
}

class SetupStateRepository(private val store: DataStore<Preferences>) {

    val record: Flow<SetupRecord> = store.data.map { preferences ->
        fun flag(flag: SetupFlag) = preferences[flag.key] ?: false
        SetupRecord(
            tileAdded = flag(SetupFlag.TILE_ADDED),
            serviceEverEnabled = flag(SetupFlag.SERVICE_EVER_ENABLED),
            restrictedSettingsConfirmed = flag(SetupFlag.RESTRICTED_SETTINGS_CONFIRMED),
            batteryConfirmed = flag(SetupFlag.BATTERY_CONFIRMED),
            autostartConfirmed = flag(SetupFlag.AUTOSTART_CONFIRMED),
        )
    }

    suspend fun set(flag: SetupFlag, value: Boolean) {
        store.edit { it[flag.key] = value }
    }

    suspend fun setTileAdded(added: Boolean) = set(SetupFlag.TILE_ADDED, added)
}
