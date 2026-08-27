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

package com.h3nc4.hypergesture.navigation

enum class NavigationMode { GESTURAL, THREE_BUTTON, UNKNOWN }

data class NavigationIntegrationStatus(
    val integrationId: String,
    val available: Boolean,
    val currentMode: NavigationMode,
    val detail: String,
)

sealed interface EnableRequestOutcome {
    /** This device exposes no mechanism a normal APK is permitted to use. */
    data object Unsupported : EnableRequestOutcome

    /** Only opened the screen; the user still has to make the selection. */
    data class LaunchedSettings(val intentAction: String) : EnableRequestOutcome

    data class Failed(val reason: String) : EnableRequestOutcome
}

/** Optional: the gesture service works regardless of whether any integration succeeds. */
interface NavigationIntegration {
    val id: String
    fun isSupported(): Boolean
    fun status(): NavigationIntegrationStatus
    fun requestEnable(): EnableRequestOutcome
}
