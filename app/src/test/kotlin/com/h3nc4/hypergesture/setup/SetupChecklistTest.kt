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

import com.h3nc4.hypergesture.diagnostics.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {

    private val firstRun = SetupFacts(
        restrictedSettingsApply = true,
        restrictedSettingsConfirmed = false,
        serviceEverEnabled = false,
        service = ServiceStatus.OFF,
        batteryUnrestricted = false,
        batteryConfirmed = false,
        autostartApplies = true,
        autostartConfirmed = false,
        tileAdded = false,
        navigationBarChangeable = true,
        navigationBarHidden = false,
    )

    private val allSet = firstRun.copy(
        serviceEverEnabled = true,
        service = ServiceStatus.WORKING,
        batteryUnrestricted = true,
        autostartConfirmed = true,
        tileAdded = true,
        navigationBarHidden = true,
    )

    private fun state(facts: SetupFacts, step: SetupStep) =
        SetupChecklist.evaluate(facts)[step]?.state

    @Test
    fun `first run asks for every required step with later ones locked`() {
        val list = SetupChecklist.evaluate(firstRun)
        assertEquals(StepState.UNVERIFIED, list[SetupStep.RESTRICTED_SETTINGS]?.state)
        assertEquals(StepState.TO_DO, list[SetupStep.ACCESSIBILITY]?.state)
        assertEquals(StepState.LOCKED, list[SetupStep.BATTERY]?.state)
        assertEquals(SetupStep.ACCESSIBILITY, list[SetupStep.BATTERY]?.waitsOn)
        assertEquals(StepState.LOCKED, list[SetupStep.AUTOSTART]?.state)
        assertEquals(0, list.requiredDone)
        assertEquals(4, list.required.size)
        assertFalse(list.essentialsDone)
    }

    @Test
    fun `a confirmation marks restricted settings done`() {
        val facts = firstRun.copy(restrictedSettingsConfirmed = true)
        assertEquals(StepState.DONE, state(facts, SetupStep.RESTRICTED_SETTINGS))
    }

    @Test
    fun `a service that was ever on proves restricted settings were allowed`() {
        assertEquals(StepState.DONE, state(firstRun.copy(serviceEverEnabled = true), SetupStep.RESTRICTED_SETTINGS))
        assertEquals(StepState.DONE, state(firstRun.copy(service = ServiceStatus.STOPPED), SetupStep.RESTRICTED_SETTINGS))
    }

    @Test
    fun `restricted settings are left out where they do not exist`() {
        val list = SetupChecklist.evaluate(firstRun.copy(restrictedSettingsApply = false))
        assertNull(list[SetupStep.RESTRICTED_SETTINGS])
        assertEquals(3, list.required.size)
    }

    @Test
    fun `a stopped service still leaves accessibility to do`() {
        assertEquals(StepState.TO_DO, state(firstRun.copy(service = ServiceStatus.STOPPED), SetupStep.ACCESSIBILITY))
    }

    @Test
    fun `a running service unlocks battery and autostart for confirmation`() {
        val facts = firstRun.copy(service = ServiceStatus.WORKING)
        assertEquals(StepState.DONE, state(facts, SetupStep.ACCESSIBILITY))
        assertEquals(StepState.UNVERIFIED, state(facts, SetupStep.BATTERY))
        assertEquals(StepState.UNVERIFIED, state(facts, SetupStep.AUTOSTART))
    }

    @Test
    fun `an unrestricted battery reads done even while locked`() {
        assertEquals(StepState.DONE, state(firstRun.copy(batteryUnrestricted = true), SetupStep.BATTERY))
    }

    @Test
    fun `a confirmed battery reads done where the app cannot see the restriction`() {
        assertEquals(StepState.DONE, state(firstRun.copy(batteryConfirmed = true), SetupStep.BATTERY))
    }

    @Test
    fun `autostart is left out off HyperOS`() {
        val list = SetupChecklist.evaluate(firstRun.copy(autostartApplies = false))
        assertNull(list[SetupStep.AUTOSTART])
    }

    @Test
    fun `essentials are done once every required step is`() {
        val facts = allSet.copy(tileAdded = false, navigationBarHidden = false)
        val list = SetupChecklist.evaluate(facts)
        assertTrue(list.essentialsDone)
        assertEquals(2, list.recommendedLeft)
    }

    @Test
    fun `the navigation bar waits for the service and then for the tile`() {
        assertEquals(SetupStep.ACCESSIBILITY, SetupChecklist.evaluate(firstRun)[SetupStep.NAVIGATION_BAR]?.waitsOn)
        val running = firstRun.copy(service = ServiceStatus.WORKING)
        val bar = SetupChecklist.evaluate(running)[SetupStep.NAVIGATION_BAR]
        assertEquals(StepState.LOCKED, bar?.state)
        assertEquals(SetupStep.TILE, bar?.waitsOn)
        assertEquals(StepState.TO_DO, state(running.copy(tileAdded = true), SetupStep.NAVIGATION_BAR))
    }

    @Test
    fun `a hidden bar reads done and an unchangeable one is a fact`() {
        assertEquals(StepState.DONE, state(firstRun.copy(navigationBarHidden = true), SetupStep.NAVIGATION_BAR))
        val generic = SetupChecklist.evaluate(allSet.copy(navigationBarChangeable = false, navigationBarHidden = false))
        assertEquals(StepState.UNAVAILABLE, generic[SetupStep.NAVIGATION_BAR]?.state)
        assertEquals(0, generic.recommendedLeft)
    }

    @Test
    fun `everything set leaves nothing to do`() {
        val list = SetupChecklist.evaluate(allSet)
        assertTrue(list.essentialsDone)
        assertEquals(0, list.recommendedLeft)
        assertTrue(list.items.all { it.state == StepState.DONE })
    }
}
