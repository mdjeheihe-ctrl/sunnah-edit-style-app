package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.model.CaptionOverlayItem
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CueAccent
import com.example.model.StoryboardFrame
import com.example.model.VideoSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

data class AnalyzedVideoResult(
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val suggestedSegments: List<VideoSegment>,
    val suggestedCaptions: List<CaptionOverlayItem>,
    val storyboardFrames: List<StoryboardFrame>
)

object VideoAnalyzer {

    private const val TARGET_EDIT_MS = 34_000L
    private const val CHUNK_BUFFER_BYTES = 64 * 1024

    suspend fun analyzeVideo(context: Context, uri: Uri): AnalyzedVideoResult =
        withContext(Dispatchers.IO) {
            val fileName = resolveDisplayName(context, uri)
            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            var width = 1080
            var height = 1920
            var rotation = 0

            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 1080
                val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 1920
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0

                if (rotation == 90 || rotation == 270) {
                    width = rawH
                    height = rawW
                } else {
                    width = rawW
                    height = rawH
                }
            } finally {
                runCatching { retriever.release() }
            }

            val safeDurationMs = durationMs.coerceAtLeast(1_000L)
            val windowEnergies = scanEnergyWindows(context, uri, safeDurationMs)
            val segments = buildSmartSunnahCuts(safeDurationMs, windowEnergies)
            val captions = buildStarterCaptions(segments)
            val storyboard = extractStoryboardFrames(context, uri, safeDurationMs, maxFrames = 6)

