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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun fixture(): String =
    requireNotNull(ReleaseTest::class.java.getResource("/github-release-latest.json")).readText()

class ReleaseTest {

    @Test
    fun `json reader handles nesting, escapes and literals`() {
        val parsed = Json.parse("""{"a":[1,2.5,-3e2],"b":"x\"é\n","c":true,"d":null,"e":{}}""")
            as Map<*, *>
        assertEquals(listOf(1.0, 2.5, -300.0), parsed["a"])
        assertEquals("x\"é\n", parsed["b"])
        assertEquals(true, parsed["c"])
        assertTrue(parsed.containsKey("d") && parsed["d"] == null)
        assertEquals(emptyMap<String, Any?>(), parsed["e"])
    }

    @Test(expected = Json.MalformedJson::class)
    fun `json reader refuses trailing content`() {
        Json.parse("""{"a":1} x""")
    }

    @Test(expected = Json.MalformedJson::class)
    fun `json reader refuses an unterminated string`() {
        Json.parse("""{"a":"open""")
    }

    @Test
    fun `tag and versionName parse to the same version`() {
        assertEquals(AppVersion.parse("1.0.0"), AppVersion.parse("v1.0.0"))
        assertEquals(listOf(1, 2, 3), AppVersion.parse("v1.2.3")?.numbers)
    }

