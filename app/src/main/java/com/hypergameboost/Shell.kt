package com.hypergameboost

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
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

    fun isRootAvailable(): Boolean {
        hasRoot?.let { return it }
        val found = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(1, TimeUnit.SECONDS)
            out.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
        hasRoot = found
        return found
    }

    /**
     * Executes a single shell command safely without hanging.
     * Tries Shizuku newProcess via reflection, falls back to Root, then standard runtime shell.
     */
    fun run(cmd: String): String {
        if (cmd.isBlank()) return ""

        // 1. Shizuku Binder Process Execution
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
                    val sb = StringBuilder()
                    val reader = BufferedReader(InputStreamReader(p.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    p.waitFor(4, TimeUnit.SECONDS)
                    return sb.toString().trim()
                }
            } catch (_: Throwable) {
                // Fallback to local execution
            }
        }

        // 2. Root (su) Fallback
        if (isRootAvailable()) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val sb = StringBuilder()
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                p.waitFor(4, TimeUnit.SECONDS)
                return sb.toString().trim()
            } catch (_: Throwable) {
                // Fallback to standard sh
            }
        }

        // 3. Standard Runtime Exec Fallback
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            p.waitFor(3, TimeUnit.SECONDS)
            sb.toString().trim()
        } catch (e: Throwable) {
            "ERR: ${e.message}"
        }
    }

    /**
     * Executes a batch of commands safely line-by-line without hanging threads.
     */
    fun runBatch(cmds: List<String>): String {
        if (cmds.isEmpty()) return ""
        val cleanCmds = cmds.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (cleanCmds.isEmpty()) return ""

        val combinedScript = cleanCmds.joinToString("\n")
        return try {
            run(combinedScript)
        } catch (_: Throwable) {
            // If bulk execution fails, run line-by-line safely
            val sb = StringBuilder()
            for (c in cleanCmds) {
                try {
                    val res = run(c)
                    if (res.isNotBlank()) sb.append(res).append("\n")
                } catch (_: Throwable) { }
            }
            sb.toString().trim()
        }
    }

    fun runBatch(vararg cmds: String): String = runBatch(cmds.toList())
}

/** Shorthand alias */
object Sh {
    fun run(cmd: String): String = Shell.run(cmd)
    fun runBatch(cmds: List<String>): String = Shell.runBatch(cmds)
    fun runBatch(vararg cmds: String): String = Shell.runBatch(*cmds)
}
