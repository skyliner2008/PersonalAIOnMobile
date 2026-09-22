package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.ui.graphics.Color
import com.skyliner2008.jarvis.ui.theme.JarvisTheme

/**
 * AvatarEmotionColors — Centralized emotion→color mapping
 *
 * ลดการซ้ำซ้อนของ when(emotion) blocks ที่กระจายอยู่ใน
 * StatusPill, AudioVisualizerRing (primary + secondary), และ auraColor
 *
 * ใช้ color palette แบบ Cyberpunk / Neon Holographic ที่สอดคล้องกับ JarvisTheme
 */
object AvatarEmotionColors {

    /**
     * Primary accent color สำหรับ UI elements: Visualizer ring, aura glow, status pill
     */
    fun primary(emotion: AvatarEmotion): Color = when (emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF00F0FF) // Neon Aqua Cyan
        AvatarEmotion.SPEAKING -> Color(0xFF00E676)  // Electric Emerald
        AvatarEmotion.THINKING -> Color(0xFFB388FF)  // Cyber Violet
        AvatarEmotion.HAPPY    -> Color(0xFF00E5FF)  // Sky Turquoise
        AvatarEmotion.EXCITED  -> Color(0xFFFFD700)  // Solar Amber Gold
        AvatarEmotion.LOVE     -> Color(0xFFFF4081)  // Hot Neon Pink (= JarvisTheme.HeartPink)
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> Color(0xFFFF1744) // Alert Flame Red
        AvatarEmotion.SAD      -> Color(0xFF80D8FF)  // Ice Slate Blue
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB)  // Calm Lavender
        AvatarEmotion.IDLE     -> Color(0xFF00F0FF)  // Signature Jarvis Cyan
        AvatarEmotion.WINK     -> Color(0xFF00E5FF)  // Sky Turquoise
        AvatarEmotion.CONFUSED -> Color(0xFFFFAB00)  // Amber
        AvatarEmotion.POUT     -> Color(0xFFFF4081)  // Hot Neon Pink
        AvatarEmotion.DIZZY    -> Color(0xFFE040FB)  // Magenta Violet
        AvatarEmotion.SURPRISED -> Color(0xFFE0F7FA) // Electric White-Cyan
        AvatarEmotion.BORED    -> Color(0xFF90A4AE)  // Slate Gray
        AvatarEmotion.DEAD     -> Color(0xFF00E5FF)  // Neon Cyan
        AvatarEmotion.LAUGHING -> Color(0xFF00F0FF)  // Neon Aqua Cyan
        AvatarEmotion.MUSIC    -> Color(0xFFFFD700)  // Gold Amber (Headphones accent)
        AvatarEmotion.VR_MODE  -> Color(0xFFB388FF)  // Cyber Violet (VR Visor)
        AvatarEmotion.DIVING   -> Color(0xFF00B0FF)  // Oceanic Blue
        AvatarEmotion.EVIL     -> Color(0xFFFF1744)  // Devil Red
        AvatarEmotion.FOCUSED  -> Color(0xFF00F0FF)  // Laser Grid Cyan
        AvatarEmotion.SHY      -> Color(0xFFFF80AB)  // Blush Pink
        AvatarEmotion.DISGUSTED -> Color(0xFF78909C) // Muted Slate
        AvatarEmotion.CAMERA_MODE -> Color(0xFFFFAB00) // Camera Amber
        AvatarEmotion.EATING   -> Color(0xFFFFB300)  // Golden Bun Amber
        AvatarEmotion.DRINKING -> Color(0xFFFFD700)  // Frothy Beer Gold
        // ─── 30 Additional Moodset ───
        AvatarEmotion.PUZZLED    -> Color(0xFF00F5FF) // Wavy Confused Cyan
        AvatarEmotion.SICK       -> Color(0xFF76FF03) // Toxic Neon Green
        AvatarEmotion.RICH       -> Color(0xFFFFD700) // Gold Bullion
        AvatarEmotion.CRYING     -> Color(0xFF448AFF) // Tear Blue
        AvatarEmotion.READING    -> Color(0xFFB388FF) // Calm Violet
        AvatarEmotion.GAMING     -> Color(0xFF00E5FF) // Gaming Neon Cyan
        AvatarEmotion.TRAVELING  -> Color(0xFF00E676) // Explorer Green
        AvatarEmotion.WORKING    -> Color(0xFF90CAF9) // Productive Blue
        AvatarEmotion.COLD       -> Color(0xFFE0F7FA) // Frost White-Cyan
        AvatarEmotion.HOT        -> Color(0xFFFF5722) // Burning Orange
        AvatarEmotion.DETECTIVE  -> Color(0xFFFFAB00) // Mystery Amber
        AvatarEmotion.COOKING    -> Color(0xFFFF9800) // Warm Kitchen Orange
        AvatarEmotion.ART_MODE   -> Color(0xFFE040FB) // Creative Magenta
        AvatarEmotion.SPACE      -> Color(0xFF7C4DFF) // Cosmic Violet
        AvatarEmotion.PARTY      -> Color(0xFFFF4081) // Party Pink
        AvatarEmotion.DREAMING   -> Color(0xFF7986CB) // Dreamy Lavender
        AvatarEmotion.EXHAUSTED  -> Color(0xFF78909C) // Tired Gray
        AvatarEmotion.ELECTRIC   -> Color(0xFF00F5FF) // Lightning Cyan
        AvatarEmotion.SNEAKY     -> Color(0xFF455A64) // Shadow Dark
        AvatarEmotion.ROMANTIC   -> Color(0xFFFF4081) // Romantic Rose Pink
        AvatarEmotion.HERO       -> Color(0xFFFF1744) // Hero Red
        AvatarEmotion.GLITCHED   -> Color(0xFFFF1744) // Glitch Red
        AvatarEmotion.MAGIC      -> Color(0xFFAA00FF) // Wizard Purple
        AvatarEmotion.SPORTY     -> Color(0xFFFF6D00) // Athletic Orange
        AvatarEmotion.SCIENTIST  -> Color(0xFF00E676) // Lab Green
        AvatarEmotion.SCARED     -> Color(0xFFCFD8DC) // Pale Fear White
        AvatarEmotion.WARRIOR    -> Color(0xFFFF3D00) // Battle Flame
        AvatarEmotion.LOW_BATTERY -> Color(0xFFFF1744) // Critical Red
    }

    /**
     * Secondary/accent color สำหรับ gradient contrast: Visualizer ring bars, dual-tone effects
     */
    fun secondary(emotion: AvatarEmotion): Color = when (emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF00B0FF)  // Deep Aqua
        AvatarEmotion.SPEAKING -> Color(0xFF69F0AE)   // Mint Neon
        AvatarEmotion.THINKING -> Color(0xFF7C4DFF)   // Deep Violet
        AvatarEmotion.HAPPY    -> Color(0xFF1DE9B6)   // Teal Emerald
        AvatarEmotion.EXCITED  -> Color(0xFFFF9100)   // Deep Amber
        AvatarEmotion.LOVE     -> Color(0xFFFF80AB)   // Soft Rose
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> Color(0xFFFF5252) // Coral Crimson
        AvatarEmotion.SAD      -> Color(0xFF448AFF)   // Cool Indigo
        AvatarEmotion.SLEEPING -> Color(0xFF3F51B5)   // Midnight Blue
        AvatarEmotion.IDLE     -> Color(0xFF7C4DFF)   // Purple Cyber Accent
        AvatarEmotion.WINK     -> Color(0xFF1DE9B6)   // Teal Emerald
        AvatarEmotion.CONFUSED -> Color(0xFFFF9100)   // Deep Amber
        AvatarEmotion.POUT     -> Color(0xFFFF80AB)   // Soft Rose
        AvatarEmotion.DIZZY    -> Color(0xFFBA68C8)   // Violet Spiral
        AvatarEmotion.SURPRISED -> Color(0xFF80DEEA)  // Pale Cyan Accent
        AvatarEmotion.BORED    -> Color(0xFF546E7A)   // Deep Slate Gray
        AvatarEmotion.DEAD     -> Color(0xFF004D66)   // Deep Dark Cyan
        AvatarEmotion.LAUGHING -> Color(0xFF1DE9B6)   // Teal Emerald
        AvatarEmotion.MUSIC    -> Color(0xFF00E5FF)   // Cyan Audio
        AvatarEmotion.VR_MODE  -> Color(0xFF7C4DFF)   // Deep Violet
        AvatarEmotion.DIVING   -> Color(0xFF00E5FF)   // Cyan Water
        AvatarEmotion.EVIL     -> Color(0xFF880E4F)   // Dark Crimson
        AvatarEmotion.FOCUSED  -> Color(0xFF00B0FF)   // Deep Laser Blue
        AvatarEmotion.SHY      -> Color(0xFFFF4081)   // Deep Pink
        AvatarEmotion.DISGUSTED -> Color(0xFF455A64)  // Dark Slate
        AvatarEmotion.CAMERA_MODE -> Color(0xFFFF6D00) // Deep Orange
        AvatarEmotion.EATING   -> Color(0xFFFF5722)   // Warm Red-Orange
        AvatarEmotion.DRINKING -> Color(0xFFFF9100)   // Deep Amber
        // ─── 30 Additional Moodset ───
        AvatarEmotion.PUZZLED    -> Color(0xFF004D6B) // Dark Cyan Shadow
        AvatarEmotion.SICK       -> Color(0xFF64DD17) // Toxic Green Accent
        AvatarEmotion.RICH       -> Color(0xFFFFAB00) // Deep Amber Gold
        AvatarEmotion.CRYING     -> Color(0xFF00B0FF) // Deep Tear Blue
        AvatarEmotion.READING    -> Color(0xFF7C4DFF) // Deep Violet
        AvatarEmotion.GAMING     -> Color(0xFF00B0FF) // Deep Aqua Cyan
        AvatarEmotion.TRAVELING  -> Color(0xFF00BFA5) // Teal Green
        AvatarEmotion.WORKING    -> Color(0xFF42A5F5) // Deep Slate Blue
        AvatarEmotion.COLD       -> Color(0xFF80DEEA) // Ice Blue Accent
        AvatarEmotion.HOT        -> Color(0xFFFF3D00) // Deep Red Orange
        AvatarEmotion.DETECTIVE  -> Color(0xFFFF8F00) // Dark Amber
        AvatarEmotion.COOKING    -> Color(0xFFFF6D00) // Deep Warm Kitchen Orange
        AvatarEmotion.ART_MODE   -> Color(0xFFAA00FF) // Deep Magenta
        AvatarEmotion.SPACE      -> Color(0xFF311B92) // Deep Cosmic Navy
        AvatarEmotion.PARTY      -> Color(0xFFFF80AB) // Party Soft Pink
        AvatarEmotion.DREAMING   -> Color(0xFF3F51B5) // Midnight Blue
        AvatarEmotion.EXHAUSTED  -> Color(0xFF455A64) // Dark Tired Slate
        AvatarEmotion.ELECTRIC   -> Color(0xFF00B0FF) // Electric Blue Accent
        AvatarEmotion.SNEAKY     -> Color(0xFF263238) // Shadow Black Accent
        AvatarEmotion.ROMANTIC   -> Color(0xFFFF80AB) // Rose Pink Accent
        AvatarEmotion.HERO       -> Color(0xFFD50000) // Deep Hero Red
        AvatarEmotion.GLITCHED   -> Color(0xFF00E5FF) // Glitch Cyan Contrast
        AvatarEmotion.MAGIC      -> Color(0xFF7C4DFF) // Magic Violet Accent
        AvatarEmotion.SPORTY     -> Color(0xFFFF3D00) // Athletic Red Orange
        AvatarEmotion.SCIENTIST  -> Color(0xFF00BFA5) // Lab Teal
        AvatarEmotion.SCARED     -> Color(0xFF90A4AE) // Ghost Gray Accent
        AvatarEmotion.WARRIOR    -> Color(0xFFBF360C) // Deep Battle Flame
        AvatarEmotion.LOW_BATTERY -> Color(0xFFB71C1C) // Dark Critical Red
    }

    /**
     * Status pill border/text color — slightly different mapping than primary
     * (e.g., LOVE uses HeartPink, SAD uses lighter blue)
     */
    fun statusPill(emotion: AvatarEmotion): Color = when (emotion) {
        AvatarEmotion.LISTENING -> JarvisTheme.Cyan
        AvatarEmotion.SPEAKING -> JarvisTheme.Green
        AvatarEmotion.THINKING -> Color(0xFFB388FF)
        AvatarEmotion.HAPPY    -> Color(0xFF00E5FF)
        AvatarEmotion.EXCITED  -> Color(0xFFFFD700)
        AvatarEmotion.LOVE     -> JarvisTheme.HeartPink
        AvatarEmotion.ANGRY, AvatarEmotion.ENRAGED -> JarvisTheme.Red
        AvatarEmotion.SAD      -> Color(0xFF80D8FF)
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB)
        AvatarEmotion.IDLE     -> JarvisTheme.Cyan
        AvatarEmotion.WINK     -> Color(0xFF00E5FF)
        AvatarEmotion.CONFUSED -> Color(0xFFFFB300)
        AvatarEmotion.POUT     -> Color(0xFFFF4081)
        AvatarEmotion.DIZZY    -> Color(0xFFCE93D8)
        AvatarEmotion.SURPRISED -> Color(0xFFE0F7FA)
        AvatarEmotion.BORED    -> Color(0xFF90A4AE)
        AvatarEmotion.DEAD     -> Color(0xFF00E5FF)
        AvatarEmotion.LAUGHING -> Color(0xFF00F0FF)
        AvatarEmotion.MUSIC    -> Color(0xFFFFD700)
        AvatarEmotion.VR_MODE  -> Color(0xFFB388FF)
        AvatarEmotion.DIVING   -> Color(0xFF00B0FF)
        AvatarEmotion.EVIL     -> JarvisTheme.Red
        AvatarEmotion.FOCUSED  -> JarvisTheme.Cyan
        AvatarEmotion.SHY      -> Color(0xFFFF80AB)
        AvatarEmotion.DISGUSTED -> Color(0xFF90A4AE)
        AvatarEmotion.CAMERA_MODE -> Color(0xFFFFAB00)
        AvatarEmotion.EATING   -> Color(0xFFFFB300)
        AvatarEmotion.DRINKING -> Color(0xFFFFD700)
        // ─── 30 Additional Moodset ───
        AvatarEmotion.PUZZLED    -> Color(0xFF00F5FF)
        AvatarEmotion.SICK       -> Color(0xFF76FF03)
        AvatarEmotion.RICH       -> Color(0xFFFFD700)
        AvatarEmotion.CRYING     -> Color(0xFF448AFF)
        AvatarEmotion.READING    -> Color(0xFFB388FF)
        AvatarEmotion.GAMING     -> Color(0xFF00E5FF)
        AvatarEmotion.TRAVELING  -> Color(0xFF00E676)
        AvatarEmotion.WORKING    -> Color(0xFF90CAF9)
        AvatarEmotion.COLD       -> Color(0xFFE0F7FA)
        AvatarEmotion.HOT        -> Color(0xFFFF5722)
        AvatarEmotion.DETECTIVE  -> Color(0xFFFFAB00)
        AvatarEmotion.COOKING    -> Color(0xFFFF9800)
        AvatarEmotion.ART_MODE   -> Color(0xFFE040FB)
        AvatarEmotion.SPACE      -> Color(0xFF7C4DFF)
        AvatarEmotion.PARTY      -> Color(0xFFFF4081)
        AvatarEmotion.DREAMING   -> Color(0xFF7986CB)
        AvatarEmotion.EXHAUSTED  -> Color(0xFF78909C)
        AvatarEmotion.ELECTRIC   -> Color(0xFF00F5FF)
        AvatarEmotion.SNEAKY     -> Color(0xFF78909C)
        AvatarEmotion.ROMANTIC   -> Color(0xFFFF4081)
        AvatarEmotion.HERO       -> Color(0xFFFF1744)
        AvatarEmotion.GLITCHED   -> Color(0xFFFF1744)
        AvatarEmotion.MAGIC      -> Color(0xFFAA00FF)
        AvatarEmotion.SPORTY     -> Color(0xFFFF6D00)
        AvatarEmotion.SCIENTIST  -> Color(0xFF00E676)
        AvatarEmotion.SCARED     -> Color(0xFFCFD8DC)
        AvatarEmotion.WARRIOR    -> Color(0xFFFF3D00)
        AvatarEmotion.LOW_BATTERY -> Color(0xFFFF1744)
    }
}
