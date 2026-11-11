package com.fastvpn.app7k2q9x.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fastvpn.app7k2q9x.MainViewModel
import com.fastvpn.app7k2q9x.data.ServerRepository
import com.fastvpn.app7k2q9x.data.Settings
import com.fastvpn.app7k2q9x.vpn.VpnSession
import com.fastvpn.app7k2q9x.vpn.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val state by VpnSession.state.collectAsState()
    val stats by VpnSession.stats.collectAsState()
    val info by VpnSession.info.collectAsState()
    val error by VpnSession.error.collectAsState()
    val progress by VpnSession.progress.collectAsState()
    val list by ServerRepository.state.collectAsState()
    val selName by Settings.selected.collectAsState()
    val server = list.servers.firstOrNull { it.name == selName }

    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state, info.connectedAt) {
        while (state == VpnState.CONNECTED && info.connectedAt > 0) {
            elapsed = (System.currentTimeMillis() - info.connectedAt) / 1000; delay(1000)
        }
        if (state == VpnState.DISCONNECTED) elapsed = 0
    }

    val connecting = state in setOf(VpnState.CONNECTING, VpnState.AUTHENTICATING, VpnState.WAITING_FOR_TUNNEL, VpnState.PERMISSION_REQUIRED)
    val accent = when (state) {
        VpnState.CONNECTED -> C.Green
        VpnState.FAILED, VpnState.NO_INTERNET -> C.Red
        VpnState.DISCONNECTED -> C.Blue
        else -> C.Gold
    }
    val title = when (state) {
        VpnState.CONNECTED -> "Connected"
        VpnState.CONNECTING -> "Connecting…"
        VpnState.AUTHENTICATING -> "Authenticating…"
        VpnState.WAITING_FOR_TUNNEL -> "Setting up tunnel…"
        VpnState.RECONNECTING -> "Reconnecting…"
        VpnState.DISCONNECTING -> "Disconnecting…"
        VpnState.PERMISSION_REQUIRED -> "Waiting for permission"
        VpnState.NO_INTERNET -> "No internet connection"
        VpnState.FAILED -> "Connection failed"
        VpnState.DISCONNECTED -> "Not connected"
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Top, C.Bg)))
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, null, tint = C.Cyan)
            Spacer(Modifier.width(6.dp))
            Text("FastVPN", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings", tint = C.Text) }
        }

        Spacer(Modifier.height(20.dp))
        Text(title, color = if (state == VpnState.CONNECTED) C.Green else C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(formatTime(elapsed), color = C.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        val sub = progress ?: error.takeIf { state == VpnState.FAILED || state == VpnState.NO_INTERNET || state == VpnState.DISCONNECTED }
        if (sub != null) Text(sub, color = if (progress == null) C.Red else C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(8.dp))
        ConnectButton(state, accent, connecting, enabled = state !in setOf(VpnState.CONNECTING, VpnState.AUTHENTICATING, VpnState.WAITING_FOR_TUNNEL, VpnState.DISCONNECTING)) { vm.onMainButton() }
        Text(
            when (state) { VpnState.CONNECTED -> "TAP TO DISCONNECT"; VpnState.FAILED -> "TAP TO RETRY"; VpnState.DISCONNECTED -> "TAP TO CONNECT"; else -> "PLEASE WAIT" },
            color = C.Muted, fontSize = 13.sp, letterSpacing = 2.sp,
        )
        if (connecting || state == VpnState.RECONNECTING) TextButton(onClick = { vm.cancel() }) { Text("Cancel", color = C.Muted) }

        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), onClick = { nav.navigate("locations") }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (server != null) server.flag else "⚡", fontSize = 30.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (server != null) "Selected server" else "Smart selection", color = C.Muted, fontSize = 12.sp)
                    Text(server?.country ?: "Fastest server (auto)", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        server?.let { "${it.name}  ·  ${it.pingMs?.let { p -> "$p ms" } ?: "– ms"}" } ?: "Best available server is chosen when you connect",
                        color = C.Muted, fontSize = 12.sp,
                    )
                }
                Text("Change", color = C.Blue, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), Icons.Filled.ArrowDownward, "Download", formatRate(stats.downRate))
            StatCard(Modifier.weight(1f), Icons.Filled.ArrowUpward, "Upload", formatRate(stats.upRate))
        }
        Spacer(Modifier.height(12.dp))
        StatCard(Modifier.fillMaxWidth(), Icons.Filled.SwapVert, "Data transferred", formatBytes(stats.downBytes + stats.upBytes))

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { nav.navigate("details") }) { Text("Details") }
            TextButton(onClick = { nav.navigate("favorites") }) { Text("Favorites") }
            TextButton(onClick = { nav.navigate("history") }) { Text("History") }
        }
    }
}

@Composable
private fun ConnectButton(state: VpnState, accent: Color, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(1f, 1.2f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(220.dp).scale(if (busy) pulse else 1f).clip(CircleShape).background(accent.copy(alpha = 0.10f)))
        Box(Modifier.size(185.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)))
        Box(
            Modifier.size(145.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f))))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy || state == VpnState.DISCONNECTING || state == VpnState.RECONNECTING) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp))
            else Icon(if (state == VpnState.FAILED || state == VpnState.NO_INTERNET) Icons.Filled.Refresh else Icons.Filled.PowerSettingsNew, "Connect", tint = Color.White, modifier = Modifier.size(62.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    Card(modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = C.Cyan)
            Spacer(Modifier.width(10.dp))
            Column { Text(label, color = C.Muted, fontSize = 12.sp); Text(value, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
