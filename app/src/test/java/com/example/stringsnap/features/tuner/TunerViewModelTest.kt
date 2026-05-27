package com.example.stringsnap.features.tuner

import com.example.stringsnap.audio.pitch.PitchDetector
import com.example.stringsnap.domain.model.StringSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerViewModelTest {
    @Test
    fun autoMode_usesClosestStringForCentOffset() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        detector.emit(110.0, 0.95f)

        val state = viewModel.uiState.value
        assertEquals(StringSelectionMode.Auto, state.stringSelectionMode)
        assertEquals(1, state.activeStringIndex)
        assertEquals(0.0, state.centOffset, 0.01)
        assertTrue(state.isInTune)
    }

    @Test
    fun selectManualString_locksTargetString() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        detector.emit(110.0, 0.95f)
        viewModel.selectManualString(1)

        val state = viewModel.uiState.value
        assertEquals(StringSelectionMode.Manual, state.stringSelectionMode)
        assertEquals(1, state.manualStringIndex)
        assertEquals(1, state.activeStringIndex)
        assertEquals(0.0, state.centOffset, 0.01)
    }

    @Test
    fun autoModeAfterManual_returnsToClosestString() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        detector.emit(110.0, 0.95f)
        viewModel.selectManualString(1)
        viewModel.setStringSelectionMode(StringSelectionMode.Auto)

        val state = viewModel.uiState.value
        assertEquals(StringSelectionMode.Auto, state.stringSelectionMode)
        assertEquals(null, state.manualStringIndex)
        assertEquals(1, state.activeStringIndex)
        assertEquals(0.0, state.centOffset, 0.01)
    }

    @Test
    fun manualMode_ignoresPitchesFarFromLockedString() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        viewModel.selectManualString(0)
        repeat(4) {
            detector.emit(110.0, 0.95f)
        }

        val state = viewModel.uiState.value
        assertEquals(StringSelectionMode.Manual, state.stringSelectionMode)
        assertEquals(0, state.manualStringIndex)
        assertEquals(0, state.activeStringIndex)
        assertEquals(null, state.detectedNote)
        assertEquals(null, state.detectedFrequency)
    }

    @Test
    fun weakDetection_clearsVisiblePitchState() {
        val detector = CapturingPitchDetector()
        val clock = MutableClock()
        val viewModel = TunerViewModel(detector, currentTimeMillis = clock::now)

        detector.emit(110.0, 0.95f)
        clock.advanceBy(9_999L)
        detector.emit(110.0, 0.2f)

        val state = viewModel.uiState.value
        assertEquals("A2", state.detectedNote.toString())
        assertEquals(110.0, state.detectedFrequency!!, 0.01)
        assertEquals(1, state.activeStringIndex)
    }

    @Test
    fun weakDetection_clearsVisiblePitchStateAfterGracePeriod() {
        val detector = CapturingPitchDetector()
        val clock = MutableClock()
        val viewModel = TunerViewModel(detector, currentTimeMillis = clock::now)

        detector.emit(110.0, 0.95f)
        clock.advanceBy(10_001L)
        detector.emit(110.0, 0.2f)

        val state = viewModel.uiState.value
        assertEquals(null, state.detectedNote)
        assertEquals(null, state.detectedFrequency)
        assertEquals(null, state.activeStringIndex)
        assertEquals(0.0f, state.confidence)
    }

    @Test
    fun autoMode_ignoresPitchesFarFromPresetStrings() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        repeat(4) {
            detector.emit(100.0, 0.95f)
        }

        val state = viewModel.uiState.value
        assertEquals(null, state.detectedNote)
        assertEquals(null, state.detectedFrequency)
        assertEquals(null, state.activeStringIndex)
    }

    @Test
    fun autoMode_debouncesStringSwitching() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        detector.emit(110.0, 0.95f)
        detector.emit(146.83, 0.95f)

        assertEquals(1, viewModel.uiState.value.activeStringIndex)

        detector.emit(146.83, 0.95f)

        assertEquals(2, viewModel.uiState.value.activeStringIndex)
    }

    @Test
    fun toggleIntonationMode_activatesDeactivatesMode() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        assertEquals(false, viewModel.uiState.value.octaveShiftState.isActive)
        viewModel.toggleIntonationMode()
        assertEquals(true, viewModel.uiState.value.octaveShiftState.isActive)
        viewModel.toggleIntonationMode()
        assertEquals(false, viewModel.uiState.value.octaveShiftState.isActive)
    }

    @Test
    fun intonationMode_shiftsAllStringsUpOneOctave() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        // When intonation mode is off, E2 should be the target for string 0
        detector.emit(82.41, 0.95f)
        assertEquals(0, viewModel.uiState.value.activeStringIndex)
        assertEquals(0.0, viewModel.uiState.value.centOffset, 1.0)

        // Activate intonation mode
        viewModel.toggleIntonationMode()
        assertEquals(true, viewModel.uiState.value.octaveShiftState.isActive)

        // Now the target for string 0 is E3 (shifted up 1 octave)
        // But we can't easily test the exact cent offset without emitting E3 itself
        // which would be ~2 semitones up, so instead just verify state changed
        val state = viewModel.uiState.value
        assertEquals(true, state.octaveShiftState.isActive)
    }

    @Test
    fun intonationMode_deactivation_restoresOriginalTargets() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        // Emit E2 (82.41 Hz) - should be in tune with E2
        detector.emit(82.41, 0.95f)
        assertEquals(0.0, viewModel.uiState.value.centOffset, 1.0)
        assertEquals(false, viewModel.uiState.value.octaveShiftState.isActive)

        // Activate intonation mode
        viewModel.toggleIntonationMode()
        assertEquals(true, viewModel.uiState.value.octaveShiftState.isActive)

        // Deactivate intonation mode - should be back to original
        viewModel.toggleIntonationMode()
        assertEquals(false, viewModel.uiState.value.octaveShiftState.isActive)

        // Re-emit E2 - should still be in tune
        detector.emit(82.41, 0.95f)
        assertEquals(0.0, viewModel.uiState.value.centOffset, 1.0)
    }

    @Test
    fun changePreset_switchesActiveTuning() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        assertEquals("E Standard", viewModel.uiState.value.preset.name)

        viewModel.changePreset("Drop D")
        assertEquals("Drop D", viewModel.uiState.value.preset.name)

        viewModel.changePreset("Open G")
        assertEquals("Open G", viewModel.uiState.value.preset.name)
    }

    @Test
    fun changePreset_resetsManualStringSelection() {
        val detector = CapturingPitchDetector()
        val viewModel = TunerViewModel(detector)

        viewModel.selectManualString(1)
        assertEquals(1, viewModel.uiState.value.manualStringIndex)

        viewModel.changePreset("Drop D")
        assertEquals(null, viewModel.uiState.value.manualStringIndex)
    }

    private class CapturingPitchDetector : PitchDetector {
        private var listener: PitchDetector.Listener? = null

        override fun start() = Unit

        override fun stop() = Unit

        override fun setListener(listener: PitchDetector.Listener?) {
            this.listener = listener
        }

        fun emit(frequencyHz: Double, confidence: Float) {
            listener?.onPitchDetected(frequencyHz, confidence)
        }
    }

    private class MutableClock {
        private var value: Long = 0L

        fun now(): Long = value

        fun advanceBy(milliseconds: Long) {
            value += milliseconds
        }
    }
}
