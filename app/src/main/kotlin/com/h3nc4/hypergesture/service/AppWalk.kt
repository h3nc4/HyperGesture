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

// One entry per launchable package, newest first. Home resumes between apps but has no
// launcher entry, so [resolve] returning null for it keeps steps off a package that cannot start.
internal fun <T : Any> newestFirst(
    resumed: List<String>,
    ownPackage: String,
    resolve: (String) -> T?,
): List<T> {
    val seen = mutableSetOf<String>()
    // Walked backwards so the first sighting of a package is its most recent one.
    return resumed.asReversed().mapNotNull { packageName ->
        if (packageName == ownPackage || !seen.add(packageName)) null else resolve(packageName)
    }
}

// One order serves a whole chain. Usage events lag by seconds, and a read right after a
// launch would reset the walk to the newest app and bounce the gesture between two apps.
internal class AppWalk<T>(private val chainWindowMs: Long) {

    private var order: List<T> = emptyList()
    private var position = 0
    private var lastStepAtMs = 0L

    val isEmpty: Boolean get() = order.isEmpty()

    // A long enough pause means the user went somewhere by hand. The walk then starts over.
    fun restartsAt(nowMs: Long): Boolean = order.isEmpty() || nowMs - lastStepAtMs > chainWindowMs

    fun begin(newOrder: List<T>) {
        order = newOrder
        position = 0
    }

    /** The app one step away, or null past either end of the order. */
    fun target(toPrevious: Boolean): T? = order.getOrNull(position + direction(toPrevious))

    /** Records a landed step, which keeps the chain alive. */
    fun landed(toPrevious: Boolean, nowMs: Long) {
        position += direction(toPrevious)
        lastStepAtMs = nowMs
    }

    private fun direction(toPrevious: Boolean) = if (toPrevious) 1 else -1
}
