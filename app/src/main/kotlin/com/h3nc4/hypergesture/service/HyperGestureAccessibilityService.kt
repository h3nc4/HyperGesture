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

// The service uses this privilege for navigation alone, without reading window content or
// keystrokes or sending anything. The app goes online only for its opt-in update check.

package com.h3nc4.hypergesture.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.h3nc4.hypergesture.diagnostics.FailureLog
import com.h3nc4.hypergesture.diagnostics.StopLog
import com.h3nc4.hypergesture.gesture.GestureAction
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import com.h3nc4.hypergesture.gesture.PlatformDefaults
import com.h3nc4.hypergesture.gesture.ReplayPlan
import com.h3nc4.hypergesture.gesture.ScreenEdge
import com.h3nc4.hypergesture.gesture.TouchSample
import com.h3nc4.hypergesture.navigation.ButtonRestore
import com.h3nc4.hypergesture.navigation.NavigationIntegrationFactory
import com.h3nc4.hypergesture.navigation.NavigationKeeperSettings
import com.h3nc4.hypergesture.navigation.NavigationKeeperWatch
import com.h3nc4.hypergesture.settings.GestureSettingsRepository
import com.h3nc4.hypergesture.setup.GestureTileService
import com.h3nc4.hypergesture.setup.StopNotifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Hosts the edge strips in-process rather than in a foreground service:
 * `TYPE_ACCESSIBILITY_OVERLAY` needs no `SYSTEM_ALERT_WINDOW` permission and no ongoing
 * notification, and the framework rebinds an enabled service itself, so no watchdog.
 */
class HyperGestureAccessibilityService : AccessibilityService(), TouchReplayer {

    /**
     * An uncaught throw in [connectionScope] - realistically DataStore on an unreadable file -
     * kills the process and gets the service reported as malfunctioning.
     */
    private val coroutineFailures = CoroutineExceptionHandler { _, throwable ->
        FailureLog.record(this, "settings collector", throwable)
    }
    private val connectionScope =
        ConnectionScope { SupervisorJob() + Dispatchers.Main.immediate + coroutineFailures }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var settings: GestureSettingsRepository
    private lateinit var performer: GestureActionPerformer
    private lateinit var windowManager: WindowManager
    private var overlay: EdgeTouchOverlay? = null
    private var keeperWatch: NavigationKeeperWatch? = null
    private var configuration: GestureConfiguration = GestureConfiguration()
    private var lastGeometry: ScreenGeometry? = null
    private val screenWatch = ScreenWatch(
        onScreenOn = ::repairStrips,
        onScreenOff = ::detachStripForTest,
    )

    private val installer = StripInstaller(
        install = ::installOverlay,
        schedule = { delayMs, retry ->
            if (instance === this) handler.postDelayed(Runnable(retry), INSTALL_RETRY_TOKEN, delayMs)
        },
        cancelRetry = { handler.removeCallbacksAndMessages(INSTALL_RETRY_TOKEN) },
        onFailure = { FailureLog.record(this, "installOverlay", it) },
        onAttached = ::onInstalled,
    )
    private val connectionLoss = ConnectionLoss()
    private val settleCheck = InsetSettleCheck(
        schedule = { delayMs, check ->
            if (instance === this) handler.postDelayed(Runnable(check), SETTLE_CHECK_TOKEN, delayMs)
        },
        cancel = { handler.removeCallbacksAndMessages(SETTLE_CHECK_TOKEN) },
        check = ::reinstallIfGeometryChanged,
    )

