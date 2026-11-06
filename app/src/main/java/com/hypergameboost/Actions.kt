package com.hypergameboost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Profile { PERFORMANCE, BALANCED, BATTERY_SAVER }

object Games {
    /** Jo PUBG variants app pehchanti hai (package name hamesha is list se aata hai, user input se nahi) */
    val known = linkedMapOf(
        "com.tencent.ig" to "PUBG Mobile (Global)",
        "com.pubg.imobile" to "BGMI",
        "com.pubg.krmobile" to "PUBG Mobile (KR)",
        "com.vng.pubgmobile" to "PUBG Mobile (VN)",
        "com.rekoo.pubgm" to "PUBG Mobile (TW)"
    )
}

object Actions {
    private val pkgRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    private const val CONFIG_SUFFIX =
        "files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android"
    private const val BACKUP_ROOT = "/sdcard/HyperGameBooster/backup"

    private fun safeGame(pkg: String): String {
        require(pkg in Games.known) { "Unknown game package" }
        return pkg
    }

    suspend fun installedGames(): List<String> = withContext(Dispatchers.IO) {
        val out = Sh.run("pm list packages")
        Games.known.keys.filter { out.contains("package:$it") }
    }

    /** Sirf standard Android commands. Jo HyperOS support na kare wo ignore ho jati hai. */
    suspend fun applyProfile(profile: Profile, game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val cmds = when (profile) {
            Profile.PERFORMANCE -> listOf(
                "cmd game mode performance $g",
                "cmd power set-fixed-performance-mode-enabled true",
                "settings put global low_power 0"
            )
            Profile.BALANCED -> listOf(
                "cmd game mode standard $g",
                "cmd power set-fixed-performance-mode-enabled false",
                "settings put global low_power 0"
            )
            Profile.BATTERY_SAVER -> listOf(
                "cmd game mode battery $g",
                "cmd power set-fixed-performance-mode-enabled false",
                "settings put global low_power 1"
            )
        }
        cmds.joinToString("\n") { c -> "$ $c\n" + Sh.run(c) }
    }

    suspend fun launch(game: String): String = withContext(Dispatchers.IO) {
        Sh.run("monkey -p ${safeGame(game)} -c android.intent.category.LAUNCHER 1")
    }

    /** Third-party apps force-stop karta hai (game, launcher, keyboard, Shizuku aur khud app bachayi jati hain). */
    suspend fun cleanRam(keep: Set<String>, self: String): String = withContext(Dispatchers.IO) {
        val protectedWords = listOf("launcher", "inputmethod", "keyboard", "shizuku", "dialer", "systemui")
        val pkgs = Sh.run("pm list packages -3")
            .lines()
            .mapNotNull { it.removePrefix("package:").trim().takeIf { p -> pkgRegex.matches(p) } }
            .filter { it != self && it !in keep && it !in Games.known && protectedWords.none { w -> it.contains(w, true) } }
        pkgs.forEach { Sh.run("am force-stop $it") }
        "Band ki gayi apps: ${pkgs.size}\n" + pkgs.joinToString("\n")
    }

    suspend fun trimCaches(): String = withContext(Dispatchers.IO) {
        Sh.run("pm trim-caches 999G").ifBlank { "Cache saaf kar diya gaya." }
    }

    // ---- PUBG cache / config backup (graphics editor v2 mein, aapke asli config file ke baad) ----

    suspend fun clearGameCache(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Sh.run("rm -rf /sdcard/Android/data/$g/cache && echo done").ifBlank { "done" }
    }

    suspend fun backupConfig(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val src = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val dst = "$BACKUP_ROOT/$g"
        Sh.run("[ -d '$src' ] && mkdir -p '$dst' && cp -r '$src/.' '$dst/' && echo 'Backup ho gaya: $dst' || echo 'Config folder nahi mila (pehle game ek baar chalayein)'")
    }

    suspend fun restoreConfig(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val dst = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val src = "$BACKUP_ROOT/$g"
        Sh.run("[ -d '$src' ] && cp -r '$src/.' '$dst/' && echo 'Restore ho gaya' || echo 'Backup nahi mila'")
    }

