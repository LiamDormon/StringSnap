package com.example.stringsnap.ui.tuner

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stringsnap.domain.model.StringSelectionMode
import com.example.stringsnap.domain.model.TunerUiState
import com.example.stringsnap.features.tuner.TunerViewModel
import kotlin.math.roundToInt

object TunerScreen {
    @Composable
    fun Content(
        state: TunerUiState,
        onStringSelectionModeChange: (StringSelectionMode) -> Unit,
        onStringSelected: (Int) -> Unit,
        onIntonationModeToggle: () -> Unit,
        onOctaveShiftChange: (Int, Int) -> Unit,
        onPresetChange: (String) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val background = Color(0xFF101214)
        val panel = Color(0xFF1B1F23)
        val accent = if (state.isInTune) Color(0xFF4CAF7D) else Color(0xFFE0B44D)
        val hasSignal = state.detectedFrequency != null
        val pitchHint = pitchHint(state.centOffset, state.isInTune)

        Surface(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Preset picker - full width at top
                PresetPicker(
                    currentPreset = state.preset.name,
                    onPresetChange = onPresetChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Top detected note: show only the note name and accidental (no octave)
                        val topNoteLabel = state.detectedNote?.let { note ->
                            buildString {
                                append(note.name)
                                note.accidental?.let { append(it) }
                            }
                        } ?: "--"
                        Text(
                            text = topNoteLabel,
                            color = Color.White,
                            fontSize = 76.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        text = state.detectedFrequency?.let { "%.2f Hz".format(it) } ?: "",
                        color = Color(0xFF9AA5AD),
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.heightIn(min = 54.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = if (hasSignal) pitchHint.headline else "",
                            color = pitchHint.color,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (hasSignal) pitchHint.instruction else "",
                            color = Color(0xFFCED6DC),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                ModeSelector(
                    selectedMode = state.stringSelectionMode,
                    onModeSelected = onStringSelectionModeChange,
                    intonationModeActive = state.octaveShiftState.isActive,
                    onIntonationModeToggle = onIntonationModeToggle
                )

                PitchMeter(
                    cents = state.centOffset,
                    accent = accent,
                    hasSignal = hasSignal,
                    targetFrequencyHz = state.activeStringIndex?.let { index ->
                        val string = state.preset.strings.getOrNull(index)
                        if (string != null && state.octaveShiftState.isActive) string.baseFrequencyHz * 2.0 else string?.baseFrequencyHz
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(panel, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Strings", color = Color(0xFFCED6DC), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when {
                                state.isInTune -> "In tune"
                                hasSignal -> "Tuning"
                                else -> ""
                            },
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        state.preset.strings.forEachIndexed { index, string ->
                            val active = index == state.activeStringIndex
                            val modeLabel = if (state.stringSelectionMode == StringSelectionMode.Manual && active) {
                                "Locked"
                            } else {
                                // If intonation mode is active, show the shifted note name for the string
                                if (state.octaveShiftState.isActive) {
                                    string.note.shiftOctave(1).toString()
                                } else {
                                    string.label
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .semantics {
                                            contentDescription = "Select ${string.label}"
                                        }
                                        .clickable(role = Role.Button) {
                                            onStringSelected(index)
                                        }
                                        .background(
                                            if (active) accent else Color(0xFF2A3035),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = string.note.name,
                                        color = if (active) Color(0xFF101214) else Color(0xFFCED6DC),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(modeLabel, color = Color(0xFF9AA5AD), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModeSelector(
        selectedMode: StringSelectionMode,
        onModeSelected: (StringSelectionMode) -> Unit,
        intonationModeActive: Boolean,
        onIntonationModeToggle: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeButton(
                label = "Auto",
                selected = selectedMode == StringSelectionMode.Auto,
                onClick = { onModeSelected(StringSelectionMode.Auto) },
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                label = "Manual",
                selected = selectedMode == StringSelectionMode.Manual,
                onClick = { onModeSelected(StringSelectionMode.Manual) },
                modifier = Modifier.weight(1f)
            )
            IntonationModeIconButton(
                active = intonationModeActive,
                onClick = onIntonationModeToggle,
                modifier = Modifier.size(48.dp)
            )
        }
    }

    @Composable
    private fun ModeButton(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) Color(0xFFE0B44D) else Color(0xFF2A3035),
                contentColor = if (selected) Color(0xFF101214) else Color(0xFFCED6DC)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun PresetPicker(
        currentPreset: String,
        onPresetChange: (String) -> Unit,
        modifier: Modifier = Modifier
    ) {
        var expanded by remember { mutableStateOf(false) }
        val presets = listOf(
            "E Standard", "Drop D", "Open G", "Open D", "Open E",
            "DADGAD", "Drop C", "Half Step Down", "Full Step Down"
        )

        Box(modifier = modifier) {
            Button(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A3035),
                    contentColor = Color(0xFFCED6DC)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(currentPreset, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color(0xFF1B1F23))
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                preset,
                                color = if (preset == currentPreset) Color(0xFFE0B44D) else Color(0xFFCED6DC)
                            )
                        },
                        onClick = {
                            onPresetChange(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun IntonationModeIconButton(
        active: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Color(0xFFF5C842) else Color(0xFF2A3035),
                contentColor = if (active) Color(0xFF101214) else Color(0xFFCED6DC)
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = if (active) "Intonation mode on" else "Intonation mode off",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    private fun pitchHint(cents: Double, isInTune: Boolean): PitchHint {
        return when {
            isInTune -> PitchHint(
                headline = "Perfect",
                instruction = "Hold it steady.",
                color = Color(0xFF4CAF7D)
            )
            cents < 0.0 -> PitchHint(
                headline = "A little flat",
                instruction = "Tune up: tighten the string.",
                color = Color(0xFFE0B44D)
            )
            cents > 0.0 -> PitchHint(
                headline = "A little sharp",
                instruction = "Tune down: loosen the string.",
                color = Color(0xFFFF6B5E)
            )
            else -> PitchHint(
                headline = "Find the note",
                instruction = "Pluck a string and let it ring.",
                color = Color(0xFFCED6DC)
            )
        }
    }

    private fun signedCents(cents: Double): String {
        val rounded = cents.roundToInt()
        return when {
            rounded > 0 -> "+${rounded}"
            rounded < 0 -> "${rounded}"
            else -> "0"
        }
    }

    private data class PitchHint(
        val headline: String,
        val instruction: String,
        val color: Color
    )

    @Composable
    private fun PitchMeter(cents: Double, accent: Color, hasSignal: Boolean, targetFrequencyHz: Double?) {
        val clamped = cents.coerceIn(-50.0, 50.0).toFloat()
        val animated by animateFloatAsState(
            targetValue = clamped,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "pitch-offset"
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val centerX = widthPx * 0.5f
            val endX = widthPx * 0.92f
            val needleX = centerX + (animated / 50f) * (endX - centerX)
            val labelWidthPx = with(density) { 70.dp.toPx() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("♭", color = Color(0xFF8FB5FF), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("♯", color = Color(0xFFFF8C7A), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height * 0.68f
                val startX = size.width * 0.08f
                val endX = size.width * 0.92f
                val centerX = size.width * 0.5f
                drawLine(
                    color = Color(0xFF3B434A),
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
                for (tick in -50..50 step 10) {
                    val x = centerX + (tick / 50f) * (endX - centerX)
                    val height = if (tick == 0) 44f else 24f
                    drawLine(
                        color = if (tick == 0) Color.White else Color(0xFF69747D),
                        start = Offset(x, centerY - height / 2f),
                        end = Offset(x, centerY + height / 2f),
                        strokeWidth = if (tick == 0) 5f else 3f,
                        cap = StrokeCap.Round
                    )
                }

                val needleX = centerX + (animated / 50f) * (endX - centerX)
                drawLine(
                    color = accent,
                    start = Offset(needleX, centerY - 70f),
                    end = Offset(needleX, centerY + 42f),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
            }
            if (hasSignal) {
                Text(
                    text = signedCents(cents),
                    color = Color(0xFF101214),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(
                                x = (needleX - labelWidthPx / 2f).roundToInt(),
                                y = 42.dp.roundToPx()
                            )
                        }
                        .width(70.dp)
                        .background(accent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            // Center tick target frequency label
            targetFrequencyHz?.let { targetHz ->
                Text(
                    text = "Target %.2f Hz".format(targetHz),
                    color = Color(0xFF9AA5AD),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-18).dp)
                )
            }
        }
    }

    fun renderText(viewModel: TunerViewModel): String {
        val state = viewModel.uiState
        val s = state.value
        val note = s.detectedNote?.toString() ?: "--"
        val freq = s.detectedFrequency?.let { String.format("%.2f Hz", it) } ?: "--"
        val cents = String.format("%.1f cents", s.centOffset)
        val active = s.activeStringIndex?.toString() ?: "-"
        return "Note: $note | Freq: $freq | Offset: $cents | ActiveString: $active"
    }
}
