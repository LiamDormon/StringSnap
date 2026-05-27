package com.example.stringsnap.domain.model

/**
 * Represents a musical note with name and octave.
 */
data class Note(
    val name: String, // e.g. "E"
    val accidental: String? = null, // "#" or "b" or null
    val octave: Int
) {
    override fun toString(): String = buildString {
        append(name)
        accidental?.let { append(it) }
        append(octave)
    }

    /**
     * Returns a new Note with the octave shifted by the given amount.
     * Used for intonation mode to display shifted note names.
     */
    fun shiftOctave(octaves: Int): Note {
        return copy(octave = this.octave + octaves)
    }
}

