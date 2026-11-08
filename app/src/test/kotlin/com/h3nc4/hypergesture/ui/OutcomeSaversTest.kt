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

import com.h3nc4.hypergesture.navigation.EnableRequestOutcome
import com.h3nc4.hypergesture.setup.TileAddOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeSaversTest {

    @Test
    fun `every tile outcome survives a save and restore`() {
        listOf(
            null,
            TileAddOutcome.Added,
            TileAddOutcome.AlreadyAdded,
            TileAddOutcome.Dismissed,
            TileAddOutcome.Unsupported,
            TileAddOutcome.Refused(3),
            TileAddOutcome.Failed("binder: died"),
        ).forEach { assertEquals(it, decodeTileOutcome(encodeTileOutcome(it))) }
    }

    @Test
    fun `every navigation outcome survives a save and restore`() {
        listOf(
            null,
            EnableRequestOutcome.Unsupported,
            EnableRequestOutcome.LaunchedSettings("com.android.settings.NAVIGATION_MODE_SETTINGS"),
            EnableRequestOutcome.Failed("no activity: found"),
        ).forEach { assertEquals(it, decodeEnableOutcome(encodeEnableOutcome(it))) }
    }

    @Test
    fun `a saved value this build cannot read restores no outcome`() {
        assertNull(decodeTileOutcome("renamed"))
        assertNull(decodeTileOutcome("refused:x"))
        assertNull(decodeTileOutcome("refused"))
        assertNull(decodeTileOutcome("failed"))
        assertNull(decodeEnableOutcome("renamed"))
        assertNull(decodeEnableOutcome("launched"))
        assertNull(decodeEnableOutcome("failed"))
    }
}
