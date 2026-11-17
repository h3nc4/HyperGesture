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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
class AppSwitcher(private val context: Context, private val scope: CoroutineScope) {

    private class LaunchTarget(val packageName: String, val intent: Intent)

    private val walk = AppWalk<LaunchTarget>(CHAIN_WINDOW_MS)

    private val prefetch = OrderPrefetch<LaunchTarget>(PREPARED_MAX_AGE_MS)

    // Steps that arrived while an order is still being read, applied in order once it arrives.
    private var waitingSteps: MutableList<Boolean>? = null

    /** Reads the order off the main thread once [stream] arms as a switch, before it fires. */
    fun prepare(stream: Long) {
        val startedAt = SystemClock.elapsedRealtime()
        if (!walk.restartsAt(startedAt)) return
        prefetch.request(stream)
        scope.launch {
            val apps = withContext(Dispatchers.IO) { readOrder() }
            prefetch.deliver(PreparedOrder(stream, startedAt, apps))
        }
    }

    /** False when the step failed at once, true when it landed or waits for its order. */
    fun step(toPrevious: Boolean, stream: Long): Boolean {
        waitingSteps?.let { waiting ->
            waiting.add(toPrevious)
            return true
        }
        val now = SystemClock.elapsedRealtime()
        if (walk.restartsAt(now)) {
            val prepared = prefetch.take(stream, now)
            if (prepared == null) {
                readThenStep(toPrevious)
                return true
            }
            walk.begin(prepared)
        }
        return advance(toPrevious, now)
    }

    // On a prefetch miss the step waits for a read off the main thread.
    private fun readThenStep(toPrevious: Boolean) {
        val waiting = mutableListOf(toPrevious)
        waitingSteps = waiting
        scope.launch {
            try {
                walk.begin(withContext(Dispatchers.IO) { readOrder() })
            } finally {
                waitingSteps = null
            }
            waiting.forEach { advance(it, SystemClock.elapsedRealtime()) }
        }
    }

    private fun advance(toPrevious: Boolean, now: Long): Boolean {
        if (walk.isEmpty) {
            val message = "app switch: no usage events, so usage access is probably not granted"
            lastFailure = message
            Log.w(TAG, message)
            return false
        }

        val target = walk.target(toPrevious)
        if (target == null) {
            lastFailure = "app switch: nothing ${if (toPrevious) "older" else "newer"} than this"
            return false
        }

        val name = target.packageName
        return runCatching { context.startActivity(target.intent) }
            .onFailure { throwable ->
                lastFailure = "app switch: starting $name threw ${throwable.javaClass.name}"
                Log.d(TAG, "startActivity($name) threw", throwable)
            }
            .onSuccess {
                walk.landed(toPrevious, now)
                lastFailure = null
            }
            .isSuccess
    }

    // A short window answers in most cases. The long one is read only when it finds nothing
    // to step back to, such as after an hour in one app.
    private fun readOrder(): List<LaunchTarget> = runCatching {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@runCatching emptyList()
        recentApps(usage, RECENT_WINDOW_MS).takeIf { it.size > 1 }
            ?: recentApps(usage, TRAIL_WINDOW_MS)
    }.getOrDefault(emptyList())

    // Apps by when each was last in front, with launch intents resolved here off the main
    // thread. A step then only starts the activity.
    private fun recentApps(usage: UsageStatsManager, windowMs: Long): List<LaunchTarget> {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - windowMs, now) ?: return emptyList()
        val resumed = mutableListOf<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            resumed.add(event.packageName ?: continue)
        }
        return newestFirst(resumed, context.packageName) { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { LaunchTarget(packageName, it) }
        }
    }

    companion object {
        private const val TAG = "HyperGesture"
        private const val RECENT_WINDOW_MS = 60L * 60L * 1000L
        private const val TRAIL_WINDOW_MS = 6L * 60L * 60L * 1000L

        /** How long a chain of swipes keeps walking one order before it restarts. */
        private const val CHAIN_WINDOW_MS = 5_000L

        /** How old an order read at touch-down may be when its gesture fires. */
        private const val PREPARED_MAX_AGE_MS = 3_000L

        @Volatile
        var lastFailure: String? = null
            private set
    }
}
