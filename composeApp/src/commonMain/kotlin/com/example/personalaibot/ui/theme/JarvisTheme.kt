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
    val Amber   = Color(0xFFFFB300)   // 2026-04-30 — offline / stale banner
    val Green   = Color(0xFF22C55E)   // 2026-04-30 — success indicators

    // ─── Avatar / Always Live Colors ─────────────────────────────────────
    val RobotWhite  = Color(0xFFF0F4F8)    // หุ่นยนต์ตัวหลัก
    val RobotVisor  = Color(0xFF1A1A2E)    // หน้าจอ face visor
    val RobotEye    = Color(0xFF00E5FF)    // ตา (= Cyan)
    val RobotGlow   = Color(0xFF00E5FF)    // ambient glow
    val HeartPink   = Color(0xFFFF69B4)    // love mode
    val AngryRed    = Color(0xFFFF4444)    // angry mode (= Red)
    val SleepBlue   = Color(0xFF4A6FA5)    // sleep mode
    val ExcitedGold = Color(0xFFFFD700)    // excited mode

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
