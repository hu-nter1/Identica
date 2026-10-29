package com.example.identica.ui.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.random.Random

class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val matrixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON_GREEN
        textSize = 12f * density
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON_CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val gridPaint = Paint().apply {
        color = NEON_GREEN
        alpha = 32
        strokeWidth = 1f
    }
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON_GREEN
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON_GREEN
    }

    private var laserFraction = LASER_HIDDEN
    private var frame = 0L
    private var matrixColumns = FloatArray(0)
    private var matrixSpeeds = FloatArray(0)
    private var matrixLengths = IntArray(0)
    private var running = false
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            frame++
            if (frame % 20L == 0L) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
            }
            invalidate()
            postDelayed(this, 33L)
        }
    }

    fun startMatrix() {
        if (running) return
        running = true
        ensureMatrix()
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
        removeCallbacks(ticker)
        post(ticker)
    }

    fun stopMatrix() {
        running = false
        removeCallbacks(ticker)
        laserFraction = LASER_HIDDEN
        invalidate()
    }

    fun updateLaser(fraction: Float) {
        laserFraction = fraction
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        stopMatrix()
        tone.release()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        ensureMatrix()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 14f * density
        val left = inset
        val top = inset
        val right = width - inset
        val bottom = height - inset

        canvas.drawColor(Color.argb(235, 0, 8, 3))
        drawMatrix(canvas, left, top, right, bottom)
        drawGrid(canvas, left, top, right, bottom)
        drawBrackets(canvas, left, top, right, bottom)
        drawHud(canvas, left, top, right, bottom)
        drawLaser(canvas, left, top, right, bottom)
    }

    private fun ensureMatrix() {
        val count = max(1, (width / (18f * density)).toInt())
        if (matrixColumns.size == count) return
        matrixColumns = FloatArray(count) { i -> 10f * density + i * 18f * density }
        matrixSpeeds = FloatArray(count) { Random.nextFloat() * 2.5f + 1.2f }
        matrixLengths = IntArray(count) { Random.nextInt(5, 18) }
    }

    private fun drawMatrix(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val step = 18f * density
        val chars = "01IDENTICA<>[]{}#$%/\\\\"
        for (i in matrixColumns.indices) {
            var y = ((frame * matrixSpeeds[i] * density) % (height + 180f * density)) - 90f * density
            repeat(matrixLengths[i]) { n ->
                if (y in top..bottom) {
                    matrixPaint.alpha = if (n == 0) 230 else max(25, 150 - n * 9)
                    val c = chars[(i + n + frame.toInt()) % chars.length]
                    canvas.drawText(c.toString(), matrixColumns[i], y, matrixPaint)
                }
                y += step
            }
        }
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val step = 24f * density
        var x = left
        while (x <= right) {
            canvas.drawLine(x, top, x, bottom, gridPaint)
            x += step
        }
        var y = top
        while (y <= bottom) {
            canvas.drawLine(left, y, right, y, gridPaint)
            y += step
        }
    }

    private fun drawBrackets(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val length = 30f * density
        canvas.drawLine(left, top, left + length, top, bracketPaint)
        canvas.drawLine(left, top, left, top + length, bracketPaint)
        canvas.drawLine(right, top, right - length, top, bracketPaint)
        canvas.drawLine(right, top, right, top + length, bracketPaint)
        canvas.drawLine(left, bottom, left + length, bottom, bracketPaint)
        canvas.drawLine(left, bottom, left, bottom - length, bracketPaint)
        canvas.drawLine(right, bottom, right - length, bottom, bracketPaint)
        canvas.drawLine(right, bottom, right, bottom - length, bracketPaint)
    }

    private fun drawHud(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        matrixPaint.alpha = 210
        matrixPaint.textSize = 10f * density
        canvas.drawText("Identica", left + 12f * density, top + 20f * density, matrixPaint)
        canvas.drawText("Live matrix ${frame.toString(16).uppercase()}", left + 12f * density, bottom - 12f * density, matrixPaint)
        canvas.drawText("SCAN STREAM", right - 92f * density, top + 20f * density, matrixPaint)
    }

    private fun drawLaser(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        if (laserFraction < 0f) return
        val y = top + laserFraction * (bottom - top)
        glowPaint.alpha = 35
        canvas.drawRect(left, y - 12f * density, right, y + 12f * density, glowPaint)
        glowPaint.alpha = 70
        canvas.drawRect(left, y - 5f * density, right, y + 5f * density, glowPaint)
        laserPaint.strokeWidth = 2f * density
        canvas.drawLine(left, y, right, y, laserPaint)
    }

    companion object {
        const val LASER_HIDDEN = -1f
        private val NEON_GREEN = Color.rgb(0, 255, 65)
        private val NEON_CYAN = Color.rgb(0, 255, 255)
    }
}
