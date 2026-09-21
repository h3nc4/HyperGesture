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

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Walks the apps the user came from, which Android exposes to nobody: there is no global
 * action for it, so the order is read from usage events and the app is launched outright.
 *
 * Starting an activity from here is a background start, allowed only because the service
 * holds a visible accessibility overlay. Measured on HyperOS as
 * `BAL_ALLOW_NON_APP_VISIBLE_WINDOW`, so the edge strips are what make this legal.
 *
 * Needs the usage access the user grants in Settings, and reports [lastFailure] when it is
 * missing so the diagnostics panel can say so rather than the gesture looking broken.
 */
class AppSwitcher(private val context: Context) {

    // The order this walk started from, newest first, and where in it the last step landed.
    // Kept rather than re-read because usage events lag by seconds: right after a launch the
    // event stream still names the previous app, so re-reading would keep resetting the walk
    // to the newest entry and the gesture would bounce between two apps.
    private var walk: List<String> = emptyList()
    private var position = 0
    private var lastStepAt = 0L

    fun step(toPrevious: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        // A pause long enough means the user went somewhere by hand, so the walk restarts.
        if (walk.isEmpty() || now - lastStepAt > CHAIN_WINDOW_MS) {
            walk = recentApps()
            position = 0
        }
        if (walk.isEmpty()) {
            val message = "app switch: no usage events, so usage access is probably not granted"
            lastFailure = message
            Log.w(TAG, message)
            return false
        }

        val next = position + if (toPrevious) 1 else -1
        if (next !in walk.indices) {
            lastFailure = "app switch: nothing ${if (toPrevious) "older" else "newer"} than this"
            return false
        }

        val target = walk[next]
        val intent = context.packageManager.getLaunchIntentForPackage(target)
        if (intent == null) {
            val message = "app switch: $target has no launcher entry"
            lastFailure = message
            Log.w(TAG, message)
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching { context.startActivity(intent) }
            .onFailure { throwable ->
                lastFailure = "app switch: starting $target threw ${throwable.javaClass.name}"
                Log.w(TAG, "startActivity($target) threw", throwable)
            }
            .onSuccess {
                position = next
                lastStepAt = now
                lastFailure = null
            }
            .isSuccess
    }

    /**
     * Apps by when each was last in front, newest first, every package once. Our own package
     * is dropped, and so is anything with no launcher entry: the home screen resumes between
     * every pair of apps and advertises CATEGORY_HOME rather than CATEGORY_LAUNCHER, so
     * leaving it in makes a step land on a package that cannot be started.
     */
    private fun recentApps(): List<String> {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val now = System.currentTimeMillis()
        val events = runCatching { usage.queryEvents(now - TRAIL_WINDOW_MS, now) }.getOrNull()
            ?: return emptyList()
        val newestFirst = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        val resumed = mutableListOf<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            resumed.add(event.packageName ?: continue)
        }
        // Walked backwards so the first sighting of a package is its most recent one.
        for (packageName in resumed.asReversed()) {
            if (packageName == context.packageName) continue
            if (!seen.add(packageName)) continue
            if (context.packageManager.getLaunchIntentForPackage(packageName) == null) continue
            newestFirst.addLast(packageName)
        }
        return newestFirst.toList()
    }

    companion object {
        private const val TAG = "HyperGesture"
        private const val TRAIL_WINDOW_MS = 6L * 60L * 60L * 1000L

        /** How long a chain of swipes keeps walking one order before it restarts. */
        private const val CHAIN_WINDOW_MS = 5_000L

        @Volatile
        var lastFailure: String? = null
            private set
    }
}