    /**
     * Rotation reaches the display *after* the configuration change lands, so
     * [onConfigurationChanged] alone would race it.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            readDisplayState()
            onGeometrySignal("display")
        }
    }

    private val keyguardGate = KeyguardGate(
        keyguardLocked = ::keyguardLocked,
        schedule = { delayMs, check ->
            if (instance === this) handler.postDelayed(Runnable(check), KEYGUARD_CHECK_TOKEN, delayMs)
        },
        cancel = { handler.removeCallbacksAndMessages(KEYGUARD_CHECK_TOKEN) },
        onChange = { armed -> overlay?.setArmed(armed) },
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> keyguardGate.screenOff()
                Intent.ACTION_SCREEN_ON -> keyguardGate.screenOn()
                Intent.ACTION_USER_PRESENT -> keyguardGate.userPresent()
            }
        }
    }

    private val wakeListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            // Wake only. A display change mid-unlock can read as not on for a moment.
            val state = getSystemService(DisplayManager::class.java)?.getDisplay(displayId)?.state
            if (state == Display.STATE_ON) keyguardGate.screenOn()
        }
    }

    /**
     * An uncaught throw in setup makes Android mark the service "malfunctioning" and
     * refuse to re-bind it until the user toggles it by hand; degraded but bound is
     * better, and the Diagnostics section shows what failed.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SERVICE_CONNECTED ${System.identityHashCode(this)}")
        instance = this
        stripsState.value = null
        connectionLoss.reset()
        connectionLostState.value = false
        runningState.value = true
        StopLog.serviceStarted(this)
        try {
            setUp()
        } catch (t: Throwable) {
            FailureLog.record(this, "onServiceConnected", t)
        }
        GestureTileService.requestRefresh(this)
        StopNotifier.sync(this)
    }

    private fun setUp() {
        val scope = connectionScope.open()
        settings = GestureSettingsRepository(applicationContext)
        performer = GestureActionPerformer(this, scope)
        windowManager = getSystemService(WindowManager::class.java)
        readDisplayState()
        overlay = EdgeTouchOverlay(
            context = this,
            windowManager = windowManager,
            replayer = this,
            onGestureAction = ::onGestureAction,
            onAppSwitchArmed = ::onAppSwitchArmed,
            onStripRefused = connectionLoss::refused,
        )

        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(displayListener, handler)
        // Protected system broadcasts only, so no export flag is needed.
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            null,
            handler,
        )
        getSystemService(DisplayManager::class.java).registerDisplayListener(wakeListener, handler)
        keyguardGate.connected(screenOn = getSystemService(PowerManager::class.java)?.isInteractive != false)

        // A failed read would end the collector, and with it every later install.
        scope.launch {
            settings.configuration
                .catch {
                    FailureLog.record(this@HyperGestureAccessibilityService, "settings collector", it)
                    emit(GestureConfiguration())
                }
                // A write that leaves the configuration equal, such as a key set to its default, rebuilds no window.
                .distinctUntilChanged()
                .collect { updated ->
                    configuration = updated
                    installer.run()
                }
        }
        scope.launch {
            NavigationKeeperSettings(applicationContext).enabled.collect { watchNavigation(it) }
        }
    }

    // Reads the integration again on each change. With the switch off the observer only feeds the stop log.
    private suspend fun watchNavigation(enabled: Boolean) {
        keeperWatch?.stop()
        keeperWatch = null
        val keeper = NavigationIntegrationFactory.create(this).keeper?.takeIf { enabled }
        keeper?.let { ButtonRestore(it, NavigationKeeperSettings(applicationContext)).hiding() }
        keeperWatch = NavigationKeeperWatch(this, handler, keeper).also { it.start() }
    }

    // Blocks the unbind for one DataStore write, because the system can kill the process once it returns.
    private fun restoreButtons() {
        runCatching {
            runBlocking { ButtonRestore.forDevice(applicationContext)?.gesturesStopped(serviceRunning = false) }
        }.onSuccess { outcome ->
            outcome?.let {
                Log.i(TAG, ButtonRestore.logLine(it))
                StopLog.recordNavigation(this, StopLog.WRITTEN_BY_APP)
            }
        }.onFailure {
            FailureLog.record(this, "restoreButtons", it)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "SERVICE_UNBIND ${System.identityHashCode(this)}")
        StopLog.serviceUnbound(this)
        teardown()
        GestureTileService.requestRefresh(this)
        StopNotifier.sync(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "SERVICE_DESTROY ${System.identityHashCode(this)}")
        teardown()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onGeometrySignal("configuration")
    }

    /** No events are requested in `accessibility_service_config.xml`. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun keyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun onInstalled(attached: Int) {
        if (instance !== this) return
        val lost = connectionLoss.installed(attached)
        val changed = lost != connectionLostState.value
        if (changed && lost) {
            Log.w(TAG, "SERVICE_CONNECTION_LOST ${System.identityHashCode(this)} strips refused for a null token")
            StopLog.connectionLost(this)
        }
        // Set first, so a screen refreshing on the strip count already reads the loss.
        connectionLostState.value = lost
        stripsState.value = attached
        if (changed) GestureTileService.requestRefresh(this)
    }

    private fun installOverlay(): Int {
        val overlay = overlay ?: return 0
        val geometry = ScreenGeometry.current(this, windowManager)
        lastGeometry = geometry
        return overlay.install(configuration.copy(platform = platformDefaults()), geometry)
            .also { settleCheck.start() }
    }

    // Read on every install, so a changed touch and hold delay lands with the next settings
    // change or rotation. The AOSP values stand in if the platform refuses.
    private fun platformDefaults(): PlatformDefaults {
        val fallback = PlatformDefaults()
        val touchSlopDp = runCatching {
            ViewConfiguration.get(this).scaledTouchSlop / resources.displayMetrics.density
        }.getOrDefault(fallback.touchSlopDp)
        return fallback.copy(
            longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
            touchSlopDp = touchSlopDp,
        )
    }

    // Logs what each signal reads, since the two arrive in either order and seconds apart.
    private fun onGeometrySignal(source: String) {
        val read = runCatching { ScreenGeometry.current(this, windowManager) }
        Log.i(TAG, "GEOMETRY_SIGNAL $source read=${read.getOrNull()} installed=$lastGeometry")
        reinstallIfGeometryChanged()
        // A signal that changed nothing yet can still precede the inset it announces.
        if (overlay != null) settleCheck.start()
    }

    /**
     * Strip windows keep their pixel sizes across a rotation, so a side strip measured
     * against portrait height runs off a landscape screen.
     */
    private fun reinstallIfGeometryChanged() {
        if (overlay == null) return
        reinstallOnGeometryChange(
            last = lastGeometry,
            read = { ScreenGeometry.current(this, windowManager) },
            reinstall = installer::run,
            onFailure = { FailureLog.record(this, "reinstallIfGeometryChanged", it) },
        )
    }

