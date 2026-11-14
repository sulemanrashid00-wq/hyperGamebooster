package com.hypergameboost

import android.content.Context
import android.content.SharedPreferences

/**
 * GfxConfig - Container for complete hardware framebuffer resolution & downscale parameters.
 */
data class GfxConfig(
    val presetId: Int,
    val label: String,
    val wmSize: String,       // e.g. "810x1800" or "reset"
    val density: String,      // e.g. "310" or "reset"
    val downscale: String,    // e.g. "0.55" or "disable"
    val fps: Int = 120,
    val lodBias: Int = 3
)

/**
 * AppPrefs - Unified Persistent State Engine (v17.0 Auto-Locked Custom Profile)
 * Pre-configured with the exact 22-point master profile:
 * 810p Scrims (810x1800 @ 310 DPI, 0.55 downscale, 120 FPS, LOD +3)
 * Dolby Voice/Scout (Preset 2), 70% Clean Volume, 480Hz Jiggle God, 100/100/100 Touch IC,
 * SkiaGL + System Driver, FastConnect Wi-Fi, Bypass Charging & Type-C Splitter Guard.
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

    // 1. Target Package
    fun getSelectedPkg(): String =
        getSp()?.getString("selected_pkg", "com.tencent.ig") ?: "com.tencent.ig"

    fun setSelectedPkg(pkg: String) {
        getSp()?.edit()?.putString("selected_pkg", pkg)?.apply()
    }

    // 2. GFX Presets & Framebuffer Config
    // 0 = Native 1.5K (reset, reset, disable)
    // 1 = 720p Ultra-Potato (720x1600, 280 DPI, scale 0.55)
    // 2 = 810p Scrims (810x1800, 310 DPI, scale 0.55) [v17.0 DEFAULT]
    // 3 = 900p Pro (900x2000, 350 DPI, scale 0.75)
    // 4 = Stretched Wide Hitbox (1080x2160, 360 DPI, scale 0.80)
    // 5 = iPad 4:3 View (1200x1600, 300 DPI, scale 0.85)
    // 6 = Custom PGT+ (custom wm size & scale)
    fun getGfxPreset(game: String): Int =
        getSp()?.getInt("gfx_preset_$game", 2) ?: 2

    fun setGfxPreset(game: String, preset: Int) {
        getSp()?.edit()?.putInt("gfx_preset_$game", preset)?.apply()
    }

    fun getCustomWmSize(game: String): String =
        getSp()?.getString("custom_wm_size_$game", "810x1800") ?: "810x1800"

    fun getCustomWmDensity(game: String): String =
        getSp()?.getString("custom_wm_density_$game", "310") ?: "310"

    fun getCustomDownscale(game: String): String =
        getSp()?.getString("custom_scale_$game", "0.55") ?: "0.55"

    fun setCustomGfx(game: String, size: String, density: String, scale: String) {
        getSp()?.edit()
            ?.putInt("gfx_preset_$game", 6)
            ?.putString("custom_wm_size_$game", size)
            ?.putString("custom_wm_density_$game", density)
            ?.putString("custom_scale_$game", scale)
            ?.apply()
    }

    fun getEffectiveGfxConfig(game: String): GfxConfig {
        val preset = getGfxPreset(game)
        val fps = getTargetFps(game)
        val lod = getLodBias(game)
        return when (preset) {
            0 -> GfxConfig(0, "Native 1.5K (Stock)", "reset", "reset", "disable", fps, 0)
            1 -> GfxConfig(1, "720p Ultra-Potato", "720x1600", "280", "0.55", fps, lod)
            2 -> GfxConfig(2, "810p Scrims (800x600 Ultra)", "810x1800", "310", "0.55", fps, lod)
            3 -> GfxConfig(3, "900p Pro", "900x2000", "350", "0.75", fps, lod)
            4 -> GfxConfig(4, "Stretched Wide Hitbox", "1080x2160", "360", "0.80", fps, lod)
            5 -> GfxConfig(5, "iPad 4:3 View", "1200x1600", "300", "0.85", fps, lod)
            6 -> {
                val size = getCustomWmSize(game)
                val density = getCustomWmDensity(game)
                val scale = getCustomDownscale(game)
                GfxConfig(6, "Custom PGT+ ($size @ ${density}DPI, $scale)", size, density, scale, fps, lod)
            }
            else -> GfxConfig(2, "810p Scrims (800x600 Ultra)", "810x1800", "310", "0.55", fps, lod)
        }
    }

    // 3. Target FPS (default 120)
    fun getTargetFps(game: String): Int =
        getSp()?.getInt("fps_$game", 120) ?: 120

    fun setTargetFps(game: String, fps: Int) {
        getSp()?.edit()?.putInt("fps_$game", fps)?.apply()
    }

    // 4. LOD Bias (default 3 for blurry zero-lag textures)
    fun getLodBias(game: String): Int =
        getSp()?.getInt("lod_bias_$game", 3) ?: 3

    fun setLodBias(game: String, lod: Int) {
        getSp()?.edit()?.putInt("lod_bias_$game", lod)?.apply()
    }

    // 5. Color Filter Mode: 0 = Stock 1.0x, 1 = Vivid 1.5x, 2 = Contrast 1.25x, 3 = Scout Invert
    fun getColorFilterMode(game: String): Int =
        getSp()?.getInt("color_filter_$game", 1) ?: 1

    fun setColorFilterMode(game: String, mode: Int) {
        getSp()?.edit()?.putInt("color_filter_$game", mode)?.apply()
    }

    fun getColorSaturationValue(game: String): Float = when (getColorFilterMode(game)) {
        1 -> 1.5f
        2 -> 1.25f
        else -> 1.0f
    }

    // 6. Driver Mode: 0 = Game Driver, 1 = System Driver (default 1 = System Driver for best hit-reg)
    fun getDriverMode(game: String): Int =
        getSp()?.getInt("driver_mode_$game", 1) ?: 1

    fun setDriverMode(game: String, mode: Int) {
        getSp()?.edit()?.putInt("driver_mode_$game", mode)?.apply()
    }

    // 7. Dolby Footstep Preset: 1 = Full Tep-Tep, 2 = Voice/Scout (default 2), 3 = 3D Spatial, 4 = IEM, 0 = Off
    fun getDolbyPreset(game: String): Int =
        getSp()?.getInt("dolby_preset_$game", 2) ?: 2

    fun setDolbyPreset(game: String, preset: Int) {
        getSp()?.edit()?.putInt("dolby_preset_$game", preset)?.apply()
    }

    // 8. Pure Clean Volume Percent (default 70%)
    fun getCleanVolumePercent(game: String): Int =
        getSp()?.getInt("clean_boost_pct_$game", 70) ?: 70

    fun setCleanVolumePercent(game: String, pct: Int) {
        getSp()?.edit()?.putInt("clean_boost_pct_$game", pct.coerceIn(0, 100))?.apply()
    }

    // 9. Hardware & System Toggles
    fun getSplitterGuardOn(game: String): Boolean =
        getSp()?.getBoolean("splitter_guard_$game", true) ?: true

    fun setSplitterGuardOn(game: String, enabled: Boolean) {
        getSp()?.edit()?.putBoolean("splitter_guard_$game", enabled)?.apply()
    }

    fun getFocusDndOn(game: String): Boolean =
        getSp()?.getBoolean("focus_dnd_$game", true) ?: true

    fun setFocusDndOn(game: String, enabled: Boolean) {
        getSp()?.edit()?.putBoolean("focus_dnd_$game", enabled)?.apply()
    }

    fun getThermalZeroOn(game: String): Boolean =
        getSp()?.getBoolean("thermal_zero_$game", true) ?: true

    fun setThermalZeroOn(game: String, enabled: Boolean) {
        getSp()?.edit()?.putBoolean("thermal_zero_$game", enabled)?.apply()
    }

    fun getDnsMode(game: String): Int =
        getSp()?.getInt("dns_mode_$game", 1) ?: 1

    fun setDnsMode(game: String, mode: Int) {
        getSp()?.edit()?.putInt("dns_mode_$game", mode)?.apply()
    }

    // 10. Backward-Compatibility Bridges
    fun getDownscale(game: String): String = getEffectiveGfxConfig(game).downscale

    fun setDownscale(game: String, downscale: String) {
        getSp()?.edit()?.putString("ds_$game", downscale)?.apply()
    }

    fun getFps(game: String): Int = getTargetFps(game)

    fun setFps(game: String, fps: Int) = setTargetFps(game, fps)

    fun getCleanBoost(game: String): Boolean = true

    fun setCleanBoost(game: String, enabled: Boolean) {
        getSp()?.edit()?.putBoolean("clean_boost_$game", enabled)?.apply()
    }

    fun getCleanBoostPct(game: String): Int = getCleanVolumePercent(game)

    fun setCleanBoostPct(game: String, pct: Int) = setCleanVolumePercent(game, pct)
}
