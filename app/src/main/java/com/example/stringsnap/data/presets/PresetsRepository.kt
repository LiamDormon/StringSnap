package com.example.stringsnap.data.presets

import com.example.stringsnap.domain.model.Note
import com.example.stringsnap.domain.model.TargetString
import com.example.stringsnap.domain.model.TuningPreset

/**
 * Provides a small set of standard presets for initial development.
 */
object PresetsRepository {
    private fun note(name: String, octave: Int): Note {
        return if (name.length == 2 && name[1] == '#') {
            Note(name[0].toString(), "#", octave)
        } else {
            Note(name, null, octave)
        }
    }

    // Frequencies are approximate for standard A4=440
    val E_STANDARD = TuningPreset(
        "E Standard",
        listOf(
            TargetString("E2", note("E", 2), 82.41),
            TargetString("A2", note("A", 2), 110.0),
            TargetString("D3", note("D", 3), 146.83),
            TargetString("G3", note("G", 3), 196.0),
            TargetString("B3", note("B", 3), 246.94),
            TargetString("E4", note("E", 4), 329.63)
        )
    )

    val DROP_D = TuningPreset(
        "Drop D",
        listOf(
            TargetString("D2", note("D", 2), 73.42),
            TargetString("A2", note("A", 2), 110.0),
            TargetString("D3", note("D", 3), 146.83),
            TargetString("G3", note("G", 3), 196.0),
            TargetString("B3", note("B", 3), 246.94),
            TargetString("E4", note("E", 4), 329.63)
        )
    )

    val OPEN_G = TuningPreset(
        "Open G",
        listOf(
            TargetString("D2", note("D", 2), 73.42),
            TargetString("G2", note("G", 2), 98.0),
            TargetString("D3", note("D", 3), 146.83),
            TargetString("G3", note("G", 3), 196.0),
            TargetString("B3", note("B", 3), 246.94),
            TargetString("D4", note("D", 4), 293.66)
        )
    )

    val OPEN_D = TuningPreset(
        "Open D",
        listOf(
            TargetString("D2", note("D", 2), 73.42),
            TargetString("A2", note("A", 2), 110.0),
            TargetString("D3", note("D", 3), 146.83),
            TargetString("F#3", note("F#", 3), 185.0),
            TargetString("A3", note("A", 3), 220.0),
            TargetString("D4", note("D", 4), 293.66)
        )
    )

    val OPEN_E = TuningPreset(
        "Open E",
        listOf(
            TargetString("E2", note("E", 2), 82.41),
            TargetString("B2", note("B", 2), 123.47),
            TargetString("E3", note("E", 3), 164.81),
            TargetString("G#3", note("G#", 3), 207.65),
            TargetString("B3", note("B", 3), 246.94),
            TargetString("E4", note("E", 4), 329.63)
        )
    )

    val DADGAD = TuningPreset(
        "DADGAD",
        listOf(
            TargetString("D2", note("D", 2), 73.42),
            TargetString("A2", note("A", 2), 110.0),
            TargetString("D3", note("D", 3), 146.83),
            TargetString("G3", note("G", 3), 196.0),
            TargetString("A3", note("A", 3), 220.0),
            TargetString("D4", note("D", 4), 293.66)
        )
    )

    val DROP_C = TuningPreset(
        "Drop C",
        listOf(
            TargetString("C2", note("C", 2), 65.41),
            TargetString("G2", note("G", 2), 98.0),
            TargetString("C3", note("C", 3), 130.81),
            TargetString("F3", note("F", 3), 174.61),
            TargetString("A3", note("A", 3), 220.0),
            TargetString("D4", note("D", 4), 293.66)
        )
    )

    val HALF_STEP_DOWN = TuningPreset(
        "Half Step Down",
        listOf(
            TargetString("Eb2", note("D#", 2), 77.78),
            TargetString("Ab2", note("G#", 2), 103.83),
            TargetString("Db3", note("C#", 3), 138.59),
            TargetString("Gb3", note("F#", 3), 185.0),
            TargetString("Bb3", note("A#", 3), 233.08),
            TargetString("Eb4", note("D#", 4), 311.13)
        )
    )

    val FULL_STEP_DOWN = TuningPreset(
        "Full Step Down",
        listOf(
            TargetString("D2", note("D", 2), 73.42),
            TargetString("G2", note("G", 2), 98.0),
            TargetString("C3", note("C", 3), 130.81),
            TargetString("F3", note("F", 3), 174.61),
            TargetString("A3", note("A", 3), 220.0),
            TargetString("D4", note("D", 4), 293.66)
        )
    )

    val ALL = listOf(
        E_STANDARD,
        DROP_D,
        OPEN_G,
        OPEN_D,
        OPEN_E,
        DADGAD,
        DROP_C,
        HALF_STEP_DOWN,
        FULL_STEP_DOWN
    )
}

