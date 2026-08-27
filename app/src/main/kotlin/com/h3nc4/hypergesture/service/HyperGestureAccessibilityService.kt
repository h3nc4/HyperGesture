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

// This accessibility privilege is used for navigation only: it never reads window
// content, text or keystrokes, and sends nothing off the device.

package com.h3nc4.hypergesture.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.h3nc4.hypergesture.diagnostics.FailureLog
import com.h3nc4.hypergesture.gesture.GestureAction
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.gesture.ScreenEdge
import com.h3nc4.hypergesture.gesture.TouchSample
import com.h3nc4.hypergesture.settings.GestureSettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Hosts the edge strips in-process rather than in a foreground service:
 * `TYPE_ACCESSIBILITY_OVERLAY` needs no `SYSTEM_ALERT_WINDOW` permission and no ongoing
 * notification, and the framework rebinds an enabled service itself, so no watchdog.
 */
class HyperGestureAccessibilityService : AccessibilityService(), TouchReplayer {

    /**
     * An uncaught throw in [scope] - realistically DataStore on an unreadable file -
     * kills the process and gets the service reported as malfunctioning.
     */
    private val coroutineFailures = CoroutineExceptionHandler { _, throwable ->
        FailureLog.record(this, "settings collector", throwable)
    }
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + coroutineFailures)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var settings: GestureSettingsRepository
    private lateinit var performer: GestureActionPerformer
    private lateinit var windowManager: WindowManager
    private var overlay: EdgeTouchOverlay? = null
    private var configuration: GestureConfiguration = GestureConfiguration()
    private var lastGeometry: ScreenGeometry? = null

    /**
     * Rotation reaches the display *after* the configuration change lands, so
     * [onConfigurationChanged] alone would race it.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) reinstallIfGeometryChanged()
        }
    }

    /**
     * An uncaught throw in setup makes Android mark the service "malfunctioning" and
     * refuse to re-bind it until the user toggles it by hand; degraded but bound is
     * better, and the Diagnostics section shows what failed.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            setUp()
        } catch (t: Throwable) {
            FailureLog.record(this, "onServiceConnected", t)
        }
    }

    private fun setUp() {
        settings = GestureSettingsRepository(applicationContext)
        performer = GestureActionPerformer(this)
        windowManager = getSystemService(WindowManager::class.java)
        overlay = EdgeTouchOverlay(
            context = this,
            windowManager = windowManager,
            replayer = this,
            onGestureAction = ::onGestureAction,
        )

        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(displayListener, handler)

        scope.launch {
            settings.configuration.collect { updated ->
                configuration = updated
                installOverlay()
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reinstallIfGeometryChanged()
    }

    /** No events are requested in `accessibility_service_config.xml`. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun installOverlay() {
        val geometry = ScreenGeometry.current(this, windowManager)
        lastGeometry = geometry
        overlay?.install(configuration, geometry)
    }

    /**
     * Strip windows keep their pixel sizes across a rotation, so a side strip measured
     * against portrait height runs off a landscape screen.
     */
    private fun reinstallIfGeometryChanged() {
        if (overlay == null) return
        if (ScreenGeometry.current(this, windowManager) == lastGeometry) return
        installOverlay()
    }

    private fun teardown() {
        runCatching {
            (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .unregisterDisplayListener(displayListener)
        }
        overlay?.remove()
        overlay = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        instance = null
    }

    private fun onGestureAction(action: GestureAction, edge: ScreenEdge) {
        Log.v(TAG, "Gesture from $edge -> $action")
        performer.perform(action)
    }

    /**
     * The strips have been non-touchable and alpha 0 since this touch's `ACTION_DOWN`, so
     * its own duration already counted toward the asynchronous window update and only the
     * remainder of [WINDOW_SETTLE_MS] still has to be waited out.
     */
    override fun replay(samples: List<TouchSample>, onDone: () -> Unit): Boolean {
        if (samples.isEmpty()) return false

        val first = samples.first()
        val last = samples.last()
        val movedPx = hypot(last.xPx - first.xPx, last.yPx - first.yPx)
        val isTap = movedPx < TAP_SLOP_PX
        val rawDurationMs = last.timestampMs - first.timestampMs

        val path = Path().apply {
            moveTo(first.xPx, first.yPx)
            if (isTap) {
                // dispatchGesture rejects a zero-length path. Nudge by a sub-slop pixel:
                // still a tap, not a drag.
                lineTo(first.xPx + 1f, first.yPx + 1f)
            } else {
                for (i in 1 until samples.size) lineTo(samples[i].xPx, samples[i].yPx)
            }
        }

        val durationMs = when {
            !isTap -> rawDurationMs.coerceIn(1L, MAX_REPLAY_MS)
            // A deliberate hold: mirror it so the target's long-press fires too.
            rawDurationMs >= ViewConfiguration.getLongPressTimeout() ->
                rawDurationMs.coerceAtMost(MAX_REPLAY_MS)
            // The click fires at the end of the stroke, so capping it cuts perceived
            // tap latency; the press physically happened already.
            else -> rawDurationMs.coerceIn(MIN_TAP_MS, TAP_STROKE_CAP_MS)
        }

        val gesture = runCatching {
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build()
        }.getOrElse {
            Log.w(TAG, "Could not build replay gesture", it)
            return false
        }

        val settleDelayMs = (WINDOW_SETTLE_MS - rawDurationMs).coerceAtLeast(0L)
        var finished = false
        val finish = Runnable {
            if (!finished) {
                finished = true
                onDone()
            }
        }
        // Safety net: release the strips even if the result callback never arrives.
        handler.postDelayed(finish, durationMs + settleDelayMs + FINISH_GRACE_MS)
        handler.postDelayed({
            // After this delay the service may be unbound, and dispatchGesture then throws
            // instead of returning false; uncaught that kills the process and gets the
            // service flagged "malfunctioning".
            val dispatched = runCatching {
                dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            releaseStrips()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            // The touch never reached the app underneath, so the tap is
                            // lost; the strips still have to come back either way.
                            Log.w(TAG, "REPLAY_CANCELLED after ${durationMs}ms")
                            releaseStrips()
                        }

                        private fun releaseStrips() {
                            handler.removeCallbacks(finish)
                            finish.run()
                        }
                    },
                    handler,
                )
            }.getOrElse { throwable ->
                FailureLog.record(this, "dispatchGesture", throwable)
                false
            }
            if (dispatched) {
                Log.d(TAG, "REPLAY_DISPATCHED ${samples.size} samples over ${durationMs}ms")
            } else {
                Log.w(TAG, "REPLAY_REFUSED dispatchGesture rejected the replay")
                handler.removeCallbacks(finish)
                finish.run()
            }
        }, settleDelayMs)
        return true
    }

    companion object {
        private const val TAG = "HyperGesture"

        private const val MAX_REPLAY_MS = 3_000L

        /** A 1ms stroke completes but never clicks. */
        private const val MIN_TAP_MS = 50L

        private const val TAP_STROKE_CAP_MS = 60L

        private const val TAP_SLOP_PX = 12f

        /**
         * Grace for the non-touchable + alpha-0 update to reach the input pipeline, so the
         * injected touch is not discarded as obscured.
         */
        private const val WINDOW_SETTLE_MS = 65L

        private const val FINISH_GRACE_MS = 1_000L

        @Volatile
        private var instance: HyperGestureAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
    }
}
