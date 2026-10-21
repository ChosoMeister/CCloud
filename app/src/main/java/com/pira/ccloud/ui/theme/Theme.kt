package com.pira.ccloud.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
import com.pira.ccloud.data.model.FontSettings
import com.pira.ccloud.utils.StorageUtils

// Set of Material typography styles to start with
fun appTypography(fontFamily: FontFamily?): androidx.compose.material3.Typography {
    return androidx.compose.material3.Typography(
        bodyLarge = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily ?: FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2D2D2D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE0E0E0)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EC),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun CCloudTheme(
    themeSettings: ThemeSettings = ThemeSettings(),
    fontSettings: FontSettings = FontSettings(), // Add font settings parameter
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeSettings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    // Load font family based on font settings
    val fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType)
    
    // Keep app colors stable across devices and wallpaper choices. Most importantly,
    // both palettes are semantically complete so screens share readable text/surfaces.
    val accent = if (themeSettings.primaryColor != defaultPrimaryColor) themeSettings.primaryColor
        else if (darkTheme) Purple80 else Purple40
    val colorScheme = if (darkTheme) darkColorScheme(
        primary = accent, secondary = accent, tertiary = Pink80,
        background = Color(0xFF0B0B0D), surface = Color(0xFF141417),
        surfaceVariant = Color(0xFF24242A), surfaceContainer = Color(0xFF1C1C21),
        onPrimary = Color.White, onSecondary = Color.White, onTertiary = Color.White,
        onBackground = Color(0xFFF4F4F5), onSurface = Color(0xFFF4F4F5),
        onSurfaceVariant = Color(0xFFB4B4BB), outline = Color(0xFF777780),
    ) else lightColorScheme(
        primary = accent, secondary = accent, tertiary = Pink40,
        background = Color(0xFFF5F5F8), surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE9E8EF), surfaceContainer = Color(0xFFF0EFF5),
        onPrimary = Color.White, onSecondary = Color.White, onTertiary = Color.White,
        onBackground = Color(0xFF1B1A20), onSurface = Color(0xFF1B1A20),
        onSurfaceVariant = Color(0xFF494650), outline = Color(0xFF77727D),
    )
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(fontFamily),
        content = content
    )
}
