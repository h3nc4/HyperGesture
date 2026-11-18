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

package com.h3nc4.hypergesture.diagnostics

// A fall from a steady status into STOPPED or DEGRADED waits graceMs before home shows it,
// because a rebind or a strip reinstall passes through both for a moment.
class StatusSettle(private val graceMs: Long, initial: ServiceStatus) {

    var shown: ServiceStatus = initial
        private set

    private var faultSince: Long? = null

    /** Returns how long until the read is due again, or null. [heldMs] is how long the caller saw it hold. */
    fun offer(read: ServiceStatus, nowMs: Long, heldMs: Long = 0): Long? {
        if (!read.isFault() || shown.isFault()) return settle(read)
        val since = faultSince ?: (nowMs - heldMs).also { faultSince = it }
        val left = since + graceMs - nowMs
        return if (left > 0) left else settle(read)
    }

    private fun settle(read: ServiceStatus): Long? {
        shown = read
        faultSince = null
        return null
    }

    private fun ServiceStatus.isFault(): Boolean =
        this == ServiceStatus.STOPPED || this == ServiceStatus.DEGRADED
}
