package com.example.stringsnap.audio.pitch

import android.os.Handler
import android.os.Looper
import com.example.stringsnap.audio.AudioRecorder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * YIN-based pitch detector for real-time guitar tuning.
 *
 * Implements the YIN algorithm (de Cheveigné & Kawahara, 2002) with the following
 * pipeline:
 *
 *   1. Silence gate            — discard frames below RMS noise floor
 *   2. Difference function     — d(τ): mean-square difference at each lag τ
 *   3. CMNDF                   — d'(τ): normalise by cumulative mean to suppress
 *                                sub-harmonic false positives (octave errors)
 *   4. Absolute threshold      — find the first d'(τ) dip below [YIN_THRESHOLD]
 *   5. Parabolic interpolation — refine the integer lag to sub-sample resolution,
 *                                reducing frequency quantisation error from ~13 cents
 *                                at E4 down to < 1 cent
 *
 * All callbacks are dispatched on the main thread via [Handler]. The listener
 * reference is held in an [AtomicReference] so [setListener] is safe to call
 * from any thread without races.
 *
 * Replaces the previous NCC-based `TarsosPitchDetector`, which was susceptible to
 * octave errors and had insufficient frequency resolution on higher strings.
 */
class YinPitchDetector(
    private val recorder: AudioRecorder = AudioRecorder()
) : PitchDetector {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listenerRef = AtomicReference<PitchDetector.Listener?>(null)

    @Volatile private var running = false

    // ── Constants ────────────────────────────────────────────────────────────

    /**
     * Normalized RMS below this level is treated as silence and the frame is
     * discarded without running YIN. Value is in [0, 1] after dividing raw
     * Short samples by [Short.MAX_VALUE].
     *
     * 0.003 accepts weak guitar signals down to ~98 audio units; combined with
     * YIN's periodicity analysis, this reliably catches E2 and other low notes
     * even in quiet playing.
     */
    private val SILENCE_THRESHOLD_RMS = 0.003

    /**
     * Lowest frequency the detector will attempt to identify (Hz).
     * 55 Hz (A1) gives headroom below Drop C (C2 ≈ 65.4 Hz), the lowest string
     * in any preset currently supported by the app.
     */
    private val MIN_FREQUENCY_HZ = 55.0

    /**
     * Highest frequency the detector will attempt to identify (Hz).
     * 1 400 Hz covers upper harmonics required for intonation work per the spec.
     */
    private val MAX_FREQUENCY_HZ = 1_400.0

    /**
     * YIN absolute threshold for the CMNDF dip. The detector accepts the first
     * lag τ where d'(τ) drops below this value.
     *
     * 0.08 is aggressive but suitable for the 8k sample frame window (~15 E2 periods).
     * Provides high detection rate with acceptable false positive rate due to
     * 8k frame size providing good harmonic separation.
     */
    private val YIN_THRESHOLD = 0.08

    /**
     * RMS level treated as a "full strength" signal when scaling the confidence
     * output. Signals at or above this level contribute their maximum weight to
     * the blended confidence score.
     */
    private val FULL_SIGNAL_RMS = 0.025

    // ── PitchDetector interface ───────────────────────────────────────────────

    override fun start() {
        if (running) return
        running = true
        recorder.start { frame, read ->
            val event = estimatePitch(frame, read, recorder.sampleRateHz)
            if (running) {
                mainHandler.post {
                    val listener = listenerRef.get() ?: return@post
                    when (event) {
                        is PitchEvent.Detected ->
                            listener.onPitchDetected(event.frequencyHz, event.confidence)
                        PitchEvent.Silence ->
                            listener.onSilence()
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
        listenerRef.set(listener)
    }

    // ── YIN pipeline ─────────────────────────────────────────────────────────

    private fun estimatePitch(
        samples: ShortArray,
        count: Int,
        sampleRateHz: Int
    ): PitchEvent {
        val minLag = (sampleRateHz / MAX_FREQUENCY_HZ).toInt().coerceAtLeast(1)
        val maxLag = (sampleRateHz / MIN_FREQUENCY_HZ).toInt()

        // The YIN window is the first half of the buffer; the second half is used
        // as the lagged window. Require at least 2 × maxLag samples.
        if (count < maxLag * 2 || maxLag <= minLag) return PitchEvent.Silence

        // Step 1 — Silence gate
        val rms = computeNormalisedRms(samples, count)
        if (rms < SILENCE_THRESHOLD_RMS) return PitchEvent.Silence

        // Step 2 — Squared difference function d(τ), computed for all lags 1..maxLag
        //           so that the CMNDF denominator accumulates correctly from τ = 1.
        val diff = computeDifferenceFunction(samples, count, maxLag)

        // Step 3 — Cumulative mean normalised difference d'(τ)
        val cmndf = computeCmndf(diff, maxLag)

        // Step 4 — Absolute-threshold peak picking within [minLag, maxLag]
        val bestLag = pickBestLag(cmndf, minLag, maxLag) ?: return PitchEvent.Silence

        // Step 5 — Parabolic interpolation for sub-sample lag resolution
        val refinedLag = parabolicInterpolate(cmndf, bestLag, minLag, maxLag)

        val frequency = sampleRateHz / refinedLag
        if (frequency.isNaN() || frequency.isInfinite()) return PitchEvent.Silence

        // Confidence: depth of the CMNDF dip scaled by signal strength.
        // cmndf[bestLag] near 0 → strong periodic signal → high confidence.
        val dip = cmndf[bestLag]
        val signalStrength = (rms / FULL_SIGNAL_RMS).coerceIn(0.0, 1.0)
        val confidence = ((1.0 - dip) * signalStrength).coerceIn(0.0, 1.0).toFloat()

        return PitchEvent.Detected(frequency, confidence)
    }

    // ── Step 1: RMS ───────────────────────────────────────────────────────────

    // ── Step 1: RMS ───────────────────────────────────────────────────────────

    /**
     * Computes the root mean square of [samples] normalised to [0, 1] by dividing
     * each sample by [Short.MAX_VALUE] before squaring. Consistent normalisation
     * is applied here and nowhere else so the rest of the pipeline uses raw Short
     * arithmetic throughout.
     */
    private fun computeNormalisedRms(samples: ShortArray, count: Int): Double {
        var sumSquares = 0.0
        val norm = Short.MAX_VALUE.toDouble()
        for (i in 0 until count) {
            val s = samples[i] / norm
            sumSquares += s * s
        }
        return sqrt(sumSquares / count)
    }

    // ── Step 2: Difference function ───────────────────────────────────────────

    /**
     * Computes the YIN squared difference function:
     *
     *   d(τ) = Σ_{i=0}^{W−1} ( x[i] − x[i+τ] )²
     *
     * where W = [count] / 2 is the analysis window. The buffer must therefore
     * contain at least 2 × [maxLag] samples.
     *
     * Computed for τ = 0..[maxLag] (including τ = 0 which is trivially 0).
     * The full range starting from 1 (not [minLag]) is required so that the
     * CMNDF denominator accumulates all lags from the origin, matching the
     * definition in the original paper.
     *
     * Uses raw Short values throughout; no per-sample normalisation is applied
     * here because it cancels in the CMNDF ratio.
     */
    private fun computeDifferenceFunction(
        samples: ShortArray,
        count: Int,
        maxLag: Int
    ): DoubleArray {
        val window = count / 2
        val diff = DoubleArray(maxLag + 1) // diff[0] stays 0.0 by definition
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until window) {
                val delta = samples[i].toDouble() - samples[i + lag].toDouble()
                sum += delta * delta
            }
            diff[lag] = sum
        }
        return diff
    }

    // ── Step 3: CMNDF ────────────────────────────────────────────────────────

    /**
     * Computes the cumulative mean normalised difference function:
     *
     *   d'(0) = 1
     *   d'(τ) = d(τ) · τ / Σ_{j=1}^{τ} d(j)     for τ ≥ 1
     *
     * Normalising by the running mean forces the function to begin near 1 and
     * dip toward 0 at multiples of the true period. Crucially, it suppresses
     * the large dips at sub-multiples (e.g. τ/2) that cause octave errors in
     * plain autocorrelation and NCC approaches.
     */
    private fun computeCmndf(diff: DoubleArray, maxLag: Int): DoubleArray {
        val cmndf = DoubleArray(diff.size) { 1.0 } // initialise all to 1.0 (no dip)
        var runningSum = 0.0
        // cmndf[0] = 1.0 by definition; loop from 1
        for (lag in 1..maxLag) {
            runningSum += diff[lag]
            cmndf[lag] = if (runningSum == 0.0) 0.0
            else diff[lag] * lag / runningSum
        }
        return cmndf
    }

    // ── Step 4: Peak picking ─────────────────────────────────────────────────

    /**
     * Selects the best lag candidate from the CMNDF using the absolute threshold
     * method (Section 4 of de Cheveigné & Kawahara, 2002):
     *
     * 1. Scan [minLag]..[maxLag] for the first τ where d'(τ) < [YIN_THRESHOLD].
     * 2. From that τ, advance to the local minimum of the dip (in case the
     *    threshold is crossed on the ascending slope of a neighbouring dip).
     * 3. If no τ meets the threshold, fall back to the global minimum as long as
     *    it is below 0.7 (tolerant upper bound suitable for small windows).
     *    Confidence will be moderate in this case.
     *
     * Returns null if no plausible pitch period is found.
     */
    private fun pickBestLag(cmndf: DoubleArray, minLag: Int, maxLag: Int): Int? {
        var lag = minLag
        while (lag <= maxLag) {
            if (cmndf[lag] < YIN_THRESHOLD) {
                // Walk forward to the bottom of the dip
                while (lag + 1 <= maxLag && cmndf[lag + 1] < cmndf[lag]) {
                    lag++
                }
                return lag
            }
            lag++
        }
        // Fallback: return the global minimum if it looks at all periodic
        var bestLag = minLag
        for (l in minLag + 1..maxLag) {
            if (cmndf[l] < cmndf[bestLag]) bestLag = l
        }
        return if (cmndf[bestLag] < 0.7) bestLag else null
    }

    // ── Step 5: Parabolic interpolation ──────────────────────────────────────

    /**
     * Refines an integer lag estimate to sub-sample precision by fitting a
     * parabola through the CMNDF values at (lag−1, lag, lag+1) and returning
     * the continuous-domain minimum:
     *
     *   τ_refined = lag + (d'[lag−1] − d'[lag+1]) / (2 · (d'[lag−1] − 2·d'[lag] + d'[lag+1]))
     *
     * Without this step, frequency resolution at E4 (~330 Hz, sample rate 44.1 kHz)
     * is ~2.5 Hz ≈ 13 cents — nearly three times the 5-cent in-tune threshold.
     * With interpolation, resolution falls well below 1 cent across all strings.
     *
     * Returns the original integer [lag] unmodified if it lies on a boundary or
     * the parabola denominator is zero (flat region).
     */
    private fun parabolicInterpolate(
        cmndf: DoubleArray,
        lag: Int,
        minLag: Int,
        maxLag: Int
    ): Double {
        if (lag <= minLag || lag >= maxLag) return lag.toDouble()
        val prev = cmndf[lag - 1]
        val curr = cmndf[lag]
        val next = cmndf[lag + 1]
        val denominator = prev - 2.0 * curr + next
        return if (denominator == 0.0) lag.toDouble()
        else lag + (prev - next) / (2.0 * denominator)
    }

    // ── Internal result type ──────────────────────────────────────────────────

    private sealed class PitchEvent {
        data class Detected(val frequencyHz: Double, val confidence: Float) : PitchEvent()
        object Silence : PitchEvent()
    }
}