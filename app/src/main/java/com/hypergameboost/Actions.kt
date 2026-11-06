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