    private fun readDisplayState() {
        val display = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return
        screenWatch.onDisplayState(display.state)
    }

    private fun repairStrips() {
        val overlay = overlay ?: return
        repairDeadStrips(
            strips = overlay.stripWindows(),
            reattach = { dead ->
                val found = dead.joinToString { "${it.edge} ${it.widthPx}x${it.heightPx} attached=${it.attached}" }
                Log.i(TAG, "SCREEN_ON_REPAIR $found")
                overlay.reattach(dead.map(StripWindow::edge))
            },
            reinstall = installer::run,
        )
    }

    private fun detachStripForTest() {
        val edge = stripToDetachForTest(this) ?: return
        overlay?.detachWindow(edge)
        Log.i(TAG, "STRIP_DETACHED_FOR_TEST $edge")
    }

    private fun teardown() {
        runCatching {
            (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .unregisterDisplayListener(displayListener)
        }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(wakeListener) }
        overlay?.remove()
        overlay = null
        keeperWatch?.stop()
        keeperWatch = null
        restoreButtons()
        handler.removeCallbacksAndMessages(null)
        connectionScope.close()
        // A replaced instance can unbind after its successor connected, which must keep its state.
        if (instance === this) {
            instance = null
            connectionLostState.value = false
            runningState.value = false
        }
    }

    private fun onGestureAction(action: GestureAction, edge: ScreenEdge, stream: Long) {
        Log.v(TAG, "Gesture from $edge -> $action")
        performer.perform(action, stream)
    }

    private fun onAppSwitchArmed(stream: Long) {
        if (configuration.appSwitchEnabled) performer.prepareAppSwitch(stream)
    }

    /**
     * The strips have been non-touchable and alpha 0 since this touch's `ACTION_DOWN`, so
     * its own duration already counted toward the asynchronous window update and only the
     * remainder of [WINDOW_SETTLE_MS] still has to be waited out.
     */
    override fun replay(samples: List<TouchSample>, onDone: () -> Unit): Boolean {
        val plan = ReplayPlan.of(
            samples,
            resources.displayMetrics.density,
            ViewConfiguration.getLongPressTimeout().toLong(),
        ) ?: return false
        val strokes = runCatching { replayStrokes(plan) }.getOrElse {
            Log.w(TAG, "Could not build replay gesture", it)
            return false
        }
        val durationMs = plan.durationMs
        val rawDurationMs = samples.last().timestampMs - samples.first().timestampMs

        val settleDelayMs = (WINDOW_SETTLE_MS - rawDurationMs).coerceAtLeast(0L)
        var finished = false
        val finish = Runnable {
            if (!finished) {
                finished = true
                onDone()
            }
        }
        // Safety net: release the strips even if the result callback never arrives.
        handler.postDelayed({
            if (!finished) Log.w(TAG, "REPLAY_TIMED_OUT after ${durationMs + settleDelayMs + FINISH_GRACE_MS}ms")
            finish.run()
        }, finish, durationMs + settleDelayMs + FINISH_GRACE_MS)

        fun releaseStrips() {
            handler.removeCallbacksAndMessages(finish)
            finish.run()
        }

        fun dispatchSegment(index: Int): Boolean = runCatching {
            dispatchGesture(
                GestureDescription.Builder().addStroke(strokes[index]).build(),
                segmentCallback(index, strokes.size, { finished }, ::releaseStrips),
                handler,
            )
        }.getOrElse { throwable ->
            // Once unbound, dispatchGesture throws instead of returning false, and uncaught that
            // kills the process and gets the service flagged "malfunctioning".
            FailureLog.record(this, "dispatchGesture", throwable)
            false
        }

        handler.postDelayed({
            // The framework queues a continuation behind the stroke it continues. Sent from each callback
            // instead, every hop crossed two main threads, and one stall outran the safety net.
            val refused = strokes.indices.firstOrNull { !dispatchSegment(it) }
            if (refused == null) {
                Log.i(TAG, "REPLAY_DISPATCHED ${samples.size} samples over ${durationMs}ms, segments=${strokes.size}")
            } else {
                Log.w(TAG, "REPLAY_REFUSED segment ${refused + 1} of ${strokes.size}")
                releaseStrips()
            }
        }, settleDelayMs)
        return true
    }

