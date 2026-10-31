package com.gpsx.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Arcade palette: near-black, warm off-white text and one signal colour for focus and action.
// No gradients, no tinted glass: contrast that still reads on a projector at three metres.
val Ink = Color(0xFF0C0C0D)
val Panel = Color(0xFF161618)
val PanelRaised = Color(0xFF202023)
val Line = Color(0xFF2E2E33)
val Signal = Color(0xFFFF5A1F)
val Chalk = Color(0xFFC9C4BA)
val SoftWhite = Color(0xFFF4F1EA)
val Muted = Color(0xFF8D8A84)
val Amber = Color(0xFFFFC400)
val Danger = Color(0xFFFF3B3B)
val Go = Color(0xFF3DDC84)

private val GpsxColors =
    darkColorScheme(
        primary = Signal,
        onPrimary = Ink,
        secondary = Chalk,
        onSecondary = Ink,
        background = Ink,
        onBackground = SoftWhite,
        surface = Panel,
        onSurface = SoftWhite,
        error = Danger,
    )

@Composable
fun GpsxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GpsxColors, content = content)
}
