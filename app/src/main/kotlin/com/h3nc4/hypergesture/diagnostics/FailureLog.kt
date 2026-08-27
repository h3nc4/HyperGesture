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
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Logcat cannot be trusted on the target ROM: its global `log.tag` threshold drops debug
 * and info logs, its `adb shell` lacks `WRITE_SECURE_SETTINGS`, and DropBox stayed empty —
 * a crash can be invisible from a host machine. So the stack trace goes to app storage:
 * `adb shell run-as com.h3nc4.hypergesture cat files/last_failure.txt` (debug builds).
 */
object FailureLog {

    private const val TAG = "HyperGesture"
    private const val FILE_NAME = "last_failure.txt"
    private const val MAX_BYTES = 16 * 1024

    fun record(context: Context, label: String, throwable: Throwable) {
        Log.e(TAG, "FAILURE [$label]", throwable)
        val text = buildString {
            appendLine("label: $label")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("---")
            append(stackTraceOf(throwable))
        }
        runCatching {
            file(context).writeText(text.take(MAX_BYTES))
        }.onFailure { Log.e(TAG, "Could not persist the failure record", it) }
    }

    fun read(context: Context): String? = runCatching {
        val file = file(context)
        if (file.exists()) file.readText().ifBlank { null } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString()
    }
}
