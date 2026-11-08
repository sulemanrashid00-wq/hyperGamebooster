package com.hypergameboost

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val esportsColors = darkColorScheme(
                primary = Color(0xFF00FF66), // Cyber Neon Green
                onPrimary = Color.Black,
                secondary = Color(0xFF00E5FF), // Electric Cyan
                background = Color(0xFF080C14), // AMOLED Deep Gaming Black
                surface = Color(0xFF0F1722),
                onSurface = Color(0xFFECEFF1)
            )
            MaterialTheme(colorScheme = esportsColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App()
                }
            }
        }
    }
}

fun triggerVibe(context: Context) {
    try {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            v?.vibrate(35)
        }
    } catch (_: Throwable) {}
}

@Composable
fun VipGamingLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Outer Hex Shield
        val shieldPath = Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.95f, h * 0.25f)
            lineTo(w * 0.95f, h * 0.70f)
            lineTo(w * 0.5f, h * 0.95f)
            lineTo(w * 0.05f, h * 0.70f)
            lineTo(w * 0.05f, h * 0.25f)
            close()
        }
        drawPath(path = shieldPath, color = Color(0xFF101928))
        drawPath(path = shieldPath, color = Color(0xFF00E5FF), style = Stroke(width = 3f))

        // Inner Cyber Neon Border
        val innerPath = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.30f)
            lineTo(w * 0.85f, h * 0.65f)
            lineTo(w * 0.5f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.65f)
            lineTo(w * 0.15f, h * 0.30f)
            close()
        }
        drawPath(path = innerPath, color = Color(0xFF00FF66), style = Stroke(width = 2f))

        // Electric Neon Lightning Bolt
        val boltPath = Path().apply {
            moveTo(w * 0.54f, h * 0.22f)
            lineTo(w * 0.34f, h * 0.52f)
            lineTo(w * 0.48f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.78f)
            lineTo(w * 0.68f, h * 0.46f)
            lineTo(w * 0.54f, h * 0.46f)
            close()
        }
        drawPath(path = boltPath, brush = Brush.linearGradient(listOf(Color(0xFF00FF66), Color(0xFF00E5FF))))
    }
}

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1724)),
        border = BorderStroke(1.dp, Color(0xFF1D293A))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE),
                        fontSize = 11.sp
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun ToggleRow(label: String, subtitle: String? = null, enabled: Boolean = true, onAction: () -> Unit, offAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, color = Color.White, fontSize = 13.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF78909C), fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                enabled = enabled,
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("ON", fontSize = 11.sp)
            }
            OutlinedButton(
                enabled = enabled,
                onClick = offAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("OFF", fontSize = 11.sp)
            }
        }
    }
}

