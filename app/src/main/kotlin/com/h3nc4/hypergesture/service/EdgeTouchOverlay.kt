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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.h3nc4.hypergesture.gesture.GestureAction
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.gesture.GestureTracker
import com.h3nc4.hypergesture.gesture.ScreenEdge
import com.h3nc4.hypergesture.gesture.TouchSample
import com.h3nc4.hypergesture.gesture.TrackerAction

/**
 * Captures raw touches in thin strips along the screen edges and feeds them to a
 * per-edge [GestureTracker].
 *
 * A plain [android.accessibilityservice.AccessibilityService] receives no arbitrary
 * [MotionEvent]s, hence real windows; `TYPE_ACCESSIBILITY_OVERLAY` needs no
 * `SYSTEM_ALERT_WINDOW` permission. A strip must consume `ACTION_DOWN` to see whether a
 * swipe develops, so a touch that is not a gesture goes to [TouchReplayer] instead of
 * being swallowed.
 *
 * While a finger holds a strip, every strip goes `FLAG_NOT_TOUCHABLE` **and** alpha 0.
 * Applying it at `ACTION_DOWN` gives the asynchronous window update the whole touch to
 * take effect, since window changes only affect the targeting of *new* touches; alpha 0
 * because a visible non-touchable overlay still counts toward Android's
 * obscuring-opacity cap, and an injected touch under one is discarded as untrusted.
 */
