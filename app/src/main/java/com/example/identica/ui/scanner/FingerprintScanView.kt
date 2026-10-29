package com.example.identica.ui.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.example.identica.R
import kotlin.math.min

class FingerprintScanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.neon_cyan)
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.neon_green)
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
        alpha = 32
    }

    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.neon_green)
        strokeWidth = 2f * density
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = 10f * density
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
        letterSpacing = 0.08f
    }

    private var progress = 0
    private var running = false
    private var scanDirection = 1
    private var scanY = 0f

    private val scanAnimation = object : Runnable {
        override fun run() {
            if (!running) return

            scanY += scanDirection * 5f * density
            if (scanY >= height - 38f * density) scanDirection = -1
            if (scanY <= 38f * density) scanDirection = 1

            invalidate()
            postDelayed(this, 24L)
        }
    }

    fun setProgress(value: Int) {
        progress = value.coerceIn(0, 100)
        invalidate()
    }

    fun startScan() {
        if (running) return
        running = true
        scanDirection = 1
        scanY = 38f * density
        post(scanAnimation)
    }

    fun stopScan() {
        running = false
        removeCallbacks(scanAnimation)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(context.getColor(R.color.matrix_charcoal))
        drawFingerprint(canvas)
        drawScanLine(canvas)
        drawLabels(canvas)
    }

    private fun drawFingerprint(canvas: Canvas) {
        val patternWidth = min(width * 0.72f, 190f * density)
        val patternHeight = min(height - 74f * density, 190f * density)
        if (patternWidth <= 0f || patternHeight <= 0f) return

        val scale = min(patternWidth / PATTERN_SIZE, patternHeight / PATTERN_SIZE)
        val left = (width - PATTERN_SIZE * scale) / 2f
        val top = (height - PATTERN_SIZE * scale) / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)

        val fingerprintRidges = createFingerprintRidges()
        fingerprintRidges.forEachIndexed { index, ridge ->
            if (running && scanY / density in ridge.bounds.top..ridge.bounds.bottom) {
                canvas.drawPath(ridge.path, glowPaint)
            }
            ridgePaint.alpha = ridge.opacity
            canvas.drawPath(ridge.path, ridgePaint)
        }

        ridgePaint.alpha = 255
        canvas.restore()
    }

    private fun createFingerprintRidges(): List<Ridge> {
        val ridges = mutableListOf<Ridge>()

        for (ridgeNumber in 0 until RIDGE_COUNT) {
            val inset = ridgeNumber * RIDGE_SPACING
            val top = RIDGE_TOP + ridgeNumber * RIDGE_TOP_SPACING
            val path = Path().apply {
                moveTo(LEFT_EDGE + inset, BOTTOM_EDGE)
                cubicTo(
                    LEFT_EDGE - CURVE_WIDTH + inset,
                    top + CURVE_HEIGHT,
                    CENTER_X - CURVE_WIDTH + inset / 2f,
                    top,
                    CENTER_X,
                    top
                )
                cubicTo(
                    CENTER_X + CURVE_WIDTH - inset / 2f,
                    top,
                    RIGHT_EDGE + CURVE_WIDTH - inset,
                    top + CURVE_HEIGHT,
                    RIGHT_EDGE - inset,
                    BOTTOM_EDGE
                )
            }
            ridges += Ridge(path, 235 - ridgeNumber * 12)
        }

        val innerWhorl = Path().apply {
            moveTo(LEFT_EDGE + 55f, 128f)
            cubicTo(LEFT_EDGE + 24f, 112f, LEFT_EDGE + 33f, 63f, CENTER_X - 3f, 57f)
            cubicTo(CENTER_X + 31f, 51f, RIGHT_EDGE - 27f, 72f, RIGHT_EDGE - 37f, 101f)
            cubicTo(RIGHT_EDGE - 45f, 127f, CENTER_X + 27f, 146f, CENTER_X + 5f, 128f)
            cubicTo(CENTER_X - 12f, 115f, CENTER_X - 4f, 88f, CENTER_X + 14f, 88f)
            cubicTo(CENTER_X + 29f, 88f, CENTER_X + 31f, 104f, CENTER_X + 20f, 111f)
        }
        ridges += Ridge(innerWhorl, 255)

        return ridges
    }

    private fun drawScanLine(canvas: Canvas) {
        if (!running) return

        val sidePadding = 24f * density
        val scanPosition = scanY.coerceIn(30f * density, height - 30f * density)
        canvas.drawLine(sidePadding, scanPosition, width - sidePadding, scanPosition, glowPaint)
        canvas.drawLine(sidePadding, scanPosition, width - sidePadding, scanPosition, scanPaint)
    }

    private fun drawLabels(canvas: Canvas) {
        val sidePadding = 16f * density
        val topLabelY = 22f * density
        val bottomLabelY = height - 14f * density

        canvas.drawText("FINGERPRINT RIDGES", sidePadding, topLabelY, labelPaint)
        val status = if (running) {
            "VERIFYING · $progress%"
        } else {
            "READY FOR VERIFICATION"
        }
        canvas.drawText(status, sidePadding, bottomLabelY, labelPaint)
    }

    private data class Ridge(
        val path: Path,
        val opacity: Int
    ) {
        val bounds = path.computeBounds(android.graphics.RectF(), true)
            .let { android.graphics.RectF() }
    }

    private companion object {
        const val PATTERN_SIZE = 220f
        const val CENTER_X = 110f
        const val LEFT_EDGE = 25f
        const val RIGHT_EDGE = 195f
        const val TOP = 30f
        const val RIDGE_TOP = 34f
        const val RIDGE_TOP_SPACING = 5.2f
        const val RIDGE_SPACING = 8f
        const val RIDGE_COUNT = 8
        const val BOTTOM_EDGE = 190f
        const val CURVE_WIDTH = 32f
        const val CURVE_HEIGHT = 35f
    }
}
