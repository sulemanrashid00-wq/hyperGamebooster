package com.hypergameboost

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.PI
import kotlin.math.sin

object AudioTest {
    private var eq: Equalizer? = null
    private var le: LoudnessEnhancer? = null

    /** Sound preset: low rumble kam, 1-4 kHz thora zyada. Global audio effect, har device par kaam nahi karta. */
    fun footstepEq(on: Boolean): String {
        return try {
            if (on) {
                val e = eq ?: Equalizer(0, 0).also { eq = it }
                val lo = e.bandLevelRange[0]; val hi = e.bandLevelRange[1]
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000
                    e.setBandLevel(b.toShort(), when {
                        hz < 180 -> (lo / 2).toShort()
                        hz in 900..4500 -> (hi / 2).toShort()
                        else -> 0
                    })
                }
                e.enabled = true
                val l = le ?: LoudnessEnhancer(0).also { le = it }
                l.setTargetGain(300); l.enabled = true
                "Sound preset ON (1-4kHz up, bass down). Kaam na kare to device is effect ko support nahi karta."
            } else {
                eq?.enabled = false; eq?.release(); eq = null
                le?.enabled = false; le?.release(); le = null
                "Sound preset OFF"
            }
        } catch (e: Throwable) { "Sound preset error: ${e.message}" }
    }

    /** side: 0 = sirf left, 1 = sirf right. 1 second ka 440 Hz tone (earphone L/R check). */
    fun play(side: Int) {
        Thread {
            try {
                val sr = 44100
                val buf = ShortArray(sr * 2)
                for (i in 0 until sr) {
                    val v = (sin(2.0 * PI * 440.0 * i / sr) * 0.4 * Short.MAX_VALUE).toInt().toShort()
                    if (side == 0) buf[2 * i] = v else buf[2 * i + 1] = v
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(1100)
                track.release()
            } catch (e: Throwable) {
                // audio device busy ho to chup-chaap ignore
            }
        }.start()
    }
}
