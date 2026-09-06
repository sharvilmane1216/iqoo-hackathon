package com.aasra.companion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class VoicePalette(val filaments: List<Color>, val mist: Color)

val LocalVoicePalette = staticCompositionLocalOf {
    VoicePalette(listOf(Voice1Dark, Voice2Dark, Voice3Dark, Voice4Dark), VoiceMistDark)
}

private val DarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = OnPrimaryDark,
    secondary = InkSoftDark,
    onSecondary = OnPrimaryDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = PaperRaisedDark,
    onSurfaceVariant = InkSoftDark,
    error = SosRedDark,
    onError = OnPrimaryDark,
    outline = LineOnPaperDark,
    primaryContainer = PaperRaisedDark,
    onPrimaryContainer = InkDark,
    secondaryContainer = PaperRaisedDark,
    onSecondaryContainer = InkDark,
    tertiary = InkSoftDark,
    onTertiary = OnPrimaryDark,
    tertiaryContainer = PaperRaisedDark,
    onTertiaryContainer = InkDark,
    surfaceContainer = PaperRaisedDark,
    surfaceContainerHigh = PaperRaisedDark,
    surfaceContainerHighest = PaperRaisedDark,
    surfaceContainerLow = PaperDark,
    surfaceContainerLowest = PaperDark,
    surfaceTint = Color.Transparent,
)

@Composable
fun AasraTheme(content: @Composable () -> Unit) {
    val voice = VoicePalette(listOf(Voice1Dark, Voice2Dark, Voice3Dark, Voice4Dark), VoiceMistDark)
    CompositionLocalProvider(LocalVoicePalette provides voice) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = AasraTypography,
            shapes = Shapes(
                small = RoundedCornerShape(13.dp),
                medium = RoundedCornerShape(17.dp),
                large = RoundedCornerShape(19.dp),
                extraLarge = RoundedCornerShape(22.dp),
            ),
            content = content,
        )
    }
}
