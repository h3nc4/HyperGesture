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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.service.voice.VoiceInteractionSession
import android.util.Log

// Shows the assistant session with omni.entry_point, a call that doesn't need a permission,
// as CTSLauncher (chenxiaolong) and MiCTS (parallelcc) do, both GPL-3.0.
internal object CircleToSearchLauncher {

    private const val TAG = "HyperGesture"

    private const val VOICE_INTERACTION_SERVICE = "voiceinteraction"
    private const val DESCRIPTOR = "com.android.internal.app.IVoiceInteractionManagerService"

    // The third method of IVoiceInteractionManagerService.aidl. Reading the stub's
    // TRANSACTION_ field instead would be blocked hidden-API reflection.
    private const val SHOW_SESSION_FROM_SESSION = IBinder.FIRST_CALL_TRANSACTION + 2

    // ActivityManager.INVOCATION_TIME_MS_KEY, as SystemUI's AssistManager sets it.
    private const val INVOCATION_TIME_MS_KEY = "invocation_time_ms"

    // The key the Google app reads to open contextual search instead of the assistant.
    private const val ENTRY_POINT_KEY = "omni.entry_point"

    // OMNIENT_ENTRYPOINT_LONG_PRESS_NAV_HANDLE, the very gesture this replaces.
    private const val ENTRY_POINT_LONG_PRESS_NAV_HANDLE = 1

    /** Tries [vendorTag] before the app's own tag. Returns null on success, otherwise why not. */
    fun launch(context: Context, vendorTag: String?): String? {
        val service = runCatching { voiceInteractionService() }.getOrElse {
            Log.w(TAG, "Circle to Search: voice interaction service unavailable", it)
            return "service unavailable (${it.javaClass.simpleName})"
        }

        val tags = buildList {
            vendorTag?.let(::add)
            add(context.attributionTag)
        }.distinct()

        var lastProblem = "refused by the assistant"
        for (tag in tags) {
            val shown = runCatching { showSessionFromSession(service, sessionArgs(), tag) }
                .getOrElse {
                    Log.w(TAG, "Circle to Search: showSessionFromSession threw (tag=$tag)", it)
                    lastProblem = "${it.javaClass.simpleName}: ${it.message}"
                    false
                }
            if (shown) return null
        }
        return "$lastProblem. Is Google the default digital assistant?"
    }

    private fun sessionArgs() = Bundle().apply {
        putLong(INVOCATION_TIME_MS_KEY, SystemClock.elapsedRealtime())
        putInt(ENTRY_POINT_KEY, ENTRY_POINT_LONG_PRESS_NAV_HANDLE)
    }

    // ServiceManager.getService is on the platform's "unsupported" list, allowed for apps
    // though not public. Everything after it is a plain Binder transaction.
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun voiceInteractionService(): IBinder {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
        return getService.invoke(null, VOICE_INTERACTION_SERVICE) as? IBinder
            ?: error("no '$VOICE_INTERACTION_SERVICE' service")
    }

    // Written by hand so no hidden interface is resolved. The AIDL layout is token, typed
    // Bundle, flags, then the attribution tag since Android 14.
    private fun showSessionFromSession(service: IBinder, args: Bundle, tag: String?): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeStrongBinder(null)
            data.writeInt(1)
            args.writeToParcel(data, 0)
            // The flags SystemUI passes when the handle is long-pressed.
            data.writeInt(
                VoiceInteractionSession.SHOW_WITH_ASSIST or
                    VoiceInteractionSession.SHOW_WITH_SCREENSHOT or
                    VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) data.writeString(tag)
            service.transact(SHOW_SESSION_FROM_SESSION, data, reply, 0)
            reply.readException()
            return reply.readInt() != 0
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
