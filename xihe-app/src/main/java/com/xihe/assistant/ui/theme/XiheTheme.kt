package com.xihe.assistant.ui.theme

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 羲和智能助手主题
 * 提供现代化的UI设计风格
 */

private val DarkColorScheme = darkColorScheme(
    primary = XiheColors.Primary,
    onPrimary = XiheColors.OnPrimary,
    primaryContainer = XiheColors.PrimaryContainer,
    onPrimaryContainer = XiheColors.OnPrimaryContainer,
    secondary = XiheColors.Secondary,
    onSecondary = XiheColors.OnSecondary,
    secondaryContainer = XiheColors.SecondaryContainer,
    onSecondaryContainer = XiheColors.OnSecondaryContainer,
    tertiary = XiheColors.Tertiary,
    onTertiary = XiheColors.OnTertiary,
    tertiaryContainer = XiheColors.TertiaryContainer,
    onTertiaryContainer = XiheColors.OnTertiaryContainer,
    error = XiheColors.Error,
    onError = XiheColors.OnError,
    errorContainer = XiheColors.ErrorContainer,
    onErrorContainer = XiheColors.OnErrorContainer,
    background = XiheColors.Background,
    onBackground = XiheColors.OnBackground,
    surface = XiheColors.Surface,
    onSurface = XiheColors.OnSurface,
    surfaceVariant = XiheColors.SurfaceVariant,
    onSurfaceVariant = XiheColors.OnSurfaceVariant,
    outline = XiheColors.Outline,
    outlineVariant = XiheColors.OutlineVariant,
    scrim = XiheColors.Scrim,
    inverseSurface = XiheColors.InverseSurface,
    inverseOnSurface = XiheColors.InverseOnSurface,
    inversePrimary = XiheColors.InversePrimary,
    surfaceDim = XiheColors.SurfaceDim,
    surfaceBright = XiheColors.SurfaceBright,
    surfaceContainerLowest = XiheColors.SurfaceContainerLowest,
    surfaceContainerLow = XiheColors.SurfaceContainerLow,
    surfaceContainer = XiheColors.SurfaceContainer,
    surfaceContainerHigh = XiheColors.SurfaceContainerHigh,
    surfaceContainerHighest = XiheColors.SurfaceContainerHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = XiheColors.Primary,
    onPrimary = XiheColors.OnPrimary,
    primaryContainer = XiheColors.PrimaryContainer,
    onPrimaryContainer = XiheColors.OnPrimaryContainer,
    secondary = XiheColors.Secondary,
    onSecondary = XiheColors.OnSecondary,
    secondaryContainer = XiheColors.SecondaryContainer,
    onSecondaryContainer = XiheColors.OnSecondaryContainer,
    tertiary = XiheColors.Tertiary,
    onTertiary = XiheColors.OnTertiary,
    tertiaryContainer = XiheColors.TertiaryContainer,
    onTertiaryContainer = XiheColors.OnTertiaryContainer,
    error = XiheColors.Error,
    onError = XiheColors.OnError,
    errorContainer = XiheColors.ErrorContainer,
    onErrorContainer = XiheColors.OnErrorContainer,
    background = XiheColors.Background,
    onBackground = XiheColors.OnBackground,
    surface = XiheColors.Surface,
    onSurface = XiheColors.OnSurface,
    surfaceVariant = XiheColors.SurfaceVariant,
    onSurfaceVariant = XiheColors.OnSurfaceVariant,
    outline = XiheColors.Outline,
    outlineVariant = XiheColors.OutlineVariant,
    scrim = XiheColors.Scrim,
    inverseSurface = XiheColors.InverseSurface,
    inverseOnSurface = XiheColors.InverseOnSurface,
    inversePrimary = XiheColors.InversePrimary,
    surfaceDim = XiheColors.SurfaceDim,
    surfaceBright = XiheColors.SurfaceBright,
    surfaceContainerLowest = XiheColors.SurfaceContainerLowest,
    surfaceContainerLow = XiheColors.SurfaceContainerLow,
    surfaceContainer = XiheColors.SurfaceContainer,
    surfaceContainerHigh = XiheColors.SurfaceContainerHigh,
    surfaceContainerHighest = XiheColors.SurfaceContainerHighest,
)

@Composable
fun XiheTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XiheTypography,
        content = content
    )
}