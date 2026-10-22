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

package com.h3nc4.hypergesture.setup

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.diagnostics.DiagnosticsCollector
import com.h3nc4.hypergesture.service.HyperGestureAccessibilityService
import com.h3nc4.hypergesture.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

// The way back when HyperOS stops the service with the bar hidden, as Control Center still opens.
// Only the user may turn an accessibility service on. A tap on a stopped tile opens Settings.
class GestureTileService : TileService() {

    override fun onTileAdded() {
        recordAdded(true)
        refresh()
    }

    override fun onTileRemoved() = recordAdded(false)

    override fun onStartListening() {
        listening = WeakReference(this)
        // Only a tile in the panel listens, which also covers one added before this was recorded.
        recordAdded(true)
        refresh()
    }

    private fun recordAdded(added: Boolean) {
        writes.launch { applicationContext.setupState.setTileAdded(added) }
    }

    override fun onStopListening() {
        if (listening?.get() === this) listening = null
    }

    override fun onClick() {
        val target = when (currentFace().target) {
            TileTarget.APP -> Intent(this, MainActivity::class.java)
            TileTarget.ACCESSIBILITY_SETTINGS -> accessibilityIntent(this)
        }
        if (isLocked) unlockAndRun { open(target) } else open(target)
    }

    private fun currentFace() =
        TileFace.of(DiagnosticsCollector.isAccessibilityServiceEnabled(this))

    private fun refresh() {
        val tile = qsTile ?: return
        val face = currentFace()
        tile.state = if (face.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(face.subtitle)
        tile.updateTile()
    }

    // The Intent overload throws on Android 14+, and the PendingIntent one is missing below it.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {

        fun component(context: Context) = ComponentName(context, GestureTileService::class.java)

        // Kept off the service, since the system may destroy it right after onTileRemoved.
        private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // The system ignores requestListeningState for a tile that is already listening.
        @Volatile
        private var listening: WeakReference<GestureTileService>? = null

        /** Redraws the tile as the gesture service starts or stops. */
        fun requestRefresh(context: Context) {
            val open = listening?.get()
            if (open != null) {
                open.refresh()
            } else {
                runCatching { requestListeningState(context, component(context)) }
            }
        }

        /** HyperGesture's own Accessibility page where the platform has one, else the full list. */
        fun accessibilityIntent(context: Context): Intent {
            val details = Intent(ACCESSIBILITY_DETAILS_SETTINGS).putExtra(
                Intent.EXTRA_COMPONENT_NAME,
                ComponentName(context, HyperGestureAccessibilityService::class.java)
                    .flattenToString(),
            )
            val target = context.packageManager.resolveActivity(details, 0)?.activityInfo
            val reachable = detailsReachable(target != null, target?.permission) {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
            return if (reachable) details else Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

        // Settings names it only from API 31, and minSdk 30 falls back to the full list.
        private const val ACCESSIBILITY_DETAILS_SETTINGS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    }
}