internal class EdgeTouchOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val replayer: TouchReplayer,
    private val onGestureAction: (GestureAction, ScreenEdge) -> Unit,
    private val layout: OverlayLayout = OverlayLayout(),
) {

    private val strips = mutableMapOf<ScreenEdge, View>()

    private var streamHeld = false

    fun install(configuration: GestureConfiguration, geometry: ScreenGeometry) {
        remove()

        val edges = buildList {
            if (configuration.leftEdgeBackEnabled) add(ScreenEdge.LEFT)
            if (configuration.rightEdgeBackEnabled) add(ScreenEdge.RIGHT)
            add(ScreenEdge.BOTTOM)
        }

        for (edge in edges) {
            addStrip(edge, configuration, geometry)
        }
        applyInteractivity()
    }

    fun remove() {
        strips.values.forEach { view ->
            runCatching { windowManager.removeView(view) }
        }
        strips.clear()
        // Detaching kills any in-flight stream, so its UP never arrives to release the
        // hold; without this reset, freshly installed strips start out inert.
        streamHeld = false
    }

    private fun addStrip(
        edge: ScreenEdge,
        configuration: GestureConfiguration,
        geometry: ScreenGeometry,
    ) {
        val view = TouchCaptureView(
            context = context,
            edge = edge,
            tracker = GestureTracker(edge, configuration, geometry.density),
            hapticsEnabled = configuration.hapticFeedbackEnabled,
            onStreamStart = ::onStreamStart,
            onFire = { action -> onFire(action, edge) },
            onUnused = ::onUnused,
            onStreamEnd = ::releaseStream,
        )
        val params = paramsFor(edge, configuration, geometry)
        runCatching {
            windowManager.addView(view, params)
            strips[edge] = view
            Log.d(TAG, "strip $edge installed ${params.width}x${params.height} geometry=$geometry")
            // Keep the platform's own back gesture off these pixels.
            view.post {
                if (view.width > 0 && view.height > 0) {
                    view.systemGestureExclusionRects =
                        listOf(Rect(0, 0, view.width, view.height))
                }
            }
        }.onFailure { Log.e(TAG, "Could not add $edge strip", it) }
    }

    private fun paramsFor(
        edge: ScreenEdge,
        configuration: GestureConfiguration,
        geometry: ScreenGeometry,
    ): WindowManager.LayoutParams {
        val thicknessPx = (configuration.edgeWidthDp * geometry.density).toInt().coerceAtLeast(1)

        // Each strip is extended across its own edge's navigation-bar inset: the bar is
        // slippery, so a swipe starting on it lands on us only after leaving the bar.
        val (widthPx, heightPx, gravity) = when (edge) {
            ScreenEdge.LEFT -> Triple(
                thicknessPx + geometry.navLeftPx,
                (geometry.heightPx * layout.sideLengthPercent / 100).coerceAtLeast(1),
                Gravity.START or Gravity.CENTER_VERTICAL,
            )
            ScreenEdge.RIGHT -> Triple(
                thicknessPx + geometry.navRightPx,
                (geometry.heightPx * layout.sideLengthPercent / 100).coerceAtLeast(1),
                Gravity.END or Gravity.CENTER_VERTICAL,
            )
            ScreenEdge.BOTTOM -> Triple(
                (geometry.widthPx * layout.bottomLengthPercent / 100).coerceAtLeast(1),
                thicknessPx + geometry.navBottomPx,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            )
        }

        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            // Without this the strip is pushed inside the inset it was just extended across.
            fitInsetsTypes = 0
        }
    }

    private fun onStreamStart() {
        streamHeld = true
        applyInteractivity()
    }

    private fun onFire(action: GestureAction, edge: ScreenEdge) {
        onGestureAction(action, edge)
    }

    private fun onUnused(samples: List<TouchSample>) {
        val dispatched = replayer.replay(samples) { releaseStream() }
        if (!dispatched) releaseStream()
    }

    private fun releaseStream() {
        streamHeld = false
        applyInteractivity()
        Log.d(TAG, "stream released; strips interactive again")
    }

    /** See the class comment for why this is alpha 0 as well as non-touchable. */
    private fun applyInteractivity() {
        val interactive = !streamHeld
        strips.values.forEach { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            val flags = if (interactive) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            val alpha = if (interactive) 1f else 0f
            if (flags == params.flags && params.alpha == alpha) return@forEach
            params.flags = flags
            params.alpha = alpha
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    /** Reads coordinates and timestamps only — never window content, text or keystrokes. */
    @SuppressLint("ViewConstructor")
    private class TouchCaptureView(
        context: Context,
        private val edge: ScreenEdge,
        private val tracker: GestureTracker,
        private val hapticsEnabled: Boolean,
        private val onStreamStart: () -> Unit,
        private val onFire: (GestureAction) -> Unit,
        private val onUnused: (List<TouchSample>) -> Unit,
        private val onStreamEnd: () -> Unit,
    ) : View(context) {

        private val samples = ArrayList<TouchSample>(MAX_SAMPLES)
        private var abandoned = false

        private val holdRunnable = Runnable { handle(tracker.onHoldElapsed(), replayable = false) }

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Nothing here to click: a real tap is replayed to the app underneath instead.
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // rawX/rawY are display coordinates, which is what the replay path expects.
            val sample = TouchSample(event.rawX, event.rawY, event.eventTime)
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    samples.clear()
                    abandoned = false
                    samples += sample
                    Log.d(TAG, "$edge DOWN at ${sample.xPx.toInt()},${sample.yPx.toInt()}")
                    onStreamStart()
                    handle(tracker.onDown(sample), replayable = false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (samples.size < MAX_SAMPLES) samples += sample
                    handle(tracker.onMove(sample), replayable = false)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Never a navigation gesture, and replaying a partial pinch would be
                    // worse than dropping it.
                    Log.d(TAG, "$edge ABANDONED second pointer went down")
                    abandoned = true
                    cancelHold()
                    tracker.onCancel()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    cancelHold()
                    // Every UP must end the stream, or the strips stay inert forever after
                    // the first gesture. Only a started replay defers the release.
                    val releaseDeferred = if (abandoned) {
                        false
                    } else {
                        if (samples.size < MAX_SAMPLES) samples += sample
                        handle(tracker.onUp(sample), replayable = true)
                    }
                    samples.clear()
                    if (!releaseDeferred) onStreamEnd()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Commonly SystemUI when the touch began on the navigation bar, which
                    // sits above us in Z order.
                    Log.d(TAG, "$edge CANCELLED stream taken over by another window")
                    cancelHold()
                    tracker.onCancel()
                    samples.clear()
                    onStreamEnd()
                    true
                }
                else -> false
            }
        }

        /**
         * [replayable] only on release: a mid-gesture rejection must not inject anything
         * while the finger is still down. Returns true when the release was deferred.
         */
        private fun handle(action: TrackerAction, replayable: Boolean): Boolean {
            when (action) {
                is TrackerAction.None -> Unit
                is TrackerAction.Armed -> {
                    Log.d(TAG, "$edge armed (hold=${action.scheduleHoldMs})")
                    // The bottom edge ticks on fire instead of on arming, so a plain
                    // upward swipe does not buzz on the way up.
                    action.scheduleHoldMs?.let { postDelayed(holdRunnable, it) } ?: tick()
                }
                is TrackerAction.RearmHold -> {
                    removeCallbacks(holdRunnable)
                    postDelayed(holdRunnable, action.delayMs)
                }
                is TrackerAction.Fire -> {
                    cancelHold()
                    tick()
                    // action.id, not ::class.simpleName - R8 obfuscates class names.
                    Log.i(TAG, "GESTURE_FIRED $edge ${action.action.id}")
                    onFire(action.action)
                }
                is TrackerAction.Unused -> {
                    cancelHold()
                    Log.d(TAG, "$edge unused (${action.reason})")
                    if (replayable && samples.isNotEmpty()) {
                        onUnused(samples.toList())
                        return true
                    }
                }
            }
            return false
        }

        private fun cancelHold() = removeCallbacks(holdRunnable)

        /** View haptics rather than the Vibrator API: no VIBRATE permission needed. */
        private fun tick() {
            if (!hapticsEnabled) return
            performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        }

        private companion object {
            const val MAX_SAMPLES = 400
        }
    }

    private companion object {
        const val TAG = "HyperGesture"
    }
}
