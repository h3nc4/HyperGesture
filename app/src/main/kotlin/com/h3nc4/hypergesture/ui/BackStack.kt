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

package com.h3nc4.hypergesture.ui

import androidx.compose.runtime.saveable.Saver

/** A second tap on a row lands before home leaves the screen, so it must not stack a copy. */
internal fun List<Screen>.opening(screen: Screen): List<Screen> = if (lastOrNull() == screen) this else this + screen

/** A name saved by an older build may no longer exist, and the stack always keeps a screen. */
internal fun backStackOf(names: List<String>): List<Screen> =
    names.mapNotNull { name -> Screen.entries.firstOrNull { it.name == name } }.ifEmpty { listOf(Screen.HOME) }

internal val BackStackSaver = Saver<List<Screen>, ArrayList<String>>(
    save = { stack -> ArrayList(stack.map { it.name }) },
    restore = ::backStackOf,
)
