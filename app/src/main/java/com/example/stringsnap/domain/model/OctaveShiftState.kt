package com.example.stringsnap.domain.model

/**
 * Tracks octave shift state for intonation mode.
 * When intonation mode is active, all strings are shifted up by 1 octave.
 * This is non-destructive and resets when intonation mode is deactivated.
 *
 * @param isActive Whether intonation mode is currently active
 */
data class OctaveShiftState(
    val isActive: Boolean = false
) {
    fun toggle(): OctaveShiftState = copy(isActive = !isActive)

    companion object {
        val EMPTY = OctaveShiftState(false)
    }
}

