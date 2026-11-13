/*
 * Copyright 2026 Libre Radio contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuriy.openradio.mobile.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Libre Radio palette: a warm "tube amp" coral on warm near-black, with a teal accent.
 * The same on every device (no wallpaper colors), so the app keeps its own look.
 */
private val LibreRadioDark = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5C1A04),
    primaryContainer = Color(0xFF7E2D12),
    onPrimaryContainer = Color(0xFFFFDBD0),
    inversePrimary = Color(0xFFA43F1E),
    secondary = Color(0xFFE7BDB1),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFF8DD3C7),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFA9F0E3),
    background = Color(0xFF161210),
    onBackground = Color(0xFFEDE0DC),
    surface = Color(0xFF161210),
    onSurface = Color(0xFFEDE0DC),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceTint = Color(0xFFFFB59D),
    inverseSurface = Color(0xFFEDE0DC),
    inverseOnSurface = Color(0xFF362F2C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3D3735),
    surfaceDim = Color(0xFF161210),
    surfaceContainerLowest = Color(0xFF100D0B),
    surfaceContainerLow = Color(0xFF1E1A18),
    surfaceContainer = Color(0xFF231E1C),
    surfaceContainerHigh = Color(0xFF2D2826),
    surfaceContainerHighest = Color(0xFF383330),
)

/**
 * Dark Material 3 theme with the Libre Radio palette.
 */
@Composable
fun LibreRadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LibreRadioDark, content = content)
}
