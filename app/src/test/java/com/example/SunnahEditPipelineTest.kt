package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.media.ReverbAndGainAudioProcessor
import com.example.media.VideoAnalyzer
import com.example.model.CueAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SunnahEditPipelineTest {

    @Test
    fun smartAutoTrim_targets30To40Seconds_fromThreeMinuteVideo() {
        val rawDurationMs = 180_000L // 3 minutes
        val energies = List(180) { idx ->
            if (idx in 15..25 || idx in 95..110) 0.95f else 0.35f
        }
        val segments = VideoAnalyzer.buildSmartSunnahCuts(rawDurationMs, energies)

        assertTrue("Expected at least 3 segments", segments.size >= 3)
        val totalMs = segments.sumOf { it.durationMs }
        assertTrue("Expected total duration between 30s and 40s, got ${totalMs}ms", totalMs in 30_000L..40_000L)
        assertEquals(CueAccent.RED_TENSION, segments.first().cueAccent)
        assertTrue(segments.any { it.cueAccent == CueAccent.GREEN_SUNNAH })
    }

    @Test
    fun smartAutoTrim_preservesShortClipsGracefully() {
        val rawDurationMs = 22_000L
        val segments = VideoAnalyzer.buildSmartSunnahCuts(rawDurationMs)
        val totalMs = segments.sumOf { it.durationMs }
        assertEquals(22_000L, totalMs)
    }

    @Test
    fun reverbAndGainProcessor_outputsSameFrameByteCount() {
        val processor = ReverbAndGainAudioProcessor(gain = 0.5f, reverbWet = 0.4f)
        val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val samples = 1024
        val input = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            input.putShort(10_000)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertEquals(samples * 2, output.remaining())
    }
}
