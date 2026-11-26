package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AudioMixSettings
import com.example.ui.theme.EmeraldCue
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SunnahGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun AudioTab(
    audioMix: AudioMixSettings,
    onPickCustomAudio: () -> Unit,
    onUseBuiltInAmbient: () -> Unit,
    onUpdateAudioMix: ((AudioMixSettings) -> AudioMixSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Track Selection Card
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, StudioOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emotional Background Audio",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Track: ${audioMix.bgMusicTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (audioMix.enableBgMusic) SunnahGold else TextSecondary
                        )
                    }
                    Switch(
                        checked = audioMix.enableBgMusic,
                        onCheckedChange = { enabled ->
                            onUpdateAudioMix { it.copy(enableBgMusic = enabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioObsidian,
                            checkedTrackColor = SunnahGold
                        ),
                        modifier = Modifier.testTag("bg_music_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onUseBuiltInAmbient,
                        border = BorderStroke(
                            1.dp,
                            if (audioMix.useBuiltInAmbient) EmeraldCue else StudioOutline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (audioMix.useBuiltInAmbient) EmeraldCue else TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("builtin_ambient_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Built-in ambient pad",
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ambient Pad")
                    }

                    OutlinedButton(
                        onClick = onPickCustomAudio,
                        border = BorderStroke(
                            1.dp,
                            if (!audioMix.useBuiltInAmbient) SunnahGold else StudioOutline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (!audioMix.useBuiltInAmbient) SunnahGold else TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = "Pick audio file",
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Audio")
                    }
                }
            }
        }

        // Slowed + Reverb DSP Card
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, StudioOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Slowed + Reverb Processor",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Real-time pitch drop + warm stereo comb reverb tail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = audioMix.slowedAndReverb,
                        enabled = audioMix.enableBgMusic,
                        onCheckedChange = { enabled ->
                            onUpdateAudioMix { it.copy(slowedAndReverb = enabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioObsidian,
                            checkedTrackColor = EmeraldCue
                        ),
                        modifier = Modifier.testTag("slowed_reverb_switch")
                    )
                }

                // Slowed Speed Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Slowed Speed & Pitch",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = String.format("%.2fx", audioMix.slowedSpeed),
                            style = MaterialTheme.typography.labelMedium,
                            color = SunnahGold
                        )
                    }
                    Slider(
                        value = audioMix.slowedSpeed,
                        enabled = audioMix.enableBgMusic && audioMix.slowedAndReverb,
                        onValueChange = { value ->
                            onUpdateAudioMix { it.copy(slowedSpeed = value) }
                        },
                        valueRange = 0.78f..1.00f,
                        colors = SliderDefaults.colors(
                            thumbColor = SunnahGold,
                            activeTrackColor = SunnahGold
                        ),
                        modifier = Modifier.testTag("slowed_speed_slider")
                    )
                }

                // Reverb Depth Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Reverb Atmosphere Tail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${(audioMix.reverbWetLevel * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldCue
                        )
                    }
                    Slider(
                        value = audioMix.reverbWetLevel,
                        enabled = audioMix.enableBgMusic && audioMix.slowedAndReverb,
                        onValueChange = { value ->
                            onUpdateAudioMix { it.copy(reverbWetLevel = value) }
                        },
                        valueRange = 0f..0.85f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldCue,
                            activeTrackColor = EmeraldCue
                        ),
                        modifier = Modifier.testTag("reverb_depth_slider")
                    )
                }
            }
        }

        // Volume Balance Mixer Card
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, StudioOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Volume Balance Mixer",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Original Video Audio (Speech)", color = TextPrimary)
                        Text(
                            text = "${(audioMix.originalVolume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                    Slider(
                        value = audioMix.originalVolume,
                        onValueChange = { vol ->
                            onUpdateAudioMix { it.copy(originalVolume = vol) }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("original_volume_slider")
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Background Emotional Music", color = TextPrimary)
                        Text(
                            text = "${(audioMix.bgMusicVolume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = SunnahGold
                        )
                    }
                    Slider(
                        value = audioMix.bgMusicVolume,
                        enabled = audioMix.enableBgMusic,
                        onValueChange = { vol ->
                            onUpdateAudioMix { it.copy(bgMusicVolume = vol) }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = SunnahGold,
                            activeTrackColor = SunnahGold
                        ),
                        modifier = Modifier.testTag("bg_volume_slider")
                    )
                }
            }
        }
    }
}
