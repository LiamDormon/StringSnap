package com.example.stringsnap.audio.pitch

/**
 * Contract for a real-time pitch detector driven by microphone input.
 *
 * Implementations call [Listener.onPitchDetected] when a confident pitch is
 * found in a buffer, and [Listener.onSilence] when the signal is below the
 * silence threshold or no reliable pitch can be determined. All callbacks are
 * dispatched on the main thread.
 */
interface PitchDetector {

    /** Begin audio capture and pitch analysis. No-op if already running. */
    fun start()

    /** Stop audio capture, release the microphone, and cancel pending callbacks. */
    fun stop()

    /** Replace the current listener. Safe to call on any thread. Pass null to unsubscribe. */
    fun setListener(listener: Listener?)

    interface Listener {

        /**
         * Called when a pitch has been detected above the silence threshold.
         *
         * @param frequencyHz Estimated fundamental frequency in Hz.
         * @param confidence  Detection confidence in [0, 1]. Values below ~0.5 should
         *                    be treated with caution (noisy signal or weak fundamental).
         */
        fun onPitchDetected(frequencyHz: Double, confidence: Float)

        /**
         * Called when the input signal is silent or no reliable pitch can be found.
         * The UI should use this to return to an idle/waiting state rather than
         * freezing on the last detected value.
         */
        fun onSilence()
    }
}