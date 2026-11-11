package com.hypergameboost

import android.content.Context
import android.content.SharedPreferences

/**
 * AppPrefs - Centralized Profile & SharedPreferences Accessor
 * Ensures Actions.kt, AutoService.kt, and MainActivity.kt always access
 * consistent, persistent user configurations (downscale, FPS, Dolby, audio boost).
 */
object AppPrefs {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun getSp(): SharedPreferences? =
        appContext?.getSharedPreferences("hgb_profiles", Context.MODE_PRIVATE)

    fun getDownscale(game: String): String {
        val raw = getSp()?.getString("ds_$game", "0.75") ?: "0.75"
        // Normalize Android 16 downscaleFactor
        return when (raw) {
            "0.50", "0.55" -> "0.55"
            "0.60", "0.65" -> "0.65"
            "0.70", "0.75" -> "0.75"
            "0.80", "0.85" -> "0.85"
            "1.0", "1.00", "disable" -> "disable"
            else -> raw
        }
    }

    fun setDownscale(game: String, downscale: String) {
        getSp()?.edit()?.putString("ds_$game", downscale)?.apply()
    }

    fun getFps(game: String): Int {
        return getSp()?.getInt("fps_$game", 120) ?: 120
    }

    fun setFps(game: String, fps: Int) {
        getSp()?.edit()?.putInt("fps_$game", fps)?.apply()
    }

    fun getDolbyPreset(game: String): Int {
        return getSp()?.getInt("dolby_preset_$game", 0) ?: 0
    }

    fun setDolbyPreset(game: String, preset: Int) {
        getSp()?.edit()?.putInt("dolby_preset_$game", preset)?.apply()
    }

    fun getCleanBoost(game: String): Boolean {
        return getSp()?.getBoolean("clean_boost_$game", true) ?: true
    }

    fun setCleanBoost(game: String, enabled: Boolean) {
        getSp()?.edit()?.putBoolean("clean_boost_$game", enabled)?.apply()
    }

    fun getCleanBoostPct(game: String): Int {
        return getSp()?.getInt("clean_boost_pct_$game", 50) ?: 50
    }

    fun setCleanBoostPct(game: String, pct: Int) {
        getSp()?.edit()?.putInt("clean_boost_pct_$game", pct)?.apply()
    }
}
