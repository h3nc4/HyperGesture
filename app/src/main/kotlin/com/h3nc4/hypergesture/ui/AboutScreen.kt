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

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.h3nc4.hypergesture.R

@Composable
internal fun AboutScreen(model: AppModel) {
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.url_source)
    val licenseUrl = stringResource(R.string.url_license)
    ValueRow(
        stringResource(R.string.about_version),
        model.diagnostics.appVersionName ?: stringResource(R.string.diag_unknown),
    )
    // Plain text until the site is live, so there is nothing to open yet.
    ValueRow(stringResource(R.string.about_website), stringResource(R.string.value_website))
    // A device with no browser throws ActivityNotFoundException, which startFirst absorbs.
    NavigationRow(
        title = stringResource(R.string.about_source),
        supporting = stringResource(R.string.value_source),
    ) { context.startFirst(Intent(Intent.ACTION_VIEW, sourceUrl.toUri())) }
    NavigationRow(
        title = stringResource(R.string.diag_license),
        supporting = stringResource(R.string.value_license),
    ) { context.startFirst(Intent(Intent.ACTION_VIEW, licenseUrl.toUri())) }

    SectionHeader(stringResource(R.string.section_credits))
    InfoRow(
        title = stringResource(R.string.credit_victor),
        supporting = stringResource(R.string.credit_victor_body),
    )
    InfoRow(
        title = stringResource(R.string.credit_cts),
        supporting = stringResource(R.string.credit_cts_body),
    )
}
