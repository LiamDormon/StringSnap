package com.example.stringsnap.domain.model

/**
 * Represents a tuning preset (6 strings, low → high)
 */
data class TargetString(
    val label: String,
    val note: Note,
    val baseFrequencyHz: Double,
    val octaveShift: Int = 0
)

data class TuningPreset(
    val name: String,
    val strings: List<TargetString>
)

