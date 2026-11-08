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
 * AudioTest & Tactical Audio Hunter Engine (v6.0 Esports Master)
 * - 4 Esports Presets: Footstep Hunter, Gunshot Suppressor, Vehicle Rumble Cut, Pro Flat
 * - RedMagic "Sonic Radar" Visualizer(0) capturing 1kHz–4kHz footstep/gunshot frequencies
 * - 44.1kHz Stereo PCM sine wave tester (520Hz Left / 780Hz Right)
 */
object AudioTest {
    private var eq: Equalizer? = null
    private var le: LoudnessEnhancer? = null
    private var visualizer: Visualizer? = null

    @Volatile var currentThreatLevel: Int = 0 // 0 to 100
    @Volatile var isSonicRadarActive: Boolean = false
    @Volatile var activePreset: Int = 0 // 0: Off, 1: Footstep, 2: Gunshot, 3: Vehicle, 4: Pro Flat

    fun applyPreset(preset: Int): String {
        activePreset = preset
        if (preset == 0) {
            eq?.enabled = false
            eq?.release()
            eq = null
            le?.enabled = false
            le?.release()
            le = null
            return "Tactical Audio FX DISABLED (Stock sound profile)"
        }

        return try {
            val e = eq ?: Equalizer(0, 0).also { eq = it }
            val lo = e.bandLevelRange[0]
            val hi = e.bandLevelRange[1]
            val l = le ?: LoudnessEnhancer(0).also { le = it }

            for (b in 0 until e.numberOfBands) {
                val hz = e.getCenterFreq(b.toShort()) / 1000
                val level = when (preset) {
                    1 -> when { // Footstep Hunter
                        hz < 180 -> (lo * 0.7f).toInt().toShort()
                        hz in 900..4500 -> (hi * 0.8f).toInt().toShort()
                        else -> 0
                    }
                    2 -> when { // Gunshot Peak Suppressor
                        hz > 4000 -> (lo * 0.5f).toInt().toShort()
                        hz in 800..3000 -> (hi * 0.4f).toInt().toShort()
                        else -> 0
                    }
                    3 -> when { // Vehicle Rumble Cut
                        hz < 250 -> (lo * 0.9f).toInt().toShort()
                        hz in 1000..4000 -> (hi * 0.5f).toInt().toShort()
                        else -> 0
                    }
                    4 -> 0.toShort() // Pro Flat
                    else -> 0.toShort()
                }
                e.setBandLevel(b.toShort(), level)
            }
            e.enabled = true

            val gain = when (preset) {
                1 -> 600
                2 -> 400
                3 -> 500
                4 -> 500
                else -> 0
            }
            l.setTargetGain(gain)
            l.enabled = true

            when (preset) {
                1 -> "🎧 Footstep Hunter ACTIVE: +6dB Footsteps (1-4.5kHz), Bass Sub-Cut, Gain +600mB"
                2 -> "🎧 Gunshot Suppressor ACTIVE: High-Peak Dampened, Mid-Range Boosted"
                3 -> "🎧 Vehicle Rumble Cut ACTIVE: Engine Frequencies Muted, Nearby Steps Clear"
                else -> "🎧 Pro Flat Competitive ACTIVE: Clean Acoustics with +500mB Loudness"
            }
        } catch (e: Throwable) {
            "Audio FX error: ${e.message}"
        }
    }

    /** Legacy helper for backward compatibility */
    fun footstepEq(on: Boolean): String {
        return applyPreset(if (on) 1 else 0)
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
                            val threat = (avgMag * 3.5).toInt().coerceIn(0, 100)
                            currentThreatLevel = threat
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                this.captureSize = captureSize
                enabled = true
            }
            visualizer = vis
            isSonicRadarActive = true
            "Sonic Radar ACTIVE (Listening on AudioSession 0)"
        } catch (e: Throwable) {
            "Sonic Radar error: ${e.message}"
        }
    }

    @Synchronized
    fun stopSonicRadar(): String {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        isSonicRadarActive = false
        currentThreatLevel = 0
        return "Sonic Radar STOPPED"
    }

    /**
     * Stereo Left/Right Audio Channel Diagnostic:
     * Plays 520Hz pure tone on Left Channel (0), 780Hz on Right Channel (1).
     */
    fun play(channel: Int) {
        val sampleRate = 44100
        val durationMs = 350
        val numSamples = (durationMs * sampleRate) / 1000
        val freq = if (channel == 0) 520.0 else 780.0
        val buffer = ShortArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i / (sampleRate / freq)
            val sample = (sin(angle) * Short.MAX_VALUE * 0.75).toInt().toShort()
            if (channel == 0) {
                buffer[2 * i] = sample
                buffer[2 * i + 1] = 0
            } else {
                buffer[2 * i] = 0
                buffer[2 * i + 1] = sample
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
    }
}
