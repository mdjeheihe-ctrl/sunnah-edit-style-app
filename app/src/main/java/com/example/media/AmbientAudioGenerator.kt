package com.example.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates a warm, emotional harmonic ambient pad (.wav) in cacheDir so creators
 * can immediately preview & export slowed + reverb background mixing without external audio files.
 */
object AmbientAudioGenerator {

    private const val SAMPLE_RATE = 44_100
    private const val CHANNELS = 2
    private const val DURATION_SECONDS = 16
    private const val FILE_NAME = "noorcut_ambient_pad.wav"

    suspend fun ensureAmbientTrackUri(context: Context): Uri = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, FILE_NAME)
        if (outFile.exists() && outFile.length() > 100_000L) {
            return@withContext Uri.fromFile(outFile)
        }

        val totalSamples = SAMPLE_RATE * DURATION_SECONDS
        val dataByteCount = totalSamples * CHANNELS * 2
        val byteRate = SAMPLE_RATE * CHANNELS * 2

        BufferedOutputStream(FileOutputStream(outFile), 16_384).use { out ->
            // Write standard 44-byte RIFF/WAVE header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataByteCount)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16) // PCM chunk size
                putShort(1) // PCM format
                putShort(CHANNELS.toShort())
                putInt(SAMPLE_RATE)
                putInt(byteRate)
                putShort((CHANNELS * 2).toShort())
                putShort(16) // Bits per sample
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataByteCount)
            }
            out.write(header.array())

            // Emotional 4-chord pad progression (Dm9 -> Bbmaj7 -> F -> Csus4)
            val chords = listOf(
                doubleArrayOf(146.83, 220.00, 261.63, 329.63),
                doubleArrayOf(116.54, 174.61, 220.00, 293.66),
                doubleArrayOf(174.61, 220.00, 261.63, 349.23),
                doubleArrayOf(130.81, 196.00, 261.63, 349.23)
            )
            val samplesPerChord = totalSamples / chords.size
            val chunkBuf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE.toDouble()
                val chordIdx = (i / samplesPerChord).coerceIn(0, chords.lastIndex)
                val posInChord = (i % samplesPerChord).toDouble() / samplesPerChord.toDouble()
                // Smooth breath envelope for seamless looping without clicks
                val envelope = sin(PI * posInChord).coerceIn(0.0, 1.0)
                val chord = chords[chordIdx]

                var left = 0.0
                var right = 0.0
                for (n in chord.indices) {
                    val freq = chord[n]
                    val detune = 1.0 + (if (n % 2 == 0) 0.002 else -0.002)
                    val fundamental = sin(2.0 * PI * freq * t)
                    val shimmer = 0.25 * sin(2.0 * PI * (freq * 2.0 * detune) * t)
                    val sub = 0.2 * sin(2.0 * PI * (freq * 0.5) * t)
                    val voice = (fundamental + shimmer + sub) * 0.14
                    if (n % 2 == 0) {
                        left += voice * 0.75
                        right += voice * 0.55
                    } else {
                        left += voice * 0.55
                        right += voice * 0.75
                    }
                }

                val lSample = (left * envelope * 22_000.0).toInt().coerceIn(-32767, 32767).toShort()
                val rSample = (right * envelope * 22_000.0).toInt().coerceIn(-32767, 32767).toShort()

                if (chunkBuf.remaining() < 4) {
                    out.write(chunkBuf.array(), 0, chunkBuf.position())
                    chunkBuf.clear()
                }
                chunkBuf.putShort(lSample)
                chunkBuf.putShort(rSample)
            }
            if (chunkBuf.position() > 0) {
                out.write(chunkBuf.array(), 0, chunkBuf.position())
            }
        }
        Uri.fromFile(outFile)
    }
}