            AnalyzedVideoResult(
                fileName = fileName,
                durationMs = safeDurationMs,
                width = width,
                height = height,
                suggestedSegments = segments,
                suggestedCaptions = captions,
                storyboardFrames = storyboard
            )
        }

    fun buildSmartSunnahCuts(
        totalDurationMs: Long,
        windowEnergies: List<Float> = emptyList()
    ): List<VideoSegment> {
        if (totalDurationMs <= 40_000L) {
            val mid = totalDurationMs / 2L
            return listOf(
                VideoSegment(
                    label = "Beat 1 • Tension Hook",
                    startMs = 0L,
                    endMs = mid.coerceAtLeast(1_000L),
                    cueAccent = CueAccent.RED_TENSION,
                    energyScore = 0.85f
                ),
                VideoSegment(
                    label = "Beat 2 • Sunnah Turning Point",
                    startMs = mid.coerceAtLeast(1_000L),
                    endMs = totalDurationMs,
                    cueAccent = CueAccent.GREEN_SUNNAH,
                    energyScore = 0.92f
                )
            )
        }

        // Partition long video (1-3+ min) into 4 narrative zones and pick highest-energy ~8.5s windows
        val targetTotal = min(TARGET_EDIT_MS, totalDurationMs)
        val beatDurations = listOf(8_000L, 8_000L, 9_500L, 8_500L) // sums to 34s
        val zones = listOf(
            0f to 0.25f,
            0.25f to 0.52f,
            0.52f to 0.78f,
            0.78f to 1.0f
        )
        val beatTitles = listOf(
            "Beat 1 • Conflict / Test",
            "Beat 2 • Turning Flash",
            "Beat 3 • Act of Kindness",
            "Beat 4 • Heart Resonance"
        )
        val beatCues = listOf(
            CueAccent.RED_TENSION,
            CueAccent.FLASH_CUT,
            CueAccent.GREEN_SUNNAH,
            CueAccent.SLOW_ZOOM
        )

        val result = mutableListOf<VideoSegment>()
        var lastEndMs = 0L

        zones.forEachIndexed { index, (startRatio, endRatio) ->
            val zoneStart = max(lastEndMs, (totalDurationMs * startRatio).toLong())
            val zoneEnd = (totalDurationMs * endRatio).toLong()
            val desiredLen = beatDurations[index]
            val available = (zoneEnd - zoneStart).coerceAtLeast(0L)

            if (available >= 2_500L) {
                val clipLen = min(desiredLen, available)
                val bestStart = findHighestEnergyStart(
                    zoneStart = zoneStart,
                    zoneEnd = zoneEnd,
                    clipLen = clipLen,
                    totalDurationMs = totalDurationMs,
                    windowEnergies = windowEnergies
                )
                val clipEnd = (bestStart + clipLen).coerceAtMost(totalDurationMs)
                val energy = averageEnergyForRange(bestStart, clipEnd, totalDurationMs, windowEnergies)
                result.add(
                    VideoSegment(
                        label = beatTitles[index],
                        startMs = bestStart,
                        endMs = clipEnd,
                        enabled = true,
                        cueAccent = beatCues[index],
                        energyScore = energy
                    )
                )
                lastEndMs = clipEnd
            }
        }

        // Ensure total duration sits cleanly near targetTotal (30-40s)
        val currentTotal = result.sumOf { it.durationMs }
        if (currentTotal < 30_000L && result.isNotEmpty() && totalDurationMs > 34_000L) {
            val deficit = (34_000L - currentTotal).coerceAtLeast(0L)
            val last = result.last()
            val expandedEnd = (last.endMs + deficit).coerceAtMost(totalDurationMs)
            result[result.lastIndex] = last.copy(endMs = expandedEnd)
        } else if (currentTotal > 40_000L && result.isNotEmpty()) {
            val scale = targetTotal.toFloat() / currentTotal.toFloat()
            for (i in result.indices) {
                val seg = result[i]
                val newLen = ((seg.endMs - seg.startMs) * scale).toLong().coerceAtLeast(3_000L)
                result[i] = seg.copy(endMs = (seg.startMs + newLen).coerceAtMost(totalDurationMs))
            }
        }

        return result
    }

    fun buildStarterCaptions(segments: List<VideoSegment>): List<CaptionOverlayItem> {
        if (segments.isEmpty()) return emptyList()
        val sampleLines = listOf(
            "When the heart is tested..." to CaptionStyle.CRIMSON_CONTRAST,
            "Choose patience over anger." to CaptionStyle.CLEAN_MINIMAL,
            "A single Sunnah of mercy" to CaptionStyle.EMERALD_HOPE,
            "changes everything." to CaptionStyle.SUNNAH_GOLD
        )
        var cursorMs = 0L
        return segments.mapIndexed { index, seg ->
            val segDur = seg.durationMs.coerceAtLeast(1_500L)
            val capStart = cursorMs + 400L
            val capEnd = (cursorMs + segDur - 350L).coerceAtLeast(capStart + 1_200L)
            cursorMs += segDur
            val (line, style) = sampleLines.getOrElse(index) {
                "Reflect on what matters most." to CaptionStyle.SUNNAH_GOLD
            }
            CaptionOverlayItem(
                text = line,
                startMs = capStart,
                endMs = capEnd,
                position = if (index == segments.lastIndex) CaptionPosition.CENTER else CaptionPosition.BOTTOM,
                style = style
            )
        }
    }

    /**
     * Memory-safe chunked packet-density scanner using MediaExtractor.
     * Streams small 64KB buffers instead of loading raw media into memory.
     */
    private fun scanEnergyWindows(
        context: Context,
        uri: Uri,
        totalDurationMs: Long
    ): List<Float> {
        val bucketCount = (totalDurationMs / 1_000L).toInt().coerceIn(8, 300)
        val buckets = LongArray(bucketCount)
        val counts = IntArray(bucketCount)
        val extractor = MediaExtractor()

        return try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex == -1) {
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/")) {
                        trackIndex = i
                        break
                    }
                }
            }
            if (trackIndex == -1) return List(bucketCount) { 0.5f }

            extractor.selectTrack(trackIndex)
            val buffer = ByteBuffer.allocate(CHUNK_BUFFER_BYTES)
            var samplesScanned = 0
            val maxSamples = 18_000

            while (samplesScanned < maxSamples) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val timeMs = extractor.sampleTime / 1_000L
                val bucketIdx = ((timeMs * bucketCount) / totalDurationMs)
                    .toInt()
                    .coerceIn(0, bucketCount - 1)
                buckets[bucketIdx] += size.toLong()
                counts[bucketIdx]++
                samplesScanned++
                if (!extractor.advance()) break
            }

            val averages = buckets.indices.map { idx ->
                if (counts[idx] > 0) buckets[idx].toFloat() / counts[idx] else 0f
            }
            val maxAvg = averages.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            averages.map { (it / maxAvg).coerceIn(0.1f, 1.0f) }
        } catch (_: Exception) {
            List(bucketCount) { 0.5f }
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findHighestEnergyStart(
        zoneStart: Long,
        zoneEnd: Long,
        clipLen: Long,
        totalDurationMs: Long,
        windowEnergies: List<Float>
    ): Long {
        if (windowEnergies.isEmpty() || zoneEnd - zoneStart <= clipLen) {
            return zoneStart
        }
        val stepMs = 1_000L
        var bestStart = zoneStart
        var bestScore = -1f
        var candidate = zoneStart
        while (candidate + clipLen <= zoneEnd) {
            val score = averageEnergyForRange(
                candidate,
                candidate + clipLen,
                totalDurationMs,
                windowEnergies
            )
            if (score > bestScore) {
                bestScore = score
                bestStart = candidate
            }
            candidate += stepMs
        }
        return bestStart
    }

    private fun averageEnergyForRange(
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long,
        windowEnergies: List<Float>
    ): Float {
        if (windowEnergies.isEmpty() || totalDurationMs <= 0L) return 0.65f
        val startIdx = ((startMs * windowEnergies.size) / totalDurationMs)
            .toInt()
            .coerceIn(0, windowEnergies.lastIndex)
        val endIdx = ((endMs * windowEnergies.size) / totalDurationMs)
            .toInt()
            .coerceIn(startIdx, windowEnergies.lastIndex)
        var sum = 0f
        var count = 0
        for (i in startIdx..endIdx) {
            sum += windowEnergies[i]
            count++
        }
        return if (count > 0) sum / count else 0.65f
    }

    private fun extractStoryboardFrames(
        context: Context,
        uri: Uri,
        durationMs: Long,
        maxFrames: Int
    ): List<StoryboardFrame> {
        val frames = mutableListOf<StoryboardFrame>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until maxFrames) {
                val fraction = (i + 0.5f) / maxFrames.toFloat()
                val timestampMs = (durationMs * fraction).toLong()
                val timeUs = timestampMs * 1_000L
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        360,
                        202
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { raw ->
                            val scaled = Bitmap.createScaledBitmap(raw, 360, 202, true)
                            if (scaled != raw) raw.recycle()
                            scaled
                        }
                }
                if (bitmap != null) {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                    bitmap.recycle()
                    frames.add(StoryboardFrame(timestampMs, baos.toByteArray()))
                }
            }
        } catch (_: Exception) {
            // Best-effort thumbnails; editor continues cleanly even if frame extraction fails
        } finally {
            runCatching { retriever.release() }
        }
        return frames
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "raw_clip.mp4"
    }
}
