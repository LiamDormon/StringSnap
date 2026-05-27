package com.example.stringsnap.audio.pitch

import android.os.Handler
import android.os.Looper
import kotlin.math.sin

/**
 * Emits deterministic test pitches on a regular interval for UI testing.
 * It cycles through a list of frequencies to simulate different strings.
 */
class FakePitchDetector(private val intervalMs: Long = 250L) : PitchDetector {
    private var listener: PitchDetector.Listener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    // A small sequence of test frequencies (E2..E4 roughly)
    private val testFreqs = doubleArrayOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)
    private var index = 0

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return
            val freq = testFreqs[index % testFreqs.size]
            // A fake confidence that wiggles slightly
            val conf = (0.5f + 0.5f * sin(index.toDouble())).toFloat()
            listener?.onPitchDetected(freq, conf)
            index++
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun start() {
        if (running) return
        running = true
        handler.post(runnable)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(runnable)
    }

    override fun setListener(listener: PitchDetector.Listener?) {
        this.listener = listener
    }
}

