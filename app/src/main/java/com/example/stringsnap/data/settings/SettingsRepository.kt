package com.example.stringsnap.data.settings

/**
 * Simple in-memory settings repository used for early development.
 */
class SettingsRepository {
    // Concert pitch A4 in Hz
    var concertPitchA4: Double = 440.0

    // in-tune threshold in cents
    var inTuneThresholdCents: Int = 5

    // Ignore weak detector results so room noise does not move the tuner.
    var minimumPitchConfidence: Float = 0.35f

    // Auto mode only claims a string when the pitch is near one of the preset targets.
    var autoStringDetectionWindowCents: Double = 120.0

    // Manual mode still rejects wildly off-target noise.
    var manualStringDetectionWindowCents: Double = 220.0

    // Require repeated frames before switching to avoid jitter.
    var detectionDebounceFrames: Int = 2

    // Keep the last relevant note visible through short gaps and noisy frames.
    var signalLostGracePeriodMs: Long = 10_000L
}
