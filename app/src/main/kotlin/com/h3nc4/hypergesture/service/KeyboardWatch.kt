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
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.h3nc4.hypergesture.diagnostics.FailureLog

// An overlay is above the keyboard and doesn't receive IME insets, so this reads the display's
// own at every touch down anywhere, which ACTION_OUTSIDE reports without its coordinates.
internal class KeyboardWatch(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onKeyboardTop: (Int?) -> Unit,
) {

    private var view: View? = null

    /** Throws when the window manager refuses the probe, which the caller retries. */
    @SuppressLint("ClickableViewAccessibility")
    fun install() {
        if (view != null) return
        // Read first, so a refused probe still fits the strips to a keyboard already up.
        check()
        val watcher = object : View(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) check()
                return false
            }
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            // Alpha 0, or it would count toward the obscuring-opacity cap a replay must stay under.
            alpha = 0f
        }
        windowManager.addView(watcher, params)
        view = watcher
    }

    fun remove() {
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        view = null
    }

    private fun check() {
        runCatching {
            val metrics = windowManager.currentWindowMetrics
            val ime = WindowInsets.Type.ime()
            keyboardTop(
                metrics.windowInsets.isVisible(ime),
                metrics.windowInsets.getInsets(ime).bottom,
                metrics.bounds.height(),
            )
        }.onSuccess(onKeyboardTop)
            .onFailure { FailureLog.record(context, "KeyboardWatch.check", it) }
    }
}
