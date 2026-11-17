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

package com.h3nc4.hypergesture.service

import org.junit.Assert.assertEquals
import org.junit.Test

class InsetSettleCheckTest {

    private val portrait = ScreenGeometry(1080, 2400, 2.625f, 0, 0, 126)
    private val delays = mutableListOf<Long>()
    private val pending = ArrayDeque<() -> Unit>()

    private fun settleCheck(check: () -> Unit) = InsetSettleCheck(
        schedule = { delay, run ->
            delays += delay
            pending += run
        },
        cancel = { pending.clear() },
        check = check,
    )

    private fun runAll() {
        while (pending.isNotEmpty()) pending.removeFirst()()
    }

    @Test
    fun `an install re-reads a bounded number of times`() {
        var checks = 0
        settleCheck { checks++ }.start()
        runAll()
        assertEquals(InsetSettleCheck.DELAYS_MS.size, checks)
        assertEquals(InsetSettleCheck.DELAYS_MS.toList(), delays)
    }

    @Test
    fun `an inset that lands after the install is measured again`() {
        var reads = 0
        var installed: ScreenGeometry? = null
        lateinit var subject: InsetSettleCheck
        // The first two reads still see the bar from before the switch, as the emulator did.
        val read = { if (reads++ < 2) portrait.copy(navBottomPx = 63) else portrait }
        val install = {
            installed = read()
            subject.start()
        }
        subject = settleCheck {
            reinstallOnGeometryChange(installed, read, install, { throw it })
        }
        install()
        runAll()
        assertEquals(portrait, installed)
    }

    @Test
    fun `a reinstall from a re-read restarts the series once`() {
        var checks = 0
        lateinit var subject: InsetSettleCheck
        subject = settleCheck {
            checks++
            if (checks == 2) subject.start()
        }
        subject.start()
        runAll()
        assertEquals(2 + InsetSettleCheck.DELAYS_MS.size, checks)
    }
}
