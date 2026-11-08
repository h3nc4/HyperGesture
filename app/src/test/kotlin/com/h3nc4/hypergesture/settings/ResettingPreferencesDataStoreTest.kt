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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.h3nc4.hypergesture.FilesContext
import com.h3nc4.hypergesture.diagnostics.FailureLog
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val Context.corruptible: DataStore<Preferences> by resettingPreferencesDataStore("corruptible")

class ResettingPreferencesDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a corrupt file reads as empty preferences, takes writes again and is recorded`() = runBlocking {
        val context = FilesContext(folder.root)
        File(folder.root, "datastore/corruptible.preferences_pb").apply {
            parentFile?.mkdirs()
            writeBytes(TRUNCATED_PROTOBUF)
        }

        assertEquals(emptyPreferences(), context.corruptible.data.first())
        context.corruptible.edit { it[KEY] = true }
        assertEquals(true, context.corruptible.data.first()[KEY])
        assertTrue(FailureLog.read(context).orEmpty().contains("label: corrupt corruptible reset to defaults"))
    }

    private companion object {
        val KEY = booleanPreferencesKey("key")

        // Field 1 claims 127 bytes and one follows.
        val TRUNCATED_PROTOBUF = byteArrayOf(0x0A, 0x7F, 0x01)
    }
}
