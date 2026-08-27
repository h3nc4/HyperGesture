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

package com.h3nc4.hypergesture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette taken from the launcher icon: slate ground, light blue swipe arc.
// Every role is set explicitly: overriding only a few leaves the rest on Material 3's
// purple baseline, which made the tonal button lavender and the card surfaces pink-grey.

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2C5EA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FA),
    onPrimaryContainer = Color(0xFF0B2A57),
    secondary = Color(0xFF4A5B72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3FA),
    onSecondaryContainer = Color(0xFF16305C),
    tertiary = Color(0xFF2C5EA8),
    onTertiary = Color.White,
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF10151C),
    surface = Color.White,
    onSurface = Color(0xFF10151C),
    surfaceVariant = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFF48525F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5FA),
    surfaceContainer = Color(0xFFECF0F7),
    surfaceContainerHigh = Color(0xFFE6EBF3),
    surfaceContainerHighest = Color(0xFFE0E6EF),
    outline = Color(0xFF79808B),
    outlineVariant = Color(0xFFC9D0DA),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B2A57),
    primaryContainer = Color(0xFF21456F),
    onPrimaryContainer = Color(0xFFD7E3FA),
    secondary = Color(0xFFB9C6DA),
    onSecondary = Color(0xFF23303F),
    secondaryContainer = Color(0xFF2A3F5C),
    onSecondaryContainer = Color(0xFFD7E3FA),
    tertiary = Color(0xFF8AB4F8),
    onTertiary = Color(0xFF0B2A57),
    background = Color(0xFF10151C),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF2A3644),
    onSurfaceVariant = Color(0xFFC0C8D4),
    surfaceContainerLowest = Color(0xFF0B0F14),
    surfaceContainerLow = Color(0xFF151B23),
    surfaceContainer = Color(0xFF1A222C),
    surfaceContainerHigh = Color(0xFF232C38),
    surfaceContainerHighest = Color(0xFF2C3644),
    outline = Color(0xFF8A93A0),
    outlineVariant = Color(0xFF3C4654),
)

val successContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E5136) else Color(0xFFC3EDD4)

val onSuccessContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA8E5BF) else Color(0xFF06331A)

val warningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF5C3D00) else Color(0xFFFFE2A8)

val onWarningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFDFA0) else Color(0xFF3A2600)

// Wallpaper-derived dynamic colour is deliberately not used: it makes the palette
// unpredictable - on a neutral wallpaper everything renders grey and the badges turn muddy.
@Composable
fun HyperGestureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
