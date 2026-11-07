package com.hypergameboost

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun OnOff(label: String, enabled: Boolean, onOn: () -> Unit, onOff: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = onOn) { Text("ON") }
            OutlinedButton(enabled = enabled, onClick = onOff) { Text("OFF") }
        }
    }
}

/** Dikhata hai ke screen app ko kitni Hz par touch events deti hai (raw samples ke intervals se). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchTester() {
    var result by remember { mutableStateOf("Is pad par ungli tez aur lambi ghumayein, phir chhodein.") }
    val times = remember { ArrayList<Long>() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFF263238))
            .pointerInteropFilter { ev ->
                val nanos = Build.VERSION.SDK_INT >= 34
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
                        val d = times.zipWithNext { a, b -> b - a }.filter { it > 0L }
                        result = if (d.size >= 8) {
                            val med = d.sorted()[d.size / 2] / 1_000_000.0
                            "Touch report rate: ~${"%.0f".format(1000.0 / med)} Hz (median ${"%.2f".format(med)} ms, ${d.size} samples)"
                        } else {
                            "Kam samples. Pad par ungli tez aur lambi ghumayein."
                        }
                    }
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        Text(result, color = Color.White)
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var shizuku by remember { mutableStateOf(ShizukuState.current()) }
    var games by remember { mutableStateOf(listOf<String>()) }
    var selected by remember { mutableStateOf(Games.known.keys.first()) }
    var log by remember { mutableStateOf("Taiyar.") }
    var busy by remember { mutableStateOf(false) }
    var dpiText by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }
    var nativeReport by remember { mutableStateOf(NativeTools.deviceReport(ctx)) }
    var tab by remember { mutableStateOf(0) }
    var ds by remember { mutableStateOf("0.7") }
    var fps by remember { mutableStateOf(120) }
    var hudOn by remember { mutableStateOf(false) }
    var toolsOn by remember { mutableStateOf(false) }
    var sat by remember { mutableStateOf("1.0") }

    DisposableEffect(Unit) {
        val l = Shizuku.OnRequestPermissionResultListener { _, _ -> shizuku = ShizukuState.current() }
        val b = Shizuku.OnBinderReceivedListener { shizuku = ShizukuState.current() }
        val d = Shizuku.OnBinderDeadListener { shizuku = ShizukuState.current() }
        Shizuku.addRequestPermissionResultListener(l)
        Shizuku.addBinderReceivedListenerSticky(b)
        Shizuku.addBinderDeadListener(d)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(l)
            Shizuku.removeBinderReceivedListener(b)
            Shizuku.removeBinderDeadListener(d)
        }
    }

    LaunchedEffect(shizuku) {
        if (shizuku == ShizukuState.READY) {
            games = Actions.installedGames()
            games.firstOrNull()?.let { selected = it }
        }
    }

    fun run(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            log = try { block() } catch (e: Throwable) { "ERROR: ${e.message}" }
            busy = false
        }
    }

    fun overlay(hud: Boolean, tools: Boolean) {
        hudOn = hud
        toolsOn = tools
        if (!Settings.canDrawOverlays(ctx)) {
            log = "Pehle 'Overlay permission dein' dabayein, phir dobara try karein."
            hudOn = false
            toolsOn = false
            return
        }
        ctx.startForegroundService(
            Intent(ctx, OverlayService::class.java)
                .putExtra(OverlayService.EXTRA_GAME, selected)
                .putExtra(OverlayService.EXTRA_HUD, hud)
                .putExtra(OverlayService.EXTRA_TOOLS, tools)
        )
    }

    val ready = shizuku == ShizukuState.READY
    val can = ready && !busy

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HyperGameBooster", style = MaterialTheme.typography.headlineSmall)
            when (shizuku) {
                ShizukuState.NOT_RUNNING -> Text("Shizuku chal nahi raha. Pehle Shizuku app se start karein.")
                ShizukuState.NO_PERMISSION -> Button(onClick = { Shizuku.requestPermission(1001) }) { Text("Shizuku permission dein") }
                else -> Text("Shizuku: tayyar")
            }
            val list = if (games.isEmpty()) Games.known.keys.toList() else games
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { pkg ->
                    FilterChip(selected = selected == pkg, onClick = { selected = pkg }, label = { Text(Games.known[pkg] ?: pkg) })
                }
            }
        }

        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            listOf("Dashboard", "GFX", "Input", "Lab", "Restore").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> {
                    Section("Native mode (bina Shizuku ke bhi chalta hai)") {
                        Text(nativeReport, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { nativeReport = NativeTools.deviceReport(ctx) }) { Text("Refresh info") }
                            OutlinedButton(onClick = { log = NativeTools.setWifiLowLatency(ctx, true) }) { Text("Wi-Fi low-latency ON") }
                            OutlinedButton(onClick = { log = NativeTools.setWifiLowLatency(ctx, false) }) { Text("OFF") }
                        }
                    }
                    Section("1-Tap Boost") {
                        Button(enabled = can, onClick = { run { Actions.esportsMode(selected, ctx.packageName) } }) { Text("ACTIVATE ESPORTS MODE") }
                        Text("Thermal override + 120Hz + Performance + DND + Wi-Fi low latency + keep-alive + RAM clean", style = MaterialTheme.typography.bodySmall)
                        Button(enabled = can, onClick = {
                            run {
                                val a = Actions.esportsMode(selected, ctx.packageName)
                                val l = Actions.launch(selected)
                                "$a\n\n$l"
                            }
                        }) { Text("Esports Mode + Game Start") }
                    }
                    Section("Auto mode (game detect)") {
                        OnOff("Auto mode", can, {
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            ctx.startForegroundService(Intent(ctx, AutoService::class.java).putExtra(AutoService.EXTRA_GAME, selected))
                            log = "Auto mode ON: ${Games.known[selected]} detect hote hi Esports Mode chalega, game se bahar jaane par default."
                        }, {
                            ctx.stopService(Intent(ctx, AutoService::class.java))
                            run { Actions.revertAll(selected) }
                        })
                        Text("Shizuku chalta rehna zaroori hai. Game khulte hi boost, band hote hi sab default (kuch second baad).", style = MaterialTheme.typography.bodySmall)
                    }
                    Section("Game Profile") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = can, onClick = { run { Actions.applyProfile(Profile.PERFORMANCE, selected) } }) { Text("Performance") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.applyProfile(Profile.BALANCED, selected) } }) { Text("Balanced") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.applyProfile(Profile.BATTERY_SAVER, selected) } }) { Text("Battery") }
                        }
                    }
                    Section("Safai") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = can, onClick = { run { Actions.cleanRam(emptySet(), ctx.packageName) } }) { Text("RAM clean") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.trimCaches() } }) { Text("Cache clean") }
                        }
                        OnOff("Background bloat standby", can, { run { Actions.standby(true) } }, { run { Actions.standby(false) } })
                        OnOff("Phantom process limit", can, { run { Actions.phantom(true) } }, { run { Actions.phantom(false) } })
                    }
                }

                1 -> {
                    Section("Asli Resolution (system-wide, PUBG par bhi chalta hai)") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(100, 90, 85, 80, 75, 70, 65, 60).forEach { p ->
                                FilterChip(selected = false, enabled = can, onClick = {
                                    ctx.getSharedPreferences("hgb", Context.MODE_PRIVATE).edit().putInt("res_$selected", p).apply()
                                    run { Actions.setResolution(p) }
                                }, label = { Text("$p%") })
                            }
                        }
                        OutlinedButton(enabled = can, onClick = {
                            ctx.getSharedPreferences("hgb", Context.MODE_PRIVATE).edit().remove("res_$selected").apply()
                            run { Actions.resetResolution() }
                        }) { Text("Resolution Reset (original)") }
                        Text("Dabate hi screen resolution + density kam hoti hai (UI wahi size ki rehti hai). Auto mode is game ke liye yehi % lagayega aur game band hone par original kar dega. 75-80% best balance hai.", style = MaterialTheme.typography.bodySmall)
                    }
                    Section("Stretch Resolution (UltDroid jaisa)") {
                        Text("Sensitivity: landscape height badhta hai (zyada pixels per mm = swipe/aim tez). Smoothness: width kam (halka GPU load). Default = original. Game band hone par original wapas.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("sens" to "Sensitivity", "smooth" to "Smoothness").forEach { (mk, ml) ->
                                listOf("low", "med", "high").forEach { lv ->
                                    FilterChip(selected = false, enabled = can, onClick = {
                                        ctx.getSharedPreferences("hgb", Context.MODE_PRIVATE).edit().putString("stretch_$selected", "$mk:$lv").apply()
                                        run { Actions.stretchResolution(mk, lv) }
                                    }, label = { Text("$ml $lv") })
                                }
                            }
                        }
                        OutlinedButton(enabled = can, onClick = {
                            ctx.getSharedPreferences("hgb", Context.MODE_PRIVATE).edit().putString("stretch_$selected", "ipad:x").apply()
                            run { Actions.stretchResolution("ipad", "x") }
                        }) { Text("iPad view (4:3)") }
                        OutlinedButton(enabled = can, onClick = {
                            ctx.getSharedPreferences("hgb", Context.MODE_PRIVATE).edit().remove("stretch_$selected").apply()
                            run { Actions.resetResolution() }
                        }) { Text("Default (original resolution)") }
                    }
                    Section("Resolution scale (Game Mode)") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("0.5", "0.6", "0.7", "0.8", "0.9", "1.0").forEach { v ->
                                FilterChip(selected = ds == v, onClick = { ds = v }, label = { Text(v) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60, 90, 120).forEach { v ->
                                FilterChip(selected = fps == v, onClick = { fps = v }, label = { Text("$v FPS") })
                            }
                        }
                        Button(enabled = can, onClick = { run { Actions.gameModeTuning(selected, fps, ds) } }) { Text("Apply (cmd game set)") }
                        OutlinedButton(enabled = can, onClick = { run { Actions.overlayFallback(selected, fps, ds) } }) { Text("Apply (fallback: game_overlay)") }
                        OutlinedButton(enabled = can, onClick = { run { Actions.gameModeReset(selected) + "\n" + Actions.overlayReset(selected) } }) { Text("Reset Game Mode") }
                        OutlinedButton(enabled = !busy, onClick = {
                            ctx.getSharedPreferences("hgb", Context.MODE_PRIVATE).edit()
                                .putString("ds_$selected", ds).putInt("fps_$selected", fps).apply()
                            log = "Is game ke liye save: scale $ds, ${fps}fps (Auto mode mein khud lagega)"
                        }) { Text("Is game ke liye save (Auto mode)") }
                        Text("Asar tab hota hai jab game Android Game Mode support kare; PUBG apna resolution khud bhi control karta hai.", style = MaterialTheme.typography.bodySmall)
                    }
                    Section("Display") {
                        OnOff("Refresh 120Hz lock", can, { run { Actions.lockRefresh(120) } }, { run { Actions.lockRefresh(null) } })
                        OnOff("Animations 0.5x", can, { run { Actions.animations("0.5") } }, { run { Actions.animations(null) } })
                        OutlinedButton(enabled = can, onClick = { run { Actions.densityInfo() } }) { Text("Density dekhein") }
                        OutlinedTextField(value = dpiText, onValueChange = { dpiText = it.filter(Char::isDigit) }, label = { Text("DPI (240-800)") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = can && dpiText.isNotEmpty(), onClick = { run { Actions.setDensity(dpiText.toInt()) } }) { Text("Set") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.resetDensity() } }) { Text("Reset") }
                        }
                    }
                    Section("Colour saturation (enemy visibility)") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1.0", "1.2", "1.4", "1.6").forEach { v ->
                                FilterChip(selected = sat == v, onClick = { sat = v }, label = { Text(v) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = can, onClick = { run { Actions.saturation(sat) } }) { Text("Apply") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.saturation(null) } }) { Text("Reset") }
                        }
                        Text("Sirf display ke rang badalta hai (FPS par asar nahi). HyperOS 3 par block ho sakta hai.", style = MaterialTheme.typography.bodySmall)
                    }
                    Section("Game files (experimental)") {
                        OutlinedButton(enabled = can, onClick = { run { Actions.optimizeApp(selected) } }) { Text("AOT compile (slow, ek dafa)") }
                        OutlinedButton(enabled = can, onClick = { run { Actions.clearShaderCache(selected) } }) { Text("Shader cache clear") }
                        OnOff("UserCustom.ini (potato)", can, { run { Actions.userCustomIni(selected, true) } }, { run { Actions.userCustomIni(selected, false) } })
                        Text("UserCustom.ini game config badalta hai: asar game version par depend karta hai aur ban ka risk ho sakta hai. OFF dabane se purani file wapas aati hai.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = can, onClick = { run { Actions.backupConfig(selected) } }) { Text("Backup") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.restoreConfig(selected) } }) { Text("Restore") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.diagnose(selected) } }) { Text("Diagnose") }
                        }
                    }
                }

                2 -> {
                    Section("Input guard") {
                        OnOff("Haptic / vibration band", can, { run { Actions.haptics(true) } }, { run { Actions.haptics(false) } })
                        OnOff("Edge back gesture band", can, { run { Actions.edgeGesture(true) } }, { run { Actions.edgeGesture(false) } })
                        OnOff("DND", can, { run { Actions.dnd(true) } }, { run { Actions.dnd(false) } })
                        OnOff("Heads-up popups band", can, { run { Actions.headsUp(true) } }, { run { Actions.headsUp(false) } })
                        OnOff("Auto-brightness lock", can, { run { Actions.brightnessLock(true) } }, { run { Actions.brightnessLock(false) } })
                        OnOff("Social apps suspend (TikTok/Insta/FB)", can, { run { Actions.suspendSocial(true) } }, { run { Actions.suspendSocial(false) } })
                        Text(
                            "Touch sampling rate aur aim/swipe sensitivity shell se nahi badalti (touch chip ka firmware hai). Game ki apni sensitivity PUBG Settings mein hai.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Section("Network") {
                        OnOff("Wi-Fi low latency", can, { run { Actions.wifiLowLatency(true) } }, { run { Actions.wifiLowLatency(false) } })
                        OnOff("Wi-Fi/BLE background scan band", can, { run { Actions.wifiTweaks(true) } }, { run { Actions.wifiTweaks(false) } })
                        OnOff("Captive portal check band", can, { run { Actions.captivePortal(true) } }, { run { Actions.captivePortal(false) } })
                        OnOff("Game keep-alive", can, { run { Actions.keepAlive(selected, true) } }, { run { Actions.keepAlive(selected, false) } })
                        OnOff("Social apps background data firewall", can, { run { Actions.backgroundFirewall(true) } }, { run { Actions.backgroundFirewall(false) } })
                        OnOff("Bluetooth absolute volume off", can, { run { Actions.btAbsVolume(true) } }, { run { Actions.btAbsVolume(false) } })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = can, onClick = { run { Actions.dns("cloudflare") } }) { Text("DNS Cloudflare") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.dns("google") } }) { Text("DNS Google") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.dns("auto") } }) { Text("DNS Auto") }
                        }
                        Text("DNS badalne se in-game ping zyada nahi badalta (game IP se judta hai); ye sirf login/matchmaking ke liye hai.", style = MaterialTheme.typography.bodySmall)
                    }
                    Section("Background freeze (RAM + CPU khali)") {
                        OnOff("Cached-apps freezer + compaction + BG apps band + game VIP", can, { run { Actions.gamePerf(selected, true) } }, { run { Actions.gamePerf(selected, false) } })
                        Text("WhatsApp/Insta/TikTok/Snapchat/Telegram background mein band, game ko VIP standby bucket. Game ke baad Restore se wapas.", style = MaterialTheme.typography.bodySmall)
                    }
                    Section("Extra performance (UltDroid wali)") {
                        OnOff("Fixed performance + adaptive saver off + low power off", can, { run { Actions.powerExtras(true) } }, { run { Actions.powerExtras(false) } })
                        OnOff("Looper stats band (thora halka CPU)", can, { run { Actions.looperStats(true) } }, { run { Actions.looperStats(false) } })
                        OnOff("Doze (deviceidle) band (battery zyada khayega)", can, { run { Actions.deviceIdle(true) } }, { run { Actions.deviceIdle(false) } })
                        OutlinedButton(enabled = can, onClick = { run { Actions.killAll() } }) { Text("am kill-all (background saaf)") }
                        OnOff("DC dimming band (touch/flicker)", can, { run { Actions.dcDimming(true) } }, { run { Actions.dcDimming(false) } })
                        OutlinedButton(enabled = can, onClick = { run { Actions.idleMaintenance() } }) { Text("Idle maintenance (match se pehle)") }
                        OutlinedButton(enabled = can, onClick = { run { Actions.cleanGameLogs(selected) } }) { Text("Game Logs saaf (sirf Logs folder)") }
                    }
                    Section("Thermal") {
                        OnOff("Thermal override (software)", can, { run { Actions.thermalOverride(true) } }, { run { Actions.thermalOverride(false) } })
                        Text("Ye sirf Android ka thermal status badalta hai; Xiaomi ka hardware throttle nahi rukta.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                3 -> {
                    Section("Game Turbo ki asli settings dhoondhein (A/B diff)") {
                        Text("1) A dabayein  2) Game Turbo mein sirf EK option badlein  3) B dabayein  4) Diff dekhein. Log ko dabakar copy kar sakte hain.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = can, onClick = { run { Actions.snapshot("A") } }) { Text("Snapshot A") }
                            OutlinedButton(enabled = can, onClick = { run { Actions.snapshot("B") } }) { Text("Snapshot B") }
                            Button(enabled = !busy, onClick = { run { Actions.diffSnaps() } }) { Text("Diff") }
                        }
                    }
                    Section("Overlay pack (HUD + Tools)") {
                        OutlinedButton(enabled = can, onClick = { run { Actions.grantOverlay(ctx.packageName) } }) { Text("Overlay permission dein (Shizuku se)") }
                        OnOff("Floating HUD (FPS / Temp / Ping)", true, { overlay(true, toolsOn) }, { overlay(false, toolsOn) })
                        OnOff("Tools panel (stopwatch, counter, screenshot, AFK)", true, { overlay(hudOn, true) }, { overlay(hudOn, false) })
                        Text("HUD game ke upar FPS, temperature, thermal level aur ping dikhata hai. Tools panel ko drag karein; header tap karne se khulta/band hota hai. AFK mode se bahar nikalne ke liye 3 baar tez tap karein.", style = MaterialTheme.typography.bodySmall)
                    }
                    Section("Lobby timer (Flash Return)") {
                        Text("Match shuru hone ke intezaar mein game minimize karein; floating timer dikhega. Tap = game wapis, lamba dabao = band. Overlay permission upar se dein.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 45, 60).forEach { s ->
                                OutlinedButton(onClick = {
                                    ctx.startForegroundService(Intent(ctx, LobbyTimerService::class.java)
                                        .putExtra(LobbyTimerService.EXTRA_GAME, selected).putExtra(LobbyTimerService.EXTRA_SECONDS, s))
                                }) { Text("${s}s") }
                            }
                        }
                    }
                    Section("Earphone Left / Right test") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { AudioTest.play(0) }) { Text("Sirf LEFT") }
                            OutlinedButton(onClick = { AudioTest.play(1) }) { Text("Sirf RIGHT") }
                        }
                        Text("Sahi hone par left dabane par sirf left kaan mein awaaz aani chahiye.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { log = AudioTest.footstepEq(true) }) { Text("Sound preset ON") }
                            OutlinedButton(onClick = { log = AudioTest.footstepEq(false) }) { Text("OFF") }
                        }
                    }
                }

                else -> {
                    Section("Revert to Stock") {
                        Button(enabled = can, onClick = { run { Actions.revertAll(selected) } }) { Text("Sab kuch default par wapas") }
                        Text("Game ke baad zaroor dabayein: refresh, thermal, game mode, DND, Wi-Fi, haptics sab default.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = can, onClick = { run { Actions.settingsSnapshot() } }) { Text("Settings snapshot (backup)") }
                        OutlinedButton(enabled = can, onClick = { run { Actions.fstrim() } }) { Text("Storage TRIM (sm fstrim)") }
                    }
                }
            }

            Section("Log") { SelectionContainer { Text(log, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

private fun readStats(ctx: Context): String {
    val bat: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val hz = (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
        .getDisplay(0)?.refreshRate ?: 0f
    val thermal = if (Build.VERSION.SDK_INT >= 29) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        listOf("None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")
            .getOrElse(pm.currentThermalStatus) { "?" }
    } else {
        "n/a"
    }
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val ma = Math.abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / 1000
    val mv = bat?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val watts = ma.toDouble() * mv / 1_000_000.0
    val pct = if (level >= 0) level * 100 / scale else 0
    val freeMb = mi.availMem shr 20
    val totalMb = mi.totalMem shr 20
    return "Battery: $pct%   Temp: ${"%.1f".format(temp)} C\n" +
        "RAM free: $freeMb / $totalMb MB\n" +
        "Refresh rate: ${hz.toInt()} Hz\n" +
        "Thermal status: $thermal\n" +
        "Power draw: $ma mA (~${"%.1f".format(watts)} W)"
}
