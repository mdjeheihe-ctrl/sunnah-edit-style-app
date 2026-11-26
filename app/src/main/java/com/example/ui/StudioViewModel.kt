package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.ai.SunnahAiAnalyzer
import com.example.data.StudioPreferences
import com.example.media.AmbientAudioGenerator
import com.example.media.ReverbAndGainAudioProcessor
import com.example.media.VideoAnalyzer
import com.example.media.VideoExportEngine
import com.example.model.AiAnalysisState
import com.example.model.AudioMixSettings
import com.example.model.CaptionOverlayItem
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CueAccent
import com.example.model.EditProject
import com.example.model.ExportQuality
import com.example.model.ExportState
import com.example.model.StoryboardFrame
import com.example.model.StudioTab
import com.example.model.VideoSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = StudioPreferences(appContext)
    private val aiAnalyzer = SunnahAiAnalyzer()
    private val exportEngine = VideoExportEngine(appContext)
    private val bgReverbProcessor = ReverbAndGainAudioProcessor(gain = 0.42f, reverbWet = 0.45f)

    private var cachedStoryboardFrames: List<StoryboardFrame> = emptyList()

    private val _project = MutableStateFlow(EditProject())
    val project: StateFlow<EditProject> = _project.asStateFlow()

    private val _selectedTab = MutableStateFlow(StudioTab.TIMELINE)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _isAnalyzingVideo = MutableStateFlow(false)
    val isAnalyzingVideo: StateFlow<Boolean> = _isAnalyzingVideo.asStateFlow()

    private val _aiState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiState: StateFlow<AiAnalysisState> = _aiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _timelinePositionMs = MutableStateFlow(0L)
    val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

    val videoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    syncAndPlayBgAudio()
                } else {
                    bgAudioPlayer.pause()
                }
            }
        })
    }

    private val bgAudioPlayer: ExoPlayer = ExoPlayer.Builder(
        appContext,
        object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(bgReverbProcessor))
                    .build()
            }
        }
    ).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
    }

    init {
        viewModelScope.launch {
            val defaults = preferences.defaultsFlow.first()
            _project.update { current ->
                current.copy(
                    exportQuality = defaults.exportQuality,
                    audioMix = current.audioMix.copy(
                        slowedAndReverb = defaults.slowedAndReverb,
                        slowedSpeed = defaults.slowedSpeed,
                        reverbWetLevel = defaults.reverbWetLevel,
                        originalVolume = defaults.originalVolume,
                        bgMusicVolume = defaults.bgMusicVolume
                    )
                )
            }
            applyAudioSettingsToPlayers(_project.value.audioMix)
        }

        // Poll current timeline position cleanly at 30Hz while playing
        viewModelScope.launch {
            while (isActive) {
                if (videoPlayer.isPlaying || _project.value.sourceVideoUri != null) {
                    _timelinePositionMs.value = computeCurrentTimelineMs()
                }
                delay(40L)
            }
        }
    }

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzingVideo.value = true
            _aiState.value = AiAnalysisState.Idle
            _exportState.value = ExportState.Idle
            pausePreview()

            try {
                val analysis = VideoAnalyzer.analyzeVideo(appContext, uri)
                cachedStoryboardFrames = analysis.storyboardFrames

                val ambientUri = AmbientAudioGenerator.ensureAmbientTrackUri(appContext)
                _project.update { current ->
                    current.copy(
                        sourceVideoUri = uri,
                        sourceFileName = analysis.fileName,
                        sourceDurationMs = analysis.durationMs,
                        sourceWidth = analysis.width,
                        sourceHeight = analysis.height,
                        segments = analysis.suggestedSegments,
                        captions = analysis.suggestedCaptions,
                        audioMix = if (current.audioMix.bgMusicUri == null) {
                            current.audioMix.copy(bgMusicUri = ambientUri)
                        } else {
                            current.audioMix
                        },
                        aiSummaryNote = "Auto-trimmed ${analysis.durationMs / 1000}s raw clip into ${analysis.suggestedSegments.sumOf { it.durationMs } / 1000}s emotional arc."
                    )
                }
                rebuildVideoPlayerPlaylist()
                prepareBgAudioPlayer()
                applyAudioSettingsToPlayers(_project.value.audioMix)
            } catch (e: Exception) {
                _aiState.value = AiAnalysisState.Error(
                    e.localizedMessage ?: "Unable to read selected video."
                )
            } finally {
                _isAnalyzingVideo.value = false
            }
        }
    }

    fun updateTopicPrompt(prompt: String) {
        _project.update { it.copy(topicPrompt = prompt) }
    }

    fun runSmartAutoTrim() {
        val current = _project.value
        if (current.sourceVideoUri == null) return
        val newSegments = VideoAnalyzer.buildSmartSunnahCuts(current.sourceDurationMs)
        val newCaptions = if (current.captions.isEmpty()) {
            VideoAnalyzer.buildStarterCaptions(newSegments)
        } else {
            current.captions
        }
        _project.update {
            it.copy(
                segments = newSegments,
                captions = newCaptions,
                aiSummaryNote = "Smart Auto-Trim selected ${newSegments.size} high-energy beats (${newSegments.sumOf { s -> s.durationMs } / 1000}s)."
            )
        }
        rebuildVideoPlayerPlaylist()
    }

    fun runAiDirector() {
        val current = _project.value
        if (current.sourceVideoUri == null) {
            _aiState.value = AiAnalysisState.Error("Import a raw video first before running AI Director.")
            return
        }
        viewModelScope.launch {
            _aiState.value = AiAnalysisState.Analyzing("Analyzing storyboard frames & emotional arc...")
            try {
                val plan = aiAnalyzer.generateSunnahEditPlan(
                    sourceDurationMs = current.sourceDurationMs,
                    topicPrompt = current.topicPrompt,
                    storyboardFrames = cachedStoryboardFrames
                )
                if (plan.segments.isNotEmpty()) {
                    _project.update {
                        it.copy(
                            segments = plan.segments,
                            captions = if (plan.captions.isNotEmpty()) plan.captions else it.captions,
                            aiSummaryNote = plan.narrativeSummary
                        )
                    }
                    rebuildVideoPlayerPlaylist()
                    _aiState.value = AiAnalysisState.Success(plan.narrativeSummary)
                } else {
                    _aiState.value = AiAnalysisState.Error("AI Director could not identify usable segments.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiAnalysisState.Error(
                    e.localizedMessage ?: "AI analysis failed. Check connection or try Smart Auto-Trim."
                )
            }
        }
    }

    fun toggleSegment(segmentId: String) {
        _project.update { proj ->
            proj.copy(
                segments = proj.segments.map {
                    if (it.id == segmentId) it.copy(enabled = !it.enabled) else it
                }
            )
        }
        rebuildVideoPlayerPlaylist()
    }

    fun updateSegmentBounds(segmentId: String, startMs: Long, endMs: Long) {
        val maxDur = _project.value.sourceDurationMs.coerceAtLeast(1_000L)
        val clampedStart = startMs.coerceIn(0L, (maxDur - 1_000L).coerceAtLeast(0L))
        val clampedEnd = endMs.coerceIn(clampedStart + 1_000L, maxDur)
        _project.update { proj ->
            proj.copy(
                segments = proj.segments.map {
                    if (it.id == segmentId) it.copy(startMs = clampedStart, endMs = clampedEnd) else it
                }
            )
        }
        rebuildVideoPlayerPlaylist()
    }

    fun updateSegmentCue(segmentId: String, cueAccent: CueAccent) {
        _project.update { proj ->
            proj.copy(
                segments = proj.segments.map {
                    if (it.id == segmentId) it.copy(cueAccent = cueAccent) else it
                }
            )
        }
    }

    fun moveSegment(segmentId: String, delta: Int) {
        _project.update { proj ->
            val list = proj.segments.toMutableList()
            val idx = list.indexOfFirst { it.id == segmentId }
            val targetIdx = idx + delta
            if (idx != -1 && targetIdx in list.indices) {
                val item = list.removeAt(idx)
                list.add(targetIdx, item)
            }
            proj.copy(segments = list)
        }
        rebuildVideoPlayerPlaylist()
    }

    fun addCaption(
        text: String,
        startMs: Long,
        endMs: Long,
        position: CaptionPosition,
        style: CaptionStyle
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val maxTimeline = _project.value.targetOutputDurationMs.coerceAtLeast(5_000L)
        val safeStart = startMs.coerceIn(0L, (maxTimeline - 800L).coerceAtLeast(0L))
        val safeEnd = endMs.coerceIn(safeStart + 800L, maxTimeline)
        _project.update { proj ->
            proj.copy(
                captions = (proj.captions + CaptionOverlayItem(
                    text = trimmed,
                    startMs = safeStart,
                    endMs = safeEnd,
                    position = position,
                    style = style
                )).sortedBy { it.startMs }
            )
        }
    }

    fun updateCaption(updated: CaptionOverlayItem) {
        _project.update { proj ->
            proj.copy(
                captions = proj.captions.map { if (it.id == updated.id) updated else it }
                    .sortedBy { it.startMs }
            )
        }
    }

    fun deleteCaption(captionId: String) {
        _project.update { proj ->
            proj.copy(captions = proj.captions.filterNot { it.id == captionId })
        }
    }

    fun onCustomAudioSelected(uri: Uri) {
        val title = resolveFileName(uri)
        _project.update { proj ->
            proj.copy(
                audioMix = proj.audioMix.copy(
                    bgMusicUri = uri,
                    bgMusicTitle = title,
                    useBuiltInAmbient = false,
                    enableBgMusic = true
                )
            )
        }
        prepareBgAudioPlayer()
    }

    fun selectBuiltInAmbient() {
        viewModelScope.launch {
            val uri = AmbientAudioGenerator.ensureAmbientTrackUri(appContext)
            _project.update { proj ->
                proj.copy(
                    audioMix = proj.audioMix.copy(
                        bgMusicUri = uri,
                        bgMusicTitle = "Built-in Ambient Pad",
                        useBuiltInAmbient = true,
                        enableBgMusic = true
                    )
                )
            }
            prepareBgAudioPlayer()
        }
    }

    fun updateAudioMix(transform: (AudioMixSettings) -> AudioMixSettings) {
        _project.update { proj ->
            val updatedMix = transform(proj.audioMix)
            proj.copy(audioMix = updatedMix)
        }
        val mix = _project.value.audioMix
        applyAudioSettingsToPlayers(mix)
        viewModelScope.launch {
            preferences.saveAudioDefaults(
                slowedAndReverb = mix.slowedAndReverb,
                slowedSpeed = mix.slowedSpeed,
                reverbWet = mix.reverbWetLevel,
                origVol = mix.originalVolume,
                bgVol = mix.bgMusicVolume
            )
        }
    }

    fun updateExportQuality(quality: ExportQuality) {
        _project.update { it.copy(exportQuality = quality) }
        viewModelScope.launch {
            preferences.saveExportQuality(quality)
        }
    }

    fun togglePlayPause() {
        if (_project.value.sourceVideoUri == null) return
        if (videoPlayer.isPlaying) {
            pausePreview()
        } else {
            if (videoPlayer.mediaItemCount == 0) {
                rebuildVideoPlayerPlaylist()
            }
            videoPlayer.play()
        }
    }

    fun pausePreview() {
        videoPlayer.pause()
        bgAudioPlayer.pause()
    }

    fun seekToTimelineMs(targetMs: Long) {
        val enabled = _project.value.enabledSegments
        if (enabled.isEmpty()) return
        var cursor = 0L
        for (i in enabled.indices) {
            val segDur = enabled[i].durationMs.coerceAtLeast(1L)
            if (targetMs <= cursor + segDur || i == enabled.lastIndex) {
                val localOffset = (targetMs - cursor).coerceIn(0L, segDur)
                videoPlayer.seekTo(i, localOffset)
                _timelinePositionMs.value = targetMs
                return
            }
            cursor += segDur
        }
    }

    fun activeSegmentAt(timelineMs: Long): Pair<VideoSegment, Long>? {
        var cursor = 0L
        for (seg in _project.value.enabledSegments) {
            val dur = seg.durationMs
            if (timelineMs in cursor..(cursor + dur)) {
                return seg to (timelineMs - cursor)
            }
            cursor += dur
        }
        return _project.value.enabledSegments.firstOrNull()?.let { it to 0L }
    }

    fun startExport() {
        pausePreview()
        _exportState.value = ExportState.Exporting(0, "Starting Media3 pipeline...")
        exportEngine.startExport(
            project = _project.value,
            scope = viewModelScope,
            onProgress = { pct, stage ->
                _exportState.value = ExportState.Exporting(pct, stage)
            },
            onSuccess = { file, sizeBytes, durationMs, galleryUri ->
                _exportState.value = ExportState.Completed(
                    outputFilePath = file.absolutePath,
                    outputSizeBytes = sizeBytes,
                    durationMs = durationMs,
                    savedGalleryUri = galleryUri
                )
            },
            onError = { msg ->
                _exportState.value = ExportState.Error(msg)
            }
        )
    }

    fun cancelExport() {
        exportEngine.cancelExport()
        _exportState.value = ExportState.Idle
    }

    fun buildShareIntent(filePath: String): Intent? {
        val file = File(filePath)
        if (!file.exists()) return null
        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun rebuildVideoPlayerPlaylist() {
        val uri = _project.value.sourceVideoUri ?: return
        val enabled = _project.value.enabledSegments
        if (enabled.isEmpty()) {
            videoPlayer.clearMediaItems()
            return
        }
        val items = enabled.map { seg ->
            MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(seg.startMs)
                        .setEndPositionMs(seg.endMs)
                        .build()
                )
                .build()
        }
        videoPlayer.setMediaItems(items, true)
        videoPlayer.prepare()
    }

    private fun prepareBgAudioPlayer() {
        val audioUri = _project.value.audioMix.bgMusicUri ?: return
        bgAudioPlayer.setMediaItem(MediaItem.fromUri(audioUri))
        bgAudioPlayer.prepare()
        if (videoPlayer.isPlaying && _project.value.audioMix.enableBgMusic) {
            bgAudioPlayer.play()
        }
    }

    private fun syncAndPlayBgAudio() {
        val mix = _project.value.audioMix
        applyAudioSettingsToPlayers(mix)
        if (mix.enableBgMusic && mix.bgMusicUri != null) {
            if (bgAudioPlayer.mediaItemCount == 0) {
                prepareBgAudioPlayer()
            }
            bgAudioPlayer.play()
        } else {
            bgAudioPlayer.pause()
        }
    }

    private fun applyAudioSettingsToPlayers(mix: AudioMixSettings) {
        videoPlayer.volume = mix.originalVolume.coerceIn(0f, 1f)
        bgReverbProcessor.gain = if (mix.enableBgMusic) mix.bgMusicVolume else 0f
        bgReverbProcessor.reverbWet = if (mix.slowedAndReverb) mix.reverbWetLevel else 0f
        val speed = if (mix.slowedAndReverb) mix.slowedSpeed.coerceIn(0.75f, 1.0f) else 1.0f
        bgAudioPlayer.playbackParameters = PlaybackParameters(speed, speed)
        if (!mix.enableBgMusic) {
            bgAudioPlayer.pause()
        } else if (videoPlayer.isPlaying && bgAudioPlayer.mediaItemCount > 0) {
            bgAudioPlayer.play()
        }
    }

    private fun computeCurrentTimelineMs(): Long {
        val enabled = _project.value.enabledSegments
        if (enabled.isEmpty()) return 0L
        val idx = videoPlayer.currentMediaItemIndex.coerceIn(0, enabled.lastIndex)
        var offset = 0L
        for (i in 0 until idx) {
            offset += enabled[i].durationMs
        }
        return (offset + videoPlayer.currentPosition).coerceAtLeast(0L)
    }

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "content") {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Custom Audio"
    }

    override fun onCleared() {
        super.onCleared()
        exportEngine.cancelExport()
        videoPlayer.release()
        bgAudioPlayer.release()
    }
}
