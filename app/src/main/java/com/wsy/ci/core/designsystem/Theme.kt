package com.wsy.ci.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalCiDarkTheme = staticCompositionLocalOf { false }

@Composable
fun CiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) CiDarkColorScheme else CiLightColorScheme
    val ciColors = remember(darkTheme) { CiColors.from(colorScheme, isDark = darkTheme) }

    CompositionLocalProvider(
        LocalCiColors provides ciColors,
        LocalCiDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CiTypography,
            shapes = CiShapeScale,
            content = content,
        )
    }
}

/** 语义 token 的取用入口：`CiTheme.colors.income`。 */
object CiTheme {
    val colors: CiColors
        @Composable get() = LocalCiColors.current

    val isDark: Boolean
        @Composable get() = LocalCiDarkTheme.current
}
