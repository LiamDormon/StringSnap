package com.example.stringsnap.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteUtilsTest {
    @Test
    fun frequencyToMidi_mapsConcertPitchToA4() {
        assertEquals(69.0, NoteUtils.frequencyToMidi(440.0), 0.0001)
    }

    @Test
    fun midiToFrequency_mapsA4ToConcertPitch() {
        assertEquals(440.0, NoteUtils.midiToFrequency(69.0), 0.0001)
    }

    @Test
    fun frequencyToNearestNote_returnsNoteAndCentOffset() {
        val (note, cents) = NoteUtils.frequencyToNearestNote(445.0)

        assertEquals("A4", note.toString())
        assertEquals(19.56, cents, 0.05)
    }

    @Test
    fun centsBetween_returnsPositiveWhenSharp() {
        assertEquals(100.0, NoteUtils.centsBetween(466.1637615, 440.0), 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun frequencyToMidi_rejectsNonPositiveFrequency() {
        NoteUtils.frequencyToMidi(0.0)
    }
}
