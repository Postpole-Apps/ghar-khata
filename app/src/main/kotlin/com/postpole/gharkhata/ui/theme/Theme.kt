package com.postpole.gharkhata.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens from design/mockups/styles.css (Ghar Khata v3, deep green + lime).

internal val LightColors = lightColorScheme(
    primary = Color(0xFF006C47),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8EF7C1),
    onPrimaryContainer = Color(0xFF005234),
    inversePrimary = Color(0xFF71DAA6),
    secondary = Color(0xFF566500),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7EE66),
    onSecondaryContainer = Color(0xFF404C00),
    tertiary = Color(0xFF146683),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9FF),
    onTertiaryContainer = Color(0xFF004D65),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF8FBE4),
    onBackground = Color(0xFF191D0F),
    surface = Color(0xFFF8FBE4),
    onSurface = Color(0xFF191D0F),
    surfaceVariant = Color(0xFFDAE7CE),
    onSurfaceVariant = Color(0xFF3F4A38),
    surfaceTint = Color(0xFF006C47),
    surfaceDim = Color(0xFFD9DCC6),
    surfaceBright = Color(0xFFF8FBE4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F6DF),
    surfaceContainer = Color(0xFFEDF0D9),
    surfaceContainerHigh = Color(0xFFE7EAD4),
    surfaceContainerHighest = Color(0xFFE1E5CE),
    inverseSurface = Color(0xFF2E3223),
    inverseOnSurface = Color(0xFFF0F3DC),
    outline = Color(0xFF6F7B67),
    outlineVariant = Color(0xFFBECBB3),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF71DAA6),
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF8EF7C1),
    inversePrimary = Color(0xFF006C47),
    secondary = Color(0xFFBBD14D),
    onSecondary = Color(0xFF2C3400),
    secondaryContainer = Color(0xFF404C00),
    onSecondaryContainer = Color(0xFFD7EE66),
    tertiary = Color(0xFF8CCFF0),
    onTertiary = Color(0xFF003547),
    tertiaryContainer = Color(0xFF004D65),
    onTertiaryContainer = Color(0xFFBFE9FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111508),
    onBackground = Color(0xFFE1E5CE),
    surface = Color(0xFF111508),
    onSurface = Color(0xFFE1E5CE),
    surfaceVariant = Color(0xFF3F4A38),
    onSurfaceVariant = Color(0xFFBECBB3),
    surfaceTint = Color(0xFF71DAA6),
    surfaceDim = Color(0xFF111508),
    surfaceBright = Color(0xFF373B2B),
    surfaceContainerLowest = Color(0xFF0C1004),
    surfaceContainerLow = Color(0xFF191D0F),
    surfaceContainer = Color(0xFF1D2113),
    surfaceContainerHigh = Color(0xFF282C1D),
    surfaceContainerHighest = Color(0xFF323627),
    inverseSurface = Color(0xFFE1E5CE),
    inverseOnSurface = Color(0xFF2E3223),
    outline = Color(0xFF88957F),
    outlineVariant = Color(0xFF3F4A38),
    scrim = Color(0xFF000000),
)

/** Extended roles that are not part of [androidx.compose.material3.ColorScheme]. Saffron is an accent only. */
@Immutable
data class GharColors(
    val lime: Color,
    val limeDim: Color,
    val onLime: Color,
    val limeContainer: Color,
    val onLimeContainer: Color,
    val hero: Color,
    val onHero: Color,
    val heroVariant: Color,
    val onHeroVariant: Color,
    val saffron: Color,
    val onSaffron: Color,
    val saffronContainer: Color,
    val onSaffronContainer: Color,
)

val LightGharColors = GharColors(
    lime = Color(0xFFCDEA2B),
    limeDim = Color(0xFFB7D300),
    onLime = Color(0xFF222900),
    limeContainer = Color(0xFFE2F970),
    onLimeContainer = Color(0xFF2C3400),
    hero = Color(0xFF1E3527),
    onHero = Color(0xFFDCF8E2),
    heroVariant = Color(0xFF344C3D),
    onHeroVariant = Color(0xFFC0DBC6),
    saffron = Color(0xFF944A00),
    onSaffron = Color(0xFFFFFFFF),
    saffronContainer = Color(0xFFFFDCC6),
    onSaffronContainer = Color(0xFF301400),
)

val DarkGharColors = GharColors(
    lime = Color(0xFFCDEA2B),
    limeDim = Color(0xFFB7D300),
    onLime = Color(0xFF222900),
    limeContainer = Color(0xFF404C00),
    onLimeContainer = Color(0xFFD2F031),
    hero = Color(0xFF0D2417),
    onHero = Color(0xFFDCF8E2),
    heroVariant = Color(0xFF1E3527),
    onHeroVariant = Color(0xFFB2CDB9),
    saffron = Color(0xFFFFB784),
    onSaffron = Color(0xFF4F2500),
    saffronContainer = Color(0xFF713700),
    onSaffronContainer = Color(0xFFFFDCC6),
)

val LocalGharColors = staticCompositionLocalOf { LightGharColors }

/** Convenience accessor: `MaterialTheme.gharColors`. */
val MaterialTheme.gharColors: GharColors
    @Composable get() = LocalGharColors.current

val GharShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Component shapes from the design (cards 24, hero block bottom 32, sheets 32 top, pills/buttons full).
 * largeIncreased / extraLargeIncreased are not public in the pinned material3 version, so they live here.
 */
object GharShapesExtra {
    val LargeIncreased = RoundedCornerShape(20.dp)
    val ExtraLargeIncreased = RoundedCornerShape(32.dp)
    val Card = RoundedCornerShape(24.dp)
    val HeroBottom = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    val SheetTop = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val Pill = CircleShape
}

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

val GharTypography = Typography(
    displayLarge = style(57, 64, 400, -0.25),
    displayMedium = style(45, 52, 400),
    displaySmall = style(36, 44, 400),
    headlineLarge = style(32, 40, 400),
    headlineMedium = style(28, 36, 700, -0.3),
    headlineSmall = style(24, 32, 700, -0.2),
    titleLarge = style(22, 28, 700, -0.1),
    titleMedium = style(16, 24, 700, 0.1),
    titleSmall = style(14, 20, 500, 0.1),
    bodyLarge = style(16, 24, 400, 0.2),
    bodyMedium = style(14, 20, 400, 0.2),
    bodySmall = style(12, 16, 400, 0.3),
    labelLarge = style(14, 20, 700, 0.1),
    labelMedium = style(12, 16, 700, 0.4),
    labelSmall = style(11, 16, 500, 0.5),
)

/** Tabular numerals for amounts and counts (`--gk-type-numeral-*`). */
object GharNumerals {
    private fun numeral(size: Int, line: Int, weight: Int, tracking: Double) = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking.sp,
        fontFeatureSettings = "tnum",
    )

    val XL = numeral(56, 60, 800, -2.0)
    val L = numeral(40, 44, 800, -1.2)
    val M = numeral(26, 30, 800, -0.6)
    val S = numeral(18, 24, 700, -0.2)
}

@Composable
fun GharKhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalGharColors provides if (darkTheme) DarkGharColors else LightGharColors) {
        MaterialTheme(colorScheme = colorScheme, typography = GharTypography, shapes = GharShapes, content = content)
    }
}
