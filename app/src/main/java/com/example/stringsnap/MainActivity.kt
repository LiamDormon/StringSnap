package com.example.stringsnap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.stringsnap.audio.pitch.TarsosPitchDetector
import com.example.stringsnap.audio.pitch.YinPitchDetector
import com.example.stringsnap.features.tuner.TunerViewModel
import com.example.stringsnap.ui.tuner.TunerScreen

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: TunerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val detector = YinPitchDetector()
        viewModel = TunerViewModel(detector)

        setContent {
            MaterialTheme {
                var hasAudioPermission by remember {
                    mutableStateOf(hasRecordAudioPermission())
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasAudioPermission = granted
                }
                val state by viewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    if (!hasAudioPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                LaunchedEffect(hasAudioPermission) {
                    if (hasAudioPermission) {
                        viewModel.start()
                    } else {
                        viewModel.stop()
                    }
                }
                DisposableEffect(Unit) {
                    onDispose { viewModel.stop() }
                }

                TunerScreen.Content(
                    state = state,
                    onStringSelectionModeChange = viewModel::setStringSelectionMode,
                    onStringSelected = viewModel::selectManualString,
                    onIntonationModeToggle = viewModel::toggleIntonationMode,
                    onOctaveShiftChange = { _, _ -> }, // No-op: simplified intonation mode
                    onPresetChange = viewModel::changePreset
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stop()
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

