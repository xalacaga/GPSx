// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code generated on the device (no network service sees the encoded link). */
@Composable
fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap =
        remember(content) {
            runCatching {
                val matrix =
                    QRCodeWriter().encode(
                        content,
                        BarcodeFormat.QR_CODE,
                        0,
                        0,
                        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
                    )
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix.get(index % matrix.width, index / matrix.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        } ?: return
    Image(bitmap, contentDescription = "QR code", filterQuality = FilterQuality.None, modifier = modifier.size(size))
}
