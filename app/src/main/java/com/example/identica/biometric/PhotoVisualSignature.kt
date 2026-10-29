package com.example.identica.biometric

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Offline perceptual visual signature for image similarity. It is more tolerant of
 * JPEG re-encoding and modest brightness changes than SHA-256. It is NOT a trained
 * face-recognition embedding and must not be interpreted as proof of identity.
 */
object PhotoVisualSignature {
    private const val GRID_W = 17
    private const val GRID_H = 16

    fun fromFile(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while (options.outWidth / sample > 1024 || options.outHeight / sample > 1024) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return try { fromBitmap(bitmap) } finally { bitmap.recycle() }
    }

    fun fromBitmap(source: Bitmap): String {
        // Center-square crop reduces differences from portrait framing and background margins.
        val side = min(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val crop = Bitmap.createBitmap(source, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(crop, GRID_W, GRID_H, true)
        if (crop !== source) crop.recycle()
        val gray = IntArray(GRID_W * GRID_H)
        for (y in 0 until GRID_H) for (x in 0 until GRID_W) {
            val c = scaled.getPixel(x, y)
            gray[y * GRID_W + x] = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }
        scaled.recycle()
        val bits = StringBuilder(GRID_H * (GRID_W - 1) / 4)
        var nibble = 0
        var count = 0
        for (y in 0 until GRID_H) for (x in 0 until GRID_W - 1) {
            nibble = (nibble shl 1) or if (gray[y * GRID_W + x] >= gray[y * GRID_W + x + 1]) 1 else 0
            count++
            if (count == 4) { bits.append("0123456789abcdef"[nibble]); nibble = 0; count = 0 }
        }
        if (count > 0) { nibble = nibble shl (4 - count); bits.append("0123456789abcdef"[nibble]) }
        return bits.toString()
    }
}
