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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderPrefetchTest {

    private val maxAgeMs = 3_000L

    private val apps = listOf("org.example.mail", "org.example.maps")

    private val prefetch = OrderPrefetch(maxAgeMs)

    @Test
    fun `a fresh order read for this stream is used`() {
        val order = PreparedOrder(stream = 4L, readAtMs = 1_000L, apps = apps)
        assertEquals(apps, order.usableFor(stream = 4L, nowMs = 1_200L, maxAgeMs = maxAgeMs))
    }

    @Test
    fun `an order exactly at the age limit is still used`() {
        val order = PreparedOrder(stream = 4L, readAtMs = 1_000L, apps = apps)
        assertEquals(apps, order.usableFor(stream = 4L, nowMs = 1_000L + maxAgeMs, maxAgeMs = maxAgeMs))
    }

    @Test
    fun `nothing prepared means a fresh read`() {
        assertNull(null.usableFor(stream = 4L, nowMs = 0L, maxAgeMs = maxAgeMs))
    }

    @Test
    fun `an order read for another stream means a fresh read`() {
        val order = PreparedOrder(stream = 3L, readAtMs = 1_000L, apps = apps)
        assertNull(order.usableFor(stream = 4L, nowMs = 1_200L, maxAgeMs = maxAgeMs))
    }

    @Test
    fun `a stale order means a fresh read`() {
        val order = PreparedOrder(stream = 4L, readAtMs = 1_000L, apps = apps)
        assertNull(order.usableFor(stream = 4L, nowMs = 1_001L + maxAgeMs, maxAgeMs = maxAgeMs))
    }

    @Test
    fun `an order stamped after now means a fresh read`() {
        val order = PreparedOrder(stream = 4L, readAtMs = 1_000L, apps = apps)
        assertNull(order.usableFor(stream = 4L, nowMs = 999L, maxAgeMs = maxAgeMs))
    }

    @Test
    fun `an empty order means a fresh read, since a failed read is empty too`() {
        val order = PreparedOrder(stream = 4L, readAtMs = 1_000L, apps = emptyList())
        assertNull(order.usableFor(stream = 4L, nowMs = 1_200L, maxAgeMs = maxAgeMs))
    }

    @Test
    fun `a delivered read is taken by its own stream`() {
        prefetch.request(stream = 1L)
        assertTrue(prefetch.deliver(PreparedOrder(1L, 100L, apps)))
        assertEquals(apps, prefetch.take(stream = 1L, nowMs = 300L))
    }

    @Test
    fun `nothing is ready before the read arrives`() {
        prefetch.request(stream = 1L)
        assertNull(prefetch.take(stream = 1L, nowMs = 300L))
    }

    @Test
    fun `a late read from an older stream is dropped`() {
        prefetch.request(stream = 1L)
        prefetch.request(stream = 2L)
        assertFalse(prefetch.deliver(PreparedOrder(1L, 100L, apps)))
        assertNull(prefetch.take(stream = 1L, nowMs = 300L))
        assertNull(prefetch.take(stream = 2L, nowMs = 300L))
    }

    @Test
    fun `a read nobody asked for is dropped`() {
        assertFalse(prefetch.deliver(PreparedOrder(1L, 100L, apps)))
    }

    @Test
    fun `a new request forgets the read of the stream before`() {
        prefetch.request(stream = 1L)
        prefetch.deliver(PreparedOrder(1L, 100L, apps))
        prefetch.request(stream = 2L)
        assertNull(prefetch.take(stream = 1L, nowMs = 300L))
    }

    @Test
    fun `a taken read is not handed out twice`() {
        prefetch.request(stream = 1L)
        prefetch.deliver(PreparedOrder(1L, 100L, apps))
        prefetch.take(stream = 1L, nowMs = 300L)
        assertNull(prefetch.take(stream = 1L, nowMs = 400L))
    }

    @Test
    fun `a read refused as stale stays unused`() {
        prefetch.request(stream = 1L)
        prefetch.deliver(PreparedOrder(1L, 100L, apps))
        assertNull(prefetch.take(stream = 1L, nowMs = 101L + maxAgeMs))
    }
}
