package com.fastvpn.app7k2q9x.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fastvpn.app7k2q9x.data.ServerRepository
import com.fastvpn.app7k2q9x.data.ServerStore
import com.fastvpn.app7k2q9x.data.Settings
import com.fastvpn.app7k2q9x.util.DiagLog
import com.fastvpn.app7k2q9x.vpn.VpnSession
import com.fastvpn.app7k2q9x.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = C.Muted, modifier = Modifier.weight(1f))
        Text(value, color = C.Text)
    }
}

@Composable
fun DetailsScreen(onBack: () -> Unit) {
    val state by VpnSession.state.collectAsState()
    val info by VpnSession.info.collectAsState()
    val stats by VpnSession.stats.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var publicIp by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val connected = state == VpnState.CONNECTED
    ScreenScaffold("Connection details", onBack) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Row2("Status", state.name.lowercase().replace('_', ' '))
            Row2("Server", info.server?.let { "${it.country} (${it.name})" } ?: "–")
            Row2("Assigned VPN IP", info.assignedIp ?: "Not reported by engine")
            Row2("Public IP", publicIp ?: "Not checked")
            Row2("Duration", if (connected && info.connectedAt > 0) formatTime((now - info.connectedAt) / 1000) else "–")
            Row2("Protocol", "OpenVPN")
            Row2("Transport", info.transport ?: "–")
            Row2("Downloaded", formatBytes(stats.downBytes))
            Row2("Uploaded", formatBytes(stats.upBytes))
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                scope.launch { publicIp = try { ServerRepository.httpGetText("https://api.ipify.org") } catch (e: Exception) { "Lookup failed" } }
            }) { Text("Check public IP") }
            Text("This sends one request to api.ipify.org. Compare the result with your normal IP to confirm traffic is tunnelled; leaks are not tested by this app.", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val h by ServerStore.history.collectAsState()
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    ScreenScaffold("Connection history", onBack, actions = { TextButton(onClick = { ServerStore.clearHistory() }) { Text("Clear") } }) {
        if (h.isEmpty()) Text("No connections yet.", color = C.Muted, modifier = Modifier.padding(24.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(h) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${e.country} · ${e.serverName}", color = C.Text)
                        Text("${df.format(Date(e.startedAt))} · ${formatTime(e.durationSec)}", color = C.Muted, fontSize = 12.sp)
                        Text(e.result, color = if (e.result.startsWith("Failed")) C.Red else C.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController, onBack: () -> Unit) {
    val launch by Settings.connectOnLaunch.collectAsState()
    val reconnect by Settings.reconnect.collectAsState()
    val theme by Settings.theme.collectAsState()
    @Composable fun Toggle(label: String, sub: String, v: Boolean, set: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label, color = C.Text); Text(sub, color = C.Muted, fontSize = 12.sp) }
            Switch(checked = v, onCheckedChange = set)
        }
    }
    ScreenScaffold("Settings", onBack) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Toggle("Connect on app launch", "Uses the selected server, or the fastest one", launch) { Settings.setConnectOnLaunch(it) }
            Toggle("Reconnect automatically", "Up to 5 attempts with increasing delay", reconnect) { Settings.setReconnect(it) }
            Text("Theme", color = C.Text, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Dark", "Light").forEachIndexed { i, l -> FilterChip(selected = theme == i, onClick = { Settings.setTheme(i) }, label = { Text(l) }) }
            }
            val engine by Settings.engine.collectAsState()
            Text("OpenVPN engine", color = C.Text, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto", "ics-openvpn", "OpenVPN 3").forEachIndexed { i, l -> FilterChip(selected = engine == i, onClick = { Settings.setEngine(i) }, label = { Text(l) }) }
            }
            Text("Applies to the next connection. Only engines included in this build can connect.", color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            listOf("favorites" to "Favorites", "history" to "Connection history", "diagnostics" to "Diagnostic logs", "about" to "Privacy & about").forEach { (r, l) ->
                TextButton(onClick = { nav.navigate(r) }) { Text(l) }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val lines by DiagLog.lines.collectAsState()
    val ctx = LocalContext.current
    ScreenScaffold("Diagnostics", onBack, actions = {
        TextButton(onClick = {
            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, DiagLog.export()) }
            ctx.startActivity(Intent.createChooser(i, "Export sanitized logs"))
        }) { Text("Export") }
        TextButton(onClick = { DiagLog.clear() }) { Text("Clear") }
    }) {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(lines.asReversed()) { Text(it, color = C.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold("Privacy & about", onBack) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("FastVPN 2.0 · OpenVPN only", color = C.Text)
            Text("Servers come from a public, community-run list (VPN Gate volunteers) published on GitHub. Availability, speed and privacy depend entirely on each third-party server operator. Operators can see and log your traffic. Do not use these servers for banking, passwords or other sensitive activity, and do not assume anonymity.", color = C.Muted)
            Text("What this app does: it downloads the server list and the selected server's profile from GitHub over HTTPS, keeps a local cache, favorites and connection history on your device, and keeps sanitized diagnostic logs in memory. It has no account system and no backend, and it does not collect browsing history or send your traffic to us.", color = C.Muted)
            Text("Protection claims: the app routes traffic according to the server's own profile. It does not test for DNS or IPv6 leaks and does not guarantee that all traffic is tunnelled.", color = C.Muted)
            Text("The OpenVPN engine is third-party open-source software (OpenVPN for Android / OpenVPN core) under its own licenses.", color = C.Muted)
        }
    }
}
