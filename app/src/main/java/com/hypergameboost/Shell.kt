package com.hypergameboost

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** 0 = Shizuku chal nahi raha, 1 = chal raha hai magar permission nahi, 2 = tayyar */
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
    } catch (e: Throwable) {
        NOT_RUNNING
    }
}

object Sh {
    /**
     * Shizuku ke zariye shell command chalata hai.
     * Shizuku 13.1+ mein newProcess() public API nahi raha, isliye reflection use hui hai.
     * (Agar aap chahein to baad mein isay Shizuku UserService (AIDL) se badal sakte hain.)
     */
    fun run(cmd: String): String {
        return try {
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
            (out + err).trim()
        } catch (e: Throwable) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
