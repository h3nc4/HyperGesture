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

package com.h3nc4.hypergesture.gesture

/** At most [capacity] samples. When full it halves its interior and doubles its stride,
 * so a long drag keeps an even spread of its path and always its last sample. */
class TouchRecording(capacity: Int) {

    init {
        require(capacity >= MIN_CAPACITY) { "capacity $capacity is below $MIN_CAPACITY" }
    }

    private val kept = ArrayList<TouchSample>(capacity)

    // Odd, so halving keeps an even spacing and leaves room for the newest sample.
    private val thinAt = capacity - 1 - capacity % 2

    // The newest sample between two strides. A replay must end where the finger lifted.
    private var newest: TouchSample? = null
    private var stride = 1
    private var skipped = 0

    val first: TouchSample? get() = kept.firstOrNull()

    val size: Int get() = kept.size + if (newest == null) 0 else 1

    fun isEmpty(): Boolean = kept.isEmpty()

    fun clear() {
        kept.clear()
        newest = null
        stride = 1
        skipped = 0
    }

    fun add(sample: TouchSample) {
        if (kept.isNotEmpty() && ++skipped < stride) {
            newest = sample
            return
        }
        if (kept.size == thinAt) {
            val thinned = thinSamples(kept)
            kept.clear()
            kept.addAll(thinned)
            stride *= 2
            newest = sample
            return
        }
        skipped = 0
        newest = null
        kept += sample
    }

    fun snapshot(): List<TouchSample> = newest?.let { kept + it } ?: kept.toList()

    private companion object {
        const val MIN_CAPACITY = 4
    }
}

/** Drops every other interior sample, keeping the first and the last in their order. */
fun thinSamples(samples: List<TouchSample>): List<TouchSample> =
    samples.filterIndexed { index, _ -> index % 2 == 0 || index == samples.lastIndex }
