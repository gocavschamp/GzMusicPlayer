package com.example.litcompose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkMintPrimary,
        onPrimary = AppOnSurface,
        primaryContainer = DarkMintPrimaryContainer,
        onPrimaryContainer = DarkOnSurface,
        secondary = DarkAquaSecondary,
        onSecondary = AppOnSurface,
        secondaryContainer = DarkAquaSecondaryContainer,
        onSecondaryContainer = DarkOnSurface,
        tertiary = DarkPeachTertiary,
        onTertiary = AppOnSurface,
        tertiaryContainer = DarkPeachTertiaryContainer,
        onTertiaryContainer = DarkOnSurface,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurface = DarkOnSurface,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = MintPrimary,
        onPrimary = MintOnPrimary,
        primaryContainer = MintPrimaryContainer,
        onPrimaryContainer = MintOnPrimaryContainer,
        secondary = AquaSecondary,
        onSecondary = AquaOnSecondary,
        secondaryContainer = AquaSecondaryContainer,
        onSecondaryContainer = AquaOnSecondaryContainer,
        tertiary = PeachTertiary,
        onTertiary = PeachOnTertiary,
        tertiaryContainer = PeachTertiaryContainer,
        onTertiaryContainer = PeachOnTertiaryContainer,
        background = AppBackground,
        surface = AppSurface,
        surfaceVariant = AppSurfaceVariant,
        onSurface = AppOnSurface,
        onSurfaceVariant = AppOnSurfaceVariant,
        outline = AppOutline,
    )

@Composable
fun LitComposeTheme(
    content: @Composable () -> Unit,
) {
    // 从 ThemeController 读取深色模式与主题主色，切换后自动重组换肤
    val darkTheme = ThemeController.darkTheme
    val accent = ThemeController.accent
    val colorScheme =
        (if (darkTheme) DarkColorScheme else LightColorScheme).copy(
            primary = if (darkTheme) accent.dark else accent.light,
        )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
