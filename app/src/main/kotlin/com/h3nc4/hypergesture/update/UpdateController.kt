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

import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

internal sealed interface FetchResult {
    data class Ok(val body: String) : FetchResult
    data object NotFound : FetchResult
    data class RateLimited(val resetEpochSeconds: Long?) : FetchResult
    data class HttpError(val code: Int) : FetchResult
    data class NoNetwork(val reason: String) : FetchResult

    companion object {
        /** GitHub answers an exhausted quota with 403 or 429 and a zero remaining header. */
        fun of(code: Int, remaining: String?, reset: String?, body: () -> String): FetchResult =
            when {
                code == 200 -> Ok(body())
                code == 404 -> NotFound
                code == 429 || (code == 403 && remaining == "0") ->
                    RateLimited(reset?.toLongOrNull())
                else -> HttpError(code)
            }
    }
}

internal sealed interface DownloadResult {
    data class Ok(val file: File, val verified: Boolean) : DownloadResult
    data object ChecksumMismatch : DownloadResult
    data class BlockedHost(val host: String) : DownloadResult
    data class Failed(val reason: String) : DownloadResult
}

internal sealed interface InstallOutcome {
    data object Installed : InstallOutcome
    data object Declined : InstallOutcome
    data class Failed(val status: Int, val message: String?) : InstallOutcome {
        /** Android reports an APK signed with another key as a conflict with the installed one. */
        val signatureConflict: Boolean get() = status == PackageInstaller.STATUS_FAILURE_CONFLICT
    }

    companion object {
        /** Null for STATUS_PENDING_USER_ACTION, which is a prompt to show rather than an end. */
        fun of(status: Int, message: String?): InstallOutcome? = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> null
            PackageInstaller.STATUS_SUCCESS -> Installed
            PackageInstaller.STATUS_FAILURE_ABORTED -> Declined
            else -> Failed(status, message)
        }
    }
}

/** Process-wide, because InstallResultReceiver emits each outcome and the screen collects it. */
internal object InstallEvents {
    private val mutableOutcomes = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<InstallOutcome> = mutableOutcomes

    fun emit(outcome: InstallOutcome) {
        mutableOutcomes.tryEmit(outcome)
    }
}

internal fun interface ReleaseSource {
    suspend fun latest(): FetchResult
}

internal fun interface ApkDownloader {
    suspend fun download(asset: ReleaseAsset, onProgress: (Int?) -> Unit): DownloadResult
}

internal interface ApkInstaller {
    /** Whether the user allowed this app to install packages, under Install unknown apps. */
    fun canInstall(): Boolean

    /** Hands the file to the system, which answers later through [UpdateController.onInstallOutcome]. */
    fun install(file: File): String?
}

internal interface UpdateStore {
    suspend fun enabled(): Boolean
    suspend fun setEnabled(enabled: Boolean)
    suspend fun lastAttemptMs(): Long?

    /** [body] is the last good API answer, so a day without a check still shows its result. */
    suspend fun recordAttempt(atMs: Long, body: String?)
    suspend fun cachedBody(): String?
}

internal sealed interface CheckFailure {
    data class NoNetwork(val reason: String) : CheckFailure
    data class RateLimited(val resetEpochSeconds: Long?) : CheckFailure
    data class Http(val code: Int) : CheckFailure
    data class BadResponse(val reason: String) : CheckFailure
}

internal sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data class Checked(val result: CheckResult) : UpdatePhase
    data class CheckFailed(val failure: CheckFailure) : UpdatePhase
    data class Downloading(val percent: Int?) : UpdatePhase
    data object PermissionNeeded : UpdatePhase
    data object Confirming : UpdatePhase
    data class DownloadFailed(val result: DownloadResult) : UpdatePhase
    data class InstallEnded(val outcome: InstallOutcome) : UpdatePhase
    data class InstallNotStarted(val reason: String) : UpdatePhase
}

