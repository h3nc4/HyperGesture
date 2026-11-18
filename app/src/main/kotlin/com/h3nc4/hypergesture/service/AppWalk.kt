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

/** A package coming to the front, from an `ACTIVITY_RESUMED` usage event. */
internal data class Resumed(val packageName: String, val atMs: Long)

/** Which packages a walk never starts, whatever their launch intent says. */
internal class WalkFilter(
    private val ownPackage: String,
    private val homePackages: Set<String>,
    private val isSystem: (String) -> Boolean,
    private val hasLauncherEntry: (String) -> Boolean,
) {
    // A third-party launcher often has a launcher entry of its own, so home is matched by name.
    fun skips(packageName: String): Boolean =
        packageName == ownPackage ||
            packageName in homePackages ||
            packageName in SYSTEM_UI_PACKAGES ||
            (isSystem(packageName) && !hasLauncherEntry(packageName))

    private companion object {
        val SYSTEM_UI_PACKAGES = setOf("android", "com.android.systemui")
    }
}

/** Apps newest first. Without the app in front, such as the launcher, a first step back opens the newest. */
internal data class WalkOrder<T>(val apps: List<T>, val frontInOrder: Boolean) {
    val hasPrevious: Boolean get() = apps.size > if (frontInOrder) 1 else 0
}

// One entry per package the user really visited, newest first. A visit lasts until another
// package resumes, so a dialog-only task or a step passed through on the way is shorter than
// [minVisitMs] and drops out, while the app still in front always counts.
internal fun <T : Any> newestFirst(
    resumed: List<Resumed>,
    minVisitMs: Long,
    filter: WalkFilter,
    resolve: (String) -> T?,
): WalkOrder<T> {
    val visits = resumed.filterIndexed { i, event -> i == 0 || resumed[i - 1].packageName != event.packageName }
    val seen = mutableSetOf<String>()
    var frontInOrder = true
    // Walked backwards so the first sighting of a package is its most recent real visit.
    val apps = visits.indices.reversed().mapNotNull { i ->
        val packageName = visits[i].packageName
        val endedAtMs = visits.getOrNull(i + 1)?.atMs
        when {
            endedAtMs != null && endedAtMs - visits[i].atMs < minVisitMs -> null
            !seen.add(packageName) || filter.skips(packageName) -> null
            else -> resolve(packageName)
        }.also { if (i == visits.lastIndex && it == null) frontInOrder = false }
    }
    return WalkOrder(apps, frontInOrder)
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

    fun begin(newOrder: WalkOrder<T>) {
        order = newOrder.apps
        position = if (newOrder.frontInOrder) 0 else -1
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
