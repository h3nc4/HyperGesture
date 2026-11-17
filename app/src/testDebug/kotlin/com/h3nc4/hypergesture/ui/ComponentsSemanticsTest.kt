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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.h3nc4.hypergesture.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentsSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `a toggle row is one switch that carries its label and state`() {
        var checked by mutableStateOf(false)
        compose.setContent {
            ToggleRow(label = "Left edge back", checked = checked, tag = "row") { checked = it }
        }

        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithTag("row")
            .assert(isSwitch)
            .assertIsOff()
            .assert(hasText("Left edge back"))
            .performClick()
        assertEquals(true, checked)
        compose.onNodeWithTag("row").assertIsOn()
    }

    @Test
    fun `a split row labels its switch with the row title`() {
        compose.setContent {
            SplitSwitchRow(
                title = "App switching",
                supporting = "On",
                checked = true,
                rowTag = "row",
                switchTag = "switch",
                onOpen = {},
                onChange = {},
            )
        }

        compose.onNodeWithTag("switch")
            .assert(hasContentDescription("App switching"))
            .assertIsOn()
    }

    @Test
    fun `a slider reads its value in the unit shown rather than a percent`() {
        compose.setContent {
            ThresholdSlider(
                label = "Edge width",
                value = 20f,
                range = 8f..64f,
                formatter = { stringResource(R.string.value_dp, it) },
                onCommit = {},
            )
        }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(hasContentDescription("Edge width"))
            .assert(hasStateDescription("20 dp"))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(20f, 8f..64f),
                ),
            )
    }

    @Test
    fun `a section header is a heading`() {
        compose.setContent { SectionHeader("Edges") }

        compose.onNodeWithText("Edges").assert(isHeading())
    }
}
