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
 * Whether the strips are held for a touch stream, and which stream that is. Every start and
 * every reinstall hands out a new token, and a release with an older token is ignored.
 */
internal class StreamHold(private val quietLimitMs: Long) {

    private var token = 0L

    private var lastActivityMs = 0L

    private var reheld = false

    var held = false
        private set

    /** When [expire] would release the stream, or null while nothing is held. */
    val deadlineMs: Long?
        get() = if (held) lastActivityMs + quietLimitMs else null

    fun start(nowMs: Long): Long {
        token++
        held = true
        reheld = false
        lastActivityMs = nowMs
        return token
    }

    fun isCurrent(streamToken: Long): Boolean = streamToken == token

    /**
     * A touch event of [streamToken]. True when it arrived after [expire] gave the strips back,
     * because the finger was only still, so the caller must hold them again.
     */
    fun activity(streamToken: Long, nowMs: Long): Boolean {
        if (!isCurrent(streamToken)) return false
        lastActivityMs = nowMs
        if (held) return false
        held = true
        reheld = true
        return true
    }

    /**
     * False once [activity] held the strips again. After so long a touch a replay skips the settle
     * delay, and strips that still take its touch would start another replay.
     */
    fun mayReplay(streamToken: Long): Boolean = isCurrent(streamToken) && !reheld

    fun release(streamToken: Long): Boolean {
        if (!isCurrent(streamToken) || !held) return false
        held = false
        return true
    }

    /** True when the stream went [quietLimitMs] without a touch event and is now released. */
    fun expire(nowMs: Long): Boolean {
        val deadline = deadlineMs ?: return false
        if (nowMs < deadline) return false
        held = false
        return true
    }

    /** The strips are gone, so whatever stream they held can never complete. */
    fun reset() {
        token++
        held = false
    }

    companion object {
        // Past the longest stretch a live stream goes without a touch event, a replay after its
        // UP, with a second to spare for a busy main thread.
        const val STUCK_STREAM_MS = HyperGestureAccessibilityService.LONGEST_REPLAY_HOLD_MS + 1_000L
    }
}
