package com.example.model

import android.net.Uri
import java.util.UUID

enum class CueAccent(val label: String, val shortTag: String) {
    NONE("Clean Cut", "Clean"),
    RED_TENSION("Red Tension Cue", "Tension"),
    GREEN_SUNNAH("Green Sunnah Cue", "Kindness"),
    FLASH_CUT("White Flash Beat", "Flash"),
    SLOW_ZOOM("Emotional Push-In", "Zoom")
}

enum class CaptionStyle(val label: String) {
    SUNNAH_GOLD("Sunnah Gold"),
    CRIMSON_CONTRAST("Crimson Tension"),
    EMERALD_HOPE("Emerald Light"),
    CLEAN_MINIMAL("Minimal Ivory")
}

enum class CaptionPosition(val label: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom")
}

enum class ExportQuality(
    val label: String,
    val width: Int,
    val height: Int,
    val bitrateHint: String
) {
    MODEST_720P("720x1280 (Modest Hardware)", 720, 1280, "~4.5 Mbps • Low RAM"),
    HD_1080P("1080x1920 (Full HD 9:16)", 1080, 1920, "~8.0 Mbps • Crisp Reel")
}

enum class StudioTab(val label: String) {
    TIMELINE("Clips"),
    CAPTIONS("Captions"),
    AUDIO("Audio"),
    EXPORT("Export")
}

data class VideoSegment(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val enabled: Boolean = true,
    val cueAccent: CueAccent = CueAccent.NONE,
    val energyScore: Float = 0.5f,
    val speedMultiplier: Float = 1.0f
) {
    val durationMs: Long get() = ((endMs - startMs).coerceAtLeast(0L) / speedMultiplier).toLong()
}

data class CaptionOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val position: CaptionPosition = CaptionPosition.CENTER,
    val style: CaptionStyle = CaptionStyle.SUNNAH_GOLD
)

data class AudioMixSettings(
    val bgMusicUri: Uri? = null,
    val bgMusicTitle: String = "Built-in Ambient Pad",
    val useBuiltInAmbient: Boolean = true,
    val enableBgMusic: Boolean = true,
    val slowedAndReverb: Boolean = true,
    val slowedSpeed: Float = 0.86f,
    val reverbWetLevel: Float = 0.45f,
    val originalVolume: Float = 0.85f,
    val bgMusicVolume: Float = 0.42f
)

data class StoryboardFrame(
    val timestampMs: Long,
    val jpegBytes: ByteArray
)

data class EditProject(
    val sourceVideoUri: Uri? = null,
    val sourceFileName: String = "",
    val sourceDurationMs: Long = 0L,
    val sourceWidth: Int = 1080,
    val sourceHeight: Int = 1920,
    val segments: List<VideoSegment> = emptyList(),
    val captions: List<CaptionOverlayItem> = emptyList(),
    val audioMix: AudioMixSettings = AudioMixSettings(),
    val exportQuality: ExportQuality = ExportQuality.MODEST_720P,
    val topicPrompt: String = "",
    val aiSummaryNote: String = ""
) {
    val enabledSegments: List<VideoSegment>
        get() = segments.filter { it.enabled && it.endMs > it.startMs }

    val targetOutputDurationMs: Long
        get() = enabledSegments.sumOf { it.durationMs }

    val isWithinSweetSpot: Boolean
        get() = targetOutputDurationMs in 30_000L..40_000L
}

sealed interface AiAnalysisState {
    data object Idle : AiAnalysisState
    data class Analyzing(val status: String) : AiAnalysisState
    data class Success(val note: String) : AiAnalysisState
    data class Error(val message: String) : AiAnalysisState
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progressPercent: Int, val stage: String) : ExportState
    data class Completed(
        val outputFilePath: String,
        val outputSizeBytes: Long,
        val durationMs: Long,
        val savedGalleryUri: Uri?
    ) : ExportState
    data class Error(val message: String) : ExportState
}
