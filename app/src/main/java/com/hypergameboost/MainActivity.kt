package com.hypergameboost

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(this)
        AudioTest.init(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF07110C)
                ) {
                    RealmeGameSpaceApp()
                }
            }
        }
    }
}

fun triggerVibe(context: Context, millis: Long = 18) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        }
    } catch (_: Throwable) {}
}

@Composable
fun RealmeGameSpaceApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    AppPrefs.init(ctx)
    AudioTest.init(ctx)

    var shizukuState by remember { mutableIntStateOf(ShizukuState.current()) }
    var selectedGame by remember { mutableStateOf(AppPrefs.getSelectedPkg()) }
    var installedGamesList by remember { mutableStateOf(listOf("com.tencent.ig")) }
    var statusLog by remember { mutableStateOf("⚡ Initializing Realme GT Game Space...") }

    // Section 1: Realme 3-Way Mode (0 = Low Power, 1 = Balanced, 2 = GT Mode)
    var perfMode by remember { mutableIntStateOf(AppPrefs.getPerformanceMode(selectedGame)) }

    // Section 2: Realme Interactive Sliders
    var touchAimVal by remember { mutableFloatStateOf(AppPrefs.getTouchAimSensitivity(selectedGame).toFloat()) }
    var swipeJiggleVal by remember { mutableFloatStateOf(AppPrefs.getSwipeJiggleResponse(selectedGame).toFloat()) }
    var tapStabilityVal by remember { mutableFloatStateOf(AppPrefs.getTapStabilityRecoil(selectedGame).toFloat()) }
    var resStepIdx by remember { mutableIntStateOf(AppPrefs.getResolutionStepIndex(selectedGame)) }

    // Section 3: TSOML GFX State
    var tsomlRes by remember { mutableStateOf(AppPrefs.getTsomlResolution(selectedGame)) }
    var tsomlGfx by remember { mutableStateOf(AppPrefs.getTsomlGraphics(selectedGame)) }
    var tsomlFps by remember { mutableIntStateOf(AppPrefs.getTsomlFps(selectedGame)) }
    var tsomlPotato by remember { mutableStateOf(AppPrefs.getTsomlPotatoLod(selectedGame)) }
    var tsomlBilinear by remember { mutableStateOf(AppPrefs.getTsomlBilinear(selectedGame)) }
    var tsomlSimpleShader by remember { mutableStateOf(AppPrefs.getTsomlSimpleShader(selectedGame)) }
    var tsomlDriver by remember { mutableStateOf(AppPrefs.getTsomlDriver(selectedGame)) }
    var tsomlSaveControls by remember { mutableStateOf(AppPrefs.getTsomlSaveControls(selectedGame)) }

    // Section 4: Realme Dolby Footstep & Audio
    var dolbyPreset by remember { mutableIntStateOf(AppPrefs.getDolbyPreset(selectedGame)) }
    var cleanVolPercent by remember { mutableFloatStateOf(AppPrefs.getCleanVolumePercent(selectedGame).toFloat()) }
    var splitterGuard by remember { mutableStateOf(AppPrefs.getSplitterGuardOn(selectedGame)) }

    fun refreshFromPrefs(game: String) {
        perfMode = AppPrefs.getPerformanceMode(game)
        touchAimVal = AppPrefs.getTouchAimSensitivity(game).toFloat()
        swipeJiggleVal = AppPrefs.getSwipeJiggleResponse(game).toFloat()
        tapStabilityVal = AppPrefs.getTapStabilityRecoil(game).toFloat()
        resStepIdx = AppPrefs.getResolutionStepIndex(game)
        tsomlRes = AppPrefs.getTsomlResolution(game)
        tsomlGfx = AppPrefs.getTsomlGraphics(game)
        tsomlFps = AppPrefs.getTsomlFps(game)
        tsomlPotato = AppPrefs.getTsomlPotatoLod(game)
        tsomlBilinear = AppPrefs.getTsomlBilinear(game)
        tsomlSimpleShader = AppPrefs.getTsomlSimpleShader(game)
        tsomlDriver = AppPrefs.getTsomlDriver(game)
        tsomlSaveControls = AppPrefs.getTsomlSaveControls(game)
        dolbyPreset = AppPrefs.getDolbyPreset(game)
        cleanVolPercent = AppPrefs.getCleanVolumePercent(game).toFloat()
        splitterGuard = AppPrefs.getSplitterGuardOn(game)
    }

    DisposableEffect(Unit) {
        val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuState = ShizukuState.current()
        }
        val binderListener = Shizuku.OnBinderReceivedListener {
            shizukuState = ShizukuState.current()
        }
        val deadListener = Shizuku.OnBinderDeadListener {
            shizukuState = ShizukuState.NOT_RUNNING
        }

        try {
            Shizuku.addRequestPermissionResultListener(permListener)
            Shizuku.addBinderReceivedListener(binderListener)
            Shizuku.addBinderDeadListener(deadListener)
        } catch (_: Throwable) {}

        onDispose {
            try {
                Shizuku.removeRequestPermissionResultListener(permListener)
                Shizuku.removeBinderReceivedListener(binderListener)
                Shizuku.removeBinderDeadListener(deadListener)
            } catch (_: Throwable) {}
        }
    }

    // Auto-Apply on App Open when Shizuku is Ready!
    LaunchedEffect(shizukuState) {
        if (shizukuState == ShizukuState.READY) {
            installedGamesList = Actions.installedGames()
            if (!installedGamesList.contains(selectedGame)) {
                installedGamesList.firstOrNull()?.let {
                    selectedGame = it
                    AppPrefs.setSelectedPkg(it)
                    refreshFromPrefs(it)
                }
            }
            scope.launch {
                statusLog = "⚡ Auto-applying Realme GT Game Space Master Profile..."
                val res = Actions.executeMasterGtBoost(ctx, selectedGame, applyDisplayResolution = false, launchGame = false)
                statusLog = "✅ $res"
            }
        }
    }

    val emerald = Color(0xFF00E676)
    val darkCard = Color(0xFF0E1A14)
    val borderEmerald = Color(0xFF1B3D2B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "REALME GAME SPACE",
                    color = emerald,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "GT MODE PRO v18.0 • Snapdragon 8s Gen 4",
                    color = Color(0xFF80CBC4),
                    fontSize = 11.sp
                )
            }

            // Shizuku Status Pill
            val (statusText, statusBg, statusFg) = when (shizukuState) {
                ShizukuState.READY -> Triple("Shizuku Ready", Color(0xFF004D25), emerald)
                ShizukuState.NO_PERMISSION -> Triple("No Permission", Color(0xFF4A3B00), Color(0xFFFFD54F))
                else -> Triple("Shizuku Off", Color(0xFF4A1010), Color(0xFFFF5252))
            }
            Surface(
                color = statusBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, statusFg.copy(alpha = 0.5f))
            ) {
                Text(
                    text = statusText,
                    color = statusFg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // STATUS LOG BANNER
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF0A140F),
            border = BorderStroke(1.dp, borderEmerald)
        ) {
            Text(
                text = statusLog,
                color = Color(0xFFB2DFDB),
                fontSize = 11.sp,
                modifier = Modifier.padding(10.dp)
            )
        }

        // =====================================================================
        // SECTION 1: REALME GAME SPACE HEADER & 1-TAP MASTER LAUNCHER
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = darkCard),
            border = BorderStroke(1.5.dp, emerald),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🔥 SECTION 1: REALME GT PERFORMANCE & 1-TAP LAUNCHER",
                    color = emerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // 1. Game Variant Selector Bar
                Text("Select Game Variant:", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val gameVariants = listOf(
                        "com.tencent.ig" to "PUBG Global",
                        "com.pubg.imobile" to "BGMI",
                        "com.pubg.krmobile" to "Korea",
                        "com.vng.pubgmobile" to "Vietnam",
                        "com.rekoo.pubgm" to "Taiwan",
                        "com.tencent.tmgp.pubgmhd" to "China"
                    )
                    gameVariants.forEach { (pkg, label) ->
                        val isSel = selectedGame == pkg
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                triggerVibe(ctx)
                                selectedGame = pkg
                                AppPrefs.setSelectedPkg(pkg)
                                refreshFromPrefs(pkg)
                                statusLog = "🎯 Game target set to $label ($pkg)"
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = emerald,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                // 2. Realme 3-Way Mode Selector
                Text("Realme Performance Mode:", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        Triple(0, "🔋 Low Power", "60Hz Standard"),
                        Triple(1, "⚖️ Balanced", "90/120Hz"),
                        Triple(2, "🔥 GT MODE", "120Hz Max Perf")
                    )
                    modes.forEach { (m, title, sub) ->
                        val isSel = perfMode == m
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                triggerVibe(ctx)
                                perfMode = m
                                AppPrefs.setPerformanceMode(selectedGame, m)
                                scope.launch {
                                    val res = Actions.applyRealmePerformanceMode(selectedGame, m)
                                    statusLog = "✅ $res"
                                    Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) emerald else Color.Transparent,
                                contentColor = if (isSel) Color.Black else Color.White
                            ),
                            border = BorderStroke(1.dp, if (isSel) emerald else Color(0xFF264D36)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(sub, fontSize = 8.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Master Button: 🚀 BOOST & LAUNCH
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = {
                        triggerVibe(ctx)
                        statusLog = "🚀 Executing 1-Tap High Performance GT Boost & Starting PUBG..."
                        scope.launch {
                            val res = Actions.executeMasterGtBoostAndLaunch(ctx, selectedGame)
                            statusLog = res
                            Toast.makeText(ctx, "🚀 GT Mode Applied & Game Started!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = emerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "🚀 1-TAP HIGH PERFORMANCE BOOST & LAUNCH PUBG NOW",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }

                // Primary Master Button: ♻️ 1-TAP FACTORY RESET
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    onClick = {
                        triggerVibe(ctx)
                        statusLog = "♻️ Reverting phone 100% to stock 1.5K display & factory defaults..."
                        scope.launch {
                            val res = Actions.executeFactoryFreshReset(ctx, selectedGame)
                            statusLog = res
                            resStepIdx = 5
                            AppPrefs.setResolutionStepIndex(selectedGame, 5)
                            Toast.makeText(ctx, "Phone restored to factory stock!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90A4AE)),
                    border = BorderStroke(1.dp, Color(0xFF37474F)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "♻️ 1-TAP FACTORY FRESH RESET (REVERT PHONE TO 100% STOCK)",
                        fontSize = 11.sp
                    )
                }
            }
        }

        // =====================================================================
        // SECTION 2: REALME PRO TOUCH, SWIPE & RESOLUTION SLIDING BARS
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = darkCard),
            border = BorderStroke(1.dp, borderEmerald),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⚡ SECTION 2: REALME PRO TOUCH & RESOLUTION SLIDING BARS",
                    color = emerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // Slider 1: Touch & Aim Sensitivity Bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🎯 Touch & Aim Sensitivity:", color = Color(0xFFECEFF1), fontSize = 11.sp)
                        Text("${touchAimVal.toInt()}% (IC Level: ${(touchAimVal / 20).toInt().coerceIn(1, 5)}/5)", color = emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = touchAimVal,
                        onValueChange = { touchAimVal = it },
                        onValueChangeFinished = {
                            val v = touchAimVal.toInt()
                            AppPrefs.setTouchAimSensitivity(selectedGame, v)
                            scope.launch {
                                val r = Actions.applyRealmeTouchAimSlider(v)
                                statusLog = "✅ $r"
                            }
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = emerald, activeTrackColor = emerald)
                    )
                }

                // Slider 2: Swipe & Jiggle Responsiveness Bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("⚡ Swipe & Jiggle Responsiveness:", color = Color(0xFFECEFF1), fontSize = 11.sp)
                        Text("${swipeJiggleVal.toInt()}% / 480Hz", color = emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = swipeJiggleVal,
                        onValueChange = { swipeJiggleVal = it },
                        onValueChangeFinished = {
                            val v = swipeJiggleVal.toInt()
                            AppPrefs.setSwipeJiggleResponse(selectedGame, v)
                            scope.launch {
                                val r = Actions.applyRealmeSwipeJiggleSlider(selectedGame, v)
                                statusLog = "✅ $r"
                            }
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = emerald, activeTrackColor = emerald)
                    )
                }

                // Slider 3: Tap Stability & Recoil Control Bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🛡️ Tap Stability & Recoil Control:", color = Color(0xFFECEFF1), fontSize = 11.sp)
                        Text("${tapStabilityVal.toInt()}% (Edge: 0)", color = emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = tapStabilityVal,
                        onValueChange = { tapStabilityVal = it },
                        onValueChangeFinished = {
                            val v = tapStabilityVal.toInt()
                            AppPrefs.setTapStabilityRecoil(selectedGame, v)
                            scope.launch {
                                val r = Actions.applyRealmeTapStabilitySlider(v)
                                statusLog = "✅ $r"
                            }
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = emerald, activeTrackColor = emerald)
                    )
                }

                // Slider 4: Realme Resolution & Scrims Downscale Bar (5 Discrete Steps)
                val curStep = AppPrefs.getResolutionStep(resStepIdx)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("📐 Resolution & Scrims Scaling:", color = Color(0xFFECEFF1), fontSize = 11.sp)
                        Text(curStep.label, color = emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = resStepIdx.toFloat(),
                        onValueChange = { resStepIdx = it.toInt() },
                        onValueChangeFinished = {
                            AppPrefs.setResolutionStepIndex(selectedGame, resStepIdx)
                            scope.launch {
                                val r = Actions.applyRealmeResolutionStep(selectedGame, resStepIdx)
                                statusLog = "✅ $r"
                                Toast.makeText(ctx, r, Toast.LENGTH_SHORT).show()
                            }
                        },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = emerald, activeTrackColor = emerald)
                    )
                    Text(
                        text = "Step $resStepIdx: ${curStep.wmSize} @ ${curStep.density} DPI (Hardware Downscale: ${curStep.downscaleFactor})",
                        color = Color(0xFF80CBC4),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // =====================================================================
        // SECTION 3: TSOML GFX TOOL PRO CARD
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = darkCard),
            border = BorderStroke(1.dp, borderEmerald),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🎮 SECTION 3: TSOML GFX TOOL PRO (PRE-LOCKED COMPETITIVE)",
                    color = emerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // Resolution
                Text("Resolution:", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val resolutions = listOf("768x432", "800x600", "960x540", "1280x720 (HD)", "1440x720 (HD+)", "1920x1080")
                    resolutions.forEach { res ->
                        val isSel = tsomlRes == res
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                triggerVibe(ctx)
                                tsomlRes = res
                                AppPrefs.setTsomlResolution(selectedGame, res)
                            },
                            label = { Text(res, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = emerald, selectedLabelColor = Color.Black)
                        )
                    }
                }

                // Graphics & FPS
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Graphics Quality:", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                        val gfxOptions = listOf("Super Smooth", "Smooth", "Balanced", "HD")
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            gfxOptions.forEach { g ->
                                val isSel = tsomlGfx == g
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        triggerVibe(ctx)
                                        tsomlGfx = g
                                        AppPrefs.setTsomlGraphics(selectedGame, g)
                                    },
                                    label = { Text(g, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = emerald, selectedLabelColor = Color.Black)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Target FPS:", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                        val fpsOptions = listOf(60, 90, 120)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            fpsOptions.forEach { fps ->
                                val isSel = tsomlFps == fps
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        triggerVibe(ctx)
                                        tsomlFps = fps
                                        AppPrefs.setTsomlFps(selectedGame, fps)
                                    },
                                    label = { Text("${fps}FPS", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = emerald, selectedLabelColor = Color.Black)
                                )
                            }
                        }
                    }
                }

                // Potato LOD
                Text("Potato Foliage / Objects LOD:", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lods = listOf("Off", "Smooth (LOD +2)", "Ultra (LOD +3)")
                    lods.forEach { lod ->
                        val isSel = tsomlPotato == lod
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                triggerVibe(ctx)
                                tsomlPotato = lod
                                AppPrefs.setTsomlPotatoLod(selectedGame, lod)
                            },
                            label = { Text(lod, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = emerald, selectedLabelColor = Color.Black)
                        )
                    }
                }

                // Toggles: Bilinear, Simple Shader, Save Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bilinear Texture Filtering", color = Color(0xFFECEFF1), fontSize = 11.sp)
                    Switch(
                        checked = tsomlBilinear,
                        onCheckedChange = {
                            triggerVibe(ctx)
                            tsomlBilinear = it
                            AppPrefs.setTsomlBilinear(selectedGame, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = emerald, checkedTrackColor = Color(0xFF1B3D2B))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Simple Shader & Shadows Disable", color = Color(0xFFECEFF1), fontSize = 11.sp)
                    Switch(
                        checked = tsomlSimpleShader,
                        onCheckedChange = {
                            triggerVibe(ctx)
                            tsomlSimpleShader = it
                            AppPrefs.setTsomlSimpleShader(selectedGame, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = emerald, checkedTrackColor = Color(0xFF1B3D2B))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Save Controls (Never Delete Active.sav)", color = Color(0xFFECEFF1), fontSize = 11.sp)
                    Switch(
                        checked = tsomlSaveControls,
                        onCheckedChange = {
                            triggerVibe(ctx)
                            tsomlSaveControls = it
                            AppPrefs.setTsomlSaveControls(selectedGame, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = emerald, checkedTrackColor = Color(0xFF1B3D2B))
                    )
                }

                // 1-Tap Apply GFX Button
                Button(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    onClick = {
                        triggerVibe(ctx)
                        statusLog = "⚡ Applying TSOML GFX ($tsomlRes, $tsomlGfx, ${tsomlFps}FPS)..."
                        scope.launch {
                            val res = Actions.applyTsomlGfxConfig(
                                game = selectedGame,
                                res = tsomlRes,
                                gfx = tsomlGfx,
                                fps = tsomlFps,
                                potatoLod = tsomlPotato,
                                bilinear = tsomlBilinear,
                                simpleShader = tsomlSimpleShader,
                                driver = tsomlDriver,
                                saveControls = tsomlSaveControls
                            )
                            statusLog = "✅ $res"
                            Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3D2B)),
                    border = BorderStroke(1.dp, emerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⚡ APPLY GFX SETTINGS (0666 Safe UE4 Lock)", color = emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // =====================================================================
        // SECTION 4: REALME DOLBY FOOTSTEP STUDIO & TYPE-C SPLITTER GUARD
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = darkCard),
            border = BorderStroke(1.dp, borderEmerald),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🎧 SECTION 4: REALME DOLBY FOOTSTEP STUDIO & AUDIO BOOST",
                    color = emerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // Dolby Atmos Preset Selector
                Text("Realme Dolby Atmos Preset:", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        2 to "🗣️ Dolby Voice / Scout",
                        1 to "👣 Full Tep-Tep",
                        3 to "🌐 3D Spatial",
                        4 to "🎧 IEM Studio",
                        0 to "❌ Off"
                    )
                    presets.forEach { (id, title) ->
                        val isSel = dolbyPreset == id
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                triggerVibe(ctx)
                                dolbyPreset = id
                                AppPrefs.setDolbyPreset(selectedGame, id)
                                scope.launch {
                                    val r = Actions.applyRealmeAudioAndSplitterGuard(
                                        context = ctx,
                                        dolbyPreset = id,
                                        volumePercent = cleanVolPercent.toInt(),
                                        splitterGuard = splitterGuard
                                    )
                                    statusLog = "✅ $r"
                                }
                            },
                            label = { Text(title, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = emerald, selectedLabelColor = Color.Black)
                        )
                    }
                }

                // Clean Volume Booster Slider (0% to 100% | Default: 70%)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Clean Volume Boost Slider:", color = Color(0xFFECEFF1), fontSize = 11.sp)
                        Text("${cleanVolPercent.toInt()}% (Capped @ 400mB — Zero Distortion)", color = emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = cleanVolPercent,
                        onValueChange = { cleanVolPercent = it },
                        onValueChangeFinished = {
                            val v = cleanVolPercent.toInt()
                            AppPrefs.setCleanVolumePercent(selectedGame, v)
                            scope.launch {
                                AudioTest.setCleanVolumeBoost(ctx, true, v)
                                statusLog = "🎧 Clean Volume Boost set to $v% (Safe Headroom Locked)"
                            }
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = emerald, activeTrackColor = emerald)
                    )
                }

                // Type-C Dual Splitter Power Guard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Type-C Dual Splitter Power Guard", color = Color(0xFFECEFF1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Prevents DAC disconnects & audio stutter during simultaneous charging", color = Color(0xFF80CBC4), fontSize = 9.sp)
                    }
                    Switch(
                        checked = splitterGuard,
                        onCheckedChange = {
                            triggerVibe(ctx)
                            splitterGuard = it
                            AppPrefs.setSplitterGuardOn(selectedGame, it)
                            scope.launch {
                                Actions.applyRealmeAudioAndSplitterGuard(ctx, dolbyPreset, cleanVolPercent.toInt(), it)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = emerald, checkedTrackColor = Color(0xFF1B3D2B))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
