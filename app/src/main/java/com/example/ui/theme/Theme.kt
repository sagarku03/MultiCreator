package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CyberGlassColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianDeep,
    primaryContainer = CyberViolet.copy(alpha = 0.28f),
    onPrimaryContainer = TextPrimary,
    secondary = CyberViolet,
    onSecondary = TextPrimary,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldGlow,
    onTertiary = ObsidianDeep,
    tertiaryContainer = EmeraldGlow.copy(alpha = 0.18f),
    onTertiaryContainer = EmeraldSoft,
    background = ObsidianDeep,
    onBackground = TextPrimary,
    surface = ObsidianMid,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderMedium,
    outlineVariant = GlassBorderSubtle,
    error = RoseAlert,
    onError = TextPrimary
)

val MultiCreatorShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MultiCreatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberGlassColorScheme,
        typography = MultiCreatorTypography,
        shapes = MultiCreatorShapes,
        content = content
    )
}
