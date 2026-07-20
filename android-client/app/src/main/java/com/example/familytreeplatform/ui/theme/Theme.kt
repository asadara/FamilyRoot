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
    primary = Color(0xFFFFC96B),
    onPrimary = Color(0xFF3D2B00),
    primaryContainer = Color(0xFF573F08),
    onPrimaryContainer = Color(0xFFFFE3A5),
    secondary = Color(0xFFE3BD61),
    onSecondary = Color(0xFF3E2E00),
    secondaryContainer = Color(0xFF594500),
    onSecondaryContainer = Color(0xFFFFE8A7),
    tertiary = Color(0xFFD9B878),
    onTertiary = Color(0xFF3B2F18),
    background = Color(0xFF1B1710),
    onBackground = Color(0xFFF0E7D8),
    surface = Color(0xFF241F17),
    onSurface = Color(0xFFF0E7D8),
    surfaceVariant = Color(0xFF332C21),
    onSurfaceVariant = Color(0xFFD4C7B5),
    outline = Color(0xFF9D8E7A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6B4A18),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE9BE),
    onPrimaryContainer = Color(0xFF281A05),
    secondary = Color(0xFF805B12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7B0),
    onSecondaryContainer = Color(0xFF2B1D00),
    tertiary = Color(0xFF705A2D),
    onTertiary = Color.White,
    background = Color(0xFFFFF8ED),
    onBackground = Color(0xFF2A2116),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF2A2116),
    surfaceVariant = Color(0xFFF3E8D7),
    onSurfaceVariant = Color(0xFF554A3B),
    outline = Color(0xFF837567)
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
