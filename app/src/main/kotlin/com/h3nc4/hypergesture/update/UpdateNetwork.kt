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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext

private const val TAG = "HyperGesture"
private const val TIMEOUT_MS = 15_000
private const val MAX_REDIRECTS = 5
private const val BUFFER_BYTES = 64 * 1024

internal const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/h3nc4/HyperGesture/releases/latest"

internal fun userAgent(versionName: String?) = "HyperGesture/${versionName ?: "unknown"}"

internal fun Throwable.reason(): String = message ?: javaClass.name

internal interface HttpResponse : Closeable {
    val code: Int
    val contentLength: Long
    fun header(name: String): String?
    fun body(): InputStream
}

/** One request and no redirects, which [followRedirects] takes so it can check every hop. */
internal fun interface HttpTransport {
    fun open(url: String, accept: String): HttpResponse
}

internal class UrlConnectionTransport(private val userAgent: String) : HttpTransport {

    override fun open(url: String, accept: String): HttpResponse {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", accept)
        }
        return object : HttpResponse {
            override val code = connection.responseCode
            override val contentLength get() = connection.contentLengthLong
            override fun header(name: String): String? = connection.getHeaderField(name)
            override fun body(): InputStream = connection.inputStream
            override fun close() = connection.disconnect()
        }
    }
}

internal class BlockedHostException(val host: String) : IOException("blocked host $host")

internal fun followRedirects(transport: HttpTransport, url: String, accept: String): HttpResponse {
    var target = url
    repeat(MAX_REDIRECTS + 1) {
        if (!AllowedHosts.isAllowed(target)) throw BlockedHostException(AllowedHosts.hostOf(target))
        val response = transport.open(target, accept)
        if (response.code !in 300..399) return response
        val location = response.header("Location")
        response.close()
        target = resolve(target, location ?: throw IOException("redirect without a location"))
    }
    throw IOException("too many redirects")
}

private fun resolve(base: String, location: String): String = try {
    URI(base).resolve(location).toString()
} catch (e: IllegalArgumentException) {
    throw IOException("bad redirect location", e)
}

internal class GitHubReleaseSource(private val transport: HttpTransport) : ReleaseSource {

    override suspend fun latest(): FetchResult = withContext(Dispatchers.IO) {
        try {
            followRedirects(transport, LATEST_RELEASE_URL, "application/vnd.github+json").use {
                FetchResult.of(
                    code = it.code,
                    remaining = it.header("x-ratelimit-remaining"),
                    reset = it.header("x-ratelimit-reset"),
                ) { it.body().use { body -> body.readBytes().decodeToString() } }
            }
        } catch (e: IOException) {
            Log.i(TAG, "update check failed: ${e.javaClass.simpleName}")
            FetchResult.NoNetwork(e.reason())
        }
    }
}

/** Reports a change of whole percent, or null throughout when the size is unknown. */
internal class ProgressMeter(private val total: Long) {
    private var written = 0L
    private var started = false

    var percent: Int? = null
        private set

    /** Whether [percent] moved, so progress goes out once per step rather than per buffer. */
    fun add(bytes: Int): Boolean {
        written += bytes
        val next = if (total > 0) (written * 100 / total).toInt() else null
        val moved = !started || next != percent
        started = true
        percent = next
        return moved
    }
}

/** Downloads into app-private storage that no other app can read or replace. */
internal class HttpApkDownloader(
    private val directory: File,
    private val transport: HttpTransport,
    private val progressContext: CoroutineContext,
) : ApkDownloader {

    override suspend fun download(asset: ReleaseAsset, onProgress: (Int?) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            val file = File(directory, ReleaseParser.APK_NAME)
            val result = try {
                Files.createDirectories(directory.toPath())
                Files.list(directory.toPath()).use { stale -> stale.forEach(::discard) }
                fetch(asset, file, onProgress)
            } catch (e: BlockedHostException) {
                DownloadResult.BlockedHost(e.host)
            } catch (e: IOException) {
                DownloadResult.Failed(e.reason())
            }
            if (result !is DownloadResult.Ok) discard(file.toPath())
            result
        }

    private suspend fun fetch(asset: ReleaseAsset, file: File, onProgress: (Int?) -> Unit): DownloadResult =
        followRedirects(transport, asset.downloadUrl, "application/octet-stream").use { response ->
            if (response.code != 200) return DownloadResult.Failed("HTTP ${response.code}")
            val meter = ProgressMeter(asset.size.takeIf { it > 0 } ?: response.contentLength)
            val actual = copyHashing(response.body(), file) { bytes ->
                if (meter.add(bytes)) withContext(progressContext) { onProgress(meter.percent) }
            }
            when (asset.sha256) {
                null -> DownloadResult.Ok(file, verified = false)
                actual -> DownloadResult.Ok(file, verified = true)
                else -> DownloadResult.ChecksumMismatch
            }
        }

    private suspend fun copyHashing(input: InputStream, file: File, onChunk: suspend (Int) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { source ->
            file.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                var read = source.read(buffer)
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    onChunk(read)
                    read = source.read(buffer)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // A file left behind is harmless, since the next download clears the directory first.
    private fun discard(path: Path) {
        runCatching { Files.deleteIfExists(path) }
    }
}
