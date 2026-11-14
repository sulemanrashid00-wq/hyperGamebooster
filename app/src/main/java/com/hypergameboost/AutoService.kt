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
 * AutoService - Realme GT Game Space & 5-Minute Watchdog Engine (v18.0)
 * 1. Automatically tracks game process via `pidof $game`
 * 2. Continuously pins UE4 child threads (RenderThread, GameThread, RHIThread) to Snapdragon Prime/Gold cores (`0xfc`, `renice -20`)
 * 3. Keeps Joyose terminated and 120Hz display mode locked (-1 override)
 * 4. 5-Minute (300-second) Post-Game Auto-Revert:
 *    When game exits, begins 300s grace countdown. If user doesn't re-open game,
 *    reverts phone 100% to stock (wm size reset, pm install-existing, audio DSP off) and stops service!
 */
class AutoService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val game = intent?.getStringExtra(EXTRA_GAME) ?: "com.tencent.ig"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Realme GT Game Space Watchdog", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Realme GT Game Space Active")
            .setContentText("Monitoring $game (GT Mode + 120 FPS + Dolby Scout)")
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
                    // Enforce GT Boost & Pinning
                    Actions.executeMasterGtBoost(this@AutoService, game, applyDisplayResolution = true, launchGame = false)
                }

                // Periodic Watchdog Maintenance: Kill Joyose, pin CPU cores 0xfc, lock 120Hz
                Sh.run("am force-stop com.xiaomi.joyose 2>/dev/null")
                Actions.pinGameProcess(game)
                Actions.lockHardware120HzDisplayMode()
                updateNotification("Realme GT Game Space Active", "🔥 120FPS + 810p + Dolby Scout + 480Hz ($game)")
            } else if (gameHasRun) {
                // Game was running and is now closed: start / continue 5-minute (300,000 ms) countdown
                if (shutdownStartTimeMs == 0L) {
                    shutdownStartTimeMs = System.currentTimeMillis()
                }
                val elapsed = System.currentTimeMillis() - shutdownStartTimeMs
                if (elapsed < 300_000L) {
                    val remainingSec = ((300_000L - elapsed) / 1000L).coerceAtLeast(1L)
                    updateNotification("Game Closed ($game)", "⏳ Auto-Reverting Stock in ${remainingSec}s (Re-open game to cancel)")
                } else {
                    // 5 minutes expired! Revert everything to stock and reinstall system packages
                    updateNotification("Realme GT Game Space", "♻️ 5-Minute Timer Expired: Restoring 100% Stock State...")
                    Actions.executeFactoryFreshReset(this@AutoService, game)
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
        private const val CHANNEL = "realme_gt_watchdog_v18"
    }
}
