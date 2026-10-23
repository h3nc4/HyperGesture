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

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.update.CheckFailure
import com.h3nc4.hypergesture.update.CheckResult
import com.h3nc4.hypergesture.update.DownloadResult
import com.h3nc4.hypergesture.update.InstallOutcome
import com.h3nc4.hypergesture.update.UpdatePhase
import com.h3nc4.hypergesture.update.UpdateState
import java.util.Date

/** The last answer with its time and actions, shown on the Updates screen while the check is on. */
@Composable
internal fun UpdateStatusRow(
    state: UpdateState,
    onCheckNow: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
) {
    val phase = state.phase
    ListItem(
        headlineContent = { Text(stringResource(R.string.update_status)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SupportingText(
                    text = updateMessage(state) ?: stringResource(R.string.update_not_checked),
                    modifier = Modifier.testTag(TestTags.UPDATE_STATUS),
                )
                if (phase is UpdatePhase.Downloading) {
                    val percent = phase.percent
                    if (percent == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                state.lastCheckedMs?.let {
                    SupportingText(
                        stringResource(
                            R.string.update_last_checked,
                            DateUtils.getRelativeTimeSpanString(it).toString(),
                        ),
                    )
                }
                // Wraps onto a second line rather than squeezing a button at a large font.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (phase == UpdatePhase.PermissionNeeded) {
                        FilledTonalButton(
                            onClick = onAllowInstalls,
                            modifier = Modifier.testTag(TestTags.UPDATE_ALLOW_INSTALLS),
                        ) { Text(stringResource(R.string.update_allow_installs)) }
                    } else if (state.offer != null) {
                        FilledTonalButton(
                            onClick = onInstall,
                            enabled = !state.busy,
                            modifier = Modifier.testTag(TestTags.UPDATE_INSTALL),
                        ) { Text(stringResource(R.string.update_download_install)) }
                    }
                    OutlinedButton(
                        onClick = onCheckNow,
                        enabled = !state.busy,
                        modifier = Modifier.testTag(TestTags.UPDATE_CHECK_NOW),
                    ) { Text(stringResource(R.string.update_check_now)) }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun updateMessage(state: UpdateState): String? {
    val installed = state.installedVersion.orEmpty()
    return when (val phase = state.phase) {
        UpdatePhase.Idle -> null
        UpdatePhase.Checking -> stringResource(R.string.update_checking)
        is UpdatePhase.Checked -> when (val result = phase.result) {
            is CheckResult.UpToDate ->
                stringResource(R.string.update_up_to_date, installed, result.latest)
            is CheckResult.Available ->
                stringResource(R.string.update_available, result.tag, installed)
            is CheckResult.NoApkAsset -> stringResource(R.string.update_no_apk, result.tag)
            CheckResult.NothingPublished -> stringResource(R.string.update_nothing_published)
            is CheckResult.UnreadableVersion ->
                stringResource(R.string.update_unreadable_version, result.text)
        }
        is UpdatePhase.CheckFailed -> when (val failure = phase.failure) {
            is CheckFailure.NoNetwork -> stringResource(R.string.update_no_network, failure.reason)
            is CheckFailure.RateLimited -> failure.resetEpochSeconds?.let {
                val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(it * 1000))
                stringResource(R.string.update_rate_limited_until, time)
            } ?: stringResource(R.string.update_rate_limited)
            is CheckFailure.Http -> stringResource(R.string.update_http_error, failure.code)
            is CheckFailure.BadResponse ->
                stringResource(R.string.update_bad_response, failure.reason)
        }
        is UpdatePhase.Downloading -> stringResource(
            R.string.update_downloading,
            state.offer?.tag.orEmpty(),
        )
        UpdatePhase.PermissionNeeded -> stringResource(R.string.update_permission_needed)
        UpdatePhase.Confirming -> stringResource(R.string.update_confirming)
        is UpdatePhase.DownloadFailed -> when (val result = phase.result) {
            DownloadResult.ChecksumMismatch -> stringResource(R.string.update_checksum_mismatch)
            is DownloadResult.BlockedHost ->
                stringResource(R.string.update_blocked_host, result.host)
            is DownloadResult.Failed ->
                stringResource(R.string.update_download_failed, result.reason)
            is DownloadResult.Ok -> null
        }
        is UpdatePhase.InstallNotStarted ->
            stringResource(R.string.update_install_failed, phase.reason)
        is UpdatePhase.InstallEnded -> when (val outcome = phase.outcome) {
            InstallOutcome.Installed -> stringResource(R.string.update_installed)
            InstallOutcome.Declined -> stringResource(R.string.update_declined)
            is InstallOutcome.Failed -> {
                val reason = outcome.message ?: outcome.status.toString()
                if (outcome.signatureConflict) {
                    stringResource(R.string.update_signature_conflict, reason)
                } else {
                    stringResource(R.string.update_install_failed, reason)
                }
            }
        }
    }
}

/** The supporting text of the Updates row on home, short enough for one line. */
@Composable
internal fun updatesSummary(state: UpdateState): String {
    val offer = state.offer
    return when {
        !state.enabled -> stringResource(R.string.feature_off)
        offer != null -> stringResource(R.string.update_summary_available, offer.tag)
        else -> when (val phase = state.phase) {
            UpdatePhase.Checking -> stringResource(R.string.update_summary_checking)
            is UpdatePhase.Checked -> when (val result = phase.result) {
                is CheckResult.NoApkAsset -> stringResource(R.string.update_summary_no_apk, result.tag)
                CheckResult.NothingPublished -> stringResource(R.string.update_summary_nothing_published)
                is CheckResult.UnreadableVersion ->
                    stringResource(R.string.update_summary_unreadable_version)
                is CheckResult.UpToDate, is CheckResult.Available ->
                    stringResource(R.string.update_summary_up_to_date)
            }
            is UpdatePhase.CheckFailed -> when (val failure = phase.failure) {
                is CheckFailure.NoNetwork -> stringResource(R.string.update_summary_no_network)
                is CheckFailure.RateLimited -> stringResource(R.string.update_summary_rate_limited)
                is CheckFailure.Http -> stringResource(R.string.update_summary_http_error, failure.code)
                is CheckFailure.BadResponse -> stringResource(R.string.update_summary_bad_response)
            }
            else -> stringResource(R.string.update_not_checked)
        }
    }
}
