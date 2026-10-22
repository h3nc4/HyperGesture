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

/** What the home screen leads with, from the switch, the system binding and the live service. */
enum class ServiceStatus {
    OFF,
    STOPPED,
    WORKING,
    ;

    companion object {
        // HyperOS can stop the service while its switch stays on, which the live instance reveals.
        fun of(switchedOn: Boolean, bound: Boolean, running: Boolean): ServiceStatus = when {
            running -> WORKING
            switchedOn || bound -> STOPPED
            else -> OFF
        }
    }
}

/** Reads the `package/class` entries joined by ':', the form AOSP Settings writes and splits. */
fun isServiceListed(enabledServices: String?, packageName: String, className: String): Boolean =
    enabledServices.orEmpty().split(':').any { entry ->
        val pkg = entry.substringBefore('/', missingDelimiterValue = "")
        val cls = entry.substringAfter('/', missingDelimiterValue = "")
        val fullClass = if (cls.startsWith('.')) pkg + cls else cls
        pkg == packageName && fullClass == className
    }
