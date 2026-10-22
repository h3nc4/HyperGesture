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

import java.net.URI

/** The release workflow tags `v1.2.3` and scripts/version.sh writes `1.2.3` as versionName. */
internal data class AppVersion(val numbers: List<Int>, val suffix: String?) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(numbers.size, other.numbers.size)) {
            val diff = (numbers.getOrNull(i) ?: 0) - (other.numbers.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        // A suffix marks a pre-release, which sorts below the plain version it leads to.
        return when {
            suffix == other.suffix -> 0
            suffix == null -> 1
            other.suffix == null -> -1
            else -> suffix.compareTo(other.suffix)
        }
    }

    override fun toString(): String =
        numbers.joinToString(".") + (suffix?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?$""")

        fun parse(text: String?): AppVersion? {
            val match = text?.trim()?.let(PATTERN::matchEntire) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(numbers, match.groupValues[2].ifEmpty { null })
        }
    }
}

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    /** Lower-case hex, or null when GitHub published no SHA-256 digest for the asset. */
    val sha256: String?,
)

internal data class Release(
    val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val apk: ReleaseAsset?,
) {
    val version: AppVersion? get() = AppVersion.parse(tag)
}

internal object ReleaseParser {

    const val APK_NAME = "hypergesture.apk"

    class MalformedRelease(override val message: String, cause: Throwable? = null) :
        Exception(message, cause)

    fun parse(body: String): Release {
        val json = try {
            Json.parse(body)
        } catch (e: Json.MalformedJson) {
            throw MalformedRelease(e.message, e)
        }
        val root = json as? Map<*, *> ?: throw MalformedRelease("not an object")
        val tag = root["tag_name"] as? String ?: throw MalformedRelease("no tag_name")
        val assets = (root["assets"] as? List<*>).orEmpty().mapNotNull(::asset)
        return Release(
            tag = tag,
            draft = root["draft"] == true,
            prerelease = root["prerelease"] == true,
            apk = assets.firstOrNull { it.name == APK_NAME }
                ?: assets.singleOrNull { it.name.endsWith(".apk") },
        )
    }

    fun parseOrNull(body: String): Release? = try {
        parse(body)
    } catch (_: MalformedRelease) {
        null
    }

    private fun asset(raw: Any?): ReleaseAsset? {
        val map = raw as? Map<*, *> ?: return null
        return ReleaseAsset(
            name = map["name"] as? String ?: return null,
            downloadUrl = map["browser_download_url"] as? String ?: return null,
            size = (map["size"] as? Double)?.toLong() ?: -1,
            sha256 = sha256Of(map["digest"] as? String),
        )
    }

    /** GitHub writes `sha256:<hex>`, and any other algorithm is treated as no digest. */
    fun sha256Of(digest: String?): String? {
        val hex = digest?.removePrefix("sha256:")?.lowercase() ?: return null
        if (hex == digest.lowercase()) return null
        return hex.takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } }
    }
}

internal sealed interface CheckResult {
    data class UpToDate(val latest: String) : CheckResult
    data class Available(val tag: String, val asset: ReleaseAsset) : CheckResult
    data class NoApkAsset(val tag: String) : CheckResult
    data object NothingPublished : CheckResult
    data class UnreadableVersion(val text: String) : CheckResult

    companion object {
        fun decide(installedVersionName: String?, release: Release?): CheckResult {
            if (release == null || release.draft || release.prerelease) return NothingPublished
            val latest = release.version ?: return UnreadableVersion(release.tag)
            val installed = AppVersion.parse(installedVersionName)
                ?: return UnreadableVersion(installedVersionName.orEmpty())
            if (latest <= installed) return UpToDate(release.tag)
            val apk = release.apk ?: return NoApkAsset(release.tag)
            return Available(release.tag, apk)
        }
    }
}

internal object UpdateSchedule {
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /** A clock set backwards counts as due, or a wrong date would silence the check for days. */
    fun isDue(nowMs: Long, lastAttemptMs: Long?): Boolean =
        lastAttemptMs == null || nowMs < lastAttemptMs || nowMs - lastAttemptMs >= INTERVAL_MS
}

/** The API host plus where GitHub redirects an asset download, and nothing else. */
internal object AllowedHosts {
    private val EXACT = setOf("api.github.com", "github.com")
    private const val ASSET_SUFFIX = ".githubusercontent.com"

    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            (host in EXACT || host.endsWith(ASSET_SUFFIX))
    }

    fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
}
