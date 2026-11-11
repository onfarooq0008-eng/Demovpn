package com.fastvpn.app7k2q9x.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class HistoryEntry(val serverName: String, val country: String, val startedAt: Long, val durationSec: Long, val result: String)
@Serializable data class ServerStat(val ok: Int = 0, val fail: Int = 0, val consecutiveFail: Int = 0, val lastFail: Long = 0, val lastTried: Long = 0)

/** Favorites, recents, per-server success statistics and connection history (local only). */
object ServerStore {
    private lateinit var p: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    val favorites = MutableStateFlow<Set<String>>(emptySet())
    val recents = MutableStateFlow<List<String>>(emptyList())
    val history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    @Volatile var stats: Map<String, ServerStat> = emptyMap()
        private set

    fun init(c: Context) {
        p = c.getSharedPreferences("store", Context.MODE_PRIVATE)
        favorites.value = p.getStringSet("fav", emptySet()) ?: emptySet()
        recents.value = p.getString("recents", null)?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
        history.value = p.getString("history", null)?.let { runCatching { json.decodeFromString<List<HistoryEntry>>(it) }.getOrNull() } ?: emptyList()
        stats = p.getString("stats", null)?.let { runCatching { json.decodeFromString<Map<String, ServerStat>>(it) }.getOrNull() } ?: emptyMap()
    }

    fun toggleFavorite(name: String) {
        val n = favorites.value.toMutableSet().also { if (!it.add(name)) it.remove(name) }
        favorites.value = n; p.edit().putStringSet("fav", n).apply()
    }

    @Synchronized fun recordSuccess(name: String) {
        val s = stats[name] ?: ServerStat()
        stats = stats + (name to s.copy(ok = s.ok + 1, consecutiveFail = 0, lastTried = System.currentTimeMillis()))
        val r = (listOf(name) + recents.value.filter { it != name }).take(10)
        recents.value = r
        p.edit().putString("stats", json.encodeToString(stats)).putString("recents", json.encodeToString(r)).apply()
    }

    @Synchronized fun recordFailure(name: String) {
        val now = System.currentTimeMillis()
        val s = stats[name] ?: ServerStat()
        stats = stats + (name to s.copy(fail = s.fail + 1, consecutiveFail = s.consecutiveFail + 1, lastFail = now, lastTried = now))
        p.edit().putString("stats", json.encodeToString(stats)).apply()
    }

    /** A server is flagged unavailable after 3 consecutive failures within the last 30 minutes. */
    fun isAvailable(name: String): Boolean {
        val s = stats[name] ?: return true
        return !(s.consecutiveFail >= 3 && System.currentTimeMillis() - s.lastFail < 30 * 60_000)
    }

    fun addHistory(e: HistoryEntry) {
        val h = (listOf(e) + history.value).take(100)
        history.value = h
        p.edit().putString("history", json.encodeToString(h)).apply()
    }
    fun clearHistory() { history.value = emptyList(); p.edit().remove("history").apply() }
}
