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

// AOSP can reset a connection with init(null) and no onUnbind, and the live service keeps a null window
// token. Installs refused for that token alone, several in a row, read as a lost connection.
internal class ConnectionLoss(private val threshold: Int = REFUSED_INSTALLS) {

    private var nullToken = false

    private var otherFailure = false

    private var streak = 0

    var lost = false
        private set

    fun refused(failure: Throwable) {
        if (isNullToken(failure)) nullToken = true else otherFailure = true
    }

    /** Closes one install and returns [lost]. */
    fun installed(attached: Int): Boolean {
        streak = if (attached == 0 && nullToken && !otherFailure) streak + 1 else 0
        nullToken = false
        otherFailure = false
        lost = streak >= threshold
        return lost
    }

    fun reset() {
        nullToken = false
        otherFailure = false
        streak = 0
        lost = false
    }

    companion object {
        const val REFUSED_INSTALLS = 3

        // ViewRootImpl prints the token into its BadTokenException, and a reset one prints as null.
        fun isNullToken(failure: Throwable): Boolean =
            failure.message?.contains("token null is not valid") == true
    }
}
