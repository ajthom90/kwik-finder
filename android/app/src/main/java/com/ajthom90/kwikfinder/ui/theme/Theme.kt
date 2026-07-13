package com.ajthom90.kwikfinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KwikRed = Color(0xFFE30613)
private val KwikRedDark = Color(0xFFB00010)

private val LightColors = lightColorScheme(
    primary = KwikRed,
    onPrimary = Color.White,
    secondary = KwikRedDark,
)

private val DarkColors = darkColorScheme(
    primary = KwikRed,
    onPrimary = Color.White,
    secondary = KwikRed,
)

@Composable
fun KwikFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
