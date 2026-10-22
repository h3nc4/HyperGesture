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

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateControllerTest {

    private class FakeSource(var answer: FetchResult) : ReleaseSource {
        var calls = 0
        var whileFetching: suspend () -> Unit = {}
        override suspend fun latest(): FetchResult {
            calls++
            whileFetching()
            return answer
        }
    }

    private class FakeDownloader(var answer: DownloadResult) : ApkDownloader {
        var calls = 0
        var whileDownloading: suspend () -> Unit = {}
        override suspend fun download(asset: ReleaseAsset, onProgress: (Int?) -> Unit): DownloadResult {
            calls++
            onProgress(50)
            whileDownloading()
            return answer
        }
    }

    private class FakeInstaller(var allowed: Boolean, var refusal: String? = null) : ApkInstaller {
        val installed = mutableListOf<File>()
        override fun canInstall() = allowed
        override fun install(file: File): String? {
            installed += file
            return refusal
        }
    }

    private class FakeStore(var on: Boolean, var last: Long? = null, var body: String? = null) : UpdateStore {
        override suspend fun enabled() = on
        override suspend fun setEnabled(enabled: Boolean) {
            on = enabled
        }
        override suspend fun lastAttemptMs() = last
        override suspend fun recordAttempt(atMs: Long, body: String?) {
            last = atMs
            if (body != null) this.body = body
        }
        override suspend fun cachedBody() = body
    }

    private val release = fixture()
    private val tag = ReleaseParser.parse(release).tag
    private val apk = File("hypergesture.apk")

    private val source = FakeSource(FetchResult.Ok(release))
    private val downloader = FakeDownloader(DownloadResult.Ok(apk, verified = true))
    private val installer = FakeInstaller(allowed = true)
    private var now = 10 * UpdateSchedule.INTERVAL_MS
    private var installedVersion = "0.0.1"

    private fun controller(store: FakeStore) = UpdateController(
        source = source,
        downloader = downloader,
        installer = installer,
        store = store,
        installedVersion = { installedVersion },
        clock = { now },
    )

    private val UpdateController.phase get() = state.value.phase

    @Test
    fun `switched off, opening the app makes no request`() = runBlocking {
        val controller = controller(FakeStore(on = false))
        controller.onAppOpen()
        controller.checkNow()
        controller.downloadAndInstall()
        assertEquals(0, source.calls)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun `switched on, the first open checks and offers a newer release`() = runBlocking {
        val store = FakeStore(on = true)
        val controller = controller(store)
        controller.onAppOpen()
        assertEquals(1, source.calls)
        assertEquals(tag, controller.state.value.offer?.tag)
        assertEquals(now, store.last)
        assertEquals(release, store.body)
    }

    @Test
    fun `a second open on the same day shows the cached answer without a request`() = runBlocking {
        val store = FakeStore(on = true, last = now - 1000, body = release)
        val controller = controller(store)
        controller.onAppOpen()
        assertEquals(0, source.calls)
        assertEquals(tag, controller.state.value.offer?.tag)
    }

    @Test
    fun `check now ignores the daily limit`() = runBlocking {
        val controller = controller(FakeStore(on = true, last = now - 1000))
        controller.onAppOpen()
        controller.checkNow()
        assertEquals(1, source.calls)
    }

    @Test
    fun `installed release newer than github is up to date`() = runBlocking {
        installedVersion = "999.0.0"
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        assertEquals(UpdatePhase.Checked(CheckResult.UpToDate(tag)), controller.phase)
        assertNull(controller.state.value.offer)
    }

    @Test
    fun `check now reports the version installed at that moment`() = runBlocking {
        installedVersion = "999.0.0"
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        installedVersion = "0.0.1"
        controller.checkNow()
        assertEquals("0.0.1", controller.state.value.installedVersion)
        assertEquals(tag, controller.state.value.offer?.tag)
    }

    @Test
    fun `turning the switch on checks at once and off clears the offer`() = runBlocking {
        val store = FakeStore(on = false)
        val controller = controller(store)
        controller.onAppOpen()
        controller.setEnabled(true)
        assertEquals(1, source.calls)
        controller.setEnabled(false)
        assertEquals(UpdateState(enabled = false), controller.state.value)
        assertEquals(false, store.on)
    }

    @Test
    fun `every failed check is reported`() = runBlocking {
        val cases = mapOf(
            FetchResult.NoNetwork("timeout") to CheckFailure.NoNetwork("timeout"),
            FetchResult.RateLimited(7) to CheckFailure.RateLimited(7),
            FetchResult.HttpError(502) to CheckFailure.Http(502),
        )
        for ((answer, failure) in cases) {
            source.answer = answer
            val controller = controller(FakeStore(on = true))
            controller.onAppOpen()
            assertEquals(UpdatePhase.CheckFailed(failure), controller.phase)
        }
        source.answer = FetchResult.Ok("<html>")
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        assertTrue(controller.phase.let { it is UpdatePhase.CheckFailed && it.failure is CheckFailure.BadResponse })
    }

    @Test
    fun `no releases at all reads as nothing published`() = runBlocking {
        source.answer = FetchResult.NotFound
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        assertEquals(UpdatePhase.Checked(CheckResult.NothingPublished), controller.phase)
    }

    @Test
    fun `install waits for the unknown apps permission and resumes once granted`() = runBlocking {
        installer.allowed = false
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        controller.downloadAndInstall()
        assertEquals(UpdatePhase.PermissionNeeded, controller.phase)
        assertEquals(0, downloader.calls)

        installer.allowed = true
        controller.onResume()
        assertEquals(UpdatePhase.Confirming, controller.phase)
        assertEquals(listOf(apk), installer.installed)
    }

    @Test
    fun `a checksum mismatch never reaches the installer`() = runBlocking {
        downloader.answer = DownloadResult.ChecksumMismatch
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        controller.downloadAndInstall()
        assertEquals(UpdatePhase.DownloadFailed(DownloadResult.ChecksumMismatch), controller.phase)
        assertTrue(installer.installed.isEmpty())
    }

    @Test
    fun `installer refusing the session is reported`() = runBlocking {
        installer.refusal = "no space"
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        controller.downloadAndInstall()
        assertEquals(UpdatePhase.InstallNotStarted("no space"), controller.phase)
    }

    @Test
    fun `the system's answer ends the install and keeps the offer for a retry`() = runBlocking {
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        controller.downloadAndInstall()
        controller.onInstallOutcome(InstallOutcome.Declined)
        assertEquals(UpdatePhase.InstallEnded(InstallOutcome.Declined), controller.phase)
        assertEquals(tag, controller.state.value.offer?.tag)
    }

    @Test
    fun `a check already running is not started again`() = runBlocking {
        val controller = controller(FakeStore(on = true))
        source.whileFetching = {
            assertTrue(controller.state.value.busy)
            controller.checkNow()
        }
        controller.onAppOpen()
        assertEquals(1, source.calls)
    }

    @Test
    fun `nothing else starts while a download runs`() = runBlocking {
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        downloader.whileDownloading = {
            assertEquals(UpdatePhase.Downloading(50), controller.phase)
            controller.checkNow()
            controller.downloadAndInstall()
        }
        controller.downloadAndInstall()
        assertEquals(1, source.calls)
        assertEquals(1, downloader.calls)
    }

    @Test
    fun `an install waiting on the system prompt blocks a second one`() = runBlocking {
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        controller.downloadAndInstall()
        assertEquals(UpdatePhase.Confirming, controller.phase)
        controller.downloadAndInstall()
        controller.checkNow()
        assertEquals(1, downloader.calls)
        assertEquals(1, source.calls)
    }

    @Test
    fun `a same-day open with no usable cached answer shows nothing`() = runBlocking {
        for (body in listOf(null, "<html>")) {
            val controller = controller(FakeStore(on = true, last = now - 1000, body = body))
            controller.onAppOpen()
            assertEquals(UpdatePhase.Idle, controller.phase)
            assertNull(controller.state.value.offer)
        }
        assertEquals(0, source.calls)
    }

    @Test
    fun `resuming leaves the phase alone unless the permission was the wait`() = runBlocking {
        val controller = controller(FakeStore(on = true))
        controller.onAppOpen()
        controller.onResume()
        assertEquals(0, downloader.calls)

        installer.allowed = false
        controller.downloadAndInstall()
        controller.onResume()
        assertEquals(UpdatePhase.PermissionNeeded, controller.phase)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun `an install answer broadcast by the system reaches the screen`() = runBlocking {
        val received = async(start = CoroutineStart.UNDISPATCHED) { InstallEvents.outcomes.first() }
        InstallEvents.emit(InstallOutcome.Installed)
        assertEquals(InstallOutcome.Installed, received.await())
    }
}
