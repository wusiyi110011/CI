package com.wsy.ci.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6B35),
    secondary = Color(0xFFB8860B),
    tertiary = Color(0xFF4A6FA5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FBC7F),
    secondary = Color(0xFFDAA520),
    tertiary = Color(0xFF9BB8E0)
)

@Composable
fun CiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
