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

package com.h3nc4.hypergesture.navigation

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.util.Log

// Lives in the accessibility service, so it starts with it after boot and stops with it.
// A reset arrives as a settings change, so nothing polls.
class NavigationKeeperWatch(
    private val context: Context,
    handler: Handler,
    private val keeper: NavigationKeeper,
) : ContentObserver(handler) {

    fun start() {
        NavigationKeeper.KEPT.forEach {
            context.contentResolver.registerContentObserver(AndroidSettingsAccess.uriOf(it), false, this)
        }
        keepNow()
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(this) }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) = keepNow()

    private fun keepNow() {
        when (val outcome = keeper.keep()) {
            KeepOutcome.AlreadyKept -> Unit
            KeepOutcome.NotGranted -> Log.w(TAG, "NAVIGATION_NOT_KEPT the grant is missing")
            is KeepOutcome.Restored -> Log.i(TAG, "NAVIGATION_RESTORED ${outcome.keys.joinToString()}")
            is KeepOutcome.Failed -> Log.w(TAG, "NAVIGATION_NOT_KEPT ${outcome.reason}")
        }
    }

    private companion object {
        const val TAG = "HyperGesture"
    }
}
