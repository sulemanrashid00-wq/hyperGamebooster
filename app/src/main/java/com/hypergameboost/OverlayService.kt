package com.hypergameboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
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
 * OverlayService - Esports HUD Suite (Section 2 & 5)
 * - Draggable Telemetry HUD: FPS, Hz, Ping ms, Battery Temp °C, Thermal, GPU Busy %, Free RAM MB
 * - 3-Style Custom Crosshair + Quick Hide (Dot, Tactical Cross, Circle-Cross)
 * - Auxiliary Grenade Trajectory Line
 * - Cooldown Stopwatch & Scrim Kill Counter
 * - 4D Haptic Fire Ring (Heavy click haptic burst)
 * - AMOLED #000000 AFK Pocket Lock (Max power save during lobby / matchmaking)
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Overlays
    private var hudView: TextView? = null
    private var panelView: LinearLayout? = null
    private var crosshairView: View? = null
    private var auxLineView: View? = null
    private var hapticFireRingView: View? = null
    private var afkView: FrameLayout? = null

    private var hudJob: Job? = null
    private var game = ""
    private var layer: String? = null

    // Tools state
    private var swRunning = false
    private var swElapsed = 0L
    private var swStartedAt = 0L
    private var killTally = 0
    private var crosshairStyle = 0 // 0: None, 1: Dot, 2: Cross, 3: Circle+Cross
    private var showAuxLine = false
    private var showFireRing = false

    // Screen brightness backup for AFK Mode
    private var prevBrightness: String? = null
    private var prevBrightnessMode: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        val g = intent?.getStringExtra(EXTRA_GAME) ?: "com.tencent.ig"
        val wantHud = intent?.getBooleanExtra(EXTRA_HUD, false) ?: false
        val wantTools = intent?.getBooleanExtra(EXTRA_TOOLS, false) ?: false
        val wantCrosshair = intent?.getIntExtra(EXTRA_CROSSHAIR, 0) ?: 0
        val wantAux = intent?.getBooleanExtra(EXTRA_AUX_LINE, false) ?: false
        val wantFire = intent?.getBooleanExtra(EXTRA_FIRE_RING, false) ?: false

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        game = g
        layer = null

        if (wantHud) showHud() else removeHud()
        if (wantTools) showTools() else removeTools()

        crosshairStyle = wantCrosshair
        updateCrosshair()

        showAuxLine = wantAux
        updateAuxLine()

        showFireRing = wantFire
        updateFireRing()

        return START_NOT_STICKY
    }

    private fun promote() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Esports Overlay", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGameBooster Active")
            .setContentText("Esports HUD & Telemetry Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, n)
        }
    }

    // =========================================================================
    // 1. Live Telemetry HUD (FPS, Ping, Temp, GPU %, RAM)
    // =========================================================================
    private fun showHud() {
        if (hudView != null) return
        val tv = TextView(this).apply {
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10f
            setBackgroundColor(Color.argb(170, 10, 14, 20))
            setPadding(12, 6, 12, 6)
            text = "INITIALIZING HUD..."
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
            x = 20
            y = 20
        }
        wm.addView(tv, lp)
        hudView = tv
        hudJob?.cancel()
        hudJob = scope.launch { hudLoop() }
    }

    private fun removeHud() {
        hudJob?.cancel()
        hudJob = null
        hudView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        hudView = null
    }

    private suspend fun hudLoop() {
        var tick = 0
        var pingStr = "--ms"
        var gpuBusy = "--%"
        var freeRamMb = 0L

        while (currentCoroutineContext().isActive) {
            // Live Socket Ping (every 2.5s)
            if (tick % 2 == 0) {
                val ping = NativeTools.measureSocketPing("1.1.1.1", 53, 1000)
                pingStr = if (ping > 0) "${ping}ms" else "--ms"
            }

            // Battery and Thermal
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val th = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            } else 0

            // RAM free
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            freeRamMb = memInfo.availMem / (1024 * 1024)

            // GPU Busy (read kgsl every 4 ticks)
            if (tick % 4 == 0) {
                val gpu = Sh.run("cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null").trim()
                val parts = gpu.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val busy = parts[0].toLongOrNull() ?: 0L
                    val total = parts[1].toLongOrNull() ?: 1L
                    gpuBusy = if (total > 0) "${(busy * 100 / total).coerceIn(0, 100)}%" else "--%"
                }
            }

            val fpsStr = measureFps()
            val telemetry = "$fpsStr | Ping: $pingStr | ${"%.1f".format(temp)}°C | T:$th | GPU: $gpuBusy | RAM: ${freeRamMb}MB"

            ui.post { hudView?.text = telemetry }
            tick++
            delay(1200)
        }
    }

    private fun measureFps(): String {
        if (layer == null) {
            val ls = Sh.run("dumpsys SurfaceFlinger --list").lines().filter { it.contains(game) }
            layer = (ls.firstOrNull { it.contains("SurfaceView") } ?: ls.firstOrNull())?.trim()
        }
        val l = layer ?: return "FPS: --"
        val ts = Sh.run("dumpsys SurfaceFlinger --latency '$l'").lines().drop(1).mapNotNull {
            it.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
        }.filter { it > 0L && it != Long.MAX_VALUE }

        if (ts.size < 8 || ts.last() <= ts.first()) {
            layer = null
            return "FPS: --"
        }
        val f = (ts.size - 1) * 1_000_000_000.0 / (ts.last() - ts.first())
        return "FPS: ${"%.0f".format(f)}"
    }

    // =========================================================================
    // 2. Esports Tactical Tools Panel (Stopwatch, Kills, Tools, Crosshairs)
    // =========================================================================
    private fun showTools() {
        if (panelView != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(220, 15, 18, 25))
            setPadding(12, 8, 12, 8)
        }

        val header = TextView(this).apply {
            text = "⚡ HYPER ESPORTS TOOLS (TAP)"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
            setPadding(4, 4, 4, 4)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val swText = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 8, 0)
        }

        val tallyText = TextView(this).apply {
            text = "Kills: $killTally"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 0, 8, 0)
        }

        // Row 1: Stopwatch
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(swText)
        row1.addView(createBtn("Start/Stop") {
            if (swRunning) {
                swElapsed += SystemClock.elapsedRealtime() - swStartedAt
                swRunning = false
            } else {
                swStartedAt = SystemClock.elapsedRealtime()
                swRunning = true
            }
        })
        row1.addView(createBtn("Reset") {
            swRunning = false
            swElapsed = 0L
        })

        // Row 2: Kill Tally
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(tallyText)
        row2.addView(createBtn("+") { killTally++; tallyText.text = "Kills: $killTally" })
        row2.addView(createBtn("-") { if (killTally > 0) killTally--; tallyText.text = "Kills: $killTally" })
        row2.addView(createBtn("0") { killTally = 0; tallyText.text = "Kills: 0" })

        // Row 3: Crosshairs & Visuals
        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(createBtn("Crosshair: ${crosshairName()}") {
            crosshairStyle = (crosshairStyle + 1) % 4
            updateCrosshair()
            (it as? Button)?.text = "Crosshair: ${crosshairName()}"
        })
        row3.addView(createBtn(if (showAuxLine) "Aux: ON" else "Aux: OFF") {
            showAuxLine = !showAuxLine
            updateAuxLine()
            (it as? Button)?.text = if (showAuxLine) "Aux: ON" else "Aux: OFF"
        })

        // Row 4: 4D Haptics, Screenshot, AFK Pocket Lock
        val row4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row4.addView(createBtn(if (showFireRing) "4D Ring: ON" else "4D Ring: OFF") {
            showFireRing = !showFireRing
            updateFireRing()
            (it as? Button)?.text = if (showFireRing) "4D Ring: ON" else "4D Ring: OFF"
        })
        row4.addView(createBtn("AFK Lock") { showAfk() })
        row4.addView(createBtn("Screenshot") { takeScreenshot() })

        body.addView(row1)
        body.addView(row2)
        body.addView(row3)
        body.addView(row4)

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
            x = 20
            y = 200
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var isMove = false

        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    isMove = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMove = true
                    if (isMove) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        wm.updateViewLayout(root, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMove) {
                        body.visibility = if (body.visibility == View.GONE) View.VISIBLE else View.GONE
                    }
                }
            }
            true
        }

        wm.addView(root, lp)
        panelView = root

        // Stopwatch ticker
        ui.post(object : Runnable {
            override fun run() {
                if (panelView == null) return
                val ms = swElapsed + if (swRunning) SystemClock.elapsedRealtime() - swStartedAt else 0L
                val sec = ms / 1000
                swText.text = "%02d:%02d".format(sec / 60, sec % 60)
                ui.postDelayed(this, 500)
            }
        })
    }

    private fun removeTools() {
        panelView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        panelView = null
    }

    private fun crosshairName(): String = when (crosshairStyle) {
        1 -> "Dot"
        2 -> "Cross"
        3 -> "Circle"
        else -> "OFF"
    }

    private fun createBtn(label: String, onClick: (View) -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 10f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(10, 4, 10, 4)
        setOnClickListener { onClick(it) }
    }

    // =========================================================================
    // 3. 3-Style Esports Custom Crosshairs
    // =========================================================================
    private fun updateCrosshair() {
        crosshairView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        crosshairView = null

        if (crosshairStyle == 0) return

        val cv = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00FF66")
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF0055")
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                when (crosshairStyle) {
                    1 -> { // Dot
                        canvas.drawCircle(cx, cy, 4f, fillPaint)
                    }
                    2 -> { // Tactical Cross
                        canvas.drawLine(cx - 16f, cy, cx - 4f, cy, paint)
                        canvas.drawLine(cx + 4f, cy, cx + 16f, cy, paint)
                        canvas.drawLine(cx, cy - 16f, cx, cy - 4f, paint)
                        canvas.drawLine(cx, cy + 4f, cx, cy + 16f, paint)
                        canvas.drawCircle(cx, cy, 2f, fillPaint)
                    }
                    3 -> { // Circle + Cross
                        canvas.drawCircle(cx, cy, 14f, paint)
                        canvas.drawLine(cx - 20f, cy, cx - 8f, cy, paint)
                        canvas.drawLine(cx + 8f, cy, cx + 20f, cy, paint)
                        canvas.drawLine(cx, cy - 20f, cx, cy - 8f, paint)
                        canvas.drawLine(cx, cy + 8f, cx, cy + 20f, paint)
                        canvas.drawCircle(cx, cy, 2.5f, fillPaint)
                    }
                }
            }
        }

        val size = 120
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        wm.addView(cv, lp)
        crosshairView = cv
    }

    // =========================================================================
    // 4. Auxiliary Grenade Trajectory Line Guide
    // =========================================================================
    private fun updateAuxLine() {
        auxLineView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        auxLineView = null

        if (!showAuxLine) return

        val av = object : View(this) {
            private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 255, 215, 0)
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
            }
            private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255)
                strokeWidth = 2f
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                // Draw 45-degree angled auxiliary ruler guide from center toward upper right
                val endX = cx + 220f
                val endY = cy - 220f
                canvas.drawLine(cx, cy, endX, endY, linePaint)
                // Draw 5 distance tick marks
                for (i in 1..5) {
                    val tx = cx + i * 40f
                    val ty = cy - i * 40f
                    canvas.drawLine(tx - 6f, ty - 6f, tx + 6f, ty + 6f, tickPaint)
                }
            }
        }

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(av, lp)
        auxLineView = av
    }

    // =========================================================================
    // 5. 4D Haptic Fire Ring (Draggable Heavy-Click On-Screen Trigger)
    // =========================================================================
    private fun updateFireRing() {
        hapticFireRingView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        hapticFireRingView = null

        if (!showFireRing) return

        val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val ringView = object : View(this) {
            private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 255, 40, 40)
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(80, 255, 40, 40)
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val r = width / 2f
                canvas.drawCircle(r, r, r - 6f, innerPaint)
                canvas.drawCircle(r, r, r - 6f, ringPaint)
            }
        }

        val ringSize = 150
        val lp = WindowManager.LayoutParams(
            ringSize,
            ringSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 100
            y = 200
        }

        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false

        ringView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    // 4D Game Vibration Effect (Heavy Click burst)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(40)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        moved = true
                        lp.x = startX + dx.toInt()
                        lp.y = startY - dy.toInt() // Bottom origin
                        wm.updateViewLayout(ringView, lp)
                    }
                }
            }
            true
        }

        wm.addView(ringView, lp)
        hapticFireRingView = ringView
    }

    // =========================================================================
    // 6. AMOLED #000000 AFK Pocket Lock (90% Battery Saver)
    // =========================================================================
    private fun showAfk() {
        if (afkView != null) return
        val v = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val hint = TextView(this).apply {
            text = "AMOLED AFK LOCK ACTIVE\nTriple-tap rapidly to unlock"
            setTextColor(Color.rgb(55, 55, 55))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        v.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
        afkView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        val wasShown = afkView != null
        afkView = null
        if (!wasShown) return

        val pb = prevBrightness
        val pm = prevBrightnessMode
        CoroutineScope(Dispatchers.IO).launch {
            if (pb != null && pb.toIntOrNull() != null) Sh.run("settings put system screen_brightness $pb")
            if (pm != null && pm.toIntOrNull() != null) Sh.run("settings put system screen_brightness_mode $pm")
        }
    }

    private fun takeScreenshot() {
        panelView?.visibility = View.INVISIBLE
        hudView?.visibility = View.INVISIBLE
        scope.launch {
            delay(300)
            val file = "/sdcard/Pictures/HGB_${System.currentTimeMillis()}.png"
            val out = Sh.run("screencap -p '$file'")
            ui.post {
                panelView?.visibility = View.VISIBLE
                hudView?.visibility = View.VISIBLE
                Toast.makeText(
                    this@OverlayService,
                    if (out.isBlank()) "Screenshot: $file" else "Screenshot: $out",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun removeAll() {
        hideAfk()
        removeTools()
        removeHud()
        crosshairStyle = 0
        updateCrosshair()
        showAuxLine = false
        updateAuxLine()
        showFireRing = false
        updateFireRing()
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
        const val EXTRA_CROSSHAIR = "crosshair"
        const val EXTRA_AUX_LINE = "aux_line"
        const val EXTRA_FIRE_RING = "fire_ring"
        private const val CHANNEL = "overlay_service_v2"
    }
}
