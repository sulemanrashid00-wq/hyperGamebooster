package com.hypergameboost

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.hardware.display.DisplayManager
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

    // Shizuku listeners
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

    // Installed games
    LaunchedEffect(shizuku) {
        if (shizuku == ShizukuState.READY) {
            games = Actions.installedGames()
            games.firstOrNull()?.let { selected = it }
        }
    }

    // Live monitor (har 2 second)
    LaunchedEffect(Unit) {
        while (true) {
            stats = readStats(ctx)
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("HyperGameBooster", style = MaterialTheme.typography.headlineSmall)

        // --- Shizuku status ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (shizuku) {
                    ShizukuState.NOT_RUNNING -> Text("Shizuku chal nahi raha. Pehle Shizuku app kholkar start karein.")
                    ShizukuState.NO_PERMISSION -> {
                        Text("Shizuku chal raha hai, permission chahiye.")
                        Button(onClick = { Shizuku.requestPermission(1001) }) { Text("Permission dein") }
                    }
                    else -> Text("Shizuku: tayyar")
                }
                OutlinedButton(onClick = { shizuku = ShizukuState.current() }) { Text("Dobara check") }
            }
        }

        // --- Monitor ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Monitor", style = MaterialTheme.typography.titleMedium)
                Text(stats)
            }
        }

        // --- Game select ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Game", style = MaterialTheme.typography.titleMedium)
                val list = if (games.isEmpty()) Games.known.keys.toList() else games
                list.forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == pkg, onClick = { selected = pkg })
                        Text(Games.known[pkg] ?: pkg)
                    }
                }
                if (games.isEmpty() && ready) Text("Koi supported game install nahi mila.", style = MaterialTheme.typography.bodySmall)
            }
        }

        // --- Profiles ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Game Profile", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = ready && !busy, onClick = { run { Actions.applyProfile(Profile.PERFORMANCE, selected) } }) { Text("Performance") }
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.applyProfile(Profile.BALANCED, selected) } }) { Text("Balanced") }
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.applyProfile(Profile.BATTERY_SAVER, selected) } }) { Text("Battery") }
                }
                Button(
                    enabled = ready && !busy,
                    onClick = {
                        run {
                            val a = Actions.applyProfile(Profile.PERFORMANCE, selected)
                            val c = Actions.cleanRam(emptySet(), ctx.packageName)
                            val l = Actions.launch(selected)
                            "$a\n\n$c\n\n$l"
                        }
                    }
                ) { Text("Boost + Game Start") }
            }
        }

        // --- Cleaning ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Safai", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.cleanRam(emptySet(), ctx.packageName) } }) { Text("RAM clean") }
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.trimCaches() } }) { Text("Cache clean") }
                }
                OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.clearGameCache(selected) } }) { Text("Game cache clear") }
            }
        }

        // --- Extra gaming tools ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Extra gaming tools", style = MaterialTheme.typography.titleMedium)
                OnOff("Refresh 120Hz", ready && !busy, { run { Actions.lockRefresh(120) } }, { run { Actions.lockRefresh(null) } })
                OnOff("DND", ready && !busy, { run { Actions.dnd(true) } }, { run { Actions.dnd(false) } })
                OnOff("Wi-Fi low latency", ready && !busy, { run { Actions.wifiLowLatency(true) } }, { run { Actions.wifiLowLatency(false) } })
                OnOff("Game keep-alive", ready && !busy, { run { Actions.keepAlive(selected, true) } }, { run { Actions.keepAlive(selected, false) } })
                OnOff("Animations 0.5x", ready && !busy, { run { Actions.animations("0.5") } }, { run { Actions.animations(null) } })
                OnOff("Game Mode 120fps", ready && !busy, { run { Actions.gameModeTuning(selected, 120, "1.0") } }, { run { Actions.gameModeReset(selected) } })
                OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.optimizeApp(selected) } }) { Text("Game optimize (dexopt, slow)") }
                Button(enabled = ready && !busy, onClick = { run { Actions.revertAll(selected) } }) { Text("Sab kuch default par wapas") }
            }
        }

        // --- Config backup ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Game config backup", style = MaterialTheme.typography.titleMedium)
                Text("Kisi bhi tool se settings badalne se pehle backup lein.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.backupConfig(selected) } }) { Text("Backup") }
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.restoreConfig(selected) } }) { Text("Restore") }
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.diagnose(selected) } }) { Text("Diagnose") }
                }
            }
        }

        // --- Density ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display density", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.densityInfo() } }) { Text("Current dekhein") }
                OutlinedTextField(value = dpiText, onValueChange = { dpiText = it.filter(Char::isDigit) }, label = { Text("DPI (240-800)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = ready && !busy && dpiText.isNotEmpty(), onClick = { run { Actions.setDensity(dpiText.toInt()) } }) { Text("Set") }
                    OutlinedButton(enabled = ready && !busy, onClick = { run { Actions.resetDensity() } }) { Text("Reset") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Log", style = MaterialTheme.typography.titleMedium)
                Text(log, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun OnOff(label: String, enabled: Boolean, onOn: () -> Unit, onOff: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = onOn) { Text("ON") }
            OutlinedButton(enabled = enabled, onClick = onOff) { Text("OFF") }
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
    return "Battery: ${if (level >= 0) level * 100 / scale else 0}%   Temp: %.1f C\nRAM free: %d / %d MB\nRefresh rate: %.0f Hz"
        .format(temp, mi.availMem shr 20, mi.totalMem shr 20, hz)
}
