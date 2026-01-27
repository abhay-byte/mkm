package com.ivarna.mkm.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Nord Color Palette
val NordPolarNight0 = Color(0xFF2E3440)
val NordPolarNight1 = Color(0xFF3B4252)
val NordPolarNight2 = Color(0xFF434C5E)
val NordPolarNight3 = Color(0xFF4C566A)

val NordSnowStorm0 = Color(0xFFD8DEE9)
val NordSnowStorm1 = Color(0xFFE5E9F0)
val NordSnowStorm2 = Color(0xFFECEFF4)

val NordFrost0 = Color(0xFF8FBCBB)
val NordFrost1 = Color(0xFF88C0D0)
val NordFrost2 = Color(0xFF81A1C1)
val NordFrost3 = Color(0xFF5E81AC)

val NordAuroraRed = Color(0xFFBF616A)
val NordAuroraOrange = Color(0xFFD08770)
val NordAuroraYellow = Color(0xFFEBCB8B)
val NordAuroraGreen = Color(0xFFA3BE8C)
val NordAuroraPurple = Color(0xFFB48EAD)

// Dracula Palette
val DraculaBg = Color(0xFF282A36)
val DraculaFg = Color(0xFFF8F8F2)
val DraculaCurrentLine = Color(0xFF44475A)
val DraculaPurple = Color(0xFFBD93F9)
val DraculaPink = Color(0xFFFF79C6)
val DraculaGreen = Color(0xFF50FA7B)
val DraculaRed = Color(0xFFFF5555)

// Monokai Palette
val MonokaiBg = Color(0xFF272822)
val MonokaiFg = Color(0xFFF8F8F2)
val MonokaiPink = Color(0xFFF92672)
val MonokaiGreen = Color(0xFFA6E22E)
val MonokaiBlue = Color(0xFF66D9EF)
val MonokaiPurple = Color(0xFFAE81FF)
val MonokaiYellow = Color(0xFFE6DB74)

// Gruvbox Palette
val GruvboxBg = Color(0xFF282828)
val GruvboxFg = Color(0xFFEBDBB2)
val GruvboxBgSoft = Color(0xFF32302F)
val GruvboxGreen = Color(0xFFB8BB26)
val GruvboxYellow = Color(0xFFFABD2F)
val GruvboxRed = Color(0xFFFB4934)
val GruvboxAqua = Color(0xFF8EC07C)
val GruvboxOrange = Color(0xFFFE8019)

// Solarized Dark Palette
val SolarizedBase03 = Color(0xFF002B36)
val SolarizedBase02 = Color(0xFF073642)
val SolarizedBase01 = Color(0xFF586E75)
val SolarizedBase0 = Color(0xFF839496)
val SolarizedBlue = Color(0xFF268BD2)
val SolarizedCyan = Color(0xFF2AA198)
val SolarizedMagenta = Color(0xFFD33682)
val SolarizedGreen = Color(0xFF859900)

// Synthwave Palette
val SynthwaveBg = Color(0xFF262335)
val SynthwaveFg = Color(0xFFFFFFFF)
val SynthwaveSurface = Color(0xFF342E48)
val SynthwavePink = Color(0xFFFF2A6D)
val SynthwaveCyan = Color(0xFF05D9E8)
val SynthwaveYellow = Color(0xFFFFD166)
val SynthwavePurple = Color(0xFF7F5AF0)

// Gruvbox Light Palette
val GruvboxLightBg = Color(0xFFFBF1C7)
val GruvboxLightFg = Color(0xFF3C3836)
val GruvboxLightBgSoft = Color(0xFFEBDBB2)

// Solarized Light Palette
val SolarizedBase3 = Color(0xFFFDF6E3)
val SolarizedBase2 = Color(0xFFEEE8D5)
val SolarizedBase00 = Color(0xFF657B83)
val SolarizedBase01Light = Color(0xFF586E75) // For content on light bg

// One Light Palette
val OneLightBg = Color(0xFFFAFAFA)
val OneLightFg = Color(0xFF383A42)
val OneLightSurface = Color(0xFFF0F0F1)
val OneLightBlue = Color(0xFF4078F2)
val OneLightCyan = Color(0xFF0184BC)
val OneLightGreen = Color(0xFF50A14F)
val OneLightMagenta = Color(0xFFA626A4)
val OneLightRed = Color(0xFFE45649)
val OneLightYellow = Color(0xFF986801)


// Semantic Colors for MKM
object MkmColors {
    // Swap status indicators
    val SwapActive = Color(0xFF4CAF50)      // Green
    val SwapInactive = Color(0xFF9E9E9E)    // Gray
    val SwapError = Color(0xFFF44336)       // Red
    val SwapWarning = Color(0xFFFF9800)     // Orange

    // Memory indicators
    val MemoryLow = Color(0xFF4CAF50)       // Green (plenty)
    val MemoryMedium = Color(0xFFFF9800)    // Orange (caution)
    val MemoryHigh = Color(0xFFF44336)      // Red (critical)
}
