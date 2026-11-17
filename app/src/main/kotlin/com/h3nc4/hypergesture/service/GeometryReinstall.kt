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


package com.h3nc4.hypergesture.service

/**
 * Runs on the main thread from a rotation or display change, where an uncaught throw kills
 * the process and gets the service flagged as malfunctioning.
 */
internal fun reinstallOnGeometryChange(
    last: ScreenGeometry?,
    read: () -> ScreenGeometry,
    reinstall: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    try {
        if (read() != last) reinstall()
    } catch (t: Throwable) {
        onFailure(t)
    }
}
