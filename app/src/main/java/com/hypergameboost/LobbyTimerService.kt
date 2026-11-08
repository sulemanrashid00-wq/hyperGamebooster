package com.hypergameboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Realme GT Mode Flash Return (Spawn Island Countdown Bubble)
 * Section 2 & 5:
 * Floating 45-second (or configurable) spawn-island countdown bubble that relaunches the game on tap.
 */
class LobbyTimerService : Service() {
    private var view: TextView? = null
    private var timer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val game = intent?.getStringExtra(EXTRA_GAME) ?: "com.tencent.ig"
        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 45) ?: 45

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Flash Return Timer", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CH)
            .setContentTitle("HyperGameBooster Flash Return")
            .setContentText("Spawn countdown active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(3, n)
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        show(game, seconds)
        return START_NOT_STICKY
    }

    private fun show(game: String, seconds: Int) {
        remove()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 20, 25, 35))
            textSize = 15f
            setPadding(28, 16, 28, 16)
            text = "⏳ ${seconds}s (Tap to Return)"
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
        }

        tv.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(game)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(launchIntent)
            } else {
                Sh.run("monkey -p $game -c android.intent.category.LAUNCHER 1")
            }
            stopSelf()
        }

        tv.setOnLongClickListener {
            stopSelf()
            true
        }

        wm.addView(tv, lp)
        view = tv

        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                val sec = ms / 1000
                tv.text = "⏳ ${sec}s (Tap to Return)"
                if (sec <= 5) {
                    tv.setTextColor(Color.parseColor("#FF1744"))
                }
            }

            override fun onFinish() {
                tv.text = "🔥 MATCH START! (Tap)"
                tv.setBackgroundColor(Color.argb(230, 0, 200, 83))
                tv.setTextColor(Color.WHITE)
            }
        }.start()
    }

    private fun remove() {
        timer?.cancel()
        timer = null
        view?.let {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {}
        }
        view = null
    }

    override fun onDestroy() {
        remove()
        super.onDestroy()
    }

    companion object {
        const val CH = "lobby_flash_return"
        const val EXTRA_GAME = "game"
        const val EXTRA_SECONDS = "seconds"
    }
}
