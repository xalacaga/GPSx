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
import com.gpsx.tv.retro.RetroSystem

/**
 * Original GPSx drawings of retro systems, in the spirit of [StandingConsoleArt]: drawn in code from
 * plain shapes, with no photo, logo or trademark. [glow] tints the light accents. Systems without a
 * drawing of their own share the cartridge console.
 */
@Composable
fun RetroConsoleArt(system: RetroSystem, modifier: Modifier = Modifier, glow: Color = Signal) {
    Canvas(modifier) {
        when (system) {
            RetroSystem.NEOGEO -> drawBlackCartridgeConsole(glow)
            RetroSystem.ARCADE -> drawArcadeCabinet(glow)
            RetroSystem.AMIGA -> drawKeyboardComputer(glow)
            else -> drawGreyCartridgeConsole(glow)
        }
    }
}

/** A pale 16-bit era console seen in three-quarter view: rounded body, cartridge in its slot, pad. */
private fun DrawScope.drawGreyCartridgeConsole(glow: Color) {
    val w = size.width
    val h = size.height
    floorShadow(w * 0.08f, h * 0.86f, w * 0.84f)
    // Body: a low rounded box, lighter on top.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFE9E6DF), Color(0xFFC9C5BC)), startY = h * 0.42f, endY = h * 0.86f),
        topLeft = Offset(w * 0.08f, h * 0.42f),
        size = Size(w * 0.84f, h * 0.44f),
        cornerRadius = CornerRadius(w * 0.08f),
    )
    // Top deck and its darker front lip.
    drawRoundRect(Color(0xFFF4F1EA), topLeft = Offset(w * 0.12f, h * 0.44f), size = Size(w * 0.76f, h * 0.16f), cornerRadius = CornerRadius(w * 0.06f))
    drawRect(Color(0xFF8D8A84), topLeft = Offset(w * 0.08f, h * 0.70f), size = Size(w * 0.84f, h * 0.012f))
    // Cartridge standing in the slot.
    drawRoundRect(Color(0xFF3A3A40), topLeft = Offset(w * 0.32f, h * 0.30f), size = Size(w * 0.36f, h * 0.20f), cornerRadius = CornerRadius(w * 0.02f))
    drawRoundRect(Color(0xFF55555D), topLeft = Offset(w * 0.36f, h * 0.33f), size = Size(w * 0.28f, h * 0.10f), cornerRadius = CornerRadius(w * 0.01f))
    // Two front switches and a power light.
    drawRoundRect(Color(0xFF9D9991), topLeft = Offset(w * 0.18f, h * 0.50f), size = Size(w * 0.10f, h * 0.05f), cornerRadius = CornerRadius(w * 0.02f))
    drawRoundRect(Color(0xFF9D9991), topLeft = Offset(w * 0.72f, h * 0.50f), size = Size(w * 0.10f, h * 0.05f), cornerRadius = CornerRadius(w * 0.02f))
    drawCircle(glow, radius = w * 0.018f, center = Offset(w * 0.16f, h * 0.76f))
    drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(w * 0.08f, h * 0.42f), size = Size(w * 0.84f, h * 0.44f), cornerRadius = CornerRadius(w * 0.08f), style = Stroke(w * 0.006f))
}

