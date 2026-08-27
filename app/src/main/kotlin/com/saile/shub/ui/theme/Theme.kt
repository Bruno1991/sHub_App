package com.saile.shub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SHubDarkScheme = darkColorScheme(
    primary = Color(0xFF8ECAE6),
    onPrimary = Color(0xFF003545),
    secondary = Color(0xFFA8DADC),
    surface = Color(0xFF101318),
    surfaceVariant = Color(0xFF20262D),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE1E6EB),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SHubDarkScheme,
        content = content,
    )
}
