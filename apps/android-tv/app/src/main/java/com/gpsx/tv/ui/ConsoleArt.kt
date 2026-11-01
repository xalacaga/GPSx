// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/**
 * Original GPSx drawing of a standing current-generation console: a dark core between two pale,
 * waisted side shells, on a small stand. Drawn in code (no photo, logo or trademark), so it stays
 * sharp at any size and carries no third-party artwork. [glow] tints the light strip along the core.
 */
@Composable
fun StandingConsoleArt(modifier: Modifier = Modifier, glow: Color = Signal) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Soft floor shadow and stand.
        drawOval(Color.Black.copy(alpha = 0.45f), topLeft = Offset(w * 0.18f, h * 0.93f), size = Size(w * 0.64f, h * 0.06f))
        drawRoundRect(Color(0xFF1B1B1E), topLeft = Offset(w * 0.30f, h * 0.915f), size = Size(w * 0.40f, h * 0.04f), cornerRadius = CornerRadius(w * 0.02f))
        // Dark core with a faint vertical sheen.
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF0E0E10), Color(0xFF26262A), Color(0xFF0E0E10)), startX = w * 0.38f, endX = w * 0.62f),
            topLeft = Offset(w * 0.385f, h * 0.05f),
            size = Size(w * 0.23f, h * 0.87f),
            cornerRadius = CornerRadius(w * 0.03f),
        )
        drawShell(glow)
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(w / 2f, h / 2f)) { drawShell(glow) }
    }
}

/** Left side shell; the right one is the same path mirrored. */
private fun DrawScope.drawShell(glow: Color) {
    val w = size.width
    val h = size.height
    val shell =
        Path().apply {
            moveTo(w * 0.375f, h * 0.035f)
            lineTo(w * 0.375f, h * 0.905f)
            lineTo(w * 0.17f, h * 0.915f)
            // Outer edge: flared at both ends, drawn in at the waist.
            cubicTo(w * 0.27f, h * 0.70f, w * 0.27f, h * 0.30f, w * 0.10f, h * 0.0f)
            close()
        }
    drawPath(shell, Brush.verticalGradient(listOf(Color(0xFFFBFAF6), Color(0xFFE6E2D9), Color(0xFFC9C4BA)), startY = 0f, endY = h))
    drawPath(shell, Color.Black.copy(alpha = 0.18f), style = Stroke(width = w * 0.006f))
    // Light strip in the seam between shell and core.
    drawLine(
        Brush.verticalGradient(listOf(glow.copy(alpha = 0f), glow.copy(alpha = 0.9f), glow.copy(alpha = 0f)), startY = h * 0.08f, endY = h * 0.88f),
        start = Offset(w * 0.381f, h * 0.08f),
        end = Offset(w * 0.381f, h * 0.88f),
        strokeWidth = w * 0.012f,
    )
}
