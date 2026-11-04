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

import org.junit.Assert.assertEquals
import org.junit.Test

class StopNoticeTest {

    private val stopped = StopNoticeFacts(
        running = false,
        switchedOn = false,
        serviceEverEnabled = true,
        alreadyPosted = false,
        notificationsAllowed = true,
    )

    private fun decide(facts: StopNoticeFacts) = StopNotice.decide(facts)

    @Test
    fun `a used service switched off posts the notice`() {
        assertEquals(StopNoticeAction.POST, decide(stopped))
    }

    @Test
    fun `a fresh install that never ran the service gets nothing`() {
        assertEquals(StopNoticeAction.KEEP, decide(stopped.copy(serviceEverEnabled = false)))
    }

    @Test
    fun `a running service clears the notice`() {
        assertEquals(StopNoticeAction.CLEAR, decide(stopped.copy(running = true, switchedOn = true)))
        assertEquals(StopNoticeAction.CLEAR, decide(stopped.copy(running = true, alreadyPosted = true)))
    }

    @Test
    fun `a first run clears too, which records that the service was used`() {
        assertEquals(StopNoticeAction.CLEAR, decide(stopped.copy(running = true, serviceEverEnabled = false)))
    }

    @Test
    fun `a switch left on waits for the framework to bind`() {
        assertEquals(StopNoticeAction.KEEP, decide(stopped.copy(switchedOn = true)))
    }

    @Test
    fun `a stop already noticed is not posted again`() {
        assertEquals(StopNoticeAction.KEEP, decide(stopped.copy(alreadyPosted = true)))
    }

    @Test
    fun `refused notifications post nothing and leave the stop unnoticed`() {
        assertEquals(StopNoticeAction.KEEP, decide(stopped.copy(notificationsAllowed = false)))
    }

    @Test
    fun `the full cycle posts once and clears on return`() {
        assertEquals(StopNoticeAction.POST, decide(stopped))
        // Boot and app resume after the post, including after the user dismissed it.
        assertEquals(StopNoticeAction.KEEP, decide(stopped.copy(alreadyPosted = true)))
        assertEquals(StopNoticeAction.CLEAR, decide(stopped.copy(running = true, switchedOn = true, alreadyPosted = true)))
        // Gestures returned, so the flag reset and a later stop posts again.
        assertEquals(StopNoticeAction.POST, decide(stopped))
    }
}
