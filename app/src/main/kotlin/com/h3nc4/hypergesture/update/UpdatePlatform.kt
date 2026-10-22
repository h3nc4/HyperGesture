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

package com.h3nc4.hypergesture.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.File

private const val TAG = "HyperGesture"

/** The session API shows the system confirmation and refuses an APK signed with another key. */
internal class SessionApkInstaller(private val context: Context) : ApkInstaller {

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun install(file: File): String? = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(file.length())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(ReleaseParser.APK_NAME, 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            // Mutable, since the installer writes its status into the extras.
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }.exceptionOrNull()?.reason()
}

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val outcome = InstallOutcome.of(status, message)
        if (outcome != null) {
            Log.i(TAG, "update install ended with status $status")
            InstallEvents.emit(outcome)
            return
        }
        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        if (confirm == null) {
            InstallEvents.emit(InstallOutcome.Failed(status, "no confirmation screen"))
            return
        }
        runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { InstallEvents.emit(InstallOutcome.Failed(status, it.message)) }
    }
}

private val Context.updateDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "update_check")

internal fun updateStore(context: Context): UpdateStore = DataStoreUpdateStore(context.updateDataStore)

internal object InstalledVersion {
    private const val OVERRIDE = "hypergesture_version_name"

    /** A debuggable build may report another version, so the emulator can test both answers. */
    fun name(context: Context): String? {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            runCatching { Settings.Global.getString(context.contentResolver, OVERRIDE) }
                .getOrNull()?.let { return it }
        }
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
}
