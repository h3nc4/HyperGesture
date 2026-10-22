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

package com.h3nc4.hypergesture.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreUpdateStore(private val store: DataStore<Preferences>) : UpdateStore {

    private suspend fun preferences() = store.data.first()

    override suspend fun enabled(): Boolean = preferences()[ENABLED] ?: false

    override suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    override suspend fun lastAttemptMs(): Long? = preferences()[LAST_ATTEMPT_MS]

    override suspend fun recordAttempt(atMs: Long, body: String?) {
        store.edit {
            it[LAST_ATTEMPT_MS] = atMs
            if (body != null) it[LAST_BODY] = body
        }
    }

    override suspend fun cachedBody(): String? = preferences()[LAST_BODY]

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LAST_ATTEMPT_MS = longPreferencesKey("last_attempt_ms")
        val LAST_BODY = stringPreferencesKey("last_release_json")
    }
}