internal data class UpdateState(
    val enabled: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val offer: CheckResult.Available? = null,
    val lastCheckedMs: Long? = null,
    val installedVersion: String? = null,
) {
    val busy: Boolean
        get() = phase == UpdatePhase.Checking || phase is UpdatePhase.Downloading ||
            phase == UpdatePhase.Confirming
}

/** Every network call goes through here, and none happens while the switch is off. */
internal class UpdateController(
    private val source: ReleaseSource,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val store: UpdateStore,
    private val installedVersion: () -> String?,
    private val clock: () -> Long,
) {
    private val mutableState = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    suspend fun onAppOpen() {
        val enabled = store.enabled()
        val last = store.lastAttemptMs()
        mutableState.value = UpdateState(
            enabled = enabled,
            lastCheckedMs = last,
            installedVersion = installedVersion(),
        )
        if (!enabled) return
        if (UpdateSchedule.isDue(clock(), last)) check() else showCached()
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
        if (enabled) onAppOpen() else mutableState.value = UpdateState(enabled = false)
    }

    suspend fun checkNow() {
        if (mutableState.value.enabled && !mutableState.value.busy) check()
    }

    private suspend fun showCached() {
        val body = store.cachedBody() ?: return
        val release = ReleaseParser.parseOrNull(body) ?: return
        show(CheckResult.decide(installedVersion(), release))
    }

    private suspend fun check() {
        update { it.copy(phase = UpdatePhase.Checking) }
        val now = clock()
        val fetched = source.latest()
        store.recordAttempt(now, (fetched as? FetchResult.Ok)?.body)
        update { it.copy(lastCheckedMs = now) }
        when (fetched) {
            is FetchResult.Ok -> {
                val release = try {
                    ReleaseParser.parse(fetched.body)
                } catch (e: ReleaseParser.MalformedRelease) {
                    fail(CheckFailure.BadResponse(e.message))
                    return
                }
                show(CheckResult.decide(installedVersion(), release))
            }
            FetchResult.NotFound -> show(CheckResult.NothingPublished)
            is FetchResult.RateLimited -> fail(CheckFailure.RateLimited(fetched.resetEpochSeconds))
            is FetchResult.HttpError -> fail(CheckFailure.Http(fetched.code))
            is FetchResult.NoNetwork -> fail(CheckFailure.NoNetwork(fetched.reason))
        }
    }

    private fun show(result: CheckResult) = update {
        it.copy(
            phase = UpdatePhase.Checked(result),
            offer = result as? CheckResult.Available,
            installedVersion = installedVersion(),
        )
    }

    private fun fail(failure: CheckFailure) = update {
        it.copy(phase = UpdatePhase.CheckFailed(failure))
    }

    suspend fun downloadAndInstall() {
        val current = mutableState.value
        // Switching off clears the offer, so an offer implies the switch is on.
        val offer = current.offer ?: return
        if (current.busy) return
        if (!installer.canInstall()) {
            update { it.copy(phase = UpdatePhase.PermissionNeeded) }
            return
        }
        update { it.copy(phase = UpdatePhase.Downloading(null)) }
        val downloaded = downloader.download(offer.asset) { percent ->
            update { it.copy(phase = UpdatePhase.Downloading(percent)) }
        }
        if (downloaded !is DownloadResult.Ok) {
            update { it.copy(phase = UpdatePhase.DownloadFailed(downloaded)) }
            return
        }
        val refusal = installer.install(downloaded.file)
        update {
            it.copy(phase = refusal?.let(UpdatePhase::InstallNotStarted) ?: UpdatePhase.Confirming)
        }
    }

    /** Returning from Install unknown apps with the permission granted carries on by itself. */
    suspend fun onResume() {
        if (mutableState.value.phase == UpdatePhase.PermissionNeeded && installer.canInstall()) {
            update { it.copy(phase = UpdatePhase.Idle) }
            downloadAndInstall()
        }
    }

    fun onInstallOutcome(outcome: InstallOutcome) = update {
        it.copy(phase = UpdatePhase.InstallEnded(outcome))
    }

    private inline fun update(transform: (UpdateState) -> UpdateState) {
        mutableState.value = transform(mutableState.value)
    }
}
