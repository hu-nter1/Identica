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
            while (options.outWidth / sample > MAX_IMAGE_EDGE ||
                options.outHeight / sample > MAX_IMAGE_EDGE
            ) {
                sample *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            return try {
                fromBitmap(bitmap)
            } finally {
                bitmap.recycle()
        }
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
            for (y in 0 until GRID_H) {
                for (x in 0 until GRID_W) {
                    val color = scaled.getPixel(x, y)
                    val grayValue = (
                        Color.red(color) * RED_WEIGHT +
                            Color.green(color) * GREEN_WEIGHT +
                            Color.blue(color) * BLUE_WEIGHT
                        ) / COLOR_WEIGHT_TOTAL
                    gray[y * GRID_W + x] = grayValue
                }
            }
            scaled.recycle()

            val signature = StringBuilder(SIGNATURE_HEX_LENGTH)
            var nibble = 0

            for (y in 0 until GRID_H) {
                for (x in 0 until GRID_W - 1) {
                    val currentPixel = gray[y * GRID_W + x]
                    val nextPixel = gray[y * GRID_W + x + 1]
                    val currentBit = if (currentPixel >= nextPixel) 1 else 0

                    nibble = (nibble shl 1) or currentBit
                    if ((x + 1) % BITS_PER_HEX_DIGIT == 0) {
                        signature.append(HEX_DIGITS[nibble])
                        nibble = 0
                    }
                }
            }

            return signature.toString()
        }

        private const val MAX_IMAGE_EDGE = 1024
        private const val RED_WEIGHT = 299
        private const val GREEN_WEIGHT = 587
        private const val BLUE_WEIGHT = 114
        private const val COLOR_WEIGHT_TOTAL = 1000
        private const val BITS_PER_HEX_DIGIT = 4
        private const val SIGNATURE_HEX_LENGTH = GRID_H * (GRID_W - 1) / BITS_PER_HEX_DIGIT
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
