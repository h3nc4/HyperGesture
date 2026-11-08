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

package com.h3nc4.hypergesture.navigation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.h3nc4.hypergesture.settings.resettingPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.navigationKeeperDataStore: DataStore<Preferences> by
    resettingPreferencesDataStore("navigation_keeper")

/** Off until the user turns it on, and only read on a device whose integration has a keeper. */
class NavigationKeeperSettings(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.navigationKeeperDataStore)

    val enabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: false }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    /** Set while the keeper may have left the bar hidden, so a kill with no callback is caught later. */
    val barHidden: Flow<Boolean> = store.data.map { it[BAR_HIDDEN] ?: false }.distinctUntilChanged()

    suspend fun setBarHidden(hidden: Boolean) {
        store.edit { it[BAR_HIDDEN] = hidden }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val BAR_HIDDEN = booleanPreferencesKey("bar_hidden")
    }
}
