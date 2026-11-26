package com.example.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import com.example.model.CaptionOverlayItem
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.CueAccent
import com.example.model.ExportQuality
import com.example.model.VideoSegment
import kotlin.math.min

/**
 * Builds Media3 video effects for vertical 9:16 framing, emotional push-in zoom,
 * Red/Green Sunnah cue accents, and timed on-screen captions via a single reusable BitmapOverlay.
 */
@OptIn(UnstableApi::class)
object SunnahVideoEffects {

    fun buildSegmentVideoEffects(
        segment: VideoSegment,
        segmentTimelineOffsetMs: Long,
        allCaptions: List<CaptionOverlayItem>,
        quality: ExportQuality,
        serifTypeface: Typeface?
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        // 1. Vertical 9:16 center-crop framing
        effects.add(
            Presentation.createForWidthAndHeight(
                quality.width,
                quality.height,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        // 2. Emotional Push-In / Beat Flash Matrix
        when (segment.cueAccent) {
            CueAccent.SLOW_ZOOM, CueAccent.GREEN_SUNNAH -> {
                val clipDurationUs = (segment.durationMs * 1_000L).coerceAtLeast(1_000_000L)
                effects.add(
                    MatrixTransformation { presentationTimeUs ->
                        val progress = (presentationTimeUs.toFloat() / clipDurationUs.toFloat())
                            .coerceIn(0f, 1f)
                        val scale = 1.0f + (0.09f * progress)
                        Matrix().apply { setScale(scale, scale) }
                    }
                )
            }
            CueAccent.FLASH_CUT -> {
                effects.add(
                    MatrixTransformation { presentationTimeUs ->
                        val tSec = presentationTimeUs / 1_000_000f
                        val scale = if (tSec < 0.25f) 1.10f - (0.40f * tSec) else 1.0f
                        Matrix().apply { setScale(scale, scale) }
                    }
                )
            }
            else -> Unit
        }

        // 3. Timed Captions + Red/Green Cue Borders via reusable Canvas BitmapOverlay
        val overlay = SunnahCanvasOverlay(
            width = quality.width,
            height = quality.height,
            segmentCue = segment.cueAccent,
            segmentTimelineOffsetMs = segmentTimelineOffsetMs,
            captions = allCaptions,
            serifTypeface = serifTypeface
        )
        effects.add(OverlayEffect(listOf(overlay)))

        return effects
    }
}

@OptIn(UnstableApi::class)
private class SunnahCanvasOverlay(
    private val width: Int,
    private val height: Int,
    private val segmentCue: CueAccent,
    private val segmentTimelineOffsetMs: Long,
    private val captions: List<CaptionOverlayItem>,
    serifTypeface: Typeface?
) : BitmapOverlay() {

    private val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = width * 0.052f
        typeface = serifTypeface ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
        setShadowLayer(12f, 0f, 4f, Color.argb(210, 0, 0, 0))
    }

    private val overlaySettings = StaticOverlaySettings.Builder()
        .setOverlayFrameAnchor(0f, 0f)
        .setBackgroundFrameAnchor(0f, 0f)
        .build()

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val localMs = presentationTimeUs / 1_000L
        val globalMs = segmentTimelineOffsetMs + localMs

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Draw subtle Red/Green visual cue aura or flash beat
        when (segmentCue) {
            CueAccent.RED_TENSION -> {
                drawCueEdgeGlow(Color.argb(120, 239, 68, 68), localMs)
            }
            CueAccent.GREEN_SUNNAH -> {
                drawCueEdgeGlow(Color.argb(120, 16, 185, 129), localMs)
            }
            CueAccent.FLASH_CUT -> {
                if (localMs < 220L) {
                    val alpha = ((1f - localMs / 220f) * 180f).toInt().coerceIn(0, 180)
                    canvas.drawColor(Color.argb(alpha, 255, 250, 235))
                }
            }
            else -> Unit
        }

        // Draw active captions at global composition timestamp
        val activeCaptions = captions.filter { globalMs in it.startMs..it.endMs }
        for (caption in activeCaptions) {
            drawCaption(caption)
        }

        return bitmap
    }

    private fun drawCueEdgeGlow(glowColor: Int, localMs: Long) {
        val strokeW = width * 0.018f
        borderPaint.strokeWidth = strokeW
        borderPaint.color = glowColor
        val inset = strokeW / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            width * 0.04f,
            width * 0.04f,
            borderPaint
        )

        // Soft bottom/top tint vignette
        val fadeIn = min(1f, localMs / 350f)
        val tintAlpha = (52 * fadeIn).toInt()
        val baseRgb = glowColor and 0x00FFFFFF
        bgPaint.shader = LinearGradient(
            0f,
            height * 0.72f,
            0f,
            height.toFloat(),
            Color.TRANSPARENT,
            (tintAlpha shl 24) or baseRgb,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height * 0.72f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null
    }

    private fun drawCaption(caption: CaptionOverlayItem) {
        val horizontalPadding = (width * 0.10f).toInt()
        val maxTextWidth = (width - horizontalPadding * 2).coerceAtLeast(100)

        val (textColor, pillColor, accentBorder) = when (caption.style) {
            CaptionStyle.SUNNAH_GOLD -> Triple(
                Color.rgb(255, 236, 179),
                Color.argb(175, 18, 16, 10),
                Color.argb(200, 232, 185, 84)
            )
            CaptionStyle.CRIMSON_CONTRAST -> Triple(
                Color.rgb(254, 226, 226),
                Color.argb(185, 42, 10, 12),
                Color.argb(210, 239, 68, 68)
            )
            CaptionStyle.EMERALD_HOPE -> Triple(
                Color.rgb(209, 250, 229),
                Color.argb(185, 7, 36, 26),
                Color.argb(210, 16, 185, 129)
            )
            CaptionStyle.CLEAN_MINIMAL -> Triple(
                Color.WHITE,
                Color.argb(150, 10, 13, 18),
                Color.argb(140, 244, 246, 249)
            )
        }

        textPaint.color = textColor
        val layout = StaticLayout.Builder
            .obtain(caption.text, 0, caption.text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(6f, 1.1f)
            .build()

        val textHeight = layout.height.toFloat()
        val centerY = when (caption.position) {
            CaptionPosition.TOP -> height * 0.16f
            CaptionPosition.CENTER -> height * 0.50f
            CaptionPosition.BOTTOM -> height * 0.76f
        }
        val topY = centerY - textHeight / 2f

        // Find widest line for compact pill framing
        var widestLine = 0f
        for (line in 0 until layout.lineCount) {
            widestLine = maxOf(widestLine, layout.getLineWidth(line))
        }
        val pillPadX = width * 0.045f
        val pillPadY = width * 0.025f
        val pillLeft = (width - widestLine) / 2f - pillPadX
        val pillRight = (width + widestLine) / 2f + pillPadX
        val pillRect = RectF(
            pillLeft.coerceAtLeast(width * 0.05f),
            topY - pillPadY,
            pillRight.coerceAtMost(width * 0.95f),
            topY + textHeight + pillPadY
        )

        pillPaint.color = pillColor
        pillPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(pillRect, width * 0.025f, width * 0.025f, pillPaint)

        borderPaint.color = accentBorder
        borderPaint.strokeWidth = width * 0.004f
        canvas.drawRoundRect(pillRect, width * 0.025f, width * 0.025f, borderPaint)

        canvas.save()
        canvas.translate(horizontalPadding.toFloat(), topY)
        layout.draw(canvas)
        canvas.restore()
    }
}
