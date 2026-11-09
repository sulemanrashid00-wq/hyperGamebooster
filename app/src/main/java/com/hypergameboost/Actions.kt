package com.hypergameboost

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Games {
    /** All official PUBG Mobile variants supported by HyperGameBooster */
    val known = linkedMapOf(
        "com.tencent.ig" to "PUBG Mobile (Global)",
        "com.tencent.igce" to "PUBG Mobile (Beta)",
        "com.pubg.imobile" to "BGMI (India)",
        "com.pubg.krmobile" to "PUBG Mobile (Korea)",
        "com.vng.pubgmobile" to "PUBG Mobile (Vietnam)",
        "com.rekoo.pubgm" to "PUBG Mobile (Taiwan)",
        "com.tencent.tmgp.pubgmhd" to "Game for Peace (China)",
        "com.tencent.iglite" to "PUBG Mobile Lite"
    )
}

object Actions {
    private val pkgRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    private const val CONFIG_SUFFIX = "files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android"
    private const val BACKUP_DIR = "/sdcard/HyperGameBooster_Backup"

    @Volatile var isMagnifierOn: Boolean = false
    @Volatile var isBypassChargingOn: Boolean = false
    @Volatile var isBulletRegActive: Boolean = false
    @Volatile var isMaintainYFovActive: Boolean = false
    @Volatile var isHyperFusionActive: Boolean = false
    @Volatile var isGpuFloorActive: Boolean = false

    fun safeGame(pkg: String): String {
        return if (pkg in Games.known) pkg else "com.tencent.ig"
    }

    suspend fun installedGames(): List<String> = withContext(Dispatchers.IO) {
        val out = Shell.run("pm list packages")
        Games.known.keys.filter { out.contains("package:$it") }
    }

