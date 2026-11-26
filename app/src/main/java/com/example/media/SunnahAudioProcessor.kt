package com.example.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Real-time PCM AudioProcessor for volume balancing and warm Schroeder-style stereo reverb.
 * Works identically in both live ExoPlayer preview and Media3 Transformer export.
 */
@OptIn(UnstableApi::class)
class ReverbAndGainAudioProcessor(
    @Volatile var gain: Float = 1.0f,
    @Volatile var reverbWet: Float = 0.0f
) : BaseAudioProcessor() {

    private var delayBuffers: Array<FloatArray> = emptyArray()
    private var delayIndices: IntArray = IntArray(0)
    private var lowPassState: Float = 0f

    // Prime delay lengths in ms for lush, non-metallic ambient tail
    private val tapDelaysMs = intArrayOf(29, 43, 61, 89)
    private val tapFeedbacks = floatArrayOf(0.56f, 0.50f, 0.44f, 0.38f)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        val ch = inputAudioFormat.channelCount.coerceAtLeast(1)
        delayBuffers = Array(tapDelaysMs.size) { tapIdx ->
            val len = ((sr * tapDelaysMs[tapIdx]) / 1000) * ch
            FloatArray(len.coerceAtLeast(ch))
        }
        delayIndices = IntArray(tapDelaysMs.size)
        lowPassState = 0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outBuffer = replaceOutputBuffer(remaining)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val inSlice = inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)

        val currentGain = gain.coerceIn(0f, 1.5f)
        val wet = reverbWet.coerceIn(0f, 0.85f)
        val dry = 1.0f - (wet * 0.35f)
        val useReverb = wet > 0.01f && delayBuffers.isNotEmpty()

        while (inSlice.remaining() >= 2) {
            val rawSample = inSlice.short.toFloat() / 32768f
            val processed = if (useReverb) {
                var reverbSum = 0f
                for (t in delayBuffers.indices) {
                    val buf = delayBuffers[t]
                    val idx = delayIndices[t]
                    val delayed = buf[idx]
                    // Warm damping inside feedback loop
                    val damped = delayed * 0.82f + lowPassState * 0.18f
                    lowPassState = damped
                    buf[idx] = (rawSample + damped * tapFeedbacks[t]).coerceIn(-1.2f, 1.2f)
                    delayIndices[t] = (idx + 1) % buf.size
                    reverbSum += delayed
                }
                val wetSample = reverbSum / delayBuffers.size.toFloat()
                (rawSample * dry + wetSample * wet) * currentGain
            } else {
                rawSample * currentGain
            }

            val clamped = (processed * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
            outBuffer.putShort(clamped)
        }

        inputBuffer.position(inputBuffer.limit())
        outBuffer.flip()
    }

    override fun onFlush() {
        for (buf in delayBuffers) {
            buf.fill(0f)
        }
        delayIndices.fill(0)
        lowPassState = 0f
    }

    override fun onReset() {
        delayBuffers = emptyArray()
        delayIndices = IntArray(0)
        lowPassState = 0f
    }
}
