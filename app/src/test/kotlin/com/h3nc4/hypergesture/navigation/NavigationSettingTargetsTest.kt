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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
package com.h3nc4.hypergesture.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSettingTargetsTest {
    @Test
    fun onlyTheTwoOptedInNavigationValuesAreVerified() {
        assertEquals(
            listOf(
                SettingTarget("global", "force_fsg_nav_bar", "1"),
                SettingTarget("secure", "navigation_mode", "2"),
            ),
            NavigationSettingTargets.all,
        )
        assertTrue(
            NavigationSettingTargets.verified(
                mapOf("force_fsg_nav_bar" to "1", "navigation_mode" to "2"),
            ),
        )
        assertFalse(
            NavigationSettingTargets.verified(
                mapOf("force_fsg_nav_bar" to "1", "navigation_mode" to "0"),
            ),
        )
        assertFalse(NavigationSettingTargets.verified(mapOf("force_fsg_nav_bar" to "1")))
    }

    @Test
    fun buildRecoveryScriptContainsEssentialCommands() {
        val script = ShizukuNavigationRecovery.buildRecoveryScript("com.h3nc4.hypergesture")
        assertTrue(script.contains("settings put global force_fsg_nav_bar 1"))
        assertTrue(script.contains("settings put secure navigation_mode 2"))
        assertTrue(script.contains("settings --user 0 put secure navigation_mode 2"))
        assertTrue(script.contains("settings --user 0 put global force_fsg_nav_bar 1"))
        assertTrue(script.contains("settings put global use_gesture_version_three 1"))
        assertTrue(script.contains("cmd overlay disable com.android.internal.systemui.navbar.threebutton"))
        assertTrue(script.contains("appops set com.h3nc4.hypergesture 10008 allow"))
        assertTrue(script.contains("appops set com.h3nc4.hypergesture 10021 allow"))
        assertTrue(script.contains("dumpsys deviceidle whitelist +com.h3nc4.hypergesture"))
        assertTrue(script.contains("am set-standby-bucket com.h3nc4.hypergesture active"))
    }

    @Test
    fun parseOutputExtractsValuesAndValidatesVerification() {
        val goodOutput = """
            [x] overlay disabled
            FSG=1
            NAV=2
        """.trimIndent()
        val parsedGood = ShizukuNavigationRecovery.parseOutput(goodOutput)
        assertEquals("1", parsedGood.fsg)
        assertEquals("2", parsedGood.nav)
        assertTrue(parsedGood.isVerified)

        val badOutput = "FSG=0\nNAV=2"
        val parsedBad = ShizukuNavigationRecovery.parseOutput(badOutput)
        assertFalse(parsedBad.isVerified)

        val emptyOutput = ""
        val parsedEmpty = ShizukuNavigationRecovery.parseOutput(emptyOutput)
        assertFalse(parsedEmpty.isVerified)
    }
}