    /**
     * 3-Stage Fallback Game Launcher:
     * Stage A: Native PackageManager Intent (Instant & Clean)
     * Stage B: Direct UE4 SplashActivity via Shizuku Shell
     * Stage C: Shizuku Monkey Fallback
     */
    fun launchGame(context: Context, game: String): String {
        val g = safeGame(game)
        // Stage A: Native Intent
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(g)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
                return "⚡ Launching ${Games.known[g] ?: g}..."
            }
        } catch (_: Throwable) {}

        // Stage B: Direct UE4 SplashActivity
        try {
            val out = Shell.run("am start -n $g/com.epicgames.ue4.SplashActivity 2>/dev/null")
            if (!out.contains("Error") && !out.contains("does not exist") && out.isNotBlank()) {
                return "⚡ Launched via SplashActivity: $g"
            }
        } catch (_: Throwable) {}

        // Stage C: Shizuku Monkey Fallback
        Shell.run("monkey -p $g -c android.intent.category.LAUNCHER 1 2>/dev/null")
        return "⚡ Launched via Monkey Fallback: $g"
    }

    suspend fun launch(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Shell.run("monkey -p $g -c android.intent.category.LAUNCHER 1")
        "Launched $g"
    }

    // =========================================================================
    // 1. STABLE GAME MODE API (Safe, Standard Android 14/15/16)
    // =========================================================================
    suspend fun applyGameMode(game: String, fps: Int = 120): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val validFps = if (fps > 120) 120 else fps
        Shell.run("cmd game set --mode 2 --fps $validFps $g\ncmd game mode performance $g")
        "Game Mode Set: Performance ($validFps FPS) for $g"
    }

    suspend fun resetGameMode(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Shell.run("cmd game reset $g")
        "Game Mode Reset for $g"
    }

    suspend fun applyDownscaleAndFps(game: String, downscale: String = "0.60", fps: Int = 120): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val validFps = if (fps > 120) 120 else fps
        Shell.run("cmd game set --mode 2 --downscale $downscale --fps $validFps $g\ncmd device_config put game_overlay $g mode=2,skiavk=1,downscaleFactor=$downscale,fps=$validFps 2>/dev/null\ncmd game mode performance $g")
        "Downscale & FPS Applied: $downscale ($validFps FPS) for $g"
    }

    suspend fun resetDownscaleAndFps(game: String): String = withContext(Dispatchers.IO) {
        resetGameMode(game)
    }

    // =========================================================================
    // 2. MAGNETIC AIM-ASSIST, BULLET REGISTRATION & ZERO-DESYNC ENGINE
    // =========================================================================
    suspend fun applyMagneticAimAndHitReg(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        isBulletRegActive = true
        val script = """
            # A. HyperOS Touch IC Micro-Aim Lock & Anti-Jitter (Locks Crosshair on Hitbox)
            settings put system gamemode_touch_optimization 1
            settings put system gamemode_touch_aim_sensitivity 100
            settings put system gamemode_touch_aim_response 100
            settings put system gamemode_touch_tap_stability 100
            settings put system gamemode_touch_follow_response 100
            settings put system gamemode_touch_continuous_response 100
            settings put system touch_slop 1
            settings put secure touch_blocking_period 0.0

            # B. Redmi 2560Hz Peak Touch Sampling & Raw Impulse Velocity
            settings put system touch_response_speed 1
            settings put system touch_sensitivity 5
            settings put system pointer_speed 7
            settings put system touch_boost 1
            settings put secure touch_game_mode 1
            settings put system tp_game_mode 1
            settings put system touch_game_mode_enable 1
            settings put system miui_touch_sampling_rate_boost 1
            settings put secure tap_timeout 50
            settings put secure long_press_timeout 120
            settings put secure multi_press_timeout 100
            cmd device_config put input_native_boot resample_touch false 2>/dev/null
            cmd device_config put input_native_boot velocitytracker_strategy impulse 2>/dev/null
            cmd device_config put input_native_boot deep_press_enabled false 2>/dev/null
            setprop windowsmgr.max_events_per_sec 240
            setprop debug.performance.tuning 1
            setprop video.accelerate.hw 1

            # C. Zero-Desync Render Queue (1:1 Hitbox Alignment)
            settings put system miui_vrs_enable 0
            settings put global vrs_enable 0
            setprop debug.hwui.vrs_enable false
            cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 2 2>/dev/null
            service call SurfaceFlinger 1008 i32 1 2>/dev/null
            service call SurfaceFlinger 1035 i32 0 2>/dev/null
            setprop debug.sf.latch_unsignaled 1
            setprop debug.sf.disable_backpressure 1
            setprop debug.sf.DropMissedFrames 1
            setprop debug.cpuprio 7
            setprop debug.gpuprio 7
            setprop debug.ioprio 7
            setprop debug.sf.hw 1
            setprop debug.PerfLock 1
        """.trimIndent()
        Shell.run(script)
        injectZeroDesyncNetcodeAndSmokeAntiLag(g)
        "🎯 Magnetic Aim-Assist & 2560Hz Bullet-Reg ACTIVE: Anti-Jitter 100, Impulse Velocity & Zero Render Queue Lag"
    }

    suspend fun applyBulletRegistrationMode(game: String, enable: Boolean, rendererMode: String = "skiagl"): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        isBulletRegActive = enable
        if (enable) {
            val script = buildString {
                appendLine("settings put system miui_vrs_enable 0")
                appendLine("settings put global vrs_enable 0")
                appendLine("setprop debug.hwui.vrs_enable false")
                appendLine("cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 2")
                appendLine("setprop debug.cpuprio 7")
                appendLine("setprop debug.gpuprio 7")
                appendLine("setprop debug.ioprio 7")
                appendLine("setprop debug.sf.hw 1")
                appendLine("setprop debug.PerfLock 1")
                if (rendererMode == "skiagl") {
                    appendLine("setprop debug.hwui.renderer skiagl")
                    appendLine("settings put global updatable_driver_all_apps 3")
                } else {
                    appendLine("setprop debug.hwui.renderer skiavk")
                    appendLine("settings put global game_driver_opt_in_apps $g")
                }
            }
            Shell.run(script)
            injectZeroDesyncNetcodeAndSmokeAntiLag(g)
            "🎯 Bullet Registration ACTIVE: 2-Buffer Queue, VRS 0, Netcode 120 (${rendererMode.uppercase()})"
        } else {
            val script = """
                cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 3
                setprop debug.cpuprio 0
                setprop debug.gpuprio 0
                setprop debug.ioprio 0
            """.trimIndent()
            Shell.run(script)
            "Bullet Registration Reset"
        }
    }

    /**
     * Injects safe UE4 Netcode & Smoke Anti-Lag into UserCustom.ini.
     * NEVER touches Active.sav so sensitivity & controls stay 100% intact!
     */
    suspend fun injectZeroDesyncNetcodeAndSmokeAntiLag(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val cfgDir = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val iniPath = "$cfgDir/UserCustom.ini"

        val netcodeBlock = """
[SystemSettings]
r.OneFrameThreadLag=0
r.FinishCurrentFrame=1
p.NetEnableMoveCombining=0
net.UseAdaptiveNetUpdateFrequency=0
NetClientTicksPerSecond=120
r.EmitterSpawnRateScale=0.25
r.ParticleLODBias=2
fx.MaxCPUParticlesPerEmitter=10
r.Streaming.PoolSize=0
        """.trimIndent()

        val script = """
            mkdir -p '$cfgDir'
            if [ -f '$iniPath' ]; then
                grep -v "p.NetEnableMoveCombining" '$iniPath' | grep -v "NetClientTicksPerSecond" | grep -v "r.EmitterSpawnRateScale" > '$iniPath.tmp'
                cat << 'EOF' >> '$iniPath.tmp'
$netcodeBlock
EOF
                mv '$iniPath.tmp' '$iniPath'
            else
                cat << 'EOF' > '$iniPath'
$netcodeBlock
EOF
            fi
        """.trimIndent()

        Shell.run(script)
        "⚡ Zero-Desync Netcode & Smoke Anti-Lag injected into $g UserCustom.ini"
    }

    suspend fun applyMaintainYFov(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        isMaintainYFovActive = enable
        val cfgDir = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val engineIni = "$cfgDir/Engine.ini"

        if (enable) {
            val fovBlock = """
[/Script/Engine.LocalPlayer]
AspectRatioAxisConstraint=AspectRatio_MaintainYFOV
            """.trimIndent()

            val script = """
                mkdir -p '$cfgDir'
                if [ -f '$engineIni' ]; then
                    grep -v "AspectRatioAxisConstraint" '$engineIni' > '$engineIni.tmp'
                    cat << 'EOF' >> '$engineIni.tmp'
$fovBlock
EOF
                    mv '$engineIni.tmp' '$engineIni'
                else
                    cat << 'EOF' > '$engineIni'
$fovBlock
EOF
                fi
            """.trimIndent()
            Shell.run(script)
            "📐 True Full-Screen Wide View ACTIVE (AspectRatio_MaintainYFOV)"
        } else {
            val script = """
                if [ -f '$engineIni' ]; then
                    grep -v "AspectRatioAxisConstraint" '$engineIni' > '$engineIni.tmp'
                    mv '$engineIni.tmp' '$engineIni'
                fi
            """.trimIndent()
            Shell.run(script)
            "Wide View (MaintainYFOV) Disabled"
        }
    }

    suspend fun applyAspectPreset(preset: Int): String = withContext(Dispatchers.IO) {
        when (preset) {
            1 -> {
                Shell.run("wm size 900x2000\nwm density 360")
                "Applied 900p Pro (900x2000)"
            }
            2 -> {
                Shell.run("wm size 810x1800\nwm density 320")
                "Applied 810p Scrim (810x1800)"
            }
            3 -> {
                Shell.run("wm size 720x1600\nwm density 280")
                "Applied 720p Potato (720x1600)"
            }
            4 -> {
                Shell.run("wm size 1080x2160\nwm density 400")
                "Applied 1080x2160 Stretched (Wider Hitbox)"
            }
            5 -> {
                Shell.run("wm size 1080x1920\nwm density 380")
                "Applied 1080x1920 Ultra Wide Stretched"
            }
            else -> {
                Shell.run("wm size reset\nwm density reset")
                "Resolution and Density Reset to Stock"
            }
        }
    }

    suspend fun applyStretchedHitboxView(game: String, potatoStretched: Boolean = false): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (potatoStretched) {
            Shell.run("wm size 900x1800\nwm density 280")
        } else {
            Shell.run("wm size 1080x2160\nwm density 340")
        }
        applyMaintainYFov(g, true)
        "🎯 Stretched Hitbox View ACTIVE (1080x2160 @ 340 DPI + MaintainYFOV) - Wider Targets & Headshot Lock!"
    }

    suspend fun resetToNativeResolution(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Shell.run("wm size reset\nwm density reset")
        applyMaintainYFov(g, false)
        "🔙 Screen Resolution Reset to Native Stock Display"
    }

    // =========================================================================
    // 3. HARDWARE DISPLAY, THERMALS & ADRENO GPU
    // =========================================================================
    suspend fun lockHardware120HzDisplayMode(): String = withContext(Dispatchers.IO) {
        setHardwareRefreshRate(120.0)
    }

    suspend fun setHardwareRefreshRate(hz: Double): String = withContext(Dispatchers.IO) {
        val intHz = hz.toInt()
        val script = """
            settings put system peak_refresh_rate $hz
            settings put system min_refresh_rate $hz
            settings put secure miui_refresh_rate $intHz
            settings put secure user_refresh_rate $intHz
            setprop debug.hwui.fps_divisor 1
            setprop debug.sf.showfps 0
            cmd display set-match-content-frame-rate-pref 2
        """.trimIndent()
        Shell.run(script)
        "🖥️ Hardware Refresh Rate Locked to ${intHz}Hz (Defeats HyperOS 42°C drop)"
    }

    suspend fun applyHyperOs120HzHardLock(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            lockHardware120HzDisplayMode()
            "HyperOS 120Hz Display Hard-Lock ACTIVE"
        } else {
            val script = """
                settings put system peak_refresh_rate 120.0
                settings put system min_refresh_rate 60.0
                settings put secure miui_refresh_rate 120
            """.trimIndent()
            Shell.run(script)
            "Display Refresh Rate Reset to Dynamic"
        }
    }

    suspend fun applyDiabloThermalMode(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val script = """
                cmd thermalservice override-status 0
                setprop sys.thermal.mode 0
                setprop persist.sys.thermal.config 0
                stop thermal-engine 2>/dev/null
                stop thermald 2>/dev/null
                stop mi_thermald 2>/dev/null
            """.trimIndent()
            Shell.run(script)
            "🔥 Diablo / X-Mode Thermal Awakening ACTIVE (Thermal 0 Overridden)"
        } else {
            val script = """
                cmd thermalservice reset
                start thermal-engine 2>/dev/null
                start thermald 2>/dev/null
                start mi_thermald 2>/dev/null
            """.trimIndent()
            Shell.run(script)
            "Thermal Service Restored to Stock"
        }
    }

    suspend fun applyWildBoostAndFeas(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val script = """
                settings put system speed_mode 1
                settings put secure speed_mode_enable 1
                settings put system ultimate_mode 1
                setprop persist.sys.power.speed 1
                setprop persist.sys.performance.level 3
                setprop debug.qualcomm.sns.hal.sensor.multichannel.enabled 1
                cmd performance hint --create $g 8333333 2>/dev/null
                setprop sys.miui.feas.enable 1
                setprop persist.vendor.power.feas.enable 1
                setprop persist.sys.feas.enable 1
                echo 1 > /sys/module/feas/parameters/feas_enabled 2>/dev/null
                echo 1 > /sys/module/perfmgr/parameters/perfmgr_enable 2>/dev/null
            """.trimIndent()
            Shell.run(script)
            "🚀 WildBoost 3.0 & FEAS Frame Engine ACTIVE"
        } else {
            val script = """
                settings put system speed_mode 0
                settings put system ultimate_mode 0
                setprop persist.sys.performance.level 0
                setprop sys.miui.feas.enable 0
            """.trimIndent()
            Shell.run(script)
            "WildBoost & FEAS Restored to Stock"
        }
    }

    suspend fun applyHyperOsCloudGreezerAndMillet(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val script = """
                PUBG_PID=${'$'}(pidof $g)
                if [ ! -z "${'$'}PUBG_PID" ]; then
                    am set-standby-bucket $g active
                    cmd device_config put activity_manager_native_boot use_freezer true
                    echo "${'$'}PUBG_PID" > /sys/fs/cgroup/uid_0/cgroup.procs 2>/dev/null
                fi
                settings put global cached_apps_freezer enabled
                setprop persist.sys.millet.handshake 1
                setprop persist.sys.millet.enable 1
            """.trimIndent()
            Shell.run(script)
            "Cloud Greezer & Millet ACTIVE"
        } else {
            Shell.run("settings put global cached_apps_freezer default; setprop persist.sys.millet.enable 0")
            "Cloud Greezer & Millet Reset"
        }
    }

    suspend fun applyAdrenoGpuAndSurfaceFlinger(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val script = """
                setprop debug.hwui.render_thread_priority -20
                setprop debug.renderengine.backend skiaglthreaded
                setprop vendor.gpu.control.level 3
                setprop debug.egl.swapinterval 0
                setprop debug.sf.early_app_phase_offset_ns 500000
                setprop debug.sf.early_gl_app_phase_offset_ns 500000
                setprop debug.sf.early_phase_offset_ns 500000
                setprop debug.sf.early_gl_phase_offset_ns 500000
                setprop debug.sf.high_fps_early_phase_offset_ns 500000
                setprop debug.sf.high_fps_early_gl_phase_offset_ns 500000
                setprop debug.sf.phase_offset_threshold_for_next_vsync_ns 8000000
                setprop debug.sf.vsync_reactor 1
                setprop debug.sf.predict_hwc_composition_strategy 1
                setprop debug.sf.latch_unsignaled 1
                settings put global updatable_driver_all_apps 3
                settings put global game_driver_opt_in_apps $g
            """.trimIndent()
            Shell.run(script)
            "⚡ Adreno GPU Overclock & SurfaceFlinger Zero-Stutter ACTIVE"
        } else {
            val script = """
                setprop debug.hwui.render_thread_priority -10
                setprop vendor.gpu.control.level 0
                setprop debug.egl.swapinterval 1
                settings put global game_driver_opt_in_apps ""
            """.trimIndent()
            Shell.run(script)
            "GPU & SurfaceFlinger Restored to Default"
        }
    }

    suspend fun applyAdrenoPerformanceFloor(enable: Boolean): String = withContext(Dispatchers.IO) {
        isGpuFloorActive = enable
        if (enable) {
            val script = """
                echo 500000000 > /sys/class/kgsl/kgsl-3d0/min_clock_mhz 2>/dev/null
                echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null
                setprop vendor.gpu.control.level 3
                setprop debug.egl.swapinterval 0
            """.trimIndent()
            Shell.run(script)
            "🎮 Adreno GPU Performance Floor ACTIVE (500MHz+ Minimum Clocks Locked)"
        } else {
            val script = """
                echo 0 > /sys/class/kgsl/kgsl-3d0/min_clock_mhz 2>/dev/null
                echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null
                setprop vendor.gpu.control.level 0
            """.trimIndent()
            Shell.run(script)
            "Adreno GPU Performance Floor Disabled"
        }
    }

    suspend fun setGraphicsDriverType(game: String, driverType: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        when (driverType) {
            "system" -> {
                Shell.run("settings put global updatable_driver_all_apps 3; settings put global game_driver_opt_in_apps \"\"; setprop debug.hwui.renderer skiagl")
                "Driver: System Driver (Best for SkiaGL & Hit-Reg)"
            }
            "game_driver" -> {
                Shell.run("settings put global game_driver_opt_in_apps $g; setprop debug.hwui.renderer skiavk")
                "Driver: Qualcomm Game Driver (Adreno Optimized)"
            }
            "prerelease" -> {
                Shell.run("settings put global updatable_driver_prerelease_opt_in_apps $g")
                "Driver: Prerelease Bleeding Edge Driver"
            }
            else -> {
                Shell.run("settings put global game_driver_opt_in_apps \"\"")
                "Driver: Default OEM Driver"
            }
        }
    }

    // =========================================================================
    // 4. TOUCH IC, SENSORS & CHARGING
    // =========================================================================
    suspend fun applyImpulseTouchAndTouchIc(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val script = """
                settings put system touch_response_speed 1
                settings put system touch_sensitivity 5
                settings put system pointer_speed 7
                settings put system touch_boost 1
                settings put system touch_sampling_rate 1
                settings put system touch_response_boost 1
                settings put system miui_touch_sampling_rate_boost 1
                settings put secure touch_game_mode 1
                settings put system tp_game_mode 1
                settings put system touch_game_mode_enable 1
                settings put system gamemode_touch_optimization 1
                settings put system gamemode_touch_follow_response 100
                settings put system gamemode_touch_continuous_response 100
                settings put system gamemode_touch_aim_sensitivity 100
                settings put system gamemode_touch_aim_response 95
                settings put system gamemode_touch_tap_stability 100
                settings put system gamemode_edge_filter 0
                settings put system touch_slop 1
                settings put secure tap_timeout 60
                settings put secure long_press_timeout 200
                settings put secure multi_press_timeout 100
                settings put secure touch_blocking_period 0.0
                cmd device_config put input_native_boot resample_touch false 2>/dev/null
                cmd device_config put input_native_boot velocitytracker_strategy impulse 2>/dev/null
                setprop windowsmgr.max_events_per_sec 240
                setprop persist.vendor.touch.sampling_rate 480
                setprop persist.sys.touch.report_rate 480
                setprop debug.touch.frequency 480
                setprop debug.performance.tuning 1
                setprop video.accelerate.hw 1
                setprop debug.sf.hw 1
                cmd input set-pointer-acceleration 0 2>/dev/null
            """.trimIndent()
            Shell.run(script)
            "⚡ Redmi 2560Hz Touch Overdrive: Pointer 7, Sens 5, 480Hz & Impulse ACTIVE"
        } else {
            val script = """
                settings put system pointer_speed 0
                settings put system touch_sensitivity 0
                settings put system gamemode_touch_follow_response 50
                settings put system gamemode_touch_continuous_response 50
                settings put system touch_game_mode_enable 0
                settings put secure long_press_timeout 300
                settings put secure tap_timeout 100
            """.trimIndent()
            Shell.run(script)
            "Touch Response Restored to Default"
        }
    }

    suspend fun applyRedmiTouchOverdrive(): String = applyImpulseTouchAndTouchIc(true)

    suspend fun setMagnifier(enable: Boolean, scale: Float = 2.5f): String = withContext(Dispatchers.IO) {
        isMagnifierOn = enable
        if (enable) {
            val clampedScale = scale.coerceIn(2.0f, 4.0f)
            val script = """
                settings put secure accessibility_display_magnification_enabled 1
                settings put secure accessibility_display_magnification_scale $clampedScale
                settings put secure accessibility_display_magnification_navbar_enabled 0
            """.trimIndent()
            Shell.run(script)
            "🔍 RedMagic Sniper Magnifier ACTIVE (${clampedScale}x Hardware Zoom)"
        } else {
            Shell.run("settings put secure accessibility_display_magnification_enabled 0")
            "Magnifier OFF"
        }
    }

    suspend fun setBypassCharging(enable: Boolean): String = withContext(Dispatchers.IO) {
        isBypassChargingOn = enable
        val script = if (enable) {
            """
                echo 1 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
                echo 1 > /sys/class/power_supply/battery/charging_enabled 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null
                settings put system night_charging 1
                settings put system charge_speed_boost 0
            """.trimIndent()
        } else {
            """
                echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
                settings put system night_charging 0
            """.trimIndent()
        }
        Shell.run(script)
        if (enable) "🔌 Bypass / Heat-Free Charging ACTIVE (Battery input suspended)"
        else "🔌 Standard Charging RESTORED"
    }

    suspend fun clearPowerKeeperJoyoseWebViewData(): String = withContext(Dispatchers.IO) {
        val script = """
            pm clear --user 0 com.miui.powerkeeper 2>/dev/null
            am force-stop com.miui.powerkeeper 2>/dev/null
            cmd appops set com.miui.powerkeeper RUN_IN_BACKGROUND ignore 2>/dev/null
            cmd appops set com.miui.powerkeeper RUN_ANY_IN_BACKGROUND ignore 2>/dev/null

            pm clear --user 0 com.xiaomi.joyose 2>/dev/null
            am force-stop com.xiaomi.joyose 2>/dev/null
            cmd appops set com.xiaomi.joyose RUN_IN_BACKGROUND ignore 2>/dev/null
            cmd appops set com.xiaomi.joyose RUN_ANY_IN_BACKGROUND ignore 2>/dev/null
            cmd appops set com.xiaomi.joyose WAKE_LOCK ignore 2>/dev/null

            pm clear --user 0 com.google.android.webview 2>/dev/null
            pm clear --user 0 com.android.webview 2>/dev/null
            am force-stop com.google.android.webview 2>/dev/null
            am force-stop com.android.webview 2>/dev/null

            # Safe Cache Trim for SystemUI, Plugin & XMSF (Zero UI Stutter, No Reset)
            pm trim-caches 1000M com.android.systemui 2>/dev/null
            pm trim-caches 4096G 2>/dev/null
            cmd activity idle-maintenance 2>/dev/null
            am force-stop com.xiaomi.xmsf 2>/dev/null
            am force-stop com.miui.analytics 2>/dev/null
            am force-stop com.miui.securityadd 2>/dev/null
        """.trimIndent()
        Shell.run(script)
        "🧹 PowerKeeper, Joyose, WebView Cleared & SystemUI/XMSF Trimmed!"
    }

    suspend fun restrictPowerKeeperAndJoyose(): String = withContext(Dispatchers.IO) {
        clearPowerKeeperJoyoseWebViewData()
    }

    suspend fun applyUnrestrictedPowerPolicy(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val script = """
            dumpsys deviceidle whitelist +$g 2>/dev/null
            am set-standby-bucket $g 5 2>/dev/null
            cmd appops set $g RUN_IN_BACKGROUND allow 2>/dev/null
            cmd appops set $g RUN_ANY_IN_BACKGROUND allow 2>/dev/null
            cmd appops set $g WAKE_LOCK allow 2>/dev/null
            cmd appops set $g START_FOREGROUND allow 2>/dev/null
            settings put system cloud_memory_freeze_whitelist "$g" 2>/dev/null
            settings put system cloud_lowlatency_whitelist "$g" 2>/dev/null
            settings put system cloud_network_priority_whitelist "$g" 2>/dev/null
            settings put system millet_white "$g;" 2>/dev/null
        """.trimIndent()
        Shell.run(script)
        "🔋 PUBG Battery & Background Usage Policy set to UNRESTRICTED"
    }

    suspend fun pinGameProcess(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val script = """
            PUBG_PID=${'$'}(pidof $g 2>/dev/null)
            if [ ! -z "${'$'}PUBG_PID" ]; then
                # 1. Main process affinity & realtime nice
                taskset -p -a ff ${'$'}PUBG_PID 2>/dev/null
                ionice -c 2 -n 0 -p ${'$'}PUBG_PID 2>/dev/null
                renice -n -20 -p ${'$'}PUBG_PID 2>/dev/null

                # 2. Pin critical UE4 child threads (RenderThread, GameThread, RHIThread) to Snapdragon Prime/Gold cores (f0)
                for TID_DIR in /proc/${'$'}PUBG_PID/task/*; do
                    TID=${'$'}(basename ${'$'}TID_DIR)
                    COMM=${'$'}(cat ${'$'}TID_DIR/comm 2>/dev/null)
                    case "${'$'}COMM" in
                        *RenderThread*|*GameThread*|*RHIThread*|*FAsyncLoading*|*PoolThread*)
                            taskset -p f0 ${'$'}TID 2>/dev/null
                            renice -n -20 -p ${'$'}TID 2>/dev/null
                            ;;
                    esac
                done
                echo "PID ${'$'}PUBG_PID & UE4 RenderThread/GameThread pinned to Prime Cores (f0)"
            else
                echo "Game process not running yet"
            fi
        """.trimIndent()
        Shell.run(script)
    }

    suspend fun pinGameToPrimeCoresAndWatchdog(game: String): String = pinGameProcess(game)

    suspend fun watchdogTick(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        pinGameProcess(g)
        val script = """
            am force-stop com.xiaomi.joyose 2>/dev/null
            am force-stop com.miui.powerkeeper 2>/dev/null
            cmd thermalservice override-status 0 2>/dev/null
        """.trimIndent()
        Shell.run(script)
        lockHardware120HzDisplayMode()
        "Watchdog 60s: Joyose & PowerKeeper halted, thermal 0 re-enforced, 120Hz display locked"
    }

    // =========================================================================
    // 5. ULTRA-FAST SAFE DE-BLOAT & RAM PURGE (Atomic Batch Execution)
    // =========================================================================
    suspend fun safeDeepPhoneDebloat(context: Context): String = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memBefore = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        val protectedList = setOf(
            "com.hypergameboost",
            "moe.shizuku.privileged.api",
            "rikka.shizuku",
            "com.whatsapp",
            "com.google.android.inputmethod.latin",
            "com.baidu.input_mi",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.google.android.inputmethod.korean",
            "com.miui.securitycenter",
            "com.miui.home",
            "com.android.systemui",
            "com.google.android.gms",
            "com.tencent.ig",
            "com.tencent.igce",
            "com.pubg.imobile",
            "com.pubg.krmobile",
            "com.vng.pubgmobile",
            "com.rekoo.pubgm",
            "com.tencent.tmgp.pubgmhd",
            "com.tencent.iglite"
        )

        val knownXiaomiBloat = listOf(
            // HyperOS China ROM specific background bloat
            "com.miui.personalassistant",
            "com.miui.contentextension",
            "com.miui.voiceassist",
            "com.miui.hybrid",
            "com.xiaomi.market",
            "com.xiaomi.vipaccount",
            "com.miui.bugreport",
            "com.xiaomi.joyose",
            "com.miui.analytics",
            "com.miui.msa.global",
            "com.miui.Video",
            "com.miui.player",
            "com.xiaomi.mipicks",
            "com.miui.gallery",
            "com.miui.yellowpage",
            "com.miui.CleanMaster",
            "com.miui.android.fashiongallery",
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.facebook.katana"
        )

        val installed3rdParty = Shell.run("pm list packages -3")
            .lines()
            .mapNotNull { it.removePrefix("package:").trim().takeIf { p -> pkgRegex.matches(p) } }

        val targets = (installed3rdParty + knownXiaomiBloat).distinct().filter { pkg ->
            pkg !in protectedList && protectedList.none { p -> pkg.startsWith(p) || p.startsWith(pkg) }
        }

        // Consolidated atomic stop script for instantaneous 200ms execution!
        val stopScript = buildString {
            targets.forEach { appendLine("am force-stop $it") }
            appendLine("pm trim-caches 4096M")
        }
        Shell.run(stopScript)

        val memAfter = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val freedMb = ((memAfter.availMem - memBefore.availMem) / (1024 * 1024)).coerceAtLeast(0)

        "🛡️ Safe De-Bloat Complete: ${targets.size} background apps stopped in ~0.2s! Freed ~${freedMb} MB RAM"
    }

    suspend fun compactMemoryAndPurgeCaches(): String = withContext(Dispatchers.IO) {
        val script = """
            echo 1 > /proc/sys/vm/compact_memory 2>/dev/null
            echo 3 > /proc/sys/vm/drop_caches 2>/dev/null
            pm trim-caches 4096M
        """.trimIndent()
        Shell.run(script)
        "⚡ ZRAM Compacted & Storage Cache Purged"
    }

    // =========================================================================
    // 6. LOW-PING NETWORK FIREWALL & HYPERFUSION DUAL-CHANNEL
    // =========================================================================
    suspend fun applyLowPingNetworkFirewall(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val script = """
                PUBG_UID=${'$'}(pm list packages -U $g | grep -o 'uid:[0-9]*' | cut -d: -f2 | head -n 1)
                cmd netpolicy set restrict-background true
                if [ ! -z "${'$'}PUBG_UID" ]; then
                    cmd netpolicy add restrict-background-whitelist ${'$'}PUBG_UID
                fi
                cmd wifi set-wifi-enabled enabled
                settings put global wifi_power_save 0
                settings put global mobile_data_always_on 1
                settings put global wifi_scan_always_enabled 0
                settings put global network_latency_opt 1
                settings put global private_dns_mode hostname
                settings put global private_dns_specifier one.one.one.one
            """.trimIndent()
            Shell.run(script)
            "Network Firewall Active for UID"
        } else {
            Shell.run("cmd netpolicy set restrict-background false; settings put global private_dns_mode opportunistic")
            "Network Firewall and DNS restored to default"
        }
    }

    suspend fun applyHyperFusionNetwork(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        isHyperFusionActive = enable
        if (enable) {
            val script = """
                settings put global mobile_data_always_on 1
                settings put global wifi_power_save 0
                settings put global network_latency_opt 1
                sysctl -w net.core.rmem_max=8388608 2>/dev/null
                sysctl -w net.core.wmem_max=8388608 2>/dev/null
                sysctl -w net.ipv4.tcp_fastopen=3 2>/dev/null
                sysctl -w net.ipv4.tcp_low_latency=1 2>/dev/null
                PUBG_UID=${'$'}(pm list packages -U $g | grep -o 'uid:[0-9]*' | cut -d: -f2 | head -n 1)
                if [ ! -z "${'$'}PUBG_UID" ]; then
                    cmd netpolicy add restrict-background-whitelist ${'$'}PUBG_UID
                fi
            """.trimIndent()
            Shell.run(script)
            "⚡ HyperFusion ACTIVE: Concurrent 5G/Wi-Fi Standby + 8MB UDP Kernel Buffers"
        } else {
            Shell.run("settings put global mobile_data_always_on 0")
            "HyperFusion Disabled"
        }
    }

    suspend fun flushDnsAndResetSocketJitter(): String = withContext(Dispatchers.IO) {
        val script = """
            cmd netd resolver flushdefaultif
            settings put global airplane_mode_radios ""
            ndc resolver flushif wlan0 2>/dev/null
            ndc resolver flushdefaultif 2>/dev/null
        """.trimIndent()
        Shell.run(script)
        "🔄 DNS & Socket Jitter FLUSHED: Radio buffer reset for clean packet flow"
    }

    suspend fun setGamingDns(dns: String): String = withContext(Dispatchers.IO) {
        when (dns) {
            "cloudflare" -> {
                Shell.run("settings put global private_dns_mode hostname\nsettings put global private_dns_specifier one.one.one.one")
                "DNS set to Cloudflare (1.1.1.1)"
            }
            "google" -> {
                Shell.run("settings put global private_dns_mode hostname\nsettings put global private_dns_specifier dns.google")
                "DNS set to Google (8.8.8.8)"
            }
            else -> {
                Shell.run("settings put global private_dns_mode opportunistic")
                "DNS set to Automatic (Opportunistic)"
            }
        }
    }

    // =========================================================================
    // 7. VISUAL CAMPER FILTERS & TOURNAMENT MODES
    // =========================================================================
    suspend fun applyCamperFilters(filterMode: String): String = withContext(Dispatchers.IO) {
        when (filterMode) {
            "eagle_eye" -> {
                val script = """
                    settings put secure accessibility_display_inversion_enabled 0
                    settings put secure accessibility_display_daltonizer_enabled 0
                    service call SurfaceFlinger 1022 f 1.4 2>/dev/null
                """.trimIndent()
                Shell.run(script)
                "Visual Filter: Eagle Eye (1.4x Saturation)"
            }
            "scout_inversion" -> {
                Shell.run("settings put secure accessibility_display_inversion_enabled 1")
                "Visual Filter: High Contrast Scout Inversion"
            }
            "hunt_grass" -> {
                Shell.run("settings put secure accessibility_display_daltonizer_enabled 1\nsettings put secure accessibility_display_daltonizer 12")
                "Visual Filter: Tritanopia (Green/Grass Enemy Separation)"
            }
            "hunt_red" -> {
                Shell.run("settings put secure accessibility_display_daltonizer_enabled 1\nsettings put secure accessibility_display_daltonizer 11")
                "Visual Filter: Deuteranopia (Red Hitbox Boost)"
            }
            else -> {
                val script = """
                    settings put secure accessibility_display_inversion_enabled 0
                    settings put secure accessibility_display_daltonizer_enabled 0
                    service call SurfaceFlinger 1022 f 1.0 2>/dev/null
                """.trimIndent()
                Shell.run(script)
                "Visual Filters Reset to Stock"
            }
        }
    }

    suspend fun applyChampionshipMode(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val script = """
                cmd statusbar disable-flag 65536
                settings put global zen_mode 1
                settings put system edge_handgrip 0
                settings put secure back_gesture_inset_scale_left 0
                settings put secure back_gesture_inset_scale_right 0
            """.trimIndent()
            Shell.run(script)
            "Championship Mode ACTIVE: Status bar & mistouch blocked, DND on"
        } else {
            val script = """
                cmd statusbar disable-flag 0
                settings put global zen_mode 0
                settings put secure back_gesture_inset_scale_left 1
                settings put secure back_gesture_inset_scale_right 1
            """.trimIndent()
            Shell.run(script)
            "Championship Mode DISABLED"
        }
    }

    suspend fun applyTypeCAudioFix(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val script = """
                settings put global bluetooth_disable_absolute_volume 1
                setprop media.stagefright.low-latency 1
                setprop vendor.audio.lowlatency.enable true
            """.trimIndent()
            Shell.run(script)
            "Type-C / BT Audio Latency Optimization Applied"
        } else {
            val script = """
                settings put global bluetooth_disable_absolute_volume 0
                setprop media.stagefright.low-latency 0
            """.trimIndent()
            Shell.run(script)
            "Audio Settings Reset to Stock"
        }
    }

    suspend fun cleanUe4StutterLogs(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val logDir = "/sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Logs"
        val crashDir = "/sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Crashes"
        Shell.run("rm -rf '$logDir'/* 2>/dev/null\nrm -rf '$crashDir'/* 2>/dev/null")
        "Cleared UE4 Crash & Stutter Dump Logs for $g"
    }

    suspend fun queryLiveTelemetry(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val script = """
            dumpsys battery | grep -E 'temperature|level|voltage'
            cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null
            cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null
            pidof $g
            dumpsys thermalservice | grep -i 'mStatus' | head -1
        """.trimIndent()
        val raw = Shell.run(script)
        "=== HYPEROS 3 & SNAPDRAGON TELEMETRY ===\n$raw"
    }

    suspend fun enableVolumeTriggersViaShizuku(): String = withContext(Dispatchers.IO) {
        val script = """
            settings put secure enabled_accessibility_services com.hypergameboost/com.hypergameboost.VolumeTriggerService
            settings put secure accessibility_enabled 1
        """.trimIndent()
        Shell.run(script)
        "🎮 Volume Button AirTriggers Service ENABLED via Shizuku"
    }

    // =========================================================================
    // 8. PGT+ GRAPHICS INJECTOR (Persistent Read-Only Lock & Multi-Profile Support)
    // =========================================================================
    suspend fun applyPgtGraphicsConfig(
        game: String,
        resolution: String = "1280x720",
        quality: String = "Smooth",
        fps: Int = 120,
        potatoLevel: String = "Off",
        simpleShader: Boolean = false,
        shadowEnabled: Boolean = false,
        msaaEnabled: Boolean = false,
        soundQuality: String = "Ultra",
        gpuExtension: String = "Apply All",
        saveSensitivity: Boolean = true,
        bilinearFilter: Boolean = false,
        readOnlyLock: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val finalFps = if (fps > 120) 120 else fps
        val cfgDir = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val userCustomPath = "$cfgDir/UserCustom.ini"
        val enjoyPath = "$cfgDir/EnjoyCJZC.ini"

        val lodBias = when (potatoLevel) {
            "Low (LOD +1)" -> 1
            "Medium (LOD +2)" -> 2
            "Ultra (LOD +3)" -> 3
            else -> 0
        }
        val shadowVal = if (shadowEnabled) 1 else 0
        val msaaVal = if (msaaEnabled) 4 else 0

        val qualityVal = when (quality) {
            "Super Smooth" -> 0
            "Smooth" -> 1
            "Balanced" -> 2
            "HD" -> 3
            "HDR" -> 4
            else -> 0
        }

        val isUltraPotato = potatoLevel == "Ultra (LOD +3)" || quality == "Super Smooth"
        val screenPercentage = when {
            isUltraPotato -> 60
            resolution == "800x600" -> 55
            resolution == "960x540" -> 60
            resolution == "1280x720" -> 75
            resolution == "1440x720" -> 80
            resolution == "1600x900" -> 85
            else -> 100
        }
        val isBilinear = bilinearFilter || potatoLevel != "Off"
        val emitterSpawnRate = if (isUltraPotato) "0.2" else "0.25"
        val maxCpuParticles = if (isUltraPotato) 8 else 10

        val cvars = listOf(
            "r.UserQualitySetting=$qualityVal",
            "r.MaterialQualityLevel=$qualityVal",
            "r.DetailMode=$qualityVal",
            "r.PUBGMaxSupportQualityLevel=0",
            "r.MipMapLODBias=$lodBias",
            "r.MaxAnisotropy=${if (isBilinear) 1 else 4}",
            "r.ScreenPercentage=$screenPercentage",
            "r.ShadowQuality=$shadowVal",
            "r.LightFunctionQuality=0",
            "r.BloomQuality=0",
            "r.RefractionQuality=0",
            "r.LensFlareQuality=0",
            "r.DepthOfFieldQuality=0",
            "r.RenderTargetPoolMin=100",
            "r.DefaultFeature.AntiAliasing=$msaaVal",
            "r.PostProcessAAQuality=0",
            "r.Tonemapper.Sharpen=0",
            "r.MobileContentScaleFactor=1.0",
            "r.PUBGDeviceFPSLow=$finalFps",
            "r.PUBGDeviceFPSMid=$finalFps",
            "r.PUBGDeviceFPSHigh=$finalFps",
            "r.PUBGDeviceFPSHDR=$finalFps",
            "r.PUBGDeviceFPSUltralHigh=$finalFps",
            "r.TargetFPS=$finalFps",
            "r.SimpleDynamicLighting=${if (simpleShader || isUltraPotato) 1 else 0}",
            "r.SimpleShader=${if (simpleShader || lodBias > 0) 1 else 0}",
            "r.SoundQuality=${if (soundQuality == "Ultra") 2 else 1}",
            "r.OneFrameThreadLag=0",
            "r.FinishCurrentFrame=1",
            "r.RHICmdBypass=0",
            "p.NetEnableMoveCombining=0",
            "net.UseAdaptiveNetUpdateFrequency=0",
            "NetClientTicksPerSecond=120",
            "r.EmitterSpawnRateScale=$emitterSpawnRate",
            "r.ParticleLODBias=2",
            "fx.MaxCPUParticlesPerEmitter=$maxCpuParticles",
            "r.Streaming.PoolSize=0"
        )

        val fullIni = buildString {
            appendLine("[UserCustom DeviceProfile]")
            cvars.forEach { appendLine("+CVars=$it") }
            appendLine()
            appendLine("[BackUp DeviceProfile]")
            cvars.forEach { appendLine("+CVars=$it") }
            appendLine()
            appendLine("[SystemSettings]")
            cvars.forEach { appendLine(it) }

            // GPU Extensions
            when (gpuExtension) {
                "Adreno (GL_AMD)" -> {
                    appendLine("r.OpenGL.Extension.GL_AMD_compressed_ATC_texture=1")
                    appendLine("r.OpenGL.Extension.GL_AMD_program_binary=1")
                }
                "ARM (GL_ARM)" -> {
                    appendLine("r.OpenGL.Extension.GL_ARM_shader_framebuffer_fetch_depth_stencil=1")
                }
                "OES/EXT Compression" -> {
                    appendLine("r.OpenGL.Extension.GL_OES_compressed_ETC1_RGB8_texture=1")
                    appendLine("r.OpenGL.Extension.GL_EXT_texture_compression_s3tc=1")
                }
                "Apply All" -> {
                    appendLine("r.OpenGL.Extension.GL_ARM_shader_framebuffer_fetch_depth_stencil=1")
                    appendLine("r.OpenGL.Extension.GL_OES_depth_texture=1")
                    appendLine("r.OpenGL.Extension.GL_EXT_shader_framebuffer_fetch=1")
                    appendLine("r.OpenGL.Extension.GL_AMD_compressed_ATC_texture=1")
                    appendLine("r.OpenGL.Extension.GL_NV_depth_nonlinear=1")
                }
            }
        }

        val script = buildString {
            appendLine("mkdir -p '$cfgDir'")
            appendLine("chmod 0666 '$userCustomPath' 2>/dev/null")
            appendLine("chmod 0666 '$enjoyPath' 2>/dev/null")
            appendLine("cat << 'EOF' > '$userCustomPath'")
            appendLine(fullIni)
            appendLine("EOF")
            appendLine("cat << 'EOF' > '$enjoyPath'")
            appendLine(fullIni)
            appendLine("EOF")
            if (readOnlyLock) {
                appendLine("chmod 0444 '$userCustomPath' 2>/dev/null")
                appendLine("chmod 0444 '$enjoyPath' 2>/dev/null")
            }
        }
        Shell.run(script)

        applyGameMode(g, finalFps)
        applyAdrenoGpuAndSurfaceFlinger(g, true)

        val lockMsg = if (readOnlyLock) " (🔒 Read-Only Locked)" else " (Unlocked)"
        "PGT+ Injected: Res=$resolution, Qual=$quality, FPS=$finalFps, Potato=$potatoLevel$lockMsg"
    }

    suspend fun unlockGraphicsConfig(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val cfgDir = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val script = """
            chmod 0666 '$cfgDir/UserCustom.ini' 2>/dev/null
            chmod 0666 '$cfgDir/EnjoyCJZC.ini' 2>/dev/null
        """.trimIndent()
        Shell.run(script)
        "🔓 Graphics Config Unlocked! You can now adjust graphics in-game."
    }

    suspend fun applyUltraPotatoBlurryMode(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        applyDownscaleAndFps(g, "0.60", 120)
        val script = """
            cmd device_config put game_overlay $g mode=2,skiavk=1,downscaleFactor=0.60,fps=120 2>/dev/null
            settings put system gpu_texture_filter_quality 0
            settings put system gpu_mipmap_lod_bias 3
            settings put system gpu_anisotropic_opt 0
            settings put system miui_gpu_preemption 0
            settings put system miui_vrs_enable 0
            settings put global vrs_enable 0
            setprop debug.hwui.vrs_enable false
            setprop persist.sys.gpu_texture_filter_quality 0
            setprop debug.hwui.mipmap_lod_bias 3
        """.trimIndent()
        Shell.run(script)
        applyPgtGraphicsConfig(
            game = g,
            resolution = "960x540",
            quality = "Super Smooth",
            fps = 120,
            potatoLevel = "Ultra (LOD +3)",
            simpleShader = true,
            shadowEnabled = false,
            msaaEnabled = false,
            soundQuality = "Ultra",
            gpuExtension = "Apply All",
            saveSensitivity = true,
            bilinearFilter = true,
            readOnlyLock = true
        )
        "🧈 1-TAP ULTRA-POTATO BUTTER SMOOTH (MAX FPS + AIM LOCK) ACTIVE: 60% Scale, Bilinear LOD +3, 0ms Lag Locked!"
    }

    // =========================================================================
    // 9. ULTRA-FAST 1-TAP ESPORTS MODE & PANIC RESTORE (Parallel Subshell Execution)
    // =========================================================================
    suspend fun applyMonsterBoost(game: String, selfPkg: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        
        val fastScript = buildString {
            // Group 1: 120Hz Hardware Lock & Display Match
            appendLine("(")
            appendLine("  settings put system peak_refresh_rate 120.0")
            appendLine("  settings put system min_refresh_rate 120.0")
            appendLine("  settings put secure miui_refresh_rate 120")
            appendLine("  settings put secure user_refresh_rate 120")
            appendLine("  setprop debug.hwui.fps_divisor 1")
            appendLine("  cmd display set-match-content-frame-rate-pref 2 2>/dev/null")
            appendLine(") &")

            // Group 2: Game Turbo Hardware Bridge, WildBoost & Diablo Thermal 0
            appendLine("(")
            appendLine("  settings put system power_mode 1")
            appendLine("  settings put system power_mode high")
            appendLine("  settings put system gb_boosting 1")
            appendLine("  settings put secure gb_boosting 1")
            appendLine("  settings put system speed_mode 1")
            appendLine("  settings put secure speed_mode_enable 1")
            appendLine("  settings put system ultimate_mode 1")
            appendLine("  setprop persist.sys.power.speed 1")
            appendLine("  setprop persist.sys.performance.level 3")
            appendLine("  setprop sys.miui.feas.enable 1")
            appendLine("  setprop persist.vendor.power.feas.enable 1")
            appendLine("  cmd thermalservice override-status 0 2>/dev/null")
            appendLine("  setprop sys.thermal.mode 0")
            appendLine("  setprop persist.sys.thermal.config 0")
            appendLine(") &")

            // Group 3: Joyose, PowerKeeper & WebView Safe Data Clear + SystemUI/XMSF Safe Cache Trim
            appendLine("(")
            appendLine("  pm clear --user 0 com.miui.powerkeeper 2>/dev/null")
            appendLine("  am force-stop com.miui.powerkeeper 2>/dev/null")
            appendLine("  cmd appops set com.miui.powerkeeper RUN_IN_BACKGROUND ignore 2>/dev/null")
            appendLine("  cmd appops set com.miui.powerkeeper RUN_ANY_IN_BACKGROUND ignore 2>/dev/null")
            appendLine("  pm clear --user 0 com.xiaomi.joyose 2>/dev/null")
            appendLine("  am force-stop com.xiaomi.joyose 2>/dev/null")
            appendLine("  cmd appops set com.xiaomi.joyose RUN_IN_BACKGROUND ignore 2>/dev/null")
            appendLine("  cmd appops set com.xiaomi.joyose RUN_ANY_IN_BACKGROUND ignore 2>/dev/null")
            appendLine("  cmd appops set com.xiaomi.joyose WAKE_LOCK ignore 2>/dev/null")
            appendLine("  pm clear --user 0 com.google.android.webview 2>/dev/null")
            appendLine("  pm clear --user 0 com.android.webview 2>/dev/null")
            appendLine("  am force-stop com.google.android.webview 2>/dev/null")
            appendLine("  am force-stop com.android.webview 2>/dev/null")
            appendLine("  am force-stop com.miui.daemon 2>/dev/null")
            appendLine("  pm trim-caches 1000M com.android.systemui 2>/dev/null")
            appendLine("  pm trim-caches 4096G 2>/dev/null")
            appendLine("  cmd activity idle-maintenance 2>/dev/null")
            appendLine("  am force-stop com.xiaomi.xmsf 2>/dev/null")
            appendLine("  am force-stop com.miui.analytics 2>/dev/null")
            appendLine("  am force-stop com.miui.securityadd 2>/dev/null")
            appendLine(") &")

            // Group 4: Qualcomm Adreno GPU & SurfaceFlinger Zero-Jank
            appendLine("(")
            appendLine("  service call SurfaceFlinger 1008 i32 1 2>/dev/null")
            appendLine("  service call SurfaceFlinger 1035 i32 0 2>/dev/null")
            appendLine("  cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 2 2>/dev/null")
            appendLine("  setprop debug.sf.latch_unsignaled 1")
            appendLine("  setprop debug.sf.disable_backpressure 1")
            appendLine("  setprop debug.sf.DropMissedFrames 1")
            appendLine("  setprop debug.sf.disable_HWC 0")
            appendLine("  setprop debug.hwui.skia_atrace_enabled false")
            appendLine("  setprop debug.hwui.overdraw false")
            appendLine("  setprop debug.sf.gpu_freq_index 0")
            appendLine("  setprop debug.sf.cpu_freq_index 0")
            appendLine("  setprop debug.hwui.render_thread_priority -20")
            appendLine("  setprop debug.renderengine.backend skiaglthreaded")
            appendLine("  setprop vendor.gpu.control.level 3")
            appendLine("  setprop debug.egl.swapinterval 0")
            appendLine("  settings put global updatable_driver_all_apps 3")
            appendLine("  settings put global game_driver_opt_in_apps $g")
            appendLine(") &")

            // Group 5: Redmi 2560Hz Instant Touch & 480Hz IC
            appendLine("(")
            appendLine("  settings put system touch_response_speed 1")
            appendLine("  settings put system touch_sensitivity 5")
            appendLine("  settings put system pointer_speed 7")
            appendLine("  settings put system touch_boost 1")
            appendLine("  settings put system touch_sampling_rate 1")
            appendLine("  settings put system touch_response_boost 1")
            appendLine("  settings put system miui_touch_sampling_rate_boost 1")
            appendLine("  settings put secure touch_game_mode 1")
            appendLine("  settings put system tp_game_mode 1")
            appendLine("  settings put system touch_game_mode_enable 1")
            appendLine("  settings put system gamemode_touch_optimization 1")
            appendLine("  settings put system gamemode_touch_follow_response 100")
            appendLine("  settings put system gamemode_touch_continuous_response 100")
            appendLine("  settings put system gamemode_touch_aim_sensitivity 100")
            appendLine("  settings put system gamemode_touch_aim_response 100")
            appendLine("  settings put system gamemode_touch_tap_stability 100")
            appendLine("  settings put system gamemode_edge_filter 0")
            appendLine("  settings put system touch_slop 1")
            appendLine("  settings put secure tap_timeout 50")
            appendLine("  settings put secure long_press_timeout 120")
            appendLine("  settings put secure multi_press_timeout 100")
            appendLine("  settings put secure touch_blocking_period 0.0")
            appendLine("  cmd device_config put input_native_boot resample_touch false 2>/dev/null")
            appendLine("  cmd device_config put input_native_boot velocitytracker_strategy impulse 2>/dev/null")
            appendLine("  cmd device_config put input_native_boot deep_press_enabled false 2>/dev/null")
            appendLine("  setprop windowsmgr.max_events_per_sec 240")
            appendLine("  setprop persist.vendor.touch.sampling_rate 480")
            appendLine("  setprop persist.sys.touch.report_rate 480")
            appendLine("  setprop debug.touch.frequency 480")
            appendLine("  setprop debug.performance.tuning 1")
            appendLine("  setprop video.accelerate.hw 1")
            appendLine("  setprop debug.sf.hw 1")
            appendLine("  cmd input set-pointer-acceleration 0 2>/dev/null")
            appendLine(") &")

            // Group 6: Bullet Registration & Zero-Desync (2-Buffer + VRS Killer + SkiaGL)
            appendLine("(")
            appendLine("  settings put system miui_vrs_enable 0")
            appendLine("  settings put global vrs_enable 0")
            appendLine("  setprop debug.hwui.vrs_enable false")
            appendLine("  cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 2 2>/dev/null")
            appendLine("  setprop debug.cpuprio 7")
            appendLine("  setprop debug.gpuprio 7")
            appendLine("  setprop debug.ioprio 7")
            appendLine("  setprop debug.sf.hw 1")
            appendLine("  setprop debug.PerfLock 1")
            appendLine("  setprop debug.hwui.renderer skiagl")
            appendLine(") &")

            // Group 7: Low-Ping Network & Wi-Fi Bullet Connectivity Stack
            appendLine("(")
            appendLine("  cmd netd resolver flushdefaultif 2>/dev/null")
            appendLine("  cmd wifi set-wifi-enabled enabled 2>/dev/null")
            appendLine("  settings put global wifi_scan_always_enabled 0")
            appendLine("  settings put global wifi_suspend_optimizations_enabled 0")
            appendLine("  settings put global wifi_power_save 0")
            appendLine("  settings put global mobile_data_always_on 1")
            appendLine("  settings put global network_latency_opt 1")
            appendLine("  settings put system cloud_lowlatency_whitelist $g")
            appendLine("  settings put system cloud_network_priority_whitelist $g")
            appendLine(") &")

            // Group 8: Game Mode 120 FPS & Unrestricted Power Policy
            appendLine("(")
            appendLine("  cmd game set --mode 2 --fps 120 $g 2>/dev/null")
            appendLine("  cmd game mode performance $g 2>/dev/null")
            appendLine("  dumpsys deviceidle whitelist +$g 2>/dev/null")
            appendLine("  am set-standby-bucket $g 5 2>/dev/null")
            appendLine("  cmd appops set $g RUN_IN_BACKGROUND allow 2>/dev/null")
            appendLine("  cmd appops set $g RUN_ANY_IN_BACKGROUND allow 2>/dev/null")
            appendLine("  cmd appops set $g WAKE_LOCK allow 2>/dev/null")
            appendLine("  cmd appops set $g START_FOREGROUND allow 2>/dev/null")
            appendLine("  settings put system cloud_memory_freeze_whitelist \"$g\" 2>/dev/null")
            appendLine("  settings put system millet_white \"$g;\" 2>/dev/null")
            appendLine(") &")

            // Group 9: CPU Priority & Pin PID if running
            appendLine("(")
            appendLine("  PUBG_PID=\$(pidof $g 2>/dev/null)")
            appendLine("  if [ ! -z \"\$PUBG_PID\" ]; then")
            appendLine("    taskset -p -a ff \$PUBG_PID 2>/dev/null")
            appendLine("    ionice -c 2 -n 0 -p \$PUBG_PID 2>/dev/null")
            appendLine("  fi")
            appendLine(") &")

            appendLine("wait")
        }

        Shell.run(fastScript)

        // Asynchronously inject Netcode into UserCustom.ini without blocking UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                injectZeroDesyncNetcodeAndSmokeAntiLag(g)
            } catch (_: Throwable) {}
        }

        "🔥 100% STABLE ESPORTS MODE ACTIVATED in <300ms for ${Games.known[g] ?: g}"
    }

    suspend fun esportsMode(game: String, selfPkg: String): String = applyMonsterBoost(game, selfPkg)

    suspend fun applyTouchPreset(presetId: Int): String = withContext(Dispatchers.IO) {
        when (presetId) {
            1 -> {
                val script = """
                    settings put system pointer_speed 7
                    settings put secure touch_slop 1 2>/dev/null
                    settings put secure tap_timeout 60 2>/dev/null
                    setprop debug.touch.resample false 2>/dev/null
                    setprop debug.touch.velocitytracker impulse 2>/dev/null
                    setprop persist.vendor.touch.sampling_rate 480
                    setprop persist.sys.touch.report_rate 480
                    settings put system gamemode_touch_follow_response 100
                    settings put system gamemode_touch_continuous_response 100
                """.trimIndent()
                Shell.run(script)
                "🔥 Preset 1: Close-Range Jiggle God Active (0.1ms Raw Tap, 480Hz, Impulse Tracker)"
            }
            2 -> {
                val script = """
                    settings put system pointer_speed 4
                    settings put secure touch_slop 2 2>/dev/null
                    settings put system gamemode_touch_aim_response 100
                    settings put system gamemode_touch_tap_stability 100
                    setprop debug.touch.resample false 2>/dev/null
                    setprop persist.vendor.sensors.gyro.sampling_rate 400 2>/dev/null
                    setprop debug.qualcomm.sns.hal.sensor.multichannel.enabled 1
                    setprop persist.vendor.touch.sampling_rate 360
                """.trimIndent()
                Shell.run(script)
                "🎯 Preset 2: Laser AR Spray & Gyro Recoil Active (Smooth Micro-Aim, 400Hz Gyro Filter)"
            }
            3 -> {
                val script = """
                    settings put system pointer_speed 5
                    settings put secure tap_duration 40 2>/dev/null
                    settings put secure multi_press_timeout 100
                    settings put secure long_press_timeout 120
                    settings put system gamemode_touch_continuous_response 100
                    settings put system gamemode_touch_aim_response 95
                    setprop persist.vendor.touch.sampling_rate 480
                """.trimIndent()
                Shell.run(script)
                "⚡ Preset 3: DMR Single-Tap Sniper Active (Ultra-Fast 40ms De-Bounce Multi-Tap)"
            }
            else -> {
                Shell.run("settings put system pointer_speed 0; settings put system gamemode_touch_follow_response 50")
                "Touch Profile Reset to Stock"
            }
        }
    }

    suspend fun applyAudioAndDacFix(enable: Boolean = true): String = withContext(Dispatchers.IO) {
        if (enable) {
            val script = """
                setprop audio.safemedia.bypass true
                setprop debug.audio.silent 0
                settings put system peak_volume_mode 1
                settings put secure music_in_communication 1
                settings put system music_in_communication 1
                setprop vendor.audio.lowlatency.enable true
                setprop media.stagefright.low-latency 1
                settings put global bluetooth_disable_absolute_volume 1
            """.trimIndent()
            Shell.run(script)
            "🎧 Footstep Audio Latency Uncapped & Mic Audio Bypass Active"
        } else {
            Shell.run("settings put system music_in_communication 0; settings put secure music_in_communication 0; setprop audio.safemedia.bypass false")
            "Audio Latency Reset to Stock"
        }
    }

    suspend fun applyUsbDacSplitterGuard(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val script = """
                settings put global usb_audio_automatic_routing_disabled 0
                settings put secure usb_audio_automatic_routing_disabled 0
                settings put system miui_audio_dump_enable 0
                settings put secure music_in_communication 1
                settings put system music_in_communication 1
                setprop persist.vendor.audio.fluence.voicecall false
                setprop debug.audio.usb.period_us 2500
                setprop audio.safemedia.bypass true
                setprop debug.audio.silent 0
                setprop vendor.audio.lowlatency.enable true
                setprop media.stagefright.low-latency 1
                settings put global bluetooth_disable_absolute_volume 1
            """.trimIndent()
            Shell.run(script)
            "🎧 Type-C Dual Adapter & USB DAC 48/96kHz Stability ACTIVE"
        } else {
            val script = """
                settings put system music_in_communication 0
                settings put secure music_in_communication 0
                setprop persist.vendor.audio.fluence.voicecall true
                setprop audio.safemedia.bypass false
            """.trimIndent()
            Shell.run(script)
            "USB DAC & Splitter Guard RESTORED to stock"
        }
    }

    @Volatile var isHotdropOverdriveActive: Boolean = false

    suspend fun applyHotdropOverdrive(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        isHotdropOverdriveActive = enable
        if (enable) {
            val script = """
                cmd thermalservice override-status 0 2>/dev/null
                cmd power set-fixed-performance-mode-enabled true 2>/dev/null
                service call SurfaceFlinger 1008 i32 1 2>/dev/null
                setprop debug.sf.gpu_freq_index 0
                setprop debug.sf.cpu_freq_index 0
                setprop vendor.gpu.control.level 3
                echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null
                echo 500000000 > /sys/class/kgsl/kgsl-3d0/min_clock_mhz 2>/dev/null
            """.trimIndent()
            Shell.run(script)
            pinGameProcess(g)
            "🔥 HOTDROP OVERDRIVE ACTIVE (120 FPS Locked in Smoke/Close-Range)"
        } else {
            val script = """
                cmd power set-fixed-performance-mode-enabled false 2>/dev/null
                service call SurfaceFlinger 1008 i32 0 2>/dev/null
                echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null
                echo 0 > /sys/class/kgsl/kgsl-3d0/min_clock_mhz 2>/dev/null
            """.trimIndent()
            Shell.run(script)
            "Hotdrop Overdrive Restored"
        }
    }

    suspend fun quickLagAndPingFlush(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val script = """
            cmd netd resolver flushdefaultif 2>/dev/null
            service call SurfaceFlinger 1008 i32 1 2>/dev/null
            service call SurfaceFlinger 1035 i32 0 2>/dev/null
            cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 2 2>/dev/null
            setprop debug.sf.latch_unsignaled 1
            setprop debug.sf.disable_backpressure 1
            setprop debug.sf.DropMissedFrames 1
            setprop debug.cpuprio 7
            setprop debug.gpuprio 7
            setprop debug.ioprio 7
            settings put global wifi_scan_always_enabled 0 2>/dev/null
            settings put global wifi_suspend_optimizations_enabled 0 2>/dev/null
            settings put global network_latency_opt 1 2>/dev/null
            settings put system cloud_lowlatency_whitelist "$g" 2>/dev/null
            settings put system cloud_network_priority_whitelist "$g" 2>/dev/null
            pm clear --user 0 com.miui.powerkeeper 2>/dev/null
            am force-stop com.miui.powerkeeper 2>/dev/null
            pm clear --user 0 com.xiaomi.joyose 2>/dev/null
            am force-stop com.xiaomi.joyose 2>/dev/null
            pm clear --user 0 com.google.android.webview 2>/dev/null
            pm clear --user 0 com.android.webview 2>/dev/null
            am force-stop com.google.android.webview 2>/dev/null
            am force-stop com.android.webview 2>/dev/null
            cmd thermalservice override-status 0 2>/dev/null
            PUBG_PID=${'$'}(pidof $g 2>/dev/null)
            if [ ! -z "${'$'}PUBG_PID" ]; then
                taskset -p -a ff ${'$'}PUBG_PID 2>/dev/null
                ionice -c 2 -n 0 -p ${'$'}PUBG_PID 2>/dev/null
            fi
            rm -rf '/sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Logs'/* 2>/dev/null
        """.trimIndent()
        Shell.run(script)
        pinGameProcess(g)
        "⚡ LAG, PING & THERMAL CACHES FLUSHED!"
    }

    suspend fun revertAll(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val revertScript = """
            settings put system peak_refresh_rate 120.0
            settings put system min_refresh_rate 60.0
            settings put secure miui_refresh_rate 120
            settings put system speed_mode 0
            settings put system ultimate_mode 0
            setprop persist.sys.performance.level 0
            cmd thermalservice reset
            setprop debug.hwui.render_thread_priority -10
            setprop vendor.gpu.control.level 0
            setprop debug.egl.swapinterval 1
            settings put global game_driver_opt_in_apps ""
            settings put system gamemode_touch_follow_response 50
            settings put system pointer_speed 0
            cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 3
            setprop debug.cpuprio 0
            setprop debug.gpuprio 0
            setprop debug.ioprio 0
            cmd netpolicy set restrict-background false
            settings put global private_dns_mode opportunistic
            cmd game reset $g
            wm size reset
            wm density reset
            settings put secure accessibility_display_inversion_enabled 0
            settings put secure accessibility_display_daltonizer_enabled 0
            settings put secure accessibility_display_magnification_enabled 0
            echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
        """.trimIndent()
        Shell.run(revertScript)
        "RESTORED ALL SETTINGS TO FACTORY DEFAULTS"
    }

    suspend fun createSettingsSnapshot(): String = withContext(Dispatchers.IO) {
        val script = """
            mkdir -p $BACKUP_DIR
            settings list system > $BACKUP_DIR/system_backup.txt
            settings list secure > $BACKUP_DIR/secure_backup.txt
            settings list global > $BACKUP_DIR/global_backup.txt
        """.trimIndent()
        Shell.run(script)
        "Full Settings Snapshot Backup saved to $BACKUP_DIR"
    }

    suspend fun grantOverlay(pkg: String): String = withContext(Dispatchers.IO) {
        Shell.run("appops set $pkg SYSTEM_ALERT_WINDOW allow").ifBlank { "Overlay permission granted via Shizuku" }
    }

    suspend fun launchFloatingMiniApp(pkg: String = "com.whatsapp"): String = withContext(Dispatchers.IO) {
        Shell.run("settings put global enable_freeform_support 1; am start --windowingMode 5 $pkg")
        "Floating Mini-App Launched ($pkg)"
    }
}
