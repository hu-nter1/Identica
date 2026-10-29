package com.example.identica.ui.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class FingerprintScanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 65)
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        alpha = 35
    }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 65)
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private var progress = 0
    private var running = false
    private var direction = 1
    private var scanY = 0f
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            scanY += direction * 7f * resources.displayMetrics.density
            if (scanY >= height - 20f) direction = -1
            if (scanY <= 20f) direction = 1
            invalidate()
            postDelayed(this, 24L)
        }
    }

    fun setProgress(value: Int) { progress = value.coerceIn(0, 100); invalidate() }
    fun startScan() { if (running) return; running = true; scanY = 20f; post(tick) }
    fun stopScan() { running = false; removeCallbacks(tick); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.argb(238, 0, 10, 8))
        val cx = width / 2f
        val cy = height / 2f
        val scale = min(width, height) / 300f
        for (i in 0 until 12) {
            val path = Path()
            val rx = (24 + i * 9) * scale
            val ry = (34 + i * 10) * scale
            path.moveTo(cx, cy - ry)
            path.cubicTo(cx - rx, cy - ry * .8f, cx - rx, cy + ry * .45f, cx, cy + ry)
            path.cubicTo(cx + rx, cy + ry * .45f, cx + rx, cy - ry * .8f, cx, cy - ry)
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, linePaint)
        }
        if (running) {
            canvas.drawRect(16f, scanY - 9f, width - 16f, scanY + 9f, glowPaint)
            canvas.drawLine(16f, scanY, width - 16f, scanY, scanPaint)
        }
        linePaint.textSize = 11f * resources.displayMetrics.density
        linePaint.style = Paint.Style.FILL
        canvas.drawText("FINGERPRINT MATRIX", 16f, 24f, linePaint)
        canvas.drawText(if (running) "Live sensor $progress%" else "READY FOR SENSOR", 16f, height - 18f, linePaint)
        linePaint.style = Paint.Style.STROKE
    }
}
