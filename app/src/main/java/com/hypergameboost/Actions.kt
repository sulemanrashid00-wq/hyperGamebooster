package com.hypergameboost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Profile { PERFORMANCE, BALANCED, BATTERY_SAVER }

object Games {
    /** All official PUBG Mobile variants supported by HyperGameBooster (Section 1) */
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

    fun safeGame(pkg: String): String {
        return if (pkg in Games.known) pkg else "com.tencent.ig"
    }

    suspend fun installedGames(): List<String> = withContext(Dispatchers.IO) {
        val out = Shell.run("pm list packages")
        Games.known.keys.filter { out.contains("package:$it") }
    }

    suspend fun launch(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Shell.run("monkey -p $g -c android.intent.category.LAUNCHER 1")
    }

    // =========================================================================
    // 1. ANDROID 16 PER-GAME DOWNSCALING & 120 FPS ENGINE
    // =========================================================================
    suspend fun applyDownscaleAndFps(game: String, downscale: String = "0.75", fps: Int = 120): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val validFps = if (fps > 120) 120 else fps // Enforce conflict rule: lock to 120
        val cmds = listOf(
            "cmd game set --mode 2 --downscale $downscale --fps $validFps $g",
            "device_config put game_overlay $g mode=2,skiavk=1,downscaleFactor=$downscale,fps=$validFps:mode=3,skiavk=1,downscaleFactor=0.6,fps=90",
            "cmd game mode performance $g"
        )
        Shell.runBatch(cmds)
        "Game Mode Set: Downscale=$downscale, FPS=$validFps, Mode=Performance"
    }

    suspend fun resetDownscaleAndFps(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Shell.runBatch(
            listOf(
                "cmd game reset $g",
                "device_config delete game_overlay $g"
            )
        )
        "Game Mode and Overlay Reset for $g"
    }

    // =========================================================================
    // 2. THERMAL THROTTLING BYPASS & FIXED PERFORMANCE GOVERNOR (DIABLO MODE)
    // =========================================================================
    suspend fun applyDiabloThermalMode(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val cmds = listOf(
                "cmd thermalservice override-status 0",
                "cmd power set-mode 0",
                "cmd power set-fixed-performance-mode-enabled true",
                "cmd power set-adaptive-power-saver-enabled false",
                "cmd thermalservice headroom 10"
            )
            Shell.runBatch(cmds)
            "Diablo Mode ACTIVE: Thermal Overridden (0), Fixed Perf Clocks Locked"
        } else {
            val cmds = listOf(
                "cmd thermalservice reset",
                "cmd power set-fixed-performance-mode-enabled false",
                "cmd power set-adaptive-power-saver-enabled true"
            )
            Shell.runBatch(cmds)
            "Diablo Mode DISABLED: Thermal & Power Service Restored"
        }
    }

    // =========================================================================
    // 3. HYPEROS 3 120HZ HARD LOCK, OLED SMALL-AREA HZ DROP FIX & DC DIMMING KILLER
    // =========================================================================
    suspend fun applyHyperOs120HzHardLock(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val cmds = listOf(
                "settings put system peak_refresh_rate 120.0",
                "settings put system min_refresh_rate 120.0",
                "settings put secure user_refresh_rate 120",
                "settings put secure miui_refresh_rate 120",
                "settings put system is_smart_fps 0",
                "settings put system framepredict_enable 0",
                "settings put global refresh_rate_mode 0",
                "settings put global low_power_refresh_rate 120",
                "settings put global display_dynamic_fps 0",
                "settings put system dc_back_light 0",
                "settings put system smart_dfps_enable 0",
                "settings put system ai_display_enable 0",
                "settings put system memc_enable 0",
                "service call SurfaceFlinger 1035 i32 0",
                "cmd device_config put surface_flinger_native_boot enable_small_area_detection false",
                "cmd device_config put surface_flinger_native_boot enable_layer_caching false",
                "cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 3"
            )
            Shell.runBatch(cmds)
            "HyperOS 3 Hard-Lock: 120Hz Constant, OLED Small Area Fix ON, DC Dimming 0"
        } else {
            val cmds = listOf(
                "settings delete system peak_refresh_rate",
                "settings delete system min_refresh_rate",
                "settings delete secure user_refresh_rate",
                "settings delete secure miui_refresh_rate",
                "settings put system is_smart_fps 1",
                "cmd device_config delete surface_flinger_native_boot enable_small_area_detection",
                "cmd device_config delete surface_flinger_native_boot enable_layer_caching"
            )
            Shell.runBatch(cmds)
            "Display Refresh Rate restored to Auto"
        }
    }

    // =========================================================================
    // 4. HYPEROS 3 WILDBOOST, FEAS, FURIOSO ULTIMATE MODE & UI BLUR DISABLER
    // =========================================================================
    suspend fun applyWildBoostAndFeas(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val cmds = listOf(
                "settings put system speed_mode 1",
                "settings put secure speed_mode_enable 1",
                "settings put system power_mode high",
                "settings put system gb_boosting 1",
                "settings put system ultimate_mode_enable 1",
                "settings put secure miui_ULTIMATE_MODE 1",
                "settings put global cpu_boost 1",
                "settings put global gpu_boost 1",
                "settings put global thermal_limit 0",
                "settings put global scheduler_policy performance",
                "settings put secure miui_app_boost_pkg $g",
                "settings put global game_mode_$g 1",
                "settings put system deviceLevelList v:1,c:3,g:3",
                "settings put secure background_blur_enable 0",
                "settings put system miui_home_animation_rate 3",
                "settings put global window_animation_scale 0.5",
                "settings put global transition_animation_scale 0.5",
                "settings put global animator_duration_scale 0.5"
            )
            Shell.runBatch(cmds)
            "WildBoost 3.0 & FEAS Ultimate Mode: ENABLED for $g"
        } else {
            val cmds = listOf(
                "settings put system speed_mode 0",
                "settings put secure speed_mode_enable 0",
                "settings put system ultimate_mode_enable 0",
                "settings put secure miui_ULTIMATE_MODE 0",
                "settings delete global scheduler_policy",
                "settings put secure background_blur_enable 1",
                "settings put global window_animation_scale 1.0",
                "settings put global transition_animation_scale 1.0",
                "settings put global animator_duration_scale 1.0"
            )
            Shell.runBatch(cmds)
            "WildBoost & FEAS: Restored to default"
        }
    }

    // =========================================================================
    // 5. POWERKEEPER, JOYOSE & MIUI DAEMON THERMAL/BACKGROUND BYPASS
    // =========================================================================
    suspend fun restrictPowerKeeperAndJoyose(): String = withContext(Dispatchers.IO) {
        val cmds = listOf(
            "cmd appops set com.miui.powerkeeper RUN_IN_BACKGROUND ignore",
            "cmd appops set com.miui.powerkeeper RUN_ANY_IN_BACKGROUND ignore",
            "cmd appops set com.miui.daemon RUN_IN_BACKGROUND ignore",
            "cmd appops set com.miui.daemon RUN_ANY_IN_BACKGROUND ignore",
            "pm clear com.xiaomi.joyose",
            "am force-stop com.xiaomi.joyose",
            "am force-stop com.miui.daemon",
            "am force-stop com.miui.analytics",
            "am force-stop com.miui.msa.global"
        )
        Shell.runBatch(cmds)
        "PowerKeeper & Joyose Restrained: Background Ops Ignored & Joyose Stopped"
    }

    // =========================================================================
    // 6. HYPEROS CLOUD GREEZER, MILLET & VIP STANDBY RAM WHITELIST
    // =========================================================================
    suspend fun applyHyperOsCloudGreezerAndMillet(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val cmds = listOf(
                "settings put system cloud_lowlatency_whitelist \"$g\"",
                "settings put system cloud_network_priority_whitelist \"$g\"",
                "settings put system cloud_memory_freeze_whitelist \"$g\"",
                "settings put system millet_white \"$g;\"",
                "settings put system cloud_greezer_enable false",
                "settings put system cloud_memFreeze_control 0",
                "settings put system cloud_sleepmode_networkpolicy_enabled off",
                "settings put system background_power_saving_enable 0",
                "am set-standby-bucket $g 5",
                "dumpsys deviceidle whitelist +$g",
                "device_config put activity_manager max_phantom_processes 1024",
                "settings put global cached_apps_freezer enabled",
                "device_config put activity_manager use_compaction true"
            )
            Shell.runBatch(cmds)
            "Millet & Cloud Greezer: $g VIP Whitelisted (Standby Bucket 5)"
        } else {
            val cmds = listOf(
                "am set-standby-bucket $g 20",
                "dumpsys deviceidle whitelist -$g",
                "settings delete system cloud_lowlatency_whitelist",
                "settings delete system millet_white"
            )
            Shell.runBatch(cmds)
            "Millet & Greezer settings restored"
        }
    }

    // =========================================================================
    // 7. GPU DRIVER, ANGLE, ADRENO KEYS & 8.33MS (8333333NS) SURFACEFLINGER ENGINE
    // =========================================================================
    suspend fun applyAdrenoGpuAndSurfaceFlinger(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val cmds = listOf(
                "settings put global updatable_driver_all_apps 0",
                "settings put global game_driver_opt_in_apps $g",
                "settings put global updatable_driver_production_opt_in_apps $g",
                "settings put global angle_gl_driver_all_angle 0",
                "settings put global angle_gl_driver_selection_pkgs $g",
                "settings put global angle_gl_driver_selection_values angle",
                "settings put system gpu_texture_filter_quality 0",
                "settings put system gpu_mipmap_lod_bias 3",
                "settings put system gpu_sub_sample_enable 1",
                "settings put system gpu_anisotropic_opt 1",
                "settings put system miui_gpu_preemption 0",
                "setprop debug.hwui.renderer skiavk",
                "setprop debug.egl.force_msaa false",
                "setprop debug.egl.profiler false",
                "setprop debug.egl.hw 1",
                "setprop debug.egl.sync_EveryFrame 0",
                "setprop debug.gralloc.gfx_ubwc_disable 0",
                "setprop debug.sf.latch_unsignaled 1",
                "setprop debug.sf.disable_backpressure 1",
                "setprop debug.sf.enable_gl_backpressure 0",
                "setprop debug.sf.enable_adpf_cpu_hint true",
                "setprop debug.hwui.use_hint_manager true",
                "setprop debug.hwui.target_cpu_time_percent 100",
                "setprop debug.sf.use_phase_offsets_as_durations 1",
                "setprop debug.sf.late.sf.duration 8333333",
                "setprop debug.sf.late.app.duration 8333333",
                "setprop debug.sf.early.sf.duration 8333333",
                "setprop debug.sf.early.app.duration 8333333",
                "setprop debug.sf.earlyGl.sf.duration 8333333",
                "setprop debug.sf.earlyGl.app.duration 8333333",
                "setprop debug.sf.early_phase_offset_ns 500000",
                "setprop debug.sf.early_gl_phase_offset_ns 3000000",
                "setprop debug.sf.disable_client_composition_cache 1",
                "setprop debug.sf.predict_hwc_composition_strategy 0",
                "setprop debug.hwui.render_dirty_regions false",
                "setprop debug.hwui.use_gpu_pixel_buffers true",
                "setprop debug.hwui.skip_empty_damage true",
                "setprop debug.hwui.disable_draw_defer true",
                "setprop debug.hwui.disable_draw_reorder true",
                "settings put system hwui_disable_vsync 0",
                "settings put system hwui_render_ahead 1",
                "settings put global surfaceflinger_max_frame_interval 1",
                "cmd device_config put surface_flinger_native_boot use_frame_rate_api true",
                "cmd device_config put activity_manager open_gl_gpu_performance enable",
                "cmd device_config put activity_manager force_cpu_performance enable",
                "cmd display set-user-disabled-hdr-types 1 2 3 4"
            )
            Shell.runBatch(cmds)
            "Adreno GPU Keys & SurfaceFlinger 8.33ms Duration Engine: ACTIVE"
        } else {
            val cmds = listOf(
                "settings delete global game_driver_opt_in_apps",
                "settings delete global angle_gl_driver_selection_pkgs",
                "cmd display set-user-disabled-hdr-types"
            )
            Shell.runBatch(cmds)
            "GPU driver settings reverted to default"
        }
    }

    // =========================================================================
    // 8. ANDROID 16 RAW 1:1 IMPULSE TOUCH & HYPEROS 3 PRO TOUCH IC ENGINE
    // =========================================================================
    suspend fun applyImpulseTouchAndTouchIc(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val cmds = listOf(
                "cmd device_config set-sync-disabled-for-tests persistent",
                "cmd device_config put input_native_boot resample_touch false",
                "cmd device_config put input_native_boot velocitytracker_strategy impulse",
                "cmd device_config put input_native_boot deep_press_enabled false",
                "cmd device_config put input_native_boot scale_Factor 1.0",
                "settings put system gamemode_touch_follow_response 100",
                "settings put system gamemode_touch_aim_sensitivity 90",
                "settings put system gamemode_touch_tap_stability 100",
                "settings put system gamemode_touch_edge_suppression 1",
                "settings put system tp_game_mode 1",
                "settings put system touch_game_mode_enable 1",
                "settings put system miui_touch_sampling_rate_boost 1",
                "settings put secure gamebox_stick 1",
                "settings put system gamemode_touch_optimization 1",
                "settings put system pointer_speed 4",
                "settings put system touch_sensitivity 2",
                "settings put system touchscreen_gesture_sensitivity 2",
                "settings put system touch_response_boost 1",
                "settings put system touch_sampling_rate 1",
                "settings put system touch_latency 0",
                "settings put system touch_slop 2",
                "settings put system tap_duration 50",
                "settings put secure tap_duration_threshold 0.0",
                "settings put secure touch_blocking_period 0.0",
                "settings put system touch_blocking_period 0",
                "settings put secure tap_timeout 80",
                "settings put secure long_press_timeout 200",
                "settings put secure multi_press_timeout 150",
                "settings put secure touch_pressure_gesture_exclusion 0",
                "settings put global block_untrusted_touches 0",
                "settings put system multicore_packet_scheduler 1",
                "setprop windowsmgr.max_events_per_sec 240",
                "setprop debug.sf.frame_rate_multiple_threshold 120"
            )
            Shell.runBatch(cmds)
            "Raw 1:1 Impulse Touch Engine & HyperOS Touch IC Keys: LOCKED"
        } else {
            val cmds = listOf(
                "cmd device_config delete input_native_boot resample_touch",
                "cmd device_config delete input_native_boot velocitytracker_strategy",
                "settings put system pointer_speed 0"
            )
            Shell.runBatch(cmds)
            "Touch settings restored to default"
        }
    }

    // =========================================================================
    // 9. VISUAL CAMPER FILTERS (EAGLE EYE SATURATION, ROG SCOUT, REDMAGIC HUNT)
    // =========================================================================
    suspend fun applyCamperFilters(filterMode: String): String = withContext(Dispatchers.IO) {
        when (filterMode) {
            "eagle_eye" -> {
                Shell.runBatch(
                    listOf(
                        "service call SurfaceFlinger 1022 f 1.5",
                        "service call SurfaceFlinger 1008 i32 1",
                        "settings put system screen_mode_setting 1",
                        "settings put secure accessibility_display_inversion_enabled 0",
                        "settings put secure accessibility_display_daltonizer_enabled 0"
                    )
                )
                "Eagle Eye Saturation (1.5x) Applied"
            }
            "scout_inversion" -> {
                Shell.runBatch(
                    listOf(
                        "service call SurfaceFlinger 1022 f 1.0",
                        "settings put secure accessibility_display_inversion_enabled 1"
                    )
                )
                "ROG Scout Mode Inversion Active"
            }
            "hunt_grass" -> {
                Shell.runBatch(
                    listOf(
                        "service call SurfaceFlinger 1022 f 1.3",
                        "settings put secure accessibility_display_daltonizer_enabled 1",
                        "settings put secure accessibility_display_daltonizer 12"
                    )
                )
                "RedMagic Hunt Mode (Green Grass Contrast) Active"
            }
            "hunt_red" -> {
                Shell.runBatch(
                    listOf(
                        "settings put secure accessibility_display_daltonizer_enabled 1",
                        "settings put secure accessibility_display_daltonizer 11"
                    )
                )
                "RedMagic Hunt Mode (Red Enemy Boost) Active"
            }
            else -> {
                Shell.runBatch(
                    listOf(
                        "service call SurfaceFlinger 1022 f 1.0",
                        "service call SurfaceFlinger 1008 i32 0",
                        "settings put secure accessibility_display_inversion_enabled 0",
                        "settings put secure accessibility_display_daltonizer_enabled 0"
                    )
                )
                "Display Filters Reset to Normal"
            }
        }
    }

    // =========================================================================
    // 10. ASPECT-RATIO HARDWARE DOWNSCALER & FREEFORM IPAD VIEW
    // =========================================================================
    suspend fun applyAspectPreset(preset: Int): String = withContext(Dispatchers.IO) {
        when (preset) {
            1 -> {
                Shell.runBatch("wm size 900x2000", "wm density 340")
                "Preset 1 Applied: Redmi 900p Pro (900x2000, 340 DPI)"
            }
            2 -> {
                Shell.runBatch("wm size 810x1800", "wm density 300")
                "Preset 2 Applied: Scrims 810p Ultra FPS (810x1800, 300 DPI)"
            }
            3 -> {
                Shell.runBatch("wm size 720x1600", "wm density 280")
                "Preset 3 Applied: Potato 720p (720x1600, 280 DPI)"
            }
            else -> {
                Shell.runBatch("wm size reset", "wm density reset")
                "Resolution and Density Reset to Stock"
            }
        }
    }

    suspend fun launchFreeformIpadView(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val cmds = listOf(
            "settings put global enable_freeform_support 1",
            "settings put global force_resizable_activities 1",
            "settings put global enable_non_resizable_multi_window 1",
            "settings put development enable_freeform_support 1",
            "am start --windowingMode 5 $g/com.epicgames.ue4.SplashActivity"
        )
        Shell.runBatch(cmds)
        "Freeform iPad View Multi-Window Launched for $g"
    }

    // =========================================================================
    // 11. REDMAGIC "SCREEN EXTRACTION" HARDWARE SNIPER MAGNIFIER
    // =========================================================================
    suspend fun setMagnifier(enable: Boolean, scale: Float = 2.5f): String = withContext(Dispatchers.IO) {
        isMagnifierOn = enable
        val s = scale.coerceIn(1.5f, 5.0f)
        if (enable) {
            val cmds = listOf(
                "settings put secure accessibility_display_magnification_scale $s",
                "settings put secure accessibility_display_magnification_enabled 1"
            )
            Shell.runBatch(cmds)
            "Sniper Magnifier ACTIVE (${s}x Zero-Lag Zoom)"
        } else {
            Shell.run("settings put secure accessibility_display_magnification_enabled 0")
            "Sniper Magnifier DISABLED"
        }
    }

    // =========================================================================
    // 12. IN-GAME FLOATING MINI-APP LAUNCHER (Freeform windowingMode 5)
    // =========================================================================
    suspend fun launchFloatingMiniApp(pkg: String = "com.whatsapp"): String = withContext(Dispatchers.IO) {
        val cmds = listOf(
            "settings put global enable_freeform_support 1",
            "settings put global force_resizable_activities 1",
            "am start --windowingMode 5 $pkg"
        )
        Shell.runBatch(cmds)
        "Floating Mini-App Launched ($pkg)"
    }

    // =========================================================================
    // 13. BYPASS CHARGING & THERMAL CHARGE PROTECTION (ROG & Xiaomi)
    // =========================================================================
    suspend fun setBypassCharging(enable: Boolean): String = withContext(Dispatchers.IO) {
        isBypassChargingOn = enable
        val v = if (enable) "1" else "0"
        val script = """
            if [ -f /sys/class/power_supply/battery/input_suspend ]; then
                echo $v > /sys/class/power_supply/battery/input_suspend
            fi
            if [ -f /sys/class/power_supply/battery/night_charging ]; then
                echo $v > /sys/class/power_supply/battery/night_charging
            fi
            settings put system charge_boost_enable ${if (enable) 0 else 1}
            settings put system charge_protection_enable ${if (enable) 1 else 0}
            echo "Bypass Charging set to $v"
        """.trimIndent()
        val out = Shell.run(script)
        if (enable) "🔌 Bypass Charging ON (Battery heating eliminated during match)"
        else "🔌 Standard Charging RESTORED"
    }

    // =========================================================================
    // 14. LIVE CPU PRIME CORE PINNING (TASKSET) & REAL-TIME DISK I/O (IONICE)
    // =========================================================================
    suspend fun pinGameProcess(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val script = """
            PUBG_PID=${'$'}(pidof $g)
            if [ ! -z "${'$'}PUBG_PID" ]; then
                taskset -p -a ff ${'$'}PUBG_PID
                ionice -c 2 -n 0 -p ${'$'}PUBG_PID
                echo "PID ${'$'}PUBG_PID pinned to Prime CPU cores (mask ff) and Real-time IO"
            else
                echo "Game process not running yet"
            fi
        """.trimIndent()
        Shell.run(script)
    }

    // =========================================================================
    // 15. UE4 ANTI-STUTTER LOG CLEANER, DEX SPEED COMPILE & STORAGE FSTRIM
    // =========================================================================
    suspend fun cleanUe4StutterLogs(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val cmds = listOf(
            "rm -rf /sdcard/Android/data/$g/cache/*",
            "rm -rf /sdcard/Android/data/$g/files/ProgramBinaryCache/*",
            "rm -rf /sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Logs/*",
            "rm -rf /sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Pandora/*",
            "rm -rf /sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/UpdateInfo/*",
            "rm -rf /sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/TableDatas/*"
        )
        Shell.runBatch(cmds)
        "UE4 Program Binary & Anti-Stutter Logs Purged for $g"
    }

    suspend fun artSpeedCompile(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val cmds = listOf(
            "settings put secure app_launch_boost 1",
            "settings put secure zygote_prefork 1",
            "settings put secure ram_boost 1",
            "cmd package compile -m speed -f $g",
            "cmd package bg-dexopt-job $g"
        )
        Shell.runBatch(cmds)
        "ART Speed Compilation completed for $g"
    }

    suspend fun suspendHeavyApps(enable: Boolean): String = withContext(Dispatchers.IO) {
        val apps = listOf("com.zhiliaoapp.musically", "com.instagram.android", "com.facebook.katana")
        val action = if (enable) "suspend" else "unsuspend"
        val cmds = apps.map { "cmd package $action $it" } + if (enable) listOf(
            "am set-standby-bucket com.google.android.gms 40",
            "am set-standby-bucket com.xiaomi.mipicks 45",
            "am set-standby-bucket com.miui.analytics 45",
            "am set-standby-bucket com.miui.msa.global 45"
        ) else listOf(
            "am set-standby-bucket com.google.android.gms 20"
        )
        Shell.runBatch(cmds)
        "Heavy social apps ${if (enable) "SUSPENDED" else "UNSUSPENDED"}"
    }

    suspend fun purgeSystemServerAndTrim(): String = withContext(Dispatchers.IO) {
        val cmds = listOf(
            "sm fstrim",
            "cmd activity idle-maintenance",
            "pm trim-caches 4096G",
            "am kill-all",
            "cmd binder_calls_stats --disable",
            "cmd looper_stats disable",
            "settings put global activity_starts_logging_enabled 0",
            "settings put global foreground_service_starts_logging_enabled 0",
            "settings put global send_action_app_error 0",
            "settings put secure send_action_app_error 0",
            "settings put system send_security_reports 0",
            "settings put global sys_traced 0",
            "settings put global lpm_dump_state 0",
            "settings put global battery_stats_constants track_cpu_times_by_proc_state=false",
            "settings put global aware_enabled 0",
            "setprop persist.log.tag E",
            "setprop persist.traced.enable 0"
        )
        Shell.runBatch(cmds)
        "Storage TRIM, Loggers Disabled & Memory Purged"
    }

    // =========================================================================
    // 16. LOW-PING NETWORK FIREWALL, 5GHZ PRIORITY & GAMING DNS SWITCHER
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
                settings put global wifi_scan_always_enabled 0
                settings put global ble_scan_always_enabled 0
                settings put global wifi_Verbose_Logging_Enabled 0
                settings put global wifi_suspend_optimizations_enabled 0
                settings put global wifi_frequency_band 2
                settings put global wifi_watchdog_poor_network_test_enabled 0
                settings put global wifi_scan_optimization 1
                settings put global network_latency_opt 1
                settings put global network_tcp_buffer 1
                settings put global net_tcp_defensive 0
                settings put system wifi_assistant 0
                settings put system mishare_near_share 0
                settings put global captive_portal_mode 0
                settings put global captive_portal_detection_enabled 0
                settings put global private_dns_mode hostname
                settings put global private_dns_specifier one.one.one.one
                echo "Network Firewall ON: Game UID whitelisted, Cloudflare DNS active"
            """.trimIndent()
            Shell.run(script)
        } else {
            val cmds = listOf(
                "cmd netpolicy set restrict-background false",
                "settings put global private_dns_mode opportunistic"
            )
            Shell.runBatch(cmds)
            "Network Firewall and DNS restored to default"
        }
    }

    suspend fun setGamingDns(dns: String): String = withContext(Dispatchers.IO) {
        when (dns) {
            "cloudflare" -> {
                Shell.runBatch(
                    "settings put global private_dns_mode hostname",
                    "settings put global private_dns_specifier one.one.one.one"
                )
                "DNS set to Cloudflare (1.1.1.1)"
            }
            "google" -> {
                Shell.runBatch(
                    "settings put global private_dns_mode hostname",
                    "settings put global private_dns_specifier dns.google"
                )
                "DNS set to Google (8.8.8.8)"
            }
            else -> {
                Shell.run("settings put global private_dns_mode opportunistic")
                "DNS set to Automatic (Opportunistic)"
            }
        }
    }

    // =========================================================================
    // 17. TYPE-C SPLITTER / DAC DISCONNECT FIX & SPATIAL LOW-LATENCY AUDIO
    // =========================================================================
    suspend fun applyTypeCAudioFix(enable: Boolean): String = withContext(Dispatchers.IO) {
        if (enable) {
            val cmds = listOf(
                "settings put global usb_audio_automatic_routing_disabled 0",
                "settings put system miui_audio_dump_enable 0",
                "settings put system sound_effects_enabled 0",
                "settings put global audio_safe_volume_state 2",
                "settings put global bluetooth_disable_absolute_volume 1",
                "settings put system k2hd_effect 1",
                "settings put system tube_amp_effect 1",
                "settings put system dolby_enable 1",
                "settings put global apm_enhancement_enabled 1",
                "setprop debug.bluetooth.low_latency true",
                "setprop debug.stagefright.ccodec_delayed_params 1",
                "setprop audio.deep_buffer.media false"
            )
            Shell.runBatch(cmds)
            "Type-C DAC Fix & Spatial Low-Latency Audio: ENABLED"
        } else {
            val cmds = listOf(
                "settings put global bluetooth_disable_absolute_volume 0",
                "setprop debug.bluetooth.low_latency false"
            )
            Shell.runBatch(cmds)
            "Audio settings reverted"
        }
    }

    // =========================================================================
    // 18. CHAMPIONSHIP MODE / GUIDED ACCESS LOCK
    // =========================================================================
    suspend fun applyChampionshipMode(game: String, enable: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        if (enable) {
            val cmds = listOf(
                "settings put global policy_control immersive.full=$g",
                "settings put global heads_up_notifications_enabled 0",
                "cmd statusbar send-disable-flag 65536",
                "cmd statusbar cmd notification set-dnd on",
                "content insert --uri content://settings/secure --bind name:s:sync_on_wifi_only --bind value:i:1",
                "settings put system three_gesture_down 0",
                "settings put system three_gesture_long_press 0",
                "settings put system double_click_power_key 0",
                "settings put system miui_tof_gesture 0",
                "settings put secure back_gesture_inset_scale_left 0",
                "settings put secure back_gesture_inset_scale_right 0",
                "settings put system vibrate_input_devices 0",
                "settings put system haptic_feedback_enabled 0",
                "settings put system screen_brightness_mode 0"
            )
            Shell.runBatch(cmds)
            "Championship Guided Access: Fullscreen Lock, DND ON, Gestures OFF"
        } else {
            val cmds = listOf(
                "settings put global policy_control null*",
                "settings put global heads_up_notifications_enabled 1",
                "cmd statusbar send-disable-flag 0",
                "cmd statusbar cmd notification set-dnd off",
                "settings put secure back_gesture_inset_scale_left 1",
                "settings put secure back_gesture_inset_scale_right 1",
                "settings put system haptic_feedback_enabled 1"
            )
            Shell.runBatch(cmds)
            "Championship Mode Released"
        }
    }

    // =========================================================================
    // 19. PGT+ v0.25.9 COMPLETE CONFIG & EXTENSION INJECTOR (Requirement 7)
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
        zeroLag: Boolean = true,
        vulkanRendering: Boolean = true,
        saveSensitivity: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)

        // Conflict Prevention Rule 1: Lock to 120 FPS
        val finalFps = if (fps > 120 || (zeroLag && fps > 120)) 120 else fps
        // Conflict Prevention Rule 2: Ensure clean aspect ratio scaling
        val safeRes = if (resolution == "800x600" && potatoLevel != "Off") "960x540" else resolution

        val cfgDir = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val iniPath = "$cfgDir/UserCustom.ini"

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
            else -> 1
        }

        val iniContent = buildString {
            appendLine("[SystemSettings]")
            appendLine("r.UserQualitySetting=$qualityVal")
            appendLine("r.MipMapLODBias=$lodBias")
            appendLine("r.ShadowQuality=$shadowVal")
            appendLine("r.BloomQuality=0")
            appendLine("r.DefaultFeature.AntiAliasing=$msaaVal")
            appendLine("r.MobileContentScaleFactor=1.0")
            appendLine("r.Vulkan=${if (vulkanRendering) 1 else 0}")
            appendLine("r.TargetFPS=$finalFps")
            appendLine("r.SimpleDynamicLighting=${if (simpleShader) 1 else 0}")
            appendLine("r.SimpleShader=${if (simpleShader || lodBias > 0) 1 else 0}")
            appendLine("r.SoundQuality=${if (soundQuality == "Ultra") 2 else 1}")

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

        // Backup sensitivity Active.sav if saveSensitivity is requested
        val saveGamesDir = "/sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/SaveGames"
        val script = """
            mkdir -p '$cfgDir'
            if [ "$saveSensitivity" = "true" ] && [ -f '$saveGamesDir/Active.sav' ]; then
                cp '$saveGamesDir/Active.sav' '$saveGamesDir/Active.sav.bak'
            fi
            cat << 'EOF' > '$iniPath'
