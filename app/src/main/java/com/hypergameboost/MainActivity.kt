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
            .height(180.dp)
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

    // GFX State
    var pgtRes by remember { mutableStateOf("1280x720") }
    var pgtQual by remember { mutableStateOf("Smooth") }
    var pgtFps by remember { mutableStateOf(120) }
    var pgtVulkan by remember { mutableStateOf(true) }
    var pgtZeroLag by remember { mutableStateOf(true) }
    var pgtPotato by remember { mutableStateOf(false) }

    // HUD Service states
    var hudActive by remember { mutableStateOf(false) }
    var toolsActive by remember { mutableStateOf(false) }
    var crosshairStyle by remember { mutableStateOf(0) }
    var auxLineActive by remember { mutableStateOf(false) }
    var fireRingActive by remember { mutableStateOf(false) }

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
            installedGamesList.firstOrNull()?.let { selectedGame = it }
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
            crosshairStyle = 0
            auxLineActive = false
            fireRingActive = false
            return
        }
        val intent = Intent(ctx, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_GAME, selectedGame)
            putExtra(OverlayService.EXTRA_HUD, hudActive)
            putExtra(OverlayService.EXTRA_TOOLS, toolsActive)
            putExtra(OverlayService.EXTRA_CROSSHAIR, crosshairStyle)
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
                    "v2.0 ESPORTS",
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
                    Text("✓ Shizuku UID 2000 Ready | HyperOS 3 Optimized", color = Color(0xFF00E676), fontSize = 12.sp)
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
                        onClick = { selectedGame = pkg },
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
                // TAB 1: BOOST (Esports 1-Tap, Watchdog, Diablo, WildBoost)
                // =============================================================
                0 -> {
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
                                    val l = Actions.launch(selectedGame)
                                    "$r\n$l"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                        ) {
                            Text("BOOST & LAUNCH GAME NOW", fontSize = 13.sp)
                        }
                    }

                    SectionCard("HyperOS 3 & Joyose Watchdog", "60s cycle kills Joyose, re-applies thermal 0 & pins PID") {
                        ToggleRow(
                            label = "Joyose 60-Second Watchdog (AutoService)",
                            subtitle = "Prevents mid-match throttling in competitive scrims",
                            enabled = isReady,
                            onAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                ctx.startForegroundService(
                                    Intent(ctx, AutoService::class.java).putExtra(AutoService.EXTRA_GAME, selectedGame)
                                )
                                statusLog = "Watchdog Service STARTED for $selectedGame"
                            },
                            offAction = {
                                ctx.stopService(Intent(ctx, AutoService::class.java))
                                exec { Actions.revertAll(selectedGame) }
                            }
                        )
                    }

                    SectionCard("Flagship Performance Governors", "Bypass conservative OEM governors") {
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

                    SectionCard("System Cleaning & Maintenance", "Free up CPU threads & storage I/O") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady && !isBusy,
                                onClick = { exec { Actions.cleanRam(emptySet(), ctx.packageName) } }
                            ) {
                                Text("Clean RAM", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady && !isBusy,
                                onClick = { exec { Actions.trimCaches() } }
                            ) {
                                Text("Trim Caches", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady && !isBusy,
                                onClick = { exec { Actions.purgeSystemServerAndTrim() } }
                            ) {
                                Text("FSTRIM Purge", fontSize = 11.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady && !isBusy,
                                onClick = { exec { Actions.pinGameProcess(selectedGame) } }
                            ) {
                                Text("Pin CPU Prime (taskset ff)", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady && !isBusy,
                                onClick = { exec { Actions.suspendHeavyApps(true) } }
                            ) {
                                Text("Suspend Social Apps", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 2: GFX / RES (PGT+ Engine, Aspect Downscale, Visuals)
                // =============================================================
                1 -> {
                    SectionCard("PGT+ (v0.25.9) Graphics Engine", "Zero-conflict configuration builder") {
                        Text("Resolution:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("800x600", "960x540", "1280x720", "1440x720", "1600x900", "1920x1080").forEach { r ->
                                FilterChip(
                                    selected = pgtRes == r,
                                    onClick = { pgtRes = r },
                                    label = { Text(r, fontSize = 11.sp) }
                                )
                            }
                        }

                        Text("Graphics Quality & FPS:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Super Smooth", "Smooth", "Balanced", "HD").forEach { q ->
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vulkan Engine:", color = Color.White, fontSize = 12.sp)
                            Switch(checked = pgtVulkan, onCheckedChange = { pgtVulkan = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Zero Lag Profile (120 FPS Lock):", color = Color.White, fontSize = 12.sp)
                            Switch(checked = pgtZeroLag, onCheckedChange = { pgtZeroLag = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Potato Graphics (Minimal Textures):", color = Color.White, fontSize = 12.sp)
                            Switch(checked = pgtPotato, onCheckedChange = { pgtPotato = it })
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady && !isBusy,
                            onClick = {
                                exec {
                                    Actions.applyPgtGraphicsConfig(
                                        game = selectedGame,
                                        resolution = pgtRes,
                                        quality = pgtQual,
                                        fps = pgtFps,
                                        vulkanRendering = pgtVulkan,
                                        zeroLag = pgtZeroLag,
                                        potatoGraphics = pgtPotato
                                    )
                                }
                            }
                        ) {
                            Text("APPLY PGT+ GRAPHICS (CONFLICT-PROOFED)")
                        }
                    }

                    SectionCard("Aspect-Ratio Hardware Downscaler", "Ban-safe hardware scaling for higher frame rates") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.applyAspectPreset(1) } }
                            ) {
                                Text("900p Pro", fontSize = 11.sp)
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.applyAspectPreset(2) } }
                            ) {
                                Text("810p Scrim", fontSize = 11.sp)
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.applyAspectPreset(3) } }
                            ) {
                                Text("720p Potato", fontSize = 11.sp)
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady,
                            onClick = { exec { Actions.applyAspectPreset(0) } }
                        ) {
                            Text("Reset Screen Resolution to Stock")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isReady,
                            onClick = { exec { Actions.launchFreeformIpadView(selectedGame) } }
                        ) {
                            Text("Launch Freeform iPad View Mode")
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
                                Text("Reset Filters")
                            }
                        }
                    }

                    SectionCard("UE4 Engine Maintenance", "Clean shader caches & pre-compile ART") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.cleanUe4StutterLogs(selectedGame) } }
                            ) {
                                Text("Purge UE4 Logs", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.artSpeedCompile(selectedGame) } }
                            ) {
                                Text("ART Speed Compile", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 3: TOUCH / AUDIO (Impulse Touch, Test Pad, Footstep EQ)
                // =============================================================
                2 -> {
                    SectionCard("Android 16 Raw 1:1 Impulse Touch", "Disables touch filtering delay & sets 0 slop") {
                        ToggleRow(
                            label = "Impulse Touch & HyperOS Touch IC",
                            subtitle = "Sets gamemode_touch_follow_response 100, aim 90, stability 100",
                            enabled = isReady,
                            onAction = { exec { Actions.applyImpulseTouchAndTouchIc(true) } },
                            offAction = { exec { Actions.applyImpulseTouchAndTouchIc(false) } }
                        )
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
                // TAB 4: HUD / NET (Draggable Overlays, Firewall, DNS, Panic)
                // =============================================================
                3 -> {
                    SectionCard("Esports Overlays & HUD Suite", "Real-time in-game telemetry & tactical aids") {
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
                            label = "Draggable Telemetry HUD (FPS, Ping, Temp, GPU)",
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

                        Text("Esports Custom Crosshair:", color = Color.White, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("OFF" to 0, "Dot" to 1, "Cross" to 2, "Circle" to 3).forEach { (name, style) ->
                                FilterChip(
                                    selected = crosshairStyle == style,
                                    onClick = {
                                        crosshairStyle = style
                                        updateOverlayService()
                                    },
                                    label = { Text(name, fontSize = 11.sp) }
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    ctx.startForegroundService(
                                        Intent(ctx, LobbyTimerService::class.java)
                                            .putExtra(LobbyTimerService.EXTRA_GAME, selectedGame)
                                            .putExtra(LobbyTimerService.EXTRA_SECONDS, 45)
                                    )
                                }
                            ) {
                                Text("45s Flash Return", fontSize = 11.sp)
                            }
                        }
                    }

                    SectionCard("Low-Ping Network Firewall & DNS", "Eliminate background data consumption") {
                        ToggleRow(
                            label = "Network Bandwidth Firewall",
                            subtitle = "cmd netpolicy whitelist only active PUBG UID",
                            enabled = isReady,
                            onAction = { exec { Actions.applyLowPingNetworkFirewall(selectedGame, true) } },
                            offAction = { exec { Actions.applyLowPingNetworkFirewall(selectedGame, false) } }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.setGamingDns("cloudflare") } }
                            ) {
                                Text("Cloudflare 1.1.1.1", fontSize = 11.sp)
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.setGamingDns("google") } }
                            ) {
                                Text("Google 8.8.8.8", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = isReady,
                                onClick = { exec { Actions.setGamingDns("auto") } }
                            ) {
                                Text("Auto DNS", fontSize = 11.sp)
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