    /**
     * Troubleshoot: device info + game config folder ki asli files ki list.
     * Isi report se graphics editor ke liye asli file format samajh aata hai.
     */
    suspend fun diagnose(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val cfg = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val sb = StringBuilder()
        sb.appendLine("Model: " + Sh.run("getprop ro.product.model"))
        sb.appendLine("Android: " + Sh.run("getprop ro.build.version.release") + " (SDK " + Sh.run("getprop ro.build.version.sdk") + ")")
        sb.appendLine("HyperOS: " + Sh.run("getprop ro.mi.os.version.name") + " " + Sh.run("getprop ro.mi.os.version.incremental"))
        sb.appendLine("Screen: " + Sh.run("wm size").replace("\n", " | "))
        sb.appendLine("Game installed: " + Sh.run("pm list packages $g").ifBlank { "NO" })
        sb.appendLine("Game version: " + Sh.run("dumpsys package $g | grep versionName | head -1").trim())
        sb.appendLine("Game Mode: " + Sh.run("cmd game list-configs $g | head -5"))
        sb.appendLine("Config folder: $cfg")
        sb.appendLine(Sh.run("ls -la '$cfg' 2>&1"))
        sb.toString()
    }

    // ---- Extra gaming tools (sab standard Android commands; har ek ka revert maujood) ----

    /** hz = null matlab wapas Auto (settings hata do). HyperOS kabhi kabhi isay override karta hai. */
    suspend fun lockRefresh(hz: Int?): String = withContext(Dispatchers.IO) {
        if (hz == null) {
            Sh.run("settings delete system min_refresh_rate")
            Sh.run("settings delete system peak_refresh_rate")
            "Refresh rate Auto par wapas."
        } else {
            require(hz in 60..144)
            Sh.run("settings put system min_refresh_rate $hz")
            Sh.run("settings put system peak_refresh_rate $hz")
            "Refresh rate $hz Hz lock (garmi badh sakti hai)."
        }
    }

    suspend fun animations(scale: String?): String = withContext(Dispatchers.IO) {
        val s = scale ?: "1.0"
        require(s in listOf("0", "0.5", "1.0"))
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .forEach { Sh.run("settings put global $it $s") }
        "Animation scale: $s"
    }

    suspend fun dnd(on: Boolean): String = withContext(Dispatchers.IO) {
        Sh.run("cmd notification set_dnd ${if (on) "on" else "off"}").ifBlank { "DND ${if (on) "ON" else "OFF"}" }
    }

    suspend fun wifiLowLatency(on: Boolean): String = withContext(Dispatchers.IO) {
        Sh.run("cmd wifi force-low-latency-mode ${if (on) "enabled" else "disabled"}")
            .ifBlank { "Wi-Fi low-latency ${if (on) "ON" else "OFF"}" }
    }

    /** Game ko Doze / background restriction se bachata hai. */
    suspend fun keepAlive(game: String, on: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val a = Sh.run("cmd deviceidle whitelist ${if (on) "+" else "-"}$g")
        val b = Sh.run("cmd appops set $g RUN_IN_BACKGROUND ${if (on) "allow" else "default"}")
        "$a\n$b".trim()
    }

    /** Android Game Mode intervention (fps cap / downscale). Asar ROM aur game par depend karta hai. */
    suspend fun gameModeTuning(game: String, fps: Int, downscale: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        require(fps in listOf(60, 90, 120))
        require(downscale in listOf("1.0", "0.9", "0.8", "0.7", "0.6", "0.5"))
        Sh.run("cmd game set --mode 2 --fps $fps --downscale $downscale $g").ifBlank { "Game Mode tuning set: ${fps}fps, scale $downscale" }
    }

    suspend fun gameModeReset(game: String): String = withContext(Dispatchers.IO) {
        Sh.run("cmd game reset ${safeGame(game)}").ifBlank { "Game Mode reset." }
    }

    /** Game ki Java/ART code ko pehle se compile karta hai (slow hai, sirf ek dafa). */
    suspend fun optimizeApp(game: String): String = withContext(Dispatchers.IO) {
        Sh.run("cmd package compile -m speed-profile -f ${safeGame(game)}")
    }

    /** Fallback: device_config game_overlay (Game Mode ka doosra raasta). */
    suspend fun overlayFallback(game: String, fps: Int, downscale: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        require(fps in listOf(60, 90, 120))
        require(downscale in listOf("1.0", "0.9", "0.8", "0.7", "0.6", "0.5"))
        val a = Sh.run("device_config put game_overlay $g 'mode=2,downscaleFactor=$downscale,fps=$fps'")
        val b = Sh.run("cmd game mode performance $g")
        "$a\n$b".trim().ifBlank { "Overlay set: ${fps}fps, scale $downscale" }
    }

