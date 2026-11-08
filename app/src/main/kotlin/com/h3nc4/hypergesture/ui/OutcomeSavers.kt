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
import com.h3nc4.hypergesture.navigation.EnableRequestOutcome
import com.h3nc4.hypergesture.setup.TileAddOutcome

private const val SEPARATOR = ':'

// An absent outcome is saved as an empty string, since a null save would rerun the initial value.
internal fun encodeTileOutcome(outcome: TileAddOutcome?): String = when (outcome) {
    null -> ""
    TileAddOutcome.Added -> "added"
    TileAddOutcome.AlreadyAdded -> "already_added"
    TileAddOutcome.Dismissed -> "dismissed"
    TileAddOutcome.Unsupported -> "unsupported"
    is TileAddOutcome.Refused -> "refused$SEPARATOR${outcome.code}"
    is TileAddOutcome.Failed -> "failed$SEPARATOR${outcome.reason}"
}

internal fun decodeTileOutcome(saved: String): TileAddOutcome? {
    val (kind, detail) = split(saved)
    return when (kind) {
        "added" -> TileAddOutcome.Added
        "already_added" -> TileAddOutcome.AlreadyAdded
        "dismissed" -> TileAddOutcome.Dismissed
        "unsupported" -> TileAddOutcome.Unsupported
        "refused" -> detail?.toIntOrNull()?.let(TileAddOutcome::Refused)
        "failed" -> detail?.let(TileAddOutcome::Failed)
        else -> null
    }
}

internal fun encodeEnableOutcome(outcome: EnableRequestOutcome?): String = when (outcome) {
    null -> ""
    EnableRequestOutcome.Unsupported -> "unsupported"
    is EnableRequestOutcome.LaunchedSettings -> "launched$SEPARATOR${outcome.intentAction}"
    is EnableRequestOutcome.Failed -> "failed$SEPARATOR${outcome.reason}"
}

internal fun decodeEnableOutcome(saved: String): EnableRequestOutcome? {
    val (kind, detail) = split(saved)
    return when (kind) {
        "unsupported" -> EnableRequestOutcome.Unsupported
        "launched" -> detail?.let(EnableRequestOutcome::LaunchedSettings)
        "failed" -> detail?.let(EnableRequestOutcome::Failed)
        else -> null
    }
}

private fun split(saved: String): Pair<String, String?> {
    val at = saved.indexOf(SEPARATOR)
    return if (at < 0) saved to null else saved.substring(0, at) to saved.substring(at + 1)
}

internal val TileOutcomeSaver = Saver<TileAddOutcome?, String>(
    save = { encodeTileOutcome(it) },
    restore = { decodeTileOutcome(it) },
)

internal val EnableOutcomeSaver = Saver<EnableRequestOutcome?, String>(
    save = { encodeEnableOutcome(it) },
    restore = { decodeEnableOutcome(it) },
)
