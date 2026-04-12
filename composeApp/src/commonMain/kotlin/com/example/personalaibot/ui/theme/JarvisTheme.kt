package com.example.personalaibot.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ─── Jarvis Color Palette ─────────────────────────────────────────────────────
object JarvisTheme {
    val Cyan    = Color(0xFF00E5FF)
    val Red     = Color(0xFFFF4444)
    val Dark    = Color(0xFF0A0A14)
    val Surface = Color(0xFF141422)
    val Card    = Color(0xFF1C1C2E)
    val Purple  = Color(0xFF7C4DFF)

    val ColorScheme = darkColorScheme(
        primary          = Cyan,
        secondary        = Purple,
        background       = Dark,
        surface          = Surface,
        surfaceVariant   = Card,
        onPrimary        = Color.Black,
        onBackground     = Color.White,
        onSurface        = Color.White,
        outline          = Color(0xFF303050)
    )
}
