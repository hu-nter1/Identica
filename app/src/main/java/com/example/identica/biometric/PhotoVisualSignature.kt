package com.example.identica.biometric

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.min

/**
 * Creates a small image signature for approximate visual comparison.
 *
 * This is not a trained face-recognition model and must not be treated as proof
 * that two images belong to the same person.
 */
object PhotoVisualSignature {

    private const val GRID_WIDTH = 17
    private const val GRID_HEIGHT = 16
    private const val MAX_IMAGE_EDGE = 1024
    private const val RED_WEIGHT = 299
    private const val GREEN_WEIGHT = 587
    private const val BLUE_WEIGHT = 114
    private const val COLOR_WEIGHT_TOTAL = 1000
    private const val BITS_PER_HEX_DIGIT = 4
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val SIGNATURE_HEX_LENGTH =
        GRID_HEIGHT * (GRID_WIDTH - 1) / BITS_PER_HEX_DIGIT

    fun fromFile(file: File): String? {
        if (!file.exists() || !file.isFile) return null

        val imageBounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, imageBounds)

        if (imageBounds.outWidth <= 0 || imageBounds.outHeight <= 0) return null

        val sampleSize = calculateSampleSize(imageBounds.outWidth, imageBounds.outHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        return try {
            fromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1

        while (width / sampleSize > MAX_IMAGE_EDGE ||
            height / sampleSize > MAX_IMAGE_EDGE
        ) {
            sampleSize *= 2
        }

        return sampleSize
    }

    fun fromBitmap(source: Bitmap): String {
        // A center-square crop reduces changes caused by portrait framing and margins.
        val squareSize = min(source.width, source.height)
        val cropLeft = (source.width - squareSize) / 2
        val cropTop = (source.height - squareSize) / 2
        val squareBitmap = Bitmap.createBitmap(
            source,
            cropLeft,
            cropTop,
            squareSize,
            squareSize
        )
        val smallBitmap = Bitmap.createScaledBitmap(
            squareBitmap,
            GRID_WIDTH,
            GRID_HEIGHT,
            true
        )
        if (squareBitmap !== source) squareBitmap.recycle()

        val grayValues = readGrayValues(smallBitmap)
        smallBitmap.recycle()

        return createSignature(grayValues)
    }

    private fun readGrayValues(bitmap: Bitmap): IntArray {
        val grayValues = IntArray(GRID_WIDTH * GRID_HEIGHT)

        for (y in 0 until GRID_HEIGHT) {
            for (x in 0 until GRID_WIDTH) {
                val color = bitmap.getPixel(x, y)
                grayValues[y * GRID_WIDTH + x] = toGray(color)
            }
        }

        return grayValues
    }

    private fun toGray(color: Int): Int {
        return (
            Color.red(color) * RED_WEIGHT +
                Color.green(color) * GREEN_WEIGHT +
                Color.blue(color) * BLUE_WEIGHT
            ) / COLOR_WEIGHT_TOTAL
    }

    private fun createSignature(grayValues: IntArray): String {
        val signature = StringBuilder(SIGNATURE_HEX_LENGTH)
        var bitsInNibble = 0
        var nibble = 0

        for (y in 0 until GRID_HEIGHT) {
            for (x in 0 until GRID_WIDTH - 1) {
                val currentPixel = grayValues[y * GRID_WIDTH + x]
                val nextPixel = grayValues[y * GRID_WIDTH + x + 1]
                val currentBit = if (currentPixel >= nextPixel) 1 else 0

                nibble = (nibble shl 1) or currentBit
                bitsInNibble++

                if (bitsInNibble == BITS_PER_HEX_DIGIT) {
                    signature.append(HEX_DIGITS[nibble])
                    nibble = 0
                    bitsInNibble = 0
                }
            }
        }

        if (bitsInNibble > 0) {
            val paddingBits = BITS_PER_HEX_DIGIT - bitsInNibble
            nibble = nibble shl paddingBits
            signature.append(HEX_DIGITS[nibble])
        }

        return signature.toString()
    }
}
