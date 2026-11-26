package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldCue
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SunnahGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchitectureSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "NoorCut Pipeline Architecture",
                style = MaterialTheme.typography.headlineSmall,
                color = SunnahGold
            )
            Text(
                text = "Built on Jetpack Media3 Transformer + Firebase AI Logic (Gemini 3.8 Flash) for lightweight, memory-safe 9:16 rendering on modest devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            BlueprintCard(
                title = "1. Zero-Permission Ingestion & Chunked Scan",
                body = "Photo Picker imports 1–3+ min videos without broad storage permissions. MediaExtractor streams 64KB encoded chunks to score audio/motion energy without decoding raw frames into heap RAM."
            )
            BlueprintCard(
                title = "2. Smart 30–40s Trimming & AI Director",
                body = "Deterministic beat partitioning or multimodal Gemini 3.8 Flash storyboard inspection selects 3–5 non-overlapping beats totaling ~34s, assigning Red Tension and Green Sunnah cues."
            )
            BlueprintCard(
                title = "3. Streaming 9:16 Effects & Slowed/Reverb DSP",
                body = "Media3 Composition clips segments via ClippingConfiguration, applies 9:16 center-crop, MatrixTransformation push-in zoom, single-bitmap Canvas OverlayEffect, SonicAudioProcessor pitch/tempo slowing, and multi-tap stereo reverb."
            )
            BlueprintCard(
                title = "4. Phase 2 Upgrade Path",
                body = "Add Whisper/Speech-to-Text word-level karaoke captions, shot-boundary optical-flow transitions, LUT color grading shaders, and reusable JSON Sunnah Edit templates."
            )
        }
    }
}

@Composable
private fun BlueprintCard(title: String, body: String) {
    Surface(
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StudioOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = EmeraldCue)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}
