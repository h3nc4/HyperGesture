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

import android.app.StatusBarManager
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.diagnostics.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileModelTest {

    @Test
    fun `working service shows an active tile that opens the app`() {
        val face = TileFace.of(ServiceStatus.WORKING)
        assertTrue(face.active)
        assertEquals(R.string.status_working, face.subtitle)
        assertEquals(TileTarget.APP, face.target)
    }

    @Test
    fun `stopped service shows an inactive tile that opens accessibility settings`() {
        val face = TileFace.of(ServiceStatus.STOPPED)
        assertFalse(face.active)
        assertEquals(R.string.status_stopped, face.subtitle)
        assertEquals(TileTarget.ACCESSIBILITY_SETTINGS, face.target)
    }

    @Test
    fun `service that is off shows an inactive tile that opens accessibility settings`() {
        val face = TileFace.of(ServiceStatus.OFF)
        assertFalse(face.active)
        assertEquals(R.string.status_off, face.subtitle)
        assertEquals(TileTarget.ACCESSIBILITY_SETTINGS, face.target)
    }

    @Test
    fun `details page is used only when it resolves and its permission is held`() {
        val guard = "android.permission.OPEN_ACCESSIBILITY_DETAILS_SETTINGS"
        assertTrue(detailsReachable(resolves = true, requiredPermission = null) { false })
        assertTrue(detailsReachable(resolves = true, requiredPermission = guard) { it == guard })
        assertFalse(detailsReachable(resolves = true, requiredPermission = guard) { false })
        assertFalse(detailsReachable(resolves = false, requiredPermission = null) { true })
    }

    @Test
    fun `the three answers of the add prompt map to their outcomes`() {
        assertEquals(
            TileAddOutcome.Added,
            TileAddOutcome.fromResult(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED),
        )
        assertEquals(
            TileAddOutcome.AlreadyAdded,
            TileAddOutcome.fromResult(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED),
        )
        assertEquals(
            TileAddOutcome.Dismissed,
            TileAddOutcome.fromResult(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED),
        )
    }

    @Test
    fun `an error code is reported as a refusal that keeps the code`() {
        val code = StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND
        assertEquals(TileAddOutcome.Refused(code), TileAddOutcome.fromResult(code))
    }

    @Test
    fun `an unknown code from a vendor build is a refusal too`() {
        assertEquals(TileAddOutcome.Refused(-7), TileAddOutcome.fromResult(-7))
    }

    @Test
    fun `the add prompt exists from Android 13`() {
        assertFalse(TileAddOutcome.promptAvailable(32))
        assertTrue(TileAddOutcome.promptAvailable(33))
        assertTrue(TileAddOutcome.promptAvailable(37))
    }

    @Test
    fun `a request that throws is reported by its message, else by its type`() {
        assertEquals(TileAddOutcome.Failed("no binder"), TileAddOutcome.Failed(IllegalStateException("no binder")))
        assertEquals(
            TileAddOutcome.Failed("java.lang.SecurityException"),
            TileAddOutcome.Failed(SecurityException()),
        )
    }
}
