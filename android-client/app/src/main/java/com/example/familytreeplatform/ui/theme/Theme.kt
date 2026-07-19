package com.example.familytreeplatform.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBFC3FF),
    onPrimary = Color(0xFF252A57),
    primaryContainer = Color(0xFF3A3F6B),
    onPrimaryContainer = Color(0xFFE2E3FF),
    secondary = Color(0xFFE3BD61),
    onSecondary = Color(0xFF3E2E00),
    secondaryContainer = Color(0xFF584400),
    onSecondaryContainer = Color(0xFFFFE8A7),
    tertiary = Color(0xFFFFB36B),
    onTertiary = Color(0xFF4C2500),
    background = Color(0xFF171822),
    onBackground = Color(0xFFE8E6EC),
    surface = Color(0xFF20212D),
    onSurface = Color(0xFFE8E6EC),
    surfaceVariant = Color(0xFF2B2D3B),
    onSurfaceVariant = Color(0xFFC9C6D0),
    outline = Color(0xFF92909B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF434875),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E3F2),
    onPrimaryContainer = Color(0xFF2C315F),
    secondary = Color(0xFF8A650E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E6BC),
    onSecondaryContainer = Color(0xFF322500),
    tertiary = Color(0xFFE66A00),
    onTertiary = Color.White,
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF24242A),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF24242A),
    surfaceVariant = Color(0xFFECE9E2),
    onSurfaceVariant = Color(0xFF4A494F),
    outline = Color(0xFF77767E)
)

private val FamilyRootTypography = androidx.compose.material3.Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun FamilyTreePlatformTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FamilyRootTypography,
        content = content
    )
}
