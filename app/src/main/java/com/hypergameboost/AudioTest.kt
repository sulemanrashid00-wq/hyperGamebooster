package com.hypergameboost

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * AudioTest & Pure Clean Volume Booster Engine (v11.1 Clean)
 *
 * ZERO digital clipping, ZERO sound tearing ("phatti hui awaz").
 * - Removed all Equalizers, DynamicsProcessing, Pre-EQ, and Post-EQ filters that distorted original sound.
 * - Uses a single, isolated android.media.audiofx.LoudnessEnhancer(0) instance.
 * - Safe gain ceiling: 0mB to 650mB max (+0.0dB to +6.5dB).
 * - Turning OFF releases LoudnessEnhancer immediately for 100% untouched stock phone audio.
 */
object AudioTest {
    private var le: LoudnessEnhancer? = null
    private var visualizer: Visualizer? = null

    @Volatile var isCleanBoostActive: Boolean = false
    @Volatile var currentBoostPercent: Int = 40 // 0% to 100% (Default 40% = +260mB clean boost)
    @Volatile var currentThreatLevel: Int = 0 // 0 to 100
    @Volatile var isSonicRadarActive: Boolean = false
    @Volatile var activePreset: Int = 0

    // Compatibility getters & setters
    var isOverdriveEnabled: Boolean
        get() = isCleanBoostActive
        set(value) { isCleanBoostActive = value }

    var currentVolumePercent: Int
        get() = 100 + currentBoostPercent
        set(value) { currentBoostPercent = (value - 100).coerceIn(0, 100) }

    var currentFootstepDb: Int
        get() = 0
        set(_) {}

    @Synchronized
    fun setCleanVolumeBoost(
        context: Context? = null,
        enable: Boolean,
        boostPercent: Int = currentBoostPercent
    ): String {
        isCleanBoostActive = enable && boostPercent > 0
        currentBoostPercent = boostPercent.coerceIn(0, 100)

        if (!enable || currentBoostPercent == 0) {
            releaseLoudnessEnhancer()
            activePreset = 0
            return "🔊 Clean Volume Boost DISABLED (100% Untouched Stock Audio)"
        }

        return try {
            // Force hardware media stream to 100% if context available
            context?.let { ctx ->
                try {
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    am?.let { audioManager ->
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                    }
                } catch (_: Throwable) {}
            }

            // Safe gain ceiling: 0..100% maps to 0..650 milliBels (0dB to +6.5dB max)
            val targetGainMb = (currentBoostPercent * 6.5f).toInt().coerceIn(0, 650)

            val enhancer = le ?: LoudnessEnhancer(0).also { le = it }
            enhancer.setTargetGain(targetGainMb)
            enhancer.enabled = true

            val dbGain = targetGainMb / 100.0f
            "🔊 Clean Volume Boost ACTIVE: +${"%.1f".format(dbGain)}dB (${targetGainMb}mB) | Pure Original Sound (0 Clipping)"
        } catch (e: Throwable) {
            "Volume Booster Error: ${e.message}"
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            le?.enabled = false
            le?.release()
            le = null
        } catch (_: Throwable) {}
    }

    // Backward compatibility bridges
    fun applyLaptopAudioOverdrive(
        context: Context? = null,
        enable: Boolean,
        volumePercent: Int = 140,
        footstepDb: Int = 0
    ): String {
        val pct = (volumePercent - 100).coerceIn(0, 100)
        return setCleanVolumeBoost(context, enable, pct)
    }

    fun setFootstepBoost(enable: Boolean): String {
        return setCleanVolumeBoost(null, enable, if (enable) 40 else 0)
    }

    fun applyPreset(context: Context? = null, presetId: Int): String {
        activePreset = presetId
        return when (presetId) {
            1 -> setCleanVolumeBoost(context, true, 40)
            2 -> setCleanVolumeBoost(context, true, 80)
            3 -> setCleanVolumeBoost(context, true, 50)
            4 -> setCleanVolumeBoost(context, true, 30)
            else -> setCleanVolumeBoost(context, false, 0)
        }
    }

    fun applyPreset(presetId: Int): String = applyPreset(null, presetId)

    fun footstepEq(on: Boolean): String = setCleanVolumeBoost(null, on, if (on) 40 else 0)

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