/** A wide black home arcade console with a tall cartridge and a big stick controller beside it. */
private fun DrawScope.drawBlackCartridgeConsole(glow: Color) {
    val w = size.width
    val h = size.height
    floorShadow(w * 0.04f, h * 0.86f, w * 0.92f)
    // Body.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF34343A), Color(0xFF111114)), startY = h * 0.48f, endY = h * 0.86f),
        topLeft = Offset(w * 0.04f, h * 0.48f),
        size = Size(w * 0.62f, h * 0.38f),
        cornerRadius = CornerRadius(w * 0.03f),
    )
    drawRect(Color(0xFF4A4A52), topLeft = Offset(w * 0.04f, h * 0.48f), size = Size(w * 0.62f, h * 0.02f))
    // Tall cartridge.
    drawRoundRect(Color(0xFF1C1C20), topLeft = Offset(w * 0.18f, h * 0.20f), size = Size(w * 0.34f, h * 0.32f), cornerRadius = CornerRadius(w * 0.015f))
    drawRoundRect(Color(0xFFC9C4BA), topLeft = Offset(w * 0.21f, h * 0.24f), size = Size(w * 0.28f, h * 0.14f), cornerRadius = CornerRadius(w * 0.01f))
    // Front light strip.
    drawLine(
        Brush.horizontalGradient(listOf(glow.copy(alpha = 0f), glow, glow.copy(alpha = 0f)), startX = w * 0.08f, endX = w * 0.62f),
        start = Offset(w * 0.08f, h * 0.74f),
        end = Offset(w * 0.62f, h * 0.74f),
        strokeWidth = h * 0.012f,
    )
    // Stick controller: a long box, ball-top stick and four buttons.
    drawRoundRect(Color(0xFF1C1C20), topLeft = Offset(w * 0.68f, h * 0.66f), size = Size(w * 0.28f, h * 0.18f), cornerRadius = CornerRadius(w * 0.02f))
    drawLine(Color(0xFF8D8A84), start = Offset(w * 0.74f, h * 0.70f), end = Offset(w * 0.74f, h * 0.56f), strokeWidth = w * 0.012f)
    drawCircle(Color(0xFFE6E2D9), radius = w * 0.032f, center = Offset(w * 0.74f, h * 0.55f))
    listOf(Color(0xFFFF3B3B), Amber, Go, Color(0xFF4A90E2)).forEachIndexed { index, color ->
        drawCircle(color, radius = w * 0.014f, center = Offset(w * (0.82f + index * 0.035f), h * 0.74f))
    }
}

/** An upright arcade cabinet: marquee, glowing screen, control panel with a stick and buttons. */
private fun DrawScope.drawArcadeCabinet(glow: Color) {
    val w = size.width
    val h = size.height
    floorShadow(w * 0.24f, h * 0.93f, w * 0.52f)
    val cabinet =
        Path().apply {
            moveTo(w * 0.30f, h * 0.04f)
            lineTo(w * 0.70f, h * 0.04f)
            lineTo(w * 0.70f, h * 0.50f)
            lineTo(w * 0.76f, h * 0.60f)
            lineTo(w * 0.70f, h * 0.66f)
            lineTo(w * 0.70f, h * 0.94f)
            lineTo(w * 0.30f, h * 0.94f)
            lineTo(w * 0.30f, h * 0.66f)
            lineTo(w * 0.24f, h * 0.60f)
            lineTo(w * 0.30f, h * 0.50f)
            close()
        }
    drawPath(cabinet, Brush.horizontalGradient(listOf(Color(0xFF111114), Color(0xFF2A2A30), Color(0xFF111114)), startX = w * 0.24f, endX = w * 0.76f))
    drawPath(cabinet, Color(0xFF4A4A52), style = Stroke(w * 0.008f))
    // Marquee.
    drawRect(Brush.verticalGradient(listOf(glow.copy(alpha = 0.95f), glow.copy(alpha = 0.55f)), startY = h * 0.07f, endY = h * 0.15f), topLeft = Offset(w * 0.33f, h * 0.07f), size = Size(w * 0.34f, h * 0.08f))
    // Screen with a faint scanline glow.
    drawRect(Color(0xFF050507), topLeft = Offset(w * 0.34f, h * 0.19f), size = Size(w * 0.32f, h * 0.27f))
    drawRect(
        Brush.radialGradient(listOf(Color(0xFF3DDC84).copy(alpha = 0.35f), Color.Transparent), center = Offset(w * 0.50f, h * 0.325f), radius = w * 0.2f),
        topLeft = Offset(w * 0.34f, h * 0.19f),
        size = Size(w * 0.32f, h * 0.27f),
    )
    for (line in 0 until 9) {
        val y = h * (0.21f + line * 0.028f)
        drawLine(Color.White.copy(alpha = 0.05f), start = Offset(w * 0.35f, y), end = Offset(w * 0.65f, y), strokeWidth = h * 0.004f)
    }
    // Control panel: stick and buttons.
    drawLine(Color(0xFF8D8A84), start = Offset(w * 0.39f, h * 0.60f), end = Offset(w * 0.39f, h * 0.55f), strokeWidth = w * 0.012f)
    drawCircle(Color(0xFFFF3B3B), radius = w * 0.025f, center = Offset(w * 0.39f, h * 0.545f))
    listOf(Amber, Go, Color(0xFF4A90E2)).forEachIndexed { index, color ->
        drawCircle(color, radius = w * 0.016f, center = Offset(w * (0.50f + index * 0.06f), h * 0.59f))
    }
    // Coin door.
    drawRect(Color(0xFF3A3A40), topLeft = Offset(w * 0.43f, h * 0.74f), size = Size(w * 0.14f, h * 0.10f))
    drawRect(glow, topLeft = Offset(w * 0.47f, h * 0.77f), size = Size(w * 0.012f, h * 0.035f))
    drawRect(glow, topLeft = Offset(w * 0.52f, h * 0.77f), size = Size(w * 0.012f, h * 0.035f))
}

