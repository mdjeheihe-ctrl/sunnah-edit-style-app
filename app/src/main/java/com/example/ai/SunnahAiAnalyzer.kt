package com.example.ai

import android.graphics.BitmapFactory
import android.util.Log
import com.example.model.CaptionOverlayItem
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CueAccent
import com.example.model.StoryboardFrame
import com.example.model.VideoSegment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "SunnahAiAnalyzer"

data class AiEditPlan(
    val narrativeSummary: String,
    val segments: List<VideoSegment>,
    val captions: List<CaptionOverlayItem>
)

class SunnahAiAnalyzer {

    private val editPlanSchema = Schema.obj(
        properties = mapOf(
            "narrativeSummary" to Schema.string(
                description = "A concise 1-2 sentence summary of the emotional arc for the 30-40s Sunnah Edit."
            ),
            "segments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "label" to Schema.string(description = "Short beat label, e.g. Conflict Hook or Sunnah Turning Point"),
                        "startMs" to Schema.integer(description = "Start timestamp in the raw video in milliseconds"),
                        "endMs" to Schema.integer(description = "End timestamp in the raw video in milliseconds"),
                        "cueAccent" to Schema.enumeration(
                            values = listOf("RED_TENSION", "GREEN_SUNNAH", "FLASH_CUT", "SLOW_ZOOM", "NONE"),
                            description = "RED_TENSION for struggle/contrast, GREEN_SUNNAH for mercy/kindness turning point, FLASH_CUT or SLOW_ZOOM for emphasis"
                        )
                    )
                ),
                description = "3 to 5 non-overlapping segments totaling 30000 to 40000 milliseconds."
            ),
            "captions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "text" to Schema.string(description = "Short punchy on-screen caption (4 to 9 words max)"),
                        "startMs" to Schema.integer(description = "Start timestamp in the final edited 30-40s timeline in milliseconds"),
                        "endMs" to Schema.integer(description = "End timestamp in the final edited 30-40s timeline in milliseconds"),
                        "style" to Schema.enumeration(
                            values = listOf("SUNNAH_GOLD", "CRIMSON_CONTRAST", "EMERALD_HOPE", "CLEAN_MINIMAL"),
                            description = "Visual pill accent style"
                        ),
                        "position" to Schema.enumeration(
                            values = listOf("TOP", "CENTER", "BOTTOM"),
                            description = "Vertical placement inside 9:16 frame"
                        )
                    )
                ),
                description = "3 to 5 timed on-screen captions matching the emotional arc."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = editPlanSchema
                temperature = 0.45f
            }
        )
    }

    suspend fun generateSunnahEditPlan(
        sourceDurationMs: Long,
        topicPrompt: String,
        storyboardFrames: List<StoryboardFrame>
    ): AiEditPlan = withContext(Dispatchers.IO) {
        val bitmaps = storyboardFrames.mapNotNull { frame ->
            runCatching {
                BitmapFactory.decodeByteArray(frame.jpegBytes, 0, frame.jpegBytes.size)
            }.getOrNull()?.let { bmp -> frame.timestampMs to bmp }
        }

        try {
            val promptText = buildString {
                appendLine("You are an expert short-form 'Sunnah Edit' video director.")
                appendLine("Goal: Turn a raw ${sourceDurationMs / 1000}s video into a tight, deeply emotional 32-38 second 9:16 inspirational reel.")
                if (topicPrompt.isNotBlank()) {
                    appendLine("Creator theme/context: $topicPrompt")
                }
                appendLine("Rules:")
                appendLine("1. Select 3 to 5 non-overlapping segments from 0 to $sourceDurationMs ms whose combined duration is between 30000 and 40000 ms (if raw video < 35000 ms, use its full length).")
                appendLine("2. Use RED_TENSION on opening tension/struggle beats and GREEN_SUNNAH on kindness/patience/mercy turning points.")
                appendLine("3. Write 3 to 5 heartfelt, concise captions (4-9 words each) timed along the final 0..36000 ms output timeline.")
                if (bitmaps.isNotEmpty()) {
                    appendLine("Sampled frames with raw timestamps follow:")
                }
            }

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(promptText)
                        bitmaps.forEach { (tsMs, bmp) ->
                            text("Frame at ${tsMs}ms:")
                            image(bmp)
                        }
                    }
                )
            }

            val jsonText = response.text
                ?: throw IllegalStateException("AI Director returned an empty response.")
            parsePlanJson(jsonText, sourceDurationMs)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "AI analysis timed out", e)
            throw IllegalStateException("AI analysis timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI analysis failed", e)
            throw e
        } finally {
            bitmaps.forEach { (_, bmp) -> runCatching { bmp.recycle() } }
        }
    }

    private fun parsePlanJson(jsonString: String, maxDurationMs: Long): AiEditPlan {
        val root = JSONObject(jsonString)
        val summary = root.optString("narrativeSummary", "Emotional Sunnah Edit arc ready.")

        val segArray = root.optJSONArray("segments")
        val segments = mutableListOf<VideoSegment>()
        if (segArray != null) {
            for (i in 0 until segArray.length()) {
                val obj = segArray.getJSONObject(i)
                val rawStart = obj.optLong("startMs", 0L).coerceIn(0L, maxDurationMs - 500L)
                val rawEnd = obj.optLong("endMs", rawStart + 6_000L)
                    .coerceIn(rawStart + 1_500L, maxDurationMs)
                val cueStr = obj.optString("cueAccent", "NONE")
                val cue = runCatching { CueAccent.valueOf(cueStr) }.getOrDefault(CueAccent.NONE)
                segments.add(
                    VideoSegment(
                        label = obj.optString("label", "Beat ${i + 1}"),
                        startMs = rawStart,
                        endMs = rawEnd,
                        enabled = true,
                        cueAccent = cue,
                        energyScore = 0.85f
                    )
                )
            }
        }

        val totalEditMs = segments.sumOf { it.durationMs }.coerceAtLeast(10_000L)
        val capArray = root.optJSONArray("captions")
        val captions = mutableListOf<CaptionOverlayItem>()
        if (capArray != null) {
            for (i in 0 until capArray.length()) {
                val obj = capArray.getJSONObject(i)
                val start = obj.optLong("startMs", 0L).coerceIn(0L, totalEditMs - 1_000L)
                val end = obj.optLong("endMs", start + 4_000L)
                    .coerceIn(start + 1_000L, totalEditMs)
                val style = runCatching {
                    CaptionStyle.valueOf(obj.optString("style", "SUNNAH_GOLD"))
                }.getOrDefault(CaptionStyle.SUNNAH_GOLD)
                val position = runCatching {
                    CaptionPosition.valueOf(obj.optString("position", "BOTTOM"))
                }.getOrDefault(CaptionPosition.BOTTOM)
                val text = obj.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    captions.add(
                        CaptionOverlayItem(
                            text = text,
                            startMs = start,
                            endMs = end,
                            position = position,
                            style = style
                        )
                    )
                }
            }
        }

        return AiEditPlan(
            narrativeSummary = summary,
            segments = segments.sortedBy { it.startMs },
            captions = captions.sortedBy { it.startMs }
        )
    }
}
