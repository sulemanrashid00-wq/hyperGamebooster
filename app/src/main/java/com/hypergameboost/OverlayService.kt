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
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    @Volatile var style: Int = 0 // 0: Off, 1: Dot, 2: Cross, 3: Circle+Cross, 4: T-Cross, 5: Chevron, 6: Rangefinder
    @Volatile var rfTargetX: Int = 1800
    @Volatile var rfTargetY: Int = 600
    @Volatile var magnifierScale: Float = 2.5f
    @Volatile var floatingAppPkg: String = "com.whatsapp"

    // ASUS ROG & Black Shark Hardware Volume AirTriggers
    @Volatile var isVolumeTriggerEnabled: Boolean = false
    @Volatile var l1TargetX: Int = 350 // Default Scope/Aim location
    @Volatile var l1TargetY: Int = 450
    @Volatile var r1TargetX: Int = 1800 // Default Fire button location
    @Volatile var r1TargetY: Int = 600
    @Volatile var showTriggerPins: Boolean = false
}

/**
 * OverlayService - Esports HUD Suite & RedMagic Cyber Gaming Dock (v6.0 Esports Master)
 * 1. Real-Time Telemetry HUD: Real FPS, 1% Low, Frame-Time (ms), Battery Watts, Socket Ping, Sonic Threat
 * 2. RedMagic Sliding Cyber Dock: Expandable edge handle over PUBG with VIP quick toggles
 * 3. ASUS ROG Hardware Volume L1/R1 AirTriggers & Draggable On-Screen Pins
 * 4. ASUS ROG DMR Rapid-Fire Floating Macro (⚡RF trigger button)
 * 5. 6 Pro Crosshair Styles (Dot, Cross, Circle+Dot, T-Cross, Chevron, Rangefinder) + RGB switcher
 * 6. Tactical Audio Hunter Quick Selector (Footstep, Gunshot, Vehicle, Pro Flat)
 * 7. Freeform Mini-App Launcher (WhatsApp/Chrome PiP)
 * 8. AMOLED #000000 AFK Pocket Lock & Realme 45s Flash Return countdown
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Overlays
    private var hudContainerView: View? = null
    private var hudView: TextView? = null
    private var dockHandleView: TextView? = null
    private var dockExpandedView: LinearLayout? = null
    private var crosshairView: View? = null
    private var auxLineView: View? = null
    private var hapticFireRingView: View? = null
    private var rfMacroButtonView: View? = null
    private var afkView: FrameLayout? = null
    private var l1PinView: View? = null
    private var r1PinView: View? = null

    private var hudJob: Job? = null
    private var rfJob: Job? = null
    private var game = ""
    private var layer: String? = null

    // Tools state
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
        if (wantTools) showCyberDock() else removeCyberDock()

        CrosshairSettings.style = wantCrosshair
        updateCrosshair()

        showAuxLine = wantAux
        updateAuxLine()

        showFireRing = wantFire
        updateFireRing()

        updateTriggerPins()

        return START_NOT_STICKY
    }

    private fun promote() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Esports Overlay", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("HyperGameBooster v6.0 Active")
            .setContentText("RedMagic Cyber Dock & AirTriggers Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, n)
        }
    }

    private fun triggerHaptic(ms: Long = 30) {
        try {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(ms)
            }
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // 1. Live Telemetry HUD (Live FPS, 1% Low, Frame-Time, Power Watts, Sonic Radar)
    // =========================================================================
    private fun showHud() {
        if (hudContainerView != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(215, 8, 12, 17))
            setPadding(14, 6, 10, 6)
        }

        val tv = TextView(this).apply {
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10f
            setPadding(0, 0, 10, 0)
            text = "⚡ ESPORTS TELEMETRY ACTIVE"
        }
        container.addView(tv)

        val fixBtn = Button(this).apply {
            text = "⚡FIX"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 9f
            setBackgroundColor(Color.argb(230, 20, 32, 48))
            setPadding(8, 0, 8, 0)
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                triggerHaptic(30)
                text = "⚡..."
                scope.launch {
                    val res = Actions.quickLagAndPingFlush(game)
                    ui.post {
                        text = "⚡OK!"
                        Toast.makeText(this@OverlayService, res, Toast.LENGTH_SHORT).show()
                    }
                    delay(1000)
                    ui.post { text = "⚡FIX" }
                }
            }
        }
        container.addView(fixBtn)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 24
        }
        wm.addView(container, lp)
        hudContainerView = container
        hudView = tv
        hudJob?.cancel()
        hudJob = scope.launch { hudLoop() }
    }

    private fun removeHud() {
        hudJob?.cancel()
        hudJob = null
        hudContainerView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        hudContainerView = null
        hudView = null
    }

    private suspend fun hudLoop() {
        var tick = 0
        var pingStr = "--ms"
        var lastFpsReport = FpsReport(120.0, 114.0, 8.3)

        while (currentCoroutineContext().isActive) {
            if (tick % 2 == 0) {
                val ping = NativeTools.measureSocketPing("1.1.1.1", 53, 1000)
                pingStr = if (ping > 0) "${ping}ms" else "--ms"
            }

            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val powerDrain = NativeTools.getBatteryPowerDrain(this@OverlayService)

            val report = measureRealFps()
            if (report.liveFps > 0.0) {
                lastFpsReport = report
            }

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
                        v.setTextColor(Color.parseColor("#FF1744"))
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
    // 2. RedMagic Sliding Cyber VIP Gaming Dock (Handle + Sliding Panel)
    // =========================================================================
    private fun showCyberDock() {
        if (dockHandleView != null) return

        // Subtle edge pill handle (Default on right edge)
        val handle = TextView(this).apply {
            text = "⚡"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(220, 10, 16, 24))
            setPadding(12, 20, 12, 20)
        }

        val handleLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var isMove = false

        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = handleLp.x
                    startY = handleLp.y
                    isMove = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMove = true
                    if (isMove) {
                        handleLp.x = (startX + dx).toInt().coerceAtLeast(0)
                        handleLp.y = (startY + dy).toInt().coerceAtLeast(0)
                        wm.updateViewLayout(handle, handleLp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMove) {
                        toggleExpandedDock(handleLp.y)
                    }
                }
            }
            true
        }

        wm.addView(handle, handleLp)
        dockHandleView = handle
    }

    private fun toggleExpandedDock(anchorY: Int) {
        if (dockExpandedView != null) {
            removeExpandedDock()
            return
        }

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(240, 9, 13, 20))
            setPadding(14, 12, 14, 12)
        }

        val header = TextView(this).apply {
            text = "⚡ REDMAGIC VIP GAME SPACE"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            setPadding(4, 4, 4, 8)
        }
        dock.addView(header)

        // Row 1: Fast RAM Boost & Sniper Zoom
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createCyberBtn("⚡ Boost RAM") {
            scope.launch {
                Actions.compactMemoryAndPurgeCaches()
                ui.post { Toast.makeText(this@OverlayService, "⚡ RAM Compacted & Cache Dropped!", Toast.LENGTH_SHORT).show() }
            }
        })
        row1.addView(createCyberBtn(if (Actions.isMagnifierOn) "🔍 Zoom: ON" else "🔍 Zoom: OFF") {
            scope.launch {
                val next = !Actions.isMagnifierOn
                Actions.setMagnifier(next, CrosshairSettings.magnifierScale)
                ui.post { (it as? Button)?.text = if (next) "🔍 Zoom: ON" else "🔍 Zoom: OFF" }
            }
        })
        dock.addView(row1)

        // Row 2: Volume L1/R1 AirTriggers & Pin Mover
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(createCyberBtn(if (CrosshairSettings.isVolumeTriggerEnabled) "🎮 Vol L1/R1: ON" else "🎮 Vol L1/R1: OFF") {
            CrosshairSettings.isVolumeTriggerEnabled = !CrosshairSettings.isVolumeTriggerEnabled
            (it as? Button)?.text = if (CrosshairSettings.isVolumeTriggerEnabled) "🎮 Vol L1/R1: ON" else "🎮 Vol L1/R1: OFF"
            Toast.makeText(this@OverlayService, "Volume Triggers: ${if (CrosshairSettings.isVolumeTriggerEnabled) "ACTIVE" else "OFF"}", Toast.LENGTH_SHORT).show()
        })
        row2.addView(createCyberBtn(if (CrosshairSettings.showTriggerPins) "📍 Pins: ON" else "📍 Pins: OFF") {
            CrosshairSettings.showTriggerPins = !CrosshairSettings.showTriggerPins
            updateTriggerPins()
            (it as? Button)?.text = if (CrosshairSettings.showTriggerPins) "📍 Pins: ON" else "📍 Pins: OFF"
        })
        dock.addView(row2)

        // Row 3: Rapid-Fire Macro & Crosshair Style
        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(createCyberBtn(if (showRfMacro) "⚡ RF: ON" else "⚡ RF: OFF") {
            showRfMacro = !showRfMacro
            updateRfMacroButton()
            (it as? Button)?.text = if (showRfMacro) "⚡ RF: ON" else "⚡ RF: OFF"
        })
        row3.addView(createCyberBtn("🎯 Reticle: ${crosshairName()}") {
            CrosshairSettings.style = (CrosshairSettings.style + 1) % 7
            updateCrosshair()
            (it as? Button)?.text = "🎯 Reticle: ${crosshairName()}"
        })
        dock.addView(row3)

        // Row 4: Floating Mini-App & 45s Flash Return
        val row4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row4.addView(createCyberBtn("📱 Mini App") {
            scope.launch { Actions.launchFloatingMiniApp(CrosshairSettings.floatingAppPkg) }
        })
        row4.addView(createCyberBtn("⏳ 45s Lobby") {
            startService(Intent(this, LobbyTimerService::class.java).putExtra(LobbyTimerService.EXTRA_GAME, game))
        })
        dock.addView(row4)

        // Row 5: AMOLED AFK Lock & Tactical Audio Preset
        val row5 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row5.addView(createCyberBtn("🌙 AFK Lock") { showAfk() })
        row5.addView(createCyberBtn("🎧 Audio: ${audioPresetName()}") {
            val next = (AudioTest.activePreset + 1) % 5
            val msg = AudioTest.applyPreset(next)
            (it as? Button)?.text = "🎧 Audio: ${audioPresetName()}"
            Toast.makeText(this@OverlayService, msg, Toast.LENGTH_SHORT).show()
        })
        dock.addView(row5)

        // Row 6: Screenshot & Close
        val row6 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row6.addView(createCyberBtn("📸 Screenshot") { takeScreenshot() })
        row6.addView(createCyberBtn("❌ Close") { removeExpandedDock() })
        dock.addView(row6)

        val dockLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 70
            y = (anchorY - 50).coerceAtLeast(40)
        }

        wm.addView(dock, dockLp)
        dockExpandedView = dock
    }

    private fun removeExpandedDock() {
        dockExpandedView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        dockExpandedView = null
    }

    private fun removeCyberDock() {
        removeExpandedDock()
        dockHandleView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        dockHandleView = null
    }

    private fun audioPresetName(): String = when (AudioTest.activePreset) {
        1 -> "Footstep"
        2 -> "Gunshot"
        3 -> "Vehicle"
        4 -> "Pro Flat"
        else -> "Stock"
    }

    private fun crosshairName(): String = when (CrosshairSettings.style) {
        1 -> "Dot"
        2 -> "Cross"
        3 -> "Circle"
        4 -> "T-Cross"
        5 -> "Chevron"
        6 -> "Sniper"
        else -> "OFF"
    }

    private fun createCyberBtn(label: String, onClick: (View) -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 10f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(190, 20, 28, 42))
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(12, 6, 12, 6)
        setOnClickListener { onClick(it) }
    }

    // =========================================================================
    // 3. Draggable On-Screen Pins for Volume L1 (Aim) & R1 (Fire)
    // =========================================================================
    fun updateTriggerPins() {
        l1PinView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        r1PinView?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        l1PinView = null
        r1PinView = null

        if (!CrosshairSettings.showTriggerPins) return

        // Pin L1 (AIM)
        val l1View = TextView(this).apply {
            text = "L1:AIM"
            textSize = 11f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(8, 6, 8, 6)
        }
        val l1Lp = WindowManager.LayoutParams(
            110, 80,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = CrosshairSettings.l1TargetX
            y = CrosshairSettings.l1TargetY
        }
        makeDraggablePin(l1View, l1Lp) { x, y ->
            CrosshairSettings.l1TargetX = x
            CrosshairSettings.l1TargetY = y
        }
        wm.addView(l1View, l1Lp)
        l1PinView = l1View

        // Pin R1 (FIRE)
        val r1View = TextView(this).apply {
            text = "R1:FIRE"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF1744"))
            gravity = Gravity.CENTER
            setPadding(8, 6, 8, 6)
        }
        val r1Lp = WindowManager.LayoutParams(
            110, 80,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = CrosshairSettings.r1TargetX
            y = CrosshairSettings.r1TargetY
        }
        makeDraggablePin(r1View, r1Lp) { x, y ->
            CrosshairSettings.r1TargetX = x
            CrosshairSettings.r1TargetY = y
        }
        wm.addView(r1View, r1Lp)
        r1PinView = r1View
    }

    private fun makeDraggablePin(view: View, lp: WindowManager.LayoutParams, onMoved: (Int, Int) -> Unit) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    lp.x = (startX + dx).toInt().coerceAtLeast(0)
                    lp.y = (startY + dy).toInt().coerceAtLeast(0)
                    wm.updateViewLayout(view, lp)
                    onMoved(lp.x, lp.y)
                }
            }
            true
        }
    }

    // =========================================================================
    // 4. ASUS ROG "Rapid-Fire DMR Macro" Floating Button (⚡RF)
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
            setBackgroundColor(Color.argb(220, 255, 61, 0))
            setPadding(16, 12, 16, 12)
        }

        val lp = WindowManager.LayoutParams(
            130, 130,
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
                    rfJob?.cancel()
                    rfJob = scope.launch {
                        while (currentCoroutineContext().isActive) {
                            Shell.run("input tap ${CrosshairSettings.rfTargetX} ${CrosshairSettings.rfTargetY}")
                            delay(83) // 12 taps per second
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
    // 5. Pixel Fine-Tuned 6-Shape Crosshairs (Dot, Cross, Circle, T-Cross, Chevron, Rangefinder)
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
                    1 -> { // 1. Minimal Dot
                        canvas.drawCircle(cx, cy, (s / 4f).coerceAtLeast(3f), fillPaint)
                    }
                    2 -> { // 2. Tactical Cross
                        val half = s / 2f
                        val gap = (s / 6f).coerceAtLeast(3f)
                        canvas.drawLine(cx - half, cy, cx - gap, cy, strokePaint)
                        canvas.drawLine(cx + gap, cy, cx + half, cy, strokePaint)
                        canvas.drawLine(cx, cy - half, cx, cy - gap, strokePaint)
                        canvas.drawLine(cx, cy + gap, cx, cy + half, strokePaint)
                        canvas.drawCircle(cx, cy, 2f, fillPaint)
                    }
                    3 -> { // 3. Circle + Cross
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
                    4 -> { // 4. T-Cross (Headshot Focused)
                        val half = s / 2f
                        val gap = (s / 6f).coerceAtLeast(3f)
                        canvas.drawLine(cx - half, cy, cx - gap, cy, strokePaint)
                        canvas.drawLine(cx + gap, cy, cx + half, cy, strokePaint)
                        canvas.drawLine(cx, cy + gap, cx, cy + half, strokePaint)
                        canvas.drawCircle(cx, cy, 2f, fillPaint)
                    }
                    5 -> { // 5. Chevron / Diamond Reticle
                        val p = Path().apply {
                            moveTo(cx, cy - (s / 2.5f))
                            lineTo(cx + (s / 3f), cy + (s / 3f))
                            lineTo(cx, cy + (s / 6f))
                            lineTo(cx - (s / 3f), cy + (s / 3f))
                            close()
                        }
                        canvas.drawPath(p, fillPaint)
                    }
                    6 -> { // 6. Sniper Rangefinder
                        val half = s / 1.5f
                        canvas.drawLine(cx - half, cy, cx + half, cy, strokePaint)
                        canvas.drawLine(cx, cy - half, cx, cy + half, strokePaint)
                        for (i in 1..3) {
                            val dy = cy + (i * s / 4f)
                            val w = half / (i + 1)
                            canvas.drawLine(cx - w, dy, cx + w, dy, strokePaint)
                        }
                    }
                }
            }
        }

        val boxSize = (CrosshairSettings.sizePx * 2.5f).toInt().coerceAtLeast(100)
        val lp = WindowManager.LayoutParams(
            boxSize, boxSize,
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
    // 6. Auxiliary Trajectory & Haptic Ring
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
            ringSize, ringSize,
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
    // 7. AMOLED #000000 AFK Pocket Lock & Screenshots
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
            Sh.run("settings put system screen_brightness_mode 0\nsettings put system screen_brightness 1")
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
        dockExpandedView?.visibility = View.INVISIBLE
        dockHandleView?.visibility = View.INVISIBLE
        hudContainerView?.visibility = View.INVISIBLE
        scope.launch {
            delay(250)
            val file = "/sdcard/Pictures/HGB_${System.currentTimeMillis()}.png"
            val out = Sh.run("screencap -p '$file'")
            ui.post {
                dockExpandedView?.visibility = View.VISIBLE
                dockHandleView?.visibility = View.VISIBLE
                hudContainerView?.visibility = View.VISIBLE
                Toast.makeText(
                    this@OverlayService,
                    if (out.isBlank()) "📸 Saved: $file" else "Screenshot: $out",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun removeAll() {
        hideAfk()
        removeCyberDock()
        removeHud()
        CrosshairSettings.style = 0
        updateCrosshair()
        showAuxLine = false
        updateAuxLine()
        showFireRing = false
        updateFireRing()
        showRfMacro = false
        updateRfMacroButton()
        CrosshairSettings.showTriggerPins = false
        updateTriggerPins()
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
        private const val CHANNEL = "overlay_service_v6"
    }
}