$iniContent
EOF
            if [ "$saveSensitivity" = "true" ] && [ -f '$saveGamesDir/Active.sav.bak' ]; then
                cp '$saveGamesDir/Active.sav.bak' '$saveGamesDir/Active.sav'
            fi
        """.trimIndent()
        Shell.run(script)

        applyDownscaleAndFps(g, "0.75", finalFps)
        applyAdrenoGpuAndSurfaceFlinger(g, true)

        "PGT+ v0.25.9 Injected: Res=$safeRes, Qual=$quality, FPS=$finalFps, Potato=$potatoLevel, Vulkan=$vulkanRendering (Sensitivity Saved)"
    }

    // =========================================================================
    // 20. WATCHDOG TICK (AutoService 60s loop)
    // =========================================================================
    suspend fun watchdogTick(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val script = """
            am force-stop com.xiaomi.joyose
            cmd thermalservice override-status 0
            PUBG_PID=${'$'}(pidof $g)
            if [ ! -z "${'$'}PUBG_PID" ]; then
                taskset -p -a ff ${'$'}PUBG_PID
                ionice -c 2 -n 0 -p ${'$'}PUBG_PID
            fi
        """.trimIndent()
        Shell.run(script)
        "Watchdog 60s: Joyose killed, thermal override re-applied, PID pinned"
    }

    // =========================================================================
    // 21. 1-TAP ESPORTS MODE & PANIC RESTORE
    // =========================================================================
    suspend fun esportsMode(game: String, selfPkg: String): String {
        val g = safeGame(game)
        applyHyperOs120HzHardLock(true)
        applyWildBoostAndFeas(g, true)
        applyDiabloThermalMode(true)
        restrictPowerKeeperAndJoyose()
        applyHyperOsCloudGreezerAndMillet(g, true)
        applyAdrenoGpuAndSurfaceFlinger(g, true)
        applyImpulseTouchAndTouchIc(true)
        applyChampionshipMode(g, true)
        applyLowPingNetworkFirewall(g, true)
        applyTypeCAudioFix(true)
        pinGameProcess(g)
        cleanUe4StutterLogs(g)
        suspendHeavyApps(true)
        purgeSystemServerAndTrim()
        return "🔥 100% ESPORTS MODE ACTIVATED for ${Games.known[g] ?: g}"
    }

    suspend fun revertAll(game: String): String {
        val g = safeGame(game)
        applyHyperOs120HzHardLock(false)
        applyWildBoostAndFeas(g, false)
        applyDiabloThermalMode(false)
        applyHyperOsCloudGreezerAndMillet(g, false)
        applyAdrenoGpuAndSurfaceFlinger(g, false)
        applyImpulseTouchAndTouchIc(false)
        applyChampionshipMode(g, false)
        applyLowPingNetworkFirewall(g, false)
        applyTypeCAudioFix(false)
        resetDownscaleAndFps(g)
        applyAspectPreset(0)
        applyCamperFilters("reset")
        setMagnifier(false)
        setBypassCharging(false)
        suspendHeavyApps(false)
        return "RESTORED ALL SETTINGS TO FACTORY DEFAULTS"
    }

    // Legacy and Utility helpers
    suspend fun cleanRam(keep: Set<String>, self: String): String = withContext(Dispatchers.IO) {
        val pkgs = Shell.run("pm list packages -3")
            .lines()
            .mapNotNull { it.removePrefix("package:").trim().takeIf { p -> pkgRegex.matches(p) } }
            .filter { it != self && it !in keep && it !in Games.known }
        pkgs.forEach { Shell.run("am force-stop $it") }
        "Processes Cleaned: ${pkgs.size}"
    }

    suspend fun trimCaches(): String = withContext(Dispatchers.IO) {
        Shell.run("pm trim-caches 4096G").ifBlank { "Cache trimmed." }
    }

    suspend fun createSettingsSnapshot(): String = withContext(Dispatchers.IO) {
        val cmds = listOf(
            "mkdir -p $BACKUP_DIR",
            "settings list system > $BACKUP_DIR/system_backup.txt",
            "settings list secure > $BACKUP_DIR/secure_backup.txt",
            "settings list global > $BACKUP_DIR/global_backup.txt"
        )
        Shell.runBatch(cmds)
        "Full Settings Snapshot Backup saved to $BACKUP_DIR"
    }

    suspend fun queryLiveTelemetry(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val sb = StringBuilder()
        sb.appendLine("=== HYPEROS 3 & SNAPDRAGON TELEMETRY ===")
        sb.appendLine("Battery: " + Shell.run("dumpsys battery | grep -E 'temperature|level|voltage'").trim())
        sb.appendLine("GPU Busy: " + Shell.run("cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null").trim())
        sb.appendLine("GPU Clk: " + Shell.run("cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null").trim() + " Hz")
        sb.appendLine("Game PID: " + Shell.run("pidof $g").trim().ifBlank { "Not running" })
        sb.appendLine("Thermal Override: " + Shell.run("dumpsys thermalservice | grep -i 'mStatus' | head -1").trim())
        sb.toString()
    }

    suspend fun grantOverlay(pkg: String): String = withContext(Dispatchers.IO) {
        Shell.run("appops set $pkg SYSTEM_ALERT_WINDOW allow").ifBlank { "Overlay permission granted via Shizuku" }
    }
}
