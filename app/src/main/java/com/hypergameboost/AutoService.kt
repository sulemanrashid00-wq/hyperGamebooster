package com.hypergameboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AutoService - HyperOS 3 Smart Auto-Profile & 60s Watchdog Engine (v4.0 God-Tier)
 * 1. Automatically detects game start via PID transition (`pidof $game`)
 * 2. Instantly loads and applies per-game saved profile (FPS, Downscale, Touch IC, Saturation, Prime Pinning)
 * 3. 60-Second Watchdog Loop: kills Joyose, locks 120Hz hardware display mode (defeats 42°C drop), re-enforces thermal 0, pins PID via taskset ff
 * 4. The moment game closes (PID becomes empty), automatically reverts 100% to stock settings
 */
class AutoService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val game = intent?.getStringExtra(EXTRA_GAME) ?: "com.tencent.ig"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Auto Watchdog", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGameBooster v17.0 Master Watchdog")
            .setContentText("Monitoring $game (120FPS + 810p + Dolby Scout Active)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            AppPrefs.init(this@AutoService)
            AudioTest.init(this@AutoService)
            Actions.disableOrClearSystemThrottlers()
            runSmartWatchdog(game)
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        nm.notify(1, n)
    }

    private suspend fun runSmartWatchdog(game: String) {
        var gameHasRun = false
        var shutdownStartTimeMs = 0L

        while (currentCoroutineContext().isActive) {
            val pidOut = Sh.run("pidof $game").trim()
            val isGameRunning = pidOut.isNotEmpty() && pidOut.split(Regex("\\s+")).any { it.all(Char::isDigit) }

            if (isGameRunning) {
                shutdownStartTimeMs = 0L
                if (!gameHasRun) {
                    gameHasRun = true

                    // 1. Force Unrestricted Battery & Safe Throttler Disarm
                    Actions.applyUnrestrictedPowerPolicy(game)
                    Actions.disableOrClearSystemThrottlers()
                    Actions.esportsMode(game, packageName)
                    NativeTools.startAdpfSession(this@AutoService, 8333333L)
                    NativeTools.setWifiLowLatency(this@AutoService, true)

                    // 2. Enforce Master Profile (810p + 120Hz + 800x600 Ultra + Dolby Scout + Touch IC)
                    Actions.executeMasterProfile(this@AutoService, game, apply810pDisplay = true, launchGame = false)
                }

                // 3. Periodic Watchdog Maintenance: Terminate Joyose, pin CPU cores 0xfc, lock 120Hz
                Sh.run("am force-stop com.xiaomi.joyose 2>/dev/null")
                Actions.pinGameProcess(game)
                Actions.lockHardware120HzDisplayMode()
                updateNotification("HyperGameBooster Active", "🔥 120FPS + 810p + Dolby Scout + 480Hz Locked ($game)")
                NativeTools.reportAdpfWork(8000000L)
            } else if (gameHasRun) {
                // Game was running and is now closed! Start/continue 5-minute (300,000 ms) countdown
                if (shutdownStartTimeMs == 0L) {
                    shutdownStartTimeMs = System.currentTimeMillis()
                }
                val elapsed = System.currentTimeMillis() - shutdownStartTimeMs
                if (elapsed < 300_000L) {
                    val remainingSec = ((300_000L - elapsed) / 1000L).coerceAtLeast(1L)
                    updateNotification("HyperGameBooster: Game Closed", "⏳ Auto-Restoring Stock in ${remainingSec}s (Re-open game to cancel)")
                } else {
                    // 5 minutes expired! Revert everything to stock and reinstall system packages
                    updateNotification("HyperGameBooster", "♻️ 5-Minute Timer Expired: Restoring Stock & Packages...")
                    Actions.restoreToStock(game)
                    try {
                        AudioTest.disableEffects()
                    } catch (_: Throwable) {}
                    try {
                        stopService(Intent(this@AutoService, OverlayService::class.java))
                    } catch (_: Throwable) {}
                    NativeTools.stopAdpfSession()
                    NativeTools.setWifiLowLatency(this@AutoService, false)
                    gameHasRun = false
                    stopSelf()
                    break
                }
            }

            delay(15000L)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME = "game"
        private const val CHANNEL = "auto_watchdog_v8"
    }
}
