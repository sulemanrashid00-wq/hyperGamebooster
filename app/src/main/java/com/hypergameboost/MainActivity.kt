package com.hypergameboost

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(this)
        AudioTest.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF080E18))) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080E18)) {
                    MainScreen()
                }
            }
        }
    }
}

fun triggerVibe(ctx: Context, ms: Long = 25) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            v?.vibrate(ms)
        }
    } catch (_: Throwable) {}
}

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shizukuReady by remember { mutableStateOf(ShizukuState.current() == ShizukuState.READY) }
    var selectedGame by remember { mutableStateOf(Games.known.keys.first()) }
    var gamesList by remember { mutableStateOf(listOf("com.tencent.ig")) }
    var activeTab by remember { mutableStateOf(0) }
    var statusLog by remember { mutableStateOf("⚡ System Ready (v13.0 Clean One-Click). Tap any feature to activate.") }
    var cleanBoostPct by remember { mutableIntStateOf(AppPrefs.getCleanBoostPct(selectedGame)) }
    var activeDolby by remember { mutableIntStateOf(AppPrefs.getDolbyPreset(selectedGame)) }
    var crosshairActive by remember { mutableStateOf(CrosshairSettings.style > 0) }

    DisposableEffect(Unit) {
        val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> shizukuReady = ShizukuState.current() == ShizukuState.READY }
        val binderListener = Shizuku.OnBinderReceivedListener { shizukuReady = ShizukuState.current() == ShizukuState.READY }
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permListener)
            Shizuku.removeBinderReceivedListener(binderListener)
        }
    }

    LaunchedEffect(shizukuReady) {
        if (shizukuReady) {
            val installed = Actions.installedGames()
            gamesList = installed
            if (installed.isNotEmpty()) selectedGame = installed.first()
        }
    }

    fun exec(block: () -> String) {
        triggerVibe(ctx)
        scope.launch {
            val res = try { block() } catch (e: Throwable) { "ERR: ${e.message}" }
            statusLog = res
            Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HYPERGAMEBOOSTER",
                    color = Color(0xFF00FF66),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
                Text("Redmi Turbo 4 Pro • HyperOS 3 • Snapdragon 8s Gen 4", color = Color(0xFF90A4AE), fontSize = 10.sp)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF142436),
                border = BorderStroke(1.dp, Color(0xFF00E5FF))
            ) {
                Text(
                    text = "v13.0 ONE-CLICK",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        // SHIZUKU STATUS / GAME SELECTOR ROW
        if (!shizukuReady) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261215)),
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("⚠️ Shizuku Permission Needed", color = Color(0xFFFF5252), fontSize = 11.5.sp)
                    Button(
                        onClick = {
                            try {
                                if (Shizuku.pingBinder()) Shizuku.requestPermission(1001)
                            } catch (_: Throwable) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Grant", fontSize = 10.5.sp)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Target Game:", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    gamesList.forEach { pkg ->
                        FilterChip(
                            selected = selectedGame == pkg,
                            onClick = { selectedGame = pkg },
                            label = { Text(Games.known[pkg] ?: pkg, fontSize = 10.5.sp) }
                        )
                    }
                }
            }
        }

        // TABS NAVIGATION (4 Clean Tabs)
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF0C131D),
            contentColor = Color(0xFF00FF66)
        ) {
            listOf("1. BOOST", "2. GFX / VIEW", "3. TOUCH / AUD", "4. WIFI / HUD").forEachIndexed { index, title ->
                Tab(
                    selected = activeTab == index,
                    onClick = { activeTab = index },
                    text = { Text(title, fontSize = 11.sp, fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        // TAB CONTENT
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (activeTab) {
                // =============================================================
                // TAB 1: ONE-CLICK BOOST
                // =============================================================
                0 -> {
                    // Card 1: Hero Boost Actions
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.5.dp, Color(0xFF00FF66)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🚀 1-CLICK ULTIMATE 120 FPS BOOST & LAUNCH", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Unlocks 120Hz refresh rate, unblocks native 120 FPS, halts Joyose, sets 2560Hz touch & FastConnect Wi-Fi.", color = Color(0xFF90A4AE), fontSize = 10.5.sp)

                            Button(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                onClick = { exec { Actions.applyOneClickMaxBoost(selectedGame) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("⚡ 1-CLICK MAX BOOST (ALL-IN-ONE)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                onClick = { exec { Actions.launchGameWithBoost(ctx, selectedGame) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🎮 BOOST & LAUNCH PUBG NOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                onClick = { exec { Actions.restoreToStockAndFix90Fps(selectedGame) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2836)),
                                border = BorderStroke(1.dp, Color(0xFFFFD600)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("♻️ 1-CLICK FIX 90FPS BUG & RESTORE STOCK", color = Color(0xFFFFD600), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                    }

                    // Card 2: GPU Driver & Compilation
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.dp, Color(0xFF1D293A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚡ GPU DRIVER & AOT COMPILATION", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.setGraphicsDriverType(selectedGame, "game_driver") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("🎮 Game Driver", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.setGraphicsDriverType(selectedGame, "system") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("⚡ System Driver", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                onClick = { exec { Actions.applyAotSpeedCompile(selectedGame) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A38)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("🔥 AOT Speed Compile (Zero Enemy-Render Lag)", color = Color.White, fontSize = 10.5.sp)
                            }
                        }
                    }

                    // Card 3: Live OS Status Console
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.dp, Color(0xFF1D293A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔬 LIVE HARDWARE VERIFICATION", color = Color(0xFFB0BEC5), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Button(
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                onClick = { exec { Actions.checkActiveOsStatus(selectedGame) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EA)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("🔍 CHECK ACTIVE OS STATUS (120Hz / wm size / Joyose)", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 2: GFX & VIEW
                // =============================================================
                1 -> {
                    // Card 1: Hardware Downscaler
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📐 HARDWARE DISPLAY DOWNSCALER (wm size)", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Direct hardware panel downscaling for buttery smooth, cool gameplay with zero GPU throttling.", color = Color(0xFF90A4AE), fontSize = 10.5.sp)

                            listOf(
                                "🥔 1-Click Ultra-Potato 720p (Max Smoothness)" to 1,
                                "🔥 1-Click Scrims 810p (High FPS + Clarity)" to 2,
                                "⚡ 1-Click Esports 900p Pro (Exact 20:9 Ratio)" to 3,
                                "📱 1-Click Stretched Hitbox 1080x2160 (Wider Targets)" to 4,
                                "🔙 1-Click Reset Native 1.5K Display (1220x2712)" to 0
                            ).forEach { (label, preset) ->
                                Button(
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    onClick = { exec { Actions.applyHardwareResolution(preset, selectedGame) } },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (preset == 0) Color(0xFF1E2836) else if (preset == 4) Color(0xFF00E5FF) else Color(0xFF142436)
                                    ),
                                    border = BorderStroke(1.dp, if (preset == 4) Color(0xFF00E5FF) else Color(0xFF263238)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (preset == 4) Color.Black else Color.White,
                                        fontWeight = if (preset == 4 || preset == 1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Visual Filters & Notch Bypass
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.dp, Color(0xFF1D293A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("👁️ CAMPER VISUAL FILTERS & CUTOUT BYPASS", color = Color(0xFFFFD600), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.applyCamperFilter("vivid") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    border = BorderStroke(1.dp, Color(0xFF00FF66)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("🌿 Vivid 1.5x", fontSize = 10.sp, color = Color(0xFF00FF66))
                                }
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.applyCamperFilter("scout_invert") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("👁️ Scout Invert", fontSize = 10.sp, color = Color(0xFFFF5252))
                                }
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.applyCamperFilter("reset") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Stock 1.0x", fontSize = 10.sp, color = Color.White)
                                }
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                onClick = { exec { Actions.setCutoutBypass(selectedGame, true) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2836)),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("📱 Force Fullscreen Cutout Notch Bypass", color = Color(0xFF00E5FF), fontSize = 10.5.sp)
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 3: TOUCH & AUDIO
                // =============================================================
                2 -> {
                    // Card 1: Max Touch & Recoil Response
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.5.dp, Color(0xFF00FF66)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎯 MAX TOUCH & RECOIL RESPONSE (0ms Delay)", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Zeroes touch slop, disables touch resampling, sets 2560Hz polling, and enables 1000Hz raw gyroscope.", color = Color(0xFF90A4AE), fontSize = 10.5.sp)

                            Button(
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                onClick = { exec { Actions.applyMaxTouchAndAimLock(selectedGame) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("⚡ 1-CLICK MAX TOUCH + AIM LOCK (0ms)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }

                            Button(
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                onClick = { exec { Actions.restoreNormalTouch() } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2836)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🔙 Restore Normal Touch & Animations", color = Color.White, fontSize = 10.5.sp)
                            }
                        }
                    }

                    // Card 2: Realme Dolby Atmos & Clean Volume Slider
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.dp, Color(0xFF1D293A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎧 REALME DOLBY ATMOS & CLEAN VOLUME", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Subtractive bass-cut isolator elevates footsteps cleanly without digital clipping.", color = Color(0xFF90A4AE), fontSize = 10.5.sp)

                            listOf(
                                1 to "🎯 Dolby Gaming (Full Tep-Tep + 3D)",
                                2 to "🗣️ Voice / Scout (Pure Footsteps)",
                                3 to "🌌 3D Spatial Surround Radar",
                                0 to "🔇 Dolby EQ Off (Stock)"
                            ).forEach { (id, label) ->
                                val isSelected = activeDolby == id
                                Button(
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    onClick = {
                                        activeDolby = id
                                        AppPrefs.setDolbyPreset(selectedGame, id)
                                        exec { Actions.applyHardwareDolbyAtmosPreset(id) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) Color(0xFF00FF66) else Color(0xFF142436)),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF00FF66) else Color(0xFF263238)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text("🔊 Clean Volume Boost Slider: ${cleanBoostPct}%", color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = cleanBoostPct.toFloat(),
                                onValueChange = { cleanBoostPct = it.toInt() },
                                onValueChangeFinished = {
                                    AppPrefs.setCleanBoostPct(selectedGame, cleanBoostPct)
                                    exec { AudioTest.setCleanVolumeBoost(ctx, cleanBoostPct > 0, cleanBoostPct) }
                                },
                                valueRange = 0f..100f
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    onClick = {
                                        triggerVibe(ctx)
                                        AudioTest.playStereoCheck(true)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("🎧 Left (520Hz)", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }
                                Button(
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    onClick = {
                                        triggerVibe(ctx)
                                        AudioTest.playStereoCheck(false)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("🎧 Right (780Hz)", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 4: WIFI & HUD
                // =============================================================
                3 -> {
                    // Card 1: FastConnect Wi-Fi & DNS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📡 FASTCONNECT LOW-LATENCY WI-FI & GAMING DNS", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Button(
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                onClick = { exec { Actions.applyFastConnectWifiLock() } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📡 1-CLICK LOCK WI-FI LOW-LATENCY + RADIO FLUSH", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.setGamingDns("cloudflare") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("🌐 1.1.1.1 DNS", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.setGamingDns("google") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("🌐 8.8.8.8 DNS", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }
                                Button(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    onClick = { exec { Actions.setGamingDns("reset") } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF142436)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Stock DNS", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Card 2: Floating HUD & Crosshair
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2A)),
                        border = BorderStroke(1.dp, Color(0xFF1D293A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎯 FLOATING ESPORTS HUD & RETICLES", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    onClick = {
                                        if (!Settings.canDrawOverlays(ctx)) {
                                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                                        } else {
                                            triggerVibe(ctx)
                                            try {
                                                ctx.startForegroundService(
                                                    Intent(ctx, OverlayService::class.java)
                                                        .putExtra(OverlayService.EXTRA_GAME, selectedGame)
                                                        .putExtra(OverlayService.EXTRA_HUD, true)
                                                        .putExtra(OverlayService.EXTRA_TOOLS, true)
                                                )
                                                statusLog = "🎯 Floating Cyber HUD Started!"
                                            } catch (_: Throwable) {}
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("▶ Start HUD", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    onClick = {
                                        triggerVibe(ctx)
                                        ctx.stopService(Intent(ctx, OverlayService::class.java))
                                        statusLog = "⏹ Floating HUD Stopped"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2836)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("⏹ Stop HUD", fontSize = 11.sp, color = Color.White)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "Dot" to 1,
                                    "Cross" to 2,
                                    "Circle" to 3,
                                    "T-Cross" to 4,
                                    "Off" to 0
                                ).forEach { (name, styleId) ->
                                    FilterChip(
                                        selected = CrosshairSettings.style == styleId,
                                        onClick = {
                                            CrosshairSettings.style = styleId
                                            crosshairActive = styleId > 0
                                        },
                                        label = { Text(name, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // STATUS CONSOLE FOOTER
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF040810),
            border = BorderStroke(1.dp, Color(0xFF1D293A))
        ) {
            Text(
                text = statusLog,
                modifier = Modifier.padding(10.dp),
                color = Color(0xFF00FF66),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )
        }
    }
}
