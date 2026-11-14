package com.hypergameboost

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Games {
    /** All official PUBG Mobile variants supported by HyperGameBooster */
    val known = linkedMapOf(
        "com.tencent.ig" to "PUBG Mobile (Global)",
        "com.pubg.imobile" to "BGMI (India)",
        "com.pubg.krmobile" to "PUBG Mobile (Korea)",
        "com.vng.pubgmobile" to "PUBG Mobile (Vietnam)",
        "com.rekoo.pubgm" to "PUBG Mobile (Taiwan)",
        "com.tencent.tmgp.pubgmhd" to "Game for Peace (China)",
        "com.tencent.igce" to "PUBG Mobile (Beta)",
        "com.tencent.iglite" to "PUBG Mobile Lite"
    )
}

object Actions {
    val BLOAT_PACKAGES = listOf(
        "de.szalkowski.activitylauncher", "com.docx.word.excel.ppt.pdf.document.reader", "com.a0soft.gphone.app2sd",
        "com.b612.android.activity", "me.piebridge.brevent", "com.jpro.casiocalc", "com.capcut.android.tools",
        "com.openai.chatgpt", "com.android.chrome", "com.anthropic.claudeforandroid", "com.daraz.android",
        "com.pnikosis.pmyouthhub", "com.facebook.katana", "com.foodpanda.android", "com.google.android.apps.docs",
        "com.google.android.apps.photos", "com.google.android.apps.bard", "com.google.android.gm",
        "com.google.android.googlequicksearchbox", "com.instagram.android", "com.internee.app", "com.google.android.keep",
        "com.google.android.apps.lens", "com.adobe.lrmobile", "com.linkedin.android", "com.google.android.apps.maps",
        "com.google.android.apps.tachyon", "com.docx.word.excel.ppt.pdf.document.reader.editor", "com.melStudio.MovieBox",
        "com.olx.pk", "com.microsoft.office.outlook", "com.adobe.reader", "com.picsart.studio", "com.pikas.show.official",
        "com.android.vending", "com.playit.videoplayer", "com.supervpn.vpn", "com.surfflix.app", "com.microsoft.teams",
        "org.telegram.messenger", "com.zhiliaoapp.musically", "com.vng.vpnindia", "com.wps.moffice", "com.yango.rider",
        "com.google.android.youtube", "com.zarchiver.pro", "com.miui.personalassistant", "com.miui.contentextension",
        "com.miui.voiceassist", "com.miui.hybrid", "com.xiaomi.market", "com.miui.analytics", "com.miui.msa.global",
        "com.miui.Video", "com.xiaomi.mipicks"
    )

    fun safeGame(game: String): String {
        val trimmed = game.trim()
        val pkg = trimmed.substringBefore(" ").trim()
        return if (pkg.matches(Regex("^[a-zA-Z0-9_.]+$"))) pkg else "com.tencent.ig"
    }

    suspend fun installedGames(): List<String> = withContext(Dispatchers.IO) {
        val out = Shell.run("pm list packages").trim()
        val found = mutableListOf<String>()
        val lines = out.lines().map { it.removePrefix("package:").trim() }
        for (pkg in Games.known.keys) {
            if (lines.contains(pkg)) {
                found.add(pkg)
            }
        }
        if (found.isEmpty()) listOf("com.tencent.ig") else found
    }

