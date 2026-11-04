package com.hypergameboost

import kotlinx.coroutines.Dispatchers
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
        require(downscale in listOf("1.0", "0.9", "0.8", "0.7"))
        Sh.run("cmd game set --mode 2 --fps $fps --downscale $downscale $g").ifBlank { "Game Mode tuning set: ${fps}fps, scale $downscale" }
    }

    suspend fun gameModeReset(game: String): String = withContext(Dispatchers.IO) {
        Sh.run("cmd game reset ${safeGame(game)}").ifBlank { "Game Mode reset." }
    }

    /** Game ki Java/ART code ko pehle se compile karta hai (slow hai, sirf ek dafa). */
    suspend fun optimizeApp(game: String): String = withContext(Dispatchers.IO) {
        Sh.run("cmd package compile -m speed-profile -f ${safeGame(game)}")
    }

    /** Game ke baad sab kuch default par wapas. */
    suspend fun revertAll(game: String): String {
        return listOf(
            lockRefresh(null), animations(null), dnd(false), wifiLowLatency(false),
            keepAlive(game, false), gameModeReset(game), applyProfile(Profile.BALANCED, game)
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
}
