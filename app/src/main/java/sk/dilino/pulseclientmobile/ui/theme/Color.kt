package sk.dilino.pulseclientmobile.ui.theme

import androidx.compose.ui.graphics.Color

// "Sentry" design palette — mono ink on ink; red only where something is wrong.
object PulseColors {
    val Background = Color(0xFF0D0C0C)
    val Surface = Color(0xFF141312)
    val SurfaceElevated = Color(0xFF1C1B1A)
    val Terminal = Color(0xFF0A0909)

    /** Strong rule under headers (ink @ 30%). */
    val Border = Color(0x4DF3F2F2)
    /** Hairline between rows (ink @ 14%). */
    val BorderSubtle = Color(0x24F3F2F2)
    /** Fainter row divider (ink @ 9%). */
    val Divider = Color(0x17F3F2F2)
    /** Bar / gauge track (ink @ 10%). */
    val Track = Color(0x1AF3F2F2)

    val TextPrimary = Color(0xFFF3F2F2)
    val TextSecondary = Color(0xFF8D8A89)
    val TextTertiary = Color(0xFF605D5D)

    val Accent = Color(0xFFFF563C)
    val AccentPressed = Color(0xFFDD2B0F)
    val AccentOn = Color(0xFF0D0C0C)
    val Warning = Color(0xFFFF9783)
}
