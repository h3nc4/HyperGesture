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

import com.h3nc4.hypergesture.gesture.TouchSample

/**
 * Re-injects a touch that an edge strip consumed but did not turn into a gesture. Strips
 * must consume `ACTION_DOWN` to see whether a swipe develops, so without replay every
 * ordinary tap near an edge is silently eaten.
 */
internal fun interface TouchReplayer {

    /** [samples] are display coordinates, oldest first. */
    fun replay(samples: List<TouchSample>, onDone: () -> Unit): Boolean
}
