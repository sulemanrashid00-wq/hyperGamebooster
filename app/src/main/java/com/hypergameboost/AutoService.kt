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
 * AutoService - HyperOS 3 Game Space & Joyose 60-Second Watchdog (Section 2 & 5)
 * - Automatically detects PUBG / BGMI foreground state
 * - Activates 120 FPS Esports Engine & WildBoost 3.0
 * - 60-Second Watchdog Loop:
 *   1. Restricts PowerKeeper & MIUI Daemon
 *   2. Kills com.xiaomi.joyose to prevent mid-match HyperOS thermal lock
 *   3. Re-applies thermal override status 0
 *   4. Pins game PID to Prime CPU cores (taskset ff) & Real-time I/O (ionice -c 2 -n 0)
 * - Restores default system governor when game closes
 */
class AutoService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val game = intent?.getStringExtra(EXTRA_GAME) ?: "com.tencent.ig"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Auto Watchdog", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGameBooster Watchdog Active")
            .setContentText("Monitoring $game & HyperOS 3 Engine")
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
            // Apply initial Joyose and Powerkeeper restrictions
            Actions.restrictPowerKeeperAndJoyose()
            runWatchdog(game)
        }
        return START_NOT_STICKY
    }

    private suspend fun runWatchdog(game: String) {
        var isGameActive = false
        var consecutiveMissing = 0
        var watchdogTimer = 0L

        while (currentCoroutineContext().isActive) {
            val topActivity = Sh.run("dumpsys activity activities | grep -m 3 -E 'topResumedActivity|mResumedActivity'")
            val inGame = topActivity.contains(game)

            if (inGame) {
                consecutiveMissing = 0
                if (!isGameActive) {
                    isGameActive = true
                    // Activate full Esports Engine + WildBoost + FEAS
                    Actions.esportsMode(game, packageName)
                    NativeTools.startAdpfSession(this@AutoService, 8333333L)
                    NativeTools.setWifiLowLatency(this@AutoService, true)

                    // Pin CPU and enforce thermal immediately
                    Actions.pinGameProcess(game)

                    // Check user per-game custom preferences
                    val sp = getSharedPreferences("hgb", Context.MODE_PRIVATE)
                    val ds = sp.getString("ds_$game", null)
                    val fps = sp.getInt("fps_$game", 0)
                    if (ds != null && fps != 0) {
                        Actions.applyDownscaleAndFps(game, ds, fps)
                    }
                    val res = sp.getInt("res_$game", 0)
                    if (res in 55..99) {
                        Actions.setResolution(res)
                    }
                }

                // 60-Second Watchdog Loop execution
                val now = System.currentTimeMillis()
                if (now - watchdogTimer >= 60_000L) {
                    watchdogTimer = now
                    Actions.watchdogTick(game)
                    NativeTools.reportAdpfWork(8000000L)
                }
            } else if (isGameActive) {
                consecutiveMissing++
                // If game is not in foreground for 3 consecutive checks (~15-18 seconds)
                if (consecutiveMissing >= 3) {
                    Actions.revertAll(game)
                    NativeTools.stopAdpfSession()
                    NativeTools.setWifiLowLatency(this@AutoService, false)
                    isGameActive = false
                    consecutiveMissing = 0
                }
            }

            delay(if (isGameActive) 6000L else 4000L)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME = "game"
        private const val CHANNEL = "auto_watchdog_channel"
    }
}
