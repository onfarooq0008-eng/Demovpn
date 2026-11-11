package com.fastvpn.app7k2q9x.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fastvpn.app7k2q9x.data.Server

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
            Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, border: BorderStroke? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        color = C.Card, shape = RoundedCornerShape(18.dp), border = border,
        modifier = modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun Quality(ping: Int?, available: Boolean): Pair<String, Color> = when {
    !available -> "Unavailable" to C.Muted
    ping == null -> "Unknown" to C.Muted
    ping < 50 -> "Excellent" to C.Green
    ping < 120 -> "Good" to C.Cyan
    else -> "Fair" to C.Gold
}

@Composable
fun ServerRow(s: Server, selected: Boolean, fav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    val (q, qc) = Quality(s.pingMs, s.isAvailable)
    Card(
        Modifier.fillMaxWidth().alpha(if (s.isAvailable) 1f else 0.5f),
        border = if (selected) BorderStroke(1.5.dp, C.Blue) else null,
        onClick = if (s.isAvailable) onClick else null,
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.flag, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.country, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(s.name, color = C.Muted, fontSize = 12.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(qc))
                    Spacer(Modifier.width(6.dp))
                    Text("$q · ${s.pingMs?.let { "$it ms" } ?: "– ms"} · ${"%.0f".format(s.speedMbps)} Mbps (reported)", color = C.Muted, fontSize = 11.sp)
                }
            }
            IconButton(onClick = onFav) {
                Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorite", tint = if (fav) C.Gold else C.Muted)
            }
        }
    }
}

fun formatTime(s: Long) = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> "%.2f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.1f MB".format(b / 1e6)
    b >= 1_000 -> "%.0f KB".format(b / 1e3)
    else -> "$b B"
}
fun formatRate(b: Long) = formatBytes(b) + "/s"
