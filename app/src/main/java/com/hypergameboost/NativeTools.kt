package com.hypergameboost

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.app.ActivityManager
import android.hardware.display.DisplayManager
import android.view.Display

/** Native, ordinary-app capabilities. These do not need ADB, root, or Shizuku. */
object NativeTools {
    @Volatile private var wifiLock: WifiManager.WifiLock? = null

    fun deviceReport(context: Context): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val rates = display?.supportedModes?.map { it.refreshRate.toInt() }?.distinct()?.sorted()
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            listOf("None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")
                .getOrElse(pm.currentThermalStatus) { "Unknown" }
        } else "Unavailable"
        return buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Display refresh: ${display?.refreshRate?.toInt() ?: 0} Hz")
            appendLine("Advertised display modes: ${rates?.joinToString() ?: "Unavailable"} Hz")
            appendLine("Thermal status: $thermal")
            appendLine("Available RAM: ${mem.availMem / 1024 / 1024} MB / ${mem.totalMem / 1024 / 1024} MB")
            appendLine("Low-memory state: ${mem.lowMemory}")
            append("Native mode: no Shizuku / no ADB required")
        }
    }

    /** A standard Android Wi-Fi low-latency lock; it cannot guarantee lower game-server ping. */
    @Synchronized
    fun setWifiLowLatency(context: Context, enabled: Boolean): String {
        if (Build.VERSION.SDK_INT < 29) return "Wi-Fi low-latency lock requires Android 10 or newer."
        return try {
            if (enabled) {
                if (wifiLock?.isHeld == true) return "Wi-Fi low-latency lock is already active."
                val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "HyperGameBooster:LowLatency")
                    .apply { setReferenceCounted(false); acquire() }
                "Wi-Fi low-latency lock requested. Actual effect depends on Wi-Fi chipset and firmware."
            } else {
                wifiLock?.let { if (it.isHeld) it.release() }
                wifiLock = null
                "Wi-Fi low-latency lock released."
            }
        } catch (e: SecurityException) {
            "Permission denied by Android/HyperOS: ${e.message}"
        } catch (e: Exception) {
            "Wi-Fi low-latency mode unavailable: ${e.message}"
        }
    }

    fun releaseAll() {
        synchronized(this) {
            try { wifiLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
            wifiLock = null
        }
    }
}
