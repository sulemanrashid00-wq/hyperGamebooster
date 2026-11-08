package com.hypergameboost

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

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

    fun isRootAvailable(): Boolean {
        hasRoot?.let { return it }
        val found = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
        hasRoot = found
        return found
    }

    /**
     * Executes a command via Shizuku binder IPC.
     * Falls back to Root (su) or local standard shell (sh) if Shizuku is unavailable.
     */
    fun run(cmd: String): String {
        if (ShizukuState.current() == ShizukuState.READY) {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                val out = p.inputStream.bufferedReader().readText()
                val err = p.errorStream.bufferedReader().readText()
                p.waitFor()
                return (out + err).trim()
            } catch (_: Throwable) {
                // Fallback to local execution below
            }
        }

        // Root fallback
        if (isRootAvailable()) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val out = p.inputStream.bufferedReader().readText()
                val err = p.errorStream.bufferedReader().readText()
                p.waitFor()
                return (out + err).trim()
            } catch (_: Throwable) {
                // fallback to sh
            }
        }

        // Standard shell fallback
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (out + err).trim()
        } catch (e: Throwable) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Executes a batch of commands in a fast, single shell process session.
     */
    fun runBatch(cmds: List<String>): String {
        if (cmds.isEmpty()) return ""
        val script = cmds.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }.joinToString("\n")
        return run(script)
    }

    fun runBatch(vararg cmds: String): String = runBatch(cmds.toList())
}

/** Backward-compatible shorthand alias */
object Sh {
    fun run(cmd: String): String = Shell.run(cmd)
    fun runBatch(cmds: List<String>): String = Shell.runBatch(cmds)
    fun runBatch(vararg cmds: String): String = Shell.runBatch(*cmds)
}