    private fun segmentCallback(index: Int, count: Int, finished: () -> Boolean, release: () -> Unit) =
        object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (finished() || index < count - 1) return
                Log.i(TAG, "REPLAY_COMPLETED segments=$count")
                release()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (finished()) return
                // The touch never fully reached the app underneath. The strips still come back.
                Log.w(TAG, "REPLAY_CANCELLED at segment ${index + 1} of $count")
                release()
            }
        }

    /** One stroke per segment, each continuing the last. A tap is one stroke. */
    private fun replayStrokes(plan: ReplayPlan): List<GestureDescription.StrokeDescription> = when (plan) {
        is ReplayPlan.Tap -> {
            // dispatchGesture rejects a zero-length path. A sub-slop nudge is still a tap.
            val path = Path().apply {
                moveTo(plan.at.xPx, plan.at.yPx)
                lineTo(plan.at.xPx + 1f, plan.at.yPx + 1f)
            }
            listOf(GestureDescription.StrokeDescription(path, 0L, plan.durationMs))
        }
        is ReplayPlan.Drag -> {
            val strokes = mutableListOf<GestureDescription.StrokeDescription>()
            plan.segments.forEachIndexed { index, segment ->
                val path = segmentPath(segment.points)
                val willContinue = index < plan.segments.lastIndex
                strokes += strokes.lastOrNull()?.continueStroke(path, 0L, segment.durationMs, willContinue)
                    ?: GestureDescription.StrokeDescription(path, 0L, segment.durationMs, willContinue)
            }
            strokes
        }
    }

    // A still finger gives a zero-length path, so it steps out a pixel and back to end where it began.
    private fun segmentPath(points: List<TouchSample>): Path = Path().apply {
        val start = points.first()
        moveTo(start.xPx, start.yPx)
        val moves = points.drop(1).filter { it.xPx != start.xPx || it.yPx != start.yPx }
        if (moves.isEmpty()) {
            lineTo(start.xPx + 1f, start.yPx)
            lineTo(start.xPx, start.yPx)
        } else {
            points.drop(1).forEach { lineTo(it.xPx, it.yPx) }
        }
    }

    companion object {
        private const val TAG = "HyperGesture"

        private val KEYGUARD_CHECK_TOKEN = Any()

        /**
         * Grace for the non-touchable + alpha-0 update to reach the input pipeline, so the
         * injected touch is not discarded as obscured.
         */
        private const val WINDOW_SETTLE_MS = 65L

        private const val FINISH_GRACE_MS = 1_000L

        private val INSTALL_RETRY_TOKEN = Any()

        private val SETTLE_CHECK_TOKEN = Any()

        /** An upper bound on how long a replay holds the strips past its UP, safety net included. */
        internal const val LONGEST_REPLAY_HOLD_MS = ReplayPlan.MAX_REPLAY_MS + WINDOW_SETTLE_MS + FINISH_GRACE_MS

        @Volatile
        private var instance: HyperGestureAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        private val runningState = MutableStateFlow(false)

        /** Lets an open screen see the service start or stop without waiting for a resume. */
        val running: StateFlow<Boolean> = runningState.asStateFlow()

        private val stripsState = MutableStateFlow<Int?>(null)

        /** Null until each connection first installs, and kept on a stop so a brief UiAutomation unbind keeps its grace. */
        val attachedStrips: StateFlow<Int?> = stripsState.asStateFlow()

        private val connectionLostState = MutableStateFlow(false)

        /** True while the live instance holds a window token the system reset, which only a toggle restores. */
        val connectionLost: StateFlow<Boolean> = connectionLostState.asStateFlow()
    }
}
