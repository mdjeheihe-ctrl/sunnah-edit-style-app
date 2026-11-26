package com.example.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.model.CaptionOverlayItem
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CueAccent
import com.example.model.EditProject
import com.example.model.VideoSegment
import com.example.ui.theme.CrimsonCue
import com.example.ui.theme.EmeraldCue
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SunnahGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewPane(
    project: EditProject,
    videoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isAnalyzingVideo: Boolean,
    timelinePositionMs: Long,
    activeSegmentAndOffset: Pair<VideoSegment, Long>?,
    onTogglePlayPause: () -> Unit,
    onSeekTimeline: (Long) -> Unit,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDurationMs = project.targetOutputDurationMs.coerceAtLeast(1_000L)
    val activeSegment = activeSegmentAndOffset?.first
    val localSegmentMs = activeSegmentAndOffset?.second ?: 0L
    val activeCaptions = project.captions.filter { timelinePositionMs in it.startMs..it.endMs }

    val zoomScale = when (activeSegment?.cueAccent) {
        CueAccent.SLOW_ZOOM, CueAccent.GREEN_SUNNAH -> {
            val frac = (localSegmentMs.toFloat() / activeSegment.durationMs.coerceAtLeast(1_000L).toFloat())
                .coerceIn(0f, 1f)
            1.0f + (0.08f * frac)
        }
        CueAccent.FLASH_CUT -> {
            if (localSegmentMs < 240L) 1.09f - (0.37f * (localSegmentMs / 1000f)) else 1.0f
        }
        else -> 1.0f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 9:16 Vertical Studio Viewport
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth(0.64f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(22.dp))
                .background(StudioObsidian)
                .border(1.5.dp, StudioOutline, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (project.sourceVideoUri != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = videoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        if (view.player !== videoPlayer) {
                            view.player = videoPlayer
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                        }
                )

                // Synchronized Cue Visual Aura
                CueVisualOverlay(
                    cueAccent = activeSegment?.cueAccent ?: CueAccent.NONE,
                    localSegmentMs = localSegmentMs
                )

                // Synchronized Timed Captions
                activeCaptions.forEach { caption ->
                    PreviewCaptionOverlay(caption = caption)
                }

                // Floating Top-Right 9:16 + Cue Pill
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = StudioObsidian.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StudioOutline.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "9:16 REEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    if (activeSegment != null && activeSegment.cueAccent != CueAccent.NONE) {
                        val cueColor = when (activeSegment.cueAccent) {
                            CueAccent.RED_TENSION -> CrimsonCue
                            CueAccent.GREEN_SUNNAH -> EmeraldCue
                            else -> SunnahGold
                        }
                        Surface(
                            color = StudioObsidian.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, cueColor.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = activeSegment.cueAccent.shortTag.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = cueColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            } else {
                // Empty State Hero Artwork inside 9:16 Frame
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = "NoorCut 9:16 studio preview canvas",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    StudioObsidian.copy(alpha = 0.35f),
                                    StudioObsidian.copy(alpha = 0.86f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = SunnahGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Craft Your 35s Sunnah Edit",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Upload a 1–3+ min raw video for smart trimming, Red/Green cues, captions & slowed/reverb mix.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onPickVideo,
                        enabled = !isAnalyzingVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SunnahGold,
                            contentColor = StudioObsidian
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("pick_video_hero_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = "Select video",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select Raw Video",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (isAnalyzingVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(StudioObsidian.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SunnahGold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Scanning energy & beats...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Transport & Sweet-Spot Target Indicator
        if (project.sourceVideoUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SunnahGold,
                            contentColor = StudioObsidian
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause preview" else "Play preview"
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = timelinePositionMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                            onValueChange = { onSeekTimeline(it.toLong()) },
                            valueRange = 0f..totalDurationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = SunnahGold,
                                activeTrackColor = SunnahGold,
                                inactiveTrackColor = StudioSurfaceElevated
                            ),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("preview_scrubber")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatClockMs(timelinePositionMs)} / ${formatClockMs(totalDurationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            val targetColor = if (project.isWithinSweetSpot) EmeraldCue else SunnahGold
                            val targetLabel = if (project.isWithinSweetSpot) {
                                "TARGET 30–40s ✓"
                            } else {
                                "OUT ${project.targetOutputDurationMs / 1000}s (AIM 30–40s)"
                            }
                            Text(
                                text = targetLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = targetColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CueVisualOverlay(
    cueAccent: CueAccent,
    localSegmentMs: Long
) {
    when (cueAccent) {
        CueAccent.RED_TENSION -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, CrimsonCue.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                CrimsonCue.copy(alpha = 0.22f)
                            )
                        )
                    )
            )
        }
        CueAccent.GREEN_SUNNAH -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, EmeraldCue.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                EmeraldCue.copy(alpha = 0.22f)
                            )
                        )
                    )
            )
        }
        CueAccent.FLASH_CUT -> {
            if (localSegmentMs < 220L) {
                val alpha = (1f - localSegmentMs / 220f) * 0.65f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = alpha))
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun PreviewCaptionOverlay(caption: CaptionOverlayItem) {
    val alignment = when (caption.position) {
        CaptionPosition.TOP -> Alignment.TopCenter
        CaptionPosition.CENTER -> Alignment.Center
        CaptionPosition.BOTTOM -> Alignment.BottomCenter
    }
    val topBottomPad = when (caption.position) {
        CaptionPosition.TOP -> 44.dp
        CaptionPosition.CENTER -> 0.dp
        CaptionPosition.BOTTOM -> 34.dp
    }
    val (textColor, bgColor, borderColor) = when (caption.style) {
        CaptionStyle.SUNNAH_GOLD -> Triple(
            Color(0xFFFFECB3),
            Color(0xB312100A),
            SunnahGold.copy(alpha = 0.85f)
        )
        CaptionStyle.CRIMSON_CONTRAST -> Triple(
            Color(0xFFFEE2E2),
            Color(0xBB2A0A0C),
            CrimsonCue.copy(alpha = 0.85f)
        )
        CaptionStyle.EMERALD_HOPE -> Triple(
            Color(0xFFD1FAE5),
            Color(0xBB07241A),
            EmeraldCue.copy(alpha = 0.85f)
        )
        CaptionStyle.CLEAN_MINIMAL -> Triple(
            TextPrimary,
            StudioObsidian.copy(alpha = 0.72f),
            TextPrimary.copy(alpha = 0.6f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = topBottomPad),
        contentAlignment = alignment
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Text(
                text = caption.text,
                color = textColor,
                fontFamily = PlayfairDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
