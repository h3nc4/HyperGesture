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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.h3nc4.hypergesture.gesture.BackBelly
import com.h3nc4.hypergesture.gesture.BackBellyState
import com.h3nc4.hypergesture.gesture.ScreenEdge
import com.h3nc4.hypergesture.gesture.TrackerAction
import kotlin.math.ceil

// HyperOS's back-gesture belly beside one side strip. Shown only once a back swipe arms, since
// an armed swipe fires or reverts and neither replays a touch underneath this window.
internal class BackBellyOverlay(
    context: Context,
    private val windowManager: WindowManager,
    private val edge: ScreenEdge,
    private val geometry: ScreenGeometry,
) {

    private val view = BellyView(context, edge, geometry.density)

    private val state = BackBellyState()

    private var attached = false

    private var release: ValueAnimator? = null

    fun install() {
        val params = WindowManager.LayoutParams(
            ceil(BackBelly.WIDTH_DP * geometry.density).toInt(),
            geometry.heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or if (edge == ScreenEdge.LEFT) Gravity.START else Gravity.END
            // Full-display coordinates, so a touch's rawY is the belly's y as it stands.
            fitInsetsTypes = 0
            alpha = 0f
        }
        runCatching {
            windowManager.addView(view, params)
            attached = true
        }.onFailure { Log.e(TAG, "Could not add $edge belly", it) }
    }

    fun remove() {
        hide()
        if (attached) runCatching { windowManager.removeView(view) }
        attached = false
    }

    /** Moves the belly along with one tracker verdict for the touch at [dragPx] inward. */
    fun follow(action: TrackerAction, fingerY: Float, dragPx: Float) {
        val wasVisible = state.visible
        val hadArrow = state.arrowShown
        state.follow(action, dragPx / geometry.density)
        when {
            action is TrackerAction.Unused -> hide()
            wasVisible && !state.visible -> animateCollapse()
            !wasVisible && state.visible -> appear(fingerY)
            hadArrow != state.arrowShown -> if (state.arrowShown) view.fadeArrowIn() else view.fadeArrowOut()
        }
        if (state.visible) view.scale = state.scale
    }

    private fun appear(fingerY: Float) {
        release?.cancel()
        view.centreY = fingerY - BackBelly.ABOVE_FINGER_DP * geometry.density
        setWindowVisible(true)
        view.fadeArrowIn()
    }

    /** Xiaomi collapses the belly the same way whether the swipe fired or was cancelled. */
    fun collapse() {
        if (!state.visible) return
        state.hide()
        animateCollapse()
    }

    private fun animateCollapse() {
        release?.cancel()
        view.fadeArrowOut()
        release = ValueAnimator.ofFloat(view.scale, 0f).apply {
            duration = COLLAPSE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { view.scale = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) setWindowVisible(false)
                }
            })
            start()
        }
    }

    /** Immediate, for a new touch: a replay must never land under a visible window. */
    fun hide() {
        state.hide()
        release?.cancel()
        release = null
        view.reset()
        setWindowVisible(false)
    }

    private fun setWindowVisible(visible: Boolean) {
        if (!attached) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val alpha = if (visible) 1f else 0f
        if (params.alpha == alpha) return
        params.alpha = alpha
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    @SuppressLint("ViewConstructor")
    private class BellyView(
        context: Context,
        private val edge: ScreenEdge,
        private val density: Float,
    ) : View(context) {

        var centreY = 0f

        var scale = 0f
            set(value) {
                field = value
                invalidate()
            }

        private var arrowAlpha = 0f
            set(value) {
                field = value
                invalidate()
            }

        private var arrowAnimator: ValueAnimator? = null

        private val bellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(BELLY_ALPHA, 0, 0, 0)
        }

        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = BackBelly.ARROW_STROKE_DP * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val bellyPath = Path()

        private val arrowPath = Path()

        fun fadeArrowIn() = animateArrow(1f, ARROW_IN_MS, DecelerateInterpolator(ARROW_EASING))

        fun fadeArrowOut() = animateArrow(0f, ARROW_OUT_MS, DecelerateInterpolator(ARROW_EASING))

        fun reset() {
            arrowAnimator?.cancel()
            arrowAlpha = 0f
            scale = 0f
        }

        private fun animateArrow(target: Float, durationMs: Long, easing: DecelerateInterpolator) {
            arrowAnimator?.cancel()
            arrowAnimator = ValueAnimator.ofFloat(arrowAlpha, target).apply {
                duration = durationMs
                interpolator = easing
                addUpdateListener { arrowAlpha = it.animatedValue as Float }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (scale <= 0f) return
            val bellyWidth = BackBelly.WIDTH_DP * density * scale
            drawBelly(canvas, bellyWidth)
            if (scale > BackBelly.ARROW_MINIMUM_SCALE && arrowAlpha > 0f) {
                drawArrow(canvas, bellyWidth / 2)
            }
        }

        private fun drawBelly(canvas: Canvas, bellyWidth: Float) {
            val height = BackBelly.HEIGHT_DP * density
            val top = centreY - height / 2
            bellyPath.rewind()
            bellyPath.moveTo(fromEdge(0f), top)
            for (step in 0..BELLY_STEPS) {
                val t = step.toFloat() / BELLY_STEPS
                bellyPath.lineTo(fromEdge(BackBelly.profile(t) * bellyWidth), top + t * height)
            }
            bellyPath.close()
            canvas.drawPath(bellyPath, bellyPaint)
        }

        // Xiaomi draws the same "<" on both edges and never rotates it.
        private fun drawArrow(canvas: Canvas, inset: Float) {
            val halfWidth = (BackBelly.ARROW_WIDTH_DP * density - arrowPaint.strokeWidth) / 2
            val halfHeight = (BackBelly.ARROW_HEIGHT_DP * density - arrowPaint.strokeWidth) / 2
            val centreX = fromEdge(inset)
            arrowPath.rewind()
            arrowPath.moveTo(centreX + halfWidth, centreY - halfHeight)
            arrowPath.lineTo(centreX - halfWidth, centreY)
            arrowPath.lineTo(centreX + halfWidth, centreY + halfHeight)
            arrowPaint.alpha = (ARROW_MAX_ALPHA * arrowAlpha).toInt()
            canvas.drawPath(arrowPath, arrowPaint)
        }

        private fun fromEdge(inset: Float): Float =
            if (edge == ScreenEdge.LEFT) inset else width - inset

        private companion object {
            const val BELLY_ALPHA = 204
            const val ARROW_MAX_ALPHA = 230
            const val BELLY_STEPS = 64
            const val ARROW_IN_MS = 100L
            const val ARROW_OUT_MS = 50L
            const val ARROW_EASING = 1.5f
        }
    }

    private companion object {
        const val TAG = "HyperGesture"
        const val COLLAPSE_MS = 100L
    }
}
