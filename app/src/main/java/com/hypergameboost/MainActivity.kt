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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                primary = Color(0xFF00E676),
                onPrimary = Color.Black,
                secondary = Color(0xFF00E5FF),
                background = Color(0xFF0A0E14),
                surface = Color(0xFF131922),
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

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF00E5FF)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE)
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
            Text(label, color = Color.White, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF78909C), fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                enabled = enabled,
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("ON", fontSize = 12.sp)
            }
            OutlinedButton(
                enabled = enabled,
                onClick = offAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("OFF", fontSize = 12.sp)
            }
        }
    }
}

/** Diagnostic Touch Sampling Rate Tester Pad */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchSamplingPad() {
    var readout by remember { mutableStateOf("Swipe fast and continuously across this pad to measure raw Touch IC Hz") }
    val times = remember { ArrayList<Long>() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color(0xFF102027))
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
                            "⚡ Touch Polling Rate: ~${"%.0f".format(hz)} Hz (Median: ${"%.2f".format(med)} ms, ${diffs.size} samples)"
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
            color = Color(0xFF00E676),
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
    var statusLog by remember { mutableStateOf("Ready. Select module to optimize.") }
    var isBusy by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }

    // Bullet Reg & Renderer state
    var bulletRegActive by remember { mutableStateOf(true) }
    var rendererMode by remember { mutableStateOf("skiagl") }

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

    // Wide FOV state
    var maintainYFov by remember { mutableStateOf(false) }

    // Magnifier state
    var zoomScale by remember { mutableStateOf(2.5f) }

    // Motion Control state
    var motionEnabled by remember { mutableStateOf(false) }
    var motionLeftX by remember { mutableStateOf("300") }
    var motionLeftY by remember { mutableStateOf("800") }
    var motionRightX by remember { mutableStateOf("1800") }
    var motionRightY by remember { mutableStateOf("800") }

    // Rapid Fire Macro state
    var rfX by remember { mutableStateOf("1800") }
    var rfY by remember { mutableStateOf("600") }

    // HUD Service states
    var hudActive by remember { mutableStateOf(false) }
    var toolsActive by remember { mutableStateOf(false) }
    var auxLineActive by remember { mutableStateOf(false) }
    var fireRingActive by remember { mutableStateOf(false) }

    // Crosshair customizer state
    var crosshairStyleState by remember { mutableStateOf(CrosshairSettings.style) }
    var crosshairOffsetX by remember { mutableStateOf(CrosshairSettings.offsetX.toFloat()) }
    var crosshairOffsetY by remember { mutableStateOf(CrosshairSettings.offsetY.toFloat()) }
    var crosshairSize by remember { mutableStateOf(CrosshairSettings.sizePx.toFloat()) }
    var crosshairColor by remember { mutableStateOf(CrosshairSettings.colorHex) }

    // Floating App selection
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

    fun exec(block: suspend () -> String) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            statusLog = try {
                block()
            } catch (e: Throwable) {
                "ERROR: ${e.message}"
            } finally {
                isBusy = false
            }
        }
    }

    fun updateOverlayService() {
        if (!Settings.canDrawOverlays(ctx)) {
            statusLog = "Overlay permission required. Tap 'Grant Overlay Permission' first."
            hudActive = false
            toolsActive = false
            crosshairStyleState = 0
            auxLineActive = false
            fireRingActive = false
            return
        }
        val intent = Intent(ctx, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_GAME, selectedGame)
            putExtra(OverlayService.EXTRA_HUD, hudActive)
            putExtra(OverlayService.EXTRA_TOOLS, toolsActive)
            putExtra(OverlayService.EXTRA_CROSSHAIR, crosshairStyleState)
            putExtra(OverlayService.EXTRA_AUX_LINE, auxLineActive)
            putExtra(OverlayService.EXTRA_FIRE_RING, fireRingActive)
        }
        ctx.startForegroundService(intent)
    }

    val isReady = shizukuState == ShizukuState.READY || Shell.isRootAvailable()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1720))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HYPERGAMEBOOSTER",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF00E676)
                )
                Text(
                    "v5.0 STABLE GOD-TIER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00E5FF)
                )
            }

            // Shizuku Status Banner
            when (shizukuState) {
                ShizukuState.NOT_RUNNING -> {
                    Text(
                        "⚠️ Shizuku is not running. Start Shizuku app or connect via wireless debugging.",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp
                    )
                }
                ShizukuState.NO_PERMISSION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shizuku running (Permission Needed)", color = Color(0xFFFFD600), fontSize = 12.sp)
                        Button(
                            onClick = { Shizuku.requestPermission(1001) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))
                        ) {
                            Text("Grant Permission", fontSize = 11.sp)
                        }
                    }
                }
                else -> {
                    Text("✓ Shizuku UID 2000 Ready | HyperOS 3 Snapdragon", color = Color(0xFF00E676), fontSize = 12.sp)
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
                        label = { Text(Games.known[pkg] ?: pkg, fontSize = 12.sp) }
                    )
                }
            }
        }

        // 4 Esports Navigation Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF131B26),
            contentColor = Color(0xFF00E676)
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {
                // =============================================================
                // TAB 1: BOOST (Bullet Registration, Debloat, Watchdog, Bypass)
                // =============================================================
                0 -> {
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
                        Text("Renderer & Driver Switcher:", color = Color.White, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = rendererMode == "skiagl",
                                onClick = {
                                    rendererMode = "skiagl"
                                    saveProfileForGame(selectedGame)
                                    if (bulletRegActive) exec { Actions.applyBulletRegistrationMode(selectedGame, true, "skiagl") }
                                },
                                label = { Text("[A] SkiaGL + System Driver (Best Hit-Reg)", fontSize = 11.sp) }
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
                                label = { Text("[B] SkiaVK + Game Driver (Max FPS)", fontSize = 11.sp) }
                            )
                        }
                    }

                    SectionCard("🛡️ Safe Phone De-Bloat & RAM Guard", "Force-stop background apps without touching WhatsApp/Gboard") {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
                            onClick = { exec { Actions.safeDeepPhoneDebloat(ctx) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EA))
                        ) {
                            Text("1-TAP DEEP PHONE DE-BLOAT & RAM GUARD", fontSize = 12.sp)
                        }
                        Text("Stops third-party and Xiaomi bloatware. Whitelist protects WhatsApp, Gboard, Shizuku, and PUBG.", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    }

                    SectionCard("🔥 ESPORTS 1-TAP ACTIVATION", "Enforces all 16 optimization modules in a single batch") {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
                            onClick = { exec { Actions.esportsMode(selectedGame, ctx.packageName) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000))
                        ) {
                            Text("ACTIVATE 100% ROCK-SOLID 120 FPS", fontSize = 13.sp)
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
                            onClick = {
                                exec {
                                    val r = Actions.esportsMode(selectedGame, ctx.packageName)
                                    val l = Actions.launchGame(ctx, selectedGame)
                                    "$r\n$l"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                        ) {
                            Text("BOOST & LAUNCH GAME NOW", fontSize = 13.sp)
                        }
                    }

                    SectionCard("Bypass Charging & Thermal Protection", "Stop battery heating while playing plugged-in") {
                        ToggleRow(
                            label = "🔌 Toggle Bypass Charging / Stop Heat Charging",
                            subtitle = "Writes to input_suspend / night_charging & disables charge boost",
                            enabled = isReady,
                            onAction = { exec { Actions.setBypassCharging(true) } },
                            offAction = { exec { Actions.setBypassCharging(false) } }
                        )
                    }

                    SectionCard("Smart Watchdog & Auto-Profile", "PID tracking: auto-boost on launch, auto-revert on close") {
                        ToggleRow(
                            label = "Joyose Smart Watchdog (AutoService)",
                            subtitle = "Auto-applies saved profile when game opens, 60s Joyose kill + 120Hz lock",
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

                    SectionCard("Flagship Performance Governors", "Bypass conservative OEM throttling") {
                        ToggleRow(
                            label = "Diablo / X-Mode Awakening",
                            subtitle = "Overrides thermalservice 0 & locks fixed performance mode",
                            enabled = isReady,
                            onAction = { exec { Actions.applyDiabloThermalMode(true) } },
                            offAction = { exec { Actions.applyDiabloThermalMode(false) } }
                        )
                        ToggleRow(
                            label = "WildBoost 3.0 & FEAS Furioso",
                            subtitle = "Enables speed_mode, ultimate_mode & performance scheduler",
                            enabled = isReady,
                            onAction = { exec { Actions.applyWildBoostAndFeas(selectedGame, true) } },
                            offAction = { exec { Actions.applyWildBoostAndFeas(selectedGame, false) } }
                        )
                        ToggleRow(
                            label = "Millet, Greezer & VIP Standby (Bucket 5)",
                            subtitle = "Whitelists game PID and enables cached apps freezer",
                            enabled = isReady,
                            onAction = { exec { Actions.applyHyperOsCloudGreezerAndMillet(selectedGame, true) } },
                            offAction = { exec { Actions.applyHyperOsCloudGreezerAndMillet(selectedGame, false) } }
                        )
                    }
                }

                // =============================================================
                // TAB 2: GFX / RES (Wide View, Smoke Anti-Lag, PGT+ Complete)
                // =============================================================
                1 -> {
                    SectionCard("⚡ Netcode Tickrate & Smoke Anti-Lag", "Constant 120 FPS in smokes & instant bullet packets") {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
                            onClick = { exec { Actions.injectZeroDesyncNetcodeAndSmokeAntiLag(selectedGame) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                        ) {
                            Text("INJECT ZERO-DESYNC NETCODE & SMOKE ANTI-LAG", fontSize = 11.sp)
                        }
                        Text("Sets p.NetEnableMoveCombining=0 (every bullet sent instantly) & smoke particle spawn scale 0.25x.", color = Color(0xFF90A4AE), fontSize = 11.sp)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Save Sensitivity Settings (Active.sav protect):", color = Color.White, fontSize = 12.sp)
                            Switch(checked = pgtSaveSens, onCheckedChange = { pgtSaveSens = it })
                        }

                        Text("GPU Optimization Extensions:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Apply All", "Adreno (GL_AMD)", "ARM (GL_ARM)", "OES/EXT Compression", "Default").forEach { ext ->
                                FilterChip(
                                    selected = pgtGpuExt == ext,
                                    onClick = { pgtGpuExt = ext },
                                    label = { Text(ext, fontSize = 11.sp) }
                                )
                            }
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
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
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.setMagnifier(true, zoomScale) } }
                            ) {
                                Text("Magnifier ON")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.setMagnifier(false) } }
                            ) {
                                Text("Magnifier OFF")
                            }
                        }
                    }

                    SectionCard("Visual Camper Filters", "Spot enemies in grass (Erangel/Sanhok)") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(onClick = { exec { Actions.applyCamperFilters("eagle_eye") } }) {
                                Text("Eagle Eye (1.5x)")
                            }
                            Button(onClick = { exec { Actions.applyCamperFilters("scout_inversion") } }) {
                                Text("ROG Scout")
                            }
                            Button(onClick = { exec { Actions.applyCamperFilters("hunt_grass") } }) {
                                Text("RedMagic Grass")
                            }
                            Button(onClick = { exec { Actions.applyCamperFilters("hunt_red") } }) {
                                Text("Red Enemy Boost")
                            }
                            OutlinedButton(onClick = { exec { Actions.applyCamperFilters("reset") } }) {
                                Text("Reset")
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 3: TOUCH / AUDIO (Impulse Touch, Motion Control, DMR Macro)
                // =============================================================
                2 -> {
                    SectionCard("🎯 Bullet Registration & Desync Switch", "VRS Killer + Thread Priority 7") {
                        ToggleRow(
                            label = "Bullet Hit-Reg & Zero-Desync Mode",
                            subtitle = "Kills VRS blur & eliminates render queue delay",
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

                    SectionCard("iQOO 6-Axis Gyro Motion Control", "Tilt phone sharply to trigger on-screen taps") {
                        ToggleRow(
                            label = "Motion Control (Tilt-to-Tap)",
                            subtitle = "Sharp tilt triggers Shizuku tap (Peek/Jump/Reload)",
                            enabled = isReady,
                            onAction = {
                                motionEnabled = true
                                val lx = motionLeftX.toIntOrNull() ?: 300
                                val ly = motionLeftY.toIntOrNull() ?: 800
                                val rx = motionRightX.toIntOrNull() ?: 1800
                                val ry = motionRightY.toIntOrNull() ?: 800
                                statusLog = NativeTools.setMotionControl(ctx, true, lx, ly, rx, ry)
                            },
                            offAction = {
                                motionEnabled = false
                                statusLog = NativeTools.setMotionControl(ctx, false, 0, 0, 0, 0)
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = motionLeftX,
                                onValueChange = { motionLeftX = it },
                                label = { Text("Left Tilt X") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = motionLeftY,
                                onValueChange = { motionLeftY = it },
                                label = { Text("Left Tilt Y") },
                                singleLine = true
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = motionRightX,
                                onValueChange = { motionRightX = it },
                                label = { Text("Right Tilt X") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = motionRightY,
                                onValueChange = { motionRightY = it },
                                label = { Text("Right Tilt Y") },
                                singleLine = true
                            )
                        }
                    }

                    SectionCard("ASUS ROG Rapid-Fire DMR Target Coordinates", "12 taps/sec auto-fire trigger") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = rfX,
                                onValueChange = {
                                    rfX = it
                                    CrosshairSettings.rfTargetX = it.toIntOrNull() ?: 1800
                                },
                                label = { Text("Fire Button X") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = rfY,
                                onValueChange = {
                                    rfY = it
                                    CrosshairSettings.rfTargetY = it.toIntOrNull() ?: 600
                                },
                                label = { Text("Fire Button Y") },
                                singleLine = true
                            )
                        }
                    }

                    SectionCard("Live Touch Polling Rate (Hz) Diagnostic Pad", "Hardware sampling diagnostic") {
                        TouchSamplingPad()
                    }

                    SectionCard("400Hz+ Gyroscope Hardware Warmup", "Eliminates gyro spinup latency") {
                        ToggleRow(
                            label = "Gyro Warmup Pad (SENSOR_DELAY_FASTEST)",
                            subtitle = "Keeps gyroscope sensor at 400Hz+ peak frequency",
                            enabled = true,
                            onAction = { statusLog = NativeTools.startGyroWarmup(ctx) },
                            offAction = { statusLog = NativeTools.stopGyroWarmup() }
                        )
                    }

                    SectionCard("Tactical Footstep Audio & Equalizer", "Enhance footstep frequency & cut grenade bass") {
                        ToggleRow(
                            label = "5-Band Footstep EQ & Loudness (+600mB)",
                            subtitle = "Boosts 1kHz-4.5kHz footsteps, cuts <180Hz rumble",
                            enabled = true,
                            onAction = { statusLog = AudioTest.footstepEq(true) },
                            offAction = { statusLog = AudioTest.footstepEq(false) }
                        )
                        Text("Stereo Channel Verification (520Hz Left / 780Hz Right):", color = Color.White, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.weight(1f), onClick = { AudioTest.play(0) }) {
                                Text("Test LEFT Channel")
                            }
                            Button(modifier = Modifier.weight(1f), onClick = { AudioTest.play(1) }) {
                                Text("Test RIGHT Channel")
                            }
                        }
                        ToggleRow(
                            label = "Type-C Splitter DAC Fix & 2x BT Audio",
                            subtitle = "Disables absolute volume & sets stagefright low-latency",
                            enabled = isReady,
                            onAction = { exec { Actions.applyTypeCAudioFix(true) } },
                            offAction = { exec { Actions.applyTypeCAudioFix(false) } }
                        )
                    }
                }

                // =============================================================
                // TAB 4: HUD / NET (Flush DNS, Crosshairs, Floating App, DND)
                // =============================================================
                3 -> {
                    SectionCard("Low-Ping Network Firewall & DNS Optimizer", "Eliminate background jitter & flush DNS") {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
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
                                Text("Cloudflare 1.1.1.1", fontSize = 11.sp)
                            }
                            Button(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.setGamingDns("google") } }) {
                                Text("Google 8.8.8.8", fontSize = 11.sp)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), enabled = isReady, onClick = { exec { Actions.setGamingDns("auto") } }) {
                                Text("Auto DNS", fontSize = 11.sp)
                            }
                        }
                    }

                    SectionCard("Crosshair X/Y Alignment & Customizer", "Pixel-perfect punch-hole centering") {
                        Text("Style:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("OFF" to 0, "Dot" to 1, "Cross" to 2, "Circle" to 3).forEach { (name, style) ->
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

                        Text("X Offset: ${crosshairOffsetX.toInt()} px", color = Color.White, fontSize = 12.sp)
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

                        Text("Y Offset: ${crosshairOffsetY.toInt()} px", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = crosshairOffsetY,
                            onValueChange = {
                                crosshairOffsetY = it
                                CrosshairSettings.offsetY = it.toInt()
                                updateOverlayService()
                            },
                            valueRange = -30f..30f,
                            steps = 60
                        )

                        Text("Size: ${crosshairSize.toInt()} px", color = Color.White, fontSize = 12.sp)
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
                                "White" to "#FFFFFF"
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

                    SectionCard("Esports Overlays & Tactical Tools", "In-game telemetry & helper floating tools") {
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
                            label = "Draggable Telemetry HUD (FPS, 1%L, Ping, Watts, Radar)",
                            enabled = true,
                            onAction = { hudActive = true; updateOverlayService() },
                            offAction = { hudActive = false; updateOverlayService() }
                        )
                        ToggleRow(
                            label = "Tactical Tools Panel (Stopwatch & Kill Counter)",
                            enabled = true,
                            onAction = { toolsActive = true; updateOverlayService() },
                            offAction = { toolsActive = false; updateOverlayService() }
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

                        Text("In-Game Floating Mini-App:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "WhatsApp" to "com.whatsapp",
                                "Chrome" to "com.android.chrome",
                                "Browser" to "com.mi.globalbrowser"
                            ).forEach { (name, pkg) ->
                                FilterChip(
                                    selected = miniAppPkg == pkg,
                                    onClick = {
                                        miniAppPkg = pkg
                                        CrosshairSettings.floatingAppPkg = pkg
                                    },
                                    label = { Text(name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    SectionCard("Championship Mode & Guided Access", "Block status bar pull-down, mistouches & DND") {
                        ToggleRow(
                            label = "Championship Fullscreen Lock",
                            subtitle = "Immersive full policy, disable-flag 65536, edge gestures off",
                            enabled = isReady,
                            onAction = { exec { Actions.applyChampionshipMode(selectedGame, true) } },
                            offAction = { exec { Actions.applyChampionshipMode(selectedGame, false) } }
                        )
                    }

                    SectionCard("Panic Revert & System Snapshot", "Revert every setting back to OEM default") {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
                            onClick = { exec { Actions.revertAll(selectedGame) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                        ) {
                            Text("REVERT ALL SETTINGS TO STOCK")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.createSettingsSnapshot() } }
                            ) {
                                Text("Backup Snapshot", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.queryLiveTelemetry(selectedGame) } }
                            ) {
                                Text("Query Telemetry", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Real-Time Action Log Viewer
            SectionCard("STATUS & CONSOLE OUTPUT") {
                SelectionContainer {
                    Text(
                        text = statusLog,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF80D8FF)
                    )
                }
            }
        }
    }
}
