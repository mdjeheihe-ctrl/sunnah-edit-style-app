package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NoorCutDarkColorScheme = darkColorScheme(
    primary = SunnahGold,
    onPrimary = SunnahOnGold,
    primaryContainer = SunnahGoldContainer,
    onPrimaryContainer = SunnahGold,
    secondary = EmeraldCue,
    onSecondary = StudioObsidian,
    secondaryContainer = EmeraldCueContainer,
    onSecondaryContainer = EmeraldCue,
    tertiary = CrimsonCue,
    onTertiary = TextPrimary,
    tertiaryContainer = CrimsonCueContainer,
    onTertiaryContainer = CrimsonCue,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighlight,
    outline = StudioOutline,
    outlineVariant = StudioOutline.copy(alpha = 0.6f),
    error = CrimsonCue,
    onError = TextPrimary,
    errorContainer = CrimsonCueContainer,
    onErrorContainer = CrimsonCue
)

private val NoorCutShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NoorCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NoorCutDarkColorScheme,
        typography = NoorCutTypography,
        shapes = NoorCutShapes,
        content = content
    )
}
