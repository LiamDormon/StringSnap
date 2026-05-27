package com.example.stringsnap.audio.pitch

import android.os.Handler
import android.os.Looper
import com.example.stringsnap.audio.AudioRecorder
import kotlin.math.abs
import kotlin.math.sqrt

class TarsosPitchDetector(
    private val recorder: AudioRecorder = AudioRecorder()
) : PitchDetector {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: PitchDetector.Listener? = null
    @Volatile private var running = false

    override fun start() {
        if (running) return
        running = true
        recorder.start { frame, read ->
            val result = estimatePitch(frame, read, recorder.sampleRateHz)
            if (running) {
                mainHandler.post {
                    if (result == null) {
                        listener?.onPitchDetected(NO_SIGNAL_FREQUENCY, 0.0f)
                    } else {
                        listener?.onPitchDetected(result.frequencyHz, result.confidence)
                    }
                }
            }
        }
    }

    override fun stop() {
        running = false
        recorder.stop()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun setListener(listener: PitchDetector.Listener?) {
        this.listener = listener
    }

    private fun estimatePitch(samples: ShortArray, count: Int, sampleRateHz: Int): PitchResult? {
        if (count < 2) return null

        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i] / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        if (rms < 0.008) return null

        val minFrequency = 55.0
        val maxFrequency = 1_000.0
        val minLag = (sampleRateHz / maxFrequency).toInt().coerceAtLeast(1)
        val maxLag = (sampleRateHz / minFrequency).toInt().coerceAtMost(count - 1)
        if (maxLag <= minLag) return null

        var bestLag = minLag
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var energyA = 0.0
            var energyB = 0.0
            for (i in 0 until count - lag) {
                val a = samples[i].toDouble()
                val b = samples[i + lag].toDouble()
                correlation += a * b
                energyA += a * a
                energyB += b * b
            }
            val denominator = sqrt(energyA * energyB)
            if (denominator > 0.0) {
                val score = correlation / denominator
                if (score > bestScore) {
                    bestScore = score
                    bestLag = lag
                }
            }
        }

        if (bestScore < 0.4) return null
        val frequency = sampleRateHz.toDouble() / bestLag
        if (frequency.isNaN() || frequency.isInfinite() || abs(frequency) < minFrequency) return null
        val signalStrength = (rms / 0.025).coerceIn(0.0, 1.0)
        val confidence = (bestScore * signalStrength).coerceIn(0.0, 1.0).toFloat()
        return PitchResult(frequency, confidence)
    }

    private data class PitchResult(val frequencyHz: Double, val confidence: Float)

    private companion object {
        const val NO_SIGNAL_FREQUENCY = 0.0
    }
}
