package com.example.stringsnap.features.tuner

import androidx.lifecycle.ViewModel
import com.example.stringsnap.audio.pitch.PitchDetector
import com.example.stringsnap.data.presets.PresetsRepository
import com.example.stringsnap.data.settings.SettingsRepository
import com.example.stringsnap.domain.NoteUtils
import com.example.stringsnap.domain.model.OctaveShiftState
import com.example.stringsnap.domain.model.StringSelectionMode
import com.example.stringsnap.domain.model.TunerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Basic ViewModel that wires a PitchDetector to UI state.
 * For now it uses a fake preset repository and settings repository.
 */
class TunerViewModel(
    private val detector: PitchDetector,
    private val presetsRepo: PresetsRepository = PresetsRepository,
    private val settings: SettingsRepository = SettingsRepository(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private var pendingAutoStringIndex: Int? = null
    private var pendingAutoStringFrames: Int = 0
    private var lastRelevantSignalAtMs: Long? = null

    private val _uiState = MutableStateFlow<TunerUiState>(
        TunerUiState(
            detectedNote = null,
            detectedFrequency = null,
            centOffset = 0.0,
            activeStringIndex = null,
            stringSelectionMode = StringSelectionMode.Auto,
            manualStringIndex = null,
            confidence = 0.0f,
            isInTune = false,
            preset = presetsRepo.E_STANDARD,
            intonationModeActive = false,
            octaveShiftState = OctaveShiftState.EMPTY
        )
    )
    val uiState: StateFlow<TunerUiState> = _uiState

    private val listener = object : PitchDetector.Listener {
        override fun onPitchDetected(frequencyHz: Double, confidence: Float) {
            handleDetection(frequencyHz, confidence)
        }
        override fun onSilence() {
            handleMissedSignal()
        }
    }

    init {
        detector.setListener(listener)
    }

    fun start() {
        detector.start()
    }

    fun stop() {
        detector.stop()
    }

    fun setStringSelectionMode(mode: StringSelectionMode) {
        val state = _uiState.value
        val manualIndex = when (mode) {
            StringSelectionMode.Auto -> null
            StringSelectionMode.Manual -> state.manualStringIndex ?: state.activeStringIndex ?: 0
        }
        _uiState.value = state.copy(
            stringSelectionMode = mode,
            manualStringIndex = manualIndex,
            activeStringIndex = if (mode == StringSelectionMode.Manual) manualIndex else state.activeStringIndex
        )
        recomputeCurrentTarget()
    }

    fun selectManualString(index: Int) {
        val preset = _uiState.value.preset
        if (index !in preset.strings.indices) return
        _uiState.value = _uiState.value.copy(
            stringSelectionMode = StringSelectionMode.Manual,
            manualStringIndex = index,
            activeStringIndex = index
        )
        recomputeCurrentTarget()
    }

    fun toggleIntonationMode() {
        val state = _uiState.value
        val newShiftState = state.octaveShiftState.toggle()
        _uiState.value = state.copy(octaveShiftState = newShiftState)
        recomputeCurrentTarget()
    }

    fun changePreset(presetName: String) {
        val preset = presetsRepo.ALL.find { it.name == presetName } ?: return
        val state = _uiState.value
        _uiState.value = state.copy(
            preset = preset,
            manualStringIndex = null,
            activeStringIndex = null
        )
        recomputeCurrentTarget()
    }

    override fun onCleared() {
        detector.stop()
        super.onCleared()
    }

    private fun getTargetFrequencyForString(stringIndex: Int): Double {
        val state = _uiState.value
        val baseFrequency = state.preset.strings[stringIndex].baseFrequencyHz
        return if (state.octaveShiftState.isActive) {
            baseFrequency * 2.0  // Shift up 1 octave
        } else {
            baseFrequency
        }
    }

    private fun handleDetection(frequencyHz: Double, confidence: Float) {
        if (confidence < settings.minimumPitchConfidence) {
            handleMissedSignal()
            return
        }

        val (note, cents) = NoteUtils.frequencyToNearestNote(frequencyHz, settings.concertPitchA4)
        val preset = _uiState.value.preset
        val currentState = _uiState.value

        val closest = preset.strings.indices
            .map { idx ->
                val targetFreq = getTargetFrequencyForString(idx)
                idx to NoteUtils.centsBetween(frequencyHz, targetFreq)
            }
            .minByOrNull { (_, stringCents) -> abs(stringCents) }

        if (currentState.stringSelectionMode == StringSelectionMode.Auto) {
            val closestCents = closest?.second
            if (closestCents == null || abs(closestCents) > settings.autoStringDetectionWindowCents) {
                handleMissedSignal()
                return
            }
        }
        val guessedIndex = closest?.first
        val activeIndex = when (currentState.stringSelectionMode) {
            StringSelectionMode.Auto -> debouncedAutoStringIndex(guessedIndex)
            StringSelectionMode.Manual -> currentState.manualStringIndex ?: guessedIndex
        }
        val targetCents = activeIndex?.let { idx ->
            val targetFreq = getTargetFrequencyForString(idx)
            NoteUtils.centsBetween(frequencyHz, targetFreq)
        } ?: cents
        if (
            currentState.stringSelectionMode == StringSelectionMode.Manual &&
            abs(targetCents) > settings.manualStringDetectionWindowCents
        ) {
            handleMissedSignal()
            return
        }
        val isInTune = abs(targetCents) <= settings.inTuneThresholdCents
        lastRelevantSignalAtMs = currentTimeMillis()

        _uiState.value = _uiState.value.copy(
            detectedNote = note,
            detectedFrequency = frequencyHz,
            centOffset = targetCents,
            activeStringIndex = activeIndex,
            confidence = confidence,
            isInTune = isInTune
        )
    }

    private fun recomputeCurrentTarget() {
        val frequencyHz = _uiState.value.detectedFrequency ?: return
        handleDetection(frequencyHz, _uiState.value.confidence)
    }

    private fun debouncedAutoStringIndex(guessedIndex: Int?): Int? {
        if (guessedIndex == null) return null
        val currentActive = _uiState.value.activeStringIndex
        if (currentActive == null || currentActive == guessedIndex) {
            pendingAutoStringIndex = guessedIndex
            pendingAutoStringFrames = settings.detectionDebounceFrames
            return guessedIndex
        }
        if (pendingAutoStringIndex == guessedIndex) {
            pendingAutoStringFrames += 1
        } else {
            pendingAutoStringIndex = guessedIndex
            pendingAutoStringFrames = 1
        }
        return if (pendingAutoStringFrames >= settings.detectionDebounceFrames) {
            guessedIndex
        } else {
            currentActive
        }
    }

    private fun handleMissedSignal() {
        val lastRelevantSignalAt = lastRelevantSignalAtMs
        if (_uiState.value.detectedFrequency == null || lastRelevantSignalAt == null) {
            clearDetection()
            return
        }
        if (currentTimeMillis() - lastRelevantSignalAt > settings.signalLostGracePeriodMs) {
            clearDetection()
        }
    }

    private fun clearDetection() {
        pendingAutoStringIndex = null
        pendingAutoStringFrames = 0
        lastRelevantSignalAtMs = null
        val state = _uiState.value
        _uiState.value = state.copy(
            detectedNote = null,
            detectedFrequency = null,
            centOffset = 0.0,
            activeStringIndex = if (state.stringSelectionMode == StringSelectionMode.Manual) {
                state.manualStringIndex
            } else {
                null
            },
            confidence = 0.0f,
            isInTune = false
        )
    }
}





