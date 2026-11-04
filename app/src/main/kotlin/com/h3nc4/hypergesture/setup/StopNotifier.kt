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


package com.h3nc4.hypergesture.setup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.diagnostics.DiagnosticsCollector
import com.h3nc4.hypergesture.diagnostics.FailureLog
import com.h3nc4.hypergesture.service.HyperGestureAccessibilityService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The shade's way back to Accessibility, beside the tile, for when gestures stop. */
internal object StopNotifier {

    private const val CHANNEL_ID = "gestures_stopped"
    private const val NOTIFICATION_ID = 1

    private val lock = Mutex()

    /** Reads the live state as it runs, so events handled out of order settle on the latest. */
    fun sync(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        val failures = CoroutineExceptionHandler { _, throwable ->
            FailureLog.record(app, "stop notice", throwable)
        }
        // Kept off the service, whose own scope is cancelled as it unbinds.
        CoroutineScope(SupervisorJob() + Dispatchers.IO + failures).launch {
            try {
                lock.withLock { apply(app) }
            } finally {
                onDone()
            }
        }
    }

    private suspend fun apply(context: Context) {
        val state = context.setupState
        val record = state.record.first()
        val notifications = NotificationManagerCompat.from(context)
        val action = StopNotice.decide(
            StopNoticeFacts(
                running = HyperGestureAccessibilityService.isRunning(),
                switchedOn = DiagnosticsCollector.isAccessibilityServiceSwitchedOn(context),
                serviceEverEnabled = record.serviceEverEnabled,
                alreadyPosted = record.stopNoticePosted,
                notificationsAllowed = notifications.areNotificationsEnabled(),
            ),
        )
        when (action) {
            StopNoticeAction.POST -> if (post(context, notifications)) {
                state.set(SetupFlag.STOP_NOTICE_POSTED, true)
            }
            StopNoticeAction.CLEAR -> {
                notifications.cancel(NOTIFICATION_ID)
                if (!record.serviceEverEnabled) state.set(SetupFlag.SERVICE_EVER_ENABLED, true)
                if (record.stopNoticePosted) state.set(SetupFlag.STOP_NOTICE_POSTED, false)
            }
            StopNoticeAction.KEEP -> Unit
        }
    }

    private fun post(context: Context, notifications: NotificationManagerCompat): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notice_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            GestureTileService.accessibilityIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notice = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.notice_stopped_title))
            .setContentText(context.getString(R.string.notice_stopped_body))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .build()
        notifications.notify(NOTIFICATION_ID, notice)
        return true
    }
}
