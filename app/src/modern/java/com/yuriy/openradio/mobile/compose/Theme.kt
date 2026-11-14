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
 * Libre Radio palette: the same as the Solo radio app (Flutter): Material 3 "tonal spot" dark scheme from
 * the amber seed #F0A848, with the amber itself as primary, black on it, and a near-black background.
 * The same on every device (no wallpaper colors).
 */
val Amber = Color(0xFFF0A848)

/**
 * Placeholder behind a missing cover, as in the radio app.
 */
val CoverPlaceholder = Color(0xFF221E18)

private val LibreRadioDark = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF653E00),
    onPrimaryContainer = Color(0xFFFFDDB8),
    inversePrimary = Color(0xFF825513),
    secondary = Color(0xFFDFC2A2),
    onSecondary = Color(0xFF3F2D17),
    secondaryContainer = Color(0xFF57432B),
    onSecondaryContainer = Color(0xFFFCDDBD),
    tertiary = Color(0xFFBBCD9E),
    onTertiary = Color(0xFF273513),
    tertiaryContainer = Color(0xFF3D4C28),
    onTertiaryContainer = Color(0xFFD7E9B8),
    background = Color(0xFF0E0D0B),
    onBackground = Color(0xFFEEE0D4),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFEEE0D4),
    surfaceVariant = Color(0xFF504539),
    onSurfaceVariant = Color(0xFFD4C4B5),
    surfaceTint = Amber,
    inverseSurface = Color(0xFFEEE0D4),
    inverseOnSurface = Color(0xFF372F27),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9C8E80),
    outlineVariant = Color(0xFF504539),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF403830),
    surfaceDim = Color(0xFF18120C),
    surfaceContainerLowest = Color(0xFF130D07),
    surfaceContainerLow = Color(0xFF211A13),
    surfaceContainer = Color(0xFF251E17),
    surfaceContainerHigh = Color(0xFF302921),
    surfaceContainerHighest = Color(0xFF3B332B),
)

/**
 * Dark Material 3 theme with the Libre Radio palette.
 */
@Composable
fun LibreRadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LibreRadioDark, content = content)
}
