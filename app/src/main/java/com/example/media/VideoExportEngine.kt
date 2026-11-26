package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.R
import com.example.model.EditProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Hardware-accelerated, memory-safe Media3 Transformer export pipeline.
 * Streams trimmed clips sequentially without decoding full raw video into memory,
 * composites 9:16 crop + timed BitmapOverlay captions + Red/Green cues, and mixes
 * slowed/reverb background audio.
 */
@OptIn(UnstableApi::class)
class VideoExportEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    fun startExport(
        project: EditProject,
        scope: CoroutineScope,
        onProgress: (percent: Int, stage: String) -> Unit,
        onSuccess: (outputFile: File, fileSizeBytes: Long, durationMs: Long, galleryUri: Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        val sourceUri = project.sourceVideoUri ?: run {
            onError("No source video selected.")
            return
        }
        val segments = project.enabledSegments
        if (segments.isEmpty()) {
            onError("Enable at least one video clip before exporting.")
            return
        }

        scope.launch {
            try {
                onProgress(2, "Preparing 9:16 composition...")

                val bgAudioUri: Uri? = if (project.audioMix.enableBgMusic) {
                    if (project.audioMix.bgMusicUri != null) {
                        project.audioMix.bgMusicUri
                    } else {
                        AmbientAudioGenerator.ensureAmbientTrackUri(context)
                    }
                } else {
                    null
                }

                val serifTypeface = runCatching {
                    ResourcesCompat.getFont(context, R.font.playfair_display)
                }.getOrNull()

                val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                // Clean old cached exports to stay storage-safe on modest devices
                exportsDir.listFiles()?.forEach { old ->
                    if (System.currentTimeMillis() - old.lastModified() > 3_600_000L) {
                        old.delete()
                    }
                }
                val outputFile = File(exportsDir, "NoorCut_${System.currentTimeMillis()}.mp4")

                var offsetCursorMs = 0L
                val videoItems = segments.map { segment ->
                    val origGainProcessor = ReverbAndGainAudioProcessor(
                        gain = project.audioMix.originalVolume,
                        reverbWet = 0f
                    )
                    val videoEffects = SunnahVideoEffects.buildSegmentVideoEffects(
                        segment = segment,
                        segmentTimelineOffsetMs = offsetCursorMs,
                        allCaptions = project.captions,
                        quality = project.exportQuality,
                        serifTypeface = serifTypeface
                    )
                    offsetCursorMs += segment.durationMs

                    val clippedItem = MediaItem.Builder()
                        .setUri(sourceUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(segment.startMs)
                                .setEndPositionMs(segment.endMs)
                                .build()
                        )
                        .build()

                    EditedMediaItem.Builder(clippedItem)
                        .setEffects(Effects(listOf(origGainProcessor), videoEffects))
                        .build()
                }

                val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()

                val compositionBuilder = if (bgAudioUri != null) {
                    val audioProcessors = mutableListOf<AudioProcessor>()
                    if (project.audioMix.slowedAndReverb) {
                        val sonic = SonicAudioProcessor().apply {
                            setSpeed(project.audioMix.slowedSpeed)
                            setPitch(project.audioMix.slowedSpeed)
                        }
                        audioProcessors.add(sonic)
                    }
                    val reverbAndGain = ReverbAndGainAudioProcessor(
                        gain = project.audioMix.bgMusicVolume,
                        reverbWet = if (project.audioMix.slowedAndReverb) {
                            project.audioMix.reverbWetLevel
                        } else {
                            0f
                        }
                    )
                    audioProcessors.add(reverbAndGain)

                    val bgItem = EditedMediaItem.Builder(MediaItem.fromUri(bgAudioUri))
                        .setEffects(Effects(audioProcessors, emptyList()))
                        .build()

                    val bgSequence = EditedMediaItemSequence.Builder(listOf(bgItem))
                        .setIsLooping(true)
                        .build()

                    Composition.Builder(videoSequence, bgSequence)
                } else {
                    Composition.Builder(videoSequence)
                }

                val composition = compositionBuilder.build()

                withContext(Dispatchers.Main) {
                    launchTransformerOnMain(
                        composition = composition,
                        outputFile = outputFile,
                        expectedDurationMs = offsetCursorMs,
                        scope = scope,
                        onProgress = onProgress,
                        onSuccess = onSuccess,
                        onError = onError
                    )
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to prepare export.")
            }
        }
    }

    private fun launchTransformerOnMain(
        composition: Composition,
        outputFile: File,
        expectedDurationMs: Long,
        scope: CoroutineScope,
        onProgress: (Int, String) -> Unit,
        onSuccess: (File, Long, Long, Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        stopProgressPolling()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    activeTransformer = null
                    scope.launch {
                        val galleryUri = saveMp4ToGallery(outputFile)
                        onSuccess(
                            outputFile,
                            outputFile.length(),
                            exportResult.durationMs.takeIf { it > 0L } ?: expectedDurationMs,
                            galleryUri
                        )
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopProgressPolling()
                    activeTransformer = null
                    onError(exportException.localizedMessage ?: "Export failed during rendering.")
                }
            })
            .build()

        activeTransformer = transformer
        transformer.start(composition, outputFile.absolutePath)

        val progressHolder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val current = activeTransformer ?: return
                val state = current.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val pct = progressHolder.progress.coerceIn(0, 99)
                    val stage = when {
                        pct < 25 -> "Trimming & scaling to 9:16..."
                        pct < 75 -> "Rendering captions, cues & audio mix..."
                        else -> "Muxing final MP4 reel..."
                    }
                    onProgress(pct, stage)
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 200L)
    }

    fun cancelExport() {
        mainHandler.post {
            stopProgressPolling()
            runCatching { activeTransformer?.cancel() }
            activeTransformer = null
        }
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private suspend fun saveMp4ToGallery(file: File): Uri? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        return@withContext runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/NoorCut"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out, bufferSize = 32_768)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }
}
