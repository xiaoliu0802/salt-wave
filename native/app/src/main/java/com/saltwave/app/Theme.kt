package com.saltwave.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF1B4FE0)
val Ink = Color(0xFF14161A)
val Sub = Color(0xFF6E7480)
val PageBg = Color(0xFFF5F6F8)
val CardBg = Color(0xFFFFFFFF)
val DarkBg = Color(0xFF0B0E14)
val DarkSub = Color(0xFF98A1B2)
val Placeholder = Color(0xFFE4E8F0)

@Composable
fun SaltTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = PageBg,
            onBackground = Ink,
            surface = CardBg,
            onSurface = Ink,
        ),
        content = content,
    )
}
