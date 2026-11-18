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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.h3nc4.hypergesture.diagnostics.FailureLog
import com.h3nc4.hypergesture.diagnostics.StreamJournal
import com.h3nc4.hypergesture.diagnostics.StuckStreamLog
import com.h3nc4.hypergesture.gesture.ConfirmVerdict
import com.h3nc4.hypergesture.gesture.GestureAction
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.gesture.GestureTracker
import com.h3nc4.hypergesture.gesture.HapticGate
import com.h3nc4.hypergesture.gesture.LandscapeConfirm
import com.h3nc4.hypergesture.gesture.ScreenEdge
import com.h3nc4.hypergesture.gesture.TouchRecording
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
 * [OtherEdges] gives the other strips back half a second in, for a finger resting on one.
 */
internal class EdgeTouchOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val replayer: TouchReplayer,
    private val onGestureAction: (GestureAction, ScreenEdge, Long) -> Unit,
    private val onAppSwitchArmed: (Long) -> Unit,
    private val layout: OverlayLayout = OverlayLayout(),
) {

    private val strips = mutableMapOf<ScreenEdge, View>()

    private val bellies = mutableMapOf<ScreenEdge, BackBellyOverlay>()

    private val hold = StreamHold(StreamHold.QUIET_STREAM_MS, StreamHold.REPLAY_STREAM_MS)

    private var heldEdge: ScreenEdge? = null

    private val others = OtherEdges()

    private val othersCheck = Runnable(::onOthersCheck)

    private val journal = StreamJournal()

    // Owned here rather than by a strip, so a pending pill is kept across a reinstall.
    private val confirmation = LandscapeConfirm()

    private var pill: ConfirmPillOverlay? = null

    private var pillVisible = false

    // Kept across a reinstall, so a rotation over the lock screen brings no strip back.
    private var armed = true

    private val handler = Handler(Looper.getMainLooper())

    private val pillExpiry = Runnable(::onPillTimeout)

    private val stuckCheck = Runnable(::onStuckCheck)

    private var keyboard: KeyboardWatch? = null

    private val keyboardRetry = Any()

    private var keyboardTopPx: Int? = null

    private var fitted: Pair<GestureConfiguration, ScreenGeometry>? = null

    /** Returns how many strips the window manager accepted. */
    fun install(configuration: GestureConfiguration, geometry: ScreenGeometry): Int {
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
        fitted = configuration to geometry
        installKeyboardWatch()
        applyInteractivity()
        return strips.size
    }

    fun remove() {
        // A plain removeView waits for the loop pass after install adds the new strips.
        strips.values.forEach { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        strips.clear()
        handler.removeCallbacksAndMessages(keyboardRetry)
        keyboard?.remove()
        keyboard = null
        keyboardTopPx = null
        fitted = null
        bellies.values.forEach(BackBellyOverlay::remove)
        bellies.clear()
        handler.removeCallbacks(pillExpiry)
        handler.removeCallbacks(stuckCheck)
        closeOthers()
        pill?.remove()
        pill = null
        pillVisible = false
        // Detaching kills any in-flight stream and its UP. The new token also keeps a replay
        // still in flight from freeing the next install's strips mid-touch.
        hold.reset()
        heldEdge = null
        journal.add("overlay removed")
    }

    // After the strips and retried from the handler, so a refused probe never holds them back.
    private fun installKeyboardWatch() {
        val watch = KeyboardWatch(context, windowManager, ::onKeyboardTop)
        keyboard = watch
        StripInstaller(
            install = {
                if (keyboard === watch) watch.install()
                1
            },
            schedule = { delayMs, retry ->
                handler.postDelayed(Runnable(retry), keyboardRetry, delayMs)
            },
            cancelRetry = { handler.removeCallbacksAndMessages(keyboardRetry) },
            onFailure = { FailureLog.record(context, "KeyboardWatch.install", it) },
            onAttached = {},
        ).run()
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
            journal = journal,
            onStreamStart = { onStreamStart(edge) },
            onActivity = ::onActivity,
            onAppSwitchArmed = { stream -> if (hold.isCurrent(stream)) onAppSwitchArmed(stream) },
            admit = { stream, action -> hold.isCurrent(stream) && admit(action, edge) },
            onFire = { stream, action -> if (hold.isCurrent(stream)) onFire(action, edge, stream) },
            onUnused = { stream, samples -> onUnused(edge, stream, samples) },
            onStreamEnd = { stream -> endStream(edge, stream) },
        )
        val params = paramsFor(edge, configuration, geometry)
        runCatching {
            windowManager.addView(view, params)
            strips[edge] = view
            Log.i(TAG, "strip $edge installed ${params.width}x${params.height} geometry=$geometry")
            journal.add("strip $edge installed ${params.width}x${params.height}")
        }.onFailure { FailureLog.record(context, "addStrip $edge", it) }
    }

    private fun paramsFor(
        edge: ScreenEdge,
        configuration: GestureConfiguration,
        geometry: ScreenGeometry,
    ): WindowManager.LayoutParams {
        val thicknessPx = (configuration.edgeWidthDp * geometry.density).toInt().coerceAtLeast(1)
        val frame = stripFrame(edge, thicknessPx, geometry, layout, keyboardTopPx)
        return WindowManager.LayoutParams(
            frame.widthPx,
            frame.heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = frame.gravity
            y = frame.y
            // Without this the strip is pushed inside the inset it was just extended across.
            fitInsetsTypes = 0
            // A side strip on the camera side otherwise starts past the cutout, off the true edge.
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    // Only new touches see a resized window, so a touch already on a strip runs its course.
    private fun onKeyboardTop(topPx: Int?) {
        if (topPx == keyboardTopPx) return
        keyboardTopPx = topPx
        val (configuration, geometry) = fitted ?: return
        strips.forEach { (edge, view) ->
            val current = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            val params = paramsFor(edge, configuration, geometry).apply {
                flags = current.flags
                alpha = current.alpha
            }
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { FailureLog.record(context, "fit strip $edge to keyboard", it) }
        }
        val sizes = strips.values.joinToString(" ") { "${it.layoutParams.width}x${it.layoutParams.height}" }
        Log.i(TAG, "KEYBOARD_${if (topPx == null) "HIDDEN" else "SHOWN top=$topPx"} strips $sizes")
        journal.add("keyboard ${topPx ?: "hidden"}, strips $sizes")
    }

    private fun onStreamStart(edge: ScreenEdge): Long {
        bellies.values.forEach(BackBellyOverlay::hide)
        val now = SystemClock.uptimeMillis()
        confirmation.onDown(edge, now)
        syncPill()
        val stream = hold.start(now)
        heldEdge = edge
        others.hold(edge, now)
        applyInteractivity()
        scheduleStuckCheck()
        scheduleOthersCheck()
        return stream
    }

    private fun onActivity(stream: Long) {
        if (!hold.activity(stream, SystemClock.uptimeMillis())) return
        // The finger was only still, and its replay would be routed back into touchable strips.
        Log.w(TAG, "STREAM_HELD_AGAIN a touch event followed the stuck release")
        journal.add("stream held again, a touch event followed the stuck release")
        applyInteractivity()
        scheduleStuckCheck()
    }

    /** False when the gesture is held back behind the pill instead. */
    private fun admit(action: GestureAction, edge: ScreenEdge): Boolean {
        val verdict = confirmation.onFire(action, edge, SystemClock.uptimeMillis())
        syncPill()
        if (verdict is ConfirmVerdict.ShowPill) Log.i(TAG, "GESTURE_CONFIRM_SHOWN $edge ${action.id}")
        return verdict is ConfirmVerdict.Perform
    }

    private fun onFire(action: GestureAction, edge: ScreenEdge, stream: Long) {
        onGestureAction(action, edge, stream)
    }

    private fun onUnused(edge: ScreenEdge, stream: Long, samples: List<TouchSample>) {
        if (!hold.isCurrent(stream)) return
        if (!hold.mayReplay(stream)) {
            journal.add("replay skipped, the strips were given back during this touch")
            endStream(edge, stream)
            return
        }
        // No other edge may start a stream while the replay is in flight.
        closeOthers()
        applyInteractivity()
        confirmation.onReplayed(edge)
        syncPill(immediate = true)
        journal.add("replaying ${samples.size} samples")
        hold.replaying(stream, SystemClock.uptimeMillis())
        scheduleStuckCheck()
        val dispatched = replayer.replay(samples) { releaseStream(stream, "replay done") }
        if (!dispatched) releaseStream(stream, "replay refused")
    }

    private fun endStream(edge: ScreenEdge, stream: Long) {
        if (!hold.isCurrent(stream)) return
        confirmation.onStreamEnd(edge, SystemClock.uptimeMillis())
        syncPill()
        releaseStream(stream, "stream ended")
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

    private fun releaseStream(stream: Long, reason: String) {
        if (!hold.release(stream)) {
            journal.add("release ignored, stream no longer held ($reason)")
            return
        }
        handler.removeCallbacks(stuckCheck)
        closeOthers()
        applyInteractivity()
        Log.d(TAG, "stream released ($reason), strips interactive again")
        journal.add("stream released ($reason)")
    }

    private fun scheduleOthersCheck() {
        handler.removeCallbacks(othersCheck)
        others.dueMs?.let { handler.postAtTime(othersCheck, it) }
    }

    private fun onOthersCheck() {
        if (!others.elapse(SystemClock.uptimeMillis())) {
            scheduleOthersCheck()
            return
        }
        Log.i(TAG, "OTHER_EDGES_LIVE while $heldEdge holds a touch")
        journal.add("other edges live while $heldEdge holds a touch")
        applyInteractivity()
    }

    private fun closeOthers() {
        handler.removeCallbacks(othersCheck)
        others.close()
    }

    private fun scheduleStuckCheck() {
        handler.removeCallbacks(stuckCheck)
        hold.deadlineMs?.let { handler.postAtTime(stuckCheck, it) }
    }

    // A stream the system ends without UP or CANCEL leaves every strip inert. Seen on the
    // Redmi 14 with all three strips held and no finger down.
    private fun onStuckCheck() {
        val now = SystemClock.uptimeMillis()
        if (!hold.expire(now)) {
            scheduleStuckCheck()
            return
        }
        val quiet = if (hold.replayStarted) {
            "a replay past ${StreamHold.REPLAY_STREAM_MS}ms"
        } else {
            "${StreamHold.QUIET_STREAM_MS}ms without a touch event"
        }
        Log.w(TAG, "${StuckStreamLog.MARKER} released after $quiet")
        journal.add("stuck stream released after $quiet")
        StuckStreamLog.append(context, stuckReport())
        bellies.values.forEach(BackBellyOverlay::hide)
        heldEdge?.let { confirmation.onStreamEnd(it, now) }
        syncPill()
        applyInteractivity()
    }

    private fun stuckReport(): String = buildString {
        appendLine("${StuckStreamLog.MARKER} ${journal.now()} held by ${heldEdge ?: "none"}")
        strips.forEach { (edge, view) -> appendLine("$edge attached=${view.isAttachedToWindow}") }
        appendLine("---")
        append(journal.dump())
    }

    /** Disarmed strips let every touch through to the window below, as a held stream does. */
    fun setArmed(armed: Boolean) {
        if (this.armed == armed) return
        this.armed = armed
        Log.i(TAG, if (armed) "STRIPS_ARMED" else "STRIPS_DISARMED keyguard")
        journal.add(if (armed) "strips armed" else "strips disarmed for the keyguard")
        applyInteractivity()
    }

    /** See the class comment for why this is alpha 0 as well as non-touchable. */
    private fun applyInteractivity() {
        strips.forEach { (edge, view) ->
            val interactive = armed && others.touchable(edge, hold.held)
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
        private val journal: StreamJournal,
        private val onStreamStart: () -> Long,
        private val onActivity: (Long) -> Unit,
        private val onAppSwitchArmed: (Long) -> Unit,
        private val admit: (Long, GestureAction) -> Boolean,
        private val onFire: (Long, GestureAction) -> Unit,
        private val onUnused: (Long, List<TouchSample>) -> Unit,
        private val onStreamEnd: (Long) -> Unit,
    ) : View(context) {

        private val samples = TouchRecording(MAX_SAMPLES)
        private var abandoned = false
        private val haptics = HapticGate()

        // The overlay's token for the touch in progress, so it can tell a stale callback apart.
        private var stream = 0L

        private val holdRunnable = Runnable { handle(tracker.onHoldElapsed(), replayable = false) }

        private val longPressRunnable =
            Runnable { handle(tracker.onLongPressElapsed(), replayable = false) }

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Keep the platform's own back gesture off these pixels.
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
        }

        // A rotation mid-hold detaches the strip, and a timer left behind would fire Recents.
        override fun onDetachedFromWindow() {
            cancelHold()
            super.onDetachedFromWindow()
        }

        // Nothing here to click: a real tap is replayed to the app underneath instead.
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // rawX/rawY are display coordinates, which is what the replay path expects.
            val sample = TouchSample(event.rawX, event.rawY, event.eventTime)
            if (event.actionMasked != MotionEvent.ACTION_DOWN) onActivity(stream)
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    samples.clear()
                    abandoned = false
                    haptics.reset()
                    samples.add(sample)
                    note("$edge DOWN at ${sample.xPx.toInt()},${sample.yPx.toInt()}")
                    stream = onStreamStart()
                    track(tracker.onDown(sample), sample, replayable = false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    samples.add(sample)
                    track(tracker.onMove(sample), sample, replayable = false)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Never a navigation gesture, and replaying a partial pinch would be
                    // worse than dropping it.
                    note("$edge ABANDONED second pointer went down")
                    abandoned = true
                    collapseBelly()
                    cancelHold()
                    tracker.onCancel()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val at = "${sample.xPx.toInt()},${sample.yPx.toInt()}"
                    journal.add("$edge UP at $at after ${samples.size} samples")
                    cancelHold()
                    // Every UP must end the stream, or the strips stay inert forever after
                    // the first gesture. Only a started replay defers the release.
                    val releaseDeferred = if (abandoned) {
                        false
                    } else {
                        samples.add(sample)
                        track(tracker.onUp(sample), sample, replayable = true)
                    }
                    samples.clear()
                    if (!releaseDeferred) onStreamEnd(stream)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Commonly SystemUI when the touch began on the navigation bar, which
                    // sits above us in Z order.
                    note("$edge CANCELLED stream taken over by another window")
                    collapseBelly()
                    cancelHold()
                    tracker.onCancel()
                    samples.clear()
                    onStreamEnd(stream)
                    true
                }
                else -> {
                    journal.add("$edge ignored ${MotionEvent.actionToString(event.actionMasked)}")
                    false
                }
            }
        }

        private fun note(message: String) {
            Log.d(TAG, message)
            journal.add(message)
        }

        /**
         * [replayable] only on release: a mid-gesture rejection must not inject anything
         * while the finger is still down. Returns true when the release was deferred.
         */
        private fun handle(action: TrackerAction, replayable: Boolean): Boolean {
            val admitted = action !is TrackerAction.Fire || admit(stream, action.action)
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
                    note("$edge armed (hold=${action.scheduleHoldMs})")
                    action.scheduleHoldMs?.let { postDelayed(holdRunnable, it) }
                    if (action.appSwitch != null) onAppSwitchArmed(stream)
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
                        journal.add("GESTURE_FIRED $edge ${action.action.id}")
                        onFire(stream, action.action)
                    }
                }
                is TrackerAction.Disarmed -> Log.d(TAG, "$edge disarmed, pulled back inside the trigger")
                // Consumed rather than replayed: the app never asked for this swipe.
                is TrackerAction.Reverted -> Log.i(TAG, "GESTURE_REVERTED $edge")
                is TrackerAction.Unused -> {
                    cancelHold()
                    note("$edge unused (${action.reason})")
                    if (replayable && !samples.isEmpty()) {
                        onUnused(stream, samples.snapshot())
                        return true
                    }
                }
            }
            return false
        }

        // The belly moves first. A replay must never land under a visible window.
        private fun track(action: TrackerAction, sample: TouchSample, replayable: Boolean): Boolean {
            belly?.follow(action, samples.first?.yPx ?: sample.yPx, inwardPx(sample))
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
            val down = samples.first ?: return 0f
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
