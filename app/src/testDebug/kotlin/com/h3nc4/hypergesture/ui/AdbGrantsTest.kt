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
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdbGrantsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pkg get() = compose.activity.packageName

    @Test
    fun `every grant shows its command and whether it is granted`() {
        compose.setContent {
            AdbGrants {
                listOf(GrantState(AdbGrant.SECURE_SETTINGS, true), GrantState(AdbGrant.USAGE_ACCESS, false))
            }
        }

        compose.onNodeWithText("adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS").assertExists()
        compose.onNodeWithText("adb shell appops set $pkg GET_USAGE_STATS allow").assertExists()
        compose.onNodeWithTag(AdbGrant.SECURE_SETTINGS.tag, useUnmergedTree = true).assertTextEquals("Granted")
        compose.onNodeWithTag(AdbGrant.USAGE_ACCESS.tag, useUnmergedTree = true).assertTextEquals("Missing")
    }

    @Test
    fun `a grant made while the app was away shows on resume`() {
        var usage = false
        var reads = 0
        compose.setContent {
            AdbGrants {
                reads++
                listOf(GrantState(AdbGrant.USAGE_ACCESS, usage))
            }
        }
        compose.onNodeWithTag(AdbGrant.USAGE_ACCESS.tag, useUnmergedTree = true).assertTextEquals("Missing")

        usage = true
        val before = reads
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertEquals(before, reads)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.onNodeWithTag(AdbGrant.USAGE_ACCESS.tag, useUnmergedTree = true).assertTextEquals("Granted")
    }

    @Test
    fun `copy puts the exact command on the clipboard`() {
        compose.setContent {
            AdbGrants {
                listOf(GrantState(AdbGrant.SECURE_SETTINGS, false), GrantState(AdbGrant.USAGE_ACCESS, false))
            }
        }

        compose.onAllNodesWithText("Copy")[1].performClick()

        val clip = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("adb shell appops set $pkg GET_USAGE_STATS allow", clip.getItemAt(0).text.toString())
        compose.onNodeWithText("Copied").assertExists()
    }
}
