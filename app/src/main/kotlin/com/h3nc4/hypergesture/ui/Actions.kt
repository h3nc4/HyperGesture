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

package com.h3nc4.hypergesture.ui

import android.app.Activity
import android.app.AppOpsManager
import android.app.StatusBarManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Icon as PlatformIcon
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.setup.GestureTileService
import com.h3nc4.hypergesture.setup.TileAddOutcome

/**
 * NEW_TASK is required from a non-Activity context and wrong from an Activity one, where it
 * would give the target its own task and stop Back returning here.
 */
internal fun Context.startFirst(vararg candidates: Intent) {
    val ownTask = findActivity() == null
    for (intent in candidates) {
        if (ownTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

internal fun openAccessibility(context: Context) =
    context.startFirst(GestureTileService.accessibilityIntent(context))

/** Autostart decides whether HyperOS restores the service after a reboot. */
internal fun openAutostart(context: Context) = context.startFirst(
    Intent(MIUI_AUTOSTART_ACTION).addCategory(Intent.CATEGORY_DEFAULT),
    appDetails(context),
)

/** Power and restricted settings both live on this page, which is about this app alone. */
internal fun openAppInfo(context: Context) = context.startFirst(appDetails(context))

private fun appDetails(context: Context) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    "package:${context.packageName}".toUri(),
)

internal fun openUsageAccess(context: Context) =
    context.startFirst(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

/** AOSP's assist and voice input screen, where the assistant is chosen. */
internal fun openAssistantSettings(context: Context) = context.startFirst(
    Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    Intent(Settings.ACTION_SETTINGS),
)

private const val MIUI_AUTOSTART_ACTION = "miui.intent.action.OP_AUTO_START"

/** Android 13's one-tap prompt. Every answer, a refusal included, reaches the caller. */
internal fun requestTile(context: Context, onOutcome: (TileAddOutcome) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onOutcome(TileAddOutcome.Unsupported)
        return
    }
    val statusBar = context.getSystemService(StatusBarManager::class.java)
    if (statusBar == null) {
        onOutcome(TileAddOutcome.Failed("no StatusBarManager"))
        return
    }
    runCatching {
        statusBar.requestAddTileService(
            GestureTileService.component(context),
            context.getString(R.string.app_name),
            PlatformIcon.createWithResource(context, R.drawable.ic_tile),
            context.mainExecutor,
        ) { result -> onOutcome(TileAddOutcome.fromResult(result)) }
    }.onFailure { onOutcome(TileAddOutcome.Failed(it)) }
}

// The app op, not the permission: PACKAGE_USAGE_STATS reads as denied through
// checkSelfPermission even once the user has allowed it in Settings.
internal fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = runCatching {
        ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }.getOrNull()
    return mode == AppOpsManager.MODE_ALLOWED
}

/** A yes is trusted. HyperOS keeps its own Power setting, so a no proves nothing there. */
internal fun batteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

internal enum class Assistant { GOOGLE, OTHER, UNKNOWN }

// A hidden secure key, readable on most builds. A read that throws reports UNKNOWN.
internal fun readAssistant(context: Context): Assistant = runCatching {
    Settings.Secure.getString(context.contentResolver, ASSISTANT_SETTING)
}.fold(
    onSuccess = { if (it?.startsWith(GOOGLE_APP) == true) Assistant.GOOGLE else Assistant.OTHER },
    onFailure = { Assistant.UNKNOWN },
)

private const val ASSISTANT_SETTING = "assistant"
private const val GOOGLE_APP = "com.google.android.googlequicksearchbox/"
