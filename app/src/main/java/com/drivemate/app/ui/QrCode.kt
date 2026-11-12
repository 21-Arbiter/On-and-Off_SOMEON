package com.drivemate.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.floor

/** Black-on-white QR code for [text], drawn module by module on whole pixels so it scans cleanly. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
    }
    Canvas(modifier.background(Color.White)) {
        val cell = floor(size.minDimension / matrix.width)
        val origin = Offset((size.width - cell * matrix.width) / 2, (size.height - cell * matrix.height) / 2)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, origin + Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
    }
}
