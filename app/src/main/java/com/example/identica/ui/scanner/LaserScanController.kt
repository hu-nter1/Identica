package com.example.identica.ui.scanner

class LaserScanController(
    private val view: ScannerOverlayView,
    private val frameMillis: Long = 16L,
    private val sweepMillis: Long = 1800L
) {

    @Volatile
    private var running = false
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        view.startMatrix()
        val thread = Thread({ runLoop() }, "laser-scan-controller")
        thread.isDaemon = true
        worker = thread
        thread.start()
    }

    @Synchronized
    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        view.updateLaser(ScannerOverlayView.LASER_HIDDEN)
        view.stopMatrix()
    }

    private fun runLoop() {
        var position = 0f
        var direction = 1f
        val step = frameMillis.toFloat() / sweepMillis.toFloat()
        while (running) {
            position += direction * step
            if (position >= 1f) {
                position = 1f
                direction = -1f
            } else if (position <= 0f) {
                position = 0f
                direction = 1f
            }
            view.updateLaser(position)
            try {
                Thread.sleep(frameMillis)
            } catch (interrupted: InterruptedException) {
                return
            }
        }
    }
}