    suspend fun overlayReset(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Sh.run("device_config delete game_overlay $g").ifBlank { "Overlay reset." }
    }

    // ---- Thermal (sirf software status; hardware throttling kernel/Xiaomi engine karta hai) ----

    suspend fun thermalOverride(on: Boolean): String = withContext(Dispatchers.IO) {
        if (on) {
            Sh.run("cmd thermalservice override-status 0")
            Sh.run("cmd power set-fixed-performance-mode-enabled true")
            Sh.run("cmd power set-adaptive-power-saver-enabled false")
            "Thermal status override ON (sirf software status, hardware throttle nahi rukta)"
        } else {
            Sh.run("cmd thermalservice reset")
            Sh.run("cmd power set-fixed-performance-mode-enabled false")
            Sh.run("cmd power set-adaptive-power-saver-enabled true")
            "Thermal override OFF"
        }
    }

    suspend fun thermalInfo(): String = withContext(Dispatchers.IO) {
        val h = Sh.run("cmd thermalservice headroom 10 2>&1 | head -3")
        val d = Sh.run("dumpsys thermalservice 2>&1 | grep -iE 'Thermal Status|mValue|Temperature' | head -12")
        "Headroom (1.0 = throttle):\n$h\n\n$d"
    }

    // ---- Input guard (touch sampling / aim sensitivity shell se nahi badalti; ye sirf interference hatata hai) ----

    suspend fun haptics(off: Boolean): String = withContext(Dispatchers.IO) {
        val v = if (off) 0 else 1
        Sh.run("settings put system haptic_feedback_enabled $v")
        Sh.run("settings put system vibrate_input_devices $v")
        "Haptic/vibration ${if (off) "OFF" else "ON"}"
    }

    suspend fun edgeGesture(off: Boolean): String = withContext(Dispatchers.IO) {
        if (off) {
            Sh.run("settings put secure back_gesture_inset_scale_left 0")
            Sh.run("settings put secure back_gesture_inset_scale_right 0")
            "Edge back gesture OFF (galti se back swipe nahi hoga)"
        } else {
            Sh.run("settings delete secure back_gesture_inset_scale_left")
            Sh.run("settings delete secure back_gesture_inset_scale_right")
            "Edge back gesture default"
        }
    }

    // ---- Network ----

    suspend fun wifiTweaks(on: Boolean): String = withContext(Dispatchers.IO) {
        val keys = listOf("wifi_scan_always_enabled", "ble_scan_always_enabled", "wifi_suspend_optimizations_enabled")
        if (on) keys.forEach { Sh.run("settings put global $it 0") } else keys.forEach { Sh.run("settings delete global $it") }
        "Wi-Fi/BLE background scan ${if (on) "OFF" else "default"}"
    }

    // ---- Background ----

    private val bloat = listOf("com.xiaomi.mipicks", "com.miui.analytics", "com.miui.msa.global")

    suspend fun standby(on: Boolean): String = withContext(Dispatchers.IO) {
        bloat.forEach { Sh.run("am set-standby-bucket $it ${if (on) 45 else 20}") }
        "Background bloat standby: ${if (on) "restricted" else "normal"}"
    }

    suspend fun phantom(on: Boolean): String = withContext(Dispatchers.IO) {
        if (on) {
            Sh.run("settings put global settings_enable_monitor_phantom_procs false")
            Sh.run("device_config put activity_manager max_phantom_processes 1024")
        } else {
            Sh.run("settings delete global settings_enable_monitor_phantom_procs")
            Sh.run("device_config delete activity_manager max_phantom_processes")
        }
        "Phantom process limit: ${if (on) "badha diya" else "default"}"
    }

    // ---- Shader cache + UserCustom.ini (experimental, ban risk, backup ke saath) ----

    suspend fun clearShaderCache(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        Sh.run("rm -rf /sdcard/Android/data/$g/cache/* /sdcard/Android/data/$g/files/ProgramBinaryCache/*")
        "Shader/program cache saaf (pehla match thoda stutter kar sakta hai)"
    }

