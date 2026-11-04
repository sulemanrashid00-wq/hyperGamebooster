package com.hypergameboost

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var shizuku by remember { mutableStateOf(ShizukuState.current()) }
    var games by remember { mutableStateOf(listOf<String>()) }
    var selected by remember { mutableStateOf(Games.known.keys.first()) }
    var log by remember { mutableStateOf("Taiyar.") }
    var busy by remember { mutableStateOf(false) }
    var dpiText by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var ds by remember { mutableStateOf("0.7") }
    var fps by remember { mutableStateOf(120) }

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

    LaunchedEffect(Unit) {
        while (true) {
            stats = try { readStats(ctx) } catch (e: Throwable) { "Monitor error: ${e.message}" }
            delay(2000)
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

        TabRow(selectedTabIndex = tab) {
            listOf("Dashboard", "GFX", "Input", "Restore").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> {
                    Section("Monitor") {
                        Text(stats)
                        OutlinedButton(enabled = can, onClick = { run { Actions.thermalInfo() } }) { Text("Thermal headroom check") }
                        OutlinedButton(enabled = can, onClick = { run { Actions.fpsTest(selected) } }) { Text("FPS test (asli FPS naapein)") }
                        Text("Dabane ke 5 second baad naapna shuru hota hai: game kholein aur match/lobby mein 15 second rukein. Result neeche Log mein aayega.", style = MaterialTheme.typography.bodySmall)
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
                        Text(
                            "Touch sampling rate aur aim/swipe sensitivity shell se nahi badalti (touch chip ka firmware hai). Game ki apni sensitivity PUBG Settings mein hai.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Section("Network") {
                        OnOff("Wi-Fi low latency", can, { run { Actions.wifiLowLatency(true) } }, { run { Actions.wifiLowLatency(false) } })
                        OnOff("Wi-Fi/BLE background scan band", can, { run { Actions.wifiTweaks(true) } }, { run { Actions.wifiTweaks(false) } })
                        OnOff("Game keep-alive", can, { run { Actions.keepAlive(selected, true) } }, { run { Actions.keepAlive(selected, false) } })
                    }
                    Section("Thermal") {
                        OnOff("Thermal override (software)", can, { run { Actions.thermalOverride(true) } }, { run { Actions.thermalOverride(false) } })
                        Text("Ye sirf Android ka thermal status badalta hai; Xiaomi ka hardware throttle nahi rukta.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                else -> {
                    Section("Revert to Stock") {
                        Button(enabled = can, onClick = { run { Actions.revertAll(selected) } }) { Text("Sab kuch default par wapas") }
                        Text("Game ke baad zaroor dabayein: refresh, thermal, game mode, DND, Wi-Fi, haptics sab default.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Section("Log") { Text(log, style = MaterialTheme.typography.bodySmall) }
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
    val pct = if (level >= 0) level * 100 / scale else 0
    val freeMb = mi.availMem shr 20
    val totalMb = mi.totalMem shr 20
    return "Battery: $pct%   Temp: ${"%.1f".format(temp)} C\n" +
        "RAM free: $freeMb / $totalMb MB\n" +
        "Refresh rate: ${hz.toInt()} Hz"
}
