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
            .setContentTitle("HyperGameBooster Smart Watchdog")
            .setContentText("Monitoring $game (Auto-Boost & Auto-Restore Active)")
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
            Actions.restrictPowerKeeperAndJoyose()
            runSmartWatchdog(game)
        }
        return START_NOT_STICKY
    }

    private suspend fun runSmartWatchdog(game: String) {
        var wasGameRunning = false
        var watchdogTimer = 0L

        while (currentCoroutineContext().isActive) {
            val pidOut = Sh.run("pidof $game").trim()
            val isGameRunning = pidOut.isNotEmpty() && pidOut.split(Regex("\\s+")).any { it.all(Char::isDigit) }

            if (isGameRunning) {
                if (!wasGameRunning) {
                    wasGameRunning = true

                    // 1. Force Unrestricted Battery & Background Power + Full Esports Mode
                    Actions.applyUnrestrictedPowerPolicy(game)
                    Actions.esportsMode(game, packageName)
                    NativeTools.startAdpfSession(this@AutoService, 8333333L)
                    NativeTools.setWifiLowLatency(this@AutoService, true)

                    // 2. Read Per-Game SharedPreferences & apply customized profile + Persistent GFX
                    val sp = getSharedPreferences("hgb_profiles", Context.MODE_PRIVATE)
                    val ds = sp.getString("ds_$game", "0.75") ?: "0.75"
                    val fps = sp.getInt("fps_$game", 120)
                    val res = sp.getString("res_$game", "1280x720") ?: "1280x720"
                    val qual = sp.getString("qual_$game", "Smooth") ?: "Smooth"
                    val potato = sp.getString("potato_$game", "Off") ?: "Off"
                    val simpleShader = sp.getBoolean("simple_shader_$game", false)
                    val shadow = sp.getBoolean("shadow_$game", false)
                    val msaa = sp.getBoolean("msaa_$game", false)
                    val sound = sp.getString("sound_$game", "Ultra") ?: "Ultra"
                    val gpuExt = sp.getString("gpu_ext_$game", "Apply All") ?: "Apply All"
                    val satMode = sp.getString("sat_$game", "eagle_eye") ?: "eagle_eye"
                    val bypassCharge = sp.getBoolean("bypass_$game", false)
                    val bulletReg = sp.getBoolean("bullet_reg_$game", true)
                    val rendererMode = sp.getString("renderer_$game", "skiagl") ?: "skiagl"
                    val wideFov = sp.getBoolean("wide_fov_$game", false)

                    Actions.applyPgtGraphicsConfig(
                        game = game,
                        resolution = res,
                        quality = qual,
                        fps = fps,
                        potatoLevel = potato,
                        simpleShader = simpleShader,
                        shadowEnabled = shadow,
                        msaaEnabled = msaa,
                        soundQuality = sound,
                        gpuExtension = gpuExt,
                        readOnlyLock = true
                    )

                    Actions.applyDownscaleAndFps(game, ds, fps)
                    Actions.applyCamperFilters(satMode)
                    if (bypassCharge) {
                        Actions.setBypassCharging(true)
                    }
                    if (bulletReg) {
                        Actions.applyBulletRegistrationMode(game, true, rendererMode)
                    }
                    if (wideFov) {
                        Actions.applyMaintainYFov(game, true)
                    }

                    // 3. Pin process to Prime CPU cores immediately & lock 120Hz display mode
                    Actions.pinGameProcess(game)
                    Actions.lockHardware120HzDisplayMode()
                }

                // 60-Second Watchdog Tick
                val now = System.currentTimeMillis()
                if (now - watchdogTimer >= 60_000L) {
                    watchdogTimer = now
                    Sh.run("am force-stop com.xiaomi.joyose 2>/dev/null; am force-stop com.miui.powerkeeper 2>/dev/null")
                    Actions.watchdogTick(game)
                    NativeTools.reportAdpfWork(8000000L)
                }
            } else if (wasGameRunning) {
                // Game closed! Restore stock settings automatically
                Actions.revertAll(game)
                NativeTools.stopAdpfSession()
                NativeTools.setWifiLowLatency(this@AutoService, false)
                wasGameRunning = false
            }

            delay(if (wasGameRunning) 5000L else 3000L)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME = "game"
        private const val CHANNEL = "auto_watchdog_v6"
    }
}
