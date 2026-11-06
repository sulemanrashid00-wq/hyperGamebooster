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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Flash Return: game minimize karne par floating countdown bubble. Tap = game wapis. Lamba dabao = band. */
class LobbyTimerService : Service() {
    private var view: TextView? = null
    private var timer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val game = intent?.getStringExtra(EXTRA_GAME) ?: "com.tencent.ig"
        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 60) ?: 60
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Lobby timer", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CH).setContentTitle("Lobby timer chal raha hai")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(3, n)
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        show(game, seconds)
        return START_NOT_STICKY
    }

    private fun show(game: String, seconds: Int) {
        remove()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE); setBackgroundColor(0xCC000000.toInt()); textSize = 20f
            setPadding(32, 20, 32, 20); text = "${seconds}s"
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 24; y = 200 }
        tv.setOnClickListener {
            packageManager.getLaunchIntentForPackage(game)?.let { i -> i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i) }
            stopSelf()
        }
        tv.setOnLongClickListener { stopSelf(); true }
        wm.addView(tv, lp)
        view = tv
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(ms: Long) { tv.text = "${ms / 1000}s  (tap: game)" }
            override fun onFinish() { tv.text = "GO! tap" ; tv.setBackgroundColor(Color.parseColor("#CC2E7D32")) }
        }.start()
    }

    private fun remove() {
        timer?.cancel(); timer = null
        view?.let { try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
        view = null
    }

    override fun onDestroy() { remove(); super.onDestroy() }

    companion object {
        const val CH = "lobby"
        const val EXTRA_GAME = "game"
        const val EXTRA_SECONDS = "seconds"
    }
}
