package com.family.puzzlepop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Calm, modern look: soft lavender-tinted neutrals, one gentle accent (the mascot's grape purple),
// quiet lavender-grey for secondary controls. Dynamic colour is deliberately off so the app looks
// the same on every family device.
// Keep the window backgrounds in res/values*/theme_colors.xml equal to `background` below.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4FC2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9E0FB),
    onPrimaryContainer = Color(0xFF2B1B63),
    secondary = Color(0xFF5F5575),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E0F0),
    onSecondaryContainer = Color(0xFF1F1830),
    tertiary = Color(0xFF1E7F72),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3F0E9),
    onTertiaryContainer = Color(0xFF00382F),
    background = Color(0xFFFAF8FC),
    onBackground = Color(0xFF1D1B22),
    surface = Color(0xFFFAF8FC),
    onSurface = Color(0xFF1D1B22),
    surfaceVariant = Color(0xFFEAE5F0),
    onSurfaceVariant = Color(0xFF4A4552),
    surfaceTint = Color(0xFF6B4FC2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2F9),
    surfaceContainer = Color(0xFFF1EEF5),
    surfaceContainerHigh = Color(0xFFECE8F1),
    surfaceContainerHighest = Color(0xFFE7E3EC),
    outline = Color(0xFF7B7585),
    outlineVariant = Color(0xFFCCC6D5),
    error = Color(0xFFB83A3A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCDBDFB),
    onPrimary = Color(0xFF33216F),
    primaryContainer = Color(0xFF4B3A8C),
    onPrimaryContainer = Color(0xFFEBE2FF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE7DFF8),
    tertiary = Color(0xFF86D7C8),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF1B5A50),
    onTertiaryContainer = Color(0xFFCFF3EC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF3A3641),
    onSurfaceVariant = Color(0xFFCCC5D4),
    surfaceTint = Color(0xFFCDBDFB),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1C1A21),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF958E9E),
    outlineVariant = Color(0xFF494551),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF561E1B),
)

// Rounded everywhere; cards are 16 dp.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Material 3 type scale, with friendlier (heavier) headings and slightly larger body text for readability.
private val Base = Typography()
private val AppTypography = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PuzzlePopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