    suspend fun userCustomIni(game: String, on: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val dir = "/sdcard/Android/data/$g/$CONFIG_SUFFIX"
        val ini = "$dir/UserCustom.ini"
        if (on) {
            Sh.run(
                "mkdir -p '$dir' && { [ -f '$ini' ] && [ ! -f '$ini.hgb_bak' ] && cp '$ini' '$ini.hgb_bak'; true; } && " +
                    "printf '[SystemSettings]\\nr.MipMapLODBias=3\\nr.ShadowQuality=0\\nr.BloomQuality=0\\n' > '$ini' && echo written"
            ).ifBlank { "written" } + " (experimental: asar game version par depend karta hai)"
        } else {
            Sh.run("if [ -f '$ini.hgb_bak' ]; then mv '$ini.hgb_bak' '$ini'; else rm -f '$ini'; fi; echo restored")
        }
    }

    /**
     * Asli FPS measure karta hai (SurfaceFlinger ke frame timestamps se).
     * Button dabane ke 5 second baad sampling shuru hoti hai: us dauran game screen par aa jaye.
     */
    suspend fun fpsTest(game: String): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        delay(5000)
        val layers = Sh.run("dumpsys SurfaceFlinger --list").lines().filter { it.contains(g) }
        val layer = (layers.firstOrNull { it.contains("SurfaceView") } ?: layers.firstOrNull())?.trim()
            ?: return@withContext "Game ki layer nahi mili. Game screen par chal rahi honi chahiye (match ya lobby)."
        val times = sortedSetOf<Long>()
        repeat(20) {
            Sh.run("dumpsys SurfaceFlinger --latency '$layer'").lines().drop(1).forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 3) {
                    val t = p[1].toLongOrNull()
                    if (t != null && t > 0L && t != Long.MAX_VALUE) times.add(t)
                }
            }
            delay(500)
        }
        val ts = times.toList()
        if (ts.size < 20) return@withContext "Frame data bahut kam mila (${ts.size}). Game foreground mein rakhein aur dobara try karein."
        val diffsMs = ts.zipWithNext { a, b -> (b - a) / 1_000_000.0 }
        val avgFps = (ts.size - 1) * 1_000_000_000.0 / (ts.last() - ts.first())
        val sorted = diffsMs.sorted()
        val p99 = sorted[((sorted.size - 1) * 0.99).toInt()]
        val p999 = sorted[((sorted.size - 1) * 0.999).toInt()]
        val limits = listOf(8.5, 12.5, 16.7, 25.0, 33.4, Double.MAX_VALUE)
        val labels = listOf("<=8.5ms (120+)", "<=12.5ms (80+)", "<=16.7ms (60+)", "<=25ms", "<=33ms", ">33ms")
        val counts = IntArray(limits.size)
        diffsMs.forEach { d -> counts[limits.indexOfFirst { d <= it }]++ }
        val hist = labels.indices.joinToString("\n") { i ->
            val bar = "#".repeat(counts[i] * 30 / diffsMs.size.coerceAtLeast(1))
            "%-16s %4d %s".format(labels[i], counts[i], bar)
        }
        val over25 = diffsMs.count { it > 25.0 }
        val over50 = diffsMs.count { it > 50.0 }
        "FPS test (10 sec)\n" +
            "Average FPS: ${"%.1f".format(avgFps)}\n" +
            "1% low FPS: ${"%.1f".format(1000.0 / p99)}\n" +
            "0.1% low FPS: ${"%.1f".format(1000.0 / p999)}\n" +
            "Frames > 25 ms: $over25   Frames > 50 ms: $over50 (kul ${diffsMs.size})\n\n" +
            "Frame-time histogram:\n$hist\n\n" +
            "Layer: $layer\n\n" + throttleInspector()
    }

    // ---- Display saturation (SurfaceFlinger debug call; HyperOS 3 par kaam na kare to koi nuqsan nahi) ----

    suspend fun saturation(v: String?): String = withContext(Dispatchers.IO) {
        val s = v ?: "1.0"
        require(s in listOf("1.0", "1.2", "1.4", "1.6"))
        val out = Sh.run("service call SurfaceFlinger 1022 f $s")
        "Saturation $s -> $out"
    }

    // ---- Network ----

    /** mode: cloudflare | google | auto */
    suspend fun dns(mode: String): String = withContext(Dispatchers.IO) {
        when (mode) {
            "cloudflare" -> {
                Sh.run("settings put global private_dns_mode hostname")
                Sh.run("settings put global private_dns_specifier one.one.one.one")
                "Private DNS: Cloudflare"
            }
            "google" -> {
                Sh.run("settings put global private_dns_mode hostname")
                Sh.run("settings put global private_dns_specifier dns.google")
                "Private DNS: Google"
            }
            else -> {
                Sh.run("settings put global private_dns_mode opportunistic")
                "Private DNS: Automatic"
            }
        }
    }

    // ---- Notifications / brightness ----

    suspend fun headsUp(off: Boolean): String = withContext(Dispatchers.IO) {
        Sh.run("settings put global heads_up_notifications_enabled ${if (off) 0 else 1}")
        "Heads-up popups ${if (off) "band" else "chalu"}"
    }

    suspend fun brightnessLock(lock: Boolean): String = withContext(Dispatchers.IO) {
        Sh.run("settings put system screen_brightness_mode ${if (lock) 0 else 1}")
        "Auto-brightness ${if (lock) "band (manual)" else "chalu"}"
    }

    // ---- Storage ----

    suspend fun fstrim(): String = withContext(Dispatchers.IO) {
        Sh.run("sm fstrim").ifBlank { "TRIM chala diya gaya." }
    }

    suspend fun settingsSnapshot(): String = withContext(Dispatchers.IO) {
        Sh.run(
            "mkdir -p /sdcard/HyperGameBooster/settings_snapshot && " +
                "settings list system > /sdcard/HyperGameBooster/settings_snapshot/system.txt && " +
                "settings list secure > /sdcard/HyperGameBooster/settings_snapshot/secure.txt && " +
                "settings list global > /sdcard/HyperGameBooster/settings_snapshot/global.txt && " +
                "echo 'Snapshot saved: /sdcard/HyperGameBooster/settings_snapshot/'"
        )
    }

    // ---- Throttle inspector (sirf padhta hai) ----

    /**
     * CPU/GPU ki current aur max frequency aur thermal zones dikhata hai.
     * Agar game ke dauran cur freq max se bahut neeche ho to throttling ho rahi hai.
     * Kuch sysfs files shell ko SELinux padhne nahi deta: wahan "Permission denied" aayega.
     */
    suspend fun throttleInspector(): String = withContext(Dispatchers.IO) {
        val cpu = Sh.run(
            "for c in /sys/devices/system/cpu/cpu[0-9]*; do " +
                "echo \"\$(basename \$c): \$(cat \$c/cpufreq/scaling_cur_freq 2>&1) / \$(cat \$c/cpufreq/scaling_max_freq 2>&1) kHz\"; done"
        )
        val gpu = Sh.run(
            "echo \"gpuclk: \$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>&1)\"; " +
                "echo \"max_gpuclk: \$(cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>&1)\"; " +
                "echo \"gpu busy %: \$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>&1)\""
        )
        val temps = Sh.run(
            "for z in /sys/class/thermal/thermal_zone*; do " +
                "echo \"\$(cat \$z/type 2>/dev/null) \$(cat \$z/temp 2>/dev/null)\"; done | grep -iE 'cpu|gpu|skin|battery|soc' | head -25"
        )
        "== CPU (cur / max)\n$cpu\n\n== GPU\n$gpu\n\n== Temps (milli-degree C)\n$temps"
    }

    // ---- v0.5: social apps suspend, captive portal, ping, settings A/B diff ----

    private val socialApps = listOf("com.zhiliaoapp.musically", "com.instagram.android", "com.facebook.katana")

    /** Game ke dauran social apps suspend (notification/CPU band). OFF par sab wapas. */
    suspend fun suspendSocial(on: Boolean): String = withContext(Dispatchers.IO) {
        val installed = Sh.run("pm list packages")
        val hit = socialApps.filter { installed.contains("package:$it") }
        hit.forEach { Sh.run("pm ${if (on) "suspend" else "unsuspend"} --user 0 $it") }
        "Social apps ${if (on) "suspended" else "unsuspended"}: ${hit.size}"
    }

    private val bgApps = listOf("com.instagram.android", "com.zhiliaoapp.musically", "com.snapchat.android", "org.telegram.messenger", "com.facebook.katana", "com.facebook.orca")

    /** Real Android: cached apps freezer, memory compaction, background run band, game ko exempted standby bucket. */
    suspend fun gamePerf(game: String, on: Boolean): String = withContext(Dispatchers.IO) {
        val g = safeGame(game)
        val installed = Sh.run("pm list packages")
        val hit = bgApps.filter { installed.contains("package:$it") }
        if (on) {
            Sh.run("settings put global cached_apps_freezer enabled")
            Sh.run("device_config put activity_manager use_compaction true")
            Sh.run("settings put global low_power 0")
            Sh.run("am set-standby-bucket $g 5")
            hit.forEach { Sh.run("cmd appops set $it RUN_ANY_IN_BACKGROUND ignore") }
        } else {
            Sh.run("settings delete global cached_apps_freezer")
            Sh.run("device_config delete activity_manager use_compaction")
            Sh.run("am set-standby-bucket $g 20")
            hit.forEach { Sh.run("cmd appops set $it RUN_ANY_IN_BACKGROUND default") }
        }
        "Background freeze ${if (on) "ON" else "OFF"}: ${hit.size} apps, game bucket ${if (on) "VIP" else "normal"}"
    }

    suspend fun captivePortal(off: Boolean): String = withContext(Dispatchers.IO) {
        if (off) Sh.run("settings put global captive_portal_mode 0") else Sh.run("settings delete global captive_portal_mode")
        "Captive portal check ${if (off) "band" else "default"}"
    }

    suspend fun pingTest(): String = withContext(Dispatchers.IO) {
        "Ping 1.1.1.1 (10 packets)\n" + Sh.run("ping -c 10 -i 0.3 -W 2 1.1.1.1 2>&1 | tail -3")
    }

    object Snaps {
        val map = mutableMapOf<String, Map<String, String>>()
    }

    /** Game Turbo ki asli keys dhoondhne ke liye: A lein, Game Turbo ka ek option badlein, B lein, diff dekhein. */
    suspend fun snapshot(name: String): String = withContext(Dispatchers.IO) {
        require(name == "A" || name == "B")
        val m = linkedMapOf<String, String>()
        for (ns in listOf("system", "secure", "global")) {
            Sh.run("settings list $ns").lines().forEach { l ->
                val i = l.indexOf('=')
                if (i > 0) m["$ns:${l.substring(0, i)}"] = l.substring(i + 1)
            }
        }
        val propRegex = Regex("^\\[(.*)\\]: \\[(.*)\\]$")
        Sh.run("getprop").lines().forEach { l ->
            propRegex.find(l.trim())?.let { m["prop:${it.groupValues[1]}"] = it.groupValues[2] }
        }
        Snaps.map[name] = m
        "Snapshot $name saved (${m.size} entries)"
    }

    fun diffSnaps(): String {
        val a = Snaps.map["A"]
        val b = Snaps.map["B"]
        if (a == null || b == null) return "Pehle A aur B dono snapshot lein."
        val out = (a.keys + b.keys).toSortedSet().mapNotNull { k ->
            val x = a[k]
            val y = b[k]
            if (x != y) "$k: ${x ?: "(none)"} -> ${y ?: "(none)"}" else null
        }
        return if (out.isEmpty()) "Koi fark nahi mila." else "${out.size} badlav:\n" + out.take(80).joinToString("\n")
    }

    /** Overlay permission Shizuku se (settings mein jaane ki zarurat nahi). */
    suspend fun grantOverlay(pkg: String): String = withContext(Dispatchers.IO) {
        require(pkgRegex.matches(pkg))
        Sh.run("appops set $pkg SYSTEM_ALERT_WINDOW allow").ifBlank { "Overlay permission de di gayi." }
    }

    // ---- v0.6: DNS benchmark, background data firewall, Bluetooth volume ----

    suspend fun dnsBenchmark(): String = withContext(Dispatchers.IO) {
        listOf("1.1.1.1" to "Cloudflare", "8.8.8.8" to "Google", "9.9.9.9" to "Quad9").joinToString("\n") { (ip, name) ->
            val r = Sh.run("ping -c 5 -i 0.3 -W 2 $ip 2>&1 | tail -1")
            "$name ($ip): " + r.ifBlank { "koi jawab nahi" }
        }
    }

    @Volatile
    private var firewallOn = false

    /**
     * Social apps ka background data Data Saver ke zariye band. Dhyan: Data Saver poore phone par on hota hai,
     * to doosri apps ka background data bhi ruk sakta hai. OFF par wapas.
     */
    suspend fun backgroundFirewall(on: Boolean): String = withContext(Dispatchers.IO) {
        val out = Sh.run("pm list packages -U")
        val uids = socialApps.mapNotNull { pkg ->
            Regex("package:" + Regex.escape(pkg) + " uid:(\\d+)").find(out)?.groupValues?.get(1)
        }
        uids.forEach { Sh.run("cmd netpolicy ${if (on) "add" else "remove"} restrict-background-blacklist $it") }
        Sh.run("cmd netpolicy set restrict-background ${if (on) "true" else "false"}")
        firewallOn = on
        "Background data firewall ${if (on) "ON" else "OFF"} (${uids.size} apps, Data Saver ${if (on) "on" else "off"})"
    }

    suspend fun firewallRevertIfOn(): String =
        if (firewallOn) backgroundFirewall(false) else "Firewall pehle se off"

    suspend fun btAbsVolume(disable: Boolean): String = withContext(Dispatchers.IO) {
        if (disable) Sh.run("settings put global bluetooth_disable_absolute_volume 1")
        else Sh.run("settings delete global bluetooth_disable_absolute_volume")
        "Bluetooth absolute volume ${if (disable) "band" else "default"} (earbuds dobara connect karein)"
    }

    /** Ek button: sab kuch performance ke liye. Revert ke liye revertAll() hai. */
    suspend fun esportsMode(game: String, self: String): String {
        return listOf(
            thermalOverride(true), lockRefresh(120), applyProfile(Profile.PERFORMANCE, game),
            dnd(true), headsUp(true), wifiLowLatency(true), keepAlive(game, true), standby(true),
            suspendSocial(true), cleanRam(emptySet(), self)
        ).joinToString("\n")
    }

    /** Game ke baad sab kuch default par wapas. */
    suspend fun revertAll(game: String): String {
        return listOf(
            lockRefresh(null), animations(null), dnd(false), wifiLowLatency(false),
            keepAlive(game, false), gameModeReset(game), overlayReset(game),
            thermalOverride(false), standby(false), haptics(false), edgeGesture(false),
            wifiTweaks(false), phantom(false), saturation(null), dns("auto"),
            headsUp(false), brightnessLock(false), suspendSocial(false), gamePerf(game, false), captivePortal(false), firewallRevertIfOn(),
            applyProfile(Profile.BALANCED, game), resetResolution()
        ).joinToString("\n")
    }

    // ---- Display density / size (UltDroid jaisa) ----

    suspend fun densityInfo(): String = withContext(Dispatchers.IO) { Sh.run("wm density") }

    suspend fun setDensity(dpi: Int): String = withContext(Dispatchers.IO) {
        require(dpi in 240..800) { "DPI 240-800 ke beech hona chahiye" }
        Sh.run("wm density $dpi").ifBlank { "Density set: $dpi" }
    }

    suspend fun resetDensity(): String = withContext(Dispatchers.IO) {
        Sh.run("wm density reset").ifBlank { "Density reset ho gayi." }
    }

    // ---- Asli resolution (system-wide, har game par chalta hai; cmd game downscale PUBG ignore karta hai) ----

    private fun parseWH(s: String, key: String): Pair<Int, Int>? {
        val m = Regex(key + """:\s*(\d+)x(\d+)""").find(s) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    suspend fun setResolution(percent: Int): String = withContext(Dispatchers.IO) {
        require(percent in 55..100) { "Resolution 55-100% ke beech hona chahiye" }
        val sizeOut = Sh.run("wm size")
        val (pw, ph) = parseWH(sizeOut, "Physical size") ?: return@withContext "Screen size parse nahi hui: $sizeOut"
        val dOut = Sh.run("wm density")
        val pd = Regex("""Physical density:\s*(\d+)""").find(dOut)?.groupValues?.get(1)?.toInt()
        if (percent == 100) {
            Sh.run("wm size reset"); Sh.run("wm density reset")
            return@withContext "Resolution original: ${pw}x$ph"
        }
        val w = (pw * percent / 100) / 2 * 2
        val h = (ph * percent / 100) / 2 * 2
        val a = Sh.run("wm size ${w}x$h")
        val b = if (pd != null) Sh.run("wm density ${pd * percent / 100}") else ""
        "Resolution ${percent}%: ${w}x$h (original ${pw}x$ph) $a $b".trim()
    }

    suspend fun resetResolution(): String = withContext(Dispatchers.IO) {
        Sh.run("wm size reset"); Sh.run("wm density reset")
        "Resolution + density original par wapas"
    }
}