    @Test
    fun `versions compare numerically, not as text`() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.0")!!)
        assertTrue(AppVersion.parse("1.0.1")!! > AppVersion.parse("1.0.0")!!)
        assertEquals(0, AppVersion.parse("1.0")!!.compareTo(AppVersion.parse("1.0.0")!!))
    }

    @Test
    fun `a pre-release sorts below its plain version`() {
        assertTrue(AppVersion.parse("1.1.0-rc1")!! < AppVersion.parse("1.1.0")!!)
        assertTrue(AppVersion.parse("1.1.0")!! > AppVersion.parse("1.1.0-rc1")!!)
        assertTrue(AppVersion.parse("1.1.0-rc1")!! > AppVersion.parse("1.0.0")!!)
    }

    @Test
    fun `a longer version beats its shorter prefix only by a non-zero part`() {
        assertEquals(0, AppVersion.parse("1.0.0")!!.compareTo(AppVersion.parse("1.0")!!))
        assertTrue(AppVersion.parse("1.0.1")!! > AppVersion.parse("1.0")!!)
    }

    @Test
    fun `two pre-releases of one version sort by their suffix`() {
        assertTrue(AppVersion.parse("1.1.0-rc1")!! < AppVersion.parse("1.1.0-rc2")!!)
        assertEquals(0, AppVersion.parse("1.1.0-rc1")!!.compareTo(AppVersion.parse("v1.1.0-rc1")!!))
    }

    @Test
    fun `a version prints the way the release workflow names it`() {
        assertEquals("1.2.3", AppVersion.parse("v1.2.3").toString())
        assertEquals("1.2.3-rc.1", AppVersion.parse("1.2.3-rc.1").toString())
    }

    @Test
    fun `garbage is no version`() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse("1..2"))
        assertNull(AppVersion.parse("99999999999.0.0"))
    }

    @Test
    fun `saved api response parses to its tag and apk`() {
        val release = ReleaseParser.parse(fixture())
        assertTrue(release.tag.startsWith("v"))
        assertFalse(release.draft)
        assertFalse(release.prerelease)
        val apk = requireNotNull(release.apk)
        assertEquals(ReleaseParser.APK_NAME, apk.name)
        assertTrue(apk.downloadUrl, AllowedHosts.isAllowed(apk.downloadUrl))
        assertEquals("v1.0.0", release.tag)
        assertEquals(2_465_170L, apk.size)
        assertEquals("59d3a65c37abb67152201551453cf3f685d3254954bf130072e1750273e06a78", apk.sha256)
    }

    @Test
    fun `digest is read only as sha256 hex`() {
        val hex = "AB".repeat(32)
        assertEquals(hex.lowercase(), ReleaseParser.sha256Of("sha256:$hex"))
        assertNull(ReleaseParser.sha256Of("sha512:$hex"))
        assertNull(ReleaseParser.sha256Of(hex))
        assertNull(ReleaseParser.sha256Of("sha256:abc"))
        assertNull(ReleaseParser.sha256Of("sha256:" + "zz".repeat(32)))
        assertNull(ReleaseParser.sha256Of(null))
    }

    @Test
    fun `release without an apk asset parses with none`() {
        val release = ReleaseParser.parse("""{"tag_name":"v2.0.0","assets":[{"name":"notes.txt","browser_download_url":"https://github.com/x"}]}""")
        assertNull(release.apk)
    }

    @Test(expected = ReleaseParser.MalformedRelease::class)
    fun `response without a tag is malformed`() {
        ReleaseParser.parse("""{"message":"Not Found"}""")
    }

    @Test
    fun `a body that is not a release object is malformed`() {
        for (body in listOf("<html>", "[]", """{"tag_name":1}""")) {
            assertThrows(body, ReleaseParser.MalformedRelease::class.java) { ReleaseParser.parse(body) }
            assertNull(body, ReleaseParser.parseOrNull(body))
        }
        val cause = assertThrows(ReleaseParser.MalformedRelease::class.java) { ReleaseParser.parse("<html>") }
        assertTrue(cause.cause is Json.MalformedJson)
    }

    @Test
    fun `a release with no assets list has no apk`() {
        val release = requireNotNull(ReleaseParser.parseOrNull("""{"tag_name":"v2.0.0","draft":true}"""))
        assertNull(release.apk)
        assertTrue(release.draft)
    }

    @Test
    fun `incomplete assets are skipped and a lone apk of another name is taken`() {
        val release = ReleaseParser.parse(
            """{"tag_name":"v2.0.0","prerelease":true,"assets":[
                1,
                {"browser_download_url":"https://github.com/no-name.apk"},
                {"name":"no-url.apk"},
                {"name":"renamed.apk","browser_download_url":"https://github.com/renamed.apk"}
            ]}""",
        )
        assertTrue(release.prerelease)
        val apk = requireNotNull(release.apk)
        assertEquals("renamed.apk", apk.name)
        assertEquals(-1L, apk.size)
        assertNull(apk.sha256)
    }

    @Test
    fun `two apks of other names leave the choice unmade`() {
        val release = ReleaseParser.parse(
            """{"tag_name":"v2.0.0","assets":[
                {"name":"a.apk","browser_download_url":"https://github.com/a.apk"},
                {"name":"b.apk","browser_download_url":"https://github.com/b.apk"}
            ]}""",
        )
        assertNull(release.apk)
    }

    private fun release(tag: String, apk: Boolean = true, draft: Boolean = false, pre: Boolean = false) =
        Release(
            tag = tag,
            draft = draft,
            prerelease = pre,
            apk = ReleaseAsset("hypergesture.apk", "https://github.com/a.apk", 1, null).takeIf { apk },
        )

    @Test
    fun `same release as installed is up to date`() {
        assertEquals(CheckResult.UpToDate("v1.0.0"), CheckResult.decide("1.0.0", release("v1.0.0")))
    }

    @Test
    fun `older release than installed is up to date`() {
        assertEquals(CheckResult.UpToDate("v1.0.0"), CheckResult.decide("1.0.1", release("v1.0.0")))
    }

    @Test
    fun `newer release is offered with its apk`() {
        val result = CheckResult.decide("0.9.0", release("v1.0.0"))
        assertTrue(result is CheckResult.Available)
        assertEquals("v1.0.0", (result as CheckResult.Available).tag)
    }

    @Test
    fun `newer release without an apk says so`() {
        assertEquals(CheckResult.NoApkAsset("v2.0.0"), CheckResult.decide("1.0.0", release("v2.0.0", apk = false)))
    }

    @Test
    fun `no release at all is nothing published`() {
        assertEquals(CheckResult.NothingPublished, CheckResult.decide("1.0.0", null))
    }

    @Test
    fun `an app reporting no version is unreadable`() {
        assertEquals(CheckResult.UnreadableVersion(""), CheckResult.decide(null, release("v1.0.0")))
    }

    @Test
    fun `drafts and prereleases are ignored`() {
        assertEquals(CheckResult.NothingPublished, CheckResult.decide("1.0.0", release("v2.0.0", draft = true)))
        assertEquals(CheckResult.NothingPublished, CheckResult.decide("1.0.0", release("v2.0.0", pre = true)))
    }

    @Test
    fun `unreadable versions are reported, not guessed`() {
        assertEquals(CheckResult.UnreadableVersion("nightly"), CheckResult.decide("1.0.0", release("nightly")))
        assertEquals(CheckResult.UnreadableVersion("dev"), CheckResult.decide("dev", release("v1.0.0")))
    }

    @Test
    fun `check is due once a day`() {
        val day = UpdateSchedule.INTERVAL_MS
        assertTrue(UpdateSchedule.isDue(nowMs = 1_000, lastAttemptMs = null))
        assertFalse(UpdateSchedule.isDue(nowMs = day - 1, lastAttemptMs = 0))
        assertTrue(UpdateSchedule.isDue(nowMs = day, lastAttemptMs = 0))
        assertTrue(UpdateSchedule.isDue(nowMs = 0, lastAttemptMs = day))
    }

    @Test
    fun `only github hosts over https are allowed`() {
        assertTrue(AllowedHosts.isAllowed(LATEST_RELEASE_URL))
        assertTrue(AllowedHosts.isAllowed("https://github.com/h3nc4/HyperGesture/releases/download/v1.0.0/hypergesture.apk"))
        assertTrue(AllowedHosts.isAllowed("https://release-assets.githubusercontent.com/github-production-release-asset/1"))
        assertFalse(AllowedHosts.isAllowed("http://github.com/a"))
        assertFalse(AllowedHosts.isAllowed("https://evilgithub.com/a"))
        assertFalse(AllowedHosts.isAllowed("https://githubusercontent.com.evil.example/a"))
        assertFalse(AllowedHosts.isAllowed("not a url"))
        assertFalse(AllowedHosts.isAllowed("https:relative/path"))
    }

    @Test
    fun `a refused url is named by its host, or whole when it has none`() {
        assertEquals("evil.example", AllowedHosts.hostOf("https://evil.example/a.apk"))
        assertEquals("not a url", AllowedHosts.hostOf("not a url"))
        assertEquals("file:/data/a.apk", AllowedHosts.hostOf("file:/data/a.apk"))
    }

    @Test
    fun `http answers map to fetch results`() {
        assertEquals(FetchResult.Ok("b"), FetchResult.of(200, "59", null) { "b" })
        assertEquals(FetchResult.NotFound, FetchResult.of(404, "59", null) { "" })
        assertEquals(FetchResult.RateLimited(42), FetchResult.of(403, "0", "42") { "" })
        assertEquals(FetchResult.RateLimited(null), FetchResult.of(429, null, null) { "" })
        assertEquals(FetchResult.HttpError(403), FetchResult.of(403, "10", null) { "" })
        assertEquals(FetchResult.HttpError(500), FetchResult.of(500, null, null) { "" })
    }

    @Test
    fun `installer statuses map to outcomes`() {
        assertNull(InstallOutcome.of(PackageInstaller.STATUS_PENDING_USER_ACTION, null))
        assertEquals(InstallOutcome.Installed, InstallOutcome.of(PackageInstaller.STATUS_SUCCESS, null))
        assertEquals(InstallOutcome.Declined, InstallOutcome.of(PackageInstaller.STATUS_FAILURE_ABORTED, null))
        val conflict = InstallOutcome.of(PackageInstaller.STATUS_FAILURE_CONFLICT, "signatures do not match")
        assertTrue((conflict as InstallOutcome.Failed).signatureConflict)
        val storage = InstallOutcome.of(PackageInstaller.STATUS_FAILURE_STORAGE, null)
        assertFalse((storage as InstallOutcome.Failed).signatureConflict)
    }
}
