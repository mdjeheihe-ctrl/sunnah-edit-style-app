package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.EditProject
import com.example.model.ExportQuality
import com.example.model.ExportState
import com.example.ui.theme.CrimsonCue
import com.example.ui.theme.CrimsonCueContainer
import com.example.ui.theme.EmeraldCue
import com.example.ui.theme.EmeraldCueContainer
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SunnahGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExportTab(
    project: EditProject,
    exportState: ExportState,
    onSelectQuality: (ExportQuality) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShareFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Export Quality & Memory-Safe Mode Selector
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, StudioOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "9:16 Vertical Render Profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Media3 Transformer streams trimmed frames directly through hardware codecs without loading raw videos into RAM.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                ExportQuality.entries.forEach { quality ->
                    val selected = project.exportQuality == quality
                    Surface(
                        color = if (selected) StudioSurfaceElevated else StudioSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) SunnahGold else StudioOutline
                        ),
                        onClick = { onSelectQuality(quality) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quality_${quality.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectQuality(quality) },
                                colors = RadioButtonDefaults.colors(selectedColor = SunnahGold)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = quality.bitrateHint,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pre-flight Composition Summary
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, StudioOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Composition Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = SunnahGold
                )
                SummaryRow("Target Duration", "${project.targetOutputDurationMs / 1000}s (30–40s sweet spot)")
                SummaryRow("Active Beats", "${project.enabledSegments.size} trimmed clips")
                SummaryRow("Captions", "${project.captions.size} timed overlays")
                SummaryRow(
                    "Audio Mix",
                    if (project.audioMix.enableBgMusic) {
                        if (project.audioMix.slowedAndReverb) "Slowed ${String.format("%.2fx", project.audioMix.slowedSpeed)} + Reverb" else "Standard BG Mix"
                    } else {
                        "Original Video Audio Only"
                    }
                )
            }
        }

        // Render Progress / Result Card
        when (exportState) {
            is ExportState.Idle -> {
                Button(
                    onClick = onStartExport,
                    enabled = project.sourceVideoUri != null && project.enabledSegments.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunnahGold,
                        contentColor = StudioObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("export_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MovieCreation,
                        contentDescription = "Export MP4",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export 9:16 MP4 Reel", style = MaterialTheme.typography.titleSmall)
                }
            }

            is ExportState.Exporting -> {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SunnahGold.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = exportState.stage,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "${exportState.progressPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = SunnahGold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            color = SunnahGold,
                            trackColor = StudioSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        OutlinedButton(
                            onClick = onCancelExport,
                            border = BorderStroke(1.dp, CrimsonCue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel export",
                                tint = CrimsonCue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel Export", color = CrimsonCue)
                        }
                    }
                }
            }

            is ExportState.Completed -> {
                Surface(
                    color = EmeraldCueContainer,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, EmeraldCue)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Export complete",
                                tint = EmeraldCue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "9:16 Reel Rendered",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldCue
                            )
                        }
                        val mb = exportState.outputSizeBytes / (1024f * 1024f)
                        Text(
                            text = "Duration: ${exportState.durationMs / 1000}s • Size: ${String.format("%.1f MB", mb)}" +
                                if (exportState.savedGalleryUri != null) "\nSaved to Movies/NoorCut" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onShareFile(exportState.outputFilePath) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldCue,
                                    contentColor = StudioObsidian
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_export_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share exported MP4",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Reel")
                            }
                            OutlinedButton(
                                onClick = onStartExport,
                                border = BorderStroke(1.dp, EmeraldCue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Re-Export", color = EmeraldCue)
                            }
                        }
                    }
                }
            }

            is ExportState.Error -> {
                Surface(
                    color = CrimsonCueContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CrimsonCue)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonCue
                        )
                        Button(
                            onClick = onStartExport,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SunnahGold,
                                contentColor = StudioObsidian
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry Export")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}
