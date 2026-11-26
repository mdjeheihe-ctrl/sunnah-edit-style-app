package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
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
import com.example.model.AiAnalysisState
import com.example.model.CueAccent
import com.example.model.EditProject
import com.example.model.VideoSegment
import com.example.ui.theme.CrimsonCue
import com.example.ui.theme.CrimsonCueContainer
import com.example.ui.theme.EmeraldCue
import com.example.ui.theme.EmeraldCueContainer
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SunnahGold
import com.example.ui.theme.SunnahGoldContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TimelineTab(
    project: EditProject,
    aiState: AiAnalysisState,
    onTopicPromptChange: (String) -> Unit,
    onRunAiDirector: () -> Unit,
    onRunSmartAutoTrim: () -> Unit,
    onToggleSegment: (String) -> Unit,
    onUpdateBounds: (String, Long, Long) -> Unit,
    onUpdateCue: (String, CueAccent) -> Unit,
    onMoveSegment: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AI Director & Smart Auto-Trim Control Card
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sunnah Edit Director",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Distills ${project.sourceDurationMs / 1000}s raw footage into a 30–40s arc",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = project.topicPrompt,
                    onValueChange = onTopicPromptChange,
                    label = { Text("Theme / Story Context (optional)") },
                    placeholder = { Text("e.g. Patience under pressure, helping a stranger") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_topic_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRunAiDirector,
                        enabled = project.sourceVideoUri != null && aiState !is AiAnalysisState.Analyzing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SunnahGold,
                            contentColor = StudioObsidian
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_director_button")
                    ) {
                        if (aiState is AiAnalysisState.Analyzing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = StudioObsidian,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI Auto-Edit",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Auto-Edit", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onRunSmartAutoTrim,
                        enabled = project.sourceVideoUri != null,
                        border = BorderStroke(1.dp, StudioOutline),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("smart_trim_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCut,
                            contentDescription = "Smart Auto-Trim",
                            tint = EmeraldCue,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Smart 34s Cut", color = TextPrimary)
                    }
                }

                when (aiState) {
                    is AiAnalysisState.Analyzing -> {
                        Text(
                            text = aiState.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnahGold
                        )
                    }
                    is AiAnalysisState.Success -> {
                        Surface(
                            color = EmeraldCueContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = aiState.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldCue,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is AiAnalysisState.Error -> {
                        Surface(
                            color = CrimsonCueContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = aiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CrimsonCue,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    AiAnalysisState.Idle -> {
                        if (project.aiSummaryNote.isNotBlank()) {
                            Text(
                                text = project.aiSummaryNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Segment List
        if (project.segments.isEmpty()) {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select a raw video above to automatically detect high-energy emotional segments.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            project.segments.forEachIndexed { index, segment ->
                SegmentCard(
                    segment = segment,
                    index = index,
                    totalCount = project.segments.size,
                    sourceDurationMs = project.sourceDurationMs,
                    onToggle = { onToggleSegment(segment.id) },
                    onBoundsChange = { start, end -> onUpdateBounds(segment.id, start, end) },
                    onCueChange = { cue -> onUpdateCue(segment.id, cue) },
                    onMoveUp = { onMoveSegment(segment.id, -1) },
                    onMoveDown = { onMoveSegment(segment.id, 1) }
                )
            }
        }
    }
}

@Composable
private fun SegmentCard(
    segment: VideoSegment,
    index: Int,
    totalCount: Int,
    sourceDurationMs: Long,
    onToggle: () -> Unit,
    onBoundsChange: (Long, Long) -> Unit,
    onCueChange: (CueAccent) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val maxSource = sourceDurationMs.coerceAtLeast(2_000L).toFloat()
    val accentColor = when (segment.cueAccent) {
        CueAccent.RED_TENSION -> CrimsonCue
        CueAccent.GREEN_SUNNAH -> EmeraldCue
        CueAccent.FLASH_CUT, CueAccent.SLOW_ZOOM -> SunnahGold
        CueAccent.NONE -> StudioOutline
    }

    Surface(
        color = if (segment.enabled) StudioSurfaceElevated else StudioSurface.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (segment.enabled) accentColor.copy(alpha = 0.65f) else StudioOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_card_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (segment.enabled) TextPrimary else TextSecondary
                    )
                    Text(
                        text = "${formatDurationMs(segment.startMs)} → ${formatDurationMs(segment.endMs)} (${segment.durationMs / 1000f}s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Move clip up",
                            tint = if (index > 0) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Move clip down",
                            tint = if (index < totalCount - 1) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Switch(
                        checked = segment.enabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioObsidian,
                            checkedTrackColor = SunnahGold
                        ),
                        modifier = Modifier.testTag("segment_toggle_$index")
                    )
                }
            }

            // Trim Range Slider on Source Video
            RangeSlider(
                value = segment.startMs.toFloat().coerceIn(0f, maxSource - 1_000f)..segment.endMs.toFloat().coerceIn(1_000f, maxSource),
                onValueChange = { range ->
                    val newStart = range.start.toLong()
                    val newEnd = range.endInclusive.toLong().coerceAtLeast(newStart + 1_000L)
                    onBoundsChange(newStart, newEnd)
                },
                valueRange = 0f..maxSource,
                colors = SliderDefaults.colors(
                    thumbColor = SunnahGold,
                    activeTrackColor = accentColor.takeIf { it != StudioOutline } ?: SunnahGold,
                    inactiveTrackColor = StudioSurface
                )
            )

            // Red / Green / Flash / Zoom Visual Cue Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CueAccent.entries.forEach { cue ->
                    val selected = segment.cueAccent == cue
                    val chipColor = when (cue) {
                        CueAccent.RED_TENSION -> CrimsonCue
                        CueAccent.GREEN_SUNNAH -> EmeraldCue
                        CueAccent.FLASH_CUT, CueAccent.SLOW_ZOOM -> SunnahGold
                        CueAccent.NONE -> TextSecondary
                    }
                    val chipContainer = when (cue) {
                        CueAccent.RED_TENSION -> CrimsonCueContainer
                        CueAccent.GREEN_SUNNAH -> EmeraldCueContainer
                        else -> SunnahGoldContainer
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onCueChange(cue) },
                        label = {
                            Text(
                                text = cue.shortTag,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipContainer,
                            selectedLabelColor = chipColor,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }
    }
}
