package com.ivarna.mkm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ivarna.mkm.ui.viewmodel.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val AmoledColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black
)

private val NordColorScheme = darkColorScheme(
    primary = NordFrost2,
    onPrimary = NordPolarNight0,
    primaryContainer = NordFrost3,
    onPrimaryContainer = NordSnowStorm2,
    secondary = NordFrost0,
    onSecondary = NordPolarNight0,
    secondaryContainer = NordFrost1,
    onSecondaryContainer = NordPolarNight0,
    tertiary = NordAuroraPurple,
    onTertiary = NordPolarNight0,
    tertiaryContainer = NordAuroraRed,
    onTertiaryContainer = NordSnowStorm0,
    background = NordPolarNight0,
    onBackground = NordSnowStorm2,
    surface = NordPolarNight1,
    onSurface = NordSnowStorm2,
    surfaceVariant = NordPolarNight2,
    onSurfaceVariant = NordSnowStorm0,
    outline = NordPolarNight3
)

private val DraculaColorScheme = darkColorScheme(
    primary = DraculaPurple,
    onPrimary = DraculaBg,
    primaryContainer = DraculaCurrentLine,
    onPrimaryContainer = DraculaFg,
    secondary = DraculaPink,
    onSecondary = DraculaBg,
    secondaryContainer = DraculaCurrentLine,
    onSecondaryContainer = DraculaPink,
    tertiary = DraculaGreen,
    onTertiary = DraculaBg,
    background = DraculaBg,
    onBackground = DraculaFg,
    surface = DraculaBg,
    onSurface = DraculaFg,
    surfaceContainer = DraculaCurrentLine,
    outline = DraculaCurrentLine
)

private val MonokaiColorScheme = darkColorScheme(
    primary = MonokaiPink,
    onPrimary = MonokaiBg,
    primaryContainer = MonokaiBg,
    onPrimaryContainer = MonokaiPink,
    secondary = MonokaiGreen,
    onSecondary = MonokaiBg,
    secondaryContainer = MonokaiBg,
    onSecondaryContainer = MonokaiGreen,
    tertiary = MonokaiBlue,
    onTertiary = MonokaiBg,
    background = MonokaiBg,
    onBackground = MonokaiFg,
    surface = MonokaiBg,
    onSurface = MonokaiFg,
    surfaceContainer = Color(0xFF3E3D32), // Slightly lighter monokai bg
    outline = Color(0xFF75715E)
)

private val GruvboxColorScheme = darkColorScheme(
    primary = GruvboxYellow,
    onPrimary = GruvboxBg,
    primaryContainer = GruvboxBgSoft,
    onPrimaryContainer = GruvboxYellow,
    secondary = GruvboxGreen,
    onSecondary = GruvboxBg,
    secondaryContainer = GruvboxBgSoft,
    onSecondaryContainer = GruvboxGreen,
    tertiary = GruvboxOrange,
    onTertiary = GruvboxBg,
    background = GruvboxBg,
    onBackground = GruvboxFg,
    surface = GruvboxBg,
    onSurface = GruvboxFg,
    surfaceContainer = GruvboxBgSoft,
    outline = Color(0xFF504945)
)

private val SolarizedColorScheme = darkColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase03,
    primaryContainer = SolarizedBase02,
    onPrimaryContainer = SolarizedBlue,
    secondary = SolarizedCyan,
    onSecondary = SolarizedBase03,
    secondaryContainer = SolarizedBase02,
    onSecondaryContainer = SolarizedCyan,
    tertiary = SolarizedMagenta,
    onTertiary = SolarizedBase03,
    background = SolarizedBase03,
    onBackground = SolarizedBase0,
    surface = SolarizedBase03,
    onSurface = SolarizedBase0,
    surfaceContainer = SolarizedBase02,
    outline = SolarizedBase01
)

private val SynthwaveColorScheme = darkColorScheme(
    primary = SynthwavePink,
    onPrimary = SynthwaveBg,
    primaryContainer = SynthwaveSurface,
    onPrimaryContainer = SynthwavePink,
    secondary = SynthwaveCyan,
    onSecondary = SynthwaveBg,
    secondaryContainer = SynthwaveSurface,
    onSecondaryContainer = SynthwaveCyan,
    tertiary = SynthwaveYellow,
    onTertiary = SynthwaveBg,
    background = SynthwaveBg,
    onBackground = SynthwaveFg,
    surface = SynthwaveBg,
    onSurface = SynthwaveFg,
    surfaceContainer = SynthwaveSurface,
    outline = SynthwavePurple
)

