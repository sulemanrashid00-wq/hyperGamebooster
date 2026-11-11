package com.hypergameboost

import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
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
            val out = safeExecute(p, 2)
            out.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
        hasRoot = found
        return found
    }

    /**
     * Executes process with non-blocking async stream reader for BOTH stdout and stderr,
     * with strict 6-second timeout. Prevents any buffer saturation or deadlock!
     */
    private fun safeExecute(p: Process, timeoutSeconds: Long = 6): String {
        try {
            p.outputStream.close()
        } catch (_: Throwable) {}

        var stdout = ""
        var stderr = ""

        val outThread = Thread {
            try {
                stdout = p.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Throwable) {}
        }
        val errThread = Thread {
            try {
                stderr = p.errorStream.bufferedReader().use { it.readText() }
            } catch (_: Throwable) {}
        }

        outThread.start()
        errThread.start()

        val exited = try {
            p.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: Throwable) {
            false
        }

        if (!exited) {
            try {
                p.destroyForcibly()
            } catch (_: Throwable) {}
        }

        try {
            outThread.join(350)
            errThread.join(350)
        } catch (_: Throwable) {}

        val trimmedOut = stdout.trim()
        val trimmedErr = stderr.trim()

        return when {
            trimmedOut.isNotEmpty() && trimmedErr.isNotEmpty() -> "$trimmedOut\n$trimmedErr"
            trimmedOut.isNotEmpty() -> trimmedOut
            else -> trimmedErr
        }
    }

    /**
     * Executes shell command sequentially with guaranteed wait completion.
     */
    fun run(cmd: String): String {
        if (cmd.isBlank()) return ""

        val script = "$cmd\nwait\n"

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
                val p = m.invoke(null, arrayOf("sh", "-c", script), null, null) as? Process
                if (p != null) {
                    return safeExecute(p, 6)
                }
            } catch (_: Throwable) {
                // Fallback to local execution
            }
        }

        // 2. Root (su) Fallback
        if (isRootAvailable()) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
                return safeExecute(p, 6)
            } catch (_: Throwable) {
                // Fallback to standard sh
            }
        }

        // 3. Standard Runtime Exec Fallback
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", script))
            safeExecute(p, 5)
        } catch (e: Throwable) {
            "ERR: ${e.message}"
        }
    }

    /**
     * Executes a batch of sequential commands combined into a single atomic script.
     */
    fun runBatch(cmds: List<String>): String {
        if (cmds.isEmpty()) return ""
        val cleanCmds = cmds.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (cleanCmds.isEmpty()) return ""

        val combinedScript = cleanCmds.joinToString("\n") { it.trim() }
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
