package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CleanMinimalismColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = OnPrimaryBlueContainer,
    secondary = EmeraldGain,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldDark,
    tertiary = GoldHero,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = GoldHero,
    error = CrimsonLoss,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderDark,
    outlineVariant = CardBorderSubtle
)

@Composable
fun EdgeTraderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CleanMinimalismColorScheme,
        typography = Typography,
        content = content
    )
}

// Kept for backward compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    EdgeTraderTheme(content = content)
}


