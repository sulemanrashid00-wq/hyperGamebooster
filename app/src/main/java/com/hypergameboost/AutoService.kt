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
 * Auto mode: game foreground mein aate hi Esports Mode on, game se bahar jaate hi (kuch der baad)
 * sab kuch default par wapas. Shizuku chalte rehna zaroori hai.
 * Polling halki rakhi gayi hai (idle 4 sec, game ke dauran 8 sec) taake khud stutter na kare.
 */
class AutoService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val game = intent?.getStringExtra(EXTRA_GAME)
        if (game == null || game !in Games.known) {
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Auto mode", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGameBooster Auto mode")
            .setContentText("${Games.known[game]} ka intezaar...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch { watch(game) }
        return START_NOT_STICKY
    }

    private suspend fun watch(game: String) {
        var active = false
        var missing = 0
        while (currentCoroutineContext().isActive) {
            val top = Sh.run("dumpsys activity activities | grep -m 3 -E 'topResumedActivity|mResumedActivity'")
            val inGame = top.contains(game)
            if (inGame) {
                missing = 0
                if (!active) {
                    active = true
                    Actions.esportsMode(game, packageName)
                }
            } else if (active) {
                missing++
                if (missing >= 3) {
                    Actions.revertAll(game)
                    active = false
                    missing = 0
                }
            }
            delay(if (active) 8000L else 4000L)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME = "game"
        private const val CHANNEL = "auto_mode"
    }
}
