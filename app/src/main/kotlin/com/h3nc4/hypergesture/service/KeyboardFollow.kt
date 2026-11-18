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

// The one event for an app hiding its keyboard needs window content access, which this service
// withholds, so a shown keyboard is read again on a short delay until a read finds it gone.
internal class KeyboardFollow(
    private val read: () -> Int?,
    private val schedule: (delayMs: Long, recheck: () -> Unit) -> Unit,
    private val cancelRecheck: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val onKeyboardTop: (Int?) -> Unit,
) {

    fun check() {
        cancelRecheck()
        val topPx = try {
            read()
        } catch (t: Throwable) {
            onFailure(t)
            return
        }
        onKeyboardTop(topPx)
        if (topPx != null) schedule(RECHECK_MS, ::check)
    }

    fun stop() = cancelRecheck()

    companion object {
        const val RECHECK_MS = 500L
    }
}