private val NordLightColorScheme = lightColorScheme(
    primary = NordFrost3,                 // 5E81AC (Dark Blue)
    onPrimary = NordSnowStorm2,           // ECEFF4
    primaryContainer = NordFrost1,        // 88C0D0
    onPrimaryContainer = NordPolarNight0, // 2E3440
    secondary = NordFrost2,               // 81A1C1
    onSecondary = NordSnowStorm2,
    secondaryContainer = NordFrost0,      // 8FBCBB
    onSecondaryContainer = NordPolarNight0,
    tertiary = NordAuroraPurple,
    onTertiary = NordSnowStorm2,
    background = NordSnowStorm2,          // ECEFF4
    onBackground = NordPolarNight0,       // 2E3440
    surface = NordSnowStorm0,             // D8DEE9
    onSurface = NordPolarNight0,
    surfaceContainer = NordSnowStorm1,    // E5E9F0
    outline = NordPolarNight2
)

private val GruvboxLightColorScheme = lightColorScheme(
    primary = GruvboxYellow,
    onPrimary = GruvboxLightFg,
    primaryContainer = GruvboxLightBgSoft,
    onPrimaryContainer = GruvboxYellow,
    secondary = GruvboxGreen,
    onSecondary = GruvboxLightFg,
    secondaryContainer = GruvboxLightBgSoft,
    onSecondaryContainer = GruvboxGreen,
    tertiary = GruvboxOrange,
    onTertiary = GruvboxLightFg,
    background = GruvboxLightBg,          // FBF1C7
    onBackground = GruvboxLightFg,        // 3C3836
    surface = GruvboxLightBg,
    onSurface = GruvboxLightFg,
    surfaceContainer = GruvboxLightBgSoft,
    outline = Color(0xFF7C6F64)
)

private val SolarizedLightColorScheme = lightColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase3,           // FDF6E3
    primaryContainer = SolarizedBase2,    // EEE8D5
    onPrimaryContainer = SolarizedBlue,
    secondary = SolarizedCyan,
    onSecondary = SolarizedBase3,
    secondaryContainer = SolarizedBase2,
    onSecondaryContainer = SolarizedCyan,
    tertiary = SolarizedMagenta,
    onTertiary = SolarizedBase3,
    background = SolarizedBase3,
    onBackground = SolarizedBase00,       // 657B83
    surface = SolarizedBase3,
    onSurface = SolarizedBase00,
    surfaceContainer = SolarizedBase2,
    outline = SolarizedBase01Light
)

private val OneLightColorScheme = lightColorScheme(
    primary = OneLightBlue,
    onPrimary = Color.White,
    primaryContainer = OneLightSurface,
    onPrimaryContainer = OneLightBlue,
    secondary = OneLightCyan,
    onSecondary = Color.White,
    secondaryContainer = OneLightSurface,
    onSecondaryContainer = OneLightCyan,
    tertiary = OneLightMagenta,
    onTertiary = Color.White,
    background = OneLightBg,              // FAFAFA
    onBackground = OneLightFg,            // 383A42
    surface = OneLightBg,
    onSurface = OneLightFg,
    surfaceContainer = OneLightSurface,
    outline = Color(0xFFA0A1A7)
)

@Composable
fun MKMTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT, AppTheme.NORD_LIGHT, AppTheme.GRUVBOX_LIGHT,
        AppTheme.SOLARIZED_LIGHT, AppTheme.ONE_LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED, AppTheme.NORD,
        AppTheme.DRACULA, AppTheme.MONOKAI, AppTheme.GRUVBOX,
        AppTheme.SOLARIZED, AppTheme.SYNTHWAVE -> true
        AppTheme.DYNAMIC -> isSystemInDarkTheme()
    }

    val dynamicColor = appTheme == AppTheme.DYNAMIC || (appTheme == AppTheme.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        appTheme == AppTheme.AMOLED -> AmoledColorScheme
        appTheme == AppTheme.NORD -> NordColorScheme
        appTheme == AppTheme.NORD_LIGHT -> NordLightColorScheme
        appTheme == AppTheme.DRACULA -> DraculaColorScheme
        appTheme == AppTheme.MONOKAI -> MonokaiColorScheme
        appTheme == AppTheme.GRUVBOX -> GruvboxColorScheme
        appTheme == AppTheme.GRUVBOX_LIGHT -> GruvboxLightColorScheme
        appTheme == AppTheme.SOLARIZED -> SolarizedColorScheme
        appTheme == AppTheme.SOLARIZED_LIGHT -> SolarizedLightColorScheme
        appTheme == AppTheme.SYNTHWAVE -> SynthwaveColorScheme
        appTheme == AppTheme.ONE_LIGHT -> OneLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
