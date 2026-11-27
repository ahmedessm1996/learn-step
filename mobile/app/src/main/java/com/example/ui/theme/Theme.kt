package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StepLearnDarkColorScheme = darkColorScheme(
    primary = ForestGreen,
    onPrimary = ForestBg,
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = ForestMint,
    secondary = ForestMint,
    onSecondary = ForestBg,
    secondaryContainer = ForestSurfaceVariant,
    onSecondaryContainer = ForestInk,
    tertiary = ForestInfoInk,
    onTertiary = ForestBg,
    background = ForestBg,
    onBackground = ForestInk,
    surface = ForestSurface,
    onSurface = ForestInk,
    surfaceVariant = ForestSurfaceRaised,
    onSurfaceVariant = ForestMuted,
    outline = ForestLine,
    outlineVariant = ForestLine,
    error = ForestErrorInk,
    onError = ForestBg,
    errorContainer = ForestErrorBg,
    onErrorContainer = ForestErrorInk
)

val StepLearnShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StepLearnTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StepLearnDarkColorScheme,
        typography = StepLearnTypography,
        shapes = StepLearnShapes,
        content = content
    )
}
