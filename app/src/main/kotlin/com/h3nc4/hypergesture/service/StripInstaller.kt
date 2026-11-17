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

// The window token and insets can still be settling when the service binds, so an install
// that throws or attaches no strip is tried again with backoff instead of waiting for a rotation.
internal class StripInstaller(
    private val install: () -> Int,
    private val schedule: (delayMs: Long, retry: () -> Unit) -> Unit,
    private val cancelRetry: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val onAttached: (Int) -> Unit,
) {

    private var failures = 0

    fun run() {
        cancelRetry()
        val attached = try {
            install()
        } catch (t: Throwable) {
            onFailure(t)
            0
        }
        onAttached(attached)
        if (attached > 0) {
            failures = 0
            return
        }
        schedule(retryDelayMs(failures++), ::run)
    }

    companion object {
        const val FIRST_RETRY_MS = 250L
        const val LONGEST_RETRY_MS = 16_000L

        fun retryDelayMs(failures: Int): Long =
            (FIRST_RETRY_MS shl failures.coerceIn(0, 6)).coerceAtMost(LONGEST_RETRY_MS)
    }
}
