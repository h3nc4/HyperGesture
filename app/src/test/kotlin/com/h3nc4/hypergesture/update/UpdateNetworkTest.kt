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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext

class UpdateNetworkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeResponse(
        override val code: Int,
        private val bytes: ByteArray = ByteArray(0),
        private val headers: Map<String, String> = emptyMap(),
        override val contentLength: Long = bytes.size.toLong(),
        private val stream: () -> InputStream = { ByteArrayInputStream(bytes) },
    ) : HttpResponse {
        var closed = false
        override fun header(name: String) = headers[name]
        override fun body() = stream()
        override fun close() {
            closed = true
        }
    }

    private class FakeTransport(private val routes: Map<String, () -> FakeResponse>) : HttpTransport {
        val opened = mutableListOf<Pair<String, String>>()
        override fun open(url: String, accept: String): HttpResponse {
            opened += url to accept
            return (routes[url] ?: throw IOException("connection refused"))()
        }
    }

    private fun redirect(location: String?) =
        FakeResponse(302, headers = location?.let { mapOf("Location" to it) }.orEmpty())

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // Four 64 KiB buffers and a bit, so progress moves more than once.
    private val apkBytes = ByteArray(200_000) { (it % 251).toByte() }
    private val apkUrl = "https://github.com/h3nc4/HyperGesture/releases/download/v9.0.0/hypergesture.apk"

    private fun asset(
        url: String = apkUrl,
        size: Long = apkBytes.size.toLong(),
        sha256: String? = sha256(apkBytes),
    ) = ReleaseAsset(ReleaseParser.APK_NAME, url, size, sha256)

    private val directory by lazy { File(temp.root, "update") }

    private fun downloader(transport: HttpTransport) =
        HttpApkDownloader(directory, transport, EmptyCoroutineContext)

    private fun serving(response: () -> FakeResponse = { FakeResponse(200, apkBytes) }) =
        FakeTransport(mapOf(apkUrl to response))

    private fun download(transport: HttpTransport, asset: ReleaseAsset = asset()): Pair<DownloadResult, List<Int?>> {
        val reports = mutableListOf<Int?>()
        val result = runBlocking { downloader(transport).download(asset) { reports += it } }
        return result to reports
    }

    @Test
    fun `a redirect is resolved against the hop it came from and that hop is closed`() {
        val first = redirect("/assets/1")
        val transport = FakeTransport(
            mapOf(
                LATEST_RELEASE_URL to { first },
                "https://api.github.com/assets/1" to { FakeResponse(200) },
            ),
        )
        val response = followRedirects(transport, LATEST_RELEASE_URL, "a/b")
        assertEquals(200, response.code)
        assertTrue(first.closed)
        assertEquals(
            listOf(LATEST_RELEASE_URL to "a/b", "https://api.github.com/assets/1" to "a/b"),
            transport.opened,
        )
    }

    @Test
    fun `a redirect off github is refused before it is opened`() {
        val transport = FakeTransport(mapOf(apkUrl to { redirect("https://evil.example/a.apk") }))
        val error = assertThrows(BlockedHostException::class.java) {
            followRedirects(transport, apkUrl, "a/b")
        }
        assertEquals("evil.example", error.host)
        assertEquals(1, transport.opened.size)
    }

    @Test
    fun `broken redirects end the request`() {
        val cases = mapOf(
            null to "redirect without a location",
            "https://[broken" to "bad redirect location",
            apkUrl to "too many redirects",
        )
        for ((location, reason) in cases) {
            val transport = FakeTransport(mapOf(apkUrl to { redirect(location) }))
            val error = assertThrows(IOException::class.java) { followRedirects(transport, apkUrl, "a/b") }
            assertEquals(reason, error.message)
        }
    }

    @Test
    fun `a redirect loop gives up after five hops`() {
        val transport = FakeTransport(mapOf(apkUrl to { redirect(apkUrl) }))
        assertThrows(IOException::class.java) { followRedirects(transport, apkUrl, "a/b") }
        assertEquals(6, transport.opened.size)
    }

    @Test
    fun `the release source reads the latest release and its quota`() = runBlocking {
        val body = """{"tag_name":"v9.0.0"}"""
        val ok = FakeTransport(mapOf(LATEST_RELEASE_URL to { FakeResponse(200, body.toByteArray()) }))
        assertEquals(FetchResult.Ok(body), GitHubReleaseSource(ok).latest())
        assertEquals("application/vnd.github+json", ok.opened.single().second)

        val quota = mapOf("x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "1700000000")
        val limited = FakeTransport(mapOf(LATEST_RELEASE_URL to { FakeResponse(403, headers = quota) }))
        assertEquals(FetchResult.RateLimited(1_700_000_000), GitHubReleaseSource(limited).latest())
    }

    @Test
    fun `the release source turns a network failure into no network`() = runBlocking {
        val offline = FakeTransport(emptyMap())
        assertEquals(FetchResult.NoNetwork("connection refused"), GitHubReleaseSource(offline).latest())

        val hijacked = FakeTransport(mapOf(LATEST_RELEASE_URL to { redirect("https://evil.example/") }))
        assertEquals(FetchResult.NoNetwork("blocked host evil.example"), GitHubReleaseSource(hijacked).latest())

        val dropped = FakeResponse(200) { throw IOException("reset by peer") }
        val dropping = FakeTransport(mapOf(LATEST_RELEASE_URL to { dropped }))
        assertEquals(FetchResult.NoNetwork("reset by peer"), GitHubReleaseSource(dropping).latest())
        assertTrue(dropped.closed)
    }

    @Test
    fun `an error is reported by its message, else by its type`() {
        assertEquals("timeout", IOException("timeout").reason())
        assertEquals("java.io.IOException", IOException().reason())
    }

    @Test
    fun `the user agent names the installed version`() {
        assertEquals("HyperGesture/1.2.3", userAgent("1.2.3"))
        assertEquals("HyperGesture/unknown", userAgent(null))
    }

    @Test
    fun `progress moves once per whole percent`() {
        val meter = ProgressMeter(total = 1000)
        assertTrue(meter.add(1))
        assertEquals(0, meter.percent)
        assertFalse(meter.add(1))
        assertTrue(meter.add(998))
        assertEquals(100, meter.percent)
    }

    @Test
    fun `progress of an unknown size is reported once as unknown`() {
        val meter = ProgressMeter(total = -1)
        assertTrue(meter.add(10))
        assertNull(meter.percent)
        assertFalse(meter.add(10))
    }

    @Test
    fun `a verified download replaces whatever an earlier one left`() {
        directory.mkdirs()
        File(directory, "old.apk").writeText("stale")
        val (result, reports) = download(serving())
        val file = File(directory, ReleaseParser.APK_NAME)
        assertEquals(DownloadResult.Ok(file, verified = true), result)
        assertArrayEquals(apkBytes, file.readBytes())
        assertEquals(listOf(ReleaseParser.APK_NAME), directory.list()?.toList())
        assertEquals(listOf(32, 65, 98, 100), reports)
    }

    @Test
    fun `an asset without a digest downloads unverified`() {
        val (result, _) = download(serving(), asset(sha256 = null))
        assertEquals(DownloadResult.Ok(File(directory, ReleaseParser.APK_NAME), verified = false), result)
    }

    @Test
    fun `the length header stands in for a size the release left out`() {
        val (_, reports) = download(serving(), asset(size = 0))
        assertEquals(100, reports.last())
        val (_, unknown) = download(serving { FakeResponse(200, apkBytes, contentLength = -1) }, asset(size = 0))
        assertEquals(listOf<Int?>(null), unknown)
    }

    @Test
    fun `a checksum mismatch leaves no apk behind`() {
        val (result, _) = download(serving(), asset(sha256 = "0".repeat(64)))
        assertEquals(DownloadResult.ChecksumMismatch, result)
        assertFalse(File(directory, ReleaseParser.APK_NAME).exists())
    }

    @Test
    fun `an http error or a refused host is reported without a file`() {
        assertEquals(DownloadResult.Failed("HTTP 404"), download(serving { FakeResponse(404) }).first)
        assertEquals(
            DownloadResult.BlockedHost("evil.example"),
            download(serving(), asset(url = "https://evil.example/a.apk")).first,
        )
        assertFalse(File(directory, ReleaseParser.APK_NAME).exists())
    }

    @Test
    fun `a connection dropped halfway deletes the partial file`() {
        val dropping = serving {
            FakeResponse(200, apkBytes) {
                object : InputStream() {
                    private var left = 100_000
                    override fun read(): Int = if (left-- > 0) 1 else throw IOException("reset by peer")
                }
            }
        }
        assertEquals(DownloadResult.Failed("reset by peer"), download(dropping).first)
        assertFalse(File(directory, ReleaseParser.APK_NAME).exists())
    }

    @Test
    fun `storage that cannot hold the directory fails the download`() {
        directory.writeText("a file where the directory belongs")
        val (result, _) = download(serving())
        assertTrue(result is DownloadResult.Failed)
        assertTrue(directory.isFile)
    }

    // Answers one request with a canned response and records the request lines it got.
    private class OneShotServer(private val response: String) : Closeable {
        private val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val url = "http://127.0.0.1:${socket.localPort}"

        @Volatile
        var request: List<String> = emptyList()

        private val worker = thread {
            runCatching {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    request = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    client.getOutputStream().apply {
                        write(response.toByteArray())
                        flush()
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            worker.join()
        }
    }

    @Test
    fun `the url transport sends its agent and accept headers and reads the answer`() {
        val server = OneShotServer(
            "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nx-ratelimit-remaining: 59\r\nConnection: close\r\n\r\nhello",
        )
        val seen = UrlConnectionTransport("HyperGesture/test").open("${server.url}/latest", "a/b").use {
            listOf(it.code, it.contentLength, it.header("x-ratelimit-remaining"), it.body().bufferedReader().readText())
        }
        server.close()
        assertEquals(listOf(200, 5L, "59", "hello"), seen)
        assertEquals("GET /latest HTTP/1.1", server.request.first())
        assertTrue(server.request.toString(), "User-Agent: HyperGesture/test" in server.request)
        assertTrue(server.request.toString(), "Accept: a/b" in server.request)
    }

    @Test
    fun `the url transport hands a redirect back instead of following it`() {
        val server = OneShotServer(
            "HTTP/1.1 302 Found\r\nLocation: /elsewhere\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
        )
        val seen = UrlConnectionTransport("HyperGesture/test").open(server.url, "a/b").use {
            it.code to it.header("Location")
        }
        server.close()
        assertEquals(302 to "/elsewhere", seen)
    }
}
