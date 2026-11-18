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

/**
 * Runs on the main thread from a rotation or display change, where an uncaught throw kills
 * the process and gets the service flagged as malfunctioning.
 */
internal fun reinstallOnGeometryChange(
    last: ScreenGeometry?,
    read: () -> ScreenGeometry,
    reinstall: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    try {
        if (read() != last) reinstall()
    } catch (t: Throwable) {
        onFailure(t)
    }
}

/**
 * Insets can land after the last signal able to report them. A navigation-mode switch
 * changes the configuration about 650 ms before the new bar inset is readable.
 */
internal class InsetSettleCheck(
    private val schedule: (delayMs: Long, check: () -> Unit) -> Unit,
    private val cancel: () -> Unit,
    private val check: () -> Unit,
) {

    private var generation = 0

    /** Restarts the re-reads, so a reinstall that one of them caused gets its own full series. */
    fun start() {
        cancel()
        next(++generation, 0)
    }

    private fun next(owner: Int, step: Int) {
        if (owner != generation || step >= DELAYS_MS.size) return
        schedule(DELAYS_MS[step]) {
            if (owner == generation) {
                check()
                next(owner, step + 1)
            }
        }
    }

    companion object {
        val DELAYS_MS = longArrayOf(500L, 1_000L, 2_000L, 4_000L)
    }
}