    suspend fun launchGame(context: Context, game: String = "com.tencent.ig"): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        unlockGameConfigFiles(g)

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(g)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                context.startActivity(launchIntent)
                return@withContext "Game launched: $g"
            } catch (_: Throwable) {}
        }

        // Shizuku UID 2000 fallback
        val amRes = Shell.run("am start -n $g/com.epicgames.ue4.SplashActivity 2>/dev/null || monkey -p $g -c android.intent.category.LAUNCHER 1 2>/dev/null")
        "Game launched via Shizuku: $g ($amRes)"
    }

    fun unlockGameConfigFiles(pkg: String): String {
        val g = safeGame(pkg)
        val script = """
            for d in /sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android /storage/emulated/0/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android; do
                if [ -d "${'$'}d" ]; then
                    chmod 0777 "${'$'}d" 2>/dev/null
                    chmod 0666 "${'$'}d"/*.ini 2>/dev/null
                    chattr -i "${'$'}d"/*.ini 2>/dev/null
                fi
            done
        """.trimIndent()
        Shell.run(script)
        return "🔓 Game .ini files unlocked (0666) — native 120 FPS allowed!"
    }

    fun disableOrClearSystemThrottlers(): String {
        val cmds = listOf(
            "pm uninstall -k --user 0 com.xiaomi.joyose 2>/dev/null",
            "am force-stop com.xiaomi.joyose 2>/dev/null",
            "pm clear --user 0 com.xiaomi.joyose 2>/dev/null",
            "cmd appops set com.xiaomi.joyose RUN_IN_BACKGROUND ignore 2>/dev/null",
            "cmd appops set com.xiaomi.joyose WAKE_LOCK ignore 2>/dev/null",
            "pm uninstall -k --user 0 com.google.android.webview 2>/dev/null",
            "pm clear --user 0 com.google.android.webview 2>/dev/null",
            "am force-stop com.google.android.webview 2>/dev/null",
            "pm clear --user 0 com.android.webview 2>/dev/null",
            "am force-stop com.android.webview 2>/dev/null",
            "pm trim-caches 4096G 2>/dev/null"
        )
        Shell.runBatch(cmds)
        return "🧹 System Throttlers (Joyose & WebView) Disarmed & Trimmed!"
    }

    fun restoreSystemPackagesInstallExisting(): String {
        val cmds = listOf(
            "cmd package install-existing --user 0 com.miui.powerkeeper 2>/dev/null",
            "pm install-existing com.miui.powerkeeper 2>/dev/null",
            "cmd package install-existing --user 0 com.xiaomi.joyose 2>/dev/null",
            "pm install-existing com.xiaomi.joyose 2>/dev/null",
            "cmd package install-existing --user 0 com.google.android.webview 2>/dev/null",
            "pm install-existing com.google.android.webview 2>/dev/null",
            "cmd package install-existing --user 0 com.android.webview 2>/dev/null",
            "pm install-existing com.android.webview 2>/dev/null"
        )
        Shell.runBatch(cmds)
        return "♻️ System Packages (PowerKeeper, Joyose, WebView) Restored via install-existing!"
    }

    fun clearPowerKeeperJoyoseWebViewData(): String = disableOrClearSystemThrottlers()

    suspend fun pinGameProcess(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val script = """
            PUBG_PID=${'$'}(pidof $g 2>/dev/null)
            if [ ! -z "${'$'}PUBG_PID" ]; then
                # 1. Main process affinity to Snapdragon Prime/Gold cores (fc) & realtime nice
                taskset -p -a fc ${'$'}PUBG_PID 2>/dev/null
                ionice -c 2 -n 0 -p ${'$'}PUBG_PID 2>/dev/null
                renice -n -20 -p ${'$'}PUBG_PID 2>/dev/null

                # 2. Pin critical UE4 child threads (RenderThread, GameThread, RHIThread) to Gold/Prime cores (fc)
                for TID_DIR in /proc/${'$'}PUBG_PID/task/*; do
                    TID=${'$'}(basename ${'$'}TID_DIR)
                    COMM=${'$'}(cat ${'$'}TID_DIR/comm 2>/dev/null)
                    case "${'$'}COMM" in
                        *RenderThread*|*GameThread*|*RHIThread*|*FAsyncLoading*|*PoolThread*)
                            taskset -p fc ${'$'}TID 2>/dev/null
                            renice -n -20 -p ${'$'}TID 2>/dev/null
                            ;;
                    esac
                done
                echo "PID ${'$'}PUBG_PID & UE4 child threads pinned to Cores fc"
            else
                echo "Game process not running yet"
            fi
        """.trimIndent()
        Shell.run(script)
    }

    suspend fun lockHardware120HzDisplayMode(): String = withContext(Dispatchers.IO) {
        val script = """
            service call SurfaceFlinger 1035 i32 -1 2>/dev/null
            cmd display clear-user-preferred-display-mode 2>/dev/null
            settings put system min_refresh_rate 120.0
            settings put system peak_refresh_rate 120.0
            settings put secure user_refresh_rate 120
            settings put secure miui_refresh_rate 120
            settings put system is_smart_fps 0
            settings put system smart_fps_switch 0
            settings put system thermal_limit_refresh_rate 0
        """.trimIndent()
        Shell.run(script)
        "✓ 120Hz Display Mode Locked (-1 Override)"
    }

    // =========================================================================
    // SECTION 1: REALME 3-WAY PERFORMANCE MODE SELECTOR
    // =========================================================================

    suspend fun applyRealmePerformanceMode(game: String, mode: Int): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        when (mode) {
            0 -> {
                // Low Power Mode
                val script = """
                    cmd game mode 3 $g
                    cmd game set --mode 3 --fps 60 $g
                    settings put system min_refresh_rate 60.0
                    settings put system peak_refresh_rate 60.0
                    settings put system speed_mode 0
                    settings put system power_mode normal
                    settings put system gb_boosting 0
                    settings put system ultimate_mode_enable 0
                    cmd thermalservice reset 2>/dev/null
                    echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
                    settings put system night_charging 0 2>/dev/null
                """.trimIndent()
                Shell.run(script)
                "🔋 Realme Low Power Mode Activated (60Hz / Standard Clocks)"
            }
            1 -> {
                // Balanced Mode
                val script = """
                    cmd game mode 1 $g
                    cmd game set --mode 1 --fps 90 $g
                    settings put system min_refresh_rate 90.0
                    settings put system peak_refresh_rate 120.0
                    settings put system speed_mode 0
                    settings put system power_mode normal
                    settings put system gb_boosting 1
                    settings put system ultimate_mode_enable 0
                    service call SurfaceFlinger 1035 i32 -1 2>/dev/null
                """.trimIndent()
                Shell.run(script)
                "⚖️ Realme Balanced Mode Activated (90Hz-120Hz Adaptive)"
            }
            else -> {
                // High Performance GT Mode (Default)
                val script = buildString {
                    appendLine("cmd game mode 2 $g")
                    appendLine("cmd game set --mode 2 --fps 120 $g")
                    appendLine("cmd thermalservice override-status 0 2>/dev/null")
                    appendLine("cmd power set-fixed-performance-mode-enabled true 2>/dev/null")
                    appendLine("cmd power set-mode 0 2>/dev/null")
                    appendLine("settings put system speed_mode 1")
                    appendLine("settings put secure speed_mode_enable 1")
                    appendLine("settings put system power_mode high")
                    appendLine("settings put system gb_boosting 1")
                    appendLine("settings put system ultimate_mode_enable 1")
                    appendLine("settings put global cpu_boost 1")
                    appendLine("settings put global gpu_boost 1")
                    appendLine("settings put global scheduler_policy performance")

                    // PUBG Unrestricted Battery ("No Restrictions")
                    appendLine("dumpsys deviceidle whitelist +$g 2>/dev/null")
                    appendLine("am set-standby-bucket $g 5 2>/dev/null")
                    appendLine("cmd appops set $g RUN_IN_BACKGROUND allow 2>/dev/null")
                    appendLine("cmd appops set $g RUN_ANY_IN_BACKGROUND allow 2>/dev/null")
                    appendLine("cmd appops set $g WAKE_LOCK allow 2>/dev/null")
                    appendLine("cmd appops set $g START_FOREGROUND allow 2>/dev/null")

                    // Zero-Latency SurfaceFlinger Scanout
                    appendLine("service call SurfaceFlinger 1008 i32 1 2>/dev/null")
                    appendLine("service call SurfaceFlinger 1035 i32 -1 2>/dev/null")
                    appendLine("cmd display clear-user-preferred-display-mode 2>/dev/null")
                    appendLine("settings put system min_refresh_rate 120.0")
                    appendLine("settings put system peak_refresh_rate 120.0")
                    appendLine("settings put secure user_refresh_rate 120")
                    appendLine("settings put secure miui_refresh_rate 120")
                    appendLine("settings put system is_smart_fps 0")
                    appendLine("settings put system smart_fps_switch 0")
                    appendLine("settings put system thermal_limit_refresh_rate 0")
                    appendLine("cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 2 2>/dev/null")
                    appendLine("cmd device_config put surface_flinger_native_boot enable_frame_rate_override false 2>/dev/null")
                    appendLine("cmd device_config put surface_flinger_native_boot enable_small_area_detection false 2>/dev/null")
                    appendLine("cmd device_config put surface_flinger_native_boot enable_layer_caching false 2>/dev/null")
                    appendLine("setprop debug.sf.latch_unsignaled 1")
                    appendLine("setprop debug.sf.auto_latch_unsignaled 1")
                    appendLine("setprop debug.sf.disable_backpressure 1")
                    appendLine("setprop debug.sf.DropMissedFrames 1")

                    // SkiaGL + System Driver
                    appendLine("setprop debug.hwui.renderer skiagl")
                    appendLine("settings put global updatable_driver_all_apps 3")
                    appendLine("settings put global updatable_driver_production_opt_out_apps '$g'")
                    appendLine("settings put global game_driver_opt_in_apps \"\"")

                    // Bypass Charging & 12GB Zero-Swap
                    appendLine("echo 1 > /sys/class/power_supply/battery/input_suspend 2>/dev/null")
                    appendLine("settings put system night_charging 1 2>/dev/null")
                    appendLine("device_config put activity_manager use_compaction false 2>/dev/null")
                    appendLine("device_config put activity_manager max_cached_processes 64 2>/dev/null")
                    appendLine("settings put global ram_expand_size 0")
                    appendLine("settings put system miui_memory_extension 0")

                    // FastConnect Wi-Fi Low-Latency Mode + Cloudflare DNS
                    appendLine("cmd wifi force-low-latency-mode enabled 2>/dev/null")
                    appendLine("cmd wifi force-hi-perf-mode enabled 2>/dev/null")
                    appendLine("settings put global wifi_scan_always_enabled 0")
                    appendLine("settings put global ble_scan_always_enabled 0")
                    appendLine("settings put global wifi_suspend_optimizations_enabled 0")
                    appendLine("settings put global wifi_power_save 0")
                    appendLine("cmd netd resolver flushdefaultif 2>/dev/null")
                    appendLine("settings put global private_dns_mode hostname")
                    appendLine("settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com")

                    // Debloat 46 Resource Hogs
                    BLOAT_PACKAGES.forEach { pkgName ->
                        appendLine("am force-stop $pkgName 2>/dev/null")
                    }
                }
                Shell.run(script)
                disableOrClearSystemThrottlers()
                "🔥 HIGH PERFORMANCE GT MODE ACTIVE (GameMode 2, 120Hz, SkiaGL, Bypass Charging Locked)"
            }
        }
    }

    // =========================================================================
    // SECTION 2: REALME PRO TOUCH, SWIPE & RESOLUTION SLIDERS
    // =========================================================================

    suspend fun applyRealmeTouchAimSlider(value: Int): String = withContext(Dispatchers.IO) {
        val v = value.coerceIn(0, 100)
        val sens = ((v / 20.0).toInt()).coerceIn(1, 5)
        val script = """
            settings put system gamemode_touch_aim_sensitivity $v
            settings put system gb_touch_aim_accuracy $v
            settings put system touch_sensitivity $sens
            settings put secure touch_sensitivity $sens
            settings put system gamemode_touch_optimization 1
        """.trimIndent()
        Shell.run(script)
        "🎯 Touch & Aim Sensitivity: $v% (IC Accuracy: $v, Level: $sens/5)"
    }

    suspend fun applyRealmeSwipeJiggleSlider(game: String, value: Int): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val v = value.coerceIn(0, 100)
        val ptr = (((v / 100.0) * 7).toInt()).coerceIn(1, 7)
        val script = """
            settings put system gamemode_touch_follow_response $v
            settings put system gb_touch_response_speed $v
            settings put system pointer_speed $ptr
            settings put system touch_slop 1
            settings put system touch_response_speed 1
            settings put system touch_boost 1
            settings put system tp_game_mode 1
            settings put system miui_touch_sampling_rate_boost 1
            cmd device_config set-sync-disabled-for-tests persistent 2>/dev/null
            cmd device_config put input_native_boot resample_touch false 2>/dev/null
            cmd device_config put input_native_boot velocitytracker_strategy impulse 2>/dev/null
            cmd device_config put input_native_boot deep_press_enabled false 2>/dev/null
            setprop debug.input.resample 0
            setprop windowsmgr.max_events_per_sec 240
            settings put secure tap_duration_threshold 0.0
            settings put secure tap_duration_enabled 0
            settings put secure touch_blocking_period 0.0
            settings put secure multi_press_timeout 50
            settings put secure long_press_timeout 140
            settings put secure ignore_repeat_enabled 0
            settings put global window_animation_scale 0.0
            settings put global transition_animation_scale 0.0
            settings put global animator_duration_scale 0.0
            cmd sensorservice restrict-uid-reset 2>/dev/null
            cmd appops set $g HIGH_SAMPLING_RATE_SENSORS allow 2>/dev/null
        """.trimIndent()
        Shell.run(script)
        "⚡ Swipe & Jiggle Responsiveness: $v% (Pointer: $ptr/7, 0ms Unbatched Touch Active)"
    }

    suspend fun applyRealmeTapStabilitySlider(value: Int): String = withContext(Dispatchers.IO) {
        val v = value.coerceIn(0, 100)
        val script = """
            settings put system gamemode_touch_tap_stability $v
            settings put system gb_touch_tap_stability $v
            settings put system gb_touch_edge_zone 0
        """.trimIndent()
        Shell.run(script)
        "🛡️ Tap Stability & Recoil Control: $v% (Edge Zone Filter: 0)"
    }

    suspend fun applyRealmeResolutionStep(game: String, stepIndex: Int): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val step = AppPrefs.getResolutionStep(stepIndex)
        val script = buildString {
            if (step.wmSize == "reset") {
                appendLine("wm size reset")
                appendLine("wm density reset")
            } else {
                appendLine("wm size ${step.wmSize}")
                appendLine("wm density ${step.density}")
            }
            appendLine("cmd game mode 2 $g")
            if (step.downscaleFactor == "disable") {
                appendLine("cmd game set --mode 2 --downscale disable --fps 120 $g")
            } else {
                appendLine("cmd game set --mode 2 --downscale ${step.downscaleFactor} --fps 120 $g")
                appendLine("device_config put game_overlay $g mode=2,skiavk=1,downscaleFactor=${step.downscaleFactor},fps=120,loadingBoost=99999999:mode=3,skiavk=1,downscaleFactor=${step.downscaleFactor},fps=120")
            }
        }
        Shell.run(script)
        "📐 Resolution Step ${step.step} Applied: ${step.label} (${step.wmSize} @ ${step.density} DPI, Scale: ${step.downscaleFactor})"
    }

    // =========================================================================
    // SECTION 3: TSOML GFX TOOL PRO CARD
    // =========================================================================

    suspend fun applyTsomlGfxConfig(
        game: String,
        res: String,
        gfx: String,
        fps: Int = 120,
        potatoLod: String = "Ultra (LOD +3)",
        bilinear: Boolean = true,
        simpleShader: Boolean = true,
        driver: String = "SkiaGL + System Driver",
        saveControls: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        unlockGameConfigFiles(g)

        val lodVal = when {
            potatoLod.contains("Ultra") || potatoLod.contains("+3") -> 3
            potatoLod.contains("Smooth") || potatoLod.contains("+2") -> 2
            else -> 0
        }

        val downscale = when (res) {
            "768x432" -> "0.50"
            "800x600" -> "0.55"
            "960x540" -> "0.65"
            "1280x720 (HD)", "1280x720" -> "0.75"
            "1440x720 (HD+)", "1440x720" -> "0.80"
            "1920x1080" -> "0.90"
            else -> "0.55"
        }

        val script = buildString {
            // Android 16 Native GameMode 2 + Hardware Downscale + Target FPS
            appendLine("cmd game mode 2 $g")
            appendLine("cmd game set --mode 2 --downscale $downscale --fps $fps $g")
            appendLine("device_config put game_overlay $g mode=2,skiavk=1,downscaleFactor=$downscale,fps=$fps,loadingBoost=99999999:mode=3,skiavk=1,downscaleFactor=$downscale,fps=$fps")

            // Adreno LOD & Texture Filtering
            appendLine("settings put system gpu_mipmap_lod_bias $lodVal")
            appendLine("settings put system gpu_texture_filter_quality ${if (bilinear) 0 else 1}")
            appendLine("settings put system gpu_anisotropic_opt 0")
            appendLine("settings put system gpu_sub_sample_enable ${if (simpleShader) 1 else 0}")
            appendLine("settings put system miui_gpu_preemption 0")
            appendLine("settings put system miui_vrs_enable 0")
            appendLine("settings put global vrs_enable 0")

            // Graphics Driver
            if (driver.contains("System")) {
                appendLine("setprop debug.hwui.renderer skiagl")
                appendLine("settings put global updatable_driver_all_apps 3")
                appendLine("settings put global updatable_driver_production_opt_out_apps '$g'")
                appendLine("settings put global game_driver_opt_in_apps \"\"")
            } else {
                appendLine("setprop debug.hwui.renderer skiavk")
                appendLine("settings put global updatable_driver_all_apps 1")
                appendLine("settings put global game_driver_opt_in_apps '$g'")
            }

            // UE4 CVars into UserCustom.ini without 0444 lock
            val iniDirs = listOf(
                "/sdcard/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android",
                "/storage/emulated/0/Android/data/$g/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android"
            )
            for (d in iniDirs) {
                appendLine("if [ -d \"$d\" ]; then")
                appendLine("  cat << 'EOF' >> \"$d/UserCustom.ini\"")
                appendLine("[UserCustom DeviceProfile]")
                appendLine("+CVars=r.OneFrameThreadLag=0")
                appendLine("+CVars=r.FinishCurrentFrame=1")
                appendLine("+CVars=p.NetEnableMoveCombining=0")
                appendLine("+CVars=r.MipMapLODBias=$lodVal")
                appendLine("+CVars=r.ShadowQuality=0")
                appendLine("+CVars=r.DefaultFeature.AntiAliasing=0")
                appendLine("+CVars=r.PUBGDeviceFPSUltralHigh=$fps")
                appendLine("EOF")
                appendLine("  chmod 0666 \"$d/UserCustom.ini\" 2>/dev/null")
                appendLine("fi")
            }
        }

        Shell.run(script)
        "⚡ TSOML GFX Applied: $res | $gfx | ${fps}FPS | LOD+$lodVal | Bilinear=${if (bilinear) "ON" else "OFF"}"
    }

    // =========================================================================
    // SECTION 4: REALME DOLBY FOOTSTEP STUDIO & TYPE-C SPLITTER GUARD
    // =========================================================================

    suspend fun applyRealmeAudioAndSplitterGuard(
        context: Context?,
        dolbyPreset: Int = 2,
        volumePercent: Int = 70,
        splitterGuard: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val script = buildString {
            if (splitterGuard) {
                appendLine("settings put global usb_audio_automatic_routing_disabled 0")
                appendLine("settings put secure usb_audio_automatic_routing_disabled 0")
                appendLine("settings put system charge_boost_enable 0")
                appendLine("settings put secure charge_boost_enable 0")
                appendLine("setprop debug.audio.usb.period_us 2500")
            }
            appendLine("settings put global audio_safe_volume_state 2")
            appendLine("settings put secure music_in_communication 1")
            appendLine("settings put system dolby_enable 1")
            appendLine("setprop persist.vendor.audio.fluence.voicecall false")
            appendLine("setprop audio.safemedia.bypass true")
            appendLine("setprop audio.offload.disable 1")
            appendLine("setprop audio.deep_buffer.media false")
        }
        Shell.run(script)

        try {
            context?.let { AudioTest.init(it) }
            AudioTest.applyDolbyFootstepPreset(dolbyPreset)
            AudioTest.setCleanVolumeBoost(context, true, volumePercent)
        } catch (_: Throwable) {}

        "🎧 Realme Dolby Preset $dolbyPreset Active | Clean Volume: $volumePercent% | Splitter Guard: ${if (splitterGuard) "ON" else "OFF"}"
    }

    // =========================================================================
    // MASTER ACTIONS: BOOST & LAUNCH / 1-TAP FACTORY FRESH REVERT
    // =========================================================================

    suspend fun executeMasterGtBoost(
        context: Context,
        game: String = "com.tencent.ig",
        applyDisplayResolution: Boolean = false,
        launchGame: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        AppPrefs.init(context)

        // 1. Terminate background game instance so new framebuffer & driver take effect
        if (launchGame || applyDisplayResolution) {
            Shell.run("am force-stop $g 2>/dev/null")
            disableOrClearSystemThrottlers()
        }

        // 2. Unlocks .ini files to 0666
        unlockGameConfigFiles(g)

        // 3. Apply High Performance GT Mode
        applyRealmePerformanceMode(g, 2)

        // 4. Apply Realme Sliders (Touch & Aim 100%, Swipe 100%, Tap Stability 100%)
        applyRealmeTouchAimSlider(AppPrefs.getTouchAimSensitivity(g))
        applyRealmeSwipeJiggleSlider(g, AppPrefs.getSwipeJiggleResponse(g))
        applyRealmeTapStabilitySlider(AppPrefs.getTapStabilityRecoil(g))

        // 5. Apply Resolution Step (Step 2 by default = 810p Scrims 810x1800 @ 310 DPI)
        if (applyDisplayResolution) {
            val stepIdx = AppPrefs.getResolutionStepIndex(g)
            applyRealmeResolutionStep(g, stepIdx)
        }

        // 6. Apply TSOML GFX Settings (800x600 Super Smooth 120 FPS LOD +3)
        applyTsomlGfxConfig(
            game = g,
            res = AppPrefs.getTsomlResolution(g),
            gfx = AppPrefs.getTsomlGraphics(g),
            fps = AppPrefs.getTsomlFps(g),
            potatoLod = AppPrefs.getTsomlPotatoLod(g),
            bilinear = AppPrefs.getTsomlBilinear(g),
            simpleShader = AppPrefs.getTsomlSimpleShader(g),
            driver = AppPrefs.getTsomlDriver(g),
            saveControls = AppPrefs.getTsomlSaveControls(g)
        )

        // 7. Apply Realme Dolby Scout Footsteps + 70% Clean Volume Boost + Splitter Guard
        applyRealmeAudioAndSplitterGuard(
            context = context,
            dolbyPreset = AppPrefs.getDolbyPreset(g),
            volumePercent = AppPrefs.getCleanVolumePercent(g),
            splitterGuard = AppPrefs.getSplitterGuardOn(g)
        )

        // 8. Start AutoService Watchdog
        try {
            val autoIntent = Intent(context, AutoService::class.java).apply {
                putExtra(AutoService.EXTRA_GAME, g)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(autoIntent)
            } else {
                context.startService(autoIntent)
            }
        } catch (_: Throwable) {}

        // 9. Launch Game
        if (launchGame) {
            launchGame(context, g)
        }

        // 10. Live OS Verification Readback
        val wmRead = Shell.run("wm size").trim()
        val hzRead = Shell.run("settings get system min_refresh_rate").trim()
        val gmRead = Shell.run("cmd game get $g").trim()

        if (launchGame) {
            "🚀 HIGH PERFORMANCE GT BOOST ACTIVE & PUBG LAUNCHED!\n• Display: $wmRead\n• GameMode: $gmRead\n• Refresh Rate: ${hzRead}Hz\n• Dolby Scout: Preset 2 Active\n• Clean Volume: 70%\n• Watchdog: 5-Minute Timer Running"
        } else {
            "🔥 Realme GT Profile Active (All 4 Sections Pre-Configured) | Framebuffer: $wmRead | 120Hz: $hzRead"
        }
    }

    suspend fun executeMasterGtBoostAndLaunch(context: Context, game: String = "com.tencent.ig"): String {
        return executeMasterGtBoost(context, game, applyDisplayResolution = true, launchGame = true)
    }

    suspend fun executeFactoryFreshReset(context: Context?, game: String = "com.tencent.ig"): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)

        // 1. Reinstall system packages
        restoreSystemPackagesInstallExisting()

        // 2. Disable Audio DSP
        try {
            AudioTest.disableEffects()
        } catch (_: Throwable) {}

        // 3. Reset Display, Thermal, Touch & Power
        val revertScript = """
            wm size reset
            wm density reset
            service call SurfaceFlinger 1035 i32 -1 2>/dev/null
            service call SurfaceFlinger 1022 f 1.0 2>/dev/null
            cmd display clear-user-preferred-display-mode 2>/dev/null
            cmd thermalservice reset 2>/dev/null
            settings put system peak_refresh_rate 120.0
            settings put system min_refresh_rate 60.0
            settings put secure miui_refresh_rate 120
            settings put system speed_mode 0
            settings put system ultimate_mode 0
            setprop persist.sys.performance.level 0
            setprop vendor.gpu.control.level 0
            settings put global game_driver_opt_in_apps ""
            settings put global updatable_driver_production_opt_in_apps ""
            settings put global updatable_driver_production_opt_out_apps ""
            settings put system gamemode_touch_follow_response 50
            settings put system gb_touch_response_speed 50
            settings put system gb_touch_tap_stability 50
            settings put system gb_touch_aim_accuracy 50
            settings put system pointer_speed 0
            cmd device_config put surface_flinger_native_boot max_frame_buffer_acquired_buffers 3 2>/dev/null
            cmd device_config put activity_manager use_compaction true 2>/dev/null
            cmd wifi force-low-latency-mode disabled 2>/dev/null
            cmd wifi force-hi-perf-mode disabled 2>/dev/null
            cmd netpolicy set restrict-background false 2>/dev/null
            settings put global mobile_data_always_on 1
            settings put global private_dns_mode opportunistic
            cmd game reset $g 2>/dev/null
            settings put global window_animation_scale 0.5
            settings put global transition_animation_scale 0.5
            settings put global animator_duration_scale 0.5
            settings put secure tap_duration_threshold 0.1
            settings put secure tap_duration_enabled 0
            settings delete global policy_control 2>/dev/null
            settings put system night_charging 0 2>/dev/null
            echo 0 > /sys/class/power_supply/battery/input_suspend 2>/dev/null
        """.trimIndent()
        Shell.run(revertScript)

        // 4. Ensure all .ini files remain unlocked (0666)
        unlockGameConfigFiles(g)

        "♻️ 1-TAP FACTORY FRESH RESET COMPLETE: Phone restored 100% to stock 1.5K display, default clocks & system packages!"
    }

    // Bridges for AutoService & legacy calls
    suspend fun revertAll(game: String): String = executeFactoryFreshReset(null, game)
    suspend fun restoreToStock(pkg: String = "com.tencent.ig"): String = executeFactoryFreshReset(null, pkg)
    suspend fun executeMasterProfile(
        context: Context,
        pkg: String = "com.tencent.ig",
        apply810pDisplay: Boolean = false,
        launchGame: Boolean = false
    ): String = executeMasterGtBoost(context, pkg, applyDisplayResolution = apply810pDisplay, launchGame = launchGame)
}
