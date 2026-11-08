package com.hypergameboost

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.PI
import kotlin.math.sin

/**
 * 5-Band Tactical Footstep Equalizer & Stereo Channel Tester (Section 2 & 3)
 * - Boosts 1kHz-4.5kHz footstep sound frequency band
 * - Cuts low-frequency rumble & grenade bass (<180Hz)
 * - LoudnessEnhancer +600mB gain
 * - 44.1kHz Stereo PCM test generator (520Hz Left / 780Hz Right)
 */
object AudioTest {
    private var eq: Equalizer? = null
    private var le: LoudnessEnhancer? = null

    /**
     * Dirac / Spatial Footstep Audio 5-Band Equalizer:
     * - Cuts bass < 180Hz by -50% to prevent grenade concussion masking footsteps
     * - Boosts 1000Hz - 4500Hz footsteps & reload clicks by +50%
     * - LoudnessEnhancer +600mB gain boost
     */
    fun footstepEq(on: Boolean): String {
        return try {
            if (on) {
                val e = eq ?: Equalizer(0, 0).also { eq = it }
                val lo = e.bandLevelRange[0]
                val hi = e.bandLevelRange[1]
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000
                    e.setBandLevel(
                        b.toShort(),
                        when {
                            hz < 180 -> (lo / 2).toShort()
                            hz in 900..4500 -> (hi / 2).toShort()
                            else -> 0
                        }
                    )
                }
                e.enabled = true

                val l = le ?: LoudnessEnhancer(0).also { le = it }
                l.setTargetGain(600)
                l.enabled = true

                "Tactical Footstep EQ ACTIVE: 1kHz-4.5kHz +6dB Boost, <180Hz Bass Cut, Loudness +600mB"
            } else {
                eq?.enabled = false
                eq?.release()
                eq = null
                le?.enabled = false
                le?.release()
                le = null
                "Tactical Footstep EQ DISABLED (Audio profile restored)"
            }
        } catch (e: Throwable) {
            "Audio FX error: ${e.message}"
        }
    }

    /**
     * Generates PCM 44.1kHz sine wave tones:
     * side: 0 = 520Hz Left earphone only
     * side: 1 = 780Hz Right earphone only
     * side: 2 = Both channels
     */
    fun play(side: Int) {
        Thread {
            try {
                val sampleRate = 44100
                val freq = if (side == 0) 520.0 else 780.0
                val durationSec = 1.0
                val numSamples = (durationSec * sampleRate).toInt()
                val buffer = ShortArray(numSamples * 2)

                for (i in 0 until numSamples) {
                    val sample = (sin(2.0 * PI * freq * i / sampleRate) * 0.5 * Short.MAX_VALUE).toInt().toShort()
                    when (side) {
                        0 -> buffer[2 * i] = sample           // Left channel
                        1 -> buffer[2 * i + 1] = sample       // Right channel
                        else -> {
                            buffer[2 * i] = sample
                            buffer[2 * i + 1] = sample
                        }
                    }
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(1100)
                track.release()
            } catch (_: Throwable) { }
        }.start()
    }
}
