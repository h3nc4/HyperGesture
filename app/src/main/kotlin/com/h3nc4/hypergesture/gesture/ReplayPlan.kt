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

import kotlin.math.hypot
import kotlin.math.roundToLong

/** One stretch of a replayed drag. Its [points] begin where the previous segment ended. */
data class ReplaySegment(val points: List<TouchSample>, val durationMs: Long)

/** How a consumed touch is injected again, kept free of Android so its timing can be tested. */
sealed interface ReplayPlan {

    val durationMs: Long

    data class Tap(val at: TouchSample, override val durationMs: Long) : ReplayPlan

    /** Android moves an injected stroke at one constant speed, and a fling flattened to it
     * reaches the app at its average speed. Chained segments keep the speed near the release. */
    data class Drag(val segments: List<ReplaySegment>) : ReplayPlan {
        override val durationMs: Long = segments.sumOf { it.durationMs }
    }

    companion object {
        /** Bounds how long a replay keeps the strips inert. */
        const val MAX_REPLAY_MS = 3_000L

        /** A 1ms stroke completes but never clicks. */
        const val MIN_TAP_MS = 50L

        const val TAP_STROKE_CAP_MS = 60L

        /** Below the platform's 8 dp touch slop, so the app underneath reads it as a tap too. */
        const val TAP_SLOP_DP = 4f

        /** The last segment spans this much of the recording, and each earlier one twice the next. */
        const val TAIL_SEGMENT_MS = 16L

        /** Null for no samples. [samples] are oldest first, as the strip recorded them. */
        fun of(samples: List<TouchSample>, density: Float, longPressTimeoutMs: Long): ReplayPlan? {
            if (samples.isEmpty()) return null
            val first = samples.first()
            val last = samples.last()
            val rawDurationMs = last.timestampMs - first.timestampMs
            val movedPx = hypot(last.xPx - first.xPx, last.yPx - first.yPx)
            if (movedPx >= TAP_SLOP_DP * density) return Drag(segments(samples))
            val durationMs = if (rawDurationMs >= longPressTimeoutMs) {
                // Mirrors a deliberate hold, so the target's long press fires too.
                rawDurationMs.coerceAtMost(MAX_REPLAY_MS)
            } else {
                // The click fires at the end of the stroke. A capped stroke cuts tap latency.
                rawDurationMs.coerceIn(MIN_TAP_MS, TAP_STROKE_CAP_MS)
            }
            return Tap(first, durationMs)
        }

        private fun segments(samples: List<TouchSample>): List<ReplaySegment> {
            val startMs = samples.first().timestampMs
            val endMs = samples.last().timestampMs
            val rawMs = endMs - startMs
            // A drag past the cap plays faster throughout, along the same path.
            val scale = if (rawMs > MAX_REPLAY_MS) MAX_REPLAY_MS.toDouble() / rawMs else 1.0
            val cuts = cutIndices(samples, endMs)
            return cuts.zipWithNext().map { (from, to) ->
                val elapsedFrom = samples[from].timestampMs - startMs
                val elapsedTo = samples[to].timestampMs - startMs
                val durationMs = (elapsedTo * scale).roundToLong() - (elapsedFrom * scale).roundToLong()
                // An injected stroke needs a positive duration.
                ReplaySegment(samples.subList(from, to + 1).toList(), durationMs.coerceAtLeast(1L))
            }
        }

        /** Sample indices where segments meet, first and last included, finest near the release. */
        private fun cutIndices(samples: List<TouchSample>, endMs: Long): List<Int> {
            val cuts = mutableListOf(samples.lastIndex)
            var spanMs = TAIL_SEGMENT_MS
            var cutMs = endMs - spanMs
            while (cuts.last() > 0) {
                val index = samples.indexOfLast { it.timestampMs <= cutMs }.coerceAtLeast(0)
                if (index < cuts.last()) cuts += index
                spanMs *= 2
                cutMs -= spanMs
            }
            return cuts.reversed()
        }
    }
}
