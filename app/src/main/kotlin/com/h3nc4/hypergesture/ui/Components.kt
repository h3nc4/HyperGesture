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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.ui.theme.onSuccessContainer
import com.h3nc4.hypergesture.ui.theme.onWarningContainer
import com.h3nc4.hypergesture.ui.theme.successContainer
import com.h3nc4.hypergesture.ui.theme.warningContainer
import kotlin.math.roundToInt

internal fun Modifier.tagged(tag: String?): Modifier = if (tag != null) testTag(tag) else this

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
internal fun FootNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Green for done, amber for something to do, neutral for a fact or a wait. */
internal enum class Tone { GOOD, ATTENTION, NEUTRAL }

@Composable
internal fun StatusChip(label: String, tone: Tone, modifier: Modifier = Modifier) {
    val (background, foreground) = toneColors(tone)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun toneColors(tone: Tone): Pair<Color, Color> = when (tone) {
    Tone.GOOD -> successContainer to onSuccessContainer
    Tone.ATTENTION -> warningContainer to onWarningContainer
    Tone.NEUTRAL ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val transparentItem
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    supporting: String? = null,
    tag: String? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { SupportingText(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.tagged(tag))
        },
        colors = transparentItem,
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

/** A label that opens a screen beside a switch that toggles in place, as AOSP Settings does. */
@Composable
internal fun SplitSwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    rowTag: String,
    switchTag: String,
    onOpen: () -> Unit,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { SupportingText(supporting) },
            colors = transparentItem,
            modifier = Modifier
                .weight(1f)
                .testTag(rowTag)
                .clickable(onClick = onOpen),
        )
        VerticalDivider(modifier = Modifier.height(32.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag(switchTag),
        )
    }
}

@Composable
internal fun NavigationRow(
    title: String,
    supporting: String? = null,
    tag: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { SupportingText(it) } },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = transparentItem,
        modifier = Modifier
            .tagged(tag)
            .clickable(onClick = onClick),
    )
}

/** A row with an optional trailing element and actions laid under its text. */
@Composable
internal fun InfoRow(
    title: String,
    supporting: String? = null,
    supportingTag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    // A large font leaves the title a word per line beside a chip, so it is drawn below the title.
    val stacked = trailing != null && LocalDensity.current.fontScale >= STACKED_FONT_SCALE
    val under = trailing.takeIf { stacked }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (supporting == null && actions == null && under == null) {
            null
        } else {
            {
                Column {
                    under?.let { Box(Modifier.padding(vertical = 4.dp)) { it() } }
                    supporting?.let { SupportingText(it, Modifier.tagged(supportingTag)) }
                    actions?.let {
                        // Wraps onto a second line rather than squeezing a button at a large font.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) { it() }
                    }
                }
            }
        },
        trailingContent = trailing.takeUnless { stacked },
        colors = transparentItem,
    )
}

private const val STACKED_FONT_SCALE = 1.5f

/** Label and value stacked once a large font leaves no room beside each other. */
@Composable
internal fun ValueRow(label: String, value: String, valueTag: String? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.tagged(valueTag),
            )
        },
        colors = transparentItem,
    )
}

// Commits on release only, since every write re-emits the config flow and rebuilds all three
// overlay windows, which visibly stutters on HyperOS.
@Composable
internal fun ThresholdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatter: @Composable (Int) -> String,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (!dragging) local = value }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            Text(
                text = formatter(local.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = local,
            valueRange = range,
            onValueChange = {
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(local)
            },
        )
    }
}

@Composable
internal fun LastProblem(failure: String?) {
    ValueRow(
        label = stringResource(R.string.label_last_problem),
        value = failure ?: stringResource(R.string.diag_none),
    )
}
