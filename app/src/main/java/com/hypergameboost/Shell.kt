package com.hypergameboost

import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** 0 = Shizuku not running, 1 = Running but no permission, 2 = Ready */
object ShizukuState {
    const val NOT_RUNNING = 0
    const val NO_PERMISSION = 1
    const val READY = 2

    fun current(): Int = try {
        when {
            !Shizuku.pingBinder() -> NOT_RUNNING
            Shizuku.isPreV11() -> NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> READY
            else -> NO_PERMISSION
        }
    } catch (_: Throwable) {
        NOT_RUNNING
    }
}

object Shell {
    private var hasRoot: Boolean? = null
    private val asyncScope = CoroutineScope(Dispatchers.IO)

    fun isRootAvailable(): Boolean {
        hasRoot?.let { return it }
        val found = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(1, TimeUnit.SECONDS)
            out.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
        hasRoot = found
        return found
    }

    /**
     * Executes shell command at maximum speed with zero blocking overhead.
     */
    fun run(cmd: String): String {
        if (cmd.isBlank()) return ""

        // 1. Shizuku Binder Process Execution (High-speed reflection)
        if (ShizukuState.current() == ShizukuState.READY) {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
                if (p != null) {
                    val out = p.inputStream.bufferedReader().use { it.readText() }
                    p.waitFor(3, TimeUnit.SECONDS)
                    return out.trim()
                }
            } catch (_: Throwable) {
                // Fallback to local execution
            }
        }

        // 2. Root (su) Fallback
        if (isRootAvailable()) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val out = p.inputStream.bufferedReader().use { it.readText() }
                p.waitFor(3, TimeUnit.SECONDS)
                return out.trim()
            } catch (_: Throwable) {
                // Fallback to standard sh
            }
        }

        // 3. Standard Runtime Exec Fallback
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(2, TimeUnit.SECONDS)
            out.trim()
        } catch (e: Throwable) {
            "ERR: ${e.message}"
        }
    }

    /**
     * Executes a batch of commands combined into a single fast atomic shell process.
     */
    fun runBatch(cmds: List<String>): String {
        if (cmds.isEmpty()) return ""
        val cleanCmds = cmds.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (cleanCmds.isEmpty()) return ""

        val combinedScript = cleanCmds.joinToString("\n")
        return run(combinedScript)
    }

    fun runBatch(vararg cmds: String): String = runBatch(cmds.toList())

    /** Asynchronous fire-and-forget execution (never blocks caller) */
    fun runAsync(cmd: String) {
        asyncScope.launch { run(cmd) }
    }
}

/** Shorthand alias */
object Sh {
    fun run(cmd: String): String = Shell.run(cmd)
    fun runBatch(cmds: List<String>): String = Shell.runBatch(cmds)
    fun runBatch(vararg cmds: String): String = Shell.runBatch(*cmds)
    fun runAsync(cmd: String) = Shell.runAsync(cmd)
}
