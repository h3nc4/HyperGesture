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

/**
 * Keeps the strips disarmed from screen off until the keyguard goes, since accessibility
 * overlays draw above the lock screen, where Back and Recents have nothing to act on.
 *
 * On the emulator ACTION_USER_PRESENT lands about two seconds after the keyguard starts going
 * away, so a woken lock screen is also read every [UNLOCK_CHECK_MS] until it goes. The wake is
 * taken from the display state as well, since ACTION_SCREEN_ON can arrive late too.
 */
internal class KeyguardGate(
    private val keyguardLocked: () -> Boolean,
    private val schedule: (delayMs: Long, check: () -> Unit) -> Unit,
    private val cancel: () -> Unit,
    private val onChange: (armed: Boolean) -> Unit,
) {

    var armed = true
        private set

    private var watching = false

    /** Always reports, because each connection builds a new overlay that starts armed. */
    fun connected(screenOn: Boolean) {
        stop()
        armed = !keyguardLocked()
        onChange(armed)
        if (!armed && screenOn) watch()
    }

    fun screenOff() {
        stop()
        set(false)
    }

    // A wake inside the lock delay shows the app itself, and ACTION_USER_PRESENT never follows.
    fun screenOn() {
        if (armed || watching) return
        if (keyguardLocked()) watch() else set(true)
    }

    fun userPresent() {
        stop()
        set(true)
    }

    // Never restarted while pending, so a burst of wake signals cannot push the read back.
    private fun watch() {
        watching = true
        schedule(UNLOCK_CHECK_MS, ::check)
    }

    private fun stop() {
        watching = false
        cancel()
    }

    private fun check() {
        watching = false
        if (armed) return
        if (keyguardLocked()) watch() else set(true)
    }

    private fun set(value: Boolean) {
        if (armed == value) return
        armed = value
        onChange(value)
    }

    companion object {
        const val UNLOCK_CHECK_MS = 100L
    }
}
