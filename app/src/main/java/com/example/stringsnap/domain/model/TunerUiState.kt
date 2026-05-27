package com.example.stringsnap.domain.model

/**
 * Minimal UI state for the tuner screen.
 */
data class TunerUiState(
    val detectedNote: Note?,
    val detectedFrequency: Double?,
    val centOffset: Double,
    val activeStringIndex: Int?,
    val stringSelectionMode: StringSelectionMode,
    val manualStringIndex: Int?,
    val confidence: Float,
    val isInTune: Boolean,
    val preset: TuningPreset,
    val intonationModeActive: Boolean,
    val octaveShiftState: OctaveShiftState = OctaveShiftState.EMPTY
)

