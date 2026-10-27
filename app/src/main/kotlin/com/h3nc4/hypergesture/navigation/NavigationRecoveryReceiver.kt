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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

package com.h3nc4.hypergesture.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Listens for package and default launcher changes that trigger HyperOS SystemUI's
 * PhoneStateMonitorController watchdog. Re-applies navigation settings immediately and
 * runs a delayed second check to ensure the asynchronous watchdog did not reset them.
 */
class NavigationRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_PREFERRED_ACTIVITY_CHANGED &&
            action != Intent.ACTION_PACKAGE_ADDED &&
            action != Intent.ACTION_PACKAGE_CHANGED &&
            action != Intent.ACTION_PACKAGE_REMOVED &&
            action != Intent.ACTION_PACKAGE_REPLACED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Immediate direct check via Shizuku (<50ms)
                NavigationRecoveryScheduler.runDirectIfEnabled(context)
                // Watchdog debounce: HyperOS SystemUI PhoneStateMonitor posts an asynchronous
                // runnable when default home or packages change, which resets force_fsg_nav_bar=0.
                // Delay 1.5 seconds and trigger a direct second check to guarantee the setting sticks.
                delay(1500)
                NavigationRecoveryScheduler.runDirectIfEnabled(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION_PREFERRED_ACTIVITY_CHANGED = "android.intent.action.ACTION_PREFERRED_ACTIVITY_CHANGED"
    }
}
