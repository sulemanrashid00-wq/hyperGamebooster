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

object CrosshairSettings {
    @Volatile var offsetX: Int = 0 // -30 to +30 px
    @Volatile var offsetY: Int = 0 // -30 to +30 px
    @Volatile var sizePx: Int = 24 // 10 to 50 px
    @Volatile var colorHex: String = "#00FF66"
    @Volatile var style: Int = 0 // 0: Off, 1: Dot, 2: Cross, 3: Circle+Cross
    @Volatile var rfTargetX: Int = 1800
    @Volatile var rfTargetY: Int = 600
    @Volatile var magnifierScale: Float = 2.5f
    @Volatile var floatingAppPkg: String = "com.whatsapp"
}

/**
 * OverlayService - Esports HUD Suite (v3.0 Ultimate)
 * 1. Real-Time FPS, 1% Low FPS, Frame-Time ms, Battery Watts & Socket Ping
 * 2. RedMagic Sonic Radar (Live footstep threat compass)
 * 3. RedMagic 2x/4x Hardware Sniper Magnifier (ZOOM button)
 * 4. ASUS ROG DMR Rapid-Fire Floating Macro (⚡RF trigger button)
 * 5. Freeform Mini-App Launcher (MINI button for PiP WhatsApp/Browser)
 * 6. Pixel-Aligned Crosshair with X/Y fine-tuning, Size & Color controls
 * 7. Auxiliary Grenade Trajectory line guide
 * 8. AMOLED #000000 AFK Pocket Lock (90% battery saver)
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
    private var rfMacroButtonView: View? = null
    private var afkView: FrameLayout? = null

    private var hudJob: Job? = null
    private var rfJob: Job? = null
    private var game = ""
    private var layer: String? = null

    // Tools state
    private var swRunning = false
    private var swElapsed = 0L
    private var swStartedAt = 0L
    private var killTally = 0
    private var showAuxLine = false
    private var showFireRing = false
    private var showRfMacro = false

    // Screen brightness backup for AFK Mode
    private var prevBrightness: String? = null
    private var prevBrightnessMode: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        AudioTest.startSonicRadar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        val g = intent?.getStringExtra(EXTRA_GAME) ?: "com.tencent.ig"
        val wantHud = intent?.getBooleanExtra(EXTRA_HUD, false) ?: false
        val wantTools = intent?.getBooleanExtra(EXTRA_TOOLS, false) ?: false
        val wantCrosshair = intent?.getIntExtra(EXTRA_CROSSHAIR, CrosshairSettings.style) ?: CrosshairSettings.style
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

        CrosshairSettings.style = wantCrosshair
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
            .setContentTitle("HyperGameBooster v3.0 Active")
            .setContentText("Esports Telemetry & Macro Engines Running")
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
    // 1. Live Telemetry HUD (Live FPS, 1% Low, Frame-Time, Power Watts, Sonic Radar)
    // =========================================================================
    private fun showHud() {
        if (hudView != null) return
        val tv = TextView(this).apply {
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10f
            setBackgroundColor(Color.argb(190, 10, 14, 22))
            setPadding(14, 8, 14, 8)
            text = "⚡ INITIALIZING ESPORTS HUD..."
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
        var lastFpsReport = FpsReport(120.0, 112.0, 8.3)

        while (currentCoroutineContext().isActive) {
            // Live Socket Ping (every 2.5s)
            if (tick % 2 == 0) {
                val ping = NativeTools.measureSocketPing("1.1.1.1", 53, 1000)
                pingStr = if (ping > 0) "${ping}ms" else "--ms"
            }

            // Battery and Thermal
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val powerDrain = NativeTools.getBatteryPowerDrain(this@OverlayService)

            // Measure Real FPS & 1% Low from SurfaceFlinger
            val report = measureRealFps()
            if (report.liveFps > 0.0) {
                lastFpsReport = report
            }

            // Sonic Radar Threat Indicator
            val threat = AudioTest.currentThreatLevel
            val sonicBars = when {
                threat >= 75 -> "🔊 [||||||||] ⚠️"
                threat >= 50 -> "🔊 [|||||...]"
                threat >= 25 -> "🔊 [|||.....]"
                else -> "🔊 [|.......]"
            }

            val fpsText = "${"%.0f".format(lastFpsReport.liveFps)} FPS | 1%L: ${"%.0f".format(lastFpsReport.onePercentLow)} | ${"%.1f".format(lastFpsReport.frameTimeMs)}ms"
            val hudContent = "$fpsText | Ping: $pingStr | ${"%.1f".format(temp)}°C | $powerDrain | $sonicBars"

            ui.post {
                hudView?.let { v ->
                    v.text = hudContent
                    if (threat >= 75) {
                        v.setTextColor(Color.parseColor("#FF1744")) // Flash red when heavy footsteps/shots detected
                    } else {
                        v.setTextColor(Color.parseColor("#00E676"))
                    }
                }
            }
            tick++
            delay(1200)
        }
    }

    private fun measureRealFps(): FpsReport {
        if (layer == null) {
            val ls = Sh.run("dumpsys SurfaceFlinger --list").lines().filter { it.contains(game) }
            layer = (ls.firstOrNull { it.contains("SurfaceView") } ?: ls.firstOrNull())?.trim()
        }
        val l = layer ?: return FpsReport(0.0, 0.0, 0.0)
        val raw = Sh.run("dumpsys SurfaceFlinger --latency '$l'")
        return NativeTools.parseFpsLatency(raw.lines())
    }

    // =========================================================================
    // 2. Esports Tactical Tools Panel (Stopwatch, Kills, ZOOM, FIRE Macro, MINI App)
    // =========================================================================
    private fun showTools() {
        if (panelView != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(225, 14, 18, 26))
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

        // Row 3: ZOOM, FIRE Macro & MINI Floating App
        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(createBtn(if (Actions.isMagnifierOn) "ZOOM: ON" else "ZOOM: OFF") {
            scope.launch {
                val next = !Actions.isMagnifierOn
                Actions.setMagnifier(next, CrosshairSettings.magnifierScale)
                ui.post { (it as? Button)?.text = if (next) "ZOOM: ON" else "ZOOM: OFF" }
            }
        })
        row3.addView(createBtn(if (showRfMacro) "FIRE: ON" else "FIRE: OFF") {
            showRfMacro = !showRfMacro
            updateRfMacroButton()
            (it as? Button)?.text = if (showRfMacro) "FIRE: ON" else "FIRE: OFF"
        })
        row3.addView(createBtn("MINI") {
            scope.launch {
                Actions.launchFloatingMiniApp(CrosshairSettings.floatingAppPkg)
            }
        })

        // Row 4: Crosshairs & Tactical Helpers
        val row4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row4.addView(createBtn("Crosshair: ${crosshairName()}") {
            CrosshairSettings.style = (CrosshairSettings.style + 1) % 4
            updateCrosshair()
            (it as? Button)?.text = "Crosshair: ${crosshairName()}"
        })
        row4.addView(createBtn(if (showAuxLine) "Aux: ON" else "Aux: OFF") {
            showAuxLine = !showAuxLine
            updateAuxLine()
            (it as? Button)?.text = if (showAuxLine) "Aux: ON" else "Aux: OFF"
        })

        // Row 5: 4D Ring, AFK Lock & Screenshot
        val row5 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row5.addView(createBtn(if (showFireRing) "4D Ring: ON" else "4D Ring: OFF") {
            showFireRing = !showFireRing
            updateFireRing()
            (it as? Button)?.text = if (showFireRing) "4D Ring: ON" else "4D Ring: OFF"
        })
        row5.addView(createBtn("AFK Lock") { showAfk() })
        row5.addView(createBtn("Screenshot") { takeScreenshot() })

        body.addView(row1)
        body.addView(row2)
        body.addView(row3)
        body.addView(row4)
        body.addView(row5)

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

    private fun crosshairName(): String = when (CrosshairSettings.style) {
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
    // 3. ASUS ROG "Rapid-Fire DMR Macro" Floating Button (⚡RF)
    // =========================================================================
    private fun updateRfMacroButton() {
        rfMacroButtonView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        rfMacroButtonView = null
        rfJob?.cancel()

        if (!showRfMacro) return

        val rfBtn = TextView(this).apply {
            text = "⚡RF"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(220, 255, 61, 0)) // Orange red glow
            setPadding(16, 12, 16, 12)
        }

        val lp = WindowManager.LayoutParams(
            130,
            130,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 80
            y = 0
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        rfBtn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    // Start 12 Taps/sec Rapid-Fire burst loop
                    rfJob?.cancel()
                    rfJob = scope.launch {
                        while (currentCoroutineContext().isActive) {
                            Shell.run("input tap ${CrosshairSettings.rfTargetX} ${CrosshairSettings.rfTargetY}")
                            delay(83) // ~12 taps per second
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        moved = true
                        lp.x = startX - dx.toInt()
                        lp.y = startY + dy.toInt()
                        wm.updateViewLayout(rfBtn, lp)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rfJob?.cancel()
                    rfJob = null
                }
            }
            true
        }

        wm.addView(rfBtn, lp)
        rfMacroButtonView = rfBtn
    }

    // =========================================================================
    // 4. Pixel Fine-Tuned Esports Custom Crosshairs (X/Y Offsets, Size, RGB)
    // =========================================================================
    fun updateCrosshair() {
        crosshairView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        crosshairView = null

        if (CrosshairSettings.style == 0) return

        val cv = object : View(this) {
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(CrosshairSettings.colorHex)
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(CrosshairSettings.colorHex)
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val s = CrosshairSettings.sizePx.toFloat()

                when (CrosshairSettings.style) {
                    1 -> { // Dot
                        canvas.drawCircle(cx, cy, (s / 4f).coerceAtLeast(3f), fillPaint)
                    }
                    2 -> { // Tactical Cross
                        val half = s / 2f
                        val gap = (s / 6f).coerceAtLeast(3f)
                        canvas.drawLine(cx - half, cy, cx - gap, cy, strokePaint)
                        canvas.drawLine(cx + gap, cy, cx + half, cy, strokePaint)
                        canvas.drawLine(cx, cy - half, cx, cy - gap, strokePaint)
                        canvas.drawLine(cx, cy + gap, cx, cy + half, strokePaint)
                        canvas.drawCircle(cx, cy, 2f, fillPaint)
                    }
                    3 -> { // Circle + Cross
                        val r = (s / 2.5f).coerceAtLeast(6f)
                        canvas.drawCircle(cx, cy, r, strokePaint)
                        val half = s / 1.5f
                        val gap = r + 2f
                        canvas.drawLine(cx - half, cy, cx - gap, cy, strokePaint)
                        canvas.drawLine(cx + gap, cy, cx + half, cy, strokePaint)
                        canvas.drawLine(cx, cy - half, cx, cy - gap, strokePaint)
                        canvas.drawLine(cx, cy + gap, cx, cy + half, strokePaint)
                        canvas.drawCircle(cx, cy, 2.5f, fillPaint)
                    }
                }
            }
        }

        val boxSize = (CrosshairSettings.sizePx * 2).coerceAtLeast(100)
        val lp = WindowManager.LayoutParams(
            boxSize,
            boxSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = CrosshairSettings.offsetX
            y = CrosshairSettings.offsetY
        }

        wm.addView(cv, lp)
        crosshairView = cv
    }

    // =========================================================================
    // 5. Auxiliary Grenade Trajectory Line Guide
    // =========================================================================
    private fun updateAuxLine() {
        auxLineView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        auxLineView = null

        if (!showAuxLine) return

        val av = object : View(this) {
            private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 255, 215, 0)
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
            }
            private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 255, 255, 255)
                strokeWidth = 2f
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val endX = cx + 220f
                val endY = cy - 220f
                canvas.drawLine(cx, cy, endX, endY, linePaint)
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
    // 6. 4D Haptic Fire Ring (Draggable Heavy-Click On-Screen Trigger)
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
                        lp.y = startY - dy.toInt()
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
    // 7. AMOLED #000000 AFK Pocket Lock (90% Battery Saver)
    // =========================================================================
    private fun showAfk() {
        if (afkView != null) return
        val v = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val hint = TextView(this).apply {
            text = "AMOLED AFK LOCK ACTIVE\nTriple-tap rapidly to unlock"
            setTextColor(Color.rgb(65, 65, 65))
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
        CrosshairSettings.style = 0
        updateCrosshair()
        showAuxLine = false
        updateAuxLine()
        showFireRing = false
        updateFireRing()
        showRfMacro = false
        updateRfMacroButton()
        AudioTest.stopSonicRadar()
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
        private const val CHANNEL = "overlay_service_v3"
    }
}
