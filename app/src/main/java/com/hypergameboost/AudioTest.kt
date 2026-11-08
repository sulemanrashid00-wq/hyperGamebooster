package com.hypergameboost

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * AudioTest & RedMagic Sonic Radar Engine (v3.0 Ultimate)
 * - 5-Band Tactical Footstep Equalizer & LoudnessEnhancer (+600mB)
 * - 44.1kHz Stereo PCM sine wave tester (520Hz Left / 780Hz Right)
 * - RedMagic "Sonic Radar" Visualizer(0) capturing 1kHz–4kHz footstep/gunshot frequencies
 */
object AudioTest {
    private var eq: Equalizer? = null
    private var le: LoudnessEnhancer? = null
    private var visualizer: Visualizer? = null

    @Volatile var currentThreatLevel: Int = 0 // 0 to 100
    @Volatile var isSonicRadarActive: Boolean = false

    /**
     * Dirac / Spatial Footstep Audio 5-Band Equalizer:
     * - Cuts bass < 180Hz to prevent grenade concussion masking footsteps
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
     * RedMagic "Sonic Radar" Engine:
     * Attaches android.media.audiofx.Visualizer(0) to read live FFT data
     * Computes amplitude in 1kHz–4kHz range to alert user of nearby footsteps/fire
     */
    @Synchronized
    fun startSonicRadar(): String {
        if (isSonicRadarActive) return "Sonic Radar already active"
        return try {
            val captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
            val vis = Visualizer(0).apply {
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft == null || fft.size < 4) return
                            // fft format: [real0, imag0, real1, imag1, ...]
                            // Calculate energy around 1kHz to 4kHz (footstep and gunshot frequencies)
                            val n = fft.size / 2
                            val binHz = (samplingRate.toDouble() / 1000.0) / (2.0 * n)
                            var totalMag = 0.0
                            var countedBins = 0

                            for (i in 1 until n) {
                                val freq = i * binHz
                                if (freq in 1.0..4.5) { // 1000Hz to 4500Hz
                                    val r = fft[2 * i].toDouble()
                                    val im = fft[2 * i + 1].toDouble()
                                    totalMag += hypot(r, im)
                                    countedBins++
                                }
                            }

                            val avgMag = if (countedBins > 0) totalMag / countedBins else 0.0
                            // Normalize 0-100 scale
                            val threat = (avgMag * 2.2).toInt().coerceIn(0, 100)
                            currentThreatLevel = threat
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                setCaptureSize(captureSize)
                enabled = true
            }
            visualizer = vis
            isSonicRadarActive = true
            "Sonic Radar Visualizer ACTIVE (1kHz–4.5kHz Live Compass)"
        } catch (e: Throwable) {
            isSonicRadarActive = false
            "Sonic Radar unavailable: ${e.message}"
        }
    }

    @Synchronized
    fun stopSonicRadar(): String {
        return try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
            isSonicRadarActive = false
            currentThreatLevel = 0
            "Sonic Radar STOPPED"
        } catch (e: Throwable) {
            "Sonic Radar stop error: ${e.message}"
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
