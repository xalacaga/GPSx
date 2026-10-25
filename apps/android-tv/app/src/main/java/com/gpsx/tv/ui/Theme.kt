package com.gpsx.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Ink = Color(0xFF07131D)
val DeepBlue = Color(0xFF0B1C28)
val RaisedBlue = Color(0xFF112C3C)
val Mint = Color(0xFF54F0BD)
val Sky = Color(0xFF79AFFF)
val SoftWhite = Color(0xFFF3F8FB)
val Muted = Color(0xFF9CB0BC)
val Amber = Color(0xFFFFC96B)
val Danger = Color(0xFFFF7D86)

private val GpsxColors =
    darkColorScheme(
        primary = Mint,
        onPrimary = Ink,
        secondary = Sky,
        onSecondary = Ink,
        background = Ink,
        onBackground = SoftWhite,
        surface = DeepBlue,
        onSurface = SoftWhite,
        error = Danger,
    )

@Composable
fun GpsxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GpsxColors, content = content)
}

