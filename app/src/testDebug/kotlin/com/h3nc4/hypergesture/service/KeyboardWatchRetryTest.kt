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

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.h3nc4.hypergesture.gesture.GestureConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class KeyboardWatchRetryTest {

    private val context = RuntimeEnvironment.getApplication() as Context

    private val real = context.getSystemService(WindowManager::class.java)

    private var refusals = 1

    private val probes = mutableListOf<View>()

    // The probe is the only 1x1 window. Its first addView throws as a token not yet valid does.
    private val windowManager = Proxy.newProxyInstance(
        WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java),
    ) { _, method, args ->
        if (method.name == "addView") {
            val view = args[0] as View
            val params = args[1] as ViewGroup.LayoutParams
            if (params.width == 1 && params.height == 1) {
                if (refusals-- > 0) throw WindowManager.BadTokenException("token null is not valid")
                probes += view
            }
        }
        try {
            method.invoke(real, *(args ?: emptyArray()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    } as WindowManager

    private val overlay = EdgeTouchOverlay(
        context = context,
        windowManager = windowManager,
        replayer = { _, _ -> false },
        onGestureAction = { _, _, _ -> },
        onAppSwitchArmed = {},
    )

    private val geometry = ScreenGeometry(
        widthPx = 1080,
        heightPx = 2400,
        density = 2.625f,
        navLeftPx = 0,
        navRightPx = 0,
        navBottomPx = 126,
    )

    private val configuration = GestureConfiguration(leftEdgeBackEnabled = true, rightEdgeBackEnabled = true)

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun `a refused probe leaves the strips attached and is retried with backoff`() {
        assertEquals(3, overlay.install(configuration, geometry))
        assertEquals(0, probes.size)
        advance(StripInstaller.FIRST_RETRY_MS - 1)
        assertEquals(0, probes.size)
        advance(1)
        assertEquals(1, probes.size)
        advance(StripInstaller.LONGEST_RETRY_MS)
        assertEquals(1, probes.size)
    }

    @Test
    fun `a probe refused again waits twice as long`() {
        refusals = 2
        overlay.install(configuration, geometry)
        advance(StripInstaller.FIRST_RETRY_MS)
        assertEquals(0, probes.size)
        advance(StripInstaller.retryDelayMs(1))
        assertEquals(1, probes.size)
    }

    @Test
    fun `removing the overlay cancels a pending probe retry`() {
        overlay.install(configuration, geometry)
        overlay.remove()
        advance(StripInstaller.LONGEST_RETRY_MS)
        assertEquals(0, probes.size)
    }
}
