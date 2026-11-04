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


package com.h3nc4.hypergesture.diagnostics

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Every stuck stream the overlay released, oldest first. App-specific external storage, so
 * `adb pull` reads it on a release build, which `run-as` cannot:
 * `/sdcard/Android/data/com.h3nc4.hypergesture/files/stuck_streams.txt`
 */
object StuckStreamLog {

    /** Starts every report, and never appears inside one. */
    const val MARKER = "STREAM_STUCK"

    private const val TAG = "HyperGesture"
    private const val FILE_NAME = "stuck_streams.txt"
    private const val MAX_CHARS = 256 * 1024

    fun append(context: Context, report: String) {
        runCatching {
            val file = file(context)
            if (file.length() > MAX_CHARS) file.writeText(trimmed(file.readText(), MAX_CHARS / 2))
            file.appendText("${report.trim()}\n\n")
        }.onFailure { Log.e(TAG, "Could not persist the stuck stream", it) }
    }

    fun latest(context: Context): String? = runCatching {
        val file = file(context)
        if (file.exists()) latestOf(file.readText()) else null
    }.getOrNull()

    internal fun latestOf(text: String): String? {
        val at = text.lastIndexOf(MARKER)
        return if (at < 0) null else text.substring(at).trim().ifBlank { null }
    }

    /** The newest reports within [maxChars], none of them cut in half. */
    internal fun trimmed(text: String, maxChars: Int): String {
        val tail = text.takeLast(maxChars)
        if (tail.length == text.length || tail.startsWith(MARKER)) return tail
        val at = tail.indexOf(MARKER)
        return if (at < 0) "" else tail.substring(at)
    }

    private fun file(context: Context) =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
}
