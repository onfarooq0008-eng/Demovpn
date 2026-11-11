package com.fastvpn.app7k2q9x.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fastvpn.app7k2q9x.MainViewModel
import com.fastvpn.app7k2q9x.data.*

@Composable
fun LocationsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val list by ServerRepository.state.collectAsState()
    val favs by ServerStore.favorites.collectAsState()
    val recents by ServerStore.recents.collectAsState()
    val selName by Settings.selected.collectAsState()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }            // 0 ping, 1 speed, 2 country
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val q = query.trim()

    val filtered = list.servers.filter { q.isEmpty() || it.country.contains(q, true) || it.name.contains(q, true) }
    val groups = filtered.groupBy { it.country }
    val order = when (sort) {
        0 -> groups.entries.sortedBy { e -> e.value.minOf { it.pingMs ?: Int.MAX_VALUE } }
        1 -> groups.entries.sortedByDescending { e -> e.value.maxOf { it.speedMbps } }
        else -> groups.entries.sortedBy { it.key }
    }
    fun sorted(l: List<Server>) = when (sort) {
        0 -> l.sortedBy { it.pingMs ?: Int.MAX_VALUE }
        1 -> l.sortedByDescending { it.speedMbps }
        else -> l.sortedBy { it.name }
    }
    val recentServers = recents.mapNotNull { n -> list.servers.firstOrNull { it.name == n } }.take(3)

    ScreenScaffold("Locations", onBack, actions = {
        IconButton(onClick = { vm.refresh(true) }) { Icon(Icons.Filled.Refresh, "Refresh", tint = C.Text) }
    }) {
        if (list.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val fetched = if (list.fetchedAt > 0) DateUtils.getRelativeTimeSpanString(list.fetchedAt).toString() else "never"
                Text("${list.servers.size} servers · list updated ${list.updated ?: "unknown"} · checked $fetched", color = C.Muted, fontSize = 12.sp)
            }
            list.error?.let { e -> item { Text(e, color = C.Gold, fontSize = 13.sp) } }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search country or server", color = C.Muted) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.Muted) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Lowest ping", "Highest speed", "Country").forEachIndexed { i, label ->
                        FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), border = if (selName.isEmpty()) BorderStroke(1.5.dp, C.Blue) else null, onClick = { vm.selectFastest(); onBack() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, null, tint = C.Cyan, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Fastest server", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Ranks by ping, reachability, speed and your connection history", color = C.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (q.isEmpty() && recentServers.isNotEmpty()) {
                item { Text("Recently used", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                items(recentServers, key = { "r_" + it.name }) { s ->
                    ServerRow(s, s.name == selName, s.name in favs, { ServerStore.toggleFavorite(s.name) }) { vm.select(s); onBack() }
                }
            }
            if (list.servers.isEmpty() && !list.loading) item { Text("No servers yet. Connect to the internet and tap refresh.", color = C.Muted) }
            order.forEach { (country, servers) ->
                val open = q.isNotEmpty() || country in expanded
                item(key = "h_$country") {
                    Card(Modifier.fillMaxWidth(), onClick = { expanded = if (country in expanded) expanded - country else expanded + country }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(servers.first().flag, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(country, color = C.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${servers.size}", color = C.Muted)
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = C.Muted)
                        }
                    }
                }
                if (open) items(sorted(servers), key = { it.name }) { s ->
                    ServerRow(s, s.name == selName, s.name in favs, { ServerStore.toggleFavorite(s.name) }) { vm.select(s); onBack() }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val list by ServerRepository.state.collectAsState()
    val favs by ServerStore.favorites.collectAsState()
    val selName by Settings.selected.collectAsState()
    val items = list.servers.filter { it.name in favs }
    ScreenScaffold("Favorites", onBack) {
        if (items.isEmpty()) Text("Tap the star on any server to save it here.", color = C.Muted, modifier = Modifier.padding(24.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.name }) { s ->
                ServerRow(s, s.name == selName, true, { ServerStore.toggleFavorite(s.name) }) { vm.select(s); onBack() }
            }
        }
    }
}
