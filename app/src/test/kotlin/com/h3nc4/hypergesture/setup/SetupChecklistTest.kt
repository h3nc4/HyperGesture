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
        recentsLockApplies = true,
        recentsLockConfirmed = false,
        tileAdded = false,
        notificationsApply = false,
        notificationsAllowed = false,
        navigationBarChangeable = true,
        navigationBarHidden = false,
    )

    private val allSet = firstRun.copy(
        serviceEverEnabled = true,
        service = ServiceStatus.WORKING,
        batteryUnrestricted = true,
        autostartConfirmed = true,
        recentsLockConfirmed = true,
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
        assertEquals(StepState.LOCKED, list[SetupStep.RECENTS_LOCK]?.state)
        assertEquals(SetupStep.ACCESSIBILITY, list[SetupStep.RECENTS_LOCK]?.waitsOn)
        assertEquals(0, list.requiredDone)
        assertEquals(5, list.required.size)
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
        assertEquals(4, list.required.size)
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
    fun `a running service unlocks the recents lock for confirmation`() {
        val facts = firstRun.copy(service = ServiceStatus.WORKING)
        assertEquals(StepState.UNVERIFIED, state(facts, SetupStep.RECENTS_LOCK))
        assertEquals(SetupGroup.REQUIRED, SetupChecklist.evaluate(facts)[SetupStep.RECENTS_LOCK]?.group)
    }

    @Test
    fun `a confirmed recents lock reads done even with the service stopped`() {
        val facts = firstRun.copy(recentsLockConfirmed = true)
        assertEquals(StepState.DONE, state(facts, SetupStep.RECENTS_LOCK))
        assertEquals(StepState.DONE, state(facts.copy(service = ServiceStatus.WORKING), SetupStep.RECENTS_LOCK))
    }

    @Test
    fun `an unconfirmed recents lock keeps the essentials open`() {
        assertFalse(SetupChecklist.evaluate(allSet.copy(recentsLockConfirmed = false)).essentialsDone)
    }

    @Test
    fun `the recents lock is left out off HyperOS`() {
        val list = SetupChecklist.evaluate(firstRun.copy(recentsLockApplies = false))
        assertNull(list[SetupStep.RECENTS_LOCK])
        assertEquals(4, list.required.size)
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
    fun `the keeper switch waits for the service and then for the tile as the bar step does`() {
        assertEquals(SetupStep.ACCESSIBILITY, SetupChecklist.evaluate(firstRun).keeperWaitsOn(keeperOn = false))
        val running = firstRun.copy(service = ServiceStatus.WORKING)
        assertEquals(SetupStep.TILE, SetupChecklist.evaluate(running).keeperWaitsOn(keeperOn = false))
        assertNull(SetupChecklist.evaluate(running.copy(tileAdded = true)).keeperWaitsOn(keeperOn = false))
    }

    @Test
    fun `the keeper switch waits for the tile even with the bar already hidden`() {
        val hidden = firstRun.copy(service = ServiceStatus.WORKING, navigationBarHidden = true)
        val list = SetupChecklist.evaluate(hidden)
        assertEquals(StepState.DONE, list[SetupStep.NAVIGATION_BAR]?.state)
        assertEquals(SetupStep.TILE, list.keeperWaitsOn(keeperOn = false))
    }

    @Test
    fun `a keeper switch already on is never locked so it can be turned off`() {
        assertNull(SetupChecklist.evaluate(firstRun).keeperWaitsOn(keeperOn = true))
    }

    @Test
    fun `a hidden bar reads done and an unchangeable one is a fact`() {
        assertEquals(StepState.DONE, state(firstRun.copy(navigationBarHidden = true), SetupStep.NAVIGATION_BAR))
        val generic = SetupChecklist.evaluate(allSet.copy(navigationBarChangeable = false, navigationBarHidden = false))
        assertEquals(StepState.UNAVAILABLE, generic[SetupStep.NAVIGATION_BAR]?.state)
        assertEquals(0, generic.recommendedLeft)
    }

    @Test
    fun `notifications are a recommended step from Android 13`() {
        assertNull(SetupChecklist.evaluate(firstRun)[SetupStep.NOTIFICATIONS])
        val asked = firstRun.copy(notificationsApply = true)
        assertEquals(SetupGroup.RECOMMENDED, SetupChecklist.evaluate(asked)[SetupStep.NOTIFICATIONS]?.group)
        assertEquals(StepState.TO_DO, state(asked, SetupStep.NOTIFICATIONS))
        assertEquals(StepState.DONE, state(asked.copy(notificationsAllowed = true), SetupStep.NOTIFICATIONS))
    }

    @Test
    fun `everything set leaves nothing to do`() {
        val list = SetupChecklist.evaluate(allSet)
        assertTrue(list.essentialsDone)
        assertEquals(0, list.recommendedLeft)
        assertTrue(list.items.all { it.state == StepState.DONE })
    }
}
