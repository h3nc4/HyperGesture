// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.h3nc4.hypergesture.gesture.ConfirmVerdict
import com.h3nc4.hypergesture.gesture.GestureAction
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.gesture.GestureTracker
import com.h3nc4.hypergesture.gesture.HapticGate
import com.h3nc4.hypergesture.gesture.LandscapeConfirm
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

    private val bellies = mutableMapOf<ScreenEdge, BackBellyOverlay>()

    private var streamHeld = false

    // Owned here rather than by a strip, so a pending pill is kept across a reinstall.
    private val confirmation = LandscapeConfirm()

    private var pill: ConfirmPillOverlay? = null

    private var pillVisible = false

    private val handler = Handler(Looper.getMainLooper())

    private val pillExpiry = Runnable(::onPillTimeout)

    fun install(configuration: GestureConfiguration, geometry: ScreenGeometry) {
        remove()
        confirmation.configure(configuration.landscapeConfirmEnabled, geometry.landscape)
        if (configuration.landscapeConfirmEnabled && geometry.landscape) {
            pill = ConfirmPillOverlay(context, windowManager, geometry).also { it.install() }
        }
        syncPill()

        val edges = buildList {
            if (configuration.leftEdgeBackEnabled) add(ScreenEdge.LEFT)
            if (configuration.rightEdgeBackEnabled) add(ScreenEdge.RIGHT)
            add(ScreenEdge.BOTTOM)
        }

        for (edge in edges) {
            if (edge != ScreenEdge.BOTTOM) {
                bellies[edge] = BackBellyOverlay(context, windowManager, edge, geometry).also {
                    it.install()
                }
            }
            addStrip(edge, configuration, geometry)
        }
        applyInteractivity()
    }

    fun remove() {
        strips.values.forEach { view ->
            runCatching { windowManager.removeView(view) }
        }
        strips.clear()
        bellies.values.forEach(BackBellyOverlay::remove)
        bellies.clear()
        handler.removeCallbacks(pillExpiry)
        pill?.remove()
        pill = null
        pillVisible = false
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
            tracker = GestureTracker(
                edge,
                configuration,
                geometry.density,
                // Display coordinates, like the rawX the tracker is fed.
                handleCenterXPx = if (edge == ScreenEdge.BOTTOM) geometry.widthPx / 2f else null,
            ),
            hapticsEnabled = configuration.hapticFeedbackEnabled,
            belly = bellies[edge],
            onStreamStart = { onStreamStart(edge) },
            admit = { action -> admit(action, edge) },
            onFire = { action -> onFire(action, edge) },
            onUnused = { samples -> onUnused(edge, samples) },
            onStreamEnd = { endStream(edge) },
        )
        val params = paramsFor(edge, configuration, geometry)
        runCatching {
            windowManager.addView(view, params)
            strips[edge] = view
            Log.i(TAG, "strip $edge installed ${params.width}x${params.height} geometry=$geometry")
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

    private fun onStreamStart(edge: ScreenEdge) {
        bellies.values.forEach(BackBellyOverlay::hide)
        confirmation.onDown(edge, SystemClock.uptimeMillis())
        syncPill()
        streamHeld = true
        applyInteractivity()
    }

    /** False when the gesture is held back behind the pill instead. */
    private fun admit(action: GestureAction, edge: ScreenEdge): Boolean {
        val verdict = confirmation.onFire(action, edge, SystemClock.uptimeMillis())
        syncPill()
        if (verdict is ConfirmVerdict.ShowPill) Log.i(TAG, "GESTURE_CONFIRM_SHOWN $edge ${action.id}")
        return verdict is ConfirmVerdict.Perform
    }

    private fun onFire(action: GestureAction, edge: ScreenEdge) {
        onGestureAction(action, edge)
    }

    private fun onUnused(edge: ScreenEdge, samples: List<TouchSample>) {
        confirmation.onReplayed(edge)
        syncPill(immediate = true)
        val dispatched = replayer.replay(samples) { releaseStream() }
        if (!dispatched) releaseStream()
    }

    private fun endStream(edge: ScreenEdge) {
        confirmation.onStreamEnd(edge, SystemClock.uptimeMillis())
        syncPill()
        releaseStream()
    }

    private fun onPillTimeout() {
        confirmation.expire(SystemClock.uptimeMillis())
        if (!confirmation.pillShown) Log.i(TAG, "GESTURE_CONFIRM_EXPIRED")
        syncPill()
    }

    // Brings the pill window and its expiry timer in line with the model.
    private fun syncPill(immediate: Boolean = false) {
        val expiresAt = confirmation.expiresAtMs
        val overlay = pill
        if (expiresAt != null && overlay != null) {
            if (!pillVisible) overlay.show()
            pillVisible = true
            handler.removeCallbacks(pillExpiry)
            handler.postAtTime(pillExpiry, expiresAt)
            return
        }
        handler.removeCallbacks(pillExpiry)
        if (!pillVisible) return
        pillVisible = false
        if (immediate) overlay?.hide() else overlay?.fadeOut()
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
        private val belly: BackBellyOverlay?,
        private val onStreamStart: () -> Unit,
        private val admit: (GestureAction) -> Boolean,
        private val onFire: (GestureAction) -> Unit,
        private val onUnused: (List<TouchSample>) -> Unit,
        private val onStreamEnd: () -> Unit,
    ) : View(context) {

        private val samples = ArrayList<TouchSample>(MAX_SAMPLES)
        private var abandoned = false
        private val haptics = HapticGate()

        private val holdRunnable = Runnable { handle(tracker.onHoldElapsed(), replayable = false) }

        private val longPressRunnable =
            Runnable { handle(tracker.onLongPressElapsed(), replayable = false) }

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
                    haptics.reset()
                    samples += sample
                    Log.d(TAG, "$edge DOWN at ${sample.xPx.toInt()},${sample.yPx.toInt()}")
                    onStreamStart()
                    track(tracker.onDown(sample), sample, replayable = false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (samples.size < MAX_SAMPLES) samples += sample
                    track(tracker.onMove(sample), sample, replayable = false)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Never a navigation gesture, and replaying a partial pinch would be
                    // worse than dropping it.
                    Log.d(TAG, "$edge ABANDONED second pointer went down")
                    abandoned = true
                    collapseBelly()
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
                        track(tracker.onUp(sample), sample, replayable = true)
                    }
                    samples.clear()
                    if (!releaseDeferred) onStreamEnd()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Commonly SystemUI when the touch began on the navigation bar, which
                    // sits above us in Z order.
                    Log.d(TAG, "$edge CANCELLED stream taken over by another window")
                    collapseBelly()
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
            val admitted = action !is TrackerAction.Fire || admit(action.action)
            val buzz = haptics.shouldTick(action)
            // One buzz per gesture. A sideways swipe already ticked on arming and doesn't tick again.
            when {
                !admitted && buzz -> confirmTick()
                !admitted || !buzz -> Unit
                action is TrackerAction.Fire && action.action == GestureAction.CircleToSearch -> longPressTick()
                else -> tick()
            }
            when (action) {
                is TrackerAction.None -> Unit
                is TrackerAction.ScheduleLongPress -> {
                    removeCallbacks(longPressRunnable)
                    postDelayed(longPressRunnable, action.delayMs)
                }
                is TrackerAction.Armed -> {
                    // A swipe is under way, so this touch can no longer be a long press.
                    removeCallbacks(longPressRunnable)
                    Log.d(TAG, "$edge armed (hold=${action.scheduleHoldMs})")
                    action.scheduleHoldMs?.let { postDelayed(holdRunnable, it) }
                }
                is TrackerAction.RearmHold -> {
                    removeCallbacks(holdRunnable)
                    postDelayed(holdRunnable, action.delayMs)
                }
                is TrackerAction.Fire -> {
                    cancelHold()
                    if (admitted) {
                        // action.id, not ::class.simpleName - R8 obfuscates class names.
                        Log.i(TAG, "GESTURE_FIRED $edge ${action.action.id}")
                        onFire(action.action)
                    }
                }
                is TrackerAction.Disarmed -> Log.d(TAG, "$edge disarmed, pulled back inside the trigger")
                // Consumed rather than replayed: the app never asked for this swipe.
                is TrackerAction.Reverted -> Log.i(TAG, "GESTURE_REVERTED $edge")
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

        // The belly moves first. A replay must never land under a visible window.
        private fun track(action: TrackerAction, sample: TouchSample, replayable: Boolean): Boolean {
            belly?.follow(action, samples.firstOrNull()?.yPx ?: sample.yPx, inwardPx(sample))
            return handle(action, replayable)
        }

        // Both timers, since a long press left scheduled past the end of a touch would fire on it.
        private fun cancelHold() {
            removeCallbacks(holdRunnable)
            removeCallbacks(longPressRunnable)
        }

        private fun collapseBelly() {
            belly?.collapse()
        }

        private fun inwardPx(sample: TouchSample): Float {
            val down = samples.firstOrNull() ?: return 0f
            return if (edge == ScreenEdge.RIGHT) down.xPx - sample.xPx else sample.xPx - down.xPx
        }

        /** View haptics rather than the Vibrator API: no VIBRATE permission needed. */
        private fun tick() {
            if (!hapticsEnabled) return
            performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        }

        /** Lighter than a gesture, since nothing happened yet. */
        private fun confirmTick() {
            if (!hapticsEnabled) return
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        /** The system's long-press buzz, so the hold reads as having landed. */
        private fun longPressTick() {
            if (!hapticsEnabled) return
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        private companion object {
            const val MAX_SAMPLES = 400
        }
    }

    private companion object {
        const val TAG = "HyperGesture"
    }
}
