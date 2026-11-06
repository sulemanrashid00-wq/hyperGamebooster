package com.hypergameboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Overlay pack: (1) HUD: FPS / temperature / thermal / ping, (2) Tools panel: stopwatch, kill counter,
 * screenshot, AFK black screen. Koi crosshair, aim-assist ya zoom/filter overlay nahi.
 * Overlay permission Shizuku se di jati hai: appops set <pkg> SYSTEM_ALERT_WINDOW allow
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var hudView: TextView? = null
    private var panelView: LinearLayout? = null
    private var afkView: FrameLayout? = null
    private var hudJob: Job? = null
    private var game = ""
    private var layer: String? = null

    private var swRunning = false
    private var swElapsed = 0L
    private var swStartedAt = 0L
    private var tally = 0

    private var prevBrightness: String? = null
    private var prevBrightnessMode: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote() // startForegroundService ke baad foreground hona zaroori hai
        val g = intent?.getStringExtra(EXTRA_GAME)
        val wantHud = intent?.getBooleanExtra(EXTRA_HUD, false) ?: false
        val wantTools = intent?.getBooleanExtra(EXTRA_TOOLS, false) ?: false
        if (g == null || g !in Games.known || !Settings.canDrawOverlays(this) || (!wantHud && !wantTools)) {
            removeAll()
            stopSelf()
            return START_NOT_STICKY
        }
        game = g
        layer = null
        if (wantHud) showHud() else removeHud()
        if (wantTools) showTools() else removeTools()
        return START_NOT_STICKY
    }

    private fun promote() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Overlay", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGameBooster Overlay")
            .setContentText("HUD / Tools chal rahe hain")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, n)
        }
    }

    // ---------------- HUD ----------------

    private fun showHud() {
        if (hudView != null) return
        val tv = TextView(this).apply {
            setTextColor(Color.rgb(120, 255, 120))
            textSize = 11f
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            setPadding(10, 4, 10, 4)
            text = "HUD..."
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 16
        }
        wm.addView(tv, lp)
        hudView = tv
        hudJob?.cancel()
        hudJob = scope.launch { hudLoop() }
    }

    private fun removeHud() {
        hudJob?.cancel()
        hudJob = null
        hudView?.let { try { wm.removeView(it) } catch (e: Throwable) { } }
        hudView = null
    }

    private suspend fun hudLoop() {
        var n = 0
        var ping = "-"
        while (currentCoroutineContext().isActive) {
            if (n % 5 == 0) {
                val p = Sh.run("ping -c 1 -W 1 1.1.1.1 | grep -o 'time=[0-9.]*'")
                ping = if (p.startsWith("time=")) p.removePrefix("time=") + "ms" else "-"
            }
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val th = if (Build.VERSION.SDK_INT >= 29) {
                (getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            } else {
                0
            }
            val text = fps() + "  " + "%.1f".format(temp) + "C  T" + th + "  Ping " + ping
            ui.post { hudView?.text = text }
            n++
            delay(2000)
        }
    }

    private fun fps(): String {
        if (layer == null) {
            val ls = Sh.run("dumpsys SurfaceFlinger --list").lines().filter { it.contains(game) }
            layer = (ls.firstOrNull { it.contains("SurfaceView") } ?: ls.firstOrNull())?.trim()
        }
        val l = layer ?: return "FPS n/a"
        val ts = Sh.run("dumpsys SurfaceFlinger --latency '$l'").lines().drop(1).mapNotNull {
            it.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
        }.filter { it > 0L && it != Long.MAX_VALUE }
        if (ts.size < 10 || ts.last() <= ts.first()) {
            layer = null
            return "FPS n/a"
        }
        val f = (ts.size - 1) * 1_000_000_000.0 / (ts.last() - ts.first())
        return "FPS " + "%.0f".format(f)
    }

    // ---------------- Tools panel ----------------

    private fun btn(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(14, 4, 14, 4)
        setOnClickListener { onClick() }
    }

    private fun label(t: String, size: Float = 13f): TextView = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        textSize = size
        setPadding(0, 0, 12, 0)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun showTools() {
        if (panelView != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200, 20, 20, 30))
            setPadding(10, 6, 10, 6)
        }
        val header = label("HGB  (drag / tap)", 11f)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val swText = label("00:00", 15f)
        val tallyText = label("Kills: 0", 15f)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(swText)
        row1.addView(btn("Start/Stop") {
            if (swRunning) {
                swElapsed += SystemClock.elapsedRealtime() - swStartedAt
                swRunning = false
            } else {
                swStartedAt = SystemClock.elapsedRealtime()
                swRunning = true
            }
        })
        row1.addView(btn("Reset") {
            swRunning = false
            swElapsed = 0L
        })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(tallyText)
        row2.addView(btn("+") { tally++; tallyText.text = "Kills: $tally" })
        row2.addView(btn("-") { if (tally > 0) tally--; tallyText.text = "Kills: $tally" })
        row2.addView(btn("0") { tally = 0; tallyText.text = "Kills: 0" })

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(btn("Screenshot") { screenshot() })
        row3.addView(btn("AFK") { showAfk() })
        row3.addView(btn("Close") { removeTools() })

        body.addView(row1)
        body.addView(row2)
        body.addView(row3)
        root.addView(header)
        root.addView(body)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 220
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        wm.updateViewLayout(root, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        body.visibility = if (body.visibility == View.GONE) View.VISIBLE else View.GONE
                    }
                }
            }
            true
        }

        wm.addView(root, lp)
        panelView = root

        // stopwatch ticker
        ui.post(object : Runnable {
            override fun run() {
                if (panelView == null) return
                val ms = swElapsed + if (swRunning) SystemClock.elapsedRealtime() - swStartedAt else 0L
                swText.text = fmt(ms)
                ui.postDelayed(this, 500)
            }
        })
    }

    private fun removeTools() {
        panelView?.let { try { wm.removeView(it) } catch (e: Throwable) { } }
        panelView = null
    }

    private fun screenshot() {
        panelView?.visibility = View.INVISIBLE
        hudView?.visibility = View.INVISIBLE
        scope.launch {
            delay(350)
            val f = "/sdcard/Pictures/HGB_${System.currentTimeMillis()}.png"
            val out = Sh.run("screencap -p '$f'")
            ui.post {
                panelView?.visibility = View.VISIBLE
                hudView?.visibility = View.VISIBLE
                Toast.makeText(
                    this@OverlayService,
                    if (out.isBlank()) "Screenshot: $f" else "Screenshot error: $out",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------- AFK black screen ----------------

    private fun showAfk() {
        if (afkView != null) return
        val v = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val hint = TextView(this).apply {
            text = "AFK mode: bahar nikalne ke liye 3 baar tez tap karein"
            setTextColor(Color.rgb(45, 45, 45))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        v.addView(
            hint,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        var taps = 0
        var last = 0L
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                val now = SystemClock.uptimeMillis()
                taps = if (now - last < 600) taps + 1 else 1
                last = now
                if (taps >= 3) hideAfk()
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(v, lp)
        afkView = v
        scope.launch {
            prevBrightnessMode = Sh.run("settings get system screen_brightness_mode")
            prevBrightness = Sh.run("settings get system screen_brightness")
            Sh.run("settings put system screen_brightness_mode 0")
            Sh.run("settings put system screen_brightness 1")
        }
    }

    private fun hideAfk() {
        afkView?.let { try { wm.removeView(it) } catch (e: Throwable) { } }
        val wasShown = afkView != null
        afkView = null
        if (!wasShown) return
        val pb = prevBrightness
        val pm = prevBrightnessMode
        // alag scope: service band hone par bhi brightness wapas aani chahiye
        CoroutineScope(Dispatchers.IO).launch {
            if (pb != null && pb.toIntOrNull() != null) Sh.run("settings put system screen_brightness $pb")
            if (pm != null && pm.toIntOrNull() != null) Sh.run("settings put system screen_brightness_mode $pm")
        }
    }

    private fun removeAll() {
        hideAfk()
        removeTools()
        removeHud()
    }

    override fun onDestroy() {
        removeAll()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME = "game"
        const val EXTRA_HUD = "hud"
        const val EXTRA_TOOLS = "tools"
        private const val CHANNEL = "overlay"
    }
}
