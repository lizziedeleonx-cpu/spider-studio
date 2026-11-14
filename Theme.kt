package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SpiderDarkColorScheme = darkColorScheme(
    primary = SpiderViolet,
    onPrimary = SpiderBlack,
    primaryContainer = SpiderVioletDeep,
    onPrimaryContainer = SpiderTextPrimary,
    secondary = SpiderVioletSoft,
    onSecondary = SpiderBlack,
    secondaryContainer = SpiderSurfaceHigh,
    onSecondaryContainer = SpiderTextPrimary,
    tertiary = SpiderGuideAccent,
    onTertiary = SpiderBlack,
    background = SpiderBlack,
    onBackground = SpiderTextPrimary,
    surface = SpiderCharcoal,
    onSurface = SpiderTextPrimary,
    surfaceVariant = SpiderSurface,
    onSurfaceVariant = SpiderTextSecondary,
    surfaceContainerLowest = SpiderBlack,
    surfaceContainerLow = SpiderCharcoal,
    surfaceContainer = SpiderSurface,
    surfaceContainerHigh = SpiderSurfaceElevated,
    surfaceContainerHighest = SpiderSurfaceHigh,
    outline = SpiderBorder,
    outlineVariant = SpiderBorderSubtle,
    error = SpiderError,
    onError = SpiderTextPrimary
)

val SpiderShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SpiderStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SpiderDarkColorScheme,
        typography = SpiderTypography,
        shapes = SpiderShapes,
        content = content
    )
}
