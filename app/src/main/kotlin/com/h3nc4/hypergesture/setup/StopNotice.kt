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


package com.h3nc4.hypergesture.setup

internal enum class StopNoticeAction { POST, CLEAR, KEEP }

/** What the stop notice should do, read fresh at each lifecycle event that could change it. */
internal data class StopNoticeFacts(
    val running: Boolean,
    val switchedOn: Boolean,
    val serviceEverEnabled: Boolean,
    val alreadyPosted: Boolean,
    val notificationsAllowed: Boolean,
)

internal object StopNotice {

    fun decide(facts: StopNoticeFacts): StopNoticeAction = when {
        facts.running -> StopNoticeAction.CLEAR
        !facts.serviceEverEnabled -> StopNoticeAction.KEEP
        // A switch left on is the framework about to bind, or UiAutomation holding it off.
        facts.switchedOn -> StopNoticeAction.KEEP
        // One notice per stop, so a user who dismissed it is not asked again until gestures return.
        facts.alreadyPosted -> StopNoticeAction.KEEP
        !facts.notificationsAllowed -> StopNoticeAction.KEEP
        else -> StopNoticeAction.POST
    }
}
