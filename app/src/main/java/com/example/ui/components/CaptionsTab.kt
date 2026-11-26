package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CaptionOverlayItem
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.EditProject
import com.example.ui.theme.CrimsonCue
import com.example.ui.theme.EmeraldCue
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SunnahGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CaptionsTab(
    project: EditProject,
    currentTimelineMs: Long,
    onAddCaption: (String, Long, Long, CaptionPosition, CaptionStyle) -> Unit,
    onUpdateCaption: (CaptionOverlayItem) -> Unit,
    onDeleteCaption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftText by remember { mutableStateOf("") }
    var draftStyle by remember { mutableStateOf(CaptionStyle.SUNNAH_GOLD) }
    var draftPosition by remember { mutableStateOf(CaptionPosition.CENTER) }

    val maxEditMs = project.targetOutputDurationMs.coerceAtLeast(5_000L)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Add New Caption Card
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
                    text = "Add Punchy Text Overlay",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    label = { Text("Caption line (4–9 words recommended)") },
                    placeholder = { Text("e.g. Never underestimate a gentle word.") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("caption_draft_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CaptionStyle.entries.forEach { style ->
                        FilterChip(
                            selected = draftStyle == style,
                            onClick = { draftStyle = style },
                            label = { Text(style.label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CaptionPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = draftPosition == pos,
                                onClick = { draftPosition = pos },
                                label = { Text(pos.label) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val start = currentTimelineMs.coerceIn(0L, (maxEditMs - 2_000L).coerceAtLeast(0L))
                            val end = (start + 3_500L).coerceAtMost(maxEditMs)
                            onAddCaption(draftText, start, end, draftPosition, draftStyle)
                            draftText = ""
                        },
                        enabled = draftText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SunnahGold,
                            contentColor = StudioObsidian
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_caption_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add caption",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add @ ${formatDurationMs(currentTimelineMs)}")
                    }
                }
            }
        }

        // Existing Captions
        if (project.captions.isEmpty()) {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No captions yet. Add emotional punchlines or run AI Auto-Edit in the Clips tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            project.captions.forEachIndexed { idx, caption ->
                val accent = when (caption.style) {
                    CaptionStyle.SUNNAH_GOLD -> SunnahGold
                    CaptionStyle.CRIMSON_CONTRAST -> CrimsonCue
                    CaptionStyle.EMERALD_HOPE -> EmeraldCue
                    CaptionStyle.CLEAN_MINIMAL -> TextPrimary
                }
                Surface(
                    color = StudioSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("caption_card_$idx")
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
                            OutlinedTextField(
                                value = caption.text,
                                onValueChange = { onUpdateCaption(caption.copy(text = it)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { onDeleteCaption(caption.id) },
                                modifier = Modifier.testTag("delete_caption_$idx")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete caption",
                                    tint = CrimsonCue
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatDurationMs(caption.startMs)} → ${formatDurationMs(caption.endMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            Text(
                                text = "${caption.style.label} • ${caption.position.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }

                        RangeSlider(
                            value = caption.startMs.toFloat().coerceIn(0f, maxEditMs.toFloat() - 800f)..caption.endMs.toFloat().coerceIn(800f, maxEditMs.toFloat()),
                            onValueChange = { range ->
                                val s = range.start.toLong()
                                val e = range.endInclusive.toLong().coerceAtLeast(s + 800L)
                                onUpdateCaption(caption.copy(startMs = s, endMs = e))
                            },
                            valueRange = 0f..maxEditMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = StudioSurface
                            ),
                            modifier = Modifier.height(28.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CaptionStyle.entries.forEach { st ->
                                FilterChip(
                                    selected = caption.style == st,
                                    onClick = { onUpdateCaption(caption.copy(style = st)) },
                                    label = { Text(st.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent
                                    )
                                )
                            }
                            CaptionPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = caption.position == pos,
                                    onClick = { onUpdateCaption(caption.copy(position = pos)) },
                                    label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
