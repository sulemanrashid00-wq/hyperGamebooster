package com.hypergameboost

import android.content.Context
import android.content.SharedPreferences

/**
 * ResolutionStep - Realme Resolution & Scrims Downscale Step mapping
 */
data class ResolutionStep(
    val step: Int,
    val percent: Int,
    val label: String,
    val wmSize: String,
    val density: String,
    val downscaleFactor: String
)

/**
 * AppPrefs - Realme GT Game Space & TSOML GFX Persistent State Engine (v18.0)
 * Pre-initialized to user's exact preferred competitive defaults:
 * - High Performance GT Mode (Auto-active)
 * - 810p Scrims (810x1800 @ 310 DPI, 0.60 downscale, 120 FPS, LOD +3)
 * - 100% Touch & Aim, 100% Swipe 480Hz, 100% Tap Stability
 * - TSOML GFX: 800x600 Super Smooth 120FPS, LOD +3, Bilinear ON, Simple Shader ON
 * - Realme Dolby Voice / Scout (Preset 2), 70% Clean Volume Boost, Splitter Guard ON
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
        appContext?.getSharedPreferences("realme_game_space_v18", Context.MODE_PRIVATE)

    // Resolution Steps (5 Steps)
    val RESOLUTION_STEPS = listOf(
        ResolutionStep(1, 55, "800x600 Ultra Potato", "720x1600", "280", "0.55"),
        ResolutionStep(2, 65, "810p Scrims + 800x600 Super Smooth", "810x1800", "310", "0.60"),
        ResolutionStep(3, 75, "900p Esports Pro", "900x2000", "350", "0.75"),
        ResolutionStep(4, 85, "iPad 4:3 Wide Hitbox", "1200x1600", "300", "0.85"),
        ResolutionStep(5, 100, "Native 1.5K (1220x2712 Stock)", "reset", "reset", "disable")
    )

    fun getResolutionStep(step: Int): ResolutionStep =
        RESOLUTION_STEPS.find { it.step == step } ?: RESOLUTION_STEPS[1]

    // 1. Target Package
    fun getSelectedPkg(): String =
        getSp()?.getString("selected_pkg", "com.tencent.ig") ?: "com.tencent.ig"

    fun setSelectedPkg(pkg: String) {
        getSp()?.edit()?.putString("selected_pkg", pkg)?.apply()
    }

    // 2. Realme 3-Way Performance Mode (0 = Low Power, 1 = Balanced, 2 = High Performance GT Mode)
    fun getPerformanceMode(game: String): Int =
        getSp()?.getInt("perf_mode_$game", 2) ?: 2

    fun setPerformanceMode(game: String, mode: Int) {
        getSp()?.edit()?.putInt("perf_mode_$game", mode)?.apply()
    }

    // 3. Realme Interactive Sliding Bars (0..100)
    fun getTouchAimSensitivity(game: String): Int =
        getSp()?.getInt("touch_aim_$game", 100) ?: 100

    fun setTouchAimSensitivity(game: String, value: Int) {
        getSp()?.edit()?.putInt("touch_aim_$game", value.coerceIn(0, 100))?.apply()
    }

    fun getSwipeJiggleResponse(game: String): Int =
        getSp()?.getInt("swipe_jiggle_$game", 100) ?: 100

    fun setSwipeJiggleResponse(game: String, value: Int) {
        getSp()?.edit()?.putInt("swipe_jiggle_$game", value.coerceIn(0, 100))?.apply()
    }

    fun getTapStabilityRecoil(game: String): Int =
        getSp()?.getInt("tap_stability_$game", 100) ?: 100

    fun setTapStabilityRecoil(game: String, value: Int) {
        getSp()?.edit()?.putInt("tap_stability_$game", value.coerceIn(0, 100))?.apply()
    }

    fun getResolutionStepIndex(game: String): Int =
        getSp()?.getInt("res_step_$game", 2) ?: 2

    fun setResolutionStepIndex(game: String, step: Int) {
        getSp()?.edit()?.putInt("res_step_$game", step.coerceIn(1, 5))?.apply()
    }

    // 4. TSOML GFX Tool Settings
    fun getTsomlResolution(game: String): String =
        getSp()?.getString("tsoml_res_$game", "800x600") ?: "800x600"

    fun setTsomlResolution(game: String, res: String) {
        getSp()?.edit()?.putString("tsoml_res_$game", res)?.apply()
    }

    fun getTsomlGraphics(game: String): String =
        getSp()?.getString("tsoml_gfx_$game", "Super Smooth") ?: "Super Smooth"

    fun setTsomlGraphics(game: String, gfx: String) {
        getSp()?.edit()?.putString("tsoml_gfx_$game", gfx)?.apply()
    }

    fun getTsomlFps(game: String): Int =
        getSp()?.getInt("tsoml_fps_$game", 120) ?: 120

    fun setTsomlFps(game: String, fps: Int) {
        getSp()?.edit()?.putInt("tsoml_fps_$game", fps)?.apply()
    }

    fun getTsomlPotatoLod(game: String): String =
        getSp()?.getString("tsoml_potato_$game", "Ultra (LOD +3)") ?: "Ultra (LOD +3)"

    fun setTsomlPotatoLod(game: String, potato: String) {
        getSp()?.edit()?.putString("tsoml_potato_$game", potato)?.apply()
    }

    fun getTsomlBilinear(game: String): Boolean =
        getSp()?.getBoolean("tsoml_bilinear_$game", true) ?: true

    fun setTsomlBilinear(game: String, enable: Boolean) {
        getSp()?.edit()?.putBoolean("tsoml_bilinear_$game", enable)?.apply()
    }

    fun getTsomlSimpleShader(game: String): Boolean =
        getSp()?.getBoolean("tsoml_simple_shader_$game", true) ?: true

    fun setTsomlSimpleShader(game: String, enable: Boolean) {
        getSp()?.edit()?.putBoolean("tsoml_simple_shader_$game", enable)?.apply()
    }

    fun getTsomlDriver(game: String): String =
        getSp()?.getString("tsoml_driver_$game", "SkiaGL + System Driver") ?: "SkiaGL + System Driver"

    fun setTsomlDriver(game: String, driver: String) {
        getSp()?.edit()?.putString("tsoml_driver_$game", driver)?.apply()
    }

    fun getTsomlSaveControls(game: String): Boolean =
        getSp()?.getBoolean("tsoml_save_controls_$game", true) ?: true

    fun setTsomlSaveControls(game: String, enable: Boolean) {
        getSp()?.edit()?.putBoolean("tsoml_save_controls_$game", enable)?.apply()
    }

    // 5. Realme Dolby Footstep Studio & Clean Volume Boost
    fun getDolbyPreset(game: String): Int =
        getSp()?.getInt("dolby_preset_$game", 2) ?: 2

    fun setDolbyPreset(game: String, preset: Int) {
        getSp()?.edit()?.putInt("dolby_preset_$game", preset)?.apply()
    }

    fun getCleanVolumePercent(game: String): Int =
        getSp()?.getInt("clean_boost_pct_$game", 70) ?: 70

    fun setCleanVolumePercent(game: String, pct: Int) {
        getSp()?.edit()?.putInt("clean_boost_pct_$game", pct.coerceIn(0, 100))?.apply()
    }

    fun getSplitterGuardOn(game: String): Boolean =
        getSp()?.getBoolean("splitter_guard_$game", true) ?: true

    fun setSplitterGuardOn(game: String, enabled: Boolean) {
        getSp()?.edit()?.putBoolean("splitter_guard_$game", enabled)?.apply()
    }
}