/** A home computer built into its keyboard, seen from the front: key rows and a floppy drive on the side. */
private fun DrawScope.drawKeyboardComputer(glow: Color) {
    val w = size.width
    val h = size.height
    floorShadow(w * 0.04f, h * 0.84f, w * 0.92f)
    // Wedge-shaped case: deeper at the back, sloping towards the front edge.
    val case =
        Path().apply {
            moveTo(w * 0.06f, h * 0.84f)
            lineTo(w * 0.94f, h * 0.84f)
            lineTo(w * 0.94f, h * 0.70f)
            lineTo(w * 0.90f, h * 0.36f)
            lineTo(w * 0.10f, h * 0.36f)
            lineTo(w * 0.06f, h * 0.70f)
            close()
        }
    drawPath(case, Brush.verticalGradient(listOf(Color(0xFFF1EDE4), Color(0xFFCFCABF)), startY = h * 0.36f, endY = h * 0.84f))
    drawPath(case, Color.Black.copy(alpha = 0.18f), style = Stroke(w * 0.006f))
    // Key rows.
    for (row in 0 until 4) {
        val top = h * (0.41f + row * 0.065f)
        val inset = w * (0.13f + row * 0.008f)
        var x = inset
        while (x < w * 0.74f) {
            drawRoundRect(Color(0xFF9D9991), topLeft = Offset(x, top), size = Size(w * 0.038f, h * 0.048f), cornerRadius = CornerRadius(w * 0.006f))
            x += w * 0.046f
        }
    }
    drawRoundRect(Color(0xFF9D9991), topLeft = Offset(w * 0.30f, h * 0.67f), size = Size(w * 0.30f, h * 0.045f), cornerRadius = CornerRadius(w * 0.006f))
    // Numeric pad and the floppy slot on the right side.
    drawRoundRect(Color(0xFFB5B0A6), topLeft = Offset(w * 0.77f, h * 0.41f), size = Size(w * 0.11f, h * 0.30f), cornerRadius = CornerRadius(w * 0.01f))
    drawRect(Color(0xFF3A3A40), topLeft = Offset(w * 0.79f, h * 0.76f), size = Size(w * 0.13f, h * 0.022f))
    // Power and drive lights.
    drawCircle(glow, radius = w * 0.012f, center = Offset(w * 0.12f, h * 0.775f))
    drawCircle(glow.copy(alpha = 0.6f), radius = w * 0.012f, center = Offset(w * 0.16f, h * 0.775f))
}

private fun DrawScope.floorShadow(left: Float, top: Float, width: Float) {
    drawOval(Color.Black.copy(alpha = 0.45f), topLeft = Offset(left, top), size = Size(width, size.height * 0.06f))
}
