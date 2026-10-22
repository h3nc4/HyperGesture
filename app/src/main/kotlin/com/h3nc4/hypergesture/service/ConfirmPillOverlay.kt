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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.h3nc4.hypergesture.gesture.ConfirmPill
import kotlin.math.ceil

// The handle-like pill a held-back landscape swipe shows at the bottom centre. A window of its
// own the size of the pill, so it covers as little as it can of a touch replayed below it.
internal class ConfirmPillOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val geometry: ScreenGeometry,
) {

    private val view = PillView(context, geometry.density)

    private var attached = false

    private var fade: ValueAnimator? = null

    fun install() {
        val outlinePx = OUTLINE_DP * geometry.density
        val params = WindowManager.LayoutParams(
            ceil(ConfirmPill.WIDTH_DP * geometry.density + 2 * outlinePx).toInt(),
            ceil(ConfirmPill.HEIGHT_DP * geometry.density + 2 * outlinePx).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (ConfirmPill.ABOVE_EDGE_DP * geometry.density).toInt()
            fitInsetsTypes = 0
            alpha = 0f
        }
        runCatching {
            windowManager.addView(view, params)
            attached = true
        }.onFailure { Log.e(TAG, "Could not add confirm pill", it) }
    }

    fun remove() {
        hide()
        if (attached) runCatching { windowManager.removeView(view) }
        attached = false
    }

    fun show() {
        fade?.cancel()
        view.dark = isNightMode()
        setWindowVisible(true)
        animateTo(1f, FADE_IN_MS)
    }

    fun fadeOut() {
        if (!attached || view.fraction == 0f) return
        animateTo(0f, FADE_OUT_MS)
    }

    /** Immediate, for a touch about to be replayed: it must never land under a visible window. */
    fun hide() {
        fade?.cancel()
        fade = null
        view.fraction = 0f
        setWindowVisible(false)
    }

    private fun animateTo(target: Float, durationMs: Long) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(view.fraction, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { view.fraction = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && target == 0f) setWindowVisible(false)
                }
            })
            start()
        }
    }

    // Read on each show, since a theme switch does not rebuild the overlay.
    private fun isNightMode(): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun setWindowVisible(visible: Boolean) {
        if (!attached) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val alpha = if (visible) 1f else 0f
        if (params.alpha == alpha) return
        params.alpha = alpha
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    @SuppressLint("ViewConstructor")
    private class PillView(context: Context, density: Float) : View(context) {

        var dark = false

        var fraction = 0f
            set(value) {
                field = value
                invalidate()
            }

        private val outlinePx = OUTLINE_DP * density

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = outlinePx
        }

        private val bounds = RectF()

        // Light on a dark theme and dark on a light one, each ringed in the other for any game.
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (fraction <= 0f) return
            val ink = if (dark) Color.WHITE else Color.BLACK
            val ring = if (dark) Color.BLACK else Color.WHITE
            fill.color = Color.argb((FILL_ALPHA * fraction).toInt(), Color.red(ink), Color.green(ink), Color.blue(ink))
            outline.color = Color.argb((RING_ALPHA * fraction).toInt(), Color.red(ring), Color.green(ring), Color.blue(ring))
            bounds.set(outlinePx, outlinePx, width - outlinePx, height - outlinePx)
            val radius = bounds.height() / 2
            canvas.drawRoundRect(bounds, radius, radius, outline)
            canvas.drawRoundRect(bounds, radius, radius, fill)
        }
    }

    private companion object {
        const val TAG = "HyperGesture"
        const val OUTLINE_DP = 1f
        const val FILL_ALPHA = 230
        const val RING_ALPHA = 90
        const val FADE_IN_MS = 120L
        const val FADE_OUT_MS = 250L
    }
}
