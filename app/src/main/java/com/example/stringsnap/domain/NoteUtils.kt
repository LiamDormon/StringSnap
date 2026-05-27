package com.example.stringsnap.domain

import com.example.stringsnap.domain.model.Note
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Utilities to convert between frequency (Hz), MIDI note number, and cents.
 * Uses A4 = 440Hz by default but allows overriding.
 */
object NoteUtils {
    private const val DEFAULT_A4_FREQ = 440.0
    private const val A4_MIDI = 69

    fun frequencyToMidi(frequencyHz: Double, a4: Double = DEFAULT_A4_FREQ): Double {
        require(frequencyHz > 0.0) { "frequencyHz must be positive" }
        require(a4 > 0.0) { "a4 must be positive" }
        return (12.0 * log2(frequencyHz / a4)) + A4_MIDI
    }

    fun midiToFrequency(midi: Double, a4: Double = DEFAULT_A4_FREQ): Double {
        require(a4 > 0.0) { "a4 must be positive" }
        return a4 * 2.0.pow((midi - A4_MIDI) / 12.0)
    }

    fun centsBetween(frequencyHz: Double, targetFrequencyHz: Double): Double {
        require(frequencyHz > 0.0) { "frequencyHz must be positive" }
        require(targetFrequencyHz > 0.0) { "targetFrequencyHz must be positive" }
        return 1200.0 * log2(frequencyHz / targetFrequencyHz)
    }

    fun midiToNoteName(midi: Int): Note {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val name = names[midi % 12]
        val octave = (midi / 12) - 1
        val accidental = if (name.length == 2) "#" else null
        val baseName = name.first().toString()
        return Note(baseName, accidental, octave)
    }

    fun frequencyToNearestNote(frequencyHz: Double, a4: Double = DEFAULT_A4_FREQ): Pair<Note, Double> {
        val midi = frequencyToMidi(frequencyHz, a4)
        val nearest = midi.roundToInt()
        val note = midiToNoteName(nearest)
        val cents = (midi - nearest) * 100.0
        return Pair(note, cents)
    }
}