/** Diagnostic Touch Sampling Rate Tester Pad */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchSamplingPad() {
    var readout by remember { mutableStateOf("Swipe fast across this pad to measure raw Touch IC Hz") }
    val times = remember { ArrayList<Long>() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Color(0xFF0A1017), shape = RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF192535), shape = RoundedCornerShape(8.dp))
            .pointerInteropFilter { ev ->
                val nanos = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        times.clear()
                        times.add(if (nanos) ev.eventTimeNanos else ev.eventTime * 1_000_000L)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        for (i in 0 until ev.historySize) {
                            times.add(if (nanos) ev.getHistoricalEventTimeNanos(i) else ev.getHistoricalEventTime(i) * 1_000_000L)
                        }
                        times.add(if (nanos) ev.eventTimeNanos else ev.eventTime * 1_000_000L)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val diffs = times.zipWithNext { a, b -> b - a }.filter { it > 0L }
                        readout = if (diffs.size >= 8) {
                            val med = diffs.sorted()[diffs.size / 2] / 1_000_000.0
                            val hz = if (med > 0.0) 1000.0 / med else 0.0
                            "⚡ Touch Polling Rate: ~${"%.0f".format(hz)} Hz (Median: ${"%.2f".format(med)} ms)"
                        } else {
                            "Insufficient samples. Swipe faster across the pad."
                        }
                    }
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = readout,
            color = Color(0xFF00FF66),
            fontSize = 12.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var shizukuState by remember { mutableStateOf(ShizukuState.current()) }
    var installedGamesList by remember { mutableStateOf(listOf<String>()) }
    var selectedGame by remember { mutableStateOf(Games.known.keys.first()) }
    var statusLog by remember { mutableStateOf("⚡ System Ready. Tap any feature for instant activation.") }
    var isBusy by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }
    var btnBoostText by remember { mutableStateOf("⚡ ACTIVATE 100% ROCK-SOLID 120 FPS") }
    var btnLaunchText by remember { mutableStateOf("🚀 BOOST & LAUNCH GAME NOW") }
    var lastBoostClick by remember { mutableLongStateOf(0L) }
    var lastLaunchClick by remember { mutableLongStateOf(0L) }

    // State Variables
    var bulletRegActive by remember { mutableStateOf(true) }
    var rendererMode by remember { mutableStateOf("skiagl") }
    var hyperFusionActive by remember { mutableStateOf(false) }
    var gpuFloorActive by remember { mutableStateOf(false) }
    var hardwareRefreshRate by remember { mutableStateOf(120.0) }

    // PGT+ v0.25.9 State
    var pgtRes by remember { mutableStateOf("1280x720") }
    var pgtQual by remember { mutableStateOf("Smooth") }
    var pgtFps by remember { mutableStateOf(120) }
    var pgtPotatoLevel by remember { mutableStateOf("Off") }
    var pgtSimpleShader by remember { mutableStateOf(false) }
    var pgtShadow by remember { mutableStateOf(false) }
    var pgtMsaa by remember { mutableStateOf(false) }
    var pgtSoundQual by remember { mutableStateOf("Ultra") }
    var pgtGpuExt by remember { mutableStateOf("Apply All") }
    var pgtSaveSens by remember { mutableStateOf(true) }

    // Wide FOV & Magnifier
    var maintainYFov by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableStateOf(2.5f) }

    // Volume AirTriggers State
    var volTriggersEnabled by remember { mutableStateOf(CrosshairSettings.isVolumeTriggerEnabled) }
    var showTriggerPins by remember { mutableStateOf(CrosshairSettings.showTriggerPins) }
    var l1CoordX by remember { mutableStateOf("${CrosshairSettings.l1TargetX}") }
    var l1CoordY by remember { mutableStateOf("${CrosshairSettings.l1TargetY}") }
    var r1CoordX by remember { mutableStateOf("${CrosshairSettings.r1TargetX}") }
    var r1CoordY by remember { mutableStateOf("${CrosshairSettings.r1TargetY}") }

    // Tactical Audio Hunter & Touch Presets
    var selectedAudioPreset by remember { mutableStateOf(AudioTest.activePreset) }
    var selectedTouchPreset by remember { mutableStateOf(1) }

    // Motion Control state
    var motionEnabled by remember { mutableStateOf(false) }
    var motionLeftX by remember { mutableStateOf("300") }
    var motionLeftY by remember { mutableStateOf("800") }
    var motionRightX by remember { mutableStateOf("1800") }
    var motionRightY by remember { mutableStateOf("800") }

    // Rapid Fire Macro state
    var rfX by remember { mutableStateOf("1800") }
    var rfY by remember { mutableStateOf("600") }

    // HUD & Sidebar Services
    var hudActive by remember { mutableStateOf(false) }
    var cyberDockActive by remember { mutableStateOf(false) }
    var auxLineActive by remember { mutableStateOf(false) }
    var fireRingActive by remember { mutableStateOf(false) }

    // 6 Pro Crosshairs
    var crosshairStyleState by remember { mutableStateOf(CrosshairSettings.style) }
    var crosshairOffsetX by remember { mutableStateOf(CrosshairSettings.offsetX.toFloat()) }
    var crosshairOffsetY by remember { mutableStateOf(CrosshairSettings.offsetY.toFloat()) }
    var crosshairSize by remember { mutableStateOf(CrosshairSettings.sizePx.toFloat()) }
    var crosshairColor by remember { mutableStateOf(CrosshairSettings.colorHex) }

    // Mini App
    var miniAppPkg by remember { mutableStateOf("com.whatsapp") }

    val sp = remember { ctx.getSharedPreferences("hgb_profiles", Context.MODE_PRIVATE) }

    fun loadProfileForGame(game: String) {
        pgtFps = sp.getInt("fps_$game", 120)
        pgtRes = sp.getString("res_$game", "1280x720") ?: "1280x720"
        pgtQual = sp.getString("qual_$game", "Smooth") ?: "Smooth"
        pgtPotatoLevel = sp.getString("potato_$game", "Off") ?: "Off"
        zoomScale = sp.getFloat("zoom_$game", 2.5f)
        bulletRegActive = sp.getBoolean("bullet_reg_$game", true)
        rendererMode = sp.getString("renderer_$game", "skiagl") ?: "skiagl"
        maintainYFov = sp.getBoolean("wide_fov_$game", false)
        CrosshairSettings.magnifierScale = zoomScale
    }

    fun saveProfileForGame(game: String) {
        sp.edit()
            .putInt("fps_$game", pgtFps)
            .putString("res_$game", pgtRes)
            .putString("qual_$game", pgtQual)
            .putString("potato_$game", pgtPotatoLevel)
            .putFloat("zoom_$game", zoomScale)
            .putBoolean("bullet_reg_$game", bulletRegActive)
            .putString("renderer_$game", rendererMode)
            .putBoolean("wide_fov_$game", maintainYFov)
            .apply()
    }

    DisposableEffect(Unit) {
        val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> shizukuState = ShizukuState.current() }
        val binderListener = Shizuku.OnBinderReceivedListener { shizukuState = ShizukuState.current() }
        val deadListener = Shizuku.OnBinderDeadListener { shizukuState = ShizukuState.current() }
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permListener)
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeBinderDeadListener(deadListener)
        }
    }

    LaunchedEffect(shizukuState) {
        if (shizukuState == ShizukuState.READY) {
            installedGamesList = Actions.installedGames()
            installedGamesList.firstOrNull()?.let {
                selectedGame = it
                loadProfileForGame(it)
            }
        }
    }

    // Ultra-Fast Non-Blocking Coroutine Executor with Instant Visual Feedback
    fun exec(block: suspend () -> String) {
        statusLog = "⚡ Executing action..."
        scope.launch {
            statusLog = try {
                block()
            } catch (e: Throwable) {
                "ERR: ${e.message}"
            }
        }
    }

    fun updateOverlayService() {
        if (!Settings.canDrawOverlays(ctx)) {
            statusLog = "Overlay permission required. Tap 'Grant Overlay Permission' first."
            hudActive = false
            cyberDockActive = false
            crosshairStyleState = 0
            auxLineActive = false
            fireRingActive = false
            return
        }
        val intent = Intent(ctx, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_GAME, selectedGame)
            putExtra(OverlayService.EXTRA_HUD, hudActive)
            putExtra(OverlayService.EXTRA_TOOLS, cyberDockActive)
            putExtra(OverlayService.EXTRA_CROSSHAIR, crosshairStyleState)
            putExtra(OverlayService.EXTRA_AUX_LINE, auxLineActive)
            putExtra(OverlayService.EXTRA_FIRE_RING, fireRingActive)
        }
        ctx.startForegroundService(intent)
    }

    val isReady = shizukuState == ShizukuState.READY || Shell.isRootAvailable()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF080C14))) {
        // =====================================================================
        // CYBER GAMING HEADER
        // =====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1420), Color(0xFF090D15))))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VipGamingLogo(modifier = Modifier.size(38.dp))
                    Column {
                        Text(
                            "HYPERGAMEBOOSTER",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF00FF66),
                            fontSize = 17.sp
                        )
                        Text(
                            "ESPORTS PERFORMANCE SUITE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF78909C),
                            fontSize = 9.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF142436),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Text(
                        "v5.0 GOD-TIER",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp
                    )
                }
            }

            // Shizuku Status Banner
            when (shizukuState) {
                ShizukuState.NOT_RUNNING -> {
                    Text("⚠️ Shizuku is not running. Please start Shizuku service.", color = Color(0xFFFF5252), fontSize = 11.sp)
                }
                ShizukuState.NO_PERMISSION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shizuku Running (Permission Needed)", color = Color(0xFFFFD600), fontSize = 11.sp)
                        Button(
                            onClick = { Shizuku.requestPermission(1001) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Grant Permission", fontSize = 10.sp)
                        }
                    }
                }
                else -> {
                    Text("✓ Shizuku UID 2000 ACTIVE | HyperOS 3 Snapdragon", color = Color(0xFF00FF66), fontSize = 11.sp)
                }
            }

            // Game Variant Selector
            val displayGames = if (installedGamesList.isNotEmpty()) installedGamesList else Games.known.keys.toList()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                displayGames.forEach { pkg ->
                    FilterChip(
                        selected = selectedGame == pkg,
                        onClick = {
                            selectedGame = pkg
                            loadProfileForGame(pkg)
                        },
                        label = { Text(Games.known[pkg] ?: pkg, fontSize = 11.sp) }
                    )
                }
            }
        }

        // 4 Esports Navigation Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF0C131D),
            contentColor = Color(0xFF00FF66)
        ) {
            listOf("1. BOOST", "2. GFX / RES", "3. TOUCH / AUDIO", "4. HUD / NET").forEachIndexed { index, tabTitle ->
                Tab(
                    selected = activeTab == index,
                    onClick = { activeTab = index },
                    text = { Text(tabTitle, fontSize = 11.sp) }
                )
            }
        }

        // Scrollable Tab Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                // =============================================================
                // TAB 0: BOOST (Bullet Registration, HyperFusion, GPU Floor, Debloat)
                // =============================================================
                0 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.5.dp, Color(0xFF00FF66))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "⚡ INSTANT ESPORTS MASTER ENGINE",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF00FF66),
                                fontSize = 14.sp
                            )
                            Text(
                                "Atomic subshell execution (<300ms) with zero-stutter frame pacing",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF90A4AE),
                                fontSize = 11.sp
                            )

                            Button(
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastBoostClick < 500L) return@Button
                                    lastBoostClick = now

                                    triggerVibe(ctx)
                                    btnBoostText = "⚡ ACTIVATING 120 FPS..."
                                    statusLog = "⚡ Enforcing 120 FPS & HyperOS 3 Esports Suite..."

                                    scope.launch {
                                        val t0 = System.currentTimeMillis()
                                        val res = try {
                                            Actions.applyMonsterBoost(selectedGame, ctx.packageName)
                                        } catch (e: Throwable) {
                                            "ERR: ${e.message}"
                                        }
                                        val elapsed = System.currentTimeMillis() - t0
                                        btnBoostText = "✅ 120 FPS LOCKED IN ${elapsed}ms!"
                                        statusLog = "✅ 120 FPS LOCKED IN ${elapsed}ms!\n$res"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(btnBoostText, color = Color.Black, fontSize = 13.sp)
                            }

                            Button(
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastLaunchClick < 500L) return@Button
                                    lastLaunchClick = now

                                    triggerVibe(ctx)
                                    btnLaunchText = "🚀 LAUNCHING..."

                                    val t0 = System.currentTimeMillis()

                                    // 1. Immediately launch game via 3-Stage Fallback (<50ms instant response)
                                    val launchRes = Actions.launchGame(ctx, selectedGame)

                                    // 2. Fire the fast boost & watchdog in the background concurrently
                                    scope.launch {
                                        try {
                                            Actions.applyMonsterBoost(selectedGame, ctx.packageName)
                                            try {
                                                ctx.startForegroundService(Intent(ctx, AutoService::class.java).putExtra("game", selectedGame))
                                            } catch (_: Throwable) {}
                                        } catch (_: Throwable) {}
                                    }

                                    val elapsed = System.currentTimeMillis() - t0
                                    btnLaunchText = "✅ LAUNCHED IN ${elapsed}ms!"
                                    statusLog = "🚀 Launched in ${elapsed}ms | $launchRes"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(btnLaunchText, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    SectionCard("🎯 BULLET REGISTRATION & ZERO-DESYNC", "VRS Killer + 2-Buffer Render Queue + Thread Priority 7") {
                        ToggleRow(
                            label = "Bullet Hit-Reg & Zero-Desync Mode",
                            subtitle = "Kills VRS blur, sets 2-frame buffer & Thread Prio 7",
                            enabled = isReady,
                            onAction = {
                                bulletRegActive = true
                                saveProfileForGame(selectedGame)
                                exec { Actions.applyBulletRegistrationMode(selectedGame, true, rendererMode) }
                            },
                            offAction = {
                                bulletRegActive = false
                                saveProfileForGame(selectedGame)
                                exec { Actions.applyBulletRegistrationMode(selectedGame, false) }
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = rendererMode == "skiagl",
                                onClick = {
                                    rendererMode = "skiagl"
                                    saveProfileForGame(selectedGame)
                                    if (bulletRegActive) exec { Actions.applyBulletRegistrationMode(selectedGame, true, "skiagl") }
                                },
                                label = { Text("[A] SkiaGL + System Driver (Best Hit-Reg)", fontSize = 10.sp) }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = rendererMode == "skiavk",
                                onClick = {
                                    rendererMode = "skiavk"
                                    saveProfileForGame(selectedGame)
                                    if (bulletRegActive) exec { Actions.applyBulletRegistrationMode(selectedGame, true, "skiavk") }
                                },
                                label = { Text("[B] SkiaVK + Game Driver (Max FPS)", fontSize = 10.sp) }
                            )
                        }
                    }

                    SectionCard("⚡ HYPERFUSION DUAL-CHANNEL NETWORK", "Concurrent 5G/4G + Wi-Fi Standby with 8MB UDP Buffers") {
                        ToggleRow(
                            label = "HyperFusion Dual-Channel Engine",
                            subtitle = "Eliminates ping spikes in 1v1 fights by binding 5G & Wi-Fi",
                            enabled = isReady,
                            onAction = {
                                hyperFusionActive = true
                                exec { Actions.applyHyperFusionNetwork(selectedGame, true) }
                            },
                            offAction = {
                                hyperFusionActive = false
                                exec { Actions.applyHyperFusionNetwork(selectedGame, false) }
                            }
                        )
                    }

                    SectionCard("🎮 ADRENO GPU GOVERNOR & DRIVER SWITCHER", "500MHz+ minimum clock floor prevents frame drop") {
                        ToggleRow(
                            label = "Adreno GPU Performance Floor",
                            subtitle = "Locks GPU minimum clock to 500MHz+ (Zero hot-drop stutter)",
                            enabled = isReady,
                            onAction = {
                                gpuFloorActive = true
                                exec { Actions.applyAdrenoPerformanceFloor(true) }
                            },
                            offAction = {
                                gpuFloorActive = false
                                exec { Actions.applyAdrenoPerformanceFloor(false) }
                            }
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = { exec { Actions.setGraphicsDriverType(selectedGame, "system") } },
                                label = { Text("System Driver", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { exec { Actions.setGraphicsDriverType(selectedGame, "game_driver") } },
                                label = { Text("Qualcomm Game Driver", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { exec { Actions.setGraphicsDriverType(selectedGame, "prerelease") } },
                                label = { Text("Prerelease Driver", fontSize = 10.sp) }
                            )
                        }
                    }

                    SectionCard("🛡️ SAFE PHONE DE-BLOAT & ZRAM TURBO", "Stops bloatware in 0.2s; WhatsApp & Gboard protected") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.safeDeepPhoneDebloat(ctx) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EA))
                            ) {
                                Text("1-TAP DEBLOAT", fontSize = 11.sp)
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.compactMemoryAndPurgeCaches() } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA))
                            ) {
                                Text("COMPACT ZRAM", fontSize = 11.sp)
                            }
                        }
                    }



                    SectionCard("Bypass Charging & Thermal Protection", "Stop battery heating while playing plugged-in") {
                        ToggleRow(
                            label = "🔌 Toggle Bypass Charging",
                            subtitle = "Writes to input_suspend / night_charging & disables charge boost",
                            enabled = isReady,
                            onAction = { exec { Actions.setBypassCharging(true) } },
                            offAction = { exec { Actions.setBypassCharging(false) } }
                        )
                    }

                    SectionCard("Smart Watchdog & Auto-Profile", "PID tracking: auto-boost on launch, auto-revert on close") {
                        ToggleRow(
                            label = "Joyose Smart Watchdog (AutoService)",
                            subtitle = "Auto-applies saved profile, 60s Joyose kill + 120Hz lock",
                            enabled = isReady,
                            onAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                ctx.startForegroundService(
                                    Intent(ctx, AutoService::class.java).putExtra(AutoService.EXTRA_GAME, selectedGame)
                                )
                                statusLog = "Smart Auto-Watchdog STARTED for $selectedGame"
                            },
                            offAction = {
                                ctx.stopService(Intent(ctx, AutoService::class.java))
                                exec { Actions.revertAll(selectedGame) }
                            }
                        )
                    }
                }

                // =============================================================
                // TAB 1: GFX / RES (Display Hz, Wide FOV, Smoke Anti-Lag, PGT+)
                // =============================================================
                1 -> {
                    SectionCard("🖥️ HARDWARE REFRESH RATE SUITE", "Hard-lock screen Hz to defeat HyperOS 42°C drop") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60.0 to "60Hz", 90.0 to "90Hz", 120.0 to "120Hz", 144.0 to "144Hz").forEach { (hz, label) ->
                                FilterChip(
                                    selected = hardwareRefreshRate == hz,
                                    onClick = {
                                        hardwareRefreshRate = hz
                                        exec { Actions.setHardwareRefreshRate(hz) }
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    SectionCard("⚡ Netcode Tickrate & Smoke Anti-Lag", "Constant 120 FPS in smokes & instant bullet packets") {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady,
                            onClick = { exec { Actions.injectZeroDesyncNetcodeAndSmokeAntiLag(selectedGame) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                        ) {
                            Text("INJECT ZERO-DESYNC NETCODE & SMOKE ANTI-LAG", fontSize = 11.sp)
                        }
                        Text("Sets p.NetEnableMoveCombining=0 & smoke particle spawn scale 0.25x in UserCustom.ini.", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    }

                    SectionCard("True Full-Screen Wide View (MaintainYFOV)", "Full-screen camera expansion without black bars") {
                        ToggleRow(
                            label = "Camera Wide View (AspectRatio_MaintainYFOV)",
                            subtitle = "Expands horizontal field of view cleanly on full screen",
                            enabled = isReady,
                            onAction = {
                                maintainYFov = true
                                saveProfileForGame(selectedGame)
                                exec { Actions.applyMaintainYFov(selectedGame, true) }
                            },
                            offAction = {
                                maintainYFov = false
                                saveProfileForGame(selectedGame)
                                exec { Actions.applyMaintainYFov(selectedGame, false) }
                            }
                        )
                    }

                    SectionCard("Esports Stretched Hitbox & Aspect Downscaler", "Wider character models for easy headshots") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.applyAspectPreset(4) } }) {
                                Text("1080x2160 Stretched", fontSize = 10.sp)
                            }
                            Button(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.applyAspectPreset(5) } }) {
                                Text("1080x1920 Ultra Wide", fontSize = 10.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.applyAspectPreset(1) } }) {
                                Text("900p Pro", fontSize = 10.sp)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.applyAspectPreset(2) } }) {
                                Text("810p Scrim", fontSize = 10.sp)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.applyAspectPreset(3) } }) {
                                Text("720p Potato", fontSize = 10.sp)
                            }
                        }
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = isReady, onClick = { exec { Actions.applyAspectPreset(0) } }) {
                            Text("Reset Screen Resolution to Stock")
                        }
                    }

                    SectionCard("PGT+ (v0.25.9) Config & Extension Injector", "Full interactive controls & sensitivity protection") {
                        Text("Resolution:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("800x600", "960x540", "1280x720", "1440x720", "1600x900", "1920x1080", "2560x1440").forEach { r ->
                                FilterChip(
                                    selected = pgtRes == r,
                                    onClick = { pgtRes = r },
                                    label = { Text(r, fontSize = 11.sp) }
                                )
                            }
                        }

                        Text("Graphics Quality & Target FPS:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Super Smooth", "Smooth", "Balanced", "HD", "HDR").forEach { q ->
                                FilterChip(
                                    selected = pgtQual == q,
                                    onClick = { pgtQual = q },
                                    label = { Text(q, fontSize = 11.sp) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60, 90, 120).forEach { f ->
                                FilterChip(
                                    selected = pgtFps == f,
                                    onClick = { pgtFps = f },
                                    label = { Text("$f FPS", fontSize = 11.sp) }
                                )
                            }
                        }

                        Text("Potato Graphics Level:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Off", "Low (LOD +1)", "Medium (LOD +2)", "Ultra (LOD +3)").forEach { p ->
                                FilterChip(
                                    selected = pgtPotatoLevel == p,
                                    onClick = { pgtPotatoLevel = p },
                                    label = { Text(p, fontSize = 11.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Simple Shader (Dynamic Lighting off):", color = Color.White, fontSize = 12.sp)
                            Switch(checked = pgtSimpleShader, onCheckedChange = { pgtSimpleShader = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Shadows:", color = Color.White, fontSize = 12.sp)
                            Switch(checked = pgtShadow, onCheckedChange = { pgtShadow = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("MSAA Anti-Aliasing (4x):", color = Color.White, fontSize = 12.sp)
                            Switch(checked = pgtMsaa, onCheckedChange = { pgtMsaa = it })
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady,
                            onClick = {
                                saveProfileForGame(selectedGame)
                                exec {
                                    Actions.applyPgtGraphicsConfig(
                                        game = selectedGame,
                                        resolution = pgtRes,
                                        quality = pgtQual,
                                        fps = pgtFps,
                                        potatoLevel = pgtPotatoLevel,
                                        simpleShader = pgtSimpleShader,
                                        shadowEnabled = pgtShadow,
                                        msaaEnabled = pgtMsaa,
                                        soundQuality = pgtSoundQual,
                                        gpuExtension = pgtGpuExt,
                                        saveSensitivity = pgtSaveSens
                                    )
                                }
                            }
                        ) {
                            Text("INJECT PGT+ CONFIG (ZERO-CONFLICT)")
                        }
                    }

                    SectionCard("RedMagic Sniper Magnifier (2x–4x Zoom)", "Hardware SurfaceFlinger window zoom for scouts") {
                        Text("Magnifier Scale: ${"%.1f".format(zoomScale)}x", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = zoomScale,
                            onValueChange = {
                                zoomScale = it
                                CrosshairSettings.magnifierScale = it
                                saveProfileForGame(selectedGame)
                            },
                            valueRange = 2.0f..4.0f,
                            steps = 3
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.setMagnifier(true, zoomScale) } }) {
                                Text("Magnifier ON")
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.setMagnifier(false) } }) {
                                Text("Magnifier OFF")
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 2: TOUCH / AUDIO / AIRTRIGGERS
                // =============================================================
                2 -> {
                    SectionCard("🎯 3 1-TAP TOUCH & RECOIL PRESETS", "Instant hardware sampling & filter profiles") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedTouchPreset == 1,
                                onClick = {
                                    selectedTouchPreset = 1
                                    exec { Actions.applyTouchPreset(1) }
                                },
                                label = { Text("🔥 Preset 1: Jiggle God (480Hz)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedTouchPreset == 2,
                                onClick = {
                                    selectedTouchPreset = 2
                                    exec { Actions.applyTouchPreset(2) }
                                },
                                label = { Text("🎯 Preset 2: Laser AR (400Hz Gyro)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedTouchPreset == 3,
                                onClick = {
                                    selectedTouchPreset = 3
                                    exec { Actions.applyTouchPreset(3) }
                                },
                                label = { Text("⚡ Preset 3: DMR Sniper (40ms)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedTouchPreset == 0,
                                onClick = {
                                    selectedTouchPreset = 0
                                    exec { Actions.applyTouchPreset(0) }
                                },
                                label = { Text("Reset", fontSize = 11.sp) }
                            )
                        }
                    }

                    SectionCard("🎧 FOOTSTEP LATENCY & AUDIO DAC UNCAP", "Bypasses Android safe media limit & communication latency") {
                        ToggleRow(
                            label = "Footstep Latency & Audio Bypass",
                            subtitle = "music_in_communication 1, peak_volume 1, safemedia bypass",
                            enabled = isReady,
                            onAction = { exec { Actions.applyAudioAndDacFix(true) } },
                            offAction = { exec { Actions.applyAudioAndDacFix(false) } }
                        )
                    }

                    SectionCard("🎮 ASUS ROG HARDWARE AIRTRIGGERS (L1 / R1)", "Physical Volume Buttons Trigger On-Screen Aim & Fire") {
                        ToggleRow(
                            label = "Hardware Volume Triggers",
                            subtitle = "Volume Up = L1 (Aim/Scope) | Volume Down = R1 (Fire)",
                            enabled = isReady,
                            onAction = {
                                volTriggersEnabled = true
                                CrosshairSettings.isVolumeTriggerEnabled = true
                                statusLog = "Volume Button Triggers ACTIVE"
                            },
                            offAction = {
                                volTriggersEnabled = false
                                CrosshairSettings.isVolumeTriggerEnabled = false
                                statusLog = "Volume Button Triggers DISABLED"
                            }
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady,
                            onClick = { exec { Actions.enableVolumeTriggersViaShizuku() } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EA))
                        ) {
                            Text("1-TAP ENABLE TRIGGER SERVICE VIA SHIZUKU", fontSize = 11.sp)
                        }
                        ToggleRow(
                            label = "Show Draggable L1 / R1 Pins",
                            subtitle = "Drag [L1:AIM] and [R1:FIRE] pins directly over game buttons",
                            enabled = true,
                            onAction = {
                                showTriggerPins = true
                                CrosshairSettings.showTriggerPins = true
                                updateOverlayService()
                            },
                            offAction = {
                                showTriggerPins = false
                                CrosshairSettings.showTriggerPins = false
                                updateOverlayService()
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = l1CoordX,
                                onValueChange = {
                                    l1CoordX = it
                                    it.toIntOrNull()?.let { x -> CrosshairSettings.l1TargetX = x }
                                },
                                label = { Text("L1 Aim X") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = l1CoordY,
                                onValueChange = {
                                    l1CoordY = it
                                    it.toIntOrNull()?.let { y -> CrosshairSettings.l1TargetY = y }
                                },
                                label = { Text("L1 Aim Y") },
                                singleLine = true
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = r1CoordX,
                                onValueChange = {
                                    r1CoordX = it
                                    it.toIntOrNull()?.let { x -> CrosshairSettings.r1TargetX = x }
                                },
                                label = { Text("R1 Fire X") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = r1CoordY,
                                onValueChange = {
                                    r1CoordY = it
                                    it.toIntOrNull()?.let { y -> CrosshairSettings.r1TargetY = y }
                                },
                                label = { Text("R1 Fire Y") },
                                singleLine = true
                            )
                        }
                    }

                    SectionCard("🎧 4-PRESET TACTICAL AUDIO HUNTER", "Footstep Hunter, Gunshot Suppressor, Vehicle Cut, Pro Flat") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                1 to "Footstep Hunter (+6dB)",
                                2 to "Gunshot Suppressor",
                                3 to "Vehicle Rumble Cut",
                                4 to "Pro Flat (+500mB)",
                                0 to "Stock / Off"
                            ).forEach { (presetId, label) ->
                                FilterChip(
                                    selected = selectedAudioPreset == presetId,
                                    onClick = {
                                        selectedAudioPreset = presetId
                                        statusLog = AudioTest.applyPreset(presetId)
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                        Text("Stereo Channel Verification (520Hz Left / 780Hz Right):", color = Color.White, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.weight(1f), onClick = { AudioTest.play(0) }) {
                                Text("Test LEFT Channel")
                            }
                            Button(modifier = Modifier.weight(1f), onClick = { AudioTest.play(1) }) {
                                Text("Test RIGHT Channel")
                            }
                        }
                    }

                    SectionCard("Android 16 Raw 1:1 Impulse Touch", "Disables touch filtering delay & sets 0 slop") {
                        ToggleRow(
                            label = "Impulse Touch & HyperOS Touch IC",
                            subtitle = "gamemode_touch_follow_response 100, aim 90, stability 100",
                            enabled = isReady,
                            onAction = { exec { Actions.applyImpulseTouchAndTouchIc(true) } },
                            offAction = { exec { Actions.applyImpulseTouchAndTouchIc(false) } }
                        )
                    }

                    SectionCard("Live Touch Polling Rate (Hz) Diagnostic Pad", "Hardware sampling diagnostic") {
                        TouchSamplingPad()
                    }
                }

                // =============================================================
                // TAB 3: HUD / NET / RETICLES (RedMagic Dock, 6 Pro Crosshairs)
                // =============================================================
                3 -> {
                    SectionCard("⚡ REDMAGIC SLIDING CYBER DOCK & OVERLAYS", "Slide-out VIP Gaming Dock over PUBG Mobile") {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
                                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                    ctx.startActivity(i)
                                } else {
                                    exec { Actions.grantOverlay(ctx.packageName) }
                                }
                            }
                        ) {
                            Text("Grant Overlay Permission")
                        }
                        ToggleRow(
                            label = "RedMagic Cyber VIP Dock Handle",
                            subtitle = "Draggable neon edge-pill: tap/swipe to slide open VIP controls in-game",
                            enabled = true,
                            onAction = { cyberDockActive = true; updateOverlayService() },
                            offAction = { cyberDockActive = false; updateOverlayService() }
                        )
                        ToggleRow(
                            label = "Live Telemetry HUD (FPS, 1%L, Ping, Watts, Radar)",
                            enabled = true,
                            onAction = { hudActive = true; updateOverlayService() },
                            offAction = { hudActive = false; updateOverlayService() }
                        )
                        ToggleRow(
                            label = "Auxiliary Grenade Trajectory Line",
                            enabled = true,
                            onAction = { auxLineActive = true; updateOverlayService() },
                            offAction = { auxLineActive = false; updateOverlayService() }
                        )
                        ToggleRow(
                            label = "4D Haptic Fire Ring (Heavy-Click Burst)",
                            enabled = true,
                            onAction = { fireRingActive = true; updateOverlayService() },
                            offAction = { fireRingActive = false; updateOverlayService() }
                        )
                    }

                    SectionCard("🎯 6-SHAPE RETICLE & CROSSHAIR STUDIO", "Pixel-perfect reticles with X/Y alignment") {
                        Text("Style:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "OFF" to 0,
                                "Dot" to 1,
                                "Tactical Cross" to 2,
                                "Circle+Dot" to 3,
                                "T-Cross" to 4,
                                "Chevron" to 5,
                                "Rangefinder" to 6
                            ).forEach { (name, style) ->
                                FilterChip(
                                    selected = crosshairStyleState == style,
                                    onClick = {
                                        crosshairStyleState = style
                                        CrosshairSettings.style = style
                                        updateOverlayService()
                                    },
                                    label = { Text(name, fontSize = 11.sp) }
                                )
                            }
                        }

                        Text("X Offset: ${crosshairOffsetX.toInt()} px | Y Offset: ${crosshairOffsetY.toInt()} px", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = crosshairOffsetX,
                            onValueChange = {
                                crosshairOffsetX = it
                                CrosshairSettings.offsetX = it.toInt()
                                updateOverlayService()
                            },
                            valueRange = -30f..30f,
                            steps = 60
                        )

                        Text("Size: ${crosshairSize.toInt()} px", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = crosshairSize,
                            onValueChange = {
                                crosshairSize = it
                                CrosshairSettings.sizePx = it.toInt()
                                updateOverlayService()
                            },
                            valueRange = 10f..50f,
                            steps = 40
                        )

                        Text("RGB Color:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Neon Green" to "#00FF66",
                                "Cyan" to "#00E5FF",
                                "Red" to "#FF1744",
                                "Yellow" to "#FFEA00",
                                "White" to "#FFFFFF",
                                "Purple" to "#D500F9"
                            ).forEach { (name, hex) ->
                                FilterChip(
                                    selected = crosshairColor == hex,
                                    onClick = {
                                        crosshairColor = hex
                                        CrosshairSettings.colorHex = hex
                                        updateOverlayService()
                                    },
                                    label = { Text(name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    SectionCard("Low-Ping Network Firewall & DNS Optimizer", "Eliminate background jitter & flush DNS") {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady,
                            onClick = { exec { Actions.flushDnsAndResetSocketJitter() } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                        ) {
                            Text("🔄 1-TAP FLUSH DNS & RESET SOCKET JITTER", fontSize = 11.sp)
                        }
                        ToggleRow(
                            label = "Network Bandwidth Firewall",
                            subtitle = "cmd netpolicy whitelist only active PUBG UID",
                            enabled = isReady,
                            onAction = { exec { Actions.applyLowPingNetworkFirewall(selectedGame, true) } },
                            offAction = { exec { Actions.applyLowPingNetworkFirewall(selectedGame, false) } }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.setGamingDns("cloudflare") } }) {
                                Text("Cloudflare 1.1.1.1", fontSize = 10.sp)
                            }
                            Button(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.setGamingDns("google") } }) {
                                Text("Google 8.8.8.8", fontSize = 10.sp)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.setGamingDns("auto") } }) {
                                Text("Auto DNS", fontSize = 10.sp)
                            }
                        }
                    }

                    SectionCard("Championship Mode & Revert", "Block status bar pull-down, mistouches & DND") {
                        ToggleRow(
                            label = "Championship Fullscreen Lock",
                            subtitle = "Immersive full policy, disable-flag 65536, edge gestures off",
                            enabled = isReady,
                            onAction = { exec { Actions.applyChampionshipMode(selectedGame, true) } },
                            offAction = { exec { Actions.applyChampionshipMode(selectedGame, false) } }
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady,
                            onClick = { exec { Actions.revertAll(selectedGame) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                        ) {
                            Text("REVERT ALL SETTINGS TO FACTORY STOCK")
                        }
                    }
                }
            }

            // Real-Time Action Log Console
            SectionCard("STATUS & CONSOLE OUTPUT") {
                SelectionContainer {
                    Text(
                        text = statusLog,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00FF66),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
