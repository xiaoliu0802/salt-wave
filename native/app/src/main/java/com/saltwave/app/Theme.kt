package com.saltwave.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SwColors(
    val page: Color,
    val card: Color,
    val ink: Color,
    val sub: Color,
    val accent: Color,
    val accentSoft: Color,
    val placeholder: Color,
    val field: Color,
    val divider: Color,
    val playerBg: Color,
    val playerInk: Color,
    val playerSub: Color,
)

val AccentPalette = listOf(
    Color(0xFF1B4FE0) to "海盐蓝",
    Color(0xFF12A06A) to "青苔绿",
    Color(0xFFE0661B) to "落日橙",
    Color(0xFF7A4CE0) to "鸢尾紫",
    Color(0xFFD6336C) to "梅子红",
)

val LocalSw = staticCompositionLocalOf<SwColors> { error("SaltTheme not set") }

@Composable
fun sw(): SwColors = LocalSw.current

@Composable
fun currentAccent(): Color = AccentPalette[AppSettings.accentIndex.coerceIn(0, AccentPalette.lastIndex)].first

@Composable
fun SaltTheme(dark: Boolean, accent: Color, content: @Composable () -> Unit) {
    val colors = if (dark) {
        SwColors(
            page = Color(0xFF0B0E14),
            card = Color(0xFF151A23),
            ink = Color(0xFFE9EDF5),
            sub = Color(0xFF98A1B2),
            accent = accent,
            accentSoft = accent.copy(alpha = 0.18f),
            placeholder = Color(0xFF202634),
            field = Color(0xFF1A1F29),
            divider = Color(0xFF232935),
            playerBg = Color(0xFF0B0E14),
            playerInk = Color(0xFFF3F6FB),
            playerSub = Color(0xFF98A1B2),
        )
    } else {
        SwColors(
            page = Color(0xFFF5F6F8),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF14161A),
            sub = Color(0xFF6E7480),
            accent = accent,
            accentSoft = accent.copy(alpha = 0.10f),
            placeholder = Color(0xFFE4E8F0),
            field = Color(0xFFF0F2F6),
            divider = Color(0xFFE8EBF0),
            playerBg = Color(0xFF0B0E14),
            playerInk = Color(0xFFF3F6FB),
            playerSub = Color(0xFF98A1B2),
        )
    }

    CompositionLocalProvider(LocalSw provides colors) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = accent,
                    onPrimary = Color.White,
                    background = colors.page,
                    onBackground = colors.ink,
                    surface = colors.card,
                    onSurface = colors.ink,
                )
            } else {
                lightColorScheme(
                    primary = accent,
                    onPrimary = Color.White,
                    background = colors.page,
                    onBackground = colors.ink,
                    surface = colors.card,
                    onSurface = colors.ink,
                )
            },
            content = content,
        )
    }
}
