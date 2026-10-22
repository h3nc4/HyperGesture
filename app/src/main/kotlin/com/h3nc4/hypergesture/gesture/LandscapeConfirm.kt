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

package com.h3nc4.hypergesture.gesture

/** The pill HyperOS shows at the bottom of a landscape app when a first swipe is held back. */
object ConfirmPill {
    /** How long the pill waits for the swipe that confirms it. */
    const val WINDOW_MS = 2_000L

    /** As wide as the system gesture handle. */
    const val WIDTH_DP = 108f
    const val HEIGHT_DP = 4f
    const val ABOVE_EDGE_DP = 6f
}

sealed interface ConfirmVerdict {
    data object Perform : ConfirmVerdict

    /** Held back, with the pill up until [expiresAtMs] unless a bottom touch starts first. */
    data class ShowPill(val expiresAtMs: Long) : ConfirmVerdict
}

// Whether a bottom gesture goes ahead or waits for a second swipe. Kept out of the tracker, since
// the pill spans two touches and is kept when the strips are rebuilt.
class LandscapeConfirm(private val windowMs: Long = ConfirmPill.WINDOW_MS) {

    private var required = false

    private var landscape: Boolean? = null

    private var shownAtMs: Long? = null

    // The bottom touch in progress began while the pill was up, so it is the confirmation.
    private var confirming = false

    val pillShown: Boolean get() = shownAtMs != null

    val expiresAtMs: Long? get() = shownAtMs?.plus(windowMs)

    /** A rotation drops a pill, since it asked about the orientation that is gone. */
    fun configure(enabled: Boolean, landscape: Boolean) {
        if (this.landscape != landscape) dismiss()
        this.landscape = landscape
        required = enabled && landscape
        if (!required) dismiss()
    }

    fun onDown(edge: ScreenEdge, nowMs: Long) {
        if (edge != ScreenEdge.BOTTOM) return
        expire(nowMs)
        confirming = pillShown
    }

    fun onFire(action: GestureAction, edge: ScreenEdge, nowMs: Long): ConfirmVerdict {
        if (edge != ScreenEdge.BOTTOM || action !in GUARDED) return ConfirmVerdict.Perform
        if (!required || confirming) {
            dismiss()
            return ConfirmVerdict.Perform
        }
        shownAtMs = nowMs
        return ConfirmVerdict.ShowPill(nowMs + windowMs)
    }

    /** A bottom touch handed to the app is the user going back to it, so the pill goes. */
    fun onReplayed(edge: ScreenEdge) {
        if (edge == ScreenEdge.BOTTOM) dismiss()
    }

    fun onStreamEnd(edge: ScreenEdge, nowMs: Long) {
        if (edge != ScreenEdge.BOTTOM) return
        confirming = false
        expire(nowMs)
    }

    /** A touch that started inside the window keeps the pill until it ends. */
    fun expire(nowMs: Long) {
        val expiry = expiresAtMs ?: return
        if (!confirming && nowMs >= expiry) dismiss()
    }

    fun dismiss() {
        shownAtMs = null
        confirming = false
    }

    private companion object {
        // Circle to Search is a still hold rather than a swipe, so it never leaves by accident.
        val GUARDED = setOf(
            GestureAction.Home,
            GestureAction.Recents,
            GestureAction.PreviousApp,
            GestureAction.NextApp,
        )
    }
}
