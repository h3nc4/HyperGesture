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

/** An app order read ahead for one touch stream, newest first, and when the read started. */
internal data class PreparedOrder(val stream: Long, val readAtMs: Long, val apps: List<String>)

// Null sends the caller to read the order itself. An empty read is never trusted, since a
// failed read looks the same.
internal fun PreparedOrder?.usableFor(stream: Long, nowMs: Long, maxAgeMs: Long): List<String>? {
    val prepared = this ?: return null
    val age = nowMs - prepared.readAtMs
    val usable = prepared.stream == stream && age in 0..maxAgeMs && prepared.apps.isNotEmpty()
    return if (usable) prepared.apps else null
}

// The order read off the main thread for the latest touch armed as a switch. Only the main
// thread touches it, and a finished read is posted back there.
internal class OrderPrefetch(private val maxAgeMs: Long) {

    private var requested: Long? = null

    private var prepared: PreparedOrder? = null

    /** Forgets any earlier read, so only [stream]'s own read can be taken. */
    fun request(stream: Long) {
        requested = stream
        prepared = null
    }

    /** False when a newer stream asked since, and the late read is dropped. */
    fun deliver(order: PreparedOrder): Boolean {
        if (order.stream != requested) return false
        prepared = order
        return true
    }

    /** The order for [stream] when it is ready and fresh, consumed so no later touch reuses it. */
    fun take(stream: Long, nowMs: Long): List<String>? {
        val apps = prepared.usableFor(stream, nowMs, maxAgeMs)
        if (apps != null) prepared = null
        return apps
    }
}
