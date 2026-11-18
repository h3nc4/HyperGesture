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

import android.content.ClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecordedFailureTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `copy log puts the whole record on the clipboard, beyond what the block shows`() {
        val failure = "label: back\n---\n" + "at com.h3nc4.Frame.line\n".repeat(200)
        var copied = 0
        var cleared = 0
        compose.setContent {
            RecordedFailure(failure, onCopied = { copied++ }, onClear = { cleared++ })
        }

        compose.onNodeWithText("Copy log").performClick()

        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip!!
        assertEquals("HyperGesture failure log", clip.description.label)
        assertEquals(failure, clip.getItemAt(0).text.toString())
        assertEquals(1, copied)
        assertEquals(0, cleared)
    }

    @Test
    fun `clear sits beside copy and copies nothing`() {
        var cleared = 0
        compose.setContent { RecordedFailure("label: home", onCopied = {}, onClear = { cleared++ }) }

        compose.onNodeWithText("Clear").performClick()

        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals(false, clipboard.hasPrimaryClip())
        assertEquals(1, cleared)
    }
}
