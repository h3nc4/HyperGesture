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
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.h3nc4.hypergesture.diagnostics.FailureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// A corrupt file starts over from empty preferences, so defaults apply instead of every read
// throwing. The library delegate hands its handler no Context, which FailureLog needs.
internal fun resettingPreferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    ResettingPreferencesDataStore(name)

private class ResettingPreferencesDataStore(
    private val name: String,
) : ReadOnlyProperty<Context, DataStore<Preferences>> {

    private val lock = Any()
    private var instance: DataStore<Preferences>? = null

    override fun getValue(thisRef: Context, property: KProperty<*>): DataStore<Preferences> =
        synchronized(lock) { instance ?: create(thisRef.applicationContext).also { instance = it } }

    private fun create(app: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { exception ->
            FailureLog.record(app, "corrupt $name reset to defaults", exception)
            emptyPreferences()
        },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { app.preferencesDataStoreFile(name) },
    )
}
